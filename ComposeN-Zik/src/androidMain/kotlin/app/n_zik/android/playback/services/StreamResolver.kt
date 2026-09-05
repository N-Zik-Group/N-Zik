package app.n_zik.android.playback.services

import app.n_zik.android.core.database.Database

import app.n_zik.android.playback.utils.PlaybackDispatchers

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import app.n_zik.android.R

import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.network.client.Store

import app.n_zik.android.core.security.potoken.PoTokenGenerator
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.PlayerResponse
import it.fast4x.innertube.requests.nextPage
import it.fast4x.innertube.requests.artistPage
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.models.Format
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.exceptions.UnplayableException
import app.n_zik.android.playback.exceptions.UnmatchedSongException
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.utils.isConnectionMetered
import app.it.fast4x.rimusic.utils.okHttpDataSourceFactory
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.parseArtists

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.IOException
import org.jetbrains.annotations.NonBlocking
import java.util.Collections
import java.net.UnknownHostException
import io.ktor.client.call.body
import timber.log.Timber
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.LOCAL_KEY_PREFIX
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.core.security.potoken.PoTokenResult
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import java.util.concurrent.ConcurrentHashMap
import app.kreate.android.me.knighthat.utils.PropUtils
import it.fast4x.innertube.YtMusic
import android.database.sqlite.SQLiteConstraintException

import okhttp3.Request
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import com.metrolist.innertubex.extraction.ContentHints

private const val TAG = "StreamResolver"

private val PARTIAL_CONTENT_RANGE = Regex("""bytes\s+0-0/(\d+)""", RegexOption.IGNORE_CASE)
private val UNSATISFIED_CONTENT_RANGE = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)

// Structured scope for background tasks (caching, metadata upsert)
private val scope = CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER + Job())

// PoTokenGenerator for metadata-only requests (playerResponseForMetadata).
// Stream playback uses InnerTubeXPlayer's own PoTokenGenerator instance.
private val poTokenGenerator = PoTokenGenerator()

// Track videoIds already fetched by fetchFormatIfMissing (avoid redundant API calls)
private val fetchedFormatIds = Collections.synchronizedSet(mutableSetOf<String>())
private val webRemixFailedIds = Collections.synchronizedSet(mutableSetOf<String>())

// Per-client failure tracking: clientName → map of videoId → failure timestamp
private val clientFailedIds = ConcurrentHashMap<String, MutableMap<String, Long>>()
private const val CLIENT_FAILURE_TTL_MS = 5 * 60 * 1000L // 5 minutes

// Track ongoing background fetches to prevent concurrent duplicate API calls
private val fetchingSongInfos = Collections.synchronizedSet(mutableSetOf<String>())
private val fetchingArtists = Collections.synchronizedSet(mutableSetOf<String>())
private val fetchingAlbums = Collections.synchronizedSet(mutableSetOf<String>())

// Warmup video ID for PoToken pre-generation (first YouTube video)
private const val POTOKEN_WARMUP_VIDEO_ID = "jNQXAC9IVRw"

// Delay before retrying stream resolution after an InnerTube session change
private const val SESSION_CHANGE_RETRY_DELAY_MS = 200L

/**
 * Pre-warm the PoToken BotGuard generator to avoid cold-start latency on first playback.
 * Failure is swallowed; playback falls back to lazy init unchanged.
 */
suspend fun prewarmPoToken() {
    val sessionId = Store.getIosVisitorData() ?: return
    runCatching {
        poTokenGenerator.getWebClientPoToken(POTOKEN_WARMUP_VIDEO_ID, sessionId)
    }.onFailure { Timber.tag(TAG).w(it, "PoToken prewarm skipped: ${it.message}") }
}

/**
 * Mark a videoId as having failed WEB_REMIX validation (403/expired).
 * Next resolve will skip HEAD validation for WEB_REMIX on this videoId.
 */
fun markWebRemixFailed(videoId: String) {
    webRemixFailedIds.add(videoId)
    Timber.tag(TAG).d("Marked WEB_REMIX failed for $videoId")
}

/**
 * Clear all WEB_REMIX failure markers.
 * Called when cipher config is refreshed so WEB_REMIX gets another chance.
 */
fun clearWebRemixFailures() {
    webRemixFailedIds.clear()
    Timber.tag(TAG).d("Cleared WEB_REMIX failures")
}

/**
 * Mark a videoId as having failed with a specific client.
 * Next resolve will skip that client for this videoId for CLIENT_FAILURE_TTL_MS.
 */
fun markClientFailed(clientName: String, videoId: String) {
    clientFailedIds.getOrPut(clientName) { mutableMapOf() }[videoId] = System.currentTimeMillis()
    Timber.tag(TAG).d("Marked $clientName failed for $videoId")
}

/**
 * Check if a client failure has expired (older than CLIENT_FAILURE_TTL_MS).
 */
