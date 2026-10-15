package com.nexusarchive.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.Motion

/**
 * Softly dissolves the top edge of a scrolling list as rows slide under the path bar, instead of cutting them
 * off with a hard line. It uses transparency, not a colour, so it looks right on every theme and with glass on.
 * The fade is off while the list is at its very top so the first row stays fully visible.
 */
@Composable
fun Modifier.topFade(canScrollBackward: Boolean, height: Dp = 28.dp): Modifier {
    val reduce = LocalReduceMotion.current
    val strength by animateFloatAsState(
        targetValue = if (canScrollBackward) 1f else 0f,
        animationSpec = Motion.effects(reduce),
        label = "topFade",
    )
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val h = height.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 1f - strength), Color.Black),
                    startY = 0f,
                    endY = h,
                ),
                size = Size(size.width, h),
                blendMode = BlendMode.DstIn,
            )
        }
}
