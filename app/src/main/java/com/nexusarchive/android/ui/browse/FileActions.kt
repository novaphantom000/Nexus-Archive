package com.nexusarchive.android.ui.browse

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.core.FileTypes
import java.io.File

/** Hands files to other apps through FileProvider. Each function returns an error message, or null on success. */
object FileActions {

    fun mimeTypeOf(file: File): String {
        val ext = FileTypes.extensionOf(file.name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun open(context: Context, file: File, chooser: Boolean): String? {
        if (!file.exists()) return "This file no longer exists."
        if (FileTypes.extensionOf(file.name) == "apk") {
            return installApk(context, file)
        }
        val uri = uriFor(context, file) ?: return "This file can't be shared with other apps."
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeTypeOf(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val launch = if (chooser) Intent.createChooser(view, "Open with") else view
        return try {
            context.startActivity(launch)
            null
        } catch (e: ActivityNotFoundException) {
            "No app found that can open this file."
        }
    }

    /**
     * Hands an APK to Android's own package installer. The user always sees the system install prompt;
     * nothing is installed silently. The "install unknown apps" permission is requested through system settings.
     */
    private fun installApk(context: Context, file: File): String? {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            return try {
                context.startActivity(settings)
                "Allow Nexus Archive to install apps on the screen that just opened, then tap the APK again."
            } catch (e: ActivityNotFoundException) {
                "Allow this app to install apps in system settings, then tap the APK again."
            }
        }
        val uri = uriFor(context, file) ?: return "This file can't be handed to the installer."
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "No package installer was found on this device."
        }
    }

    /** True when the archive engine can read this archive type (not RAR, ISO and the other unsupported ones). */
    fun isOpenableArchive(name: String): Boolean =
        com.nexusarchive.android.archive.ArchiveFormats.unsupportedReason(name) == null

    fun share(context: Context, files: List<File>): String? {
        val shareable = files.filter { it.isFile }
        if (shareable.isEmpty()) return "Folders can't be shared. Select files instead."
        val uris = ArrayList<Uri>()
        for (f in shareable) {
            uris.add(uriFor(context, f) ?: return "\"${f.name}\" can't be shared with other apps.")
        }
        val mimes = shareable.map { mimeTypeOf(it) }.distinct()
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = if (mimes.size == 1) mimes[0] else "*/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri(null, uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        intent.clipData = clip
        return try {
            context.startActivity(Intent.createChooser(intent, "Share"))
            null
        } catch (e: ActivityNotFoundException) {
            "No app available to share with."
        }
    }

    fun copyText(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun uriFor(context: Context, file: File): Uri? =
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: IllegalArgumentException) {
            null
        }
}
