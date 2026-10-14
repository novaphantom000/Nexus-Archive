package com.nexusarchive.android.archive

import com.nexusarchive.android.core.ConflictResolution
import com.nexusarchive.android.core.FileNames
import com.nexusarchive.android.core.ItemFailure
import com.nexusarchive.android.core.NoProgress
import com.nexusarchive.android.core.OpOutcome
import com.nexusarchive.android.core.ProgressListener
import com.nexusarchive.android.core.existsNoFollow
import com.nexusarchive.android.core.isSymlink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Reads, tests, extracts and creates archives on real filesystem paths.
 *
 * Safety rules:
 *  - Entry names are normalized; absolute paths, drive letters and ".." are refused (reported as skipped).
 *  - Every target is re-checked with canonical paths so it cannot leave the destination folder,
 *    including through a pre-existing symbolic link.
 *  - Symbolic and hard links inside archives are never created; they are skipped and reported.
 *  - Files are written to a hidden temp file and renamed into place; failures and cancellation remove the temp file.
 *  - Nothing is ever executed.
 *
 * Progress: for ZIP and 7z the counters are uncompressed bytes written. For TAR-family and single-stream
 * formats they are compressed bytes read from the archive file, because the uncompressed size is not known
 * without decompressing everything twice.
 */
class ArchiveEngine(private val bufferSize: Int = 64 * 1024) {

    private class EntryData(val info: ArchiveEntryInfo, val stream: InputStream?, val skipReason: String?)

    /** Counts bytes read from the underlying file and reports them as progress. */
    private class CountingInputStream(input: InputStream, private val listener: ProgressListener?) : FilterInputStream(input) {
        override fun read(): Int {
            val b = super.read()
            if (b >= 0) listener?.onBytes(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) listener?.onBytes(n.toLong())
            return n
        }
    }

    private class SevenZEntryStream(private val file: SevenZFile) : InputStream() {
        override fun read(): Int = file.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = file.read(b, off, len)
    }

    // ---------------------------------------------------------------------------------------------
    // Listing
    // ---------------------------------------------------------------------------------------------

    suspend fun list(archive: File, password: CharArray? = null): ArchiveListing = withContext(Dispatchers.IO) {
        val format = requireFormat(archive)
        when (format) {
            ArchiveFormat.ZIP -> listZip(archive, format)
            ArchiveFormat.SEVEN_Z -> list7z(archive, format, password)
            ArchiveFormat.GZ, ArchiveFormat.BZ2, ArchiveFormat.XZ -> listSingle(archive, format)
            else -> listStreamed(archive, format)
        }
    }

    private fun requireFormat(archive: File): ArchiveFormat {
        if (!archive.isFile) throw ArchiveException(ArchiveException.Kind.IO, "The archive no longer exists.")
        ArchiveFormats.unsupportedReason(archive.name)?.let { reason ->
            // Only trust the extension message when the content is not a format we can read anyway.
            val detected = ArchiveFormats.detect(archive)
            if (detected == null) throw ArchiveException(ArchiveException.Kind.UNSUPPORTED, reason)
            return detected
        }
        return ArchiveFormats.detect(archive)
            ?: throw ArchiveException(ArchiveException.Kind.UNSUPPORTED, "This file isn't a supported archive, or it is damaged.")
    }

    private fun listZip(archive: File, format: ArchiveFormat): ArchiveListing {
        val entries = ArrayList<ArchiveEntryInfo>()
        var unsafe = 0
        var anyEncrypted = false
        try {
            ZipFile(archive).use { zip ->
                if (!zip.isValidZipFile) {
                    throw ArchiveException(ArchiveException.Kind.CORRUPT, "This isn't a valid ZIP archive, or it is damaged.")
                }
                val headers: List<FileHeader> = zip.fileHeaders ?: emptyList()
                for (h in headers) {
                    val path = ArchivePaths.normalize(h.fileName)
                    if (path == null) {
                        unsafe++
                        continue
                    }
                    if (path.isEmpty()) continue
                    if (h.isEncrypted) anyEncrypted = true
                    entries.add(
                        ArchiveEntryInfo(
                            path = path,
                            isDirectory = h.isDirectory,
                            size = h.uncompressedSize,
                            compressedSize = h.compressedSize,
                            modifiedMs = h.lastModifiedTimeEpoch,
                            encrypted = h.isEncrypted,
                        ),
                    )
                }
            }
        } catch (e: ZipException) {
            throw mapZip(e, encrypted = false)
        }
        return ArchiveListing(format, entries, anyEncrypted, unsafe)
    }

