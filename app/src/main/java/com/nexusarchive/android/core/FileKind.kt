package com.nexusarchive.android.core

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, CODE, DISK_IMAGE, OTHER }

/** Classifies files by extension only. No content sniffing is done in the base build. */
object FileTypes {
    private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico")
    private val video = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv")
    private val audio = setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma", "amr", "mid", "midi")
    private val document = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
        "txt", "rtf", "md", "csv", "epub",
    )
    private val archive = setOf(
        "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz", "zst", "lz4", "lzma",
        "cab", "arj", "lzh", "lha", "z", "cpio", "deb", "rpm", "zipx", "xar", "wim", "lz",
    )
    private val apk = setOf("apk", "apks", "xapk", "apkm")
    private val code = setOf(
        "kt", "java", "py", "js", "json", "xml", "html", "htm", "css", "c", "cpp", "h", "hpp",
        "sh", "yml", "yaml", "toml", "ini", "gradle", "kts", "rs", "go", "ts", "log",
    )
    private val diskImage = setOf("iso", "img", "dmg")

    /** Lower-case extension without the dot, or "" when there is none (".bashrc" has none). */
    fun extensionOf(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0 || i == name.length - 1) "" else name.substring(i + 1).lowercase()
    }

    fun kindOf(name: String, isDirectory: Boolean): FileKind {
        if (isDirectory) return FileKind.FOLDER
        val ext = extensionOf(name)
        return when (ext) {
            in image -> FileKind.IMAGE
            in video -> FileKind.VIDEO
            in audio -> FileKind.AUDIO
            in document -> FileKind.DOCUMENT
            in archive -> FileKind.ARCHIVE
            in apk -> FileKind.APK
            in code -> FileKind.CODE
            in diskImage -> FileKind.DISK_IMAGE
            else -> FileKind.OTHER
        }
    }
}

/**
 * One row in a directory listing. [sizeBytes] is -1 when unknown (always the case for folders until
 * a size calculation is explicitly requested).
 */
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isHidden: Boolean,
    val isSymlink: Boolean,
) {
    val extension: String get() = if (isDirectory) "" else FileTypes.extensionOf(name)
    val kind: FileKind get() = FileTypes.kindOf(name, isDirectory)
}
