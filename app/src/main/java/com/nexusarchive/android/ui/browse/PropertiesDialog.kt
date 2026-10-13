package com.nexusarchive.android.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.core.DirStats
import com.nexusarchive.android.core.FileDetails
import com.nexusarchive.android.core.FileEntry
import com.nexusarchive.android.core.FileStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows only what the filesystem actually reports. Anything unavailable is labelled "Unavailable".
 * Folder size and file hashes are computed only when the user asks, and can be cancelled by closing the dialog.
 */
@Composable
fun PropertiesDialog(entry: FileEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = remember(entry.path) { File(entry.path) }
    val scope = rememberCoroutineScope()

    val details by produceState<FileDetails?>(initialValue = null, entry.path) {
        value = withContext(Dispatchers.IO) { FileStats.details(file) }
    }

    var dirStats by remember { mutableStateOf<DirStats?>(null) }
    var calculating by remember { mutableStateOf(false) }
    var hashes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var hashing by remember { mutableStateOf<String?>(null) }
    var hashedBytes by remember { mutableLongStateOf(0L) }
    var hashError by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun startHash(algorithm: String) {
        job?.cancel()
        hashError = null
        hashedBytes = 0L
        job = scope.launch {
            hashing = algorithm
            try {
                val result = withContext(Dispatchers.IO) {
                    FileStats.hash(file, algorithm) { done -> hashedBytes = done }
                }
                hashes = hashes + (algorithm to result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hashError = e.message ?: "Couldn't read this file."
            } finally {
                hashing = null
            }
        }
    }

    fun startDirStats() {
        job?.cancel()
        job = scope.launch {
            calculating = true
            try {
                dirStats = withContext(Dispatchers.IO) { FileStats.directoryStats(file) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dirStats = null
            } finally {
                calculating = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PropertyRow("Type", if (entry.isDirectory) "Folder" else FileActions.mimeTypeOf(file).takeIf { it != "*/*" } ?: "Unknown type")
                if (!entry.isDirectory) {
                    PropertyRow("Extension", entry.extension.ifEmpty { "None" })
                    PropertyRow("Size", com.nexusarchive.android.core.Formatters.bytes(entry.sizeBytes))
                }
                PropertyRow("Location", details?.parentPath ?: file.parent ?: "Unavailable")
                PropertyRow("Modified", com.nexusarchive.android.core.Formatters.dateTime(entry.lastModified))
                PropertyRow(
                    "Last accessed",
                    details?.lastAccessedMs?.let { com.nexusarchive.android.core.Formatters.dateTime(it) } ?: "Unavailable",
                )
                details?.let {
                    PropertyRow("Readable", if (it.canRead) "Yes" else "No")
                    PropertyRow("Writable", if (it.canWrite) "Yes" else "No")
                    if (it.isSymlink) PropertyRow("Symbolic link", "Yes (never followed by copy/move)")
                }
                PropertyRow("Hidden", if (entry.isHidden) "Yes" else "No")
                PropertyRow("Path", entry.path, selectable = true)

                Spacer(Modifier.height(12.dp))
                if (entry.isDirectory) {
                    val stats = dirStats
                    if (stats != null) {
                        PropertyRow("Total size", com.nexusarchive.android.core.Formatters.bytes(stats.totalBytes))
                        PropertyRow("Contains", "${stats.fileCount} files, ${stats.folderCount} folders")
                    } else if (calculating) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Calculating\u2026", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        OutlinedButton(onClick = { startDirStats() }) { Text("Calculate folder size") }
                    }
                } else if (!entry.isSymlink) {
                    Text("Checksums", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { startHash("MD5") }, enabled = hashing == null) { Text("MD5") }
                        OutlinedButton(onClick = { startHash("SHA-256") }, enabled = hashing == null) { Text("SHA-256") }
                    }
                    val active = hashing
                    if (active != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Computing $active\u2026", style = MaterialTheme.typography.bodySmall)
                        if (entry.sizeBytes > 0) {
                            LinearProgressIndicator(
                                progress = { (hashedBytes.toFloat() / entry.sizeBytes.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    hashError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    hashes.forEach { (algorithm, value) ->
                        Spacer(Modifier.height(8.dp))
                        Text(algorithm, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer {
                            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        TextButton(onClick = { FileActions.copyText(context, algorithm, value) }) { Text("Copy $algorithm") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PropertyRow(label: String, value: String, selectable: Boolean = false) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selectable) {
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
        } else {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
