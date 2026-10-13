package com.nexusarchive.android.core

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

object FileNames {
    private const val MAX_NAME_BYTES = 255
    private val doubleExtensions = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst", ".tar.lz4", ".tar.lzma", ".tar.z")

    /** Returns a user-facing error, or null when [name] is acceptable as a single path component. */
    fun validate(name: String): String? {
        if (name.isBlank()) return "Name cannot be empty."
        if (name == "." || name == "..") return "That name is reserved."
        if (name.contains('/') || name.contains('\u0000')) return "Names cannot contain \"/\"."
        if (name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES) return "Name is too long."
        return null
    }

    /** Splits "photo.jpg" into ("photo", ".jpg"); folders and dotfiles have no extension part. */
    fun splitName(name: String, isDirectory: Boolean): Pair<String, String> {
        if (isDirectory) return name to ""
        val lower = name.lowercase()
        for (ext in doubleExtensions) {
            if (lower.endsWith(ext) && name.length > ext.length) {
                return name.substring(0, name.length - ext.length) to name.substring(name.length - ext.length)
            }
        }
        val idx = name.lastIndexOf('.')
        return if (idx <= 0 || idx == name.length - 1) name to "" else name.substring(0, idx) to name.substring(idx)
    }

    /** First non-existing "name (n).ext" candidate in [dir]; returns the plain name if it is free. */
    fun uniqueFile(dir: File, name: String, isDirectory: Boolean): File {
        val plain = File(dir, name)
        if (!plain.existsNoFollow()) return plain
        val (base, ext) = splitName(name, isDirectory)
        var n = 1
        while (true) {
            val candidate = File(dir, "$base ($n)$ext")
            if (!candidate.existsNoFollow()) return candidate
            n++
        }
    }
}

internal fun File.existsNoFollow(): Boolean =
    try {
        Files.exists(toPath(), LinkOption.NOFOLLOW_LINKS)
    } catch (e: Exception) {
        exists()
    }

internal fun File.isSymlink(): Boolean =
    try {
        Files.isSymbolicLink(toPath())
    } catch (e: Exception) {
        false
    }
