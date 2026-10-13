package com.nexusarchive.android.operations

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nexusarchive.android.MainActivity
import com.nexusarchive.android.R
import com.nexusarchive.android.core.Formatters

/** Builds the progress and result notifications. Every number shown comes from the operation's real counters. */
object OperationNotifications {
    const val CHANNEL_PROGRESS = "operations_progress"
    const val CHANNEL_RESULT = "operations_result"
    const val FOREGROUND_ID = 1001
    private const val RESULT_BASE = 2000

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val progress = NotificationChannel(CHANNEL_PROGRESS, "Running operations", NotificationManager.IMPORTANCE_LOW)
        progress.description = "Shows progress while files are copied, compressed or extracted."
        progress.setShowBadge(false)
        val result = NotificationChannel(CHANNEL_RESULT, "Finished operations", NotificationManager.IMPORTANCE_DEFAULT)
        result.description = "Tells you when an operation has finished."
        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(result)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun serviceIntent(context: Context, action: String, operationId: Long, requestBase: Int): PendingIntent {
        val intent = Intent(context, OperationService::class.java)
            .setAction(action)
            .putExtra(OperationService.EXTRA_ID, operationId)
        return PendingIntent.getService(
            context,
            requestBase + operationId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun cancelIntent(context: Context, operationId: Long): PendingIntent =
        serviceIntent(context, OperationService.ACTION_CANCEL, operationId, 0)

    fun progress(context: Context, active: List<OperationState>, detailed: Boolean): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_operation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))

        if (active.isEmpty() || !detailed) {
            builder.setContentTitle("Nexus Archive")
                .setContentText(if (active.isEmpty()) "Finishing up\u2026" else "Working in the background")
                .setProgress(0, 0, true)
            if (active.isNotEmpty()) builder.addAction(0, "Cancel", cancelIntent(context, if (active.size == 1) active[0].id else -1L))
            return builder.build()
        }

        val first = active[0]
        if (active.size == 1) {
            builder.setContentTitle(first.title)
            val fraction = first.fraction
            val detail = StringBuilder()
            if (first.paused) detail.append("Paused")
            if (fraction != null) {
                if (detail.isNotEmpty()) detail.append(" \u00B7 ")
                detail.append((fraction * 100).toInt()).append("%")
            }
            if (first.bytesTotal > 0 && first.type != OperationType.DELETE) {
                if (detail.isNotEmpty()) detail.append(" \u00B7 ")
                detail.append(Formatters.bytes(first.bytesDone)).append(" of ").append(Formatters.bytes(first.bytesTotal))
            } else if (first.itemsTotal > 0) {
                if (detail.isNotEmpty()) detail.append(" \u00B7 ")
                detail.append(first.itemsDone).append(" of ").append(first.itemsTotal).append(" items")
            }
            val current = first.currentItem
            if (detail.isEmpty() && current != null) detail.append(current)
            builder.setContentText(if (detail.isEmpty()) "Starting\u2026" else detail.toString())
            if (current != null) builder.setSubText(current)
            if (fraction != null) builder.setProgress(100, (fraction * 100).toInt(), false) else builder.setProgress(0, 0, true)
            if (first.paused) {
                builder.addAction(0, "Resume", serviceIntent(context, OperationService.ACTION_RESUME, first.id, 100000))
            } else {
                builder.addAction(0, "Pause", serviceIntent(context, OperationService.ACTION_PAUSE, first.id, 200000))
            }
            builder.addAction(0, "Cancel", cancelIntent(context, first.id))
        } else {
            builder.setContentTitle("${active.size} operations running")
            builder.setContentText(first.title)
            val fractions = active.map { it.fraction }
            if (fractions.all { it != null }) {
                val average = fractions.map { it ?: 0f }.average().toFloat()
                builder.setProgress(100, (average * 100).toInt(), false)
            } else {
                builder.setProgress(0, 0, true)
            }
            builder.addAction(0, "Cancel all", cancelIntent(context, -1L))
        }
        return builder.build()
    }

    /** Posts a completion notice. Cancelled operations are not announced. */
    fun postResult(context: Context, op: OperationState) {
        if (op.status == OperationStatus.CANCELLED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val headline = when (op.status) {
            OperationStatus.SUCCEEDED -> "Finished"
            OperationStatus.COMPLETED_WITH_ERRORS -> "Finished with problems"
            else -> "Failed"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_operation)
            .setContentTitle("$headline: ${op.title}")
            .setContentText(op.message ?: headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(op.message ?: headline))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        manager.notify(RESULT_BASE + (op.id % 500).toInt(), notification)
    }
}
