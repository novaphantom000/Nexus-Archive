@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.browse

import com.nexusarchive.android.ui.archive.ExtractDialog
import kotlinx.coroutines.launch
import com.nexusarchive.android.ui.components.buildLocationOptions
import com.nexusarchive.android.ui.components.LocationPickerTitle
import com.nexusarchive.android.ui.components.LocationOption
import com.nexusarchive.android.ui.components.LocalFloatingBarInset
import androidx.compose.ui.draw.clip
import com.nexusarchive.android.ui.storage.safKindIcon
import com.nexusarchive.android.ui.components.SelectionPill
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.archive.ArchiveFormats
import com.nexusarchive.android.archive.ArchiveJobs
import com.nexusarchive.android.core.ConflictResolution
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.core.FileEntry
import com.nexusarchive.android.core.FileNames
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.core.ListFailureReason
import com.nexusarchive.android.core.ListResult
import com.nexusarchive.android.data.AppSettings
import com.nexusarchive.android.data.LayoutDensity
import com.nexusarchive.android.data.ViewMode
import com.nexusarchive.android.ui.components.ConfirmDialog
import com.nexusarchive.android.ui.components.EmptyState
import com.nexusarchive.android.ui.components.StoragePermission
import com.nexusarchive.android.ui.components.TextInputDialog
import com.nexusarchive.android.ui.settings.SettingsViewModel
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.Motion
import kotlinx.coroutines.delay
import java.io.File

private enum class CreateKind { FOLDER, FILE }

private enum class TopBarMode { NORMAL, SEARCH, SELECTION }

private class PathHolder(var last: String)

/** Gate: the browser is only shown once Android has granted file access. */
@Composable
fun BrowseScreen(
    vm: BrowserViewModel,
    settings: AppSettings,
    settingsVm: SettingsViewModel,
    permission: StoragePermission,
    onOpenArchive: (String) -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSafStorage: (String) -> Unit,
) {
    LaunchedEffect(permission.hasAccess) {
        if (permission.hasAccess) vm.ensureStarted()
    }
    if (!permission.hasAccess) {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.Lock,
                    title = "File access needed",
                    message = "To browse and manage files, Nexus Archive needs the file access permission. Nothing leaves your device.",
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = permission.request) { Text("Allow file access") }
                            TextButton(onClick = permission.openAppSettings) { Text("Open app settings") }
                        }
                    },
                )
            }
        }
    } else {
        BrowseContent(vm, settings, settingsVm, permission, onOpenArchive, onOpenStorage, onOpenSafStorage)
    }
}