    private fun list7z(archive: File, format: ArchiveFormat, password: CharArray?): ArchiveListing {
        val entries = ArrayList<ArchiveEntryInfo>()
        var unsafe = 0
        try {
            open7z(archive, password).use { sevenZ ->
                for (e in sevenZ.entries) {
                    if (e.isAntiItem) continue
                    val path = ArchivePaths.normalize(e.name ?: "")
                    if (path == null) {
                        unsafe++
                        continue
                    }
                    if (path.isEmpty()) continue
                    entries.add(
                        ArchiveEntryInfo(
                            path = path,
                            isDirectory = e.isDirectory,
                            size = if (e.isDirectory) -1L else e.size,
                            compressedSize = -1L,
                            modifiedMs = if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L,
                            encrypted = false,
                        ),
                    )
                }
            }
        } catch (e: PasswordRequiredException) {
            throw ArchiveException(ArchiveException.Kind.PASSWORD_REQUIRED, "This archive is password protected.", e)
        } catch (e: IOException) {
            throw map7z(e, password != null)
        }
        return ArchiveListing(format, entries, false, unsafe)
    }

    private fun listSingle(archive: File, format: ArchiveFormat): ArchiveListing {
        val name = ArchiveFormats.baseName(archive.name)
        val entry = ArchiveEntryInfo(name, false, -1L, archive.length(), archive.lastModified(), false)
        return ArchiveListing(format, listOf(entry), false, 0)
    }

    private fun listStreamed(archive: File, format: ArchiveFormat): ArchiveListing {
        val entries = ArrayList<ArchiveEntryInfo>()
        var unsafe = 0
        try {
            openStreamed(archive, format, null).use { stream ->
                while (true) {
                    val raw = stream.nextEntry ?: break
                    val path = ArchivePaths.normalize(raw.name ?: "")
                    if (path == null) {
                        unsafe++
                        continue
                    }
                    if (path.isEmpty()) continue
                    val modified = raw.lastModifiedDate?.time ?: 0L
                    entries.add(ArchiveEntryInfo(path, raw.isDirectory, if (raw.isDirectory) -1L else raw.size, -1L, modified, false))
                }
            }
        } catch (e: IOException) {
            throw ArchiveException(ArchiveException.Kind.CORRUPT, "Couldn't read this archive: ${e.message ?: "it may be damaged."}", e)
        }
        return ArchiveListing(format, entries, false, unsafe)
    }

