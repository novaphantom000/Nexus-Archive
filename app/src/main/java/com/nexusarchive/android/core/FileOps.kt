package com.nexusarchive.android.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

enum class ConflictResolution { KEEP_BOTH, REPLACE, SKIP }

data class ItemFailure(val path: String, val message: String)

/** completed = top-level items fully handled; skipped = items (any depth) deliberately not processed. */
data class OpOutcome(val completed: Int, val skipped: Int, val failures: List<ItemFailure>)

data class ScanResult(val bytes: Long, val items: Long)

interface ProgressListener {
    fun onTotals(totalBytes: Long, totalItems: Long) {}
    fun onBytes(delta: Long) {}
    fun onItems(delta: Long) {}
    fun onCurrent(name: String) {}
}

object NoProgress : ProgressListener

/**
 * Streaming copy / move / delete on real filesystem paths.
 *
 * Safety rules:
 *  - Files are written to a hidden temp file in the destination folder and then renamed into place,
 *    so a failed or cancelled copy never leaves a half-written file under the final name and never
 *    corrupts an existing file that is being replaced.
 *  - Symbolic links are never followed. Copy/move skip them (and report the skip); delete removes the link only.
 *  - A folder can never be copied or moved into itself.
 *  - A move only deletes the source after the copy of that item fully succeeded with nothing skipped.
 */
class FileOps(private val bufferSize: Int = 256 * 1024) {

    private class Run(val resolution: ConflictResolution, val listener: ProgressListener) {
        val failures = ArrayList<ItemFailure>()
        var skipped = 0
        fun fail(file: File, message: String) {
            failures.add(ItemFailure(file.path, message))
        }
    }

