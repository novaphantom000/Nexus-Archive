package com.nexusarchive.android.core

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/**
 * A mounted storage volume that Android reports to this app. Capacity fields are null when the
 * system would not give a value; nothing here is estimated.
 */
data class StorageVolumeInfo(
    val id: String,
    val label: String,
    val path: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val isReadOnly: Boolean,
    val totalBytes: Long?,
    val freeBytes: Long?,
)

object StorageVolumes {

    @Suppress("DEPRECATION")
    fun detect(context: Context): List<StorageVolumeInfo> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val result = ArrayList<StorageVolumeInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (volume in sm.storageVolumes) {
                val dir = volume.directory ?: continue
                toInfo(context, volume, dir)?.let { result.add(it) }
            }
        } else {
            // API 26-29: derive volume roots from the app-specific directories Android hands out per volume.
            val roots = LinkedHashMap<String, File>()
            val primary = Environment.getExternalStorageDirectory()
            roots[primary.absolutePath] = primary
            for (d in context.getExternalFilesDirs(null)) {
                if (d == null) continue
                val s = d.absolutePath
                val idx = s.indexOf("/Android/data/")
                if (idx > 0) {
                    val root = File(s.substring(0, idx))
                    roots[root.absolutePath] = root
                }
            }
            for (root in roots.values) {
                toInfo(context, sm.getStorageVolume(root), root)?.let { result.add(it) }
            }
        }
        return result.sortedWith(compareByDescending<StorageVolumeInfo> { it.isPrimary }.thenBy { it.label })
    }

    @Suppress("DEPRECATION")
    private fun toInfo(context: Context, volume: StorageVolume?, dir: File): StorageVolumeInfo? {
        val state = volume?.state ?: Environment.getExternalStorageState(dir)
        if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) return null
        val isPrimary = volume?.isPrimary ?: (dir.absolutePath == Environment.getExternalStorageDirectory().absolutePath)
        val removable = volume?.isRemovable ?: !isPrimary
        val label = if (isPrimary) "Internal storage" else (volume?.getDescription(context) ?: dir.name)
        val id = volume?.uuid ?: if (isPrimary) "primary" else dir.name
        var total: Long? = null
        var free: Long? = null
        try {
            val st = StatFs(dir.absolutePath)
            total = st.totalBytes
            free = st.availableBytes
        } catch (e: Exception) {
            // Capacity not available for this volume.
        }
        return StorageVolumeInfo(
            id = id,
            label = label,
            path = dir.absolutePath.trimEnd('/'),
            isPrimary = isPrimary,
            isRemovable = removable,
            isReadOnly = state == Environment.MEDIA_MOUNTED_READ_ONLY,
            totalBytes = total,
            freeBytes = free,
        )
    }
}
