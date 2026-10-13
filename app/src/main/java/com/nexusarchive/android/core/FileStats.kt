package com.nexusarchive.android.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

data class DirStats(val totalBytes: Long, val fileCount: Long, val folderCount: Long)

/** Metadata the filesystem actually reports. Null means "not available", never a made-up value. */
data class FileDetails(
    val canRead: Boolean,
    val canWrite: Boolean,
    val lastAccessedMs: Long?,
    val isSymlink: Boolean,
    val parentPath: String?,
)

object FileStats {

    /** Recursive size/count; symbolic links are counted as files of size 0 and never followed. */
    suspend fun directoryStats(dir: File): DirStats {
        var bytes = 0L
        var files = 0L
        var folders = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        var first = true
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val f = stack.removeLast()
            if (f.isSymlink()) {
                files++
                continue
            }
            if (f.isDirectory) {
                if (!first) folders++
                first = false
                f.listFiles()?.forEach { stack.addLast(it) }
            } else {
                files++
                bytes += f.length()
            }
        }
        return DirStats(bytes, files, folders)
    }

    /** Streams the file through [algorithm] ("MD5", "SHA-1", "SHA-256"). [onProgress] gets total bytes read so far. */
    suspend fun hash(file: File, algorithm: String, onProgress: (Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(256 * 1024)
        var total = 0L
        var lastReported = 0L
        FileInputStream(file).use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                total += read
                if (total - lastReported >= 4L * 1024 * 1024) {
                    lastReported = total
                    onProgress(total)
                }
            }
        }
        onProgress(total)
        return digest.digest().joinToString("") { b -> String.format("%02x", b) }
    }

    fun details(file: File): FileDetails {
        var accessed: Long? = null
        try {
            accessed = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).lastAccessTime().toMillis()
        } catch (e: Exception) {
            // Not provided by this filesystem: leave null so the UI shows "Unavailable".
        }
        return FileDetails(
            canRead = file.canRead(),
            canWrite = file.canWrite(),
            lastAccessedMs = accessed,
            isSymlink = file.isSymlink(),
            parentPath = file.parent,
        )
    }
}