    /** Counts bytes and items (files, folders, links) under [files] without following symlinks. */
    suspend fun scan(files: List<File>): ScanResult {
        var bytes = 0L
        var items = 0L
        val stack = ArrayDeque<File>()
        files.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val f = stack.removeLast()
            items++
            if (f.isSymlink()) continue
            if (f.isDirectory) {
                f.listFiles()?.forEach { stack.addLast(it) }
            } else {
                bytes += f.length()
            }
        }
        return ScanResult(bytes, items)
    }

    suspend fun copy(
        sources: List<File>,
        destDir: File,
        resolution: ConflictResolution,
        listener: ProgressListener = NoProgress,
    ): OpOutcome {
        val total = scan(sources)
        listener.onTotals(total.bytes, total.items)
        val run = Run(resolution, listener)
        if (!destDir.isDirectory) {
            sources.forEach { run.fail(it, "The destination is not a folder.") }
            return OpOutcome(0, 0, run.failures)
        }
        val usable = try {
            destDir.usableSpace
        } catch (e: Exception) {
            0L
        }
        if (usable in 1 until total.bytes) {
            run.failures.add(
                ItemFailure(
                    destDir.path,
                    "Not enough free space: about ${Formatters.bytes(total.bytes)} needed, ${Formatters.bytes(usable)} available.",
                ),
            )
            return OpOutcome(0, 0, run.failures)
        }
        var completed = 0
        for (src in sources) {
            currentCoroutineContext().ensureActive()
            if (!src.existsNoFollow()) {
                run.fail(src, "The source no longer exists.")
                continue
            }
            if (src.isDirectory && !src.isSymlink() && isSameOrInside(destDir, src)) {
                run.fail(src, "A folder can't be copied into itself.")
                continue
            }
            if (copyNode(src, destDir, run)) completed++
        }
        return OpOutcome(completed, run.skipped, run.failures)
    }

    suspend fun move(
        sources: List<File>,
        destDir: File,
        resolution: ConflictResolution,
        listener: ProgressListener = NoProgress,
    ): OpOutcome {
        val perSource = sources.map { scan(listOf(it)) }
        listener.onTotals(perSource.sumOf { it.bytes }, perSource.sumOf { it.items })
        val run = Run(resolution, listener)
        if (!destDir.isDirectory) {
            sources.forEach { run.fail(it, "The destination is not a folder.") }
            return OpOutcome(0, 0, run.failures)
        }
        var completed = 0
        for ((index, src) in sources.withIndex()) {
            currentCoroutineContext().ensureActive()
            listener.onCurrent(src.name)
            val size = perSource[index]
            if (!src.existsNoFollow()) {
                run.fail(src, "The source no longer exists.")
                continue
            }
            val isDir = src.isDirectory && !src.isSymlink()
            if (isDir && isSameOrInside(destDir, src)) {
                run.fail(src, "A folder can't be moved into itself.")
                continue
            }
            val target = File(destDir, src.name)
            if (sameFile(src, target)) {
                run.skipped++
                account(listener, size)
                continue
            }
            var finalTarget = target
            var merge = false
            if (target.existsNoFollow()) {
                when (resolution) {
                    ConflictResolution.SKIP -> {
                        run.skipped++
                        account(listener, size)
                        continue
                    }
                    ConflictResolution.KEEP_BOTH -> finalTarget = FileNames.uniqueFile(destDir, src.name, isDir)
                    ConflictResolution.REPLACE -> {
                        if (isDir && target.isDirectory) {
                            merge = true
                        } else if (isDir != target.isDirectory) {
                            run.fail(src, "Can't replace a ${if (target.isDirectory) "folder" else "file"} with a ${if (isDir) "folder" else "file"}.")
                            continue
                        }
                    }
                }
            }
            // Fast path: a rename on the same filesystem moves everything atomically with no copying.
            if (!merge && src.renameTo(finalTarget)) {
                account(listener, size)
                completed++
                continue
            }
            // Slow path (other volume, or merging into an existing folder): copy, then delete the source
            // only if every part of it was copied.
            val failuresBefore = run.failures.size
            val skippedBefore = run.skipped
            val copied = copyNode(src, destDir, run)
            val clean = copied && run.failures.size == failuresBefore && run.skipped == skippedBefore
            if (clean) {
                val deleteRun = Run(resolution, NoProgress)
                if (deleteTree(src, deleteRun)) {
                    completed++
                } else {
                    run.failures.addAll(deleteRun.failures)
                    run.fail(src, "Copied, but the original could not be removed.")
                }
            }
        }
        return OpOutcome(completed, run.skipped, run.failures)
    }

    suspend fun delete(targets: List<File>, listener: ProgressListener = NoProgress): OpOutcome {
        val total = scan(targets)
        listener.onTotals(total.bytes, total.items)
        val run = Run(ConflictResolution.SKIP, listener)
        var completed = 0
        for (t in targets) {
            currentCoroutineContext().ensureActive()
            if (deleteTree(t, run)) completed++
        }
        return OpOutcome(completed, 0, run.failures)
    }

    // ---- internals ----------------------------------------------------------------------------

    private fun account(listener: ProgressListener, size: ScanResult) {
        listener.onBytes(size.bytes)
        listener.onItems(size.items)
    }

    /** Returns true only if [src] and everything below it was copied (nothing failed or skipped). */
    private suspend fun copyNode(src: File, destParent: File, run: Run): Boolean {
        currentCoroutineContext().ensureActive()
        run.listener.onCurrent(src.name)

        if (src.isSymlink()) {
            run.skipped++
            run.listener.onItems(1)
            return false
        }
        val isDir = src.isDirectory
        var target = File(destParent, src.name)

        if (target.existsNoFollow()) {
            if (sameFile(src, target)) {
                // Copying into the folder the item already lives in => make a duplicate.
                target = FileNames.uniqueFile(destParent, src.name, isDir)
            } else {
                when (run.resolution) {
                    ConflictResolution.KEEP_BOTH -> target = FileNames.uniqueFile(destParent, src.name, isDir)
                    ConflictResolution.SKIP -> {
                        run.skipped++
                        val skippedSize = scan(listOf(src))
                        account(run.listener, skippedSize)
                        return false
                    }
                    ConflictResolution.REPLACE -> {
                        if (isDir != target.isDirectory) {
                            run.fail(src, "Can't replace a ${if (target.isDirectory) "folder" else "file"} with a ${if (isDir) "folder" else "file"}.")
                            run.listener.onItems(1)
                            return false
                        }
                    }
                }
            }
        }

        if (isDir) {
            if (!target.exists()) {
                try {
                    Files.createDirectory(target.toPath())
                } catch (e: IOException) {
                    run.fail(src, describe(e))
                    run.listener.onItems(1)
                    return false
                }
            }
            val children = src.listFiles()
            if (children == null) {
                run.fail(src, "This folder could not be read.")
                run.listener.onItems(1)
                return false
            }
            var complete = true
            for (child in children) {
                if (!copyNode(child, target, run)) complete = false
            }
            target.setLastModified(src.lastModified())
            run.listener.onItems(1)
            return complete
        }
        return copyFile(src, target, run)
    }

    private suspend fun copyFile(src: File, target: File, run: Run): Boolean {
        val parent = target.parentFile ?: return false
        val tmp = File(parent, ".${target.name}.nexus-part")
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(bufferSize)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        run.listener.onBytes(read.toLong())
                    }
                    output.flush()
                }
            }
            tmp.setLastModified(src.lastModified())
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            run.listener.onItems(1)
            return true
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            run.fail(src, describe(e))
            run.listener.onItems(1)
            return false
        }
    }

    /** Deletes [file] (links are removed, never followed). Returns true if it is gone afterwards. */
    private suspend fun deleteTree(file: File, run: Run): Boolean {
        currentCoroutineContext().ensureActive()
        run.listener.onCurrent(file.name)
        val path = file.toPath()
        try {
            if (file.isSymlink()) {
                Files.delete(path)
                run.listener.onItems(1)
                return true
            }
            if (file.isDirectory) {
                val children = file.listFiles()
                if (children == null) {
                    run.fail(file, "This folder could not be read.")
                    run.listener.onItems(1)
                    return false
                }
                var ok = true
                for (child in children) {
                    if (!deleteTree(child, run)) ok = false
                }
                if (!ok) return false // never try to remove a folder that still has undeletable content
                Files.delete(path)
                run.listener.onItems(1)
                return true
            }
            Files.delete(path)
            run.listener.onItems(1)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchFileException) {
            run.listener.onItems(1)
            return true // already gone
        } catch (e: Exception) {
            run.fail(file, describe(e))
            run.listener.onItems(1)
            return false
        }
    }
}

