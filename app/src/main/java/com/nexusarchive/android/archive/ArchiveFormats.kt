package com.nexusarchive.android.archive

import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** Format registry: detection plus an honest description of what each format supports. */
object ArchiveFormats {

    private val levelsAll = listOf(CompressionLevel.STORE, CompressionLevel.FAST, CompressionLevel.NORMAL, CompressionLevel.MAXIMUM)
    private val levelsStream = listOf(CompressionLevel.FAST, CompressionLevel.NORMAL, CompressionLevel.MAXIMUM)

    fun capabilities(format: ArchiveFormat): FormatCapabilities = when (format) {
        ArchiveFormat.ZIP -> FormatCapabilities(
            browse = true, extract = true, create = true, passwordExtract = true, passwordCreate = true,
            levels = levelsAll, integrityTest = true,
            note = "Password protection uses AES-256 for new archives. Reads AES and classic ZIP encryption. Split archives are not supported.",
        )
        ArchiveFormat.SEVEN_Z -> FormatCapabilities(
            browse = true, extract = true, create = true, passwordExtract = true, passwordCreate = false,
            levels = emptyList(), integrityTest = true,
            note = "New 7z archives use the default LZMA2 settings and cannot be password protected. Multipart 7z is not supported.",
        )
        ArchiveFormat.TAR -> FormatCapabilities(true, true, true, false, false, emptyList(), true, "Not compressed, no encryption.")
        ArchiveFormat.TAR_GZ -> FormatCapabilities(true, true, true, false, false, levelsStream, true, "Gzip-compressed TAR. No encryption.")
        ArchiveFormat.TAR_BZ2 -> FormatCapabilities(true, true, true, false, false, emptyList(), true, "Bzip2-compressed TAR. No encryption.")
        ArchiveFormat.TAR_XZ -> FormatCapabilities(
            true, true, true, false, false, levelsStream, true,
            "XZ-compressed TAR. Levels are capped to keep memory use reasonable on phones.",
        )
        ArchiveFormat.GZ -> FormatCapabilities(true, true, true, false, false, levelsStream, true, "Compresses exactly one file; not a multi-file archive.")
        ArchiveFormat.BZ2 -> FormatCapabilities(true, true, true, false, false, emptyList(), true, "Compresses exactly one file; not a multi-file archive.")
        ArchiveFormat.XZ -> FormatCapabilities(true, true, true, false, false, levelsStream, true, "Compresses exactly one file; not a multi-file archive.")
        ArchiveFormat.AR -> FormatCapabilities(true, true, false, false, false, emptyList(), true, "Read-only.")
        ArchiveFormat.DEB -> FormatCapabilities(true, true, false, false, false, emptyList(), true, "Read-only. Shows the package's outer members (control and data archives).")
        ArchiveFormat.CPIO -> FormatCapabilities(true, true, false, false, false, emptyList(), true, "Read-only.")
    }

    val creatable: List<ArchiveFormat> = listOf(
        ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR, ArchiveFormat.TAR_GZ,
        ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ, ArchiveFormat.GZ, ArchiveFormat.BZ2, ArchiveFormat.XZ,
    )

    /** True for formats that hold exactly one compressed stream instead of several files. */
    fun isSingleStream(format: ArchiveFormat): Boolean =
        format == ArchiveFormat.GZ || format == ArchiveFormat.BZ2 || format == ArchiveFormat.XZ

    fun extensionFor(format: ArchiveFormat): String = when (format) {
        ArchiveFormat.ZIP -> ".zip"
        ArchiveFormat.SEVEN_Z -> ".7z"
        ArchiveFormat.TAR -> ".tar"
        ArchiveFormat.TAR_GZ -> ".tar.gz"
        ArchiveFormat.TAR_BZ2 -> ".tar.bz2"
        ArchiveFormat.TAR_XZ -> ".tar.xz"
        ArchiveFormat.GZ -> ".gz"
        ArchiveFormat.BZ2 -> ".bz2"
        ArchiveFormat.XZ -> ".xz"
        ArchiveFormat.AR -> ".ar"
        ArchiveFormat.DEB -> ".deb"
        ArchiveFormat.CPIO -> ".cpio"
    }

