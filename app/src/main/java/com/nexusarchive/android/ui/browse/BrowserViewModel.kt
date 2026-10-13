package com.nexusarchive.android.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.core.Arrange
import com.nexusarchive.android.core.ConflictResolution
import com.nexusarchive.android.core.FileCommands
import com.nexusarchive.android.core.FileEntry
import com.nexusarchive.android.core.FilterKind
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.core.ListFailureReason
import com.nexusarchive.android.core.ListResult
import com.nexusarchive.android.core.LocalFs
import com.nexusarchive.android.core.StorageVolumes
import com.nexusarchive.android.data.ConflictPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State holder for the file browser. Scoped to the activity so the current folder, selection and
 * clipboard survive switching between Home / Browse / Operations / Settings.
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val settingsRepo = (application as NexusApplication).settings
    private val operations = (application as NexusApplication).operations

    private val _ui = MutableStateFlow(BrowserUiState())
    val ui: StateFlow<BrowserUiState> = _ui.asStateFlow()

    private val _listing = MutableStateFlow<Listing?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val sortConfig = settingsRepo.settings.map { it.sortConfig }.distinctUntilChanged()

    /** Sorting, filtering and searching run on Dispatchers.Default so large folders never block the UI. */
    val view: StateFlow<BrowserView?> = combine(
        _listing,
        _ui.map { it.query to it.filter }.distinctUntilChanged(),
        sortConfig,
    ) { listing, queryAndFilter, sort ->
        if (listing == null) {
            null
        } else {
            BrowserView(listing, Arrange.apply(listing.entries, sort, queryAndFilter.second, queryAndFilter.first))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var started = false
    private var loadJob: Job? = null

    init {
        refreshVolumes()
        viewModelScope.launch {
            operations.changes.collect { refresh() }
        }
    }

    fun postMessage(text: String) {
        _messages.tryEmit(text)
    }

    // ---- navigation ---------------------------------------------------------------------------

    fun refreshVolumes() {
        viewModelScope.launch {
            val volumes = withContext(Dispatchers.IO) { StorageVolumes.detect(app) }
            _ui.update { it.copy(volumes = volumes) }
        }
    }

    /** Called once file access is granted: opens internal storage the first time, or retries a denied folder. */
    fun ensureStarted() {
        if (!started) {
            started = true
            viewModelScope.launch {
                val volumes = withContext(Dispatchers.IO) { StorageVolumes.detect(app) }
                _ui.update { it.copy(volumes = volumes) }
                val start = volumes.firstOrNull { it.isPrimary }?.path ?: volumes.firstOrNull()?.path ?: "/storage/emulated/0"
                load(start)
            }
        } else if (_listing.value?.failure != null) {
            refresh()
        }
    }

    fun navigateTo(path: String) {
        started = true
        _ui.update { it.copy(selection = emptySet(), searchActive = false, query = "") }
        load(path)
    }

    /** Navigates to the parent folder. Returns false when already at a storage root. */
    fun navigateUp(): Boolean {
        val state = _ui.value
        val parent = parentOf(state.currentPath, state.volumes) ?: return false
        navigateTo(parent)
        return true
    }

    fun refresh() {
        val path = _ui.value.currentPath
        if (path.isNotBlank()) load(path)
    }

    private fun load(path: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val dir = File(path)
            val (result, writable) = withContext(Dispatchers.IO) { LocalFs.list(dir) to dir.canWrite() }
            val listing = when (result) {
                is ListResult.Success -> Listing(path, result.entries, null)
                is ListResult.Failure -> Listing(path, emptyList(), result)
            }
            _listing.value = listing
            val existing = listing.entries.mapTo(HashSet()) { it.path }
            _ui.update { s ->
                s.copy(
                    currentPath = path,
                    loading = false,
                    canWriteHere = writable && listing.failure == null,
                    selection = s.selection.filterTo(HashSet()) { it in existing },
                )
            }
            if (result is ListResult.Success) settingsRepo.recordRecentDir(path)
        }
    }

    // ---- search / filter ----------------------------------------------------------------------

    fun setSearchActive(active: Boolean) {
        _ui.update { it.copy(searchActive = active, query = if (active) it.query else "") }
    }

    fun setQuery(query: String) {
        _ui.update { it.copy(query = query) }
    }

    fun setFilter(filter: FilterKind) {
        _ui.update { it.copy(filter = filter) }
    }

    // ---- selection ----------------------------------------------------------------------------

    fun toggleSelect(path: String) {
        _ui.update { s -> s.copy(selection = if (path in s.selection) s.selection - path else s.selection + path) }
    }

    fun clearSelection() {
        _ui.update { it.copy(selection = emptySet()) }
    }

    fun selectAll() {
        val all = view.value?.visible?.map { it.path }?.toSet() ?: return
        _ui.update { it.copy(selection = all) }
    }

    fun invertSelection() {
        val visible = view.value?.visible?.map { it.path } ?: return
        _ui.update { s -> s.copy(selection = visible.filter { it !in s.selection }.toSet()) }
    }

    /** Selected paths in the order they appear on screen. */
    private fun orderedSelection(): List<String> {
        val selection = _ui.value.selection
        val ordered = view.value?.visible?.map { it.path }?.filter { it in selection }
        return if (ordered.isNullOrEmpty()) selection.toList() else ordered
    }

    // ---- clipboard / paste --------------------------------------------------------------------

    fun copySelection() {
        val paths = orderedSelection()
        if (paths.isEmpty()) return
        _ui.update { it.copy(clipboard = FileClipboard(paths, ClipboardMode.COPY), selection = emptySet()) }
        postMessage("${Formatters.plural(paths.size, "item")} ready to copy")
    }

    fun cutSelection() {
        val paths = orderedSelection()
        if (paths.isEmpty()) return
        _ui.update { it.copy(clipboard = FileClipboard(paths, ClipboardMode.CUT), selection = emptySet()) }
        postMessage("${Formatters.plural(paths.size, "item")} ready to move")
    }

    fun clearClipboard() {
        _ui.update { it.copy(clipboard = null) }
    }

    fun paste() {
        val state = _ui.value
        val clip = state.clipboard ?: return
        val dest = state.currentPath
        if (dest.isBlank()) return
        if (!state.canWriteHere) {
            postMessage("This folder is read-only, so nothing can be pasted here.")
            return
        }
        viewModelScope.launch {
            val policy = settingsRepo.current().conflictPolicy
            val sources = if (clip.mode == ClipboardMode.CUT) {
                clip.paths.filter { File(it).parent != dest }
            } else {
                clip.paths
            }
            if (sources.isEmpty()) {
                postMessage("Those items are already in this folder.")
                return@launch
            }
            // Copying within the same folder makes a duplicate, so it is never a conflict.
            val conflicts = withContext(Dispatchers.IO) {
                sources.filter { File(it).parent != dest }.map { File(it).name }.filter { File(dest, it).exists() }
            }
            val effective = clip.copy(paths = sources)
            if (conflicts.isNotEmpty() && policy == ConflictPolicy.ASK) {
                _ui.update { it.copy(pendingPaste = PendingPaste(effective, dest, conflicts)) }
            } else {
                startPaste(effective, dest, policy.toResolution())
            }
        }
    }

    fun confirmPaste(resolution: ConflictResolution) {
        val pending = _ui.value.pendingPaste ?: return
        _ui.update { it.copy(pendingPaste = null) }
        startPaste(pending.clipboard, pending.destination, resolution)
    }

    fun cancelPaste() {
        _ui.update { it.copy(pendingPaste = null) }
    }

    private fun startPaste(clip: FileClipboard, dest: String, resolution: ConflictResolution) {
        if (clip.mode == ClipboardMode.COPY) {
            operations.submitCopy(clip.paths, dest, resolution)
            _ui.update { it.copy(clipboard = null) }
            postMessage("Copying ${Formatters.plural(clip.paths.size, "item")}\u2026")
        } else {
            operations.submitMove(clip.paths, dest, resolution)
            _ui.update { it.copy(clipboard = null) }
            postMessage("Moving ${Formatters.plural(clip.paths.size, "item")}\u2026")
        }
    }

    private fun ConflictPolicy.toResolution(): ConflictResolution =
        when (this) {
            ConflictPolicy.ASK, ConflictPolicy.KEEP_BOTH -> ConflictResolution.KEEP_BOTH
            ConflictPolicy.REPLACE -> ConflictResolution.REPLACE
            ConflictPolicy.SKIP -> ConflictResolution.SKIP
        }

    // ---- delete -------------------------------------------------------------------------------

    fun requestDelete() {
        val paths = orderedSelection()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            if (settingsRepo.current().confirmDelete) {
                _ui.update { it.copy(pendingDelete = paths) }
            } else {
                submitDelete(paths)
            }
        }
    }

    fun confirmDelete() {
        val paths = _ui.value.pendingDelete ?: return
        _ui.update { it.copy(pendingDelete = null) }
        submitDelete(paths)
    }

    fun cancelDelete() {
        _ui.update { it.copy(pendingDelete = null) }
    }

    private fun submitDelete(paths: List<String>) {
        operations.submitDelete(paths)
        _ui.update { s ->
            val remaining = s.clipboard?.let { c -> c.copy(paths = c.paths.filter { it !in paths }) }
            s.copy(selection = emptySet(), clipboard = remaining?.takeIf { it.paths.isNotEmpty() })
        }
        postMessage("Deleting ${Formatters.plural(paths.size, "item")}\u2026")
    }

    // ---- rename / create ----------------------------------------------------------------------

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { FileCommands.rename(File(path), newName) }
            result
                .onSuccess {
                    postMessage("Renamed to \"${it.name}\"")
                    _ui.update { s -> s.copy(selection = emptySet()) }
                    refresh()
                }
                .onFailure { postMessage(it.message ?: "Couldn't rename.") }
        }
    }

    fun createFolder(name: String) = create(name, directory = true)

    fun createFile(name: String) = create(name, directory = false)

    private fun create(name: String, directory: Boolean) {
        val parent = _ui.value.currentPath
        if (parent.isBlank()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (directory) FileCommands.createFolder(File(parent), name) else FileCommands.createFile(File(parent), name)
            }
            result
                .onSuccess {
                    postMessage(if (directory) "Created folder \"${it.name}\"" else "Created file \"${it.name}\"")
                    refresh()
                }
                .onFailure { postMessage(it.message ?: "Couldn't create it.") }
        }
    }

    // ---- bookmarks / recents ------------------------------------------------------------------

    fun toggleBookmarkForCurrent() {
        val path = _ui.value.currentPath
        if (path.isBlank()) return
        viewModelScope.launch {
            val wasBookmarked = path in settingsRepo.current().bookmarks
            settingsRepo.toggleBookmark(path)
            postMessage(if (wasBookmarked) "Bookmark removed" else "Bookmarked this folder")
        }
    }

    /** Adds every selected folder to Quick access (the bookmark list). Files in the selection are ignored. */
    fun addToQuickAccess(entries: List<FileEntry>) {
        val folders = entries.filter { it.isDirectory }
        if (folders.isEmpty()) {
            postMessage("Select a folder to add it to Quick access.")
            return
        }
        viewModelScope.launch {
            val existing = settingsRepo.current().bookmarks.toSet()
            var added = 0
            for (folder in folders) {
                if (folder.path !in existing) {
                    settingsRepo.toggleBookmark(folder.path)
                    added++
                }
            }
            postMessage(if (added == 0) "Already in Quick access" else "Added ${Formatters.plural(added, "folder")} to Quick access")
        }
    }

    fun recordRecentFile(path: String) {
        viewModelScope.launch { settingsRepo.recordRecentFile(path) }
    }

    /** True when the last listing failed because Android denied access (used to pick the right message). */
    fun isAccessDenied(view: BrowserView?): Boolean = view?.listing?.failure?.reason == ListFailureReason.ACCESS_DENIED

    fun filterIsActive(): Boolean = _ui.value.filter != FilterKind.ALL || _ui.value.query.isNotBlank()
}
