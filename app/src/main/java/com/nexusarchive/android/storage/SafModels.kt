package com.nexusarchive.android.storage

import android.net.Uri
import android.provider.DocumentsContract

/**
 * What the user told us the folder is. Android's folder picker is the same for all of these;
 * the kind only decides the label and icon, it never changes how access works.
 */
enum class SafKind(val label: String) {
    FOLDER("Folder"),
    SD_CARD("SD card"),
    USB("USB drive"),
    PROVIDER("Provider folder"),
}

enum class AccessState(val label: String) {
    READ_WRITE("Read and write"),
    READ_ONLY("Read only"),
    REVOKED("Access revoked"),
    UNAVAILABLE("Not connected"),
}

/** One saved Storage Access Framework folder. [treeUri] is a document-tree URI, never a file path. */
data class SafBookmark(
    val id: String,
    val label: String,
    val treeUri: String,
    val kind: SafKind,
    val createdAt: Long,
    val lastAccessedAt: Long,
)

/** One child of a document folder, with the capability flags the provider actually reported. */
data class SafItem(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val flags: Int,
) {
    val canDelete: Boolean get() = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
    val canRename: Boolean get() = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
}

sealed interface SafListResult {
    data class Success(val items: List<SafItem>, val canCreateHere: Boolean) : SafListResult
    data class Failure(val state: AccessState, val message: String) : SafListResult
}

/** Pure helpers, kept free of Android types so they can be unit-tested. */
object SafLabels {
    /** "primary:Download/Games" with kind SD_CARD -> "SD card: Games". Falls back to the kind label alone. */
    fun defaultLabel(treeDocumentId: String, kind: SafKind): String {
        val path = treeDocumentId.substringAfter(':', "")
        val leaf = path.trim('/').substringAfterLast('/')
        return if (leaf.isEmpty()) kind.label else "${kind.label}: $leaf"
    }
}
