package com.nexusarchive.android.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Details of the copy of this package that is already installed, when there is one. */
data class InstalledInfo(
    val versionName: String?,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val uid: Int,
    val sourceDir: String?,
    val dataDir: String?,
    /** True/false when both signing keys could be read, null when one of them couldn't. */
    val sameSignature: Boolean?,
)

data class ApkInfo(
    val file: File,
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val icon: Bitmap?,
    val sizeBytes: Long,
    val signerSubject: String?,
    val sha256: String?,
    val sha1: String?,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val providers: List<String>,
    val receivers: List<String>,
    val installed: InstalledInfo?,
)

/** Reads an APK's manifest information through Android's own package parser; nothing is executed or installed. */
object ApkInspector {

    /** "AB:CD:EF" style fingerprint text. */
    fun hex(bytes: ByteArray): String = bytes.joinToString(":") { "%02X".format(it) }

    private fun digest(algorithm: String, bytes: ByteArray): String =
        hex(MessageDigest.getInstance(algorithm).digest(bytes))

    @Suppress("DEPRECATION")
    private fun signaturesOf(info: PackageInfo): List<Signature> =
        if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toList() ?: emptyList()
        } else {
            info.signatures?.toList() ?: emptyList()
        }

    @Suppress("DEPRECATION")
    fun load(context: Context, file: File): ApkInfo? {
        val pm = context.packageManager
        val signingFlag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS or signingFlag
        val info = pm.getPackageArchiveInfo(file.path, flags) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = file.path
        app.publicSourceDir = file.path

        val label = try {
            app.loadLabel(pm).toString()
        } catch (e: Exception) {
            info.packageName
        }
        val icon = try {
            app.loadIcon(pm).toBitmap(192, 192)
        } catch (e: Exception) {
            null
        }
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

        val signatures = signaturesOf(info)
        val first = signatures.firstOrNull()
        val sha256 = first?.let { digest("SHA-256", it.toByteArray()) }
        val sha1 = first?.let { digest("SHA-1", it.toByteArray()) }
        val subject = first?.let {
            try {
                val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(it.toByteArray())) as X509Certificate
                cert.subjectX500Principal.name
            } catch (e: Exception) {
                null
            }
        }

        val installed = try {
            val current = pm.getPackageInfo(info.packageName, signingFlag)
            val currentApp = current.applicationInfo
            val currentSigs = signaturesOf(current).map { digest("SHA-256", it.toByteArray()) }.toSet()
            val newSigs = signatures.map { digest("SHA-256", it.toByteArray()) }.toSet()
            InstalledInfo(
                versionName = current.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong(),
                firstInstallTime = current.firstInstallTime,
                lastUpdateTime = current.lastUpdateTime,
                uid = currentApp?.uid ?: -1,
                sourceDir = currentApp?.sourceDir,
                dataDir = currentApp?.dataDir,
                sameSignature = if (currentSigs.isEmpty() || newSigs.isEmpty()) null else currentSigs == newSigs,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        return ApkInfo(
            file = file,
            label = label,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            minSdk = app.minSdkVersion,
            targetSdk = app.targetSdkVersion,
            icon = icon,
            sizeBytes = file.length(),
            signerSubject = subject,
            sha256 = sha256,
            sha1 = sha1,
            permissions = info.requestedPermissions?.toList()?.sorted() ?: emptyList(),
            activities = info.activities?.map { it.name }?.sorted() ?: emptyList(),
            services = info.services?.map { it.name }?.sorted() ?: emptyList(),
            providers = info.providers?.map { it.name }?.sorted() ?: emptyList(),
            receivers = info.receivers?.map { it.name }?.sorted() ?: emptyList(),
            installed = installed,
        )
    }
}
