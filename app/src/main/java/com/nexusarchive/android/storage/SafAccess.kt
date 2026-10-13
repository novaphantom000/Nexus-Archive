package com.nexusarchive.android.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import com.nexusarchive.android.core.ItemFailure
import com.nexusarchive.android.core.OpOutcome
import com.nexusarchive.android.core.ProgressListener
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Everything that talks to Android's document providers. Document URIs are only ever handled as URIs;
 * they are never converted into guessed file paths. Providers may not support every operation, so each
 * call reports failure instead of assuming success.
 */
object SafAccess {

    private val PROJECTION = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
    )

    fun rootDocumentId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    fun documentUri(tree: Uri, documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)

    /** Saves the grant so it survives app restarts. Returns an error message, or null on success. */
    fun persist(context: Context, tree: Uri): String? {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            context.contentResolver.takePersistableUriPermission(tree, flags)
            null
        } catch (e: SecurityException) {
            try {
                // Some providers only offer read access.
                context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                null
            } catch (e2: SecurityException) {
                "Android didn't allow this app to keep access to that folder."
            }
        }
    }

    fun release(context: Context, tree: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Already released; nothing to do.
        }
    }

    /** Checks the saved grant and then asks the provider for the root document. */
    fun accessState(context: Context, bookmark: SafBookmark): AccessState {
        val tree = Uri.parse(bookmark.treeUri)
        val grant = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == tree }
            ?: return AccessState.REVOKED
        return try {
            val uri = documentUri(tree, rootDocumentId(tree))
            val reachable = context.contentResolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() }
            when {
                reachable != true -> AccessState.UNAVAILABLE
                grant.isWritePermission -> AccessState.READ_WRITE
                else -> AccessState.READ_ONLY
            }
        } catch (e: SecurityException) {
            AccessState.REVOKED
        } catch (e: Exception) {
            AccessState.UNAVAILABLE
        }
    }

    fun list(context: Context, tree: Uri, documentId: String): SafListResult {
        val resolver = context.contentResolver
        return try {
            val canCreate = queryFlags(resolver, tree, documentId) and Document.FLAG_DIR_SUPPORTS_CREATE != 0
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            val items = ArrayList<SafItem>()
            val cursor = resolver.query(children, PROJECTION, null, null, null)
                ?: return SafListResult.Failure(AccessState.UNAVAILABLE, "The storage provider didn't answer. The drive may be disconnected.")
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getString(0) ?: continue
                    val mime = it.getString(2) ?: ""
                    items.add(
                        SafItem(
                            documentId = id,
                            uri = documentUri(tree, id),
                            name = it.getString(1) ?: id,
                            mimeType = mime,
                            isDirectory = mime == Document.MIME_TYPE_DIR,
                            sizeBytes = if (it.isNull(3)) -1L else it.getLong(3),
                            modifiedMs = if (it.isNull(4)) 0L else it.getLong(4),
                            flags = if (it.isNull(5)) 0 else it.getInt(5),
                        ),
                    )
                }
            }
            SafListResult.Success(items, canCreate)
        } catch (e: SecurityException) {
            SafListResult.Failure(AccessState.REVOKED, "Access to this folder was revoked. Reauthorize it to continue.")
        } catch (e: IllegalArgumentException) {
            SafListResult.Failure(AccessState.UNAVAILABLE, "This folder no longer exists or the storage is not connected.")
        } catch (e: Exception) {
            SafListResult.Failure(AccessState.UNAVAILABLE, e.message ?: "This folder could not be read.")
        }
    }

    private fun queryFlags(resolver: ContentResolver, tree: Uri, documentId: String): Int {
        val uri = documentUri(tree, documentId)
        return resolver.query(uri, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else 0
        } ?: 0
    }

    /** Returns the new document id, or null when the provider refused. */
    fun createFolder(context: Context, tree: Uri, parentId: String, name: String): String? =
        try {
            val parent = documentUri(tree, parentId)
            val created = DocumentsContract.createDocument(context.contentResolver, parent, Document.MIME_TYPE_DIR, name)
            created?.let { DocumentsContract.getDocumentId(it) }
        } catch (e: Exception) {
            null
        }

    fun delete(context: Context, uri: Uri): Boolean =
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            false
        }

    fun rename(context: Context, uri: Uri, newName: String): Boolean =
        try {
            DocumentsContract.renameDocument(context.contentResolver, uri, newName) != null
        } catch (e: Exception) {
            false
        }

    private fun displayNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.let { n -> name = n }
                    if (!it.isNull(1)) size = it.getLong(1)
                }
            }
        } catch (e: Exception) {
            // Keep the fallbacks.
        }
        return name to size
    }

    /** Copies the chosen files into a document folder. Existing names are not overwritten; the provider picks a free name. */
    suspend fun importFiles(
        context: Context,
        tree: Uri,
        parentId: String,
        sources: List<Uri>,
        listener: ProgressListener,
    ): OpOutcome {
        val resolver = context.contentResolver
        val infos = sources.map { displayNameAndSize(context, it) }
        val known = infos.all { it.second >= 0 }
        listener.onTotals(if (known) infos.sumOf { it.second } else -1L, sources.size.toLong())
        val failures = ArrayList<ItemFailure>()
        var completed = 0
        val buffer = ByteArray(64 * 1024)
        val parent = documentUri(tree, parentId)
        for ((index, source) in sources.withIndex()) {
            currentCoroutineContext().ensureActive()
            val (name, _) = infos[index]
            listener.onCurrent(name)
            var target: Uri? = null
            try {
                val mime = resolver.getType(source) ?: "application/octet-stream"
                val created: Uri = DocumentsContract.createDocument(resolver, parent, mime, name)
                    ?: throw IOException("The storage provider refused to create this file.")
                target = created
                val input = resolver.openInputStream(source) ?: throw FileNotFoundException("Couldn't open the source file.")
                input.use { src ->
                    val output = resolver.openOutputStream(created, "w") ?: throw IOException("Couldn't open the destination for writing.")
                    output.use { dst ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = src.read(buffer)
                            if (n < 0) break
                            dst.write(buffer, 0, n)
                            listener.onBytes(n.toLong())
                        }
                    }
                }
                completed++
            } catch (e: kotlinx.coroutines.CancellationException) {
                target?.let { delete(context, it) }
                throw e
            } catch (e: Exception) {
                target?.let { delete(context, it) }
                failures.add(ItemFailure(name, e.message ?: "Couldn't copy this file."))
            }
            listener.onItems(1)
        }
        return OpOutcome(completed, 0, failures)
    }

    /** Copies one document out to [destination] (a document the user just created through the system picker). */
    suspend fun copyOut(context: Context, source: Uri, destination: Uri, listener: ProgressListener): OpOutcome {
        val resolver = context.contentResolver
        val (name, size) = displayNameAndSize(context, source)
        listener.onTotals(size, 1)
        listener.onCurrent(name)
        val failures = ArrayList<ItemFailure>()
        var completed = 0
        val buffer = ByteArray(64 * 1024)
        try {
            val input = resolver.openInputStream(source) ?: throw FileNotFoundException("Couldn't open the source file.")
            input.use { src ->
                val output = resolver.openOutputStream(destination, "w") ?: throw IOException("Couldn't open the destination for writing.")
                output.use { dst ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buffer)
                        if (n < 0) break
                        dst.write(buffer, 0, n)
                        listener.onBytes(n.toLong())
                    }
                }
            }
            completed = 1
        } catch (e: kotlinx.coroutines.CancellationException) {
            delete(context, destination)
            throw e
        } catch (e: Exception) {
            delete(context, destination)
            failures.add(ItemFailure(name, e.message ?: "Couldn't copy this file."))
        }
        listener.onItems(1)
        return OpOutcome(completed, 0, failures)
    }
}
