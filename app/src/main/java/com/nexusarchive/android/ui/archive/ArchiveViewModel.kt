package com.nexusarchive.android.ui.archive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.archive.ArchiveEngine
import com.nexusarchive.android.archive.ArchiveException
import com.nexusarchive.android.archive.ArchiveFormat
import com.nexusarchive.android.archive.ArchiveFormats
import com.nexusarchive.android.archive.ArchiveJobs
import com.nexusarchive.android.archive.ArchiveListing
import com.nexusarchive.android.archive.ArchiveSortField
import com.nexusarchive.android.core.ConflictResolution
import com.nexusarchive.android.core.NoProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ArchiveUiState(
    val path: String = "",
    val loading: Boolean = true,
    val format: ArchiveFormat? = null,
    val listing: ArchiveListing? = null,
    val error: String? = null,
    val passwordRequired: Boolean = false,
    val passwordError: String? = null,
    val checkingPassword: Boolean = false,
    val currentDir: String = "",
    val selection: Set<String> = emptySet(),
    val searchActive: Boolean = false,
    val query: String = "",
    val sortField: ArchiveSortField = ArchiveSortField.NAME,
    val sortAscending: Boolean = true,
    val grid: Boolean = false,
    val staging: Boolean = false,
)

/** Why files are being pulled out of the archive into the app's cache. */
enum class StagePurpose { OPEN, OPEN_WITH, SHARE, COPY }

class StagedFiles(val purpose: StagePurpose, val files: List<File>)

