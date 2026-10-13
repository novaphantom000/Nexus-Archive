package com.nexusarchive.android.ui.storage

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.operations.OperationType
import com.nexusarchive.android.storage.AccessState
import com.nexusarchive.android.storage.SafAccess
import com.nexusarchive.android.storage.SafBookmark
import com.nexusarchive.android.storage.SafItem
import com.nexusarchive.android.storage.SafListResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SafCrumb(val documentId: String, val name: String)

data class SafBrowseState(
    val bookmark: SafBookmark? = null,
    val crumbs: List<SafCrumb> = emptyList(),
    val loading: Boolean = true,
    val items: List<SafItem> = emptyList(),
    val canCreateHere: Boolean = false,
    val failure: SafListResult.Failure? = null,
    val selection: Set<String> = emptySet(),
)

/** Browses one saved document-tree folder. Navigation is by document id, never by guessed file path. */
class SafBrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NexusApplication

    private val _ui = MutableStateFlow(SafBrowseState())
    val ui: StateFlow<SafBrowseState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadedId: String? = null
    private var tree: Uri? = null
    private var requestCounter = 0

    init {
        viewModelScope.launch {
            app.operations.changes.collect { refresh() }
        }
    }

    fun postMessage(text: String) {
        _messages.tryEmit(text)
    }

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        _ui.value = SafBrowseState()
        viewModelScope.launch {
            val bookmark = app.safStorage.snapshot().firstOrNull { it.id == id }
            if (bookmark == null) {
                _ui.update {
                    it.copy(loading = false, failure = SafListResult.Failure(AccessState.UNAVAILABLE, "This storage was removed from your list."))
                }
                return@launch
            }
            val parsed = Uri.parse(bookmark.treeUri)
            val root = try {
                SafAccess.rootDocumentId(parsed)
            } catch (e: IllegalArgumentException) {
                _ui.update {
                    it.copy(loading = false, failure = SafListResult.Failure(AccessState.UNAVAILABLE, "The saved location is not valid. Remove it and add it again."))
                }
                return@launch
            }
            tree = parsed
            _ui.update { it.copy(bookmark = bookmark, crumbs = listOf(SafCrumb(root, bookmark.label))) }
            app.safStorage.touch(id)
            refresh()
        }
    }

    fun refresh() {
        val t = tree ?: return
        val crumb = _ui.value.crumbs.lastOrNull() ?: return
        val mine = ++requestCounter
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { SafAccess.list(app, t, crumb.documentId) }
            if (mine != requestCounter) return@launch
            _ui.update { state ->
                when (result) {
                    is SafListResult.Success -> {
                        val sorted = result.items.sortedWith(
                            compareByDescending<SafItem> { it.isDirectory }.thenBy { it.name.lowercase() },
                        )
                        val ids = sorted.map { it.documentId }.toSet()
                        state.copy(
                            loading = false,
                            items = sorted,
                            canCreateHere = result.canCreateHere,
                            failure = null,
                            selection = state.selection.filter { it in ids }.toSet(),
                        )
                    }
                    is SafListResult.Failure -> state.copy(loading = false, items = emptyList(), canCreateHere = false, failure = result)
                }
            }
        }
    }

    fun openFolder(item: SafItem) {
        if (!item.isDirectory) return
        _ui.update { it.copy(crumbs = it.crumbs + SafCrumb(item.documentId, item.name), selection = emptySet()) }
        refresh()
    }

    fun navigateUp(): Boolean {
        val crumbs = _ui.value.crumbs
        if (crumbs.size <= 1) return false
        _ui.update { it.copy(crumbs = it.crumbs.dropLast(1), selection = emptySet()) }
        refresh()
        return true
    }

    fun toggleSelect(documentId: String) {
        _ui.update { state ->
            val next = if (documentId in state.selection) state.selection - documentId else state.selection + documentId
            state.copy(selection = next)
        }
    }

    fun selectAll() {
        _ui.update { state -> state.copy(selection = state.items.map { it.documentId }.toSet()) }
    }

    fun clearSelection() {
        _ui.update { it.copy(selection = emptySet()) }
    }

    fun createFolder(name: String) {
        val t = tree ?: return
        val parent = _ui.value.crumbs.lastOrNull()?.documentId ?: return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { SafAccess.createFolder(app, t, parent, name) }
            if (id == null) postMessage("Couldn't create the folder. This location may not allow it.") else refresh()
        }
    }

    fun deleteSelected() {
        val targets = _ui.value.items.filter { it.documentId in _ui.value.selection }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            var failed = 0
            withContext(Dispatchers.IO) {
                for (item in targets) {
                    if (!SafAccess.delete(app, item.uri)) failed++
                }
            }
            clearSelection()
            postMessage(if (failed == 0) "Deleted ${targets.size} item(s)." else "$failed of ${targets.size} item(s) couldn't be deleted.")
            refresh()
        }
    }

    fun rename(item: SafItem, newName: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { SafAccess.rename(app, item.uri, newName) }
            if (!ok) postMessage("Couldn't rename \"${item.name}\". This location may not allow it.")
            clearSelection()
            refresh()
        }
    }

    fun importFiles(uris: List<Uri>) {
        val t = tree ?: return
        val parent = _ui.value.crumbs.lastOrNull()?.documentId ?: return
        val label = _ui.value.bookmark?.label ?: "storage"
        app.operations.submitCustom(
            OperationType.COPY,
            "Copy ${uris.size} file(s) into $label",
            uris.map { it.toString() },
            label,
        ) { listener -> SafAccess.importFiles(app, t, parent, uris, listener) }
        postMessage("Copy started. Progress appears under Operations.")
    }

    fun copyOut(item: SafItem, destination: Uri) {
        app.operations.submitCustom(
            OperationType.COPY,
            "Save a copy of \"${item.name}\"",
            listOf(item.uri.toString()),
            destination.toString(),
        ) { listener -> SafAccess.copyOut(app, item.uri, destination, listener) }
        postMessage("Copy started. Progress appears under Operations.")
    }
}
