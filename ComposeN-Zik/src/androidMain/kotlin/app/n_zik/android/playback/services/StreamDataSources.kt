package app.n_zik.android.playback.services

import android.content.ContentResolver
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.LOCAL_KEY_PREFIX
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.ReadOnlyProfileCache
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.DownloadQualityFormat
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherPlayerBridge
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.n_zik.android.playback.exceptions.UnmatchedSongException
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.isConnectionMetered
import app.n_zik.android.utils.okHttpDataSourceFactory
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import com.metrolist.innertubex.extraction.ContentHints
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@UnstableApi
fun PlayerServiceModern.createDataSourceFactory(): DataSource.Factory =
    createPlaybackDataSourceFactory(maintainMetadata = true)

/**
 * Data source factory dedicated to the Rewind deck's hidden background-music players
 * (spec "Deck Rewind — musique de fond"): pure stream resolution only — resolve the stream
 * URL, serve it from the stream LRU cache, fall back to the download cache,
 * parental-control gate included.
 *
 * Unlike [createDataSourceFactory], it launches **no metadata maintenance**: no
 * [upsertSongInfo] (no Innertube `nextPage` call, no artist/album page pre-caching) and no
 * [fetchFormatIfMissing] (no format row upserts) — the background players must only read
 * the music, never feed the library caches (spec: the deck's stats stay untouched).
 */
@UnstableApi
fun PlayerServiceModern.createBackgroundMusicDataSourceFactory(): DataSource.Factory =
    createPlaybackDataSourceFactory(maintainMetadata = false)

@UnstableApi
private fun PlayerServiceModern.createPlaybackDataSourceFactory(maintainMetadata: Boolean): DataSource.Factory {
    val upstreamFactory = appContext().okHttpDataSourceFactory

    val resolvingDataSourceFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")
        val isLocal = dataSpec.uri.scheme == ContentResolver.SCHEME_CONTENT ||
                      dataSpec.uri.scheme == ContentResolver.SCHEME_FILE

        if (isLocal) return@Factory dataSpec

        if (maintainMetadata) {
            // Only persist verified track details for shared listening when local history is enabled.
            val isLtVideo = ListenTogetherPlayerBridge.isListenTogetherVideo(videoId)
            if (ListenTogetherPlayerBridge.shouldRunStreamInfoUpsert(
                isLtVideo,
                DataStoreUtils.getBoolean(appContext(), ListenTogetherClient.PREF_HISTORY, false)
            )) {
                scope.launch(NzikDispatchers.PLAYBACK) { upsertSongInfo(videoId) }
            }
        }

        dataSpec.process(videoId, audioQualityFormat, applicationContext.isConnectionMetered())
            .buildUpon()
            .setKey(videoId)
            .build()
    }

    val lruCacheFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
        .apply { if (cache is ReadOnlyProfileCache) setCacheWriteDataSinkFactory(null) }

    val finalCacheFactory = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(StreamingCacheReaders.factory(cache, lruCacheFactory))
        .setCacheWriteDataSinkFactory(null)
        .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    return ResolvingDataSource.Factory(StreamingCacheReaders.factory(downloadCache, finalCacheFactory)) { dataSpec ->
        val videoId = dataSpec.key ?: dataSpec.uri.toString().substringAfter("watch?v=")
        val parentalControlEnabled = appContext().preferences.getBoolean(parentalControlEnabledKey, false)
        if (parentalControlEnabled) {
            requireAllowedPlayback(videoId)
        }
        if (maintainMetadata) fetchFormatIfMissing(videoId)
        dataSpec.buildUpon().setKey(videoId).build()
    }
}

/**
 * Dedicated download data source factory - separated from streaming resolver.
 * This prevents session changes from affecting downloads (like Metrolist/Cubic).
 */
@UnstableApi
fun MyDownloadHelper.createDownloadDataSourceFactory(): DataSource.Factory {
    val upstreamFactory = appContext().okHttpDataSourceFactory

    val resolvingDataSourceFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")
        val length = if (dataSpec.length >= 0) dataSpec.length else 1

        // Cache-first: if download cache already has this range, skip resolution entirely
        if (downloadCache.isCached(videoId, dataSpec.position, length)) {
            return@Factory dataSpec
        }

        // Check the download URL cache first (populated by previous download resolves).
        // The quality must match: a URL resolved at another download quality is not reusable.
        val cachedStream = songUrlCache.get(videoId, downloadQualityToInnerTubeX(downloadQualityFormat, appContext().isConnectionMetered()))
        if (cachedStream != null) {
            return@Factory dataSpec.withResolvedStream(cachedStream).buildUpon().setKey(videoId).build()
        }

        // Direct resolution - no session changes, no fetchFormatIfMissing, no upsertSongInfo
        runCatching {
            dataSpec.processForDownload(videoId, downloadQualityFormat)
                .buildUpon()
                .setKey(videoId)
                .build()
        }.recoverCatching { firstError ->
            // Retry once after invalidating URL cache (handles expired 403/410/416)
            Timber.tag("StreamResolver").w(firstError, "Download resolve failed for $videoId, invalidating cache and retrying")
            songUrlCache.invalidate(videoId)
            try { downloadCache.removeResource(videoId) } catch (error: Exception) {
                Timber.tag("StreamResolver").w("Unable to evict failed download from cache (%s)", error.javaClass.simpleName)
            }
            dataSpec.processForDownload(videoId, downloadQualityFormat)
                .buildUpon()
                .setKey(videoId)
                .build()
        }.onFailure {
            Timber.tag("StreamResolver").e(it, "Download resolve failed for $videoId after retry")
        }.getOrThrow()
    }

    // DownloadManager owns the writable download cache
    return resolvingDataSourceFactory
}