private fun isClientFailureExpired(clientName: String, videoId: String): Boolean {
    val failureMap = clientFailedIds[clientName] ?: return true
    val failureTime = failureMap[videoId] ?: return true
    return System.currentTimeMillis() - failureTime > CLIENT_FAILURE_TTL_MS
}

/**
 * Clean up expired failure entries for a client.
 */
private fun cleanupExpiredFailures(clientName: String) {
    val failureMap = clientFailedIds[clientName] ?: return
    val now = System.currentTimeMillis()
    failureMap.entries.removeIf { (_, timestamp) -> now - timestamp > CLIENT_FAILURE_TTL_MS }
    if (failureMap.isEmpty()) {
        clientFailedIds.remove(clientName)
    }
}

/**
 * Clear all failure markers for a specific client.
 */
fun clearClientFailures(clientName: String) {
    clientFailedIds.remove(clientName)
    Timber.tag(TAG).d("Cleared failures for $clientName")
}

/**
 * Store id of song just added to the database to reduce load to Room.
 */
@set:Synchronized
private var justInserted: String = ""

suspend fun upsertSongInfo(videoId: String) {
    if (videoId == justInserted) return
    if (!fetchingSongInfos.add(videoId)) {
        Timber.tag(TAG).d("upsertSongInfo already in progress for $videoId, skipping duplicate.")
        return
    }

    try {
        Innertube.nextPage(videoId = videoId)?.fold(
            onSuccess = { nextPage ->
            val songItem = nextPage.itemsPage?.items?.firstOrNull() ?: return@fold
            Database.upsert(songItem)
            yield()

            // Read IDs from DB (retry up to 3 times if empty)
            var artistIdsFromDb = emptyList<Artist>()
            var albumFromDb: Album? = null
            for (attempt in 1..3) {
                kotlinx.coroutines.delay(3000L * attempt)
                artistIdsFromDb = Database.songArtistMapTable.findArtistsOf(videoId).firstOrNull().orEmpty()
                albumFromDb = Database.songAlbumMapTable.findAlbumOf(videoId).firstOrNull()
                if (artistIdsFromDb.isNotEmpty() || albumFromDb != null) break
                Timber.tag(TAG).d("[IDs] attempt $attempt/3: no artist/album IDs for $videoId, retrying...")
            }
            if (artistIdsFromDb.isEmpty() && albumFromDb == null) {
                Timber.tag(TAG).w("[IDs] No artist/album IDs for $videoId after 3 retries, skipping.")
            }

            // Artist cache — read IDs from DB
            artistIdsFromDb?.forEach { artist ->
                val artistId = artist.id
                if (!artistId.isNullOrBlank()) {
                    val dbArtist = Database.artistTable.findByIdDirect(artistId)
                    val currentTime = System.currentTimeMillis()
                    val lastFetchTime = dbArtist?.lastFetch
                    val isArtistRecentlyFetched = lastFetchTime?.let { currentTime - it < 2592000000L } == true

                    if (isArtistRecentlyFetched) {
                        val msAgo = currentTime - (lastFetchTime ?: currentTime)
                        val daysAgo = msAgo / 86400000L
                        Timber.tag(TAG).d("[Artist Cache] $artistId was fetched $daysAgo days ago ($msAgo ms), skipping.")
                    } else if (fetchingArtists.add(artistId)) {
                        Timber.tag(TAG).d("[Artist Cache] $artistId outdated, fetching in background.")
                        scope.launch(PlaybackDispatchers.STREAM_RESOLVER) {
                            try {
                                val artistPage = Innertube.artistPage(browseId = artistId)?.getOrNull()
                                if (artistPage != null) {
                                    Database.asyncTransaction {
                                        val existing = Database.artistTable.findByIdDirect(artistId)
                                        if (existing != null) {
                                            Database.artistTable.upsert(existing.copy(
                                                name = PropUtils.retainIfModified(existing.name, artistPage.name) ?: artistPage.name,
                                                thumbnailUrl = PropUtils.retainIfModified(existing.thumbnailUrl, artistPage.thumbnail?.url),
                                                lastFetch = System.currentTimeMillis()
                                            ))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.tag(TAG).w(e, "Failed to fetch artist $artistId")
                            } finally {
                                fetchingArtists.remove(artistId)
                            }
                        }
                    }
                }
            }

            // Album cache — use browseId from DB (navigation)
            val albumId = albumFromDb?.id

            if (!albumId.isNullOrBlank()) {
                val dbAlbum = Database.albumTable.findByIdDirect(albumId)
                val currentTime = System.currentTimeMillis()
                val lastFetchTime = dbAlbum?.lastFetch
                val isRecentlyFetched = lastFetchTime?.let { currentTime - it < 2592000000L } == true

                if (isRecentlyFetched) {
                    val msAgo = currentTime - (lastFetchTime ?: currentTime)
                    val daysAgo = msAgo / 86400000L
                    Timber.tag(TAG).d("[Album Cache] $albumId fetched $daysAgo days ago ($msAgo ms), skipping.")
                } else if (fetchingAlbums.add(albumId)) {
                    Timber.tag(TAG).d("[Album Cache] $albumId outdated, fetching songs.")
                    scope.launch(PlaybackDispatchers.STREAM_RESOLVER) {
                        try {
                            val savedCount = fetchAndSaveAlbumSongs(albumId)
                            if (savedCount < 2) {
                                Timber.tag(TAG).w("[Album Cache] $albumId: only $savedCount songs saved")
                            }
                        } finally {
                            fetchingAlbums.remove(albumId)
                        }
                    }
                }
            } else {
                Timber.tag(TAG).d("[Album Cache] No album found for $videoId, skipping album fetch")
            }
        },
        onFailure = {
            when (it) {
                is UnknownHostException -> justInserted = videoId
                else -> Timber.tag(TAG).w(it, "Failed to upsert song info for $videoId")
            }
        }
    )
    } finally {
        fetchingSongInfos.remove(videoId)
    }
}

/**
 * Fetches all songs from an album and saves them to the database.
 * Called in parallel when streaming a song to fill the album for shuffle.
 */
private suspend fun fetchAndSaveAlbumSongs(albumId: String): Int {
    try {
        Timber.tag(TAG).d("[Album Cache] Fetching album page from network for $albumId")
        val onlineAlbum = YtMusic.getAlbum(albumId.removePrefix(MODIFIED_PREFIX), true).getOrNull()
        if (onlineAlbum == null) {
            Timber.tag(TAG).w("Album page is null for $albumId")
            return 0
        }
        val albumPage = onlineAlbum.album
        val songs = onlineAlbum.songs
        if (songs.isEmpty()) {
            Timber.tag(TAG).d("No songs found in album $albumId")
            return 0
        }

        Timber.tag(TAG).d("[Album Cache] Saving ${songs.size} songs from album $albumId to database")
        val songAlbumMaps = mutableListOf<SongAlbumMap>()
        songs.forEachIndexed { index, song ->
            Database.upsert(song)
            val videoId = song.info?.endpoint?.videoId?.removePrefix(MODIFIED_PREFIX)
            if (videoId != null) {
                songAlbumMaps.add(SongAlbumMap(songId = videoId, albumId = albumId, position = index))
            }
            Timber.tag(TAG).d("Saved song: ${song.info?.name} ($videoId) to album $albumId at pos $index")
        }
        
        // Mark album as completely fetched so we don't spam the API for 30 days
        // Also update its metadata while protecting manual modifications
        Database.asyncTransaction {
            try {
                Database.albumTable.findByIdDirect(albumId)?.let { existingAlbum ->
                    Database.albumTable.upsert(existingAlbum.copy(
                        title = PropUtils.retainIfModified(existingAlbum.title, albumPage.title),
                        thumbnailUrl = PropUtils.retainIfModified(existingAlbum.thumbnailUrl, albumPage.thumbnail?.url),
                        authorsText = PropUtils.retainIfModified(existingAlbum.authorsText, albumPage.authors?.parseArtists()?.joinToString(", ")?.takeIf { it.isNotBlank() }),
                        year = PropUtils.retainIfModified(existingAlbum.year, albumPage.year),
                        shareUrl = PropUtils.retainIfModified(existingAlbum.shareUrl, onlineAlbum.url),
                        lastFetch = System.currentTimeMillis()
                    ))
                }
                Database.songAlbumMapTable.clear(albumId)
                Database.songAlbumMapTable.upsert(songAlbumMaps)
            } catch (e: SQLiteConstraintException) {
                Timber.tag(TAG).w("Foreign key constraint failed for album $albumId. Retrying in 5s...")
                scope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(5000)
                    try {
                        Database.asyncTransaction {
                            Database.albumTable.findByIdDirect(albumId)?.let { existingAlbum ->
                                Database.albumTable.upsert(existingAlbum.copy(
                                    title = PropUtils.retainIfModified(existingAlbum.title, albumPage.title),
                                    thumbnailUrl = PropUtils.retainIfModified(existingAlbum.thumbnailUrl, albumPage.thumbnail?.url),
                                    authorsText = PropUtils.retainIfModified(existingAlbum.authorsText, albumPage.authors?.parseArtists()?.joinToString(", ")?.takeIf { it.isNotBlank() }),
                                    year = PropUtils.retainIfModified(existingAlbum.year, albumPage.year),
                                    shareUrl = PropUtils.retainIfModified(existingAlbum.shareUrl, onlineAlbum.url),
                                    lastFetch = System.currentTimeMillis()
                                ))
                            }
                            Database.songAlbumMapTable.clear(albumId)
                            Database.songAlbumMapTable.upsert(songAlbumMaps)
                        }
                    } catch (e2: Exception) {
                        Timber.tag(TAG).e("Failed to save album cache even after delay: ${e2.message}")
                    }
                }
            }
        }
        Timber.tag(TAG).d("[Album Cache] Finished saving ${songs.size} songs from album $albumId with correct ordering")
        return songs.size
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "[Album Cache] Failed to fetch and save album songs for $albumId")
        return 0
    }
}

/**
 * Safely upserts a [Format] row, catching FOREIGN KEY race conditions
 * (song not yet committed when format arrives) and retrying after 5 s.
 * Mirrors [saveLyricsSafe] in LyricsFetcher.
 */
private fun saveFormatSafe(format: Format) {
    Database.asyncTransaction {
        try {
            formatTable.upsert(format)
        } catch (e: SQLiteConstraintException) {
            Timber.tag(TAG).w("Foreign key constraint failed for songId ${format.songId}. Retrying in 5 s...")
            scope.launch(PlaybackDispatchers.STREAM_RESOLVER) {
                delay(5000)
                try {
                    Database.asyncTransaction {
                        formatTable.upsert(format)
                    }
                } catch (e2: Exception) {
                    Timber.tag(TAG).e("Failed to save format even after delay for ${format.songId}: ${e2.message}")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error saving format for ${format.songId}: ${e.message}")
        }
    }
}

/**
 * Fetches format metadata for a videoId if missing from DB.
 * Called fire-and-forget for every playback, including downloaded songs
 * where the stream resolver is bypassed by downloadCache.
 *
 * Uses [playerResponseForMetadata] to get format info (bitrate, codec, etc.)
 * and [playerConfig] for perceptual loudness. Then does a HEAD request
 * on the stream URL to resolve content-length if not provided by the API.
 */
private fun fetchFormatIfMissing(videoId: String) {
    if (videoId in fetchedFormatIds) return
    if (videoId.startsWith(LOCAL_KEY_PREFIX)) return
    if (videoId.length != 11) return
    scope.launch(PlaybackDispatchers.STREAM_RESOLVER) {
        try {
            val existing = Database.formatTable.findBySongIdDirect(videoId)
            if (existing != null) {
                Timber.tag(TAG).d("fetchFormatIfMissing: re-fetch $videoId (existing in DB)")
            } else {
                Timber.tag(TAG).d("fetchFormatIfMissing: new $videoId (no format in DB)")
            }

            val response = playerResponseForMetadata(videoId).getOrNull() ?: return@launch
            val api = response.streamingData?.adaptiveFormats
                ?.filter { it.isAudio && (it.url != null || it.signatureCipher != null) }
                ?.maxByOrNull { scoreCodec(it.mimeType) * 10000 + (it.bitrate ?: 0) }
                ?: return@launch
            val apiPerceptual = response.playerConfig?.audioConfig?.perceptualLoudnessDb
            val apiLoudness = response.playerConfig?.audioConfig?.loudnessDb
            val codecs = api.mimeType.substringAfter("codecs=", "").removeSurrounding("\"").takeIf { it.isNotEmpty() }

            // Try to get contentLength: DB > API > HEAD on resolved stream URL
            val finalSize = existing?.contentLength?.takeIf { it > 0 }
                ?: api.contentLength?.takeIf { it > 0 }
                ?: runCatching {
                    val streamUri = resolveFormatUrl(videoId, api, "WEB_REMIX") ?: return@runCatching null
                    val headRequest = Request.Builder()
                        .header("Range", "bytes=0-0")
                        .url(streamUri.toString())
                        .build()
                    NetworkClientFactory.getCachelessClient().newCall(headRequest).execute().use { resp ->
                        val statusCode = resp.code
                        val contentRange = resp.header("Content-Range")
                        val contentLength = resp.header("Content-Length")
                        when (statusCode) {
                            206 -> contentRange?.let { PARTIAL_CONTENT_RANGE.find(it) }?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0L }
                            416 -> contentRange?.let { UNSATISFIED_CONTENT_RANGE.find(it) }?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0L }
                            200 -> contentLength?.toLongOrNull()?.takeIf { it > 0L }
                            else -> null
                        }
                    }
                }.onFailure { Timber.tag(TAG).w(it, "HEAD content-length failed for $videoId") }.getOrNull()
            val finalLoudness = existing?.loudnessDb ?: api.loudnessDb?.toFloat() ?: apiLoudness
            val finalPerceptual = existing?.perceptualLoudnessDb ?: apiPerceptual
            val finalBitrate = existing?.bitrate?.takeIf { it > 0 } ?: api.bitrate.toLong()
            val finalCodecs = existing?.codecs?.takeIf { it.isNotEmpty() } ?: codecs
            val finalSampleRate = existing?.sampleRate ?: api.audioSampleRate
            val finalChannels = existing?.audioChannels ?: api.audioChannels

            val formatToSave = Format(
                songId = videoId,
                itag = existing?.itag ?: api.itag,
                mimeType = existing?.mimeType ?: api.mimeType,
                bitrate = finalBitrate,
                contentLength = finalSize,
                lastModified = existing?.lastModified ?: api.lastModified,
                loudnessDb = finalLoudness,
                codecs = finalCodecs,
                sampleRate = finalSampleRate,
                perceptualLoudnessDb = finalPerceptual,
                audioChannels = finalChannels,
                playbackUrl = existing?.playbackUrl
            )
            saveFormatSafe(formatToSave)
            fetchedFormatIds.add(videoId)
            Timber.tag(TAG).d("fetchFormatIfMissing: videoId=$videoId" +
                " existing=${existing != null}" +
                " apiSize=${api.contentLength}" +
                " finalSize=$finalSize" +
                " loudness=$finalLoudness" +
                " perceptual=$finalPerceptual" +
                " bitrate=$finalBitrate" +
                " sampleRate=$finalSampleRate" +
                " channels=$finalChannels" +
                " codecs=$finalCodecs")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to fetch missing format for $videoId")
        }
    }
}

@NonBlocking
private fun upsertSongFormat(
    videoId: String,
    format: PlayerResponse.StreamingData.Format,
    perceptualLoudnessDb: Float? = null,
    playbackUrl: String? = null,
    audioConfigLoudnessDb: Float? = null
) {
    if (videoId == justInserted) return
    runCatching {
        // Extract codecs from mimeType (e.g. "audio/webm; codecs=opus" -> "opus")
        val codecs = format.mimeType
            .substringAfter("codecs=", "")
            .removeSurrounding("\"")
            .takeIf { it.isNotEmpty() }

        // Prefer audioConfig.loudnessDb (player-level, more reliable) over format-level loudnessDb
        val loudnessDb = audioConfigLoudnessDb ?: format.loudnessDb?.toFloat()

        val formatToSave = Format(
            songId = videoId,
            itag = format.itag,
            mimeType = format.mimeType,
            bitrate = format.bitrate.toLong(),
            contentLength = format.contentLength,
            lastModified = format.lastModified,
            loudnessDb = loudnessDb,
            codecs = codecs,
            sampleRate = format.audioSampleRate,
            perceptualLoudnessDb = perceptualLoudnessDb,
            audioChannels = format.audioChannels,
            playbackUrl = playbackUrl
        )
        // Ensure the Song row exists for the Format FK constraint.
        // Only insert a placeholder if the song is NOT already in the DB
        // (e.g., already seeded by onMediaItemTransition with full metadata).
        // This avoids creating a blank row that would propagate empty data
        // to the UI via reactive Flows.
        val songExists = Database.songTable.countById(videoId) > 0
        if (!songExists) {
            Database.asyncTransaction {
                try {
                    songTable.insertIgnore(Song.makePlaceholder(videoId))
                } catch (e: SQLiteConstraintException) {
                    Timber.tag(TAG).w("Foreign key constraint failed for song placeholder $videoId. Retrying in 5s...")
                    scope.launch(Dispatchers.IO) {
                        kotlinx.coroutines.delay(5000)
                        try {
                            Database.asyncTransaction {
                                songTable.insertIgnore(Song.makePlaceholder(videoId))
                            }
                        } catch (e2: Exception) {
                            Timber.tag(TAG).e("Failed to save song placeholder even after delay: ${e2.message}")
                        }
                    }
                }
            }
        }
        saveFormatSafe(formatToSave)
        justInserted = videoId
    }
}

private fun scoreCodec(mimeType: String): Int = when {
    mimeType.contains("opus", ignoreCase = true) -> 2
    mimeType.contains("mp4a", ignoreCase = true) -> 1
    else -> 0
}

/**
 * Resolves the stream URL for a given format.
 *
 * - For ANDROID_VR / ANDROID / IOS: uses the direct URL from the format.
 * - For WEB_REMIX / web clients: returns direct URL if available, null otherwise.
 *   Note: Legacy cipher deobfuscation removed — InnerTubeX handles web clients now.
 */
private suspend fun resolveFormatUrl(
    videoId: String,
    format: PlayerResponse.StreamingData.Format,
    clientName: String
): Uri? {
    // Direct URL clients (no signature cipher, no n-transform)
    val webClients = setOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5", "TVHTML5_SIMPLY", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
    if (clientName !in webClients) {
        val directUrl = format.url ?: return null
        Timber.tag(TAG).d("Direct URL for $clientName: ${directUrl.take(80)}...")
        return directUrl.toUri()
    }

    // Legacy cipher pipeline removed — InnerTubeX is now the primary path
    // For web clients with signatureCipher, we cannot deobfuscate without CipherDeobfuscator
    val formatUrl = format.url
    val resolvedUri = if (formatUrl != null) {
        Uri.parse(formatUrl)
    } else {
        Timber.tag(TAG).w("No direct URL for web client $clientName (signatureCipher requires legacy cipher)")
        null
    }

    return resolvedUri
}

/**
 * Returns true if [e] was thrown by InnerTubeX because the session generation changed
 * (i.e. [com.metrolist.innertubex.InnerTube.cancelStaleSessionRequests] cancelled the
 * in-flight request). Such cancellations are safe to retry once with the new session;
 * any other cancellation (caller scope cancelled, timeout) must propagate as-is.
 */
internal fun isInnerTubeSessionChangeCancellation(e: CancellationException): Boolean =
    e.message?.contains("InnerTube session changed", ignoreCase = true) == true

/**
 * Try to resolve stream via InnerTubeX (new pipeline).
 * Returns null if InnerTubeX fails, allowing fallback to legacy cipher pipeline.
 *
 * Retries once on [CancellationException] caused by InnerTubeX session changes
 * (e.g. when PoTokenWebView creation triggers fetchFreshVisitorData, which
 * increments the session generation and cancels in-flight requests).
 */
@UnstableApi
private suspend fun resolveStreamUriViaInnerTubeX(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
    contentHints: ContentHints = ContentHints(),
    allowBoundedRange: Boolean = true
): CachedStreamUrl? {
    val expectedGeneration = streamUrlCache.generation(videoId)

    // Retry loop: catches CancellationException from session changes and retries once.
    // InnerTubeX's fetchFreshVisitorData() can cancel in-flight player requests when
    // it publishes a new session — this is expected and recoverable.
    var lastCancellation: CancellationException? = null
    for (attempt in 0..1) {
        try {
            val connectivityManager = appContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val audioQuality = when (audioQualityFormat) {
                AudioQualityFormat.High -> InnerTubeXAudioQuality.HIGH
                AudioQualityFormat.Low -> InnerTubeXAudioQuality.LOW
                else -> if (connectionMetered) InnerTubeXAudioQuality.LOW else InnerTubeXAudioQuality.AUTO
            }

            if (attempt > 0) {
                Timber.tag(TAG).d("Retrying InnerTubeX for $videoId after session change (attempt ${attempt + 1})")
            } else {
                Timber.tag(TAG).d("Trying InnerTubeX for $videoId (quality=$audioQuality)")
            }
            val result = InnerTubeXPlayer.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                contentHints = contentHints,
                allowBoundedRange = allowBoundedRange,
            )

            return result.fold(
                onSuccess = { playbackData ->
                    Timber.tag(TAG).d("InnerTubeX success for $videoId (client=${playbackData.streamClient})")

                    // Cache PlaybackData for metadata access
                    playbackDataCache[videoId] = PlaybackData(
                        streamUrl = playbackData.streamUrl,
                        format = playbackData.format,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        videoDetails = playbackData.videoDetails,
                        playbackTracking = playbackData.playbackTracking,
                        streamExpiresInSeconds = playbackData.streamExpiresInSeconds.toLong(),
                        streamClient = playbackData.streamClient,
                        clientPlaybackNonce = playbackData.clientPlaybackNonce,
                    )
                    PlaybackDataStore.saveStreamClient(appContext(), videoId, playbackData.streamClient)

                    // Upsert song format in background
                    scope.launch(PlaybackDispatchers.STREAM_RESOLVER) {
                        upsertSongFormat(
                            videoId,
                            playbackData.format,
                            playbackData.audioConfig?.perceptualLoudnessDb,
                            playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                            playbackData.audioConfig?.loudnessDb
                        )
                    }

                    val contentLength = playbackData.format.contentLength ?: 1_000_000L
                    val streamUrl = "${playbackData.streamUrl}&range=0-$contentLength"

                    // Store in StreamUrlCache with headers from InnerTubeX
                    streamUrlCache.put(
                        mediaId = videoId,
                        url = streamUrl,
                        requestHeaders = playbackData.streamHeaders,
                        clientName = playbackData.streamClient,
                        expiresInSeconds = playbackData.streamExpiresInSeconds,
                        requireBoundedRange = playbackData.requireBoundedRange,
                        rangeChunkSizeBytes = playbackData.rangeChunkSizeBytes,
                        useRangeChunks = playbackData.useRangeChunks,
                        expectedGeneration = expectedGeneration,
                    )

                    CachedStreamUrl(
                        url = streamUrl,
                        requestHeaders = playbackData.streamHeaders,
                        clientName = playbackData.streamClient,
                        requireBoundedRange = playbackData.requireBoundedRange,
                        rangeChunkSizeBytes = playbackData.rangeChunkSizeBytes,
                        useRangeChunks = playbackData.useRangeChunks,
                    )
                },
                onFailure = { error ->
                    Timber.tag(TAG).w(error, "InnerTubeX failed for $videoId")
                    val playbackError = when {
                        error is IOException -> PlaybackException(
                            "Stream resolution IO error for $videoId: ${error.message}",
                            error,
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                        )
                        error.message?.contains("403") == true || error.message?.contains("forbidden", ignoreCase = true) == true -> PlaybackException(
                            "Stream forbidden (403) for $videoId: ${error.message}",
                            error,
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                        )
                        error.message?.contains("410") == true || error.message?.contains("expired", ignoreCase = true) == true -> PlaybackException(
                            "Stream expired (410) for $videoId: ${error.message}",
                            error,
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                        )
                        error.message?.contains("unplayable", ignoreCase = true) == true -> PlaybackException(
                            "Stream unplayable for $videoId: ${error.message}",
                            error,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )
                        else -> PlaybackException(
                            "Stream resolution failed for $videoId: ${error.message}",
                            error,
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                        )
                    }
                    throw playbackError
                }
            )
        } catch (e: CancellationException) {
            // InnerTubeX cancels in-flight session-bound requests whenever a new session is
            // published (e.g. fetchFreshVisitorData during first-time PoToken generation).
            // Retry once with the stable session instead of letting ExoPlayer re-drive the
            // whole load from scratch. The message is specific to
            // InnerTube.cancelStaleSessionRequests; any other cancellation (caller scope
            // cancelled, ExoPlayer interrupt) propagates as-is. If our own job is being
            // cancelled, delay() below throws immediately and aborts the retry.
            if (isInnerTubeSessionChangeCancellation(e) && attempt == 0) {
                lastCancellation = e
                Timber.tag(TAG).d("Stream resolution cancelled by InnerTube session change for $videoId, retrying once")
                // Brief pause so concurrent session publishers (visitor-data fetch, PoToken
                // generation) settle before the retry reads the new session.
                delay(SESSION_CHANGE_RETRY_DELAY_MS)
                continue
            }
            throw e
        } catch (e: PlaybackException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "InnerTubeX exception for $videoId")
            throw PlaybackException(
                "Unexpected stream resolution error for $videoId: ${e.message}",
                e,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            )
        }
    }
    throw lastCancellation ?: CancellationException("InnerTubeX stream resolution cancelled")
}

