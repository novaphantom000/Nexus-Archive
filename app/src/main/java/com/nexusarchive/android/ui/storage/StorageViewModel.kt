package com.nexusarchive.android.ui.storage

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.storage.AccessState
import com.nexusarchive.android.storage.SafAccess
import com.nexusarchive.android.storage.SafBookmark
import com.nexusarchive.android.storage.SafKind
import com.nexusarchive.android.storage.SafLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manages the saved external-storage folders: add, test, reauthorize, rename and remove. */
class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NexusApplication
    private val repo = app.safStorage

    val bookmarks: StateFlow<List<SafBookmark>> =
        repo.bookmarks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _states = MutableStateFlow<Map<String, AccessState>>(emptyMap())
    val states: StateFlow<Map<String, AccessState>> = _states.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun post(text: String) {
        _messages.tryEmit(text)
    }

    fun refreshStates() {
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                repo.snapshot().associate { it.id to SafAccess.accessState(app, it) }
            }
            _states.value = map
        }
    }

    /** Called with the folder the user picked in Android's folder picker. */
    fun addTree(uri: Uri, kind: SafKind) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { SafAccess.persist(app, uri) }
            if (error != null) {
                post(error)
                return@launch
            }
            val existing = repo.snapshot().firstOrNull { it.treeUri == uri.toString() }
            if (existing != null) {
                post("\"${existing.label}\" is already in your list.")
                refreshStates()
                return@launch
            }
            val documentId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (e: IllegalArgumentException) {
                ""
            }
            val label = SafLabels.defaultLabel(documentId, kind)
            repo.add(uri.toString(), label, kind)
            post("Added \"$label\". Access stays saved after you restart the app.")
            refreshStates()
        }
    }

    /** Called with the folder picked while re-granting access to [id]. */
    fun reauthorize(id: String, uri: Uri) {
        viewModelScope.launch {
            val current = repo.snapshot().firstOrNull { it.id == id } ?: return@launch
            val error = withContext(Dispatchers.IO) { SafAccess.persist(app, uri) }
            if (error != null) {
                post(error)
                return@launch
            }
            if (uri.toString() != current.treeUri) {
                SafAccess.release(app, Uri.parse(current.treeUri))
                repo.replaceTree(id, uri.toString())
                post("Access updated. This shortcut now points to the folder you just chose.")
            } else {
                post("Access restored.")
            }
            refreshStates()
        }
    }

    fun rename(id: String, label: String) {
        viewModelScope.launch { repo.rename(id, label) }
    }

    fun setKind(id: String, kind: SafKind) {
        viewModelScope.launch { repo.setKind(id, kind) }
    }

    /** Removes the shortcut and releases Android's saved grant. Files in the folder are never touched. */
    fun remove(bookmark: SafBookmark) {
        viewModelScope.launch {
            repo.remove(bookmark.id)
            withContext(Dispatchers.IO) { SafAccess.release(app, Uri.parse(bookmark.treeUri)) }
            _states.update { it - bookmark.id }
            post("Removed \"${bookmark.label}\". Your files were not changed.")
        }
    }

    fun test(bookmark: SafBookmark) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { SafAccess.accessState(app, bookmark) }
            _states.update { it + (bookmark.id to state) }
            post("${bookmark.label}: ${state.label}")
        }
    }
}
