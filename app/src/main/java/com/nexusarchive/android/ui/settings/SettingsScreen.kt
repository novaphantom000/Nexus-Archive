@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.settings

import com.nexusarchive.android.ui.components.LocalFloatingBarInset
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexusarchive.android.BuildConfig
import com.nexusarchive.android.NexusApplication
import com.nexusarchive.android.ui.storage.StorageSettingsRow
import com.nexusarchive.android.core.SortField
import com.nexusarchive.android.data.AppSettings
import com.nexusarchive.android.data.ConflictPolicy
import com.nexusarchive.android.data.LayoutDensity
import com.nexusarchive.android.data.ThemeMode
import com.nexusarchive.android.data.ViewMode
import com.nexusarchive.android.ui.browse.FileActions
import com.nexusarchive.android.ui.components.SectionHeader
import com.nexusarchive.android.ui.components.StoragePermission
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    vm: SettingsViewModel,
    permission: StoragePermission,
    systemAnimationsOff: Boolean,
    onOpenStorage: () -> Unit,
) {
    val context = LocalContext.current
    val safBookmarks by remember { (context.applicationContext as NexusApplication).safStorage.bookmarks }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showLicenses by remember { mutableStateOf(false) }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = LocalFloatingBarInset.current)
                    .padding(bottom = 32.dp),
            ) {
                    SectionHeader("Appearance")
                    ChoiceRow(
                        title = "Theme",
                        options = listOf(
                            ThemeMode.SYSTEM to "Follow system",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.DARK to "Dark",
                        ),
                        selected = settings.themeMode,
                        onSelect = vm::setThemeMode,
                    )
                    SwitchRow(
                        title = "Dynamic color",
                        subtitle = if (dynamicSupported) "Use colors from your wallpaper" else "Requires Android 12 or newer",
                        checked = settings.dynamicColor && dynamicSupported,
                        enabled = dynamicSupported,
                        onChange = vm::setDynamicColor,
                    )
                    ChoiceRow(
                        title = "Layout density",
                        options = listOf(
                            LayoutDensity.COMPACT to "Compact",
                            LayoutDensity.COMFORTABLE to "Comfortable",
                            LayoutDensity.SPACIOUS to "Spacious",
                        ),
                        selected = settings.density,
                        onSelect = vm::setDensity,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("External storage")
                    StorageSettingsRow(count = safBookmarks.size, onClick = onOpenStorage)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("File browser")
                    ChoiceRow(
                        title = "Default view",
                        options = listOf(ViewMode.LIST to "List", ViewMode.GRID to "Grid"),
                        selected = settings.viewMode,
                        onSelect = vm::setViewMode,
                    )
                    ChoiceRow(
                        title = "Sort by",
                        options = listOf(
                            SortField.NAME to "Name",
                            SortField.SIZE to "Size",
                            SortField.DATE to "Date modified",
                            SortField.TYPE to "Type",
                        ),
                        selected = settings.sortField,
                        onSelect = vm::setSortField,
                    )
                    SwitchRow("Ascending order", "Turn off for descending", settings.sortAscending, true, vm::setSortAscending)
                    SwitchRow("Folders first", "List folders above files", settings.foldersFirst, true, vm::setFoldersFirst)
                    SwitchRow("Show hidden files", "Files and folders starting with a dot", settings.showHidden, true, vm::setShowHidden)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("File operations")
                    ChoiceRow(
                        title = "When a name already exists",
                        options = listOf(
                            ConflictPolicy.ASK to "Ask every time",
                            ConflictPolicy.KEEP_BOTH to "Keep both",
                            ConflictPolicy.REPLACE to "Replace",
                            ConflictPolicy.SKIP to "Skip",
                        ),
                        selected = settings.conflictPolicy,
                        onSelect = vm::setConflictPolicy,
                    )
                    SwitchRow(
                        "Confirm before deleting",
                        "Deleting is permanent: there is no recycle bin yet",
                        settings.confirmDelete,
                        true,
                        vm::setConfirmDelete,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("Accessibility")
                    SwitchRow(
                        title = "Reduce motion",
                        subtitle = if (systemAnimationsOff) {
                            "Android's animations are turned off, so motion is already reduced"
                        } else {
                            "Replace animations with instant changes. Also follows Android's animation setting"
                        },
                        checked = settings.reduceMotion || systemAnimationsOff,
                        enabled = !systemAnimationsOff,
                        onChange = vm::setReduceMotion,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("Privacy")
                    SwitchRow(
                        title = "Show recent folders and files",
                        subtitle = "Lists them on Home and remembers what you open. Turning this off hides them and stops recording new ones",
                        checked = settings.showRecents,
                        enabled = true,
                        onChange = vm::setShowRecents,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("Notifications")
                    SwitchRow(
                        title = "Operation notifications",
                        subtitle = "Show progress details and a notice when an operation finishes. Android still requires a minimal notification while work runs in the background",
                        checked = settings.notifications,
                        enabled = true,
                        onChange = vm::setNotifications,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("Storage access")
                    ListItem(
                        headlineContent = { Text("File access") },
                        supportingContent = {
                            Text(
                                if (permission.hasAccess) {
                                    "Granted. Android still blocks some system folders such as Android/data."
                                } else {
                                    "Not granted. Browsing and file operations are unavailable."
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        if (!permission.hasAccess) {
                            Button(onClick = permission.request) { Text("Allow file access") }
                        }
                        TextButton(onClick = permission.openAppSettings) { Text("Open app settings") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("Privacy")
                    ListItem(
                        headlineContent = { Text("Everything stays on your device") },
                        supportingContent = {
                            Text("This app declares no internet permission, has no ads, and sends no analytics. File names and paths never leave your device.")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text("Clear recent folders and files") },
                        supportingContent = { Text("Bookmarks are kept") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            vm.clearRecents()
                            scope.launch { snackbar.showSnackbar("Recent history cleared") }
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("Diagnostics")
                    val diagnostics = remember {
                        "Nexus Archive ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                            "Device ${Build.MANUFACTURER} ${Build.MODEL}"
                    }
                    ListItem(
                        headlineContent = { Text("Copy diagnostics") },
                        supportingContent = { Text(diagnostics) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            FileActions.copyText(context, "Diagnostics", diagnostics)
                            scope.launch { snackbar.showSnackbar("Diagnostics copied (no file names or paths included)") }
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SectionHeader("About")
                    ListItem(
                        headlineContent = { Text("Nexus Archive") },
                        supportingContent = { Text("Version ${BuildConfig.VERSION_NAME} \u00B7 ${BuildConfig.APPLICATION_ID}") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text("Open-source licenses") },
                        supportingContent = { Text("Libraries used by this build") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showLicenses = true },
                    )
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licenses") },
            text = {
                Text(
                    "This build uses these libraries, all licensed under the Apache License 2.0:\n\n" +
                        "\u2022 AndroidX (Core, Activity, Lifecycle, Navigation, DataStore)\n" +
                        "\u2022 Jetpack Compose and Material 3\n" +
                        "\u2022 Material Icons (Compose)\n" +
                        "\u2022 Kotlin and kotlinx.coroutines\n\n" +
                        "Archive-format libraries will be added, and listed here, when archive support is built.",
                )
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selected }?.second ?: ""
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(currentLabel) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(value)
                                    open = false
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == selected,
                                onClick = {
                                    onSelect(value)
                                    open = false
                                },
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

/** Android's global animation scale of 0 means the user turned animations off system-wide. */
fun systemAnimationsDisabled(context: android.content.Context): Boolean =
    try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        false
    }