@Composable
private fun BrowseContent(
    vm: BrowserViewModel,
    settings: AppSettings,
    settingsVm: SettingsViewModel,
    permission: StoragePermission,
    onOpenArchive: (String) -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSafStorage: (String) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val safBookmarks by remember { (context.applicationContext as NexusApplication).safStorage.bookmarks }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val reduce = LocalReduceMotion.current
    val snackbar = remember { SnackbarHostState() }

    var showSortSheet by remember { mutableStateOf(false) }
    var showNewSheet by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf<CreateKind?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var propertiesTarget by remember { mutableStateOf<FileEntry?>(null) }
    var compressTargets by remember { mutableStateOf<List<FileEntry>?>(null) }
    var archiveSheetTarget by remember { mutableStateOf<FileEntry?>(null) }
    var extractChooseTarget by remember { mutableStateOf<FileEntry?>(null) }
    val archiveScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val canGoUp = parentOf(ui.currentPath, ui.volumes) != null

    val locations = remember(ui.volumes, settings.bookmarks, safBookmarks) {
        buildLocationOptions(ui.volumes, settings.bookmarks, safBookmarks)
    }
    BackHandler(enabled = ui.selection.isNotEmpty() || ui.searchActive || canGoUp) {
        when {
            ui.selection.isNotEmpty() -> vm.clearSelection()
            ui.searchActive -> vm.setSearchActive(false)
            else -> vm.navigateUp()
        }
    }

    val visible = view?.visible ?: emptyList()
    val selectedEntries = visible.filter { it.path in ui.selection }
    val mode = when {
        ui.selection.isNotEmpty() -> TopBarMode.SELECTION
        ui.searchActive -> TopBarMode.SEARCH
        else -> TopBarMode.NORMAL
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(ui.currentPath) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }
    val fabExpanded by remember(settings.viewMode) {
        derivedStateOf {
            if (settings.viewMode == ViewMode.LIST) listState.firstVisibleItemIndex == 0 else gridState.firstVisibleItemIndex == 0
        }
    }

    // Remember the last clipboard so the paste bar can animate out with its content intact.
    val lastClipboard = remember { mutableStateOf<FileClipboard?>(null) }
    SideEffect { if (ui.clipboard != null) lastClipboard.value = ui.clipboard }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(Motion.effects(reduce)) togetherWith fadeOut(Motion.effects(reduce)) },
                label = "browseTopBar",
            ) { m ->
                when (m) {
                    TopBarMode.SELECTION -> SelectionTopBar(
                        count = ui.selection.size,
                        selected = selectedEntries,
                        onClear = vm::clearSelection,
                        onCopy = vm::copySelection,
                        onCut = vm::cutSelection,
                        onDelete = vm::requestDelete,
                        onSelectAll = vm::selectAll,
                        onInvert = vm::invertSelection,
                        onShare = {
                            FileActions.share(context, selectedEntries.map { File(it.path) })?.let(vm::postMessage)
                        },
                        onCompress = {
                            if (ui.canWriteHere) {
                                compressTargets = selectedEntries
                            } else {
                                vm.postMessage("This folder is read-only, so an archive can't be created here.")
                            }
                        },
                        onRename = { entry -> renameTarget = entry },
                        onProperties = { entry -> propertiesTarget = entry },
                        onCopyPath = { entry ->
                            FileActions.copyText(context, "Path", entry.path)
                            vm.postMessage("Path copied")
                        },
                        onCopyName = { entry ->
                            FileActions.copyText(context, "Name", entry.name)
                            vm.postMessage("Name copied")
                        },
                        onOpenWith = { entry ->
                            FileActions.open(context, File(entry.path), chooser = true)?.let(vm::postMessage)
                        },
                        onAddQuickAccess = {
                            vm.addToQuickAccess(selectedEntries)
                            vm.clearSelection()
                        },
                    )
                    TopBarMode.SEARCH -> SearchTopBar(
                        query = ui.query,
                        onQuery = vm::setQuery,
                        onClose = { vm.setSearchActive(false) },
                    )
                    TopBarMode.NORMAL -> NormalTopBar(
                        title = buildBreadcrumbs(ui.currentPath, ui.volumes).lastOrNull()?.label ?: "Browse",
                        subtitle = if (view == null) "" else Formatters.plural(visible.size, "item"),
                        canGoUp = canGoUp,
                        viewMode = settings.viewMode,
                        showHidden = settings.showHidden,
                        isBookmarked = ui.currentPath in settings.bookmarks,
                        onUp = { vm.navigateUp() },
                        onSearch = { vm.setSearchActive(true) },
                        onSortFilter = { showSortSheet = true },
                        onToggleView = {
                            settingsVm.setViewMode(if (settings.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                        },
                        onRefresh = vm::refresh,
                        onToggleHidden = { settingsVm.setShowHidden(!settings.showHidden) },
                        onToggleBookmark = vm::toggleBookmarkForCurrent,
                        onSelectAll = vm::selectAll,
                        onOpenStorage = onOpenStorage,
                        locations = locations,
                        onPickLocation = { option ->
                            if (option.path != null) vm.navigateTo(option.path) else if (option.safId != null) onOpenSafStorage(option.safId)
                        },
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = ui.clipboard != null && ui.selection.isEmpty(),
                enter = slideInVertically(Motion.spatial(reduce)) { it } + fadeIn(Motion.effects(reduce)),
                exit = slideOutVertically(Motion.spatial(reduce)) { it } + fadeOut(Motion.effects(reduce)),
            ) {
                val clip = ui.clipboard ?: lastClipboard.value
                if (clip != null) {
                    Box(Modifier.padding(bottom = LocalFloatingBarInset.current)) {
                        ClipboardBar(
                            clipboard = clip,
                            canPaste = ui.canWriteHere,
                            onPaste = vm::paste,
                            onClear = vm::clearClipboard,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                modifier = Modifier.padding(bottom = LocalFloatingBarInset.current),
                visible = mode == TopBarMode.NORMAL && ui.canWriteHere && ui.clipboard == null,
                enter = fadeIn(Motion.effects(reduce)),
                exit = fadeOut(Motion.effects(reduce)),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showNewSheet = true },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("New") },
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalFloatingBarInset.current)) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (ui.currentPath.isNotBlank()) {
                BreadcrumbBar(
                    crumbs = remember(ui.currentPath, ui.volumes) { buildBreadcrumbs(ui.currentPath, ui.volumes) },
                    onNavigate = vm::navigateTo,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (ui.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

            // Directory-change animation: the new listing eases in from the direction of travel.
            val path = ui.currentPath
            val holder = remember { PathHolder(path) }
            val forward = remember(path) { path.length >= holder.last.length }
            SideEffect { holder.last = path }
            val enter = remember(path) { Animatable(if (reduce) 1f else 0f) }
            LaunchedEffect(enter) {
                if (enter.value < 1f) enter.animateTo(1f, Motion.spatial(reduce))
            }
            val offsetPx = with(LocalDensity.current) { 36.dp.toPx() }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = enter.value.coerceIn(0f, 1f)
                        translationX = (1f - enter.value) * offsetPx * (if (forward) 1f else -1f)
                    },
            ) {
                val v = view
                when {
                    v == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    v.listing.failure != null -> FailureState(
                        failure = v.listing.failure,
                        onRetry = vm::refresh,
                    )
                    v.visible.isEmpty() -> {
                        val filtering = ui.query.isNotBlank() || ui.filter != com.nexusarchive.android.core.FilterKind.ALL
                        EmptyState(
                            icon = if (filtering) Icons.Rounded.Search else Icons.Rounded.FolderOpen,
                            title = if (filtering) "No matches" else "This folder is empty",
                            message = if (filtering) {
                                "Nothing here matches your search or filter. Searching only looks inside the current folder."
                            } else if (v.listing.entries.isNotEmpty()) {
                                "This folder only contains hidden files. Turn on \"Show hidden files\" to see them."
                            } else {
                                "Create a folder or paste items to get started."
                            },
                        )
                    }
                    else -> FileCollection(
                        entries = v.visible,
                        viewMode = settings.viewMode,
                        density = settings.density,
                        selection = ui.selection,
                        reduce = reduce,
                        listState = listState,
                        gridState = gridState,
                        onClick = { entry ->
                            when {
                                ui.selection.isNotEmpty() -> vm.toggleSelect(entry.path)
                                entry.isDirectory -> vm.navigateTo(entry.path)
                                entry.kind == FileKind.ARCHIVE -> {
                                    val reason = ArchiveFormats.unsupportedReason(entry.name)
                                    if (reason != null) {
                                        vm.postMessage("$reason Use More actions > Open with to try another app.")
                                    } else {
                                        archiveSheetTarget = entry
                                    }
                                }
                                else -> {
                                    val error = FileActions.open(context, File(entry.path), chooser = false)
                                    if (error != null) vm.postMessage(error) else vm.recordRecentFile(entry.path)
                                }
                            }
                        },
                        onLongClick = { entry -> vm.toggleSelect(entry.path) },
                    )
                }
            }
        }
    }

    // ---- sheets & dialogs ---------------------------------------------------------------------

    if (showSortSheet) {
        SortFilterSheet(
            settings = settings,
            filter = ui.filter,
            onSortField = settingsVm::setSortField,
            onAscending = settingsVm::setSortAscending,
            onFoldersFirst = settingsVm::setFoldersFirst,
            onShowHidden = settingsVm::setShowHidden,
            onFilter = vm::setFilter,
            onDismiss = { showSortSheet = false },
        )
    }

    if (showNewSheet) {
        ModalBottomSheet(onDismissRequest = { showNewSheet = false }) {
            ListItem(
                headlineContent = { Text("New folder") },
                leadingContent = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    showNewSheet = false
                    createKind = CreateKind.FOLDER
                },
            )
            ListItem(
                headlineContent = { Text("New empty file") },
                leadingContent = { Icon(Icons.Rounded.NoteAdd, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    showNewSheet = false
                    createKind = CreateKind.FILE
                },
            )
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    createKind?.let { kind ->
        val isFolder = kind == CreateKind.FOLDER
        TextInputDialog(
            title = if (isFolder) "New folder" else "New empty file",
            initialValue = "",
            label = "Name",
            confirmText = "Create",
            validate = { name ->
                FileNames.validate(name)
                    ?: if (File(ui.currentPath, name).exists()) "An item named \"$name\" already exists here." else null
            },
            onConfirm = { name ->
                createKind = null
                if (isFolder) vm.createFolder(name) else vm.createFile(name)
            },
            onDismiss = { createKind = null },
        )
    }

    renameTarget?.let { entry ->
        TextInputDialog(
            title = "Rename",
            initialValue = entry.name,
            label = "Name",
            confirmText = "Rename",
            selectBaseNameOnly = !entry.isDirectory,
            validate = { name ->
                FileNames.validate(name)
                    ?: if (name != entry.name && !name.equals(entry.name, ignoreCase = true) &&
                        File(File(entry.path).parentFile, name).exists()
                    ) {
                        "An item named \"$name\" already exists here."
                    } else {
                        null
                    }
            },
            onConfirm = { name ->
                renameTarget = null
                vm.rename(entry.path, name)
            },
            onDismiss = { renameTarget = null },
        )
    }

    propertiesTarget?.let { entry ->
        PropertiesDialog(entry = entry, onDismiss = { propertiesTarget = null })
    }

    archiveSheetTarget?.let { entry ->
        val archive = File(entry.path)
        ArchiveActionSheet(
            entry = entry,
            onDismiss = { archiveSheetTarget = null },
            onView = {
                archiveSheetTarget = null
                onOpenArchive(entry.path)
            },
            onExtractHere = {
                archiveSheetTarget = null
                archiveScope.launch {
                    ArchiveQuickActions.extract(context, archive, false, ConflictResolution.KEEP_BOTH, vm::postMessage) { onOpenArchive(entry.path) }
                }
            },
            onExtractToFolder = {
                archiveSheetTarget = null
                archiveScope.launch {
                    ArchiveQuickActions.extract(context, archive, true, ConflictResolution.KEEP_BOTH, vm::postMessage) { onOpenArchive(entry.path) }
                }
            },
            onExtractChoose = {
                archiveSheetTarget = null
                extractChooseTarget = entry
            },
            onTest = {
                archiveSheetTarget = null
                archiveScope.launch {
                    ArchiveQuickActions.test(context, archive, vm::postMessage) { onOpenArchive(entry.path) }
                }
            },
            onCompress = {
                archiveSheetTarget = null
                if (ui.canWriteHere) {
                    compressTargets = listOf(entry)
                } else {
                    vm.postMessage("This folder is read-only, so an archive can't be created here.")
                }
            },
        )
    }

    extractChooseTarget?.let { entry ->
        ExtractDialog(
            selectedCount = 0,
            archiveBaseName = ArchiveFormats.baseName(entry.name),
            onStart = { request ->
                extractChooseTarget = null
                archiveScope.launch {
                    ArchiveQuickActions.extract(
                        context,
                        File(entry.path),
                        request.intoFolderNamedAfterArchive,
                        request.resolution,
                        vm::postMessage,
                    ) { onOpenArchive(entry.path) }
                }
            },
            onDismiss = { extractChooseTarget = null },
        )
    }

    compressTargets?.let { targets ->
        CompressDialog(
            sources = targets,
            outputDir = File(ui.currentPath),
            onStart = { output, format, level, password ->
                compressTargets = null
                val ops = (context.applicationContext as NexusApplication).operations
                ArchiveJobs.compress(ops, targets.map { File(it.path) }, output, format, level, password)
                password?.fill('\u0000')
                vm.clearSelection()
                vm.postMessage("Compression started. Progress appears under Operations.")
            },
            onDismiss = { compressTargets = null },
        )
    }

    ui.pendingPaste?.let { pending ->
        PasteConflictDialog(pending = pending, onConfirm = vm::confirmPaste, onDismiss = vm::cancelPaste)
    }

    ui.pendingDelete?.let { paths ->
        ConfirmDialog(
            title = "Delete ${Formatters.plural(paths.size, "item")}?",
            text = "This permanently deletes " +
                (if (paths.size == 1) "\"${File(paths[0]).name}\"" else "the selected items") +
                " and anything inside folders. It can't be undone.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = vm::confirmDelete,
            onDismiss = vm::cancelDelete,
        )
    }
}

// ---- top bars ---------------------------------------------------------------------------------

@Composable
private fun NormalTopBar(
    title: String,
    subtitle: String,
    canGoUp: Boolean,
    viewMode: ViewMode,
    showHidden: Boolean,
    isBookmarked: Boolean,
    onUp: () -> Unit,
    onSearch: () -> Unit,
    onSortFilter: () -> Unit,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenStorage: () -> Unit,
    locations: List<LocationOption>,
    onPickLocation: (LocationOption) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { LocationPickerTitle(title = title, subtitle = subtitle, locations = locations, onPick = onPickLocation) },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Search in this folder") }
            IconButton(onClick = onSortFilter) { Icon(Icons.Rounded.FilterList, contentDescription = "Sort and filter") }
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector = if (viewMode == ViewMode.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                    contentDescription = if (viewMode == ViewMode.LIST) "Switch to grid view" else "Switch to list view",
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        onClick = { menuOpen = false; onRefresh() },
                    )
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        leadingIcon = { Icon(Icons.Rounded.SelectAll, contentDescription = null) },
                        onClick = { menuOpen = false; onSelectAll() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") },
                        leadingIcon = {
                            Icon(if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onToggleHidden() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove bookmark" else "Bookmark this folder") },
                        leadingIcon = {
                            Icon(if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onToggleBookmark() },
                    )
                    DropdownMenuItem(
                        text = { Text("External storage\u2026") },
                        leadingIcon = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenStorage() },
                    )
                }
            }
        },
    )
}

@Composable
private fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { focusRequester.requestFocus() }
    }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Search in this folder") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search") }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search text") }
            }
        },
    )
}

