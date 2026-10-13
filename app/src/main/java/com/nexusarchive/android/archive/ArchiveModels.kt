package com.nexusarchive.android.archive

import java.io.IOException

/** Formats this build can actually read and/or write. Anything not listed here is unsupported. */
enum class ArchiveFormat(val label: String) {
    ZIP("ZIP"),
    SEVEN_Z("7z"),
    TAR("TAR"),
    TAR_GZ("TAR.GZ"),
    TAR_BZ2("TAR.BZ2"),
    TAR_XZ("TAR.XZ"),
    GZ("GZIP"),
    BZ2("BZIP2"),
    XZ("XZ"),
    AR("AR"),
    DEB("DEB"),
    CPIO("CPIO"),
}

enum class CompressionLevel { STORE, FAST, NORMAL, MAXIMUM }

/** What the engine genuinely implements for one format. The UI only offers what is true here. */
data class FormatCapabilities(
    val browse: Boolean,
    val extract: Boolean,
    val create: Boolean,
    val passwordExtract: Boolean,
    val passwordCreate: Boolean,
    val levels: List<CompressionLevel>,
    val integrityTest: Boolean,
    val note: String,
)

/** One entry as reported by the archive itself. [path] is already normalized and safe ('/' separated). */
data class ArchiveEntryInfo(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val modifiedMs: Long,
    val encrypted: Boolean,
)

data class ArchiveListing(
    val format: ArchiveFormat,
    val entries: List<ArchiveEntryInfo>,
    val encrypted: Boolean,
    val unsafeEntryCount: Int,
)

class ArchiveException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind { PASSWORD_REQUIRED, WRONG_PASSWORD, CORRUPT, UNSUPPORTED, IO }
}

/** A row in the archive browser: either a real entry or a folder implied by entry paths. */
data class TreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedMs: Long,
    val encrypted: Boolean,
)
