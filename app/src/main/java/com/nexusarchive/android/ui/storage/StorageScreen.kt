@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.storage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.storage.AccessState
import com.nexusarchive.android.storage.SafBookmark
import com.nexusarchive.android.storage.SafKind
import com.nexusarchive.android.ui.components.ConfirmDialog
import com.nexusarchive.android.ui.components.TextInputDialog

fun safKindIcon(kind: SafKind): ImageVector = when (kind) {
    SafKind.FOLDER -> Icons.Rounded.Folder
    SafKind.SD_CARD -> Icons.Rounded.SdCard
    SafKind.USB -> Icons.Rounded.Usb
    SafKind.PROVIDER -> Icons.Rounded.Storage
}

private class AddOption(val kind: SafKind, val title: String, val description: String)

private val addOptions = listOf(
    AddOption(
        SafKind.FOLDER,
        "Select a folder",
        "Pick any folder Android lets apps use. Read and write access, kept after restarts.",
    ),
    AddOption(
        SafKind.SD_CARD,
        "SD card folder",
        "In the picker, open the menu and choose your SD card, then a folder on it. Android may not allow the card's top level.",
    ),
    AddOption(
        SafKind.USB,
        "USB drive folder",
        "Plug the drive in first. If Android lists it in the picker you can choose a folder. The saved link only works while the drive is connected.",
    ),
    AddOption(
        SafKind.PROVIDER,
        "Folder from another app",
        "Some apps and cloud services expose folders in the picker. What you can do (write, rename, seek) depends on that app.",
    ),
)

/** Manage saved external storage and add new folders through Android's Storage Access Framework. */
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    onOpenStorage: (String) -> Unit,
    vm: StorageViewModel = viewModel(),
) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val states by vm.states.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf(SafKind.FOLDER) }
    var reauthId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<SafBookmark?>(null) }
    var removeTarget by remember { mutableStateOf<SafBookmark?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val id = reauthId
            if (id != null) vm.reauthorize(id, uri) else vm.addTree(uri, pendingKind)
        }
        reauthId = null
    }

    LaunchedEffect(Unit) { vm.refreshStates() }
    LaunchedEffect(bookmarks.size) { vm.refreshStates() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("External storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add storage") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("How this works", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Android only lets an app use folders you choose yourself. Nexus Archive saves that permission so it survives restarts. " +
                                    "It can be revoked by Android or by you, and a drive that is unplugged becomes unavailable until you reconnect it. " +
                                    "Removing a shortcut here never deletes any files.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                if (bookmarks.isEmpty()) {
                    item {
                        Text(
                            "No external storage added yet. Tap Add storage to choose a folder on an SD card, USB drive or another app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkCard(
                        bookmark = bookmark,
                        state = states[bookmark.id],
                        onOpen = { onOpenStorage(bookmark.id) },
                        onReauthorize = {
                            reauthId = bookmark.id
                            picker.launch(Uri.parse(bookmark.treeUri))
                        },
                        onTest = { vm.test(bookmark) },
                        onRename = { renameTarget = bookmark },
                        onRemove = { removeTarget = bookmark },
                    )
                }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Text("Add external storage", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "All four options open Android's own folder picker. They differ only in the label we save and the tips shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            addOptions.forEach { option ->
                ListItem(
                    headlineContent = { Text(option.title) },
                    supportingContent = { Text(option.description) },
                    leadingContent = { Icon(safKindIcon(option.kind), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        showAdd = false
                        pendingKind = option.kind
                        reauthId = null
                        picker.launch(null)
                    },
                )
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Rename shortcut",
            initialValue = target.label,
            label = "Display name",
            confirmText = "Rename",
            validate = { if (it.length > 60) "Use 60 characters or fewer." else null },
            onConfirm = {
                renameTarget = null
                vm.rename(target.id, it)
            },
            onDismiss = { renameTarget = null },
        )
    }

    removeTarget?.let { target ->
        ConfirmDialog(
            title = "Remove \"${target.label}\"?",
            text = "This removes the shortcut and Nexus Archive's saved access. Your files are not deleted.",
            confirmText = "Remove",
            destructive = true,
            onConfirm = {
                removeTarget = null
                vm.remove(target)
            },
            onDismiss = { removeTarget = null },
        )
    }
}

@Composable
private fun BookmarkCard(
    bookmark: SafBookmark,
    state: AccessState?,
    onOpen: () -> Unit,
    onReauthorize: () -> Unit,
    onTest: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val needsFix = state == AccessState.REVOKED || state == AccessState.UNAVAILABLE
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(safKindIcon(bookmark.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(bookmark.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        "${bookmark.kind.label} \u00B7 ${state?.label ?: "Checking\u2026"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (needsFix) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions for ${bookmark.label}") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Test access") }, onClick = { menuOpen = false; onTest() })
                        DropdownMenuItem(text = { Text("Reauthorize\u2026") }, onClick = { menuOpen = false; onReauthorize() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() })
                    }
                }
            }
            Text(
                "Last opened: ${if (bookmark.lastAccessedAt == bookmark.createdAt) "never" else Formatters.dateTime(bookmark.lastAccessedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state == AccessState.REVOKED) {
                Text(
                    "Android no longer lets this app use the folder. Choose it again to restore access.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (state == AccessState.UNAVAILABLE) {
                Text(
                    "The folder can't be reached. If this is a USB drive or SD card, reconnect it and test again.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needsFix) {
                    Button(onClick = onReauthorize) { Text("Reauthorize") }
                    OutlinedButton(onClick = onTest) { Text("Test again") }
                } else {
                    Button(onClick = onOpen) { Text("Open") }
                }
            }
        }
    }
}
