@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexusarchive.android.ui.operations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.operations.OperationState
import com.nexusarchive.android.operations.OperationStatus
import com.nexusarchive.android.operations.OperationType
import com.nexusarchive.android.ui.components.EmptyState
import com.nexusarchive.android.ui.components.ExpressiveProgressBar
import kotlinx.coroutines.delay

@Composable
fun OperationsScreen(vm: OperationsViewModel) {
    val operations by vm.operations.collectAsStateWithLifecycle()
    // Ticks only to refresh "elapsed" and "speed" text; progress itself comes from the engine's counters.
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(500)
            value = System.currentTimeMillis()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operations") },
                actions = {
                    if (operations.any { !it.isActive }) {
                        TextButton(onClick = vm::clearFinished) { Text("Clear finished") }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (operations.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.SwapVert,
                    title = "No operations yet",
                    message = "Copy, move and delete jobs show up here with real progress. This history is kept until the app is closed.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(operations, key = { it.id }) { op ->
                        OperationCard(
                            op = op,
                            now = now,
                            onCancel = { vm.cancel(op.id) },
                            onPause = { vm.pause(op.id) },
                            onResume = { vm.resume(op.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun OperationStatus.label(): String =
    when (this) {
        OperationStatus.QUEUED -> "Waiting"
        OperationStatus.PREPARING -> "Preparing\u2026"
        OperationStatus.RUNNING -> "Running"
        OperationStatus.SUCCEEDED -> "Done"
        OperationStatus.COMPLETED_WITH_ERRORS -> "Done with errors"
        OperationStatus.FAILED -> "Failed"
        OperationStatus.CANCELLED -> "Cancelled"
    }

@Composable
private fun OperationCard(op: OperationState, now: Long, onCancel: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val failedLike = op.status == OperationStatus.FAILED || op.status == OperationStatus.COMPLETED_WITH_ERRORS
    val container = if (failedLike) colors.errorContainer else colors.surfaceContainerLow
    val onContainer = if (failedLike) colors.onErrorContainer else colors.onSurface
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = onContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(onContainer.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (op.type) {
                            OperationType.COPY -> Icons.Rounded.ContentCopy
                            OperationType.MOVE -> Icons.Rounded.ContentCut
                            OperationType.DELETE -> Icons.Rounded.Delete
                            OperationType.COMPRESS -> Icons.Rounded.Archive
                            OperationType.EXTRACT -> Icons.Rounded.Unarchive
                            OperationType.TEST -> Icons.Rounded.CheckCircle
                        },
                        contentDescription = null,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(op.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (op.isActive && op.paused) "Paused" else op.status.label(), style = MaterialTheme.typography.labelLarge)
                }
            }

            if (op.isActive) {
                Spacer(Modifier.height(16.dp))
                val fraction = op.fraction
                if (fraction != null) {
                    ExpressiveProgressBar(
                        fraction = fraction,
                        color = colors.primary,
                        trackColor = colors.surfaceContainerHighest,
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(10.dp))
            val details = buildList {
                val fraction = op.fraction
                if (op.isActive && fraction != null) add("${(fraction * 100).toInt()}%")
                if (op.type != OperationType.DELETE && op.bytesTotal >= 0) {
                    add("${Formatters.bytes(op.bytesDone)} of ${Formatters.bytes(op.bytesTotal)}")
                } else if (op.itemsTotal >= 0) {
                    add("${op.itemsDone} of ${op.itemsTotal} items")
                }
                op.bytesPerSecond(now)?.let { add("${Formatters.bytes(it)}/s") }
                add("Elapsed ${Formatters.duration(op.elapsedMs(now))}")
            }
            Text(details.joinToString(" \u00B7 "), style = MaterialTheme.typography.bodySmall)

            op.currentItem?.let {
                Text("Current: $it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "From: " + if (op.sources.size == 1) op.sources[0] else "${op.sources.size} items",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            op.destination?.let {
                Text("To: $it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!op.isActive) {
                op.message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (op.failures.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide error details" else "Show error details (${op.failures.size})")
                }
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        op.failures.take(25).forEach { failure ->
                            Column {
                                Text(failure.path, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(failure.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (op.failures.size > 25) {
                            Text("\u2026and ${op.failures.size - 25} more", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (op.isActive) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (op.paused) {
                        FilledTonalButton(onClick = onResume) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text("  Resume")
                        }
                    } else {
                        FilledTonalButton(onClick = onPause) {
                            Icon(Icons.Rounded.Pause, contentDescription = null)
                            Text("  Pause")
                        }
                    }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}
