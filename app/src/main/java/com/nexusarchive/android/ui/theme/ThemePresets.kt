package com.nexusarchive.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** The handful of colours a preset defines; every Material 3 role is derived from these. */
class PresetColors(
    val bg: Color,
    val fg: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val error: Color,
)

/** A named colour scheme. [light] is null when the original scheme has no light variant. */
class ThemePreset(val id: String, val name: String, val dark: PresetColors, val light: PresetColors?)

private fun c(rgb: Long): Color = Color(0xFF000000L or rgb)

object ThemePresets {
    val all: List<ThemePreset> = listOf(
        ThemePreset(
            "catppuccin", "Catppuccin",
            dark = PresetColors(c(0x1E1E2E), c(0xCDD6F4), c(0xCBA6F7), c(0x89B4FA), c(0x94E2D5), c(0xF38BA8)),
            light = PresetColors(c(0xEFF1F5), c(0x4C4F69), c(0x8839EF), c(0x1E66F5), c(0x179299), c(0xD20F39)),
        ),
        ThemePreset(
            "dracula", "Dracula",
            dark = PresetColors(c(0x282A36), c(0xF8F8F2), c(0xBD93F9), c(0xFF79C6), c(0x8BE9FD), c(0xFF5555)),
            light = null,
        ),
        ThemePreset(
            "everforest", "Everforest",
            dark = PresetColors(c(0x2D353B), c(0xD3C6AA), c(0xA7C080), c(0x83C092), c(0x7FBBB3), c(0xE67E80)),
            light = PresetColors(c(0xFDF6E3), c(0x5C6A72), c(0x8DA101), c(0x35A77C), c(0x3A94C5), c(0xF85552)),
        ),
        ThemePreset(
            "gruvbox", "Gruvbox",
            dark = PresetColors(c(0x282828), c(0xEBDBB2), c(0xFABD2F), c(0x8EC07C), c(0x83A598), c(0xFB4934)),
            light = PresetColors(c(0xFBF1C7), c(0x3C3836), c(0xB57614), c(0x427B58), c(0x076678), c(0x9D0006)),
        ),
        ThemePreset(
            "nord", "Nord",
            dark = PresetColors(c(0x2E3440), c(0xD8DEE9), c(0x88C0D0), c(0x81A1C1), c(0xA3BE8C), c(0xBF616A)),
            light = PresetColors(c(0xECEFF4), c(0x2E3440), c(0x5E81AC), c(0x81A1C1), c(0x8FBCBB), c(0xBF616A)),
        ),
        ThemePreset(
            "onedark", "OneDark",
            dark = PresetColors(c(0x282C34), c(0xABB2BF), c(0x61AFEF), c(0xC678DD), c(0x98C379), c(0xE06C75)),
            light = PresetColors(c(0xFAFAFA), c(0x383A42), c(0x4078F2), c(0xA626A4), c(0x50A14F), c(0xE45649)),
        ),
        ThemePreset(
            "poimandres", "Poimandres",
            dark = PresetColors(c(0x1B1E28), c(0xA6ACCD), c(0x5DE4C7), c(0x89DDFF), c(0xF087BD), c(0xD0679D)),
            light = null,
        ),
        ThemePreset(
            "rosepine", "Ros\u00E9 Pine",
            dark = PresetColors(c(0x191724), c(0xE0DEF4), c(0xC4A7E7), c(0xEBBCBA), c(0x9CCFD8), c(0xEB6F92)),
            light = PresetColors(c(0xFAF4ED), c(0x575279), c(0x907AA9), c(0xD7827E), c(0x56949F), c(0xB4637A)),
        ),
        ThemePreset(
            "tokyonight", "TokyoNight",
            dark = PresetColors(c(0x1A1B26), c(0xC0CAF5), c(0x7AA2F7), c(0xBB9AF7), c(0x7DCFFF), c(0xF7768E)),
            light = PresetColors(c(0xE1E2E7), c(0x3760BF), c(0x2E7DE9), c(0x9854F1), c(0x007197), c(0xF52A65)),
        ),
    )

