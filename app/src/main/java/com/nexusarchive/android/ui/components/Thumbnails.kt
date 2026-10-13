package com.nexusarchive.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.nexusarchive.android.core.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Loads small previews for images, videos and APK icons. Decoding is bounded: at most three run at once,
 * images are sub-sampled, and results sit in a memory cache sized to a sixteenth of the app heap.
 * Anything that fails to decode simply falls back to the normal file-type icon.
 */
object ThumbnailLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16L).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val permits = Semaphore(3)

    suspend fun load(context: Context, path: String, kind: FileKind, lastModified: Long, sizePx: Int): Bitmap? {
        val key = "$path|$lastModified|$sizePx"
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            permits.withPermit {
                try {
                    when (kind) {
                        FileKind.IMAGE -> decodeImage(path, sizePx)
                        FileKind.VIDEO -> videoFrame(path, sizePx)
                        FileKind.APK -> apkIcon(context, path, sizePx)
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private fun decodeImage(path: String, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        val options = BitmapFactory.Options()
        options.inSampleSize = sample
        return BitmapFactory.decodeFile(path, options)
    }

    private fun videoFrame(path: String, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val frame = retriever.frameAtTime ?: return null
            val longest = maxOf(frame.width, frame.height)
            if (longest <= sizePx * 2) return frame
            val scale = (sizePx * 2).toFloat() / longest
            return Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true)
        } finally {
            retriever.release()
        }
    }

    private fun apkIcon(context: Context, path: String, sizePx: Int): Bitmap? {
        if (!path.endsWith(".apk", ignoreCase = true)) return null
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(path, 0) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = path
        appInfo.publicSourceDir = path
        return appInfo.loadIcon(pm).toBitmap(sizePx, sizePx)
    }
}

/** A real preview for images, videos and APKs; the tonal file-type icon for everything else and while loading. */
@Composable
fun KindIconOrThumbnail(kind: FileKind, path: String?, lastModified: Long, size: Dp) {
    val wantsThumbnail = path != null && (
        kind == FileKind.IMAGE || kind == FileKind.VIDEO || (kind == FileKind.APK && path.endsWith(".apk", ignoreCase = true))
        )
    if (!wantsThumbnail || path == null) {
        FileKindIcon(kind = kind, size = size)
        return
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { (size * 2).roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, lastModified, px) {
        value = ThumbnailLoader.load(context, path, kind, lastModified, px)?.asImageBitmap()
    }
    val loaded = bitmap
    if (loaded == null) {
        FileKindIcon(kind = kind, size = size)
    } else {
        Image(
            bitmap = loaded,
            contentDescription = null,
            contentScale = if (kind == FileKind.APK) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}
