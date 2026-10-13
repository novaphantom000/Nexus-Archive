package com.nexusarchive.android.ui.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.core.ConflictResolution

/**
 * Password prompt. With [confirmField] it asks twice (used when creating an encrypted archive).
 * The text lives in Compose state only while the dialog is open and is passed on as a CharArray.
 */
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    error: String?,
    busy: Boolean,
    confirmField: Boolean,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val mismatch = confirmField && confirm.isNotEmpty() && confirm != password
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null,
                    supportingText = { if (error != null) Text(error) },
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
                if (confirmField) {
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
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password.toCharArray()) },
                enabled = password.isNotEmpty() && !busy && (!confirmField || confirm == password),
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Extraction options. [selectedCount] is 0 when the whole archive is being extracted. */
@Composable
fun ExtractDialog(
    selectedCount: Int,
    archiveBaseName: String,
    onStart: (ArchiveViewModel.ExtractRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var intoFolder by remember { mutableStateOf(true) }
    var resolution by remember { mutableStateOf(ConflictResolution.KEEP_BOTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selectedCount > 0) "Extract $selectedCount selected" else "Extract all") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Destination", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                RadioRow("A new folder named \"$archiveBaseName\", next to the archive", intoFolder) { intoFolder = true }
                RadioRow("The same folder as the archive", !intoFolder) { intoFolder = false }
                Spacer(Modifier.height(12.dp))
                Text("If a file already exists", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                RadioRow("Keep both", resolution == ConflictResolution.KEEP_BOTH) { resolution = ConflictResolution.KEEP_BOTH }
                RadioRow("Replace it", resolution == ConflictResolution.REPLACE) { resolution = ConflictResolution.REPLACE }
                RadioRow("Skip it", resolution == ConflictResolution.SKIP) { resolution = ConflictResolution.SKIP }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(ArchiveViewModel.ExtractRequest(intoFolder, resolution, selectedCount > 0))
                },
            ) { Text("Extract") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
