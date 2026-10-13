@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.core.FileEntry
import com.nexusarchive.android.core.FilterKind
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.core.SortField
import com.nexusarchive.android.data.AppSettings
import com.nexusarchive.android.data.LayoutDensity
import com.nexusarchive.android.ui.components.SelectableLeading
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.Motion

private fun subtitleFor(entry: FileEntry): String {
    val date = Formatters.date(entry.lastModified)
    val first = when {
        entry.isSymlink -> "Link"
        entry.isDirectory -> "Folder"
        else -> Formatters.bytes(entry.sizeBytes)
    }
    return "$first \u00B7 $date"
}

@Composable
fun FileListItem(
    entry: FileEntry,
    isSelected: Boolean,
    density: LayoutDensity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (isSelected) colors.secondaryContainer else Color.Transparent,
        animationSpec = Motion.effects(reduce),
        label = "rowBackground",
    )
    val verticalPadding = when (density) {
        LayoutDensity.COMPACT -> 4.dp
        LayoutDensity.COMFORTABLE -> 8.dp
        LayoutDensity.SPACIOUS -> 14.dp
    }
    val iconSize = when (density) {
        LayoutDensity.COMPACT -> 36.dp
        LayoutDensity.COMFORTABLE -> 44.dp
        LayoutDensity.SPACIOUS -> 52.dp
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            .padding(horizontal = 12.dp, vertical = verticalPadding)
            .semantics(mergeDescendants = true) { this.selected = isSelected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SelectableLeading(
            kind = entry.kind,
            selected = isSelected,
            size = iconSize,
            path = entry.path,
            lastModified = entry.lastModified,
            onToggle = onLongClick,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitleFor(entry),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FileGridItem(
    entry: FileEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (isSelected) colors.secondaryContainer else colors.surfaceContainerLow,
        animationSpec = Motion.effects(reduce),
        label = "tileBackground",
    )
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { this.selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SelectableLeading(
            kind = entry.kind,
            selected = isSelected,
            size = 56.dp,
            path = entry.path,
            lastModified = entry.lastModified,
            onToggle = onLongClick,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (entry.isDirectory) "Folder" else Formatters.bytes(entry.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Clickable path segments. Always scrolls so the current folder is visible. */
@Composable
fun BreadcrumbBar(
    crumbs: List<Breadcrumb>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val listState = rememberLazyListState()
    LaunchedEffect(crumbs) {
        if (crumbs.isNotEmpty()) {
            if (reduce) listState.scrollToItem(crumbs.lastIndex) else listState.animateScrollToItem(crumbs.lastIndex)
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(crumbs, key = { index, crumb -> "$index:${crumb.path}" }) { index, crumb ->
            val isLast = index == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    onClick = { onNavigate(crumb.path) },
                    enabled = !isLast,
                    shape = CircleShape,
                    color = if (isLast) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        text = crumb.label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val sortLabels = listOf(
    SortField.NAME to "Name",
    SortField.SIZE to "Size",
    SortField.DATE to "Date",
    SortField.TYPE to "Type",
)

private val filterLabels = listOf(
    FilterKind.ALL to "All",
    FilterKind.FOLDERS to "Folders",
    FilterKind.IMAGES to "Images",
    FilterKind.VIDEOS to "Videos",
    FilterKind.AUDIO to "Audio",
    FilterKind.DOCUMENTS to "Documents",
    FilterKind.ARCHIVES to "Archives",
    FilterKind.APKS to "APKs",
)

@Composable
fun SortFilterSheet(
    settings: AppSettings,
    filter: FilterKind,
    onSortField: (SortField) -> Unit,
    onAscending: (Boolean) -> Unit,
    onFoldersFirst: (Boolean) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onFilter: (FilterKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Sort by", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sortLabels.forEach { (field, label) ->
                    FilterChip(
                        selected = settings.sortField == field,
                        onClick = { onSortField(field) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.sortAscending, onClick = { onAscending(true) }, label = { Text("Ascending") })
                FilterChip(selected = !settings.sortAscending, onClick = { onAscending(false) }, label = { Text("Descending") })
            }
            Spacer(Modifier.height(8.dp))
            SwitchLine("Folders first", settings.foldersFirst, onFoldersFirst)
            SwitchLine("Show hidden files", settings.showHidden, onShowHidden)
            Spacer(Modifier.height(12.dp))
            Text("Show", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filterLabels.forEach { (kind, label) ->
                    FilterChip(
                        selected = filter == kind,
                        onClick = { onFilter(kind) },
                        label = { Text(label) },
                        leadingIcon = if (filter == kind) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Radio-style choice used by the paste-conflict dialog. */
@Composable
fun ChoiceLine(label: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