/**
 * Core stream resolution logic (single attempt).
 *
 * InnerTubeX is the only stream resolution path.
 * Returns a [CachedStreamUrl] containing the resolved URL and request headers.
 */
@UnstableApi
private suspend fun resolveStreamUriInternal(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
    allowBoundedRange: Boolean = true
): CachedStreamUrl {
    // InnerTubeX is the sole stream resolution path
    val innertubexResult = resolveStreamUriViaInnerTubeX(videoId, audioQualityFormat, connectionMetered, allowBoundedRange = allowBoundedRange)
    if (innertubexResult != null) {
        return innertubexResult
    }

    throw UnplayableException("InnerTubeX failed to resolve stream for $videoId")
}
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Cache + DataSpec integration
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Cache of resolved stream URLs by videoId.
 * Exposed internally so [PlayerServiceModern.onPlayerError] can invalidate stale entries.
 */
internal val streamUrlCache = StreamUrlCache()

/**
 * Cache of PlaybackData by videoId.
 * Stores enriched metadata (audioConfig, videoDetails, playbackTracking) from stream resolution.
 */
internal val playbackDataCache = ConcurrentHashMap<String, PlaybackData>()

/**
 * Clear all stream caches when stream client settings change.
 * This forces re-resolution with the new client on next playback.
 * WARNING: This will cause current playback to re-buffer.
 */
