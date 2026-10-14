@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.storage

import com.nexusarchive.android.ui.components.LocalFloatingBarInset
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.core.FileNames
import com.nexusarchive.android.core.FileTypes
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.storage.AccessState
import com.nexusarchive.android.storage.SafItem
import com.nexusarchive.android.ui.components.ConfirmDialog
import com.nexusarchive.android.ui.components.EmptyState
import com.nexusarchive.android.ui.components.SelectableLeading
import com.nexusarchive.android.ui.components.SelectionPill
import com.nexusarchive.android.ui.components.TextInputDialog

private fun openDocument(context: Context, item: SafItem): String? {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri, item.mimeType.ifEmpty { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "No app found that can open this file."
    } catch (e: SecurityException) {
        "Android didn't allow opening this file."
    }
}

/** Browses a saved document-tree folder (SD card, USB drive or provider folder). */
@Composable
fun SafBrowseScreen(
    storageId: String,
    onBack: () -> Unit,
    onManageStorage: () -> Unit,
    vm: SafBrowseViewModel = viewModel(),
) {
    LaunchedEffect(storageId) { vm.load(storageId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SafItem?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var copyTarget by remember { mutableStateOf<SafItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    BackHandler {
        when {
            ui.selection.isNotEmpty() -> vm.clearSelection()
            !vm.navigateUp() -> onBack()
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.importFiles(uris)
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val item = copyTarget
        if (uri != null && item != null) vm.copyOut(item, uri)
        copyTarget = null
    }

    val selected = ui.items.filter { it.documentId in ui.selection }
    val single = if (selected.size == 1) selected[0] else null
    val title = ui.crumbs.lastOrNull()?.name ?: "External storage"

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionPill(count = selected.size, onClose = vm::clearSelection) {
                    IconButton(onClick = { confirmDelete = true }, enabled = selected.all { it.canDelete }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = vm::selectAll) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                                enabled = single != null && single.canRename,
                                onClick = {
                                    menuOpen = false
                                    renameTarget = single
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save a copy\u2026") },
                                leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                enabled = single != null && !single.isDirectory,
                                onClick = {
                                    menuOpen = false
                                    if (single != null) {
                                        copyTarget = single
                                        savePicker.launch(single.name)
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { if (!vm.navigateUp()) onBack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNewFolder = true }, enabled = ui.canCreateHere) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New folder")
                        }
                        IconButton(onClick = { importPicker.launch(arrayOf("*/*")) }, enabled = ui.canCreateHere) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = "Copy files here")
                        }
                        IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalFloatingBarInset.current)) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val failure = ui.failure
            when {
                failure != null -> EmptyState(
                    icon = Icons.Rounded.Warning,
                    title = if (failure.state == AccessState.REVOKED) "Access needed" else "Can't reach this storage",
                    message = failure.message,
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onManageStorage) { Text("Manage storage") }
                            OutlinedButton(onClick = vm::refresh) { Text("Try again") }
                        }
                    },
                )
                ui.loading && ui.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    if (ui.loading) LinearProgressIndicator(Modifier.padding(0.dp))
                    if (ui.items.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.CreateNewFolder,
                            title = "This folder is empty",
                            message = if (ui.canCreateHere) "Use the buttons above to add a folder or copy files here." else "This location is read-only.",
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalFloatingBarInset.current)) {
                            items(ui.items, key = { it.documentId }) { item ->
                                val isSelected = item.documentId in ui.selection
                                ListItem(
                                    headlineContent = { Text(item.name, maxLines = 2) },
                                    supportingContent = {
                                        val parts = ArrayList<String>()
                                        if (item.isDirectory) parts.add("Folder") else parts.add(Formatters.bytes(item.sizeBytes))
                                        if (item.modifiedMs > 0) parts.add(Formatters.date(item.modifiedMs))
                                        Text(parts.joinToString(" \u00B7 "))
                                    },
                                    leadingContent = {
                                        SelectableLeading(
                                            kind = FileTypes.kindOf(item.name, item.isDirectory),
                                            selected = isSelected,
                                            size = 44.dp,
                                            onToggle = { vm.toggleSelect(item.documentId) },
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    ),
                                    modifier = Modifier.combinedClickable(
                                        onClick = {
                                            when {
                                                selected.isNotEmpty() -> vm.toggleSelect(item.documentId)
                                                item.isDirectory -> vm.openFolder(item)
                                                FileTypes.kindOf(item.name, false) == FileKind.ARCHIVE ->
                                                    vm.postMessage("Archives on external storage can't be opened in place yet. Use Save a copy to put it in internal storage first.")
                                                else -> openDocument(context, item)?.let(vm::postMessage)
                                            }
                                        },
                                        onLongClick = { vm.toggleSelect(item.documentId) },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        TextInputDialog(
            title = "New folder",
            initialValue = "",
            label = "Name",
            confirmText = "Create",
            validate = { name ->
                FileNames.validate(name)
                    ?: if (ui.items.any { it.name.equals(name, ignoreCase = true) }) "An item named \"$name\" already exists here." else null
            },
            onConfirm = {
                showNewFolder = false
                vm.createFolder(it)
            },
            onDismiss = { showNewFolder = false },
        )
    }

    renameTarget?.let { item ->
        TextInputDialog(
            title = "Rename",
            initialValue = item.name,
            label = "Name",
            confirmText = "Rename",
            selectBaseNameOnly = !item.isDirectory,
            validate = { name ->
                FileNames.validate(name)
                    ?: if (name != item.name && ui.items.any { it.name.equals(name, ignoreCase = true) }) "An item named \"$name\" already exists here." else null
            },
            onConfirm = {
                renameTarget = null
                vm.rename(item, it)
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${selected.size} item(s)?",
            text = "This permanently deletes the selected items, including anything inside folders. It can't be undone.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.deleteSelected()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
