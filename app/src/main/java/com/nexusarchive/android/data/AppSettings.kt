package com.nexusarchive.android.data

import com.nexusarchive.android.core.SortConfig
import com.nexusarchive.android.core.SortField

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where the colours come from: the built-in palette, the system (Monet) palette, or a named preset. */
enum class ThemeStyle { DEFAULT, MONET, PRESET }

enum class LayoutDensity { COMPACT, COMFORTABLE, SPACIOUS }

enum class ViewMode { LIST, GRID }

enum class ConflictPolicy { ASK, KEEP_BOTH, REPLACE, SKIP }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val density: LayoutDensity = LayoutDensity.COMFORTABLE,
    val viewMode: ViewMode = ViewMode.LIST,
    val showHidden: Boolean = false,
    val sortField: SortField = SortField.NAME,
    val sortAscending: Boolean = true,
    val foldersFirst: Boolean = true,
    val confirmDelete: Boolean = true,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
    val reduceMotion: Boolean = false,
    val notifications: Boolean = true,
    val showRecents: Boolean = true,
    val themeStyle: ThemeStyle = ThemeStyle.DEFAULT,
    val themePreset: String = "catppuccin",
    val liquidGlass: Boolean = false,
    val recentsRespectHidden: Boolean = true,
    val itemScale: Float = 0.5f,
    val bookmarks: List<String> = emptyList(),
    val recentDirs: List<String> = emptyList(),
    val recentFiles: List<String> = emptyList(),
) {
    val sortConfig: SortConfig
        get() = SortConfig(sortField, sortAscending, foldersFirst, showHidden)
}