fun clearStreamCaches() {
    streamUrlCache.clear()
    playbackDataCache.clear()
    webRemixFailedIds.clear()
    fetchedFormatIds.clear()
    MyDownloadHelper.songUrlCache.clear()
    PlaybackDataStore.clearStreamClients(appContext())
    Timber.tag("StreamResolver").d("All stream caches cleared (format + playback data + webRemix failures + URL cache)")
}

/**
 * Player response intended for metadata / playback-tracking retrieval.
 * Stream URLs of this response might not work — don't use them for playback.
 * Used for getting videoDetails, playbackTracking, audioConfig without resolving streams.
 */
suspend fun playerResponseForMetadata(
    videoId: String,
    playlistId: String? = null,
): Result<PlayerResponse> {
    Timber.tag(TAG).d("Fetching metadata player response for videoId: $videoId")

    val signatureTimestamp: Int? = null

    val sessionId = Store.getIosVisitorData() ?: Innertube.DEFAULT_VISITOR_DATA
    val poToken = runCatching {
        poTokenGenerator.getWebClientPoToken(videoId, sessionId)
    }.getOrNull()

    return Innertube.player(
        videoId = videoId,
        playlistId = playlistId,
        signatureTimestamp = signatureTimestamp,
        poToken = poToken?.playerRequestPoToken,
    ).let { httpResponse ->
        runCatching {
            httpResponse.body<PlayerResponse>()
        }.onSuccess {
            Timber.tag(TAG).d("Successfully fetched metadata player response")
        }.onFailure {
            Timber.tag(TAG).e(it, "Failed to fetch metadata player response")
        }
    }
}

