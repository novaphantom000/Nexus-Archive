package com.nexusarchive.android.core

enum class SortField { NAME, SIZE, DATE, TYPE }

enum class FilterKind { ALL, FOLDERS, IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES, APKS }

data class SortConfig(
    val field: SortField = SortField.NAME,
    val ascending: Boolean = true,
    val foldersFirst: Boolean = true,
    val showHidden: Boolean = false,
)

/** Pure filtering + sorting of a directory listing (runs off the main thread in the ViewModel). */
object Arrange {

    fun apply(entries: List<FileEntry>, sort: SortConfig, filter: FilterKind, query: String): List<FileEntry> {
        val q = query.trim()
        val filtered = entries.filter { entry ->
            (sort.showHidden || !entry.isHidden) &&
                matchesFilter(entry, filter) &&
                (q.isEmpty() || entry.name.contains(q, ignoreCase = true))
        }

        val base: Comparator<FileEntry> = when (sort.field) {
            SortField.NAME -> Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }
            SortField.SIZE -> Comparator { a, b -> a.sizeBytes.compareTo(b.sizeBytes) }
            SortField.DATE -> Comparator { a, b -> a.lastModified.compareTo(b.lastModified) }
            SortField.TYPE -> Comparator { a, b -> a.extension.compareTo(b.extension) }
        }
        val directed: Comparator<FileEntry> = if (sort.ascending) base else base.reversed()
        val withTieBreak = Comparator<FileEntry> { a, b ->
            val c = directed.compare(a, b)
            if (c != 0) c else NaturalOrder.compare(a.name, b.name)
        }
        val finalComparator: Comparator<FileEntry> = if (sort.foldersFirst) {
            Comparator { a, b ->
                if (a.isDirectory != b.isDirectory) {
                    if (a.isDirectory) -1 else 1
                } else {
                    withTieBreak.compare(a, b)
                }
            }
        } else {
            withTieBreak
        }
        return filtered.sortedWith(finalComparator)
    }

    /**
     * Type filters keep folders visible (so the user can still navigate) and hide non-matching files.
     * FOLDERS shows folders only.
     */
    fun matchesFilter(entry: FileEntry, filter: FilterKind): Boolean {
        if (filter == FilterKind.ALL) return true
        if (filter == FilterKind.FOLDERS) return entry.isDirectory
        if (entry.isDirectory) return true
        val kind = entry.kind
        return when (filter) {
            FilterKind.IMAGES -> kind == FileKind.IMAGE
            FilterKind.VIDEOS -> kind == FileKind.VIDEO
            FilterKind.AUDIO -> kind == FileKind.AUDIO
            FilterKind.DOCUMENTS -> kind == FileKind.DOCUMENT
            FilterKind.ARCHIVES -> kind == FileKind.ARCHIVE
            FilterKind.APKS -> kind == FileKind.APK
            else -> true
        }
    }
}
