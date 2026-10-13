package com.nexusarchive.android.archive

/** Builds the folder view of a flat entry list. Pure and unit-tested. */
object ArchiveTree {

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
