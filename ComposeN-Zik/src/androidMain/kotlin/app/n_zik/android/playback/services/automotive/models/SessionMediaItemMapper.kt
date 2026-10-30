package app.n_zik.android.playback.services.automotive.models

import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants.EXTRAS_KEY_IS_EXPLICIT
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.thumbnail
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.LocalArtworkRules
import app.n_zik.android.playback.services.SessionMetadataFallbacks
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import timber.log.Timber

@UnstableApi
object SessionMediaItemMapper {

    const val ORIGINAL_ARTWORK_SOURCE_EXTRA = "app.n_zik.android.automotive.ORIGINAL_ARTWORK_SOURCE"

    private val artworkBytesCache = ArtworkBytesCache()

    @Volatile
    private var serializedFallbackBytes: ByteArray? = null

    // Sample toward the target before allocation; the final resize enforces the strict bound.
    internal fun computeInSampleSize(reqWidth: Int, reqHeight: Int, targetMaxSide: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0 || targetMaxSide <= 0) return 1
        val maxSide = maxOf(reqWidth, reqHeight)
        if (maxSide <= targetMaxSide) return 1
        var sampleSize = 1
        while (maxSide.toLong() / (sampleSize.toLong() * 2) >= targetMaxSide) sampleSize *= 2
        return sampleSize
    }

    @Synchronized
    private fun fallbackArtworkBytes(): ByteArray? {
        serializedFallbackBytes?.let { return it }
        return try {
            ArtworkBitmapDecoder.decode {
                appContext().resources.openRawResource(R.drawable.ic_launcher_box)
            }?.let { ArtworkBitmapDecoder.jpeg(it, square = true) }
                ?.also { serializedFallbackBytes = it }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.tag("AutoArtwork").w(exception, "Could not decode fallback artwork")
            null
        }
    }

    /** AA metadata parity without the legacy converter's eager full-resolution decode. */
    fun baseMediaItem(song: Song): MediaItem {
        val explicit = song.title.hasExplicitPrefix()
        val title = (if (explicit) "\uD83C\uDD74 " else "") + cleanPrefix(song.title)
        val artist = cleanPrefix(song.artistsText.orEmpty())
        val artwork = bestArtworkSource(song.thumbnailUrl)?.toUri()
        val metadata = MediaMetadata.Builder()
            .setTitle(title.takeUnless { it == "null" }.orEmpty())
            .setArtist(artist.takeUnless { it == "null" }.orEmpty())
            .setExtras(bundleOf(
                "durationText" to song.durationText,
                EXPLICIT_BUNDLE_TAG to explicit,
                EXTRAS_KEY_IS_EXPLICIT to explicit,
                "isCoverModified" to (song.thumbnailUrl?.startsWith(MODIFIED_PREFIX, true) == true),
                "isTitleModified" to song.title.startsWith(MODIFIED_PREFIX, true),
                "isArtistModified" to (song.artistsText?.startsWith(MODIFIED_PREFIX, true) == true)
            ).apply {
                song.thumbnailUrl?.let { putString(ORIGINAL_ARTWORK_SOURCE_EXTRA, it) }
            })
            .setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        setArtworkUri(metadata, song, artwork?.toString())
        val built = metadata.build()
        applyMetadataFallbacks(metadata, built)
        return MediaItem.Builder().setMediaId(song.id)
            .setUri(if (song.isLocal) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.id.substringAfter(LOCAL_KEY_PREFIX).toLong()) else song.id.toUri())
            .setCustomCacheKey(song.id).setMediaMetadata(metadata.build()).build()
    }

    /** Embeds the placeholder so the head unit can render tracks without artwork. */
    private suspend fun setSerializedFallback(metadataBuilder: MediaMetadata.Builder, song: Song) {
        metadataBuilder.setArtworkUri(ArtworkContentProvider.uriFor(song.id))
        val fallback = runInterruptible(NzikDispatchers.DATA) { fallbackArtworkBytes() }
        if (fallback != null) {
            metadataBuilder.setArtworkData(fallback, MediaMetadata.PICTURE_TYPE_ILLUSTRATION)
        }
    }

    // Java URI resolution also preserves Windows drive paths in host-side tests.
    private fun resolveArtworkFile(url: String, barePath: String): File =
        try {
            java.nio.file.Paths.get(java.net.URI(url)).toFile()
        } catch (_: Exception) {
            File(barePath)
        }

    private suspend fun loadArtworkBytes(url: String?): ByteArray? = withContext(NzikDispatchers.DATA) {
        if (url.isNullOrBlank()) return@withContext null
        val file = if (url.startsWith("file:") || url.startsWith("/")) {
            resolveArtworkFile(url, url.removePrefix("file://"))
        } else null
        val context = appContext()
        val profile = getActiveProfile(context)
        val revision = ArtworkRevisions.current()
        val key = "$profile|$revision|${file?.absolutePath ?: url}|${file?.lastModified()}|${file?.length()}"
        if (file != null && !file.exists()) return@withContext null
        artworkBytesCache.getOrLoad(key) {
            try {
                val bytes = when {
                    file != null -> runInterruptible(NzikDispatchers.DATA) {
                        encodeOwnedArtwork(square = true) { ArtworkBitmapDecoder.file(file) }
                    }
                    url.startsWith("content://") -> runInterruptible(NzikDispatchers.DATA) {
                        encodeOwnedArtwork {
                            ArtworkBitmapDecoder.decode { context.contentResolver.openInputStream(Uri.parse(url)) }
                        }
                    }
                    else -> {
                        ImageCacheFactory.loadBitmap(
                            url, requestSize = 512, qualityOverride = ImageCacheFactory.NetworkQuality.HIGH,
                        )?.let { bitmap ->
                            runInterruptible(NzikDispatchers.DATA) {
                                encodeOwnedArtwork {
                                    boundedOwnedCopy(bitmap)
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

    private fun boundedOwnedCopy(source: Bitmap): Bitmap? {
        val longest = maxOf(source.width, source.height)
        if (longest <= ArtworkBitmapDecoder.MAX_SIDE) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val scale = ArtworkBitmapDecoder.MAX_SIDE.toDouble() / longest
        // Changed dimensions guarantee a new bitmap; never recycle Coil's borrowed source.
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
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

    // Resize only remote provider URLs; local artwork sources must retain their identity.
    private fun bestArtworkSource(source: String?): String? = source?.let(::cleanPrefix)?.let { clean ->
        if (clean.toUri().scheme in setOf("http", "https")) clean.thumbnail(1200) else clean
    }

    private fun setArtworkUri(metadata: MediaMetadata.Builder, song: Song, source: String?) {
        val candidate = source?.takeUnless { it.isBlank() }?.toUri()
        val file = candidate?.takeIf { it.scheme == "file" || source?.startsWith("/") == true }
            ?.let { resolveArtworkFile(it.toString(), it.path.orEmpty()) }
        metadata.setArtworkUri(when {
            file?.exists() == true -> ArtworkContentProvider.uriForSource(appContext(), song.id, file)
            LocalArtworkRules.needsProviderArtwork(song.id, source, ArtworkContentProvider.AUTHORITY) ->
                ArtworkContentProvider.uriFor(song.id)
            candidate == null || file != null -> drawableUri(appContext(), R.drawable.ic_launcher_box)
            else -> candidate
        })
    }

    /** Uses localized labels for missing title and artist tags. */
    private fun applyMetadataFallbacks(metadataBuilder: MediaMetadata.Builder, metadata: MediaMetadata) {
        val needsTitle = SessionMetadataFallbacks.needsFallback(metadata.title)
        val needsArtist = SessionMetadataFallbacks.needsFallback(metadata.artist)
        if (!needsTitle && !needsArtist) return
        val context = appContext()
        if (needsTitle) metadataBuilder.setTitle(context.getString(R.string.unknown_title))
        if (needsArtist) metadataBuilder.setArtist(context.getString(R.string.unknown_artist))
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
        if (loadArtwork) return blockingArtworkBridge {
            mapArtistWithArtwork(parentId, id, name, thumbnailUrl, subtext, searchPath)
        }
        val iconUri = bestArtworkSource(thumbnailUrl)?.toUri()
        val item = browsableMediaItem(
            id = "$parentId/$id",
            title = name,
            subtitle = subtext,
            iconUri = iconUri,
            mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
            path = searchPath.ifEmpty { parentId }
        )
        return item
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
        if (loadArtwork) return blockingArtworkBridge {
            mapAlbumWithArtwork(parentId, id, title, authorsText, thumbnailUrl, searchPath)
        }
        val iconUri = bestArtworkSource(thumbnailUrl)?.toUri()
        val item = browsableMediaItem(
            id = "$parentId/$id",
            title = title,
            subtitle = authorsText,
            iconUri = iconUri,
            mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
            path = searchPath.ifEmpty { parentId }
        )
        return item
    }

    fun mapSongToMediaItem(song: Song, path: String, loadArtwork: Boolean = false): MediaItem {
        if (loadArtwork) return blockingArtworkBridge { mapSongWithArtwork(song, path) }
        val baseItem = baseMediaItem(song)
        val metadataBuilder = baseItem.mediaMetadata.buildUpon()
        applyMetadataFallbacks(metadataBuilder, baseItem.mediaMetadata)
        setArtworkUri(metadataBuilder, song, bestArtworkSource(song.thumbnailUrl))

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
        if (loadArtwork) return blockingArtworkBridge { mapSongWithArtwork(song, isFromPersistentQueue) }
        val mediaItem = baseMediaItem(song)
        val existingExtras = mediaItem.mediaMetadata.extras ?: Bundle()
        val bundle = Bundle(existingExtras).apply {
            putBoolean(persistentQueueKey, isFromPersistentQueue)
        }

        val metadataBuilder = mediaItem.mediaMetadata
            .buildUpon()
            .setExtras(bundle)
        applyMetadataFallbacks(metadataBuilder, mediaItem.mediaMetadata)
        setArtworkUri(metadataBuilder, song, bestArtworkSource(song.thumbnailUrl))

        return mediaItem.buildUpon().setMediaMetadata(metadataBuilder.build()).build()
    }

    // Non-AA callers expose synchronous APIs. Keep their compatibility bridge here only;
    // automobile callers use the suspend variants below and never block a DATA worker.
    private fun blockingArtworkBridge(load: suspend () -> MediaItem): MediaItem = runBlocking { load() }

    internal suspend fun mapArtistWithArtwork(
        parentId: String, id: String, name: String, thumbnailUrl: String?,
        subtext: String? = null, searchPath: String = "",
    ): MediaItem = attachBrowsableArtwork(
        mapArtistToMediaItem(parentId, id, name, thumbnailUrl, subtext, searchPath), thumbnailUrl,
    )

    internal suspend fun mapAlbumWithArtwork(
        parentId: String, id: String, title: String, authorsText: String?, thumbnailUrl: String?,
        searchPath: String = "",
    ): MediaItem = attachBrowsableArtwork(
        mapAlbumToMediaItem(parentId, id, title, authorsText, thumbnailUrl, searchPath), thumbnailUrl,
    )

    private suspend fun attachBrowsableArtwork(item: MediaItem, thumbnailUrl: String?): MediaItem {
        val bytes = loadArtworkBytes(thumbnailUrl?.let(::cleanPrefix)) ?: return item
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
            .setArtworkUri(null)
            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_ILLUSTRATION).build()).build()
    }

    internal suspend fun mapSongWithArtwork(song: Song, path: String): MediaItem =
        attachSongArtwork(mapSongToMediaItem(song, path), song)

    internal suspend fun mapSongWithArtwork(song: Song, isFromPersistentQueue: Boolean = false): MediaItem {
        val item = mapSongToMediaItem(song, isFromPersistentQueue)
        val source = song.thumbnailUrl?.let(::cleanPrefix)
        // Queue mapping historically embeds only local or file art; browse mapping loads remote art too.
        return if (song.isLocal || source?.startsWith("file:") == true || source?.startsWith("/") == true) {
            attachSongArtwork(item, song)
        } else restoreLoadedArtworkUri(item, song)
    }

    // The artwork-loading overloads historically retained the base item's 1200px remote URI.
    private fun restoreLoadedArtworkUri(item: MediaItem, song: Song): MediaItem {
        val metadata = item.mediaMetadata.buildUpon()
        setArtworkUri(metadata, song, bestArtworkSource(song.thumbnailUrl))
        return item.buildUpon().setMediaMetadata(metadata.build()).build()
    }

    private suspend fun attachSongArtwork(item: MediaItem, song: Song): MediaItem {
        val metadata = restoreLoadedArtworkUri(item, song).mediaMetadata.buildUpon()
        val bytes = loadArtworkBytes(song.thumbnailUrl?.let(::cleanPrefix))
        if (bytes != null) metadata.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_ILLUSTRATION)
        else setSerializedFallback(metadata, song)
        return item.buildUpon().setMediaMetadata(metadata.build()).build()
    }

}
