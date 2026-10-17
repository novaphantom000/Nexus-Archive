@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.core.StorageVolumeInfo
import java.io.File

/** The label shown for a tab: the storage name for a volume root, otherwise the folder's own name. */
fun tabLabel(path: String, volumes: List<StorageVolumeInfo>): String =
    volumes.firstOrNull { it.path == path }?.label ?: File(path).name.ifEmpty { path }

/** Lists every tab with grid or list layout; switch, close (never below two), reorder or add a tab. */
@Composable
fun TabSwitcherSheet(
    tabs: List<BrowserTab>,
    activeIndex: Int,
    volumes: List<StorageVolumeInfo>,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    var grid by rememberSaveable { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tabs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { grid = !grid }) {
                    Icon(
                        imageVector = if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = if (grid) "Show tabs as a list" else "Show tabs as a grid",
                    )
                }
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("  New tab")
                }
            }
            Spacer(Modifier.height(12.dp))
            val canClose = tabs.size > 2
            if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(tabs.size, key = { tabs[it].id }) { index ->
                        TabCard(
                            tab = tabs[index],
                            volumes = volumes,
                            index = index,
                            lastIndex = tabs.lastIndex,
                            active = index == activeIndex,
                            canClose = canClose,
                            onSelect = { onSelect(index) },
                            onClose = { onClose(index) },
                            onMove = onMove,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(tabs.size, key = { tabs[it].id }) { index ->
                        TabCard(
                            tab = tabs[index],
                            volumes = volumes,
                            index = index,
                            lastIndex = tabs.lastIndex,
                            active = index == activeIndex,
                            canClose = canClose,
                            onSelect = { onSelect(index) },
                            onClose = { onClose(index) },
                            onMove = onMove,
                        )
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    volumes: List<StorageVolumeInfo>,
    index: Int,
    lastIndex: Int,
    active: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tab ${index + 1}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        tabLabel(tab.path, volumes),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onClose, enabled = canClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close tab ${index + 1}")
                }
            }
            Text(
                tab.path,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move tab earlier")
                }
                IconButton(onClick = { onMove(index, index + 1) }, enabled = index < lastIndex) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move tab later")
                }
            }
        }
    }
}
