package com.nexusarchive.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.core.StorageVolumeInfo
import com.nexusarchive.android.storage.SafBookmark
import com.nexusarchive.android.ui.storage.safKindIcon
import java.io.File

/** One entry of the location picker: a local folder ([path]) or a saved external storage ([safId]). */
class LocationOption(
    val section: String,
    val label: String,
    val icon: ImageVector,
    val path: String?,
    val safId: String?,
)

/** Storage volumes, Quick access folders, then added external storage (only when some exist). */
fun buildLocationOptions(
    volumes: List<StorageVolumeInfo>,
    quickAccess: List<String>,
    saf: List<SafBookmark>,
): List<LocationOption> {
    val list = ArrayList<LocationOption>()
    for (volume in volumes) {
        list.add(LocationOption("Storage", volume.label, if (volume.isRemovable) Icons.Rounded.SdCard else Icons.Rounded.Storage, volume.path, null))
    }
    for (path in quickAccess) {
        list.add(LocationOption("Quick access", File(path).name.ifEmpty { path }, Icons.Rounded.Folder, path, null))
    }
    for (b in saf) {
        list.add(LocationOption("Added external storage", b.label, safKindIcon(b.kind), null, b.id))
    }
    return list
}

/** A top-bar title that opens a dropdown of [locations]. Used by Browse and the archive browser. */
@Composable
fun LocationPickerTitle(
    title: String,
    subtitle: String,
    locations: List<LocationOption>,
    onPick: (LocationOption) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(onClickLabel = "Choose location") { open = true }
                .padding(vertical = 2.dp)
                .padding(start = 4.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose location")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            var lastSection = ""
            locations.forEach { option ->
                if (option.section != lastSection) {
                    if (lastSection.isNotEmpty()) HorizontalDivider()
                    Text(
                        option.section,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    lastSection = option.section
                }
                DropdownMenuItem(
                    text = { Text(option.label, maxLines = 1) },
                    leadingIcon = { Icon(option.icon, contentDescription = null) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}