    fun byId(id: String): ThemePreset = all.firstOrNull { it.id == id } ?: all[0]
}

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** The most readable of the preset's own text colour, its background, white and black on top of [background]. */
private fun readableOn(background: Color, p: PresetColors): Color =
    listOf(p.fg, p.bg, Color.White, Color.Black).maxByOrNull { contrast(background, it) } ?: p.fg

/** Maps a preset onto every Material 3 colour role. Accent containers and surface tiers are blended from the preset. */
fun presetColorScheme(p: PresetColors, dark: Boolean): ColorScheme {
    val accentMix = if (dark) 0.34f else 0.26f
    val primaryContainer = lerp(p.bg, p.primary, accentMix)
    val secondaryContainer = lerp(p.bg, p.secondary, accentMix)
    val tertiaryContainer = lerp(p.bg, p.tertiary, accentMix)
    val errorContainer = lerp(p.bg, p.error, accentMix)
    val surfaceVariant = lerp(p.bg, p.fg, 0.10f)
    val inverseSurface = p.fg
    return if (dark) {
        darkColorScheme(
            primary = p.primary,
            onPrimary = readableOn(p.primary, p),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer, p),
            inversePrimary = lerp(p.bg, p.primary, 0.8f),
            secondary = p.secondary,
            onSecondary = readableOn(p.secondary, p),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer, p),
            tertiary = p.tertiary,
            onTertiary = readableOn(p.tertiary, p),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = readableOn(tertiaryContainer, p),
            error = p.error,
            onError = readableOn(p.error, p),
            errorContainer = errorContainer,
            onErrorContainer = readableOn(errorContainer, p),
            background = p.bg,
            onBackground = p.fg,
            surface = p.bg,
            onSurface = p.fg,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = lerp(p.fg, p.bg, 0.25f),
            surfaceTint = p.primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = readableOn(inverseSurface, p),
            outline = lerp(p.bg, p.fg, 0.5f),
            outlineVariant = lerp(p.bg, p.fg, 0.2f),
            scrim = Color.Black,
            surfaceBright = lerp(p.bg, p.fg, 0.14f),
            surfaceDim = lerp(p.bg, Color.Black, 0.15f),
            surfaceContainerLowest = lerp(p.bg, Color.Black, 0.25f),
            surfaceContainerLow = lerp(p.bg, p.fg, 0.03f),
            surfaceContainer = lerp(p.bg, p.fg, 0.06f),
            surfaceContainerHigh = lerp(p.bg, p.fg, 0.09f),
            surfaceContainerHighest = lerp(p.bg, p.fg, 0.12f),
        )
    } else {
        lightColorScheme(
            primary = p.primary,
            onPrimary = readableOn(p.primary, p),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer, p),
            inversePrimary = lerp(p.bg, p.primary, 0.8f),
            secondary = p.secondary,
            onSecondary = readableOn(p.secondary, p),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer, p),
            tertiary = p.tertiary,
            onTertiary = readableOn(p.tertiary, p),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = readableOn(tertiaryContainer, p),
            error = p.error,
            onError = readableOn(p.error, p),
            errorContainer = errorContainer,
            onErrorContainer = readableOn(errorContainer, p),
            background = p.bg,
            onBackground = p.fg,
            surface = p.bg,
            onSurface = p.fg,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = lerp(p.fg, p.bg, 0.25f),
            surfaceTint = p.primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = readableOn(inverseSurface, p),
            outline = lerp(p.bg, p.fg, 0.5f),
            outlineVariant = lerp(p.bg, p.fg, 0.2f),
            scrim = Color.Black,
            surfaceBright = lerp(p.bg, Color.White, 0.4f),
            surfaceDim = lerp(p.bg, p.fg, 0.10f),
            surfaceContainerLowest = lerp(p.bg, Color.White, 0.5f),
            surfaceContainerLow = lerp(p.bg, p.fg, 0.03f),
            surfaceContainer = lerp(p.bg, p.fg, 0.06f),
            surfaceContainerHigh = lerp(p.bg, p.fg, 0.09f),
            surfaceContainerHighest = lerp(p.bg, p.fg, 0.12f),
        )
    }
}
