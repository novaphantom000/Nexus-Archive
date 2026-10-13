package com.nexusarchive.android.archive

import java.io.File
import java.io.IOException

/** Path-safety helpers. Every name that comes out of an archive goes through [normalize] and [resolve]. */
object ArchivePaths {

    /**
     * Turns a raw entry name into a safe relative path using '/' separators.
     * Returns "" for harmless root markers ("./", "."), and null when the name is unsafe
     * (absolute path, drive letter, ".." segment, NUL byte).
     */
    fun normalize(raw: String): String? {
        if (raw.indexOf('\u0000') >= 0) return null
        val unified = raw.replace('\\', '/')
        if (unified.startsWith("/")) return null
        if (unified.length >= 2 && unified[1] == ':' && unified[0].isLetter()) return null
        val parts = ArrayList<String>()
        for (segment in unified.split('/')) {
            if (segment.isEmpty() || segment == ".") continue
            if (segment == "..") return null
            parts.add(segment)
        }
        return parts.joinToString("/")
    }

    /**
     * Resolves [relative] under [destDir] and verifies, using canonical paths, that the result is still
     * inside [destDir]. This also rejects paths that pass through a symbolic link pointing outside.
     * Returns null when the target would escape.
     */
    fun resolve(destDir: File, relative: String): File? {
        return try {
            val canonicalDest = destDir.canonicalFile
            val target = File(canonicalDest, relative)
            val canonicalTarget = target.canonicalFile
            val prefix = canonicalDest.path.trimEnd(File.separatorChar) + File.separator
            if (canonicalTarget.path.startsWith(prefix)) target else null
        } catch (e: IOException) {
            null
        }
    }
}