/** Which action is waiting for a password before it can start. */
enum class PendingAction { NONE, EXTRACT, TEST, STAGE }

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NexusApplication
    private val engine = ArchiveEngine()

    private val _ui = MutableStateFlow(ArchiveUiState())
    val ui: StateFlow<ArchiveUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var password: CharArray? = null
    private var loadedPath: String? = null

    /** Set when the user chose Extract/Test on a password-protected archive and still has to enter the password. */
    val pendingAction = MutableStateFlow(PendingAction.NONE)
    private var pendingExtract: ExtractRequest? = null

    private val _staged = MutableSharedFlow<StagedFiles>(extraBufferCapacity = 4)
    val staged: SharedFlow<StagedFiles> = _staged.asSharedFlow()
    private var stagingJob: Job? = null
    private var pendingStage: Pair<StagePurpose, Set<String>>? = null

    data class ExtractRequest(val intoFolderNamedAfterArchive: Boolean, val resolution: ConflictResolution, val selectedOnly: Boolean)

    fun load(path: String) {
        if (loadedPath == path) return
        loadedPath = path
        clearPassword()
        _ui.value = ArchiveUiState(path = path)
        listArchive()
    }

    fun retry() {
        _ui.update { it.copy(loading = true, error = null) }
        listArchive()
    }

    private fun listArchive() {
        val path = loadedPath ?: return
        viewModelScope.launch {
            try {
                val listing = engine.list(File(path), password)
                _ui.update {
                    it.copy(
                        loading = false,
                        listing = listing,
                        format = listing.format,
                        error = null,
                        passwordRequired = false,
                        passwordError = null,
                        checkingPassword = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArchiveException) {
                if (e.kind == ArchiveException.Kind.PASSWORD_REQUIRED) {
                    _ui.update { it.copy(loading = false, passwordRequired = true, error = null, checkingPassword = false) }
                } else if (e.kind == ArchiveException.Kind.WRONG_PASSWORD && password != null) {
                    clearPassword()
                    _ui.update {
                        it.copy(loading = false, passwordRequired = true, passwordError = "Wrong password, or the archive is damaged.", checkingPassword = false)
                    }
                } else {
                    _ui.update { it.copy(loading = false, error = e.message ?: "This archive couldn't be opened.", checkingPassword = false) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "This archive couldn't be opened.", checkingPassword = false) }
            }
        }
    }

    // ---- navigation inside the archive --------------------------------------------------------

    fun openFolder(path: String) {
        _ui.update { it.copy(currentDir = path, selection = emptySet()) }
    }

    /** Goes up one virtual folder. Returns false when already at the archive root. */
    fun navigateUp(): Boolean {
        val dir = _ui.value.currentDir
        if (dir.isEmpty()) return false
        val parent = dir.substringBeforeLast('/', "")
        _ui.update { it.copy(currentDir = parent, selection = emptySet()) }
        return true
    }

    fun toggleSelect(path: String) {
        _ui.update { state ->
            val next = if (path in state.selection) state.selection - path else state.selection + path
            state.copy(selection = next)
        }
    }

    fun selectAllIn(paths: List<String>) {
        _ui.update { it.copy(selection = it.selection + paths) }
    }

    fun selectOnly(path: String) {
        _ui.update { it.copy(selection = setOf(path)) }
    }

    fun invertIn(paths: List<String>) {
        _ui.update { state -> state.copy(selection = paths.filter { it !in state.selection }.toSet()) }
    }

    fun setSearchActive(active: Boolean) {
        _ui.update { it.copy(searchActive = active, query = if (active) it.query else "") }
    }

    fun setQuery(text: String) {
        _ui.update { it.copy(query = text) }
    }

    fun setSortField(field: ArchiveSortField) {
        _ui.update { it.copy(sortField = field) }
    }

    fun toggleSortDirection() {
        _ui.update { it.copy(sortAscending = !it.sortAscending) }
    }

    fun toggleGrid() {
        _ui.update { it.copy(grid = !it.grid) }
    }

    fun postMessage(text: String) {
        _messages.tryEmit(text)
    }

    /**
     * Extracts [paths] into the app's cache so they can be opened, shared or copied. The files are removed
     * the next time the app starts. Password-protected archives ask for the password first.
     */
    fun stage(purpose: StagePurpose, paths: Set<String>) {
        val archive = File(loadedPath ?: return)
        if (paths.isEmpty()) return
        if (needsPasswordNow()) {
            pendingStage = purpose to paths
            pendingAction.value = PendingAction.STAGE
            _ui.update { it.copy(passwordRequired = true, passwordError = null) }
            return
        }
        stagingJob?.cancel()
        stagingJob = viewModelScope.launch {
            _ui.update { it.copy(staging = true) }
            try {
                val dir = File(app.cacheDir, "archive-open/${System.nanoTime()}")
                val outcome = engine.extract(archive, dir, paths, password, ConflictResolution.REPLACE, NoProgress)
                val files = withContext(Dispatchers.IO) { paths.map { File(dir, it) }.filter { it.exists() } }
                if (files.isEmpty()) {
                    postMessage(outcome.failures.firstOrNull()?.message ?: "Couldn't prepare the file.")
                } else {
                    _staged.emit(StagedFiles(purpose, files))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArchiveException) {
                postMessage(e.message ?: "Couldn't prepare the file.")
            } catch (e: Exception) {
                postMessage(e.message ?: "Couldn't prepare the file.")
            } finally {
                _ui.update { it.copy(staging = false) }
            }
        }
    }

    fun cancelStaging() {
        stagingJob?.cancel()
        _ui.update { it.copy(staging = false) }
    }

    fun clearSelection() {
        _ui.update { it.copy(selection = emptySet()) }
    }

    // ---- passwords ----------------------------------------------------------------------------

    /** Used for the dialog shown when the archive header itself is encrypted, or before Extract/Test. */
    fun submitPassword(chars: CharArray) {
        val path = loadedPath ?: return
        _ui.update { it.copy(checkingPassword = true, passwordError = null) }
        viewModelScope.launch {
            val format = ArchiveFormats.detect(File(path))
            if (format == ArchiveFormat.ZIP) {
                val ok = try {
                    engine.checkZipPassword(File(path), chars)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _ui.update { it.copy(checkingPassword = false, passwordError = e.message ?: "Couldn't check the password.") }
                    return@launch
                }
                if (!ok) {
                    chars.fill('\u0000')
                    _ui.update { it.copy(checkingPassword = false, passwordError = "Wrong password.") }
                    return@launch
                }
            }
            clearPassword()
            password = chars
            val needsRelist = _ui.value.listing == null
            _ui.update { it.copy(checkingPassword = false, passwordRequired = false, passwordError = null) }
            if (needsRelist) {
                _ui.update { it.copy(loading = true) }
                listArchive()
            }
            resumePending()
        }
    }

    /** Lets the user enter a password up front (needed for 7z archives whose entries are encrypted but whose list is readable). */
    fun promptForPassword() {
        _ui.update { it.copy(passwordRequired = true, passwordError = null) }
    }

    fun cancelPassword() {
        pendingAction.value = PendingAction.NONE
        pendingExtract = null
        _ui.update { it.copy(passwordRequired = false, passwordError = null, checkingPassword = false) }
    }

    private fun clearPassword() {
        password?.fill('\u0000')
        password = null
    }

    override fun onCleared() {
        clearPassword()
        super.onCleared()
    }

    // ---- actions ------------------------------------------------------------------------------

    private fun needsPasswordNow(): Boolean {
        val listing = _ui.value.listing ?: return false
        return listing.encrypted && password == null
    }

    fun requestTest() {
        if (needsPasswordNow()) {
            pendingAction.value = PendingAction.TEST
            _ui.update { it.copy(passwordRequired = true, passwordError = null) }
            return
        }
        startTest()
    }

    fun requestExtract(request: ExtractRequest) {
        if (needsPasswordNow()) {
            pendingExtract = request
            pendingAction.value = PendingAction.EXTRACT
            _ui.update { it.copy(passwordRequired = true, passwordError = null) }
            return
        }
        startExtract(request)
    }

    private fun resumePending() {
        when (pendingAction.value) {
            PendingAction.TEST -> startTest()
            PendingAction.EXTRACT -> pendingExtract?.let { startExtract(it) }
            PendingAction.STAGE -> pendingStage?.let { stage(it.first, it.second) }
            PendingAction.NONE -> {}
        }
        pendingAction.value = PendingAction.NONE
        pendingExtract = null
        pendingStage = null
    }

    private fun startTest() {
        val path = loadedPath ?: return
        ArchiveJobs.test(app.operations, File(path), password)
        _messages.tryEmit("Archive test started. Results appear under Operations.")
    }

    private fun startExtract(request: ExtractRequest) {
        val path = loadedPath ?: return
        val archive = File(path)
        val parent = archive.parentFile
        if (parent == null) {
            _messages.tryEmit("This archive has no parent folder to extract into.")
            return
        }
        val dest = if (request.intoFolderNamedAfterArchive) File(parent, ArchiveFormats.baseName(archive.name)) else parent
        val selection: Set<String>? = if (request.selectedOnly) _ui.value.selection.toSet() else null
        ArchiveJobs.extract(app.operations, archive, dest, selection, password, request.resolution)
        if (selection != null) clearSelection()
        _messages.tryEmit("Extraction started. Progress appears under Operations.")
    }
}
