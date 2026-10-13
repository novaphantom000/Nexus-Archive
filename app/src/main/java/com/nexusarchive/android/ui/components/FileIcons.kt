package com.nexusarchive.android.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexusarchive.android.core.FileKind
import com.nexusarchive.android.ui.theme.LocalReduceMotion
import com.nexusarchive.android.ui.theme.Motion

data class KindStyle(val icon: ImageVector, val container: Color, val content: Color)

@Composable
fun kindStyle(kind: FileKind): KindStyle {
    val c = MaterialTheme.colorScheme
    return when (kind) {
        FileKind.FOLDER -> KindStyle(Icons.Rounded.Folder, c.primaryContainer, c.onPrimaryContainer)
        FileKind.IMAGE -> KindStyle(Icons.Rounded.Image, c.tertiaryContainer, c.onTertiaryContainer)
        FileKind.VIDEO -> KindStyle(Icons.Rounded.VideoFile, c.errorContainer, c.onErrorContainer)
        FileKind.AUDIO -> KindStyle(Icons.Rounded.AudioFile, c.secondaryContainer, c.onSecondaryContainer)
        FileKind.DOCUMENT -> KindStyle(Icons.Rounded.Description, c.surfaceContainerHighest, c.onSurface)
        FileKind.ARCHIVE -> KindStyle(Icons.Rounded.FolderZip, c.primary, c.onPrimary)
        FileKind.APK -> KindStyle(Icons.Rounded.Android, c.tertiary, c.onTertiary)
        FileKind.CODE -> KindStyle(Icons.Rounded.Code, c.secondaryContainer, c.onSecondaryContainer)
        FileKind.DISK_IMAGE -> KindStyle(Icons.Rounded.Storage, c.surfaceContainerHighest, c.onSurface)
        FileKind.OTHER -> KindStyle(Icons.AutoMirrored.Rounded.InsertDriveFile, c.surfaceContainerHighest, c.onSurfaceVariant)
    }
}

/** Decorative tonal icon tile. The row it sits in carries the accessible name. */
@Composable
fun FileKindIcon(kind: FileKind, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val style = kindStyle(kind)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(style.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.content,
            modifier = Modifier.size(size * 0.54f),
        )
    }
}

/** File icon that cross-fades to a check mark while the item is selected. */
@Composable
fun SelectableLeading(
    kind: FileKind,
    selected: Boolean,
    size: Dp = 44.dp,
    path: String? = null,
    lastModified: Long = 0L,
    onToggle: (() -> Unit)? = null,
) {
    val reduce = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    val tapModifier = if (onToggle != null) {
        Modifier
            .clip(RoundedCornerShape(size * 0.34f))
            .clickable(onClickLabel = if (selected) "Deselect" else "Select", role = Role.Checkbox, onClick = onToggle)
    } else {
        Modifier
    }
    Box(modifier = tapModifier) {
    Crossfade(
        targetState = selected,
        animationSpec = Motion.effects(reduce),
        label = "leadingSelection",
    ) { isSelected ->
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(size * 0.54f),
                )
            }
        } else {
            KindIconOrThumbnail(kind = kind, path = path, lastModified = lastModified, size = size)
        }
    }
    }
}
