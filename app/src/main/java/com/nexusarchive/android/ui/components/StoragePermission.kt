package com.nexusarchive.android.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Snapshot of whether the app may read/write shared storage, plus how to ask for it. */
class StoragePermission(
    val hasAccess: Boolean,
    val request: () -> Unit,
    val openAppSettings: () -> Unit,
)

object StorageAccess {

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            legacyPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

    fun legacyPermissions(): Array<String> =
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun request(context: Context, legacyLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val perApp = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            try {
                context.startActivity(perApp)
            } catch (e: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: ActivityNotFoundException) {
                    openAppSettings(context)
                }
            }
        } else {
            legacyLauncher.launch(legacyPermissions())
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Nothing sensible left to open.
        }
    }
}

@Composable
fun rememberStoragePermission(): StoragePermission {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StorageAccess.hasAccess(context)) }

    // The user grants "All files access" in a system screen; re-check whenever we come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = StorageAccess.hasAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = StorageAccess.hasAccess(context)
    }

    return StoragePermission(
        hasAccess = hasAccess,
        request = { StorageAccess.request(context, legacyLauncher) },
        openAppSettings = { StorageAccess.openAppSettings(context) },
    )
}
