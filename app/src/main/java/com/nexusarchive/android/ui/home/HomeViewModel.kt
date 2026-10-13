package com.nexusarchive.android.ui.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.core.StorageVolumeInfo
import com.nexusarchive.android.core.StorageVolumes
import com.nexusarchive.android.data.AppSettings
import com.nexusarchive.android.operations.OperationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class QuickFolder(val label: String, val path: String)

data class HomeUiState(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val quickFolders: List<QuickFolder> = emptyList(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application
    private val nexus = application as NexusApplication

    val settings: StateFlow<AppSettings> =
        nexus.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val operations: StateFlow<List<OperationState>> = nexus.operations.operations

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val volumes = StorageVolumes.detect(app)
                val primary = volumes.firstOrNull { it.isPrimary }
                val quick = if (primary == null) {
                    emptyList()
                } else {
                    STANDARD_FOLDERS.mapNotNull { (label, name) ->
                        val dir = File(primary.path, name)
                        if (dir.isDirectory) QuickFolder(label, dir.absolutePath) else null
                    }
                }
                HomeUiState(volumes, quick)
            }
            _ui.value = state
        }
    }

    fun removeBookmark(path: String) {
        viewModelScope.launch { nexus.settings.removeBookmark(path) }
    }

    private companion object {
        // Standard public folder names on Android shared storage.
        val STANDARD_FOLDERS = listOf(
            "Downloads" to Environment.DIRECTORY_DOWNLOADS,
            "Camera" to Environment.DIRECTORY_DCIM,
            "Pictures" to Environment.DIRECTORY_PICTURES,
            "Documents" to Environment.DIRECTORY_DOCUMENTS,
            "Music" to Environment.DIRECTORY_MUSIC,
            "Movies" to Environment.DIRECTORY_MOVIES,
        )
    }
}
