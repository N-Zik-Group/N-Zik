package app.n_zik.android.playback.services.automotive.models

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Profile-owned bounded JPEG loading shared by metadata mapping and the artwork provider. */
object SessionArtworkLoader {
    private val artworkBytesCache = ArtworkBytesCache()

    private fun resolveArtworkFile(url: String, barePath: String): File = try {
        java.nio.file.Paths.get(java.net.URI(url)).toFile()
    } catch (_: Exception) { File(barePath) }

    suspend fun load(url: String?, cropOverride: Boolean? = null): ByteArray? = withContext(NzikDispatchers.DATA) {
        if (url.isNullOrBlank()) return@withContext null
        val file = if (url.startsWith("file:") || url.startsWith("/")) {
            resolveArtworkFile(url, url.removePrefix("file://"))
        } else null
        val context = appContext()
        val profile = getActiveProfile(context)
        val revision = ArtworkRevisions.current()
        val square = cropOverride ?: context.preferences.getBoolean(cropVideoThumbnailsKey, true)
        val cacheOnly = !NetworkQualityHelper.isNetworkConnected(context)
        val contentKey = "$profile|$revision|$square|${file?.absolutePath ?: url}|${file?.lastModified()}|${file?.length()}"
        val highKey = "$contentKey|HIGH"
        if (cacheOnly) artworkBytesCache.get(highKey)?.let { return@withContext it }
        val key = if (cacheOnly && file == null) "$contentKey|offline" else highKey
        if (file != null && !file.exists()) return@withContext null
        artworkBytesCache.getOrLoad(key) {
            try {
                val bytes = when {
                    file != null -> runInterruptible(NzikDispatchers.DATA) {
                        encodeOwnedArtwork(square = square) { ArtworkBitmapDecoder.file(file) }
                    }
                    url.startsWith("content://") -> runInterruptible(NzikDispatchers.DATA) {
                        encodeOwnedArtwork(square = square) {
                            ArtworkBitmapDecoder.decode { context.contentResolver.openInputStream(Uri.parse(url)) }
                        }
                    }
                    else -> {
                        val qualities = if (cacheOnly) listOf(ImageCacheFactory.NetworkQuality.HIGH,
                            ImageCacheFactory.NetworkQuality.MEDIUM, ImageCacheFactory.NetworkQuality.LOW)
                        else listOf(ImageCacheFactory.NetworkQuality.HIGH)
                        var borrowed: Bitmap? = null
                        for (quality in qualities) {
                            borrowed = ImageCacheFactory.loadBitmap(url, requestSize = 512, qualityOverride = quality,
                                cacheOnly = cacheOnly, cropToSquare = square)
                            if (borrowed != null) break
                        }
                        borrowed?.let { bitmap ->
                            runInterruptible(NzikDispatchers.DATA) {
                                encodeOwnedArtwork {
                                    boundedOwnedCopy(bitmap, square)
                                }
                            }
                        }
                    }
                } ?: return@getOrLoad null
                if (profile != getActiveProfile(context) || revision != ArtworkRevisions.current()) return@getOrLoad null
                bytes
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.tag("AutoArtwork").w(exception, "Could not decode session artwork")
                null
            }
        }.takeIf { profile == getActiveProfile(context) && revision == ArtworkRevisions.current() }
    }

    /** Draws directly into an owned bounded target, preserving the borrowed source and crop resolution. */
    internal fun boundedOwnedCopy(source: Bitmap, square: Boolean): Bitmap {
        val side = minOf(source.width, source.height)
        val sourceRect = if (square) Rect((source.width - side) / 2, (source.height - side) / 2,
            (source.width + side) / 2, (source.height + side) / 2)
        else Rect(0, 0, source.width, source.height)
        val scale = minOf(1.0, ArtworkBitmapDecoder.MAX_SIDE.toDouble() / maxOf(sourceRect.width(), sourceRect.height()))
        val target = Bitmap.createBitmap((sourceRect.width() * scale).roundToInt().coerceAtLeast(1),
            (sourceRect.height() * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            Canvas(target).drawBitmap(source, sourceRect, Rect(0, 0, target.width, target.height), Paint(Paint.FILTER_BITMAP_FLAG))
            return target
        } catch (error: Throwable) {
            target.recycle()
            throw error
        }
    }

    // Ownership stays inside one interruptible block: prompt cancellation may discard its
    // return value, so only encoded bytes may cross that suspension boundary.
    private fun encodeOwnedArtwork(square: Boolean = false, decode: () -> Bitmap?): ByteArray? {
        val bitmap = decode() ?: return null
        return try {
            ArtworkBitmapDecoder.jpeg(bitmap, square)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

}