    /** True when the archive can't be read without a password. Other problems are left for the real operation to report. */
    suspend fun needsPassword(archive: File): Boolean = withContext(Dispatchers.IO) {
        try {
            list(archive, null).encrypted
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: ArchiveException) {
            e.kind == ArchiveException.Kind.PASSWORD_REQUIRED
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Password check (ZIP only; the only listed format where a wrong password can be told apart early)
    // ---------------------------------------------------------------------------------------------

    /** True when [password] opens the first encrypted file of a ZIP. Other formats return true (nothing to check). */
    suspend fun checkZipPassword(archive: File, password: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipFile(archive, password).use { zip ->
                val headers: List<FileHeader> = zip.fileHeaders ?: emptyList()
                val first = headers.firstOrNull { it.isEncrypted && !it.isDirectory } ?: return@withContext true
                zip.getInputStream(first).use { input ->
                    val probe = ByteArray(16)
                    input.read(probe)
                }
            }
            true
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) false else throw mapZip(e, encrypted = true)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Extraction and testing
    // ---------------------------------------------------------------------------------------------

    /**
     * Extracts [selection] (entry paths or folder paths; null = everything) into [destDir].
     * Throws [ArchiveException] for password problems and unreadable archives.
     */
    suspend fun extract(
        archive: File,
        destDir: File,
        selection: Set<String>?,
        password: CharArray?,
        resolution: ConflictResolution,
        listener: ProgressListener = NoProgress,
    ): OpOutcome = withContext(Dispatchers.IO) {
        val format = requireFormat(archive)
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw ArchiveException(ArchiveException.Kind.IO, "Couldn't create the destination folder.")
        }
        if (!destDir.isDirectory) throw ArchiveException(ArchiveException.Kind.IO, "The destination is not a folder.")

        val failures = ArrayList<ItemFailure>()
        var completed = 0
        var skipped = 0
        val buffer = ByteArray(bufferSize)
        val countsOutput = format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z

        prepareTotals(archive, format, password, selection, listener)

        walk(archive, format, password, if (countsOutput) null else listener) { entry ->
            currentCoroutineContext().ensureActive()
            val info = entry.info
            if (selection != null && !ArchiveTree.isSelected(info.path, selection)) return@walk
            listener.onCurrent(info.path)
            if (entry.skipReason != null) {
                skipped++
                failures.add(ItemFailure(info.path, entry.skipReason))
                listener.onItems(1)
                return@walk
            }
            val target = ArchivePaths.resolve(destDir, info.path)
            if (target == null) {
                skipped++
                failures.add(ItemFailure(info.path, "Skipped: this path would leave the destination folder."))
                listener.onItems(1)
                return@walk
            }
            try {
                if (info.isDirectory) {
                    if (target.existsNoFollow() && !target.isDirectory) {
                        failures.add(ItemFailure(info.path, "A file with this name already exists where a folder is needed."))
                    } else if (!target.isDirectory && !target.mkdirs()) {
                        failures.add(ItemFailure(info.path, "Couldn't create this folder."))
                    } else {
                        completed++
                    }
                } else {
                    val resolved = resolveConflict(target, resolution)
                    if (resolved == null) {
                        skipped++
                    } else {
                        val parent = resolved.parentFile
                        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                            failures.add(ItemFailure(info.path, "Couldn't create the folder for this file."))
                        } else {
                            writeFile(resolved, entry.stream!!, buffer, if (countsOutput) listener else null)
                            completed++
                        }
                    }
                }
            } catch (e: ArchiveException) {
                throw e
            } catch (e: IOException) {
                if (isOutOfSpace(e)) {
                    throw ArchiveException(ArchiveException.Kind.IO, "The destination ran out of free space.", e)
                }
                failures.add(ItemFailure(info.path, e.message ?: "Couldn't write this file."))
            }
            listener.onItems(1)
        }
        OpOutcome(completed, skipped, failures)
    }