@UnstableApi
fun DataSpec.process(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean,
    allowBoundedRange: Boolean = true
): DataSpec {
    // runBlocking is necessary because ExoPlayer's ResolvingDataSource expects a synchronous return.
    // We catch CancellationException (caused by thread interruption during media item transitions)
    // and re-throw as IOException so ExoPlayer treats it as a recoverable error.
    return try {
        runBlocking(Dispatchers.IO) {
            val isLoggedIn = !Innertube.cookie.isNullOrBlank() && Innertube.cookie?.contains("SAPISID") == true
            Timber.tag(TAG).d("Resolving stream for videoId=$videoId, isLoggedIn=$isLoggedIn")

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

            val cachedStream = streamUrlCache[videoId]

            if (cachedStream != null) {
                Timber.tag(TAG).d("StreamUrlCache hit for $videoId (client=${cachedStream.clientName})")
                return@runBlocking withResolvedStream(cachedStream)
            }

            Timber.tag(TAG).d("StreamUrlCache miss for $videoId, resolving...")
            val resolvedStream = resolveStreamUriInternal(videoId, audioQualityFormat, connectionMetered, allowBoundedRange)
            withResolvedStream(resolvedStream)
        }
    } catch (e: CancellationException) {
        if (e.cause is InterruptedException) {
            // ExoPlayer interrupted the thread during a media item transition.
            // Re-throw as IOException so ExoPlayer handles it as a recoverable load error.
            Timber.tag(TAG).w("Stream resolution interrupted for $videoId (media item transition)")
            throw IOException("Stream resolution interrupted for $videoId", e)
        }
        // Genuine coroutine cancellation — propagate as-is
        throw e
    }
}
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // DataSource factories (ExoPlayer integration)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@UnstableApi
fun PlayerServiceModern.createDataSourceFactory(): DataSource.Factory {
    val upstreamFactory = appContext().okHttpDataSourceFactory

    val resolvingDataSourceFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")
        val isLocal = dataSpec.uri.scheme == ContentResolver.SCHEME_CONTENT ||
                      dataSpec.uri.scheme == ContentResolver.SCHEME_FILE

        if (isLocal) return@Factory dataSpec

        scope.launch(PlaybackDispatchers.STREAM_RESOLVER) { upsertSongInfo(videoId) }

        dataSpec.process(videoId, audioQualityFormat, applicationContext.isConnectionMetered())
            .buildUpon()
            .setKey(videoId)
            .build()
    }

    val lruCacheFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolvingDataSourceFactory)

    val finalCacheFactory = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(lruCacheFactory)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    return ResolvingDataSource.Factory(finalCacheFactory) { dataSpec ->
        val videoId = dataSpec.key ?: dataSpec.uri.toString().substringAfter("watch?v=")
        val parentalControlEnabled = appContext().preferences.getBoolean(parentalControlEnabledKey, false)
        if (parentalControlEnabled) {
            val isExplicit = Database.songTable.findByIdDirect(videoId)?.title?.startsWith(EXPLICIT_PREFIX, true) == true
            if (isExplicit) {
                throw ExplicitContentException()
            }
        }
        fetchFormatIfMissing(videoId)
        dataSpec.buildUpon().setKey(videoId).build()
    }
}

