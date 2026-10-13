package com.nexusarchive.android.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

enum class ListFailureReason { NOT_FOUND, NOT_A_DIRECTORY, ACCESS_DENIED, UNKNOWN }

sealed interface ListResult {
    data class Success(val entries: List<FileEntry>) : ListResult
    data class Failure(val reason: ListFailureReason, val message: String) : ListResult
}

/** Directory listing for real filesystem paths (java.io.File). Android document URIs are NOT handled here. */
object LocalFs {

    fun list(dir: File): ListResult {
        if (!dir.exists()) return ListResult.Failure(ListFailureReason.NOT_FOUND, "This folder no longer exists.")
        if (!dir.isDirectory) return ListResult.Failure(ListFailureReason.NOT_A_DIRECTORY, "This location is not a folder.")
        val children: Array<File>? = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            null
        }
        if (children == null) {
            return if (!dir.canRead()) {
                ListResult.Failure(
                    ListFailureReason.ACCESS_DENIED,
                    "Android did not allow this app to read this folder. It may be protected by the system, or file access has not been granted.",
                )
            } else {
                ListResult.Failure(ListFailureReason.UNKNOWN, "This folder could not be read.")
            }
        }
        val entries = ArrayList<FileEntry>(children.size)
        for (child in children) entries.add(entryFor(child))
        return ListResult.Success(entries)
    }

    fun entryFor(file: File): FileEntry {
        val symlink = file.isSymlink()
        var isDir: Boolean
        var size: Long
        var modified: Long
        try {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            isDir = attrs.isDirectory
            size = if (isDir) -1L else attrs.size()
            modified = attrs.lastModifiedTime().toMillis()
        } catch (e: IOException) {
            // Typically a broken symlink: show it, with unknown size.
            isDir = false
            size = -1L
            modified = file.lastModified()
        } catch (e: RuntimeException) {
            isDir = file.isDirectory
            size = if (isDir) -1L else file.length()
            modified = file.lastModified()
        }
        return FileEntry(
            path = file.absolutePath,
            name = file.name,
            isDirectory = isDir,
            sizeBytes = size,
            lastModified = modified,
            isHidden = file.name.startsWith("."),
            isSymlink = symlink,
        )
    }
}