/**
 * Simplified process function for downloads - NO session change retry.
 * Calls InnerTubeXPlayer.playerResponseForPlayback() directly like Metrolist.
 * This prevents session changes from affecting downloads.
 */
@UnstableApi
private fun DataSpec.processForDownload(
    videoId: String,
    downloadQualityFormat: DownloadQualityFormat
): DataSpec {
    // runBlocking is necessary because ExoPlayer's ResolvingDataSource expects a synchronous return
    // (same hard constraint as DataSpec.process above) - the download DataSpec callback cannot be suspend.
    return try {
        runBlocking(NzikDispatchers.DATA) {
            val parentalControlEnabled = appContext().preferences.getBoolean(parentalControlEnabledKey, false)
            if (parentalControlEnabled) {
                val song = Database.songTable.findByIdDirect(videoId)
                if (song?.title?.startsWith(EXPLICIT_PREFIX, true) == true) {
                    throw ExplicitContentException()
                }
            }

            if (videoId.length != 11 && !videoId.startsWith(LOCAL_KEY_PREFIX)) {
                throw UnmatchedSongException()
            }

            // Direct call to InnerTubeXPlayer - NO session change retry
            val connectivityManager = appContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val audioQuality = downloadQualityToInnerTubeX(downloadQualityFormat, appContext().isConnectionMetered())

            // The download URL cache is only reusable at the same resolved quality
            val urlGeneration = MyDownloadHelper.songUrlCache.generation(videoId)
            val cachedStream = MyDownloadHelper.songUrlCache.get(videoId, audioQuality)
            if (cachedStream != null) {
                Timber.tag(STREAM_RESOLVER_TAG).d("Download URL cache hit for $videoId (quality=$audioQuality)")
                return@runBlocking withResolvedStream(cachedStream)
            }

            Timber.tag(STREAM_RESOLVER_TAG).d("Download resolving for $videoId (quality=$audioQuality)")
            val result = InnerTubeXPlayer.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                contentHints = ContentHints(),
                allowBoundedRange = false,
            )

            result.fold(
                onSuccess = { playbackData ->
                    Timber.tag(STREAM_RESOLVER_TAG).d("Download success for $videoId (client=${playbackData.streamClient})")
                    val contentLength = playbackData.format.contentLength ?: 1_000_000L
                    val streamUrl = "${playbackData.streamUrl}&range=0-$contentLength"

                    // Upsert song/artist/album info in background (fire-and-forget, like Cubic Music).
                    // The tracked quality comes from the [downloadQualityFormat] parameter (a
//
                    // snapshot of the setting at resolution time), not from a later re-read:
                    // upsertSongInfo does network work with retries (up to ~18 s), so reading the
//
                    // setting inside the coroutine could record a value the download did not use.
                    val trackedDownloadQuality = downloadQualityFormat.name
                    scope.launch(NzikDispatchers.PLAYBACK) {
                        upsertSongInfo(videoId)
                        upsertSongFormat(
                            videoId,
                            playbackData.format,
                            playbackData.audioConfig?.perceptualLoudnessDb,
                            playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                            playbackData.audioConfig?.loudnessDb,
                            downloadQuality = trackedDownloadQuality
                        )
                        // Apply the selected quality after the queued Format write.
                        Database.awaitWriteBatch {
                            formatTable.updateDownloadQuality(videoId, trackedDownloadQuality)
                        }
                    }

                    // Cache for future download use - in the download cache, never the player one
                    MyDownloadHelper.songUrlCache.put(
                        mediaId = videoId,
                        url = streamUrl,
                        requestHeaders = playbackData.streamHeaders,
                        clientName = playbackData.streamClient,
                        expiresInSeconds = playbackData.streamExpiresInSeconds,
                        requireBoundedRange = playbackData.requireBoundedRange,
                        rangeChunkSizeBytes = playbackData.rangeChunkSizeBytes,
                        useRangeChunks = playbackData.useRangeChunks,
                        quality = audioQuality,
                        expectedGeneration = urlGeneration,
                    )

                    withResolvedStream(
                        CachedStreamUrl(
                            url = streamUrl,
                            requestHeaders = playbackData.streamHeaders,
                            clientName = playbackData.streamClient,
                            requireBoundedRange = playbackData.requireBoundedRange,
                            rangeChunkSizeBytes = playbackData.rangeChunkSizeBytes,
                            useRangeChunks = playbackData.useRangeChunks,
                        )
                    )
                },
                onFailure = { error ->
                    Timber.tag(STREAM_RESOLVER_TAG).e(error, "Download failed for $videoId")
                    throw error
                }
            )
        }
    } catch (e: CancellationException) {
        if (e.cause is InterruptedException) {
            throw IOException("Download stream resolution interrupted for $videoId", e)
        }
        throw e
    }
}
