package com.nexusarchive.android.ui.operations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.operations.OperationState
import kotlinx.coroutines.flow.StateFlow

class OperationsViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = (application as NexusApplication).operations

    val operations: StateFlow<List<OperationState>> = manager.operations

    fun cancel(id: Long) = manager.cancel(id)

    fun pause(id: Long) = manager.pause(id)

    fun resume(id: Long) = manager.resume(id)

    fun clearFinished() = manager.clearFinished()
}
