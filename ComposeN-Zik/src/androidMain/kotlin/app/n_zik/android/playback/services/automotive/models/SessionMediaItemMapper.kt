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
import java.io.ByteArrayOutputStream
import java.io.File
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.n_zik.android.appContext
import android.net.Uri
import android.os.Bundle

@UnstableApi
object SessionMediaItemMapper {

    private fun loadArtworkBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return try {
            if (url.startsWith("file://") || url.startsWith("/")) {
                val path = url.removePrefix("file://")
                val file = File(path)
                if (!file.exists()) return null
                val bitmap = BitmapFactory.decodeFile(path) ?: return null
                val rotated = applyExifRotation(path, bitmap)
                val cropped = centerCrop(rotated)
                val stream = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                if (cropped !== rotated) cropped.recycle()
                if (rotated !== bitmap) rotated.recycle()
                bitmap.recycle()
                return stream.toByteArray()
            }
            if (url.startsWith("content://")) {
                val uri = Uri.parse(url)
                val inputStream = appContext().contentResolver.openInputStream(uri) ?: return null
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) return null
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                return stream.toByteArray()
            }
            // runBlocking justified: loadArtworkBytes is private non-suspend called from 5 non-suspend public mappers
            val bitmap = kotlinx.coroutines.runBlocking {
                ImageCacheFactory.loadBitmap(url, allowHardware = false)
            } ?: return null
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
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
                    metadataBuilder.setArtworkUri(drawableUri(appContext(), R.drawable.ic_launcher_box))
                }
            } else {
                // Load artwork via Coil (handles EXIF rotation) for Android Auto
                val artworkUrl = song.thumbnailUrl?.let { cleanPrefix(it) }
                val artworkBytes = loadArtworkBytes(artworkUrl)
                if (artworkBytes != null) {
                    metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_ILLUSTRATION)
                } else {
                    metadataBuilder.setArtworkUri(drawableUri(appContext(), R.drawable.ic_launcher_box))
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
                    metadataBuilder.setArtworkUri(drawableUri(appContext(), R.drawable.ic_launcher_box))
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
