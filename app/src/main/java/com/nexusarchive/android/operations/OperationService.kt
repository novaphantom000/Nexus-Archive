package com.nexusarchive.android.operations

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nexusarchive.android.NexusApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that exists only while an operation is running. It keeps Android from killing the
 * process mid-copy, shows real progress, and offers Cancel. The operations themselves still run in
 * [OperationManager]; this service only observes them.
 */
class OperationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private val seenActive = HashSet<Long>()

    @Volatile
    private var detailed = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        OperationNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as NexusApplication
        if (intent?.action == ACTION_PAUSE || intent?.action == ACTION_RESUME) {
            val id = intent.getLongExtra(EXTRA_ID, -1L)
            if (id >= 0) {
                if (intent.action == ACTION_PAUSE) app.operations.pause(id) else app.operations.resume(id)
            }
        }
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getLongExtra(EXTRA_ID, -1L)
            if (id >= 0) {
                app.operations.cancel(id)
            } else {
                app.operations.operations.value.filter { it.isActive }.forEach { app.operations.cancel(it.id) }
            }
        }
        val active = app.operations.operations.value.filter { it.isActive }
        ServiceCompat.startForeground(
            this,
            OperationNotifications.FOREGROUND_ID,
            OperationNotifications.progress(this, active, detailed),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (watcher == null) {
            watcher = scope.launch {
                launch { app.settings.settings.collect { detailed = it.notifications } }
                app.operations.operations.collect { list ->
                    val nowActive = list.filter { it.isActive }
                    for (op in list) {
                        if (op.isActive) {
                            seenActive.add(op.id)
                        } else if (seenActive.remove(op.id) && detailed) {
                            OperationNotifications.postResult(this@OperationService, op)
                        }
                    }
                    if (nowActive.isEmpty()) {
                        shutdown()
                    } else {
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(
                            OperationNotifications.FOREGROUND_ID,
                            OperationNotifications.progress(this@OperationService, nowActive, detailed),
                        )
                        delay(400)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 limits how long a data-sync foreground service may run; stop cleanly when told to. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        shutdown()
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        watcher?.cancel()
        watcher = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.nexusarchive.android.action.CANCEL_OPERATION"
        const val ACTION_PAUSE = "com.nexusarchive.android.action.PAUSE_OPERATION"
        const val ACTION_RESUME = "com.nexusarchive.android.action.RESUME_OPERATION"
        const val EXTRA_ID = "operation_id"

        /** Starts the service from the app. If Android refuses a background start, work simply continues unprotected. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, OperationService::class.java))
            } catch (e: Exception) {
                // Not allowed right now; the operation itself is unaffected.
            }
        }
    }
}
