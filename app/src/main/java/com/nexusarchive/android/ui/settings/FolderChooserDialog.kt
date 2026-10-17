package com.nexusarchive.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nexusarchive.android.ui.components.LocationOption

/** Picks one local folder from the known locations. An empty result means "internal storage (default)". */
@Composable
fun FolderChooserDialog(
    title: String,
    options: List<LocationOption>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                item(key = "default") {
                    ListItem(
                        headlineContent = { Text("Internal storage (default)") },
                        leadingContent = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick("") },
                    )
                }
                items(options.filter { it.path != null }, key = { "${it.section}:${it.path}" }) { option ->
                    ListItem(
                        headlineContent = { Text(option.label, maxLines = 1) },
                        supportingContent = { Text(option.section) },
                        leadingContent = { Icon(option.icon, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(option.path ?: "") },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
