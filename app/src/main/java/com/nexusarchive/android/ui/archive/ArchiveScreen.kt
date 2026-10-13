@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.archive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusarchive.android.archive.ArchiveFormats
import com.nexusarchive.android.archive.ArchiveTree
import com.nexusarchive.android.archive.TreeNode
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.core.FileTypes
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.ui.components.EmptyState
import com.nexusarchive.android.ui.components.SelectableLeading
import com.nexusarchive.android.ui.components.SelectionPill
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.Motion
import java.io.File

/** Browses an archive's contents without extracting it, and starts extraction and tests. */
@Composable
fun ArchiveScreen(
    path: String,
    onBack: () -> Unit,
    onOpenOperations: () -> Unit,
    vm: ArchiveViewModel = viewModel(),
) {
    LaunchedEffect(path) { vm.load(path) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pending by vm.pendingAction.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showExtract by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val archiveFile = remember(path) { File(path) }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val result = snackbar.showSnackbar(message, actionLabel = "View", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onOpenOperations()
        }
    }

    BackHandler {
        if (!vm.navigateUp()) onBack()
    }

    val listing = ui.listing
    val reduce = LocalReduceMotion.current

    Scaffold(
        topBar = {
            if (ui.selection.isNotEmpty() && listing != null) {
                SelectionPill(count = ui.selection.size, onClose = vm::clearSelection) {
                    IconButton(onClick = { showExtract = true }) { Icon(Icons.Rounded.Unarchive, contentDescription = "Extract selected") }
                    IconButton(onClick = { vm.selectAllIn(ArchiveTree.children(listing.entries, ui.currentDir).map { it.path }) }) {
                        Icon(Icons.Rounded.SelectAll, contentDescription = "Select all in this folder")
                    }
                }
            } else TopAppBar(
                title = {
                    Text(
                        text = if (ui.currentDir.isEmpty()) archiveFile.name else ui.currentDir.substringAfterLast('/'),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (!vm.navigateUp()) onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (listing != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
                            }
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
                                if (ui.selection.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Clear selection") },
                                        onClick = {
                                            menuOpen = false
                                            vm.clearSelection()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (listing != null && ArchiveFormats.capabilities(listing.format).extract) {
                ExtendedFloatingActionButton(
                    onClick = { showExtract = true },
                    icon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                    text = { Text(if (ui.selection.isEmpty()) "Extract all" else "Extract ${ui.selection.size}") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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
                    val dirRows = remember(listing, dir) { ArchiveTree.children(listing.entries, dir) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                    ) {
                        item(key = "header") { ArchiveHeader(ui, archiveFile) }
                        if (dirRows.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    "This folder is empty.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                        items(dirRows, key = { it.path }) { node ->
                            NodeRow(
                                node = node,
                                selected = node.path in ui.selection,
                                onClick = {
                                    if (node.isDirectory) vm.openFolder(node.path) else vm.toggleSelect(node.path)
                                },
                                onToggle = { vm.toggleSelect(node.path) },
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

    if (ui.passwordRequired) {
        val message = when (pending) {
            PendingAction.TEST -> "Enter the password to test this archive."
            PendingAction.EXTRACT -> "Enter the password to extract from this archive."
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

@Composable
private fun ArchiveHeader(ui: ArchiveUiState, file: File) {
    val listing = ui.listing ?: return
    val caps = ArchiveFormats.capabilities(listing.format)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
            Spacer(Modifier.height(2.dp))
            Text(
                "Browsing reads only the archive's table of contents where the format provides one. TAR-based archives must be read through once to list them.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NodeRow(node: TreeNode, selected: Boolean, onClick: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val kind = FileTypes.kindOf(node.name, node.isDirectory)
    ListItem(
        headlineContent = { Text(node.name, maxLines = 2) },
        supportingContent = {
            val parts = ArrayList<String>()
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
                size = 44.dp,
                onToggle = onToggle,
            )
        },
        colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

private fun folderDepth(dir: String): Int = if (dir.isEmpty()) 0 else dir.count { it == '/' } + 1
