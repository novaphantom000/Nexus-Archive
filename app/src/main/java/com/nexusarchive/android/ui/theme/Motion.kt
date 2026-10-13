package com.nexusarchive.android.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf

/** True when the user asked for reduced motion in app settings, or Android's animation scale is 0. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Spring-based motion in the spirit of Material 3 Expressive:
 *  - [spatial]: springs with a little bounce, for movement and size.
 *  - [effects]: critically damped springs, for fades and color changes.
 * With reduced motion every spec collapses to an instant change.
 */
object Motion {
    fun <T> spatial(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

    fun <T> effects(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
}
