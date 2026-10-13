package com.nexusarchive.android.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.core.FileTypes
import com.nexusarchive.android.core.FilterKind
import com.nexusarchive.android.core.isSymlink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SearchHit(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val parentPath: String,
)

data class SearchUiState(
    val query: String = "",
    val kind: FilterKind = FilterKind.ALL,
    val scopePath: String? = null,
    val results: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val truncated: Boolean = false,
    val scanned: Int = 0,
)

/**
 * Searches real folders on disk. A scan only starts when the user asks for it (search key, search button or
 * changing a pill after a search), never while typing, and it can be stopped. Symbolic links are not followed.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NexusApplication
    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()
    private var job: Job? = null

    fun setQuery(text: String) {
        _ui.update { it.copy(query = text) }
    }

    fun setKind(kind: FilterKind) {
        _ui.update { it.copy(kind = kind) }
        if (_ui.value.searched || _ui.value.query.isNotBlank()) runSearch()
    }

    fun setScope(path: String) {
        _ui.update { it.copy(scopePath = path) }
        if (_ui.value.searched) runSearch()
    }

    /** Picks a default location the first time the screen has one to offer. */
    fun ensureScope(path: String?) {
        if (_ui.value.scopePath == null && path != null) _ui.update { it.copy(scopePath = path) }
    }

    fun stop() {
        job?.cancel()
        _ui.update { it.copy(searching = false) }
    }

    fun runSearch() {
        val state = _ui.value
        val root = state.scopePath ?: return
        job?.cancel()
        if (state.query.isBlank() && state.kind == FilterKind.ALL) {
            _ui.update { it.copy(results = emptyList(), searching = false, searched = false, truncated = false, scanned = 0) }
            return
        }
        job = viewModelScope.launch {
            _ui.update { it.copy(searching = true, searched = true, results = emptyList(), truncated = false, scanned = 0) }
            val showHidden = app.settings.current().showHidden
            val query = state.query.trim().lowercase()
            val hits = ArrayList<SearchHit>()
            var truncated = false
            withContext(Dispatchers.IO) {
                var scanned = 0
                val stack = ArrayDeque<File>()
                stack.addLast(File(root))
                outer@ while (stack.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val dir = stack.removeLast()
                    val children = dir.listFiles() ?: continue
                    for (file in children) {
                        scanned++
                        if (!showHidden && file.name.startsWith(".")) continue
                        val isDir = file.isDirectory
                        if (isDir && !file.isSymlink()) stack.addLast(file)
                        if (matches(file.name, isDir, query, state.kind)) {
                            hits.add(
                                SearchHit(
                                    path = file.path,
                                    name = file.name,
                                    isDirectory = isDir,
                                    sizeBytes = if (isDir) -1L else file.length(),
                                    lastModified = file.lastModified(),
                                    parentPath = file.parent ?: "",
                                ),
                            )
                            if (hits.size >= MAX_HITS) {
                                truncated = true
                                break@outer
                            }
                        }
                        if (scanned % 2000 == 0) {
                            val count = scanned
                            _ui.update { it.copy(scanned = count) }
                            currentCoroutineContext().ensureActive()
                        }
                    }
                }
                _ui.update { it.copy(scanned = scanned) }
            }
            val sorted = hits.sortedWith(compareByDescending<SearchHit> { it.isDirectory }.thenBy { it.name.lowercase() })
            _ui.update { it.copy(results = sorted, searching = false, truncated = truncated) }
        }
    }

    companion object {
        const val MAX_HITS = 1000

        /** Pure matching rule, kept separate so it can be unit-tested. */
        fun matches(name: String, isDirectory: Boolean, queryLower: String, kind: FilterKind): Boolean {
            if (queryLower.isNotEmpty() && !name.lowercase().contains(queryLower)) return false
            val fileKind = FileTypes.kindOf(name, isDirectory)
            return when (kind) {
                FilterKind.ALL -> true
                FilterKind.FOLDERS -> isDirectory
                FilterKind.IMAGES -> fileKind == FileKind.IMAGE
                FilterKind.VIDEOS -> fileKind == FileKind.VIDEO
                FilterKind.AUDIO -> fileKind == FileKind.AUDIO
                FilterKind.DOCUMENTS -> fileKind == FileKind.DOCUMENT
                FilterKind.ARCHIVES -> fileKind == FileKind.ARCHIVE
                FilterKind.APKS -> fileKind == FileKind.APK
            }
        }
    }
}
