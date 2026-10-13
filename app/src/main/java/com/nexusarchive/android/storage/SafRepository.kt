package com.nexusarchive.android.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

private val Context.safDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_saf_storage")

/**
 * Saved external-storage folders. Only the document-tree URI, label and timestamps are stored.
 * Removing an entry never touches the files in the folder.
 */
class SafRepository(private val context: Context) {

    private val key = stringPreferencesKey("bookmarks_v1")

    val bookmarks: Flow<List<SafBookmark>> = context.safDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[key]) }

    suspend fun snapshot(): List<SafBookmark> = bookmarks.first()

    /** Adds the folder, or returns the existing entry when this exact tree is already saved. */
    suspend fun add(treeUri: String, label: String, kind: SafKind, now: Long = System.currentTimeMillis()): SafBookmark {
        var result: SafBookmark? = null
        context.safDataStore.edit { prefs ->
            val list = decode(prefs[key]).toMutableList()
            val existing = list.firstOrNull { it.treeUri == treeUri }
            if (existing != null) {
                result = existing
            } else {
                val created = SafBookmark(UUID.randomUUID().toString(), label, treeUri, kind, now, now)
                list.add(created)
                prefs[key] = encode(list)
                result = created
            }
        }
        return result!!
    }

    suspend fun rename(id: String, label: String) = update(id) { it.copy(label = label) }

    suspend fun setKind(id: String, kind: SafKind) = update(id) { it.copy(kind = kind) }

    suspend fun replaceTree(id: String, treeUri: String) = update(id) { it.copy(treeUri = treeUri) }

    suspend fun touch(id: String, now: Long = System.currentTimeMillis()) = update(id) { it.copy(lastAccessedAt = now) }

    suspend fun remove(id: String) {
        context.safDataStore.edit { prefs ->
            val list = decode(prefs[key]).filter { it.id != id }
            prefs[key] = encode(list)
        }
    }

    private suspend fun update(id: String, transform: (SafBookmark) -> SafBookmark) {
        context.safDataStore.edit { prefs ->
            val list = decode(prefs[key]).map { if (it.id == id) transform(it) else it }
            prefs[key] = encode(list)
        }
    }

    private fun encode(list: List<SafBookmark>): String {
        val array = JSONArray()
        for (b in list) {
            val o = JSONObject()
            o.put("id", b.id)
            o.put("label", b.label)
            o.put("tree", b.treeUri)
            o.put("kind", b.kind.name)
            o.put("created", b.createdAt)
            o.put("accessed", b.lastAccessedAt)
            array.put(o)
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<SafBookmark> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val out = ArrayList<SafBookmark>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val kind = try {
                    SafKind.valueOf(o.optString("kind", SafKind.FOLDER.name))
                } catch (e: IllegalArgumentException) {
                    SafKind.FOLDER
                }
                out.add(
                    SafBookmark(
                        id = o.getString("id"),
                        label = o.getString("label"),
                        treeUri = o.getString("tree"),
                        kind = kind,
                        createdAt = o.optLong("created", 0L),
                        lastAccessedAt = o.optLong("accessed", 0L),
                    ),
                )
            }
            out
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }
}