@Composable
private fun SelectionTopBar(
    count: Int,
    selected: List<FileEntry>,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onShare: () -> Unit,
    onCompress: () -> Unit,
    onRename: (FileEntry) -> Unit,
    onProperties: (FileEntry) -> Unit,
    onCopyPath: (FileEntry) -> Unit,
    onCopyName: (FileEntry) -> Unit,
    onOpenWith: (FileEntry) -> Unit,
    onAddQuickAccess: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val single = if (selected.size == 1) selected[0] else null
    val roomForInvert = LocalConfiguration.current.screenWidthDp >= 400
    SelectionPill(count = count, onClose = onClear) {
        IconButton(onClick = onCut) { Icon(Icons.Rounded.ContentCut, contentDescription = "Cut") }
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy") }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
        IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all") }
        if (roomForInvert) {
            IconButton(onClick = onInvert) { Icon(Icons.Rounded.FlipToFront, contentDescription = "Invert selection") }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    enabled = selected.any { !it.isDirectory },
                    onClick = { menuOpen = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text("Compress\u2026") },
                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                    onClick = { menuOpen = false; onCompress() },
                )
                DropdownMenuItem(
                    text = { Text("Add to Quick access") },
                    leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
                    enabled = selected.any { it.isDirectory },
                    onClick = { menuOpen = false; onAddQuickAccess() },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                    enabled = single != null,
                    onClick = { if (single != null) { menuOpen = false; onRename(single) } },
                )
                DropdownMenuItem(
                    text = { Text("Properties") },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    enabled = single != null,
                    onClick = { if (single != null) { menuOpen = false; onProperties(single) } },
                )
                DropdownMenuItem(
                    text = { Text("Open with\u2026") },
                    leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                    enabled = single != null && !single.isDirectory,
                    onClick = { if (single != null) { menuOpen = false; onOpenWith(single) } },
                )
                DropdownMenuItem(
                    text = { Text("Copy path") },
                    enabled = single != null,
                    onClick = { if (single != null) { menuOpen = false; onCopyPath(single) } },
                )
                DropdownMenuItem(
                    text = { Text("Copy name") },
                    enabled = single != null,
                    onClick = { if (single != null) { menuOpen = false; onCopyName(single) } },
                )
                if (!roomForInvert) {
                    DropdownMenuItem(
                        text = { Text("Invert selection") },
                        leadingIcon = { Icon(Icons.Rounded.FlipToFront, contentDescription = null) },
                        onClick = { menuOpen = false; onInvert() },
                    )
                }
            }
        }
    }
}

