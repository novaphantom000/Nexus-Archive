package com.nexusarchive.android.archive

import com.nexusarchive.android.core.ConflictResolution
import com.nexusarchive.android.operations.OperationManager
import com.nexusarchive.android.operations.OperationType
import java.io.File

/** Submits archive work to the shared [OperationManager] so it shows real progress and can be cancelled. */
object ArchiveJobs {
    private val engine = ArchiveEngine()

    fun extract(
        ops: OperationManager,
        archive: File,
        destDir: File,
        selection: Set<String>?,
        password: CharArray?,
        resolution: ConflictResolution,
    ): Long {
        val pw = password?.copyOf()
        val what = if (selection == null) "\"${archive.name}\"" else "${selection.size} selected item(s) from \"${archive.name}\""
        return ops.submitCustom(OperationType.EXTRACT, "Extract $what", listOf(archive.path), destDir.path) { listener ->
            try {
                engine.extract(archive, destDir, selection, pw, resolution, listener)
            } finally {
                pw?.fill('\u0000')
            }
        }
    }

    fun test(ops: OperationManager, archive: File, password: CharArray?): Long {
        val pw = password?.copyOf()
        return ops.submitCustom(OperationType.TEST, "Test \"${archive.name}\"", listOf(archive.path), null) { listener ->
            try {
                engine.test(archive, pw, listener)
            } finally {
                pw?.fill('\u0000')
            }
        }
    }

    fun compress(
        ops: OperationManager,
        sources: List<File>,
        output: File,
        format: ArchiveFormat,
        level: CompressionLevel,
        password: CharArray?,
    ): Long {
        val pw = password?.copyOf()
        val what = if (sources.size == 1) "\"${sources[0].name}\"" else "${sources.size} items"
        return ops.submitCustom(OperationType.COMPRESS, "Compress $what to \"${output.name}\"", sources.map { it.path }, output.parent) { listener ->
            try {
                engine.create(sources, output, format, level, pw, listener)
            } finally {
                pw?.fill('\u0000')
            }
        }
    }
}
