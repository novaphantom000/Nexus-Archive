package com.nexusarchive.android.ui.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.storage.SafBookmark
import com.nexusarchive.android.ui.components.SectionHeader

/** Home dashboard section: saved external storage plus the always-visible Add storage button. */
@Composable
fun ExternalStorageSection(
    bookmarks: List<SafBookmark>,
    onOpen: (String) -> Unit,
    onManage: () -> Unit,
) {
    Column {
        SectionHeader("External storage")
        if (bookmarks.isEmpty()) {
            Text(
                "SD cards, USB drives and folders from other apps appear here once you add them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        bookmarks.forEach { bookmark ->
            ListItem(
                headlineContent = { Text(bookmark.label, maxLines = 1) },
                supportingContent = { Text(bookmark.kind.label) },
                leadingContent = { Icon(safKindIcon(bookmark.kind), contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(bookmark.id) },
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onManage) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("  Add storage")
            }
            if (bookmarks.isNotEmpty()) {
                OutlinedButton(onClick = onManage) { Text("Manage") }
            }
        }
    }
}

/** Row used on the Settings screen. */
@Composable
fun StorageSettingsRow(count: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text("External storage") },
        supportingContent = {
            Text(
                if (count == 0) "Add SD cards, USB drives and provider folders" else "$count saved. Add, test, reauthorize or remove",
            )
        },
        leadingContent = { Icon(Icons.Rounded.Storage, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