    private val unsupportedNames = mapOf(
        "rar" to "RAR", "iso" to "ISO", "cab" to "CAB", "arj" to "ARJ", "lzh" to "LZH", "lha" to "LHA",
        "z" to "Z", "rpm" to "RPM", "zipx" to "ZIPX", "xar" to "XAR", "wim" to "WIM", "zst" to "Zstandard",
        "lz4" to "LZ4", "lzma" to "LZMA", "lz" to "LZIP", "dmg" to "DMG", "img" to "IMG",
    )

    /** User-facing explanation when the extension names a format this build does not handle, else null. */
    fun unsupportedReason(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val label = unsupportedNames[ext] ?: return null
        return "$label archives aren't supported in this version."
    }

    /** Pure detection from the first bytes (at least 512 if available) plus the file name. */
    fun detect(header: ByteArray, length: Int, fileName: String): ArchiveFormat? {
        val lower = fileName.lowercase()
        fun at(offset: Int, vararg values: Int): Boolean {
            if (length < offset + values.size) return false
            for (i in values.indices) {
                if ((header[offset + i].toInt() and 0xFF) != values[i]) return false
            }
            return true
        }
        val tarGzName = lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
        val tarBz2Name = lower.endsWith(".tar.bz2") || lower.endsWith(".tbz") || lower.endsWith(".tbz2")
        val tarXzName = lower.endsWith(".tar.xz") || lower.endsWith(".txz")
        return when {
            at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) -> ArchiveFormat.ZIP
            at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> ArchiveFormat.SEVEN_Z
            at(0, 0x1F, 0x8B) -> if (tarGzName) ArchiveFormat.TAR_GZ else ArchiveFormat.GZ
            at(0, 0x42, 0x5A, 0x68) -> if (tarBz2Name) ArchiveFormat.TAR_BZ2 else ArchiveFormat.BZ2
            at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> if (tarXzName) ArchiveFormat.TAR_XZ else ArchiveFormat.XZ
            at(0, 0x21, 0x3C, 0x61, 0x72, 0x63, 0x68, 0x3E, 0x0A) -> if (lower.endsWith(".deb")) ArchiveFormat.DEB else ArchiveFormat.AR
            at(257, 0x75, 0x73, 0x74, 0x61, 0x72) -> ArchiveFormat.TAR
            at(0, 0x30, 0x37, 0x30, 0x37, 0x30) || at(0, 0xC7, 0x71) || at(0, 0x71, 0xC7) -> ArchiveFormat.CPIO
            lower.endsWith(".tar") -> ArchiveFormat.TAR
            else -> null
        }
    }

    /** Reads the first bytes of [file] and detects its format. Returns null for unknown content. */
    fun detect(file: File): ArchiveFormat? {
        val buffer = ByteArray(512)
        var read = 0
        try {
            FileInputStream(file).use { input ->
                while (read < buffer.size) {
                    val n = input.read(buffer, read, buffer.size - read)
                    if (n < 0) break
                    read += n
                }
            }
        } catch (e: IOException) {
            return null
        }
        return detect(buffer, read, file.name)
    }

    /** "photos.tar.gz" -> "photos", "a.zip" -> "a". Used for the "extract to folder named after the archive" option. */
    fun baseName(fileName: String): String {
        val lower = fileName.lowercase()
        for (ext in listOf(".tar.gz", ".tar.bz2", ".tar.xz")) {
            if (lower.endsWith(ext) && fileName.length > ext.length) return fileName.substring(0, fileName.length - ext.length)
        }
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0) fileName else fileName.substring(0, dot)
    }
}
