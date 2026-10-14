package com.nexusarchive.android.ui.browse

import android.content.Context
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.archive.ArchiveEngine
import com.nexusarchive.android.archive.ArchiveFormats
import com.nexusarchive.android.archive.ArchiveJobs
import com.nexusarchive.android.core.ConflictResolution
import java.io.File

/**
 * One-tap archive actions from the file browser. If the archive is password protected they hand over to the
 * archive screen (which has the password prompt) instead of failing.
 */
object ArchiveQuickActions {
    private val engine = ArchiveEngine()

    suspend fun extract(
        context: Context,
        archive: File,
        intoFolderNamedAfterArchive: Boolean,
        resolution: ConflictResolution,
        onMessage: (String) -> Unit,
        onNeedsPassword: () -> Unit,
    ) {
        if (engine.needsPassword(archive)) {
            onMessage("This archive is password protected. Opening it so you can enter the password.")
            onNeedsPassword()
            return
        }
        val parent = archive.parentFile
        if (parent == null) {
            onMessage("This archive has no folder to extract into.")
            return
        }
        val destination = if (intoFolderNamedAfterArchive) File(parent, ArchiveFormats.baseName(archive.name)) else parent
        val ops = (context.applicationContext as NexusApplication).operations
        ArchiveJobs.extract(ops, archive, destination, null, null, resolution)
        onMessage("Extraction started. Progress appears under Operations.")
    }

    suspend fun test(
        context: Context,
        archive: File,
        onMessage: (String) -> Unit,
        onNeedsPassword: () -> Unit,
    ) {
        if (engine.needsPassword(archive)) {
            onMessage("This archive is password protected. Opening it so you can enter the password.")
            onNeedsPassword()
            return
        }
        val ops = (context.applicationContext as NexusApplication).operations
        ArchiveJobs.test(ops, archive, null)
        onMessage("Archive test started. Results appear under Operations.")
    }
}
