package com.nexusarchive.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexusarchive.android.core.SortField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_settings")

/** All persisted settings. Every key here is read by real app behaviour; nothing is stored "for later". */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val density = stringPreferencesKey("density")
        val viewMode = stringPreferencesKey("view_mode")
        val showHidden = booleanPreferencesKey("show_hidden")
        val sortField = stringPreferencesKey("sort_field")
        val sortAsc = booleanPreferencesKey("sort_ascending")
        val foldersFirst = booleanPreferencesKey("folders_first")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val conflict = stringPreferencesKey("conflict_policy")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val notifications = booleanPreferencesKey("notifications")
        val showRecents = booleanPreferencesKey("show_recents")
        val bookmarks = stringPreferencesKey("bookmarks_json")
        val recentDirs = stringPreferencesKey("recent_dirs_json")
        val recentFiles = stringPreferencesKey("recent_files_json")
    }

    val settings: Flow<AppSettings> = context.nexusDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                themeMode = p[Keys.theme].toEnum(d.themeMode),
                dynamicColor = p[Keys.dynamic] ?: d.dynamicColor,
                density = p[Keys.density].toEnum(d.density),
                viewMode = p[Keys.viewMode].toEnum(d.viewMode),
                showHidden = p[Keys.showHidden] ?: d.showHidden,
                sortField = p[Keys.sortField].toEnum(d.sortField),
                sortAscending = p[Keys.sortAsc] ?: d.sortAscending,
                foldersFirst = p[Keys.foldersFirst] ?: d.foldersFirst,
                confirmDelete = p[Keys.confirmDelete] ?: d.confirmDelete,
                conflictPolicy = p[Keys.conflict].toEnum(d.conflictPolicy),
                reduceMotion = p[Keys.reduceMotion] ?: d.reduceMotion,
                notifications = p[Keys.notifications] ?: d.notifications,
                showRecents = p[Keys.showRecents] ?: d.showRecents,
                bookmarks = decodeList(p[Keys.bookmarks]),
                recentDirs = decodeList(p[Keys.recentDirs]),
                recentFiles = decodeList(p[Keys.recentFiles]),
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(v: ThemeMode) = put(Keys.theme, v.name)
    suspend fun setDynamicColor(v: Boolean) = put(Keys.dynamic, v)
    suspend fun setDensity(v: LayoutDensity) = put(Keys.density, v.name)
    suspend fun setViewMode(v: ViewMode) = put(Keys.viewMode, v.name)
    suspend fun setShowHidden(v: Boolean) = put(Keys.showHidden, v)
    suspend fun setSortField(v: SortField) = put(Keys.sortField, v.name)
    suspend fun setSortAscending(v: Boolean) = put(Keys.sortAsc, v)
    suspend fun setFoldersFirst(v: Boolean) = put(Keys.foldersFirst, v)
    suspend fun setConfirmDelete(v: Boolean) = put(Keys.confirmDelete, v)
    suspend fun setConflictPolicy(v: ConflictPolicy) = put(Keys.conflict, v.name)
    suspend fun setReduceMotion(v: Boolean) = put(Keys.reduceMotion, v)
    suspend fun setNotifications(v: Boolean) = put(Keys.notifications, v)
    suspend fun setShowRecents(v: Boolean) = put(Keys.showRecents, v)

    suspend fun toggleBookmark(path: String) {
        context.nexusDataStore.edit { p ->
            val list = decodeList(p[Keys.bookmarks])
            val updated = if (path in list) list - path else list + path
            p[Keys.bookmarks] = encodeList(updated)
        }
    }

    suspend fun removeBookmark(path: String) {
        context.nexusDataStore.edit { p ->
            p[Keys.bookmarks] = encodeList(decodeList(p[Keys.bookmarks]).filter { it != path })
        }
    }

    suspend fun recordRecentDir(path: String) {
        context.nexusDataStore.edit { p ->
            if (p[Keys.showRecents] == false) return@edit
            p[Keys.recentDirs] = encodeList((listOf(path) + decodeList(p[Keys.recentDirs]).filter { it != path }).take(MAX_RECENTS))
        }
    }

    suspend fun recordRecentFile(path: String) {
        context.nexusDataStore.edit { p ->
            if (p[Keys.showRecents] == false) return@edit
            p[Keys.recentFiles] = encodeList((listOf(path) + decodeList(p[Keys.recentFiles]).filter { it != path }).take(MAX_RECENTS))
        }
    }

    suspend fun clearRecents() {
        context.nexusDataStore.edit { p ->
            p[Keys.recentDirs] = encodeList(emptyList())
            p[Keys.recentFiles] = encodeList(emptyList())
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.nexusDataStore.edit { it[key] = value }
    }

    private companion object {
        const val MAX_RECENTS = 12

        fun encodeList(values: List<String>): String = JSONArray(values).toString()

        fun decodeList(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { array.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E {
    if (this == null) return default
    return try {
        enumValueOf<E>(this)
    } catch (e: IllegalArgumentException) {
        default
    }
}
