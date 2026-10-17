@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.nexusarchive.android.ui.archive

import com.nexusarchive.android.data.ViewMode
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import com.nexusarchive.android.ui.components.topFade
import com.nexusarchive.android.ui.components.NexusSnackbarHost
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusarchive.android.archive.ArchiveFormats
import com.nexusarchive.android.archive.ArchiveSortField
import com.nexusarchive.android.archive.ArchiveTree
import com.nexusarchive.android.archive.TreeNode
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.core.FileTypes
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.ui.browse.Breadcrumb
import com.nexusarchive.android.ui.browse.BreadcrumbBar
import com.nexusarchive.android.ui.browse.FileActions
import com.nexusarchive.android.ui.components.EmptyState
import com.nexusarchive.android.archive.ArchivePaths
import com.nexusarchive.android.ui.components.LocalBarsCollapsed
import com.nexusarchive.android.ui.components.PathEditorRow
import com.nexusarchive.android.ui.components.LocalFloatingBarInset
import com.nexusarchive.android.ui.components.LocationOption
import com.nexusarchive.android.ui.components.LocationPickerTitle
import com.nexusarchive.android.ui.components.SelectableLeading
import com.nexusarchive.android.ui.components.SelectionPill
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.Motion
import java.io.File

/**
 * Browses an archive with the same layout as the file browser: back, name with a location dropdown,
 * search, sort, view mode and menu on top, the path underneath, and the Extract button as the main action.
 * Tapping an icon or long-pressing selects; tapping a file asks whether to open or extract it.
 */