    /** Reads every entry completely without writing anything, so CRC and format errors surface. */
    suspend fun test(archive: File, password: CharArray?, listener: ProgressListener = NoProgress): OpOutcome =
        withContext(Dispatchers.IO) {
            val format = requireFormat(archive)
            val failures = ArrayList<ItemFailure>()
            var checked = 0
            val buffer = ByteArray(bufferSize)
            val countsOutput = format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z

            prepareTotals(archive, format, password, null, listener)

            walk(archive, format, password, if (countsOutput) null else listener) { entry ->
                currentCoroutineContext().ensureActive()
                listener.onCurrent(entry.info.path)
                if (entry.skipReason != null) {
                    failures.add(ItemFailure(entry.info.path, entry.skipReason))
                } else if (!entry.info.isDirectory) {
                    val stream = entry.stream!!
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = stream.read(buffer)
                        if (n < 0) break
                        if (countsOutput) listener.onBytes(n.toLong())
                    }
                    checked++
                } else {
                    checked++
                }
                listener.onItems(1)
            }
            OpOutcome(checked, 0, failures)
        }

    private fun prepareTotals(
        archive: File,
        format: ArchiveFormat,
        password: CharArray?,
        selection: Set<String>?,
        listener: ProgressListener,
    ) {
        when (format) {
            ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z -> {
                val l = if (format == ArchiveFormat.ZIP) listZip(archive, format) else list7z(archive, format, password)
                var bytes = 0L
                var items = 0L
                var unknown = false
                for (e in l.entries) {
                    if (selection != null && !ArchiveTree.isSelected(e.path, selection)) continue
                    items++
                    if (!e.isDirectory) {
                        if (e.size < 0) unknown = true else bytes += e.size
                    }
                }
                listener.onTotals(if (unknown) -1L else bytes, items)
            }
            else -> listener.onTotals(archive.length(), -1L)
        }
    }

    private fun resolveConflict(target: File, resolution: ConflictResolution): File? {
        if (!target.existsNoFollow()) return target
        return when (resolution) {
            ConflictResolution.SKIP -> null
            ConflictResolution.KEEP_BOTH -> FileNames.uniqueFile(target.parentFile ?: target, target.name, false)
            ConflictResolution.REPLACE -> if (target.isDirectory && !target.isSymlink()) {
                throw IOException("A folder with this name already exists where a file is needed.")
            } else {
                target
            }
        }
    }

    private suspend fun writeFile(target: File, input: InputStream, buffer: ByteArray, listener: ProgressListener?) {
        val dir = target.parentFile ?: throw IOException("Invalid destination.")
        val temp = File(dir, ".nx-${System.nanoTime()}.nexus-part")
        var finished = false
        try {
            FileOutputStream(temp).use { out ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    listener?.onBytes(n.toLong())
                }
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            finished = true
        } finally {
            if (!finished) temp.delete()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Walking entries with data
    // ---------------------------------------------------------------------------------------------

    private suspend fun walk(
        archive: File,
        format: ArchiveFormat,
        password: CharArray?,
        countingListener: ProgressListener?,
        visit: suspend (EntryData) -> Unit,
    ) {
        try {
            when (format) {
                ArchiveFormat.ZIP -> walkZip(archive, password, visit)
                ArchiveFormat.SEVEN_Z -> walk7z(archive, password, visit)
                ArchiveFormat.GZ, ArchiveFormat.BZ2, ArchiveFormat.XZ -> walkSingle(archive, format, countingListener, visit)
                else -> walkStreamed(archive, format, countingListener, visit)
            }
        } catch (e: ZipException) {
            throw mapZip(e, encrypted = password != null)
        } catch (e: PasswordRequiredException) {
            throw ArchiveException(ArchiveException.Kind.PASSWORD_REQUIRED, "This archive is password protected.", e)
        } catch (e: ArchiveException) {
            throw e
        } catch (e: IOException) {
            if (isOutOfSpace(e)) throw e
            if (format == ArchiveFormat.SEVEN_Z) throw map7z(e, password != null)
            throw ArchiveException(ArchiveException.Kind.CORRUPT, "Couldn't read this archive: ${e.message ?: "it may be damaged."}", e)
        }
    }

    private suspend fun walkZip(archive: File, password: CharArray?, visit: suspend (EntryData) -> Unit) {
        val zip = if (password != null) ZipFile(archive, password) else ZipFile(archive)
        zip.use {
            if (!zip.isValidZipFile) {
                throw ArchiveException(ArchiveException.Kind.CORRUPT, "This isn't a valid ZIP archive, or it is damaged.")
            }
            val headers: List<FileHeader> = zip.fileHeaders ?: emptyList()
            for (h in headers) {
                currentCoroutineContext().ensureActive()
                val path = ArchivePaths.normalize(h.fileName)
                if (path == null) {
                    val info = ArchiveEntryInfo(h.fileName, h.isDirectory, -1L, -1L, 0L, h.isEncrypted)
                    visit(EntryData(info, null, "Skipped: unsafe path in archive."))
                    continue
                }
                if (path.isEmpty()) continue
                val info = ArchiveEntryInfo(path, h.isDirectory, h.uncompressedSize, h.compressedSize, h.lastModifiedTimeEpoch, h.isEncrypted)
                if (h.isDirectory) {
                    visit(EntryData(info, null, null))
                    continue
                }
                if (h.isEncrypted && password == null) {
                    throw ArchiveException(ArchiveException.Kind.PASSWORD_REQUIRED, "This archive is password protected.")
                }
                zip.getInputStream(h).use { stream ->
                    visit(EntryData(info, stream, null))
                }
            }
        }
    }

    private suspend fun walk7z(archive: File, password: CharArray?, visit: suspend (EntryData) -> Unit) {
        open7z(archive, password).use { sevenZ ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val e = sevenZ.nextEntry ?: break
                if (e.isAntiItem) continue
                val path = ArchivePaths.normalize(e.name ?: "")
                if (path == null) {
                    val info = ArchiveEntryInfo(e.name ?: "", e.isDirectory, -1L, -1L, 0L, false)
                    visit(EntryData(info, null, "Skipped: unsafe path in archive."))
                    continue
                }
                if (path.isEmpty()) continue
                val modified = if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L
                val info = ArchiveEntryInfo(path, e.isDirectory, if (e.isDirectory) -1L else e.size, -1L, modified, false)
                if (e.isDirectory) {
                    visit(EntryData(info, null, null))
                } else {
                    visit(EntryData(info, SevenZEntryStream(sevenZ), null))
                }
            }
        }
    }

    private suspend fun walkSingle(archive: File, format: ArchiveFormat, listener: ProgressListener?, visit: suspend (EntryData) -> Unit) {
        val counted = CountingInputStream(FileInputStream(archive), listener)
        val buffered = BufferedInputStream(counted, bufferSize)
        val decompressed: InputStream = when (format) {
            ArchiveFormat.GZ -> GzipCompressorInputStream(buffered)
            ArchiveFormat.BZ2 -> BZip2CompressorInputStream(buffered)
            else -> XZCompressorInputStream(buffered)
        }
        decompressed.use { stream ->
            val name = ArchiveFormats.baseName(archive.name)
            val info = ArchiveEntryInfo(name, false, -1L, archive.length(), archive.lastModified(), false)
            visit(EntryData(info, stream, null))
        }
    }

    private suspend fun walkStreamed(archive: File, format: ArchiveFormat, listener: ProgressListener?, visit: suspend (EntryData) -> Unit) {
        openStreamed(archive, format, listener).use { stream ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val raw = stream.nextEntry ?: break
                val rawName = raw.name ?: ""
                val path = ArchivePaths.normalize(rawName)
                if (path == null) {
                    val info = ArchiveEntryInfo(rawName, raw.isDirectory, -1L, -1L, 0L, false)
                    visit(EntryData(info, null, "Skipped: unsafe path in archive."))
                    continue
                }
                if (path.isEmpty()) continue
                val modified = raw.lastModifiedDate?.time ?: 0L
                val info = ArchiveEntryInfo(path, raw.isDirectory, if (raw.isDirectory) -1L else raw.size, -1L, modified, false)
                val isLink = when (raw) {
                    is TarArchiveEntry -> raw.isSymbolicLink || raw.isLink
                    is CpioArchiveEntry -> raw.isSymbolicLink
                    else -> false
                }
                when {
                    isLink -> visit(EntryData(info, null, "Skipped: links inside archives are never created."))
                    raw.isDirectory -> visit(EntryData(info, null, null))
                    else -> visit(EntryData(info, stream, null))
                }
            }
        }
    }

    private fun open7z(archive: File, password: CharArray?): SevenZFile =
        if (password != null) SevenZFile(archive, password) else SevenZFile(archive)

    private fun openStreamed(archive: File, format: ArchiveFormat, listener: ProgressListener?): ArchiveInputStream<*> {
        val counted = CountingInputStream(FileInputStream(archive), listener)
        val raw = BufferedInputStream(counted, bufferSize)
        try {
            return when (format) {
                ArchiveFormat.TAR -> TarArchiveInputStream(raw)
                ArchiveFormat.TAR_GZ -> TarArchiveInputStream(GzipCompressorInputStream(raw))
                ArchiveFormat.TAR_BZ2 -> TarArchiveInputStream(BZip2CompressorInputStream(raw))
                ArchiveFormat.TAR_XZ -> TarArchiveInputStream(XZCompressorInputStream(raw))
                ArchiveFormat.AR, ArchiveFormat.DEB -> ArArchiveInputStream(raw)
                ArchiveFormat.CPIO -> CpioArchiveInputStream(raw)
                else -> throw ArchiveException(ArchiveException.Kind.UNSUPPORTED, "Unsupported format.")
            }
        } catch (t: Throwable) {
            try {
                raw.close()
            } catch (ignored: IOException) {
            }
            throw t
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------------------------------------

    private class Item(val file: File, val name: String, val isDirectory: Boolean)

    /**
     * Creates [output] from [sources]. The caller must pass a name that does not exist yet
     * (see [FileNames.uniqueFile]); the archive is written to a temp file and renamed at the end.
     */
    suspend fun create(
        sources: List<File>,
        output: File,
        format: ArchiveFormat,
        level: CompressionLevel,
        password: CharArray?,
        listener: ProgressListener = NoProgress,
    ): OpOutcome = withContext(Dispatchers.IO) {
        if (!ArchiveFormats.capabilities(format).create) {
            throw ArchiveException(ArchiveException.Kind.UNSUPPORTED, "Creating ${format.label} archives isn't supported.")
        }
        if (password != null && !ArchiveFormats.capabilities(format).passwordCreate) {
            throw ArchiveException(ArchiveException.Kind.UNSUPPORTED, "${format.label} archives can't be password protected here.")
        }
        if (output.exists()) throw ArchiveException(ArchiveException.Kind.IO, "An item named \"${output.name}\" already exists.")
        val outDir = output.absoluteFile.parentFile ?: throw ArchiveException(ArchiveException.Kind.IO, "Invalid output location.")
        if (!outDir.isDirectory) throw ArchiveException(ArchiveException.Kind.IO, "The output folder doesn't exist.")

        val failures = ArrayList<ItemFailure>()
        var skipped = 0
        val items = ArrayList<Item>()
        var totalBytes = 0L
        val outputCanonical = try {
            output.canonicalFile
        } catch (e: IOException) {
            output.absoluteFile
        }

        // Gather the file list first so totals are real and symlinks are reported before any writing.
        val stack = ArrayDeque<Item>()
        for (src in sources.reversed()) {
            if (!src.exists()) {
                failures.add(ItemFailure(src.path, "The source no longer exists."))
                continue
            }
            stack.addLast(Item(src, src.name, src.isDirectory))
        }
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val item = stack.removeLast()
            if (item.file.isSymlink()) {
                skipped++
                failures.add(ItemFailure(item.file.path, "Skipped: symbolic links are not followed."))
                continue
            }
            val canonical = try {
                item.file.canonicalFile
            } catch (e: IOException) {
                item.file.absoluteFile
            }
            if (canonical == outputCanonical) continue
            items.add(item)
            if (item.isDirectory) {
                val children = item.file.listFiles()
                if (children == null) {
                    failures.add(ItemFailure(item.file.path, "Couldn't read this folder."))
                } else {
                    for (child in children.sortedByDescending { it.name }) {
                        stack.addLast(Item(child, item.name + "/" + child.name, child.isDirectory))
                    }
                }
            } else {
                totalBytes += item.file.length()
            }
        }
        if (items.isEmpty()) {
            throw ArchiveException(ArchiveException.Kind.IO, "There is nothing to add to the archive.")
        }
        if (ArchiveFormats.isSingleStream(format)) {
            if (items.size != 1 || items[0].isDirectory) {
                throw ArchiveException(ArchiveException.Kind.UNSUPPORTED, "${format.label} compresses exactly one file. Choose ZIP, 7z or TAR for several files or folders.")
            }
        }
        listener.onTotals(totalBytes, items.size.toLong())

        val temp = File(outDir, ".nx-${System.nanoTime()}.nexus-part")
        var finished = false
        try {
            when (format) {
                ArchiveFormat.ZIP -> createZip(items, temp, level, password, listener)
                ArchiveFormat.SEVEN_Z -> create7z(items, temp, listener)
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ ->
                    createTar(items, temp, format, level, listener)
                ArchiveFormat.GZ, ArchiveFormat.BZ2, ArchiveFormat.XZ -> createSingle(items[0], temp, format, level, listener)
                else -> throw ArchiveException(ArchiveException.Kind.UNSUPPORTED, "Unsupported format.")
            }
            if (!temp.isFile) throw ArchiveException(ArchiveException.Kind.IO, "The archive could not be written.")
            if (output.exists()) throw ArchiveException(ArchiveException.Kind.IO, "An item named \"${output.name}\" appeared while the archive was being created.")
            Files.move(temp.toPath(), output.toPath())
            if (!output.isFile) throw ArchiveException(ArchiveException.Kind.IO, "The archive could not be saved.")
            finished = true
        } catch (e: ArchiveException) {
            throw e
        } catch (e: IOException) {
            if (isOutOfSpace(e)) throw ArchiveException(ArchiveException.Kind.IO, "Not enough free space to create the archive.", e)
            throw ArchiveException(ArchiveException.Kind.IO, e.message ?: "The archive could not be created.", e)
        } catch (e: OutOfMemoryError) {
            throw ArchiveException(ArchiveException.Kind.IO, "Not enough memory for this compression level. Try a lower level.")
        } finally {
            if (!finished) temp.delete()
        }
        OpOutcome(items.size, skipped, failures)
    }

    private suspend fun copyInto(item: Item, out: OutputStream, buffer: ByteArray, listener: ProgressListener) {
        FileInputStream(item.file).use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                listener.onBytes(n.toLong())
            }
        }
    }

    private suspend fun createZip(items: List<Item>, temp: File, level: CompressionLevel, password: CharArray?, listener: ProgressListener) {
        val buffer = ByteArray(bufferSize)
        val fileOut = BufferedOutputStream(FileOutputStream(temp), bufferSize)
        val zipOut = if (password != null) ZipOutputStream(fileOut, password) else ZipOutputStream(fileOut)
        zipOut.use {
            for (item in items) {
                currentCoroutineContext().ensureActive()
                listener.onCurrent(item.name)
                val params = ZipParameters()
                params.lastModifiedFileTime = item.file.lastModified()
                if (item.isDirectory) {
                    params.fileNameInZip = item.name + "/"
                    params.compressionMethod = CompressionMethod.STORE
                    params.isEncryptFiles = false
                    zipOut.putNextEntry(params)
                    zipOut.closeEntry()
                } else {
                    params.fileNameInZip = item.name
                    params.entrySize = item.file.length()
                    when (level) {
                        CompressionLevel.STORE -> params.compressionMethod = CompressionMethod.STORE
                        CompressionLevel.FAST -> {
                            params.compressionMethod = CompressionMethod.DEFLATE
                            params.compressionLevel = net.lingala.zip4j.model.enums.CompressionLevel.FASTEST
                        }
                        CompressionLevel.NORMAL -> {
                            params.compressionMethod = CompressionMethod.DEFLATE
                            params.compressionLevel = net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
                        }
                        CompressionLevel.MAXIMUM -> {
                            params.compressionMethod = CompressionMethod.DEFLATE
                            params.compressionLevel = net.lingala.zip4j.model.enums.CompressionLevel.MAXIMUM
                        }
                    }
                    if (password != null) {
                        params.isEncryptFiles = true
                        params.encryptionMethod = EncryptionMethod.AES
                        params.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }
                    zipOut.putNextEntry(params)
                    copyInto(item, zipOut, buffer, listener)
                    zipOut.closeEntry()
                }
                listener.onItems(1)
            }
        }
    }

    private suspend fun create7z(items: List<Item>, temp: File, listener: ProgressListener) {
        val buffer = ByteArray(bufferSize)
        SevenZOutputFile(temp).use { out ->
            for (item in items) {
                currentCoroutineContext().ensureActive()
                listener.onCurrent(item.name)
                val entry = out.createArchiveEntry(item.file, item.name)
                out.putArchiveEntry(entry)
                if (!item.isDirectory) {
                    FileInputStream(item.file).use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            listener.onBytes(n.toLong())
                        }
                    }
                }
                out.closeArchiveEntry()
                listener.onItems(1)
            }
            out.finish()
        }
    }

    private suspend fun createTar(items: List<Item>, temp: File, format: ArchiveFormat, level: CompressionLevel, listener: ProgressListener) {
        val buffer = ByteArray(bufferSize)
        val raw = BufferedOutputStream(FileOutputStream(temp), bufferSize)
        val compressed: OutputStream = when (format) {
            ArchiveFormat.TAR_GZ -> {
                val params = GzipParameters()
                params.compressionLevel = when (level) {
                    CompressionLevel.FAST -> 1
                    CompressionLevel.MAXIMUM -> 9
                    else -> 6
                }
                GzipCompressorOutputStream(raw, params)
            }
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorOutputStream(raw)
            ArchiveFormat.TAR_XZ -> XZCompressorOutputStream(raw, xzPreset(level))
            else -> raw
        }
        TarArchiveOutputStream(compressed).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            for (item in items) {
                currentCoroutineContext().ensureActive()
                listener.onCurrent(item.name)
                val entryName = if (item.isDirectory) item.name + "/" else item.name
                val entry = TarArchiveEntry(item.file, entryName)
                tar.putArchiveEntry(entry)
                if (!item.isDirectory) copyInto(item, tar, buffer, listener)
                tar.closeArchiveEntry()
                listener.onItems(1)
            }
            tar.finish()
        }
    }

    private suspend fun createSingle(item: Item, temp: File, format: ArchiveFormat, level: CompressionLevel, listener: ProgressListener) {
        val buffer = ByteArray(bufferSize)
        val raw = BufferedOutputStream(FileOutputStream(temp), bufferSize)
        val compressed: OutputStream = when (format) {
            ArchiveFormat.GZ -> {
                val params = GzipParameters()
                params.compressionLevel = when (level) {
                    CompressionLevel.FAST -> 1
                    CompressionLevel.MAXIMUM -> 9
                    else -> 6
                }
                GzipCompressorOutputStream(raw, params)
            }
            ArchiveFormat.BZ2 -> BZip2CompressorOutputStream(raw)
            else -> XZCompressorOutputStream(raw, xzPreset(level))
        }
        compressed.use { out ->
            listener.onCurrent(item.name)
            copyInto(item, out, buffer, listener)
            listener.onItems(1)
        }
    }

    /** XZ presets above 4 need hundreds of MB of RAM to compress, which is unsafe on phones. */
    private fun xzPreset(level: CompressionLevel): Int = when (level) {
        CompressionLevel.FAST -> 1
        CompressionLevel.MAXIMUM -> 4
        else -> 3
    }

    // ---------------------------------------------------------------------------------------------
    // Error mapping
    // ---------------------------------------------------------------------------------------------

    private fun isOutOfSpace(e: IOException): Boolean {
        val m = e.message ?: return false
        return m.contains("No space left", ignoreCase = true) || m.contains("ENOSPC")
    }

    private fun mapZip(e: ZipException, encrypted: Boolean): ArchiveException = when (e.type) {
        ZipException.Type.WRONG_PASSWORD ->
            ArchiveException(ArchiveException.Kind.WRONG_PASSWORD, "Wrong password.", e)
        ZipException.Type.CHECKSUM_MISMATCH ->
            if (encrypted) {
                ArchiveException(ArchiveException.Kind.WRONG_PASSWORD, "Wrong password, or the data is corrupt (checksum mismatch).", e)
            } else {
                ArchiveException(ArchiveException.Kind.CORRUPT, "A file failed its checksum. The archive is damaged.", e)
            }
        else -> ArchiveException(ArchiveException.Kind.CORRUPT, "Couldn't read this ZIP archive: ${e.message ?: "it may be damaged."}", e)
    }

    private fun map7z(e: IOException, hadPassword: Boolean): ArchiveException =
        if (hadPassword) {
            ArchiveException(
                ArchiveException.Kind.WRONG_PASSWORD,
                "Couldn't read this 7z archive. The password may be wrong, or the archive may be damaged.",
                e,
            )
        } else {
            ArchiveException(ArchiveException.Kind.CORRUPT, "Couldn't read this 7z archive: ${e.message ?: "it may be damaged."}", e)
        }
}
