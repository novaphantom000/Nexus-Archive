package com.nexusarchive.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import com.nexusarchive.android.data.SettingsRepository
import com.nexusarchive.android.operations.OperationManager
import com.nexusarchive.android.operations.OperationService
import com.nexusarchive.android.storage.SafRepository

class NexusApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val operations: OperationManager by lazy { OperationManager() }
    val safStorage: SafRepository by lazy { SafRepository(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Temporary copies made by the archive browser (open, share, copy) are removed on every start.
        appScope.launch(Dispatchers.IO) { File(cacheDir, "archive-open").deleteRecursively() }
        // Run the foreground service whenever at least one operation is active.
        appScope.launch {
            operations.operations
                .map { list -> list.any { it.isActive } }
                .distinctUntilChanged()
                .collect { anyActive -> if (anyActive) OperationService.start(this@NexusApplication) }
        }
    }
}
