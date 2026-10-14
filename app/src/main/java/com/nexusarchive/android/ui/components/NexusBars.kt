@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.Motion

/** Height the floating navigation pill covers at the bottom of the screen. Scrollable content adds it as bottom padding. */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

data class NavItem(val label: String, val icon: ImageVector, val badge: Int = 0)

/**
 * Floating pill navigation. Tabs are icon-only; the selected tab grows a tonal pill with its label.
 * Search sits beside the pill as its own round button.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    NavPillItem(item = item, selected = index == selectedIndex, onClick = { onSelect(index) })
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            onClick = onSearch,
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 4.dp,
            modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = colors.onSurface)
            }
        }
    }
}

@Composable
private fun NavPillItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else Color.Transparent,
        animationSpec = Motion.effects(reduce),
        label = "navItemContainer",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        border = if (selected) BorderStroke(1.dp, colors.outline.copy(alpha = 0.55f)) else null,
        modifier = Modifier
            .height(48.dp)
            .semantics(mergeDescendants = true) { this.selected = selected },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = {
                    if (item.badge > 0) Badge { Text(item.badge.toString()) }
                },
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = if (selected) null else item.label,
                    tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(animationSpec = Motion.spatial(reduce)) + fadeIn(animationSpec = Motion.effects(reduce)),
                exit = shrinkHorizontally(animationSpec = Motion.spatial(reduce)) + fadeOut(animationSpec = Motion.effects(reduce)),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Floating selection toolbar: close button, live count, then the actions passed in [actions].
 * Used by every screen that supports multi-select so selection looks and behaves the same everywhere.
 */
@Composable
fun SelectionPill(
    count: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 6.dp,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection") }
            if (reduce) {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            } else {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "selectionCount",
                ) { value ->
                    Text(value.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}
