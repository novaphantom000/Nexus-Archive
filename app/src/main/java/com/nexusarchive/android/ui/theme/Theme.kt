package com.nexusarchive.android.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nexusarchive.android.data.ThemeMode
import com.nexusarchive.android.data.ThemeStyle

// Default palette: #777C6D, #B7B89F, #CBCBCB and #EEEEEE. The light scheme uses #EEEEEE as the background with
// a slightly deepened #777C6D as the accent (so white text on it stays readable); the dark scheme turns the
// same colours into a dark olive-grey surface with #B7B89F and #CBCBCB as the accents.
private val DefaultLight = lightColorScheme(
    primary = Color(0xFF5F6455),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7B89F),
    onPrimaryContainer = Color(0xFF1C1E17),
    inversePrimary = Color(0xFFB7B89F),
    secondary = Color(0xFF6A6F60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCBCBCB),
    onSecondaryContainer = Color(0xFF25271F),
    tertiary = Color(0xFF6B7260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9DACB),
    onTertiaryContainer = Color(0xFF1F2218),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEEEEEE),
    onBackground = Color(0xFF1D1E1A),
    surface = Color(0xFFEEEEEE),
    onSurface = Color(0xFF1D1E1A),
    surfaceVariant = Color(0xFFDDDDD2),
    onSurfaceVariant = Color(0xFF46483E),
    surfaceTint = Color(0xFF5F6455),
    inverseSurface = Color(0xFF31332C),
    inverseOnSurface = Color(0xFFEEEEEE),
    outline = Color(0xFF777C6D),
    outlineVariant = Color(0xFFB7B89F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7F7F5),
    surfaceDim = Color(0xFFD6D6D4),
    surfaceContainerLowest = Color(0xFFF8F8F8),
    surfaceContainerLow = Color(0xFFF2F2F1),
    surfaceContainer = Color(0xFFE9E9E7),
    surfaceContainerHigh = Color(0xFFE2E2DF),
    surfaceContainerHighest = Color(0xFFDBDBD8),
)

private val DefaultDark = darkColorScheme(
    primary = Color(0xFFB7B89F),
    onPrimary = Color(0xFF2A2C1F),
    primaryContainer = Color(0xFF4A4E40),
    onPrimaryContainer = Color(0xFFE4E5D8),
    inversePrimary = Color(0xFF5F6455),
    secondary = Color(0xFFCBCBCB),
    onSecondary = Color(0xFF2F302A),
    secondaryContainer = Color(0xFF45473E),
    onSecondaryContainer = Color(0xFFE2E2D6),
    tertiary = Color(0xFF9FA690),
    onTertiary = Color(0xFF22251B),
    tertiaryContainer = Color(0xFF3E4236),
    onTertiaryContainer = Color(0xFFDCDDC9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121310),
    onBackground = Color(0xFFE4E5DE),
    surface = Color(0xFF121310),
    onSurface = Color(0xFFE4E5DE),
    surfaceVariant = Color(0xFF44463E),
    onSurfaceVariant = Color(0xFFC9CAB9),
    surfaceTint = Color(0xFFB7B89F),
    inverseSurface = Color(0xFFE4E5DE),
    inverseOnSurface = Color(0xFF31332C),
    outline = Color(0xFF8F9183),
    outlineVariant = Color(0xFF44463E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383A33),
    surfaceDim = Color(0xFF121310),
    surfaceContainerLowest = Color(0xFF0D0E0B),
    surfaceContainerLow = Color(0xFF1A1B16),
    surfaceContainer = Color(0xFF1E201A),
    surfaceContainerHigh = Color(0xFF292B24),
    surfaceContainerHighest = Color(0xFF34362F),
)

/** True when the liquid-glass overlay is on. Floating components read it to turn translucent. */
val LocalLiquidGlass = compositionLocalOf { false }

/** Container colour for floating surfaces: solid normally, translucent (so content shows through) with glass on. */
@Composable
fun floatingContainerColor(base: Color = MaterialTheme.colorScheme.surfaceContainerHigh): Color =
    if (LocalLiquidGlass.current) base.copy(alpha = 0.6f) else base

/** Hairline highlight used only by the glass look; floating surfaces have no outline otherwise. */
@Composable
fun floatingBorder(): BorderStroke? =
    if (LocalLiquidGlass.current) BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)) else null

// Larger corner radii than stock M3: the "expressive" rounded look.
val NexusShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

private val baseTypography = Typography()

val NexusTypography = Typography(
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = baseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = baseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun NexusTheme(
    themeMode: ThemeMode,
    themeStyle: ThemeStyle,
    presetId: String,
    liquidGlass: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val wantDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val preset = ThemePresets.byId(presetId)
    // A preset that only exists in dark (for example Dracula) stays dark even when the app is set to light.
    val dark = if (themeStyle == ThemeStyle.PRESET && preset.light == null) true else wantDark
    val colorScheme = when {
        themeStyle == ThemeStyle.MONET && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeStyle == ThemeStyle.PRESET ->
            if (dark) presetColorScheme(preset.dark, true) else presetColorScheme(preset.light ?: preset.dark, false)
        dark -> DefaultDark
        else -> DefaultLight
    }

    // Keep status/navigation bar icon contrast in sync with the in-app theme (not just the system theme).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalLiquidGlass provides liquidGlass) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexusTypography,
            shapes = NexusShapes,
            content = content,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
