@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.archive.ArchiveFormats
import com.nexusarchive.android.core.FileEntry

/** The menu that slides up when an archive is tapped in the file browser. */
@Composable
fun ArchiveActionSheet(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onExtractHere: () -> Unit,
    onExtractToFolder: () -> Unit,
    onExtractChoose: () -> Unit,
    onTest: () -> Unit,
    onCompress: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            entry.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SheetRow(Icons.Rounded.Visibility, "View", "Browse the contents without extracting", onView)
        SheetRow(Icons.Rounded.Unarchive, "Extract here", "Into this folder", onExtractHere)
        SheetRow(
            Icons.Rounded.CreateNewFolder,
            "Extract to ./${ArchiveFormats.baseName(entry.name)}/",
            "Into a new folder named after the archive",
            onExtractToFolder,
        )
        SheetRow(Icons.Rounded.Unarchive, "Extract\u2026", "Choose the destination and how to handle name clashes", onExtractChoose)
        SheetRow(Icons.Rounded.CheckCircle, "Test", "Read everything to look for damage", onTest)
        SheetRow(Icons.Rounded.Archive, "Compress", "Make a new archive from this file", onCompress)
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