@Composable
fun ArchiveScreen(
    path: String,
    locations: List<LocationOption>,
    onPickLocation: (LocationOption) -> Unit,
    onBack: () -> Unit,
    onOpenOperations: () -> Unit,
    onCopyToClipboard: (List<String>) -> Unit,
    vm: ArchiveViewModel = viewModel(),
) {
    LaunchedEffect(path) { vm.load(path) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pending by vm.pendingAction.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showExtract by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    var filePrompt by remember { mutableStateOf<TreeNode?>(null) }
    var pathMenuOpen by remember { mutableStateOf(false) }
    var editingPath by remember { mutableStateOf(false) }
    var propertiesFor by remember { mutableStateOf<TreeNode?>(null) }
    val archiveFile = remember(path) { File(path) }
    val reduce = LocalReduceMotion.current
    val listing = ui.listing
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val savedScale by vm.itemScale.collectAsStateWithLifecycle()
    var liveScale by remember(savedScale) { mutableStateOf(savedScale) }
    val searching = ui.searchActive && ui.query.isNotBlank()
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val result = snackbar.showSnackbar(message, actionLabel = "View", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onOpenOperations()
        }
    }
    LaunchedEffect(Unit) {
        vm.staged.collect { staged ->
            when (staged.purpose) {
                StagePurpose.OPEN -> FileActions.open(context, staged.files[0], chooser = false)?.let { snackbar.showSnackbar(it) }
                StagePurpose.OPEN_WITH -> FileActions.open(context, staged.files[0], chooser = true)?.let { snackbar.showSnackbar(it) }
                StagePurpose.SHARE -> FileActions.share(context, staged.files)?.let { snackbar.showSnackbar(it) }
                StagePurpose.COPY -> {
                    onCopyToClipboard(staged.files.map { it.path })
                    vm.clearSelection()
                    snackbar.showSnackbar("Copied ${Formatters.plural(staged.files.size, "item")}. Open a folder and tap Paste here.")
                }
            }
        }
    }
    LaunchedEffect(ui.searchActive) { if (ui.searchActive) searchFocus.requestFocus() }

    BackHandler {
        when {
            ui.selection.isNotEmpty() -> vm.clearSelection()
            ui.searchActive -> vm.setSearchActive(false)
            !vm.navigateUp() -> onBack()
        }
    }

    // Rows of the folder being shown (or search results), already sorted.
    val currentRows: List<TreeNode> = remember(listing, ui.currentDir, ui.sortField, ui.sortAscending, ui.query, ui.searchActive) {
        when {
            listing == null -> emptyList()
            searching -> ArchiveTree.sorted(ArchiveTree.search(listing.entries, ui.query), ui.sortField, ui.sortAscending)
            else -> ArchiveTree.sorted(ArchiveTree.children(listing.entries, ui.currentDir), ui.sortField, ui.sortAscending)
        }
    }
    val selectedNodes = currentRows.filter { it.path in ui.selection }
    val single = if (selectedNodes.size == 1) selectedNodes[0] else null
    val selectionActive = ui.selection.isNotEmpty()

    val crumbs = remember(archiveFile.name, ui.currentDir) {
        val list = ArrayList<Breadcrumb>()
        list.add(Breadcrumb(archiveFile.name, ""))
        var acc = ""
        for (segment in ui.currentDir.split('/').filter { it.isNotEmpty() }) {
            acc = if (acc.isEmpty()) segment else "$acc/$segment"
            list.add(Breadcrumb(segment, acc))
        }
        list
    }

    fun nodeClick(node: TreeNode) {
        when {
            selectionActive -> vm.toggleSelect(node.path)
            node.isDirectory -> vm.openFolder(node.path)
            else -> filePrompt = node
        }
    }

    Scaffold(
        topBar = {
            if (selectionActive && listing != null) {
                SelectionPill(count = ui.selection.size, onClose = vm::clearSelection) {
                    IconButton(onClick = { vm.stage(StagePurpose.COPY, ui.selection) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = { vm.stage(StagePurpose.SHARE, ui.selection) }, enabled = selectedNodes.none { it.isDirectory }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { vm.selectAllIn(currentRows.map { it.path }) }) {
                        Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
                    }
                    IconButton(onClick = { vm.invertIn(currentRows.map { it.path }) }) {
                        Icon(Icons.Rounded.FlipToFront, contentDescription = "Invert selection")
                    }
                    Box {
                        IconButton(onClick = { selectionMenuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
                        DropdownMenu(expanded = selectionMenuOpen, onDismissRequest = { selectionMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Extract\u2026") },
                                leadingIcon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                                onClick = {
                                    selectionMenuOpen = false
                                    showExtract = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Open with\u2026") },
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                                enabled = single != null && !single.isDirectory,
                                onClick = {
                                    selectionMenuOpen = false
                                    if (single != null) vm.stage(StagePurpose.OPEN_WITH, setOf(single.path))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                enabled = false,
                                onClick = {},
                            )
                            DropdownMenuItem(
                                text = { Text("Properties") },
                                enabled = single != null,
                                onClick = {
                                    selectionMenuOpen = false
                                    propertiesFor = single
                                },
                            )
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        LocationPickerTitle(
                            title = if (ui.currentDir.isEmpty()) archiveFile.name else ui.currentDir.substringAfterLast('/'),
                            subtitle = if (listing == null) "" else Formatters.plural(currentRows.size, "item"),
                            locations = locations,
                            onPick = onPickLocation,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!vm.navigateUp()) onBack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (listing != null) {
                            IconButton(onClick = { vm.setSearchActive(!ui.searchActive) }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search in archive")
                            }
                            Box {
                                IconButton(onClick = { sortOpen = true }) { Icon(Icons.Rounded.FilterList, contentDescription = "Sort") }
                                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                    val fields = listOf(
                                        ArchiveSortField.NAME to "Name",
                                        ArchiveSortField.SIZE to "Size",
                                        ArchiveSortField.DATE to "Date",
                                        ArchiveSortField.TYPE to "Type",
                                    )
                                    fields.forEach { (field, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            trailingIcon = {
                                                if (ui.sortField == field) Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                            },
                                            onClick = {
                                                sortOpen = false
                                                vm.setSortField(field)
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("List view") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null) },
                                        trailingIcon = {
                                            if (viewMode == ViewMode.LIST) Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                        },
                                        onClick = { vm.setViewMode(ViewMode.LIST) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Grid view") },
                                        leadingIcon = { Icon(Icons.Rounded.GridView, contentDescription = null) },
                                        trailingIcon = {
                                            if (viewMode == ViewMode.GRID) Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                        },
                                        onClick = { vm.setViewMode(ViewMode.GRID) },
                                    )
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                        Text(
                                            if (viewMode == ViewMode.LIST) "Icon and row size" else "Tile size",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Slider(
                                            value = liveScale,
                                            onValueChange = { liveScale = it },
                                            onValueChangeFinished = { vm.setItemScale(liveScale) },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.widthIn(min = 200.dp),
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Descending order") },
                                        trailingIcon = {
                                            if (!ui.sortAscending) Icon(Icons.Rounded.Check, contentDescription = "On")
                                        },
                                        onClick = {
                                            sortOpen = false
                                            vm.toggleSortDirection()
                                        },
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Test archive") },
                                        onClick = {
                                            menuOpen = false
                                            vm.requestTest()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Enter password\u2026") },
                                        onClick = {
                                            menuOpen = false
                                            vm.promptForPassword()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Select all") },
                                        onClick = {
                                            menuOpen = false
                                            vm.selectAllIn(currentRows.map { it.path })
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { NexusSnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = listing != null && !selectionActive && !LocalBarsCollapsed.current &&
                    ArchiveFormats.capabilities(listing.format).extract,
                modifier = Modifier.padding(bottom = LocalFloatingBarInset.current),
                enter = fadeIn(Motion.effects(reduce)) + scaleIn(Motion.spatial(reduce), initialScale = 0.6f),
                exit = fadeOut(Motion.effects(reduce)) + scaleOut(Motion.spatial(reduce), targetScale = 0.6f),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showExtract = true },
                    icon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                    text = { Text("Extract") },
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (listing != null) {
                if (editingPath) {
                    PathEditorRow(
                        initial = ui.currentDir,
                        label = "Go to folder inside the archive",
                        onGo = { text ->
                            val normalized = ArchivePaths.normalize(text)
                            when {
                                normalized == null -> "That isn't a valid folder path."
                                normalized.isEmpty() || listing.entries.any { it.path == normalized && it.isDirectory } ||
                                    listing.entries.any { it.path.startsWith("$normalized/") } -> {
                                    vm.openFolder(normalized)
                                    editingPath = false
                                    null
                                }
                                else -> "That folder isn't in this archive."
                            }
                        },
                        onCancel = { editingPath = false },
                    )
                } else {
                    Box {
                        BreadcrumbBar(
                            crumbs = crumbs,
                            onNavigate = vm::openFolder,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            onLongPress = { pathMenuOpen = true },
                        )
                        DropdownMenu(expanded = pathMenuOpen, onDismissRequest = { pathMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Copy path") },
                                onClick = {
                                    pathMenuOpen = false
                                    val inside = if (ui.currentDir.isEmpty()) "" else "/" + ui.currentDir
                                    FileActions.copyText(context, "Path", archiveFile.path + inside)
                                    vm.postMessage("Path copied")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit path") },
                                onClick = {
                                    pathMenuOpen = false
                                    editingPath = true
                                },
                            )
                        }
                    }
                }
            }
            if (ui.searchActive) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search names in this archive") },
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { vm.setSearchActive(false) }) { Icon(Icons.Rounded.Close, contentDescription = "Close search") }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .focusRequester(searchFocus),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ui.error != null -> EmptyState(
                        icon = Icons.Rounded.Warning,
                        title = "Can't open this archive",
                        message = ui.error ?: "",
                        action = { Button(onClick = vm::retry) { Text("Try again") } },
                    )
                    listing == null -> EmptyState(
                        icon = Icons.Rounded.Lock,
                        title = "Password needed",
                        message = "This archive is password protected. Enter the password to see its contents.",
                        action = { Button(onClick = vm::promptForPassword) { Text("Enter password") } },
                    )
                    searching -> ArchiveRows(
                        rows = currentRows,
                        grid = viewMode == ViewMode.GRID,
                        itemScale = liveScale,
                        selection = ui.selection,
                        showPath = true,
                        header = null,
                        emptyText = "No names in this archive match your search.",
                        onClick = ::nodeClick,
                        onLongClick = { vm.rangeSelectTo(it.path, currentRows.map { row -> row.path }) },
                        onToggle = { vm.toggleSelect(it.path) },
                        reduce = reduce,
                    )
                    else -> AnimatedContent(
                        targetState = ui.currentDir,
                        transitionSpec = {
                            if (reduce) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val forward = folderDepth(targetState) > folderDepth(initialState)
                                (slideInHorizontally(Motion.spatial(reduce)) { if (forward) it / 3 else -it / 3 } + fadeIn(Motion.effects(reduce))) togetherWith
                                    (slideOutHorizontally(Motion.spatial(reduce)) { if (forward) -it / 3 else it / 3 } + fadeOut(Motion.effects(reduce)))
                            }
                        },
                        label = "archiveFolder",
                    ) { dir ->
                        val dirRows = remember(listing, dir, ui.sortField, ui.sortAscending) {
                            ArchiveTree.sorted(ArchiveTree.children(listing.entries, dir), ui.sortField, ui.sortAscending)
                        }
                        ArchiveRows(
                            rows = dirRows,
                            grid = viewMode == ViewMode.GRID,
                            itemScale = liveScale,
                            selection = ui.selection,
                            showPath = false,
                            header = { ArchiveHeader(ui, archiveFile) },
                            emptyText = "This folder is empty.",
                            onClick = ::nodeClick,
                            onLongClick = { vm.rangeSelectTo(it.path, dirRows.map { row -> row.path }) },
                            onToggle = { vm.toggleSelect(it.path) },
                            reduce = reduce,
                        )
                    }
                }
            }
        }
    }

    if (showExtract) {
        ExtractDialog(
            selectedCount = ui.selection.size,
            archiveBaseName = ArchiveFormats.baseName(archiveFile.name),
            onStart = { request ->
                showExtract = false
                vm.requestExtract(request)
            },
            onDismiss = { showExtract = false },
        )
    }

    filePrompt?.let { node ->
        AlertDialog(
            onDismissRequest = { filePrompt = null },
            title = { Text(node.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Open") },
                        supportingContent = { Text("Extract a temporary copy and open it in another app") },
                        leadingContent = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            filePrompt = null
                            vm.stage(StagePurpose.OPEN, setOf(node.path))
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Extract\u2026") },
                        supportingContent = { Text("Choose where this file goes") },
                        leadingContent = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            filePrompt = null
                            vm.selectOnly(node.path)
                            showExtract = true
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { filePrompt = null }) { Text("Cancel") } },
        )
    }

    propertiesFor?.let { node ->
        EntryPropertiesDialog(
            node = node,
            info = listing?.entries?.firstOrNull { it.path == node.path },
            onDismiss = { propertiesFor = null },
        )
    }

    if (ui.staging) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparing\u2026") },
            text = {
                Column {
                    Text("Extracting a temporary copy.")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = vm::cancelStaging) { Text("Cancel") } },
        )
    }

    if (ui.passwordRequired) {
        val message = when (pending) {
            PendingAction.TEST -> "Enter the password to test this archive."
            PendingAction.EXTRACT -> "Enter the password to extract from this archive."
            PendingAction.STAGE -> "Enter the password to open these files."
            PendingAction.NONE -> "Enter the password for this archive. It is kept in memory only while this screen is open."
        }
        PasswordDialog(
            title = "Password required",
            message = message,
            error = ui.passwordError,
            busy = ui.checkingPassword,
            confirmField = false,
            onSubmit = vm::submitPassword,
            onDismiss = vm::cancelPassword,
        )
    }
}

private fun folderDepth(dir: String): Int = if (dir.isEmpty()) 0 else dir.count { it == '/' } + 1

@Composable
private fun ArchiveRows(
    rows: List<TreeNode>,
    grid: Boolean,
    itemScale: Float,
    selection: Set<String>,
    showPath: Boolean,
    header: (@Composable () -> Unit)?,
    emptyText: String,
    onClick: (TreeNode) -> Unit,
    onLongClick: (TreeNode) -> Unit,
    onToggle: (TreeNode) -> Unit,
    reduce: Boolean,
) {
    val bottom = 96.dp + LocalFloatingBarInset.current
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive((88f + 100f * itemScale).dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().topFade(gridState.canScrollBackward),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = bottom),
        ) {
            items(rows, key = { it.path }) { node ->
                ArchiveCell(
                    node = node,
                    itemScale = itemScale,
                    selected = node.path in selection,
                    onClick = { onClick(node) },
                    onLongClick = { onLongClick(node) },
                    onToggle = { onToggle(node) },
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().topFade(listState.canScrollBackward),
            state = listState,
            contentPadding = PaddingValues(bottom = bottom),
        ) {
            if (header != null) item(key = "header") { header() }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(rows, key = { it.path }) { node ->
                ArchiveRow(
                    node = node,
                    itemScale = itemScale,
                    selected = node.path in selection,
                    showPath = showPath,
                    onClick = { onClick(node) },
                    onLongClick = { onLongClick(node) },
                    onToggle = { onToggle(node) },
                    modifier = if (reduce) {
                        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                    } else {
                        Modifier.animateItem()
                    },
                )
            }
        }
    }
}

@Composable
private fun ArchiveHeader(ui: ArchiveUiState, file: File) {
    val listing = ui.listing ?: return
    val caps = ArchiveFormats.capabilities(listing.format)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${listing.format.label} \u00B7 ${Formatters.bytes(file.length())} \u00B7 ${Formatters.plural(listing.entries.size, "entry").replace("entrys", "entries")}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(caps.note, style = MaterialTheme.typography.bodySmall)
            if (listing.encrypted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Contains password-protected files", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (listing.unsafeEntryCount > 0) {
                Text(
                    "${listing.unsafeEntryCount} entries have unsafe paths (absolute or containing \"..\") and will be skipped.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ArchiveRow(
    node: TreeNode,
    itemScale: Float,
    selected: Boolean,
    showPath: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = FileTypes.kindOf(node.name, node.isDirectory)
    ListItem(
        headlineContent = { Text(node.name, maxLines = 2) },
        supportingContent = {
            val parts = ArrayList<String>()
            if (showPath) parts.add(node.path.substringBeforeLast('/', "").ifEmpty { "Top level" })
            if (node.isDirectory) {
                parts.add("Folder")
            } else {
                parts.add(Formatters.bytes(node.size))
                if (node.modifiedMs > 0) parts.add(Formatters.date(node.modifiedMs))
                if (node.encrypted) parts.add("Encrypted")
            }
            Text(parts.joinToString(" \u00B7 "))
        },
        leadingContent = {
            SelectableLeading(
                kind = if (node.isDirectory) FileKind.FOLDER else kind,
                selected = selected,
                size = (32f + 40f * itemScale).dp,
                onToggle = onToggle,
            )
        },
        colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun ArchiveCell(
    node: TreeNode,
    itemScale: Float,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val kind = FileTypes.kindOf(node.name, node.isDirectory)
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SelectableLeading(
                kind = if (node.isDirectory) FileKind.FOLDER else kind,
                selected = selected,
                size = (40f + 48f * itemScale).dp,
                onToggle = onToggle,
            )
            Text(
                node.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
