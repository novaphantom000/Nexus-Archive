@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.archive.ArchiveFormat
import com.nexusarchive.android.archive.ArchiveFormats
import com.nexusarchive.android.archive.CompressionLevel
import com.nexusarchive.android.core.FileEntry
import com.nexusarchive.android.core.FileNames
import java.io.File

private fun levelLabel(level: CompressionLevel): String = when (level) {
    CompressionLevel.STORE -> "Store (no compression)"
    CompressionLevel.FAST -> "Fast"
    CompressionLevel.NORMAL -> "Normal"
    CompressionLevel.MAXIMUM -> "Maximum"
}

/**
 * Archive creation options. Only formats, levels and password support that the engine genuinely
 * implements are offered, taken from [ArchiveFormats.capabilities].
 */
@Composable
fun CompressDialog(
    sources: List<FileEntry>,
    outputDir: File,
    onStart: (output: File, format: ArchiveFormat, level: CompressionLevel, password: CharArray?) -> Unit,
    onDismiss: () -> Unit,
) {
    val singleFile = sources.size == 1 && !sources[0].isDirectory
    val formats = ArchiveFormats.creatable.filter { !ArchiveFormats.isSingleStream(it) || singleFile }
    val defaultBase = if (sources.size == 1) ArchiveFormats.baseName(sources[0].name).ifEmpty { sources[0].name } else "Archive"

    var format by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var level by remember { mutableStateOf(CompressionLevel.NORMAL) }
    var baseName by remember { mutableStateOf(defaultBase) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    val caps = ArchiveFormats.capabilities(format)
    val effectiveLevel = when {
        level in caps.levels -> level
        CompressionLevel.NORMAL in caps.levels -> CompressionLevel.NORMAL
        else -> caps.levels.firstOrNull() ?: CompressionLevel.NORMAL
    }
    val passwordActive = usePassword && caps.passwordCreate
    val fullName = baseName.trim() + ArchiveFormats.extensionFor(format)
    val nameError = if (baseName.isBlank()) null else FileNames.validate(fullName)
    val mismatch = passwordActive && confirm.isNotEmpty() && confirm != password
    val canStart = baseName.isNotBlank() && nameError == null &&
        (!passwordActive || (password.isNotEmpty() && confirm == password))
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress ${if (sources.size == 1) "\"${sources[0].name}\"" else "${sources.size} items"}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Format", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    formats.forEach { f ->
                        FilterChip(selected = f == format, onClick = { format = f }, label = { Text(f.label) })
                    }
                }
                Text(caps.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    label = { Text("Archive name") },
                    suffix = { Text(ArchiveFormats.extensionFor(format)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = {
                        if (nameError != null) {
                            Text(nameError)
                        } else if (File(outputDir, fullName).exists()) {
                            Text("This name exists already; a number will be added.")
                        } else {
                            Text("Saved in ${outputDir.name.ifEmpty { outputDir.path }}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (caps.levels.isNotEmpty()) {
                    Text("Compression level", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        caps.levels.forEach { l ->
                            FilterChip(selected = l == effectiveLevel, onClick = { level = l }, label = { Text(levelLabel(l)) })
                        }
                    }
                }

                if (caps.passwordCreate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Password protect (AES-256)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = usePassword, onCheckedChange = { usePassword = it })
                    }
                    if (usePassword) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = transformation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { visible = !visible }) {
                                    Icon(
                                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (visible) "Hide password" else "Show password",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = { Text("Confirm password") },
                            singleLine = true,
                            visualTransformation = transformation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = mismatch,
                            supportingText = { if (mismatch) Text("The passwords don't match.") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "File names inside a ZIP stay visible even when the contents are encrypted. There is no way to recover a lost password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canStart,
                onClick = {
                    val output = FileNames.uniqueFile(outputDir, fullName, false)
                    onStart(output, format, effectiveLevel, if (passwordActive) password.toCharArray() else null)
                },
            ) { Text("Compress") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