@UnstableApi
fun MyDownloadHelper.createDataSourceFactory(): DataSource.Factory {
    val upstreamFactory = appContext().okHttpDataSourceFactory

    val resolvingDataSourceFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")
        val length = if (dataSpec.length >= 0) dataSpec.length else 1

        // Cache-first: if download cache already has this range, skip resolution entirely
        if (downloadCache.isCached(videoId, dataSpec.position, length)) {
            return@Factory dataSpec
        }

        fun resolveFresh(): DataSpec {
            fetchFormatIfMissing(videoId)
            scope.launch(PlaybackDispatchers.STREAM_RESOLVER) { upsertSongInfo(videoId) }
            val resolvedSpec = dataSpec.process(videoId, audioQualityFormat, appContext().isConnectionMetered(), allowBoundedRange = false)
            val cachedStream = streamUrlCache[videoId]
            if (cachedStream != null) {
                return resolvedSpec.withResolvedStream(cachedStream).buildUpon().setKey(videoId).build()
            }
            return resolvedSpec.buildUpon().setKey(videoId).build()
        }

        // Check StreamUrlCache first (populated by playback or previous resolve)
        val cachedStream = streamUrlCache[videoId]
        if (cachedStream != null) {
            return@Factory dataSpec.withResolvedStream(cachedStream).buildUpon().setKey(videoId).build()
        }

        try {
            resolveFresh()
        } catch (e: Exception) {
            Timber.tag("StreamResolver").w(e, "Download resolve failed for $videoId, invalidating URL cache and retrying")
            streamUrlCache.invalidate(videoId)
            try { downloadCache.removeResource(videoId) } catch (_: Exception) {}
            resolveFresh()
        }
    }

    return CacheDataSource.Factory()
        .setCache(getDownloadCache(appContext()))
        .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
        .setCacheWriteDataSinkFactory(null)
}
