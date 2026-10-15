@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.search

import com.nexusarchive.android.ui.components.topFade
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.core.FileTypes
import com.nexusarchive.android.core.FilterKind
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.core.StorageVolumeInfo
import com.nexusarchive.android.ui.browse.FileActions
import com.nexusarchive.android.ui.components.EmptyState
import com.nexusarchive.android.ui.components.KindIconOrThumbnail
import java.io.File

private class SearchScope(val label: String, val path: String)

private val pillLabels = listOf(
    FilterKind.ALL to "All",
    FilterKind.FOLDERS to "Folders",
    FilterKind.IMAGES to "Images",
    FilterKind.VIDEOS to "Videos",
    FilterKind.AUDIO to "Audio",
    FilterKind.DOCUMENTS to "Documents",
    FilterKind.ARCHIVES to "Archives",
    FilterKind.APKS to "APKs",
)

/** Full-screen search: type pills on top, a location dropdown, then results. */
@Composable
fun SearchScreen(
    currentFolder: String,
    volumes: List<StorageVolumeInfo>,
    quickAccess: List<String>,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onRecentFile: (String) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var scopeMenuOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val scopes = remember(currentFolder, volumes, quickAccess) {
        val list = ArrayList<SearchScope>()
        if (currentFolder.isNotBlank()) list.add(SearchScope("Current folder (${File(currentFolder).name.ifEmpty { currentFolder }})", currentFolder))
        for (v in volumes) list.add(SearchScope(v.label, v.path))
        for (p in quickAccess) list.add(SearchScope("Quick access: ${File(p).name.ifEmpty { p }}", p))
        list
    }
    LaunchedEffect(scopes) { vm.ensureScope(scopes.firstOrNull()?.path) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BackHandler { onBack() }

    val currentScope = scopes.firstOrNull { it.path == ui.scopePath }
    val resultsState = androidx.compose.foundation.lazy.rememberLazyListState()

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search file and folder names") },
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear text") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        vm.runSearch()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pillLabels, key = { it.first.name }) { (kind, label) ->
                    FilterChip(
                        selected = ui.kind == kind,
                        onClick = { vm.setKind(kind) },
                        label = { Text(label) },
                        shape = CircleShape,
                        leadingIcon = if (ui.kind == kind) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Search in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { scopeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(currentScope?.label ?: "Choose a location", maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose where to search")
                    }
                    DropdownMenu(expanded = scopeMenuOpen, onDismissRequest = { scopeMenuOpen = false }) {
                        scopes.forEach { scope ->
                            DropdownMenuItem(
                                text = { Text(scope.label, maxLines = 1) },
                                onClick = {
                                    scopeMenuOpen = false
                                    vm.setScope(scope.path)
                                },
                            )
                        }
                    }
                }
            }

            if (ui.searching) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Looked at ${ui.scanned} items\u2026", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = vm::stop) { Text("Stop") }
                }
            }
            message?.let { text ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }

            when {
                !ui.searched && !ui.searching -> EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Search your files",
                    message = "Type a name and press search, or pick a type above. Searching a whole storage can take a while, so it only starts when you ask.",
                )
                ui.searched && !ui.searching && ui.results.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Nothing found",
                    message = "No matching items in ${currentScope?.label ?: "this location"}. Try another name, type or location.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().topFade(resultsState.canScrollBackward),
                    state = resultsState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (ui.results.isNotEmpty()) {
                        item(key = "count") {
                            Text(
                                Formatters.plural(ui.results.size, "result") + if (ui.truncated) " (showing the first ${SearchViewModel.MAX_HITS}; narrow your search)" else "",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(ui.results, key = { it.path }) { hit ->
                        val kind = FileTypes.kindOf(hit.name, hit.isDirectory)
                        ListItem(
                            headlineContent = { Text(hit.name, maxLines = 1) },
                            supportingContent = {
                                Column {
                                    Text(hit.parentPath, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        (if (hit.isDirectory) "Folder" else Formatters.bytes(hit.sizeBytes)) + " \u00B7 " + Formatters.date(hit.lastModified),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            leadingContent = { KindIconOrThumbnail(kind, hit.path, hit.lastModified, 44.dp) },
                            trailingContent = {
                                if (!hit.isDirectory) {
                                    IconButton(onClick = { onOpenFolder(hit.parentPath) }) {
                                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Show in folder")
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                message = null
                                when {
                                    hit.isDirectory -> onOpenFolder(hit.path)
                                    kind == FileKind.ARCHIVE && FileActions.isOpenableArchive(hit.name) -> onOpenArchive(hit.path)
                                    else -> {
                                        val error = FileActions.open(context, File(hit.path), chooser = false)
                                        if (error != null) message = error else onRecentFile(hit.path)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
