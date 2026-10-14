package com.nexusarchive.android.archive

enum class ArchiveSortField { NAME, SIZE, DATE, TYPE }

/** Builds the folder view of a flat entry list. Pure and unit-tested. */
object ArchiveTree {

    /** Folders always stay first; [field] and [ascending] order the rest. */
    fun sorted(nodes: List<TreeNode>, field: ArchiveSortField, ascending: Boolean): List<TreeNode> {
        val byField: Comparator<TreeNode> = when (field) {
            ArchiveSortField.NAME -> compareBy<TreeNode> { it.name.lowercase() }
            ArchiveSortField.SIZE -> compareBy<TreeNode> { it.size }
            ArchiveSortField.DATE -> compareBy<TreeNode> { it.modifiedMs }
            ArchiveSortField.TYPE -> compareBy<TreeNode> { it.name.substringAfterLast('.', "").lowercase() }
                .thenBy { it.name.lowercase() }
        }
        val ordered: Comparator<TreeNode> = if (ascending) byField else byField.reversed()
        return nodes.sortedWith(compareByDescending<TreeNode> { it.isDirectory }.then(ordered))
    }

    /** Every entry anywhere in the archive whose own name contains [query] (case-insensitive). */
    fun search(entries: List<ArchiveEntryInfo>, query: String): List<TreeNode> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return entries
            .filter { it.path.substringAfterLast('/').lowercase().contains(q) }
            .map { TreeNode(it.path.substringAfterLast('/'), it.path, it.isDirectory, it.size, it.modifiedMs, it.encrypted) }
    }


    /** Immediate children of [dir] ("" is the archive root). Folders first, then files, each sorted by name. */
    fun children(entries: List<ArchiveEntryInfo>, dir: String): List<TreeNode> {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val nodes = LinkedHashMap<String, TreeNode>()
        for (entry in entries) {
            if (!entry.path.startsWith(prefix)) continue
            val rest = entry.path.substring(prefix.length)
            if (rest.isEmpty()) continue
            val slash = rest.indexOf('/')
            if (slash < 0) {
                nodes[rest] = TreeNode(rest, entry.path, entry.isDirectory, entry.size, entry.modifiedMs, entry.encrypted)
            } else {
                val name = rest.substring(0, slash)
                if (!nodes.containsKey(name)) {
                    nodes[name] = TreeNode(name, prefix + name, true, -1L, 0L, false)
                }
            }
        }
        return nodes.values.sortedWith(
            compareByDescending<TreeNode> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
    }

    /** True when [path] equals one of [selection] or lies inside a selected folder. */
    fun isSelected(path: String, selection: Set<String>): Boolean {
        for (s in selection) {
            if (path == s || path.startsWith("$s/")) return true
        }
        return false
    }
}
