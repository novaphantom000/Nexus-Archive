package com.nexusarchive.android.ui.browse

import com.nexusarchive.android.core.FileEntry
import com.nexusarchive.android.core.FilterKind
import com.nexusarchive.android.core.ListResult
import com.nexusarchive.android.core.StorageVolumeInfo

data class Breadcrumb(val label: String, val path: String)

enum class ClipboardMode { COPY, CUT }

data class FileClipboard(val paths: List<String>, val mode: ClipboardMode)

data class PendingPaste(val clipboard: FileClipboard, val destination: String, val conflictNames: List<String>)

/** Raw result of listing one folder (unsorted, unfiltered). [failure] is set when the folder couldn't be read. */
data class Listing(val path: String, val entries: List<FileEntry>, val failure: ListResult.Failure?)

/** What the screen draws: the listing plus the sorted/filtered/searched rows. */
data class BrowserView(val listing: Listing, val visible: List<FileEntry>)

data class BrowserUiState(
    val currentPath: String = "",
    val loading: Boolean = true,
    val selection: Set<String> = emptySet(),
    val clipboard: FileClipboard? = null,
    val searchActive: Boolean = false,
    val query: String = "",
    val filter: FilterKind = FilterKind.ALL,
    val canWriteHere: Boolean = false,
    val pendingPaste: PendingPaste? = null,
    val pendingDelete: List<String>? = null,
    val volumes: List<StorageVolumeInfo> = emptyList(),
)

/** The volume whose root contains [path], preferring the deepest match. */
fun volumeFor(path: String, volumes: List<StorageVolumeInfo>): StorageVolumeInfo? =
    volumes
        .filter { path == it.path || path.startsWith(it.path.trimEnd('/') + "/") }
        .maxByOrNull { it.path.length }

fun buildBreadcrumbs(path: String, volumes: List<StorageVolumeInfo>): List<Breadcrumb> {
    val volume = volumeFor(path, volumes)
    val rootPath = volume?.path ?: "/"
    val rootLabel = volume?.label ?: "Device"
    val crumbs = mutableListOf(Breadcrumb(rootLabel, rootPath))
    val relative = path.removePrefix(rootPath).trim('/')
    if (relative.isNotEmpty()) {
        var acc = rootPath.trimEnd('/')
        for (segment in relative.split('/')) {
            acc = "$acc/$segment"
            crumbs.add(Breadcrumb(segment, acc))
        }
    }
    return crumbs
}

/** Parent folder to navigate "up" to, or null when [path] is a storage root (or "/"). */
fun parentOf(path: String, volumes: List<StorageVolumeInfo>): String? {
    if (path == "/" || volumes.any { it.path == path }) return null
    return java.io.File(path).parent
}
