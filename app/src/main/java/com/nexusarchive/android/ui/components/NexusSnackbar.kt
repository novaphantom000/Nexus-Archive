package com.nexusarchive.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.ui.theme.floatingContainerColor

/**
 * Snackbars that follow the active theme (and the liquid-glass look) and sit just above the floating
 * navigation pill instead of behind it.
 */
@Composable
fun NexusSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = state,
        modifier = modifier.padding(bottom = LocalFloatingBarInset.current + 8.dp),
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(20.dp),
                containerColor = floatingContainerColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionColor = MaterialTheme.colorScheme.primary,
                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
