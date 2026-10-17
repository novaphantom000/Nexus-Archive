@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.browse

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.apk.ApkInfo
import com.nexusarchive.android.apk.ApkInspector
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ApkLoad {
    data object Loading : ApkLoad
    data object Failed : ApkLoad
    class Ready(val info: ApkInfo) : ApkLoad
}

/**
 * The window shown when an APK is tapped: icon, name, Browse and Install/Update, and an expandable Information
 * section with everything the package declares. Installing still goes through Android's own installer.
 */
@Composable
fun ApkPreviewSheet(
    file: File,
    onBrowse: () -> Unit,
    onInstall: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state by produceState<ApkLoad>(ApkLoad.Loading, file) {
        value = withContext(Dispatchers.IO) {
            try {
                ApkInspector.load(context, file)?.let { ApkLoad.Ready(it) } ?: ApkLoad.Failed
            } catch (e: Exception) {
                ApkLoad.Failed
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (val current = state) {
            ApkLoad.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ApkLoad.Failed -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This APK can't be read", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Android couldn't read its manifest. The file may be damaged, or it may be a split or bundle package (.apks, .xapk).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onBrowse, shape = CircleShape) { Text("Browse its files") }
                Spacer(Modifier.navigationBarsPadding())
            }
            is ApkLoad.Ready -> ApkContent(current.info, onBrowse, onInstall, onMessage)
        }
    }
}

@Composable
private fun ApkContent(info: ApkInfo, onBrowse: () -> Unit, onInstall: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val reduce = LocalReduceMotion.current
    var infoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val installed = info.installed
    val installLabel = when {
        installed == null -> "Install"
        info.versionCode > installed.versionCode -> "Update"
        info.versionCode == installed.versionCode -> "Reinstall"
        else -> "Install"
    }
    val arrow by animateFloatAsState(if (infoOpen) 180f else 0f, Motion.spatial(reduce), label = "infoArrow")

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bitmap = info.icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)),
                )
            } else {
                Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(72.dp))
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(info.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                Text(
                    "Version ${info.versionName ?: "unknown"} (${info.versionCode})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    info.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("On Play Store") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            openPlayStore(context, info.packageName, onMessage)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Open system app info") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        enabled = installed != null,
                        onClick = {
                            menuOpen = false
                            openSystemInfo(context, info.packageName, onMessage)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy signature") },
                        enabled = info.sha256 != null,
                        onClick = {
                            menuOpen = false
                            info.sha256?.let {
                                FileActions.copyText(context, "Signature", it)
                                onMessage("Signature copied")
                            }
                        },
                    )
                }
            }
        }

        if (installed != null && installed.sameSignature == false) {
            Banner("A copy of this app is installed but was signed with a different key. Android will refuse to update it; uninstall the installed copy first.")
        } else if (installed != null && info.versionCode < installed.versionCode) {
            Banner("A newer version is already installed. Android doesn't allow installing an older version over it.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBrowse, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("Browse") }
            Button(onClick = onInstall, shape = CircleShape, modifier = Modifier.weight(1f)) { Text(installLabel) }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().animateContentSize(Motion.spatial(reduce)),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { infoOpen = !infoOpen }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Information", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = if (infoOpen) "Collapse information" else "Expand information",
                        modifier = Modifier.rotate(arrow),
                    )
                }
                AnimatedVisibility(
                    visible = infoOpen,
                    enter = expandVertically(Motion.spatial(reduce)) + fadeIn(Motion.effects(reduce)),
                    exit = shrinkVertically(Motion.spatial(reduce)) + fadeOut(Motion.effects(reduce)),
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoRow("Package name", info.packageName)
                        InfoRow("Version name", info.versionName ?: "Unavailable")
                        InfoRow("Version code", info.versionCode.toString())
                        InfoRow("Minimum SDK", info.minSdk.toString())
                        InfoRow("Target SDK", info.targetSdk.toString())
                        InfoRow("File size", Formatters.bytes(info.sizeBytes))
                        InfoRow("APK location", info.file.path)
                        InfoRow("Signer", info.signerSubject ?: "Unavailable")
                        InfoRow("Signature (SHA-256)", info.sha256 ?: "Unavailable", mono = true)
                        InfoRow("Signature (SHA-1)", info.sha1 ?: "Unavailable", mono = true)
                        InfoRow("Installed", if (installed == null) "Not installed" else "Yes, version ${installed.versionName ?: "?"} (${installed.versionCode})")
                        InfoRow("Installed on", if (installed == null) "Not installed" else Formatters.dateTime(installed.firstInstallTime))
                        InfoRow("Updated on", if (installed == null) "Not installed" else Formatters.dateTime(installed.lastUpdateTime))
                        InfoRow("UID", if (installed == null || installed.uid < 0) "Not installed" else installed.uid.toString())
                        InfoRow("Install directory", installed?.sourceDir ?: "Not installed")
                        InfoRow("Data directory", installed?.dataDir ?: "Not installed")
                        ExpandableList("Permissions", info.permissions)
                        ExpandableList("Activities", info.activities)
                        ExpandableList("Services", info.services)
                        ExpandableList("Providers", info.providers)
                        ExpandableList("Receivers", info.receivers)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Banner(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
private fun ExpandableList(title: String, items: List<String>) {
    val reduce = LocalReduceMotion.current
    var open by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (open) 180f else 0f, Motion.spatial(reduce), label = "sectionArrow")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().animateContentSize(Motion.spatial(reduce)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = items.isNotEmpty()) { open = !open }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$title (${items.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(arrow))
                }
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(Motion.spatial(reduce)) + fadeIn(Motion.effects(reduce)),
                exit = shrinkVertically(Motion.spatial(reduce)) + fadeOut(Motion.effects(reduce)),
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
            }
        }
    }
}

private fun openPlayStore(context: Context, packageName: String, onMessage: (String) -> Unit) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        } catch (e2: ActivityNotFoundException) {
            onMessage("No app found to open the Play Store page.")
        }
    }
}

private fun openSystemInfo(context: Context, packageName: String, onMessage: (String) -> Unit) {
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    } catch (e: ActivityNotFoundException) {
        onMessage("Couldn't open the system app info page.")
    }
}