/** True when [path] is [ancestor] itself or lies inside it (compared by canonical path). */
fun isSameOrInside(path: File, ancestor: File): Boolean =
    try {
        val a = ancestor.canonicalFile.path.trimEnd(File.separatorChar)
        val c = path.canonicalFile.path.trimEnd(File.separatorChar)
        c == a || c.startsWith(a + File.separator)
    } catch (e: IOException) {
        false
    }

internal fun sameFile(a: File, b: File): Boolean =
    try {
        a.canonicalFile == b.canonicalFile
    } catch (e: IOException) {
        a.absoluteFile == b.absoluteFile
    }

internal fun describe(e: Exception): String =
    when (e) {
        is AccessDeniedException -> "Permission denied."
        is NoSuchFileException -> "File not found."
        is FileAlreadyExistsException -> "An item with that name already exists."
        is DirectoryNotEmptyException -> "The folder is not empty."
        is FileSystemException -> e.reason ?: e.message ?: "File system error."
        else -> {
            val m = e.message ?: e.javaClass.simpleName
            if (m.contains("ENOSPC", ignoreCase = true) || m.contains("No space left", ignoreCase = true)) {
                "Not enough free space."
            } else {
                m
            }
        }
    }

/** Short, blocking operations (rename / create). Each returns a user-presentable failure message. */
object FileCommands {

    fun rename(src: File, newName: String): Result<File> {
        val name = newName.trim()
        FileNames.validate(name)?.let { return Result.failure(IllegalArgumentException(it)) }
        val parent = src.parentFile ?: return Result.failure(IllegalArgumentException("This location can't be renamed."))
        val target = File(parent, name)
        if (target.path == src.path) return Result.success(src)
        if (target.existsNoFollow() && !name.equals(src.name, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("An item named \"$name\" already exists here."))
        }
        return if (src.renameTo(target)) {
            Result.success(target)
        } else {
            Result.failure(IOException("Couldn't rename. The folder may be read-only or protected."))
        }
    }

    fun createFolder(parent: File, name: String): Result<File> = create(parent, name, directory = true)

    fun createFile(parent: File, name: String): Result<File> = create(parent, name, directory = false)

    private fun create(parent: File, rawName: String, directory: Boolean): Result<File> {
        val name = rawName.trim()
        FileNames.validate(name)?.let { return Result.failure(IllegalArgumentException(it)) }
        val target = File(parent, name)
        if (target.existsNoFollow()) {
            return Result.failure(IllegalArgumentException("An item named \"$name\" already exists here."))
        }
        return try {
            if (directory) Files.createDirectory(target.toPath()) else Files.createFile(target.toPath())
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(IOException(describe(e)))
        }
    }
}
