package app.n_zik.android.playback.services.automotive.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.thumbnail
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.n_zik.android.appContext
import android.net.Uri
import android.os.Bundle

@UnstableApi
object SessionMediaItemMapper {

    /**
     * Longest side (px) of artwork embedded into Android Auto items: the head
     * unit renders queue and browse artwork at list size, so full-resolution
     * covers only bloat the IPC parcel and the service RAM.
     */
    private const val EMBEDDED_ARTWORK_MAX_SIDE = 512

    private val artworkBytesCache = ArtworkBytesCache()

    @Volatile
    private var serializedFallbackBytes: ByteArray? = null

    /**
     * Largest power-of-two inSampleSize keeping the decoded bitmap at or below
     * [targetMaxSide] on its longest side. Pure int math so it stays
     * unit-testable off device; the decode helpers feed it the bounds of an
     * inJustDecodeBounds pass.
     */
    internal fun computeInSampleSize(reqWidth: Int, reqHeight: Int, targetMaxSide: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0 || targetMaxSide <= 0) return 1
        val maxSide = maxOf(reqWidth, reqHeight)
        if (maxSide <= targetMaxSide) return 1
        var sampleSize = 1
        while (maxSide / (sampleSize * 2) >= targetMaxSide) sampleSize *= 2
        return sampleSize
    }

    /**
     * Decodes [path] downscaled toward [EMBEDDED_ARTWORK_MAX_SIDE]: two passes,
     * bounds first, then the sampled decode, so the RAM peak never exceeds the
     * target size.
     */
    private fun decodeFileDownsampled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, EMBEDDED_ARTWORK_MAX_SIDE)
            }
        )
    }

    /** Same downsample contract as [decodeFileDownsampled] for stream-sourced bytes. */
    private fun decodeBytesDownsampled(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, EMBEDDED_ARTWORK_MAX_SIDE)
            }
        )
    }

    /**
     * Serialized fallback artwork for art-less local tracks: an
     * android.resource:// URI is unresolvable from the Android Auto head unit
     * (separate device), so the placeholder is embedded as bytes instead of a
     * URI. Decoded once — downscaled toward [EMBEDDED_ARTWORK_MAX_SIDE] like
     * every other embedded cover (the source drawable is 1200px) — then
     * shared for the process lifetime.
     */
    @Synchronized
    private fun fallbackArtworkBytes(): ByteArray? {
        serializedFallbackBytes?.let { return it }
        return try {
            val full = BitmapFactory.decodeResource(appContext().resources, R.drawable.ic_launcher_box)
                ?: return null
            // The source placeholder is 1200px — downscale toward the 512px
            // embed target like every other embedded cover (one-time decode,
            // the bytes are shared for the process lifetime).
            val bitmap = if (minOf(full.width, full.height) > EMBEDDED_ARTWORK_MAX_SIDE) {
                Bitmap.createScaledBitmap(
                    full,
                    EMBEDDED_ARTWORK_MAX_SIDE,
                    EMBEDDED_ARTWORK_MAX_SIDE,
                    true
                )
            } else full
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            bitmap.recycle()
            if (bitmap !== full) full.recycle()
            val bytes = stream.toByteArray()
            serializedFallbackBytes = bytes
            bytes
        } catch (_: Exception) {
            null
        }
    }


    /**
     * Artwork-less items must still render on the head unit: embed the
     * serialized placeholder. Falls back to the URI only when the placeholder
     * cannot be decoded (keeps phone-side rendering alive).
     */
    private fun setSerializedFallback(metadataBuilder: MediaMetadata.Builder) {
        val fallback = fallbackArtworkBytes()
        if (fallback != null) {
            metadataBuilder.setArtworkData(fallback, MediaMetadata.PICTURE_TYPE_ILLUSTRATION)
        } else {
            metadataBuilder.setArtworkUri(drawableUri(appContext(), R.drawable.ic_launcher_box))
        }
    }

    /**
     * Resolves the artwork file for a `file://` URL or a bare absolute path.
     * `java.nio.file.Paths.get(URI)` understands Windows drive URIs
     * (`file:///C:/...`), which `File("/C:/...")` silently misresolves
     * against the current drive root.
     */
    private fun resolveArtworkFile(url: String, barePath: String): File =
        try {
            java.nio.file.Paths.get(java.net.URI(url)).toFile()
        } catch (_: Exception) {
            File(barePath)
        }

    private fun loadArtworkBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        // Embedded artwork is paid once per source per process lifetime: queue
        // rebuilds (shuffle, next, playback resumption) share the bytes instead
        // of re-decoding and re-compressing every cover.
        artworkBytesCache.get(url)?.let { return it }
        return try {
            val bytes: ByteArray? = when {
                url.startsWith("file://") || url.startsWith("/") -> {
                    val path = url.removePrefix("file://")
                    val file = resolveArtworkFile(url, path)
                    if (!file.exists()) null
                    else {
                        val bitmap = decodeFileDownsampled(path) ?: return null
                        val rotated = applyExifRotation(path, bitmap)
                        val cropped = centerCrop(rotated)
                        val stream = ByteArrayOutputStream()
                        cropped.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        if (cropped !== rotated) cropped.recycle()
                        if (rotated !== bitmap) rotated.recycle()
                        bitmap.recycle()
                        stream.toByteArray()
                    }
                }

                url.startsWith("content://") -> {
                    val uri = Uri.parse(url)
                    val data = appContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return null
                    val bitmap = decodeBytesDownsampled(data) ?: return null
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    bitmap.recycle()
                    stream.toByteArray()
                }

                // runBlocking justified: loadArtworkBytes is private non-suspend called from 5 non-suspend public mappers
                else -> runBlocking(NzikDispatchers.DATA) {
                    ImageCacheFactory.loadBitmap(url, allowHardware = false)
                }?.let { bitmap ->
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    stream.toByteArray()
                }
            }
            bytes?.let {
                artworkBytesCache.put(url, it)
                it
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isArtworkAvailable(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.startsWith("content://")) {
            return try {
                appContext().contentResolver.openInputStream(Uri.parse(url))?.close()
                true
            } catch (e: Exception) {
                false
            }
        }
        if (url.startsWith("file://") || url.startsWith("/")) {
            return File(url.removePrefix("file://")).exists()
        }
        return true
    }

    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) { bitmap }
    }

    private fun centerCrop(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    fun mapArtistToMediaItem(
        parentId: String,
        id: String,
        name: String,
        thumbnailUrl: String?,
        subtext: String? = null,
        searchPath: String = "",
        loadArtwork: Boolean = false
    ): MediaItem {
        val cleanUrl = thumbnailUrl?.let { cleanPrefix(it) }
        val iconUri = cleanUrl?.thumbnail(250)?.toUri()
        val artworkBytes = if (loadArtwork) loadArtworkBytes(cleanUrl) else null
        val item = browsableMediaItem(
            id = "$parentId/$id",
            title = name,
            subtitle = subtext,
            iconUri = if (artworkBytes == null) iconUri else null,
            mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
            path = searchPath.ifEmpty { parentId }
        )
        return if (artworkBytes != null) {
            item.buildUpon().setMediaMetadata(
                item.mediaMetadata.buildUpon().setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_ILLUSTRATION).build()
            ).build()
        } else item
    }

    fun mapAlbumToMediaItem(
        parentId: String,
        id: String,
        title: String,
        authorsText: String?,
        thumbnailUrl: String?,
        searchPath: String = "",
        loadArtwork: Boolean = false
    ): MediaItem {
        val cleanUrl = thumbnailUrl?.let { cleanPrefix(it) }
        val iconUri = cleanUrl?.thumbnail(250)?.toUri()
        val artworkBytes = if (loadArtwork) loadArtworkBytes(cleanUrl) else null
        val item = browsableMediaItem(
            id = "$parentId/$id",
            title = title,
            subtitle = authorsText,
            iconUri = if (artworkBytes == null) iconUri else null,
            mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
            path = searchPath.ifEmpty { parentId }
        )
        return if (artworkBytes != null) {
            item.buildUpon().setMediaMetadata(
                item.mediaMetadata.buildUpon().setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_ILLUSTRATION).build()
            ).build()
        } else item
    }


    fun mapSongToMediaItem(song: Song, path: String, loadArtwork: Boolean = false): MediaItem {
        val baseItem = song.asMediaItem
        var metadataBuilder = baseItem.mediaMetadata.buildUpon()

        if (loadArtwork) {
            if (song.isLocal) {
                // Load artwork from media store for on-device songs
                val artworkUrl = song.thumbnailUrl?.let { cleanPrefix(it) }
                val artworkBytes = loadArtworkBytes(artworkUrl)
                if (artworkBytes != null) {
                    metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_ILLUSTRATION)
                } else {
                    setSerializedFallback(metadataBuilder)
                }
            } else {
                // Load artwork via Coil (handles EXIF rotation) for Android Auto
                val artworkUrl = song.thumbnailUrl?.let { cleanPrefix(it) }
                val artworkBytes = loadArtworkBytes(artworkUrl)
                if (artworkBytes != null) {
                    metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_ILLUSTRATION)
                } else {
                    setSerializedFallback(metadataBuilder)
                }
            }
        } else {
            val artworkUrl = song.thumbnailUrl?.let { cleanPrefix(it) }
            if (artworkUrl != null) {
                if (!isArtworkAvailable(artworkUrl)) {
                    metadataBuilder.setArtworkUri(drawableUri(appContext(), R.drawable.ic_launcher_box))
                } else {
                    metadataBuilder.setArtworkUri(Uri.parse(artworkUrl))
                }
            } else {
                metadataBuilder.setArtworkUri(drawableUri(appContext(), R.drawable.ic_launcher_box))
            }
        }



        val extras = Bundle(metadataBuilder.build().extras ?: Bundle()).apply {
            if (!song.isLocal) {
                putLong("android.media.metadata.DURATION", durationTextToMillis(song.durationText.orEmpty()))
            }
        }
        metadataBuilder.setExtras(extras)

        return if (path.contains(AutoSessionConstants.ID_SEARCH_VIDEOS)) {
            baseItem.buildUpon()
                .setMediaId("$path/${song.id}")
                .setMediaMetadata(metadataBuilder.setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO).build())
                .build()
        } else {
            baseItem.buildUpon()
                .setMediaId("$path/${song.id}")
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }
    }

    fun mapSongToMediaItem(song: Song, isFromPersistentQueue: Boolean = false, loadArtwork: Boolean = false): MediaItem {
        val mediaItem = song.asMediaItem
        val existingExtras = mediaItem.mediaMetadata.extras ?: Bundle()
        val bundle = Bundle(existingExtras).apply {
            putBoolean(persistentQueueKey, isFromPersistentQueue)
        }

        var metadataBuilder = mediaItem.mediaMetadata
            .buildUpon()
            .setExtras(bundle)

        if (loadArtwork) {
            // Load artwork for queue display (needed for on-device in AA)
            if (song.isLocal) {
                val artworkUrl = song.thumbnailUrl?.let { cleanPrefix(it) }
                val artworkBytes = loadArtworkBytes(artworkUrl)
                if (artworkBytes != null) {
                    metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_ILLUSTRATION)
                } else {
                    setSerializedFallback(metadataBuilder)
                }
            }
        } else {
            val artworkUrl = song.thumbnailUrl?.let { cleanPrefix(it) }
            if (artworkUrl != null) {
                if (!isArtworkAvailable(artworkUrl)) {
                    metadataBuilder.setArtworkUri(drawableUri(appContext(), R.drawable.ic_launcher_box))
                } else {
                    metadataBuilder.setArtworkUri(Uri.parse(artworkUrl))
                }
            } else {
                metadataBuilder.setArtworkUri(drawableUri(appContext(), R.drawable.ic_launcher_box))
            }
        }

        return mediaItem.buildUpon().setMediaMetadata(metadataBuilder.build()).build()
    }


}
