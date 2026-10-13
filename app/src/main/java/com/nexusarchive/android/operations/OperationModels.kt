package com.nexusarchive.android.operations

import com.nexusarchive.android.core.ItemFailure

enum class OperationType { COPY, MOVE, DELETE, COMPRESS, EXTRACT, TEST }

enum class OperationStatus { QUEUED, PREPARING, RUNNING, SUCCEEDED, COMPLETED_WITH_ERRORS, FAILED, CANCELLED }

/**
 * Snapshot of one operation. All numbers come from the engine's real counters:
 * -1 means "not known yet" (for example while the source folders are still being measured).
 */
data class OperationState(
    val id: Long,
    val type: OperationType,
    val title: String,
    val sources: List<String>,
    val destination: String?,
    val status: OperationStatus,
    val startedAtMs: Long,
    val finishedAtMs: Long? = null,
    val bytesDone: Long = 0,
    val bytesTotal: Long = -1,
    val itemsDone: Long = 0,
    val itemsTotal: Long = -1,
    val currentItem: String? = null,
    val failures: List<ItemFailure> = emptyList(),
    val skippedCount: Int = 0,
    val message: String? = null,
    val paused: Boolean = false,
) {
    val isActive: Boolean
        get() = status == OperationStatus.QUEUED || status == OperationStatus.PREPARING || status == OperationStatus.RUNNING

    /** 0..1 when the real total is known, otherwise null (UI shows an indeterminate indicator). */
    val fraction: Float?
        get() {
            if (type != OperationType.DELETE && bytesTotal > 0) {
                return (bytesDone.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
            }
            if (itemsTotal > 0) return (itemsDone.toFloat() / itemsTotal.toFloat()).coerceIn(0f, 1f)
            return null
        }

    fun elapsedMs(nowMs: Long): Long = (finishedAtMs ?: nowMs) - startedAtMs

    /** Average bytes/second since start; null when it cannot be measured honestly. */
    fun bytesPerSecond(nowMs: Long): Long? {
        if (type == OperationType.DELETE || bytesDone <= 0) return null
        val elapsed = elapsedMs(nowMs)
        if (elapsed < 500) return null
        return bytesDone * 1000 / elapsed
    }
}
