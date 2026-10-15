@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.home

import com.nexusarchive.android.ui.components.NexusSnackbarHost
import com.nexusarchive.android.ui.components.LocalFloatingBarInset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.core.RecentsFilter
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.ui.storage.ExternalStorageSection
import com.nexusarchive.android.core.FileTypes
import com.nexusarchive.android.core.StorageVolumeInfo
import com.nexusarchive.android.operations.OperationState
import com.nexusarchive.android.ui.components.ExpressiveProgressBar
import com.nexusarchive.android.ui.components.FileKindIcon
import com.nexusarchive.android.ui.components.PermissionCard
import com.nexusarchive.android.ui.components.SectionHeader
import com.nexusarchive.android.ui.components.StorageCard
import com.nexusarchive.android.ui.components.StoragePermission
import kotlinx.coroutines.launch
import java.io.File

private fun labelFor(path: String, volumes: List<StorageVolumeInfo>): String =
    volumes.firstOrNull { it.path == path }?.label ?: File(path).name.ifEmpty { path }

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    permission: StoragePermission,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> String?,
    onSearch: () -> Unit,
    onOpenOperations: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSafStorage: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val safBookmarks by remember { (context.applicationContext as NexusApplication).safStorage.bookmarks }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shownRecentDirs = remember(settings.recentDirs, settings.recentsRespectHidden) {
        if (settings.recentsRespectHidden) settings.recentDirs.filter { RecentsFilter.isAllowed(it) } else settings.recentDirs
    }
    val shownRecentFiles = remember(settings.recentFiles, settings.recentsRespectHidden) {
        if (settings.recentsRespectHidden) settings.recentFiles.filter { RecentsFilter.isAllowed(it) } else settings.recentFiles
    }
    val operations by vm.operations.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(permission.hasAccess) { vm.refresh() }

    val active = operations.filter { it.isActive }

    Scaffold(snackbarHost = { NexusSnackbarHost(snackbar) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 32.dp + LocalFloatingBarInset.current),
            ) {
                item { HomeHeader(onOpenSettings) }
                item { SearchEntry(onSearch) }
                if (!permission.hasAccess) {
                    item { PermissionCard(onGrant = permission.request, modifier = Modifier.padding(16.dp)) }
                }
                if (active.isNotEmpty()) {
                    item { ActiveOperationsCard(active, onOpenOperations) }
                }

                item { SectionHeader("Storage") }
                if (ui.volumes.isEmpty()) {
                    item {
                        Text(
                            "Android didn't report any mounted storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                items(ui.volumes, key = { "vol:" + it.path }) { volume ->
                    StorageCard(
                        volume = volume,
                        onClick = { onOpenFolder(volume.path) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }

                item(key = "external-storage") {
                    ExternalStorageSection(bookmarks = safBookmarks, onOpen = onOpenSafStorage, onManage = onOpenStorage)
                }

                if (ui.quickFolders.isNotEmpty()) {
                    item { SectionHeader("Quick access") }
                    item {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ui.quickFolders.forEach { quick ->
                                AssistChip(
                                    onClick = { onOpenFolder(quick.path) },
                                    label = { Text(quick.label) },
                                    leadingIcon = { FileKindIcon(FileKind.FOLDER, size = 24.dp) },
                                )
                            }
                        }
                    }
                }

                if (settings.bookmarks.isNotEmpty()) {
                    item { SectionHeader("Bookmarks") }
                    items(settings.bookmarks, key = { "bm:$it" }) { path ->
                        PathRow(
                            title = labelFor(path, ui.volumes),
                            subtitle = path,
                            kind = FileKind.FOLDER,
                            onClick = { onOpenFolder(path) },
                            trailing = {
                                IconButton(onClick = { vm.removeBookmark(path) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove bookmark ${labelFor(path, ui.volumes)}")
                                }
                            },
                        )
                    }
                }

                if (settings.showRecents && shownRecentDirs.isNotEmpty()) {
                    item { SectionHeader("Recent folders") }
                    items(shownRecentDirs.take(5), key = { "rd:$it" }) { path ->
                        PathRow(
                            title = labelFor(path, ui.volumes),
                            subtitle = path,
                            kind = FileKind.FOLDER,
                            onClick = { onOpenFolder(path) },
                        )
                    }
                }

                if (settings.showRecents && shownRecentFiles.isNotEmpty()) {
                    item { SectionHeader("Recent files") }
                    items(shownRecentFiles.take(5), key = { "rf:$it" }) { path ->
                        val name = File(path).name
                        PathRow(
                            title = name,
                            subtitle = File(path).parent ?: path,
                            kind = FileTypes.kindOf(name, false),
                            onClick = {
                                val error = onOpenFile(path)
                                if (error != null) scope.launch { snackbar.showSnackbar(error) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderZip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        Column {
            Text("Nexus Archive", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Files and storage, on your device only",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(56.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Search in a folder",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActiveOperationsCard(active: List<OperationState>, onOpen: () -> Unit) {
    val first = active.first()
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (active.size == 1) "1 operation running" else "${active.size} operations running",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(first.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            val fraction = first.fraction
            if (fraction != null) {
                ExpressiveProgressBar(
                    fraction = fraction,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            TextButton(onClick = onOpen) { Text("View progress") }
        }
    }
}

@Composable
private fun PathRow(
    title: String,
    subtitle: String,
    kind: FileKind,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { FileKindIcon(kind, size = 40.dp) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    )
}
