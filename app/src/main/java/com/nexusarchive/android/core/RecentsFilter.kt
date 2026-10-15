package com.nexusarchive.android.core

import java.io.File

/**
 * Decides whether a path may appear in Recent files / Recent folders: nothing inside a folder whose name starts
 * with a dot, and nothing inside a folder that contains a ".nomedia" marker (the same rule gallery apps use).
 */
object RecentsFilter {
    fun isAllowed(path: String): Boolean {
        if (path.split('/').any { it.length > 1 && it.startsWith(".") }) return false
        val file = File(path)
        var dir: File? = if (file.isDirectory) file else file.parentFile
        while (dir != null) {
            if (File(dir, ".nomedia").exists()) return false
            dir = dir.parentFile
        }
        return true
    }
}
