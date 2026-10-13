package com.nexusarchive.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.core.SortField
import com.nexusarchive.android.data.AppSettings
import com.nexusarchive.android.data.ConflictPolicy
import com.nexusarchive.android.data.LayoutDensity
import com.nexusarchive.android.data.ThemeMode
import com.nexusarchive.android.data.ViewMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** null until DataStore has delivered the first value, so the UI never flashes default settings. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as NexusApplication).settings

    val settings: StateFlow<AppSettings?> = repo.settings
        .map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(v: ThemeMode) = fire { repo.setThemeMode(v) }
    fun setDynamicColor(v: Boolean) = fire { repo.setDynamicColor(v) }
    fun setDensity(v: LayoutDensity) = fire { repo.setDensity(v) }
    fun setViewMode(v: ViewMode) = fire { repo.setViewMode(v) }
    fun setShowHidden(v: Boolean) = fire { repo.setShowHidden(v) }
    fun setSortField(v: SortField) = fire { repo.setSortField(v) }
    fun setSortAscending(v: Boolean) = fire { repo.setSortAscending(v) }
    fun setFoldersFirst(v: Boolean) = fire { repo.setFoldersFirst(v) }
    fun setConfirmDelete(v: Boolean) = fire { repo.setConfirmDelete(v) }
    fun setConflictPolicy(v: ConflictPolicy) = fire { repo.setConflictPolicy(v) }
    fun setReduceMotion(v: Boolean) = fire { repo.setReduceMotion(v) }
    fun setNotifications(v: Boolean) = fire { repo.setNotifications(v) }
    fun setShowRecents(v: Boolean) = fire { repo.setShowRecents(v) }
    fun removeBookmark(path: String) = fire { repo.removeBookmark(path) }
    fun clearRecents() = fire { repo.clearRecents() }

    private fun fire(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