// ---- body -------------------------------------------------------------------------------------

@Composable
private fun FileCollection(
    entries: List<FileEntry>,
    viewMode: ViewMode,
    density: LayoutDensity,
    selection: Set<String>,
    reduce: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onClick: (FileEntry) -> Unit,
    onLongClick: (FileEntry) -> Unit,
) {
    if (viewMode == ViewMode.LIST) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp + LocalFloatingBarInset.current),
        ) {
            items(entries, key = { it.path }) { entry ->
                FileListItem(
                    entry = entry,
                    isSelected = entry.path in selection,
                    density = density,
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                    modifier = if (reduce) {
                        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                    } else {
                        Modifier.animateItem()
                    },
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 112.dp + LocalFloatingBarInset.current),
        ) {
            items(entries, key = { it.path }) { entry ->
                FileGridItem(
                    entry = entry,
                    isSelected = entry.path in selection,
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun FailureState(failure: ListResult.Failure, onRetry: () -> Unit) {
    EmptyState(
        icon = if (failure.reason == ListFailureReason.ACCESS_DENIED) Icons.Rounded.Lock else Icons.Rounded.Warning,
        title = when (failure.reason) {
            ListFailureReason.ACCESS_DENIED -> "Access denied"
            ListFailureReason.NOT_FOUND -> "Folder not found"
            ListFailureReason.NOT_A_DIRECTORY -> "Not a folder"
            ListFailureReason.UNKNOWN -> "Couldn't open this folder"
        },
        message = failure.message,
        action = { Button(onClick = onRetry) { Text("Try again") } },
    )
}

@Composable
private fun ClipboardBar(
    clipboard: FileClipboard,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (clipboard.mode == ClipboardMode.CUT) Icons.Rounded.ContentCut else Icons.Rounded.ContentCopy,
                contentDescription = null,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "${Formatters.plural(clipboard.paths.size, "item")} to ${if (clipboard.mode == ClipboardMode.CUT) "move" else "copy"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!canPaste) {
                    Text("This folder is read-only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onClear) { Text("Clear") }
            Button(onClick = onPaste, enabled = canPaste) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Paste here")
            }
        }
    }
}

@Composable
private fun PasteConflictDialog(
    pending: PendingPaste,
    onConfirm: (ConflictResolution) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(ConflictResolution.KEEP_BOTH) }
    val names = pending.conflictNames
    val shown = names.take(3).joinToString(", ") { "\"$it\"" }
    val more = if (names.size > 3) " and ${names.size - 3} more" else ""
    val verb = if (pending.clipboard.mode == ClipboardMode.CUT) "Move" else "Copy"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name conflict") },
        text = {
            Column {
                Text(
                    "${Formatters.plural(names.size, "item")} already exist${if (names.size == 1) "s" else ""} in this folder: $shown$more.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                ChoiceLine("Keep both", "New items get a number, like \"name (1)\"", choice == ConflictResolution.KEEP_BOTH) {
                    choice = ConflictResolution.KEEP_BOTH
                }
                ChoiceLine("Replace", "Overwrite matching files; folders are merged", choice == ConflictResolution.REPLACE) {
                    choice = ConflictResolution.REPLACE
                }
                ChoiceLine("Skip", "Leave the existing items untouched", choice == ConflictResolution.SKIP) {
                    choice = ConflictResolution.SKIP
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice) }) { Text(verb) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
