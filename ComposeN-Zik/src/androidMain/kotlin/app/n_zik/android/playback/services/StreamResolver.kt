package app.n_zik.android.playback.services

import app.n_zik.android.core.database.*

import app.n_zik.android.playback.services.*
import app.n_zik.android.playback.exceptions.*
import app.n_zik.android.playback.utils.*

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import app.n_zik.android.R

import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.network.client.Store
import app.n_zik.android.core.security.cipher.CipherDeobfuscator
import app.n_zik.android.core.security.cipher.PlayerJsFetcher
import app.n_zik.android.core.security.cipher.FunctionNameExtractor
import app.n_zik.android.core.security.potoken.PoTokenGenerator
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.clients.YouTubeClient
import it.fast4x.innertube.clients.YouTubeLocale
import it.fast4x.innertube.models.Context
import it.fast4x.innertube.models.PlayerResponse
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.albumPage
import it.fast4x.innertube.requests.nextPage
import it.fast4x.innertube.requests.artistPage
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.n_zik.android.isConnectionMeteredEnabled
import app.it.fast4x.rimusic.models.Format
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.exceptions.LoginRequiredException
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.exceptions.UnknownException
import app.n_zik.android.playback.exceptions.UnplayableException
import app.n_zik.android.playback.exceptions.UnmatchedSongException
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.utils.isConnectionMetered
import app.it.fast4x.rimusic.utils.okHttpDataSourceFactory
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.disabledStreamClientsKey
import app.it.fast4x.rimusic.utils.streamClientWebRemixEnabledKey
import app.it.fast4x.rimusic.utils.streamClientVisionosEnabledKey
import app.it.fast4x.rimusic.utils.streamClientTvEmbeddedEnabledKey
import app.it.fast4x.rimusic.utils.streamClientTvSimplyEnabledKey
import app.it.fast4x.rimusic.utils.streamClientTvHtml5EnabledKey
import app.it.fast4x.rimusic.utils.streamClientAndroidVrEnabledKey
import app.it.fast4x.rimusic.utils.streamClientAndroidCreatorEnabledKey
import app.it.fast4x.rimusic.utils.streamClientIosEnabledKey
import app.it.fast4x.rimusic.utils.streamClientIpadosEnabledKey
import app.it.fast4x.rimusic.utils.streamClientWebEnabledKey
import app.it.fast4x.rimusic.utils.streamClientWebCreatorEnabledKey
import app.it.fast4x.rimusic.utils.streamClientMobileEnabledKey
import app.it.fast4x.rimusic.utils.streamClientAndroidEnabledKey
import app.it.fast4x.rimusic.utils.preferredStreamClientKey
import app.it.fast4x.rimusic.utils.parseArtists

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.Blocking
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
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import it.fast4x.innertube.YtMusic
import io.ktor.client.plugins.ResponseException
import java.util.Locale
import android.database.sqlite.SQLiteConstraintException
import it.fast4x.innertube.strategy.ContentAwareFallbackStrategy
import it.fast4x.innertube.utils.NewPipeUtils
import it.fast4x.innertube.strategy.ContentHints
import okhttp3.Request
import io.ktor.http.URLBuilder

private const val TAG = "StreamResolver"
private const val CHUNK_LENGTH = 512 * 1024L
private const val MAX_RESOLVE_RETRIES = 7
private const val INITIAL_RETRY_DELAY_MS = 1500L

// Singleton PoTokenGenerator to reuse across resolve calls (avoids recreating WebView each time)
private val poTokenGenerator = PoTokenGenerator()

// Content-aware fallback: selects client order based on content type (live, explicit, kids, uploaded)
private val contentFallbackStrategy = ContentAwareFallbackStrategy()

// Full client list for fallback when content-aware list is exhausted
// VISIONOS: CDN URL has no spc throttle gate, streams whole songs with no poToken/cipher
private val FALLBACK_CLIENTS = listOf(
    YouTubeClient.WEB_REMIX,
    YouTubeClient.VISIONOS,
    YouTubeClient.WEB_CREATOR,
    YouTubeClient.TVHTML5,
    YouTubeClient.ANDROID_VR_1_65_10,
    YouTubeClient.ANDROID_VR_1_43_32,
    YouTubeClient.TVHTML5_SIMPLY,
    YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
    YouTubeClient.IOS,
    YouTubeClient.IPADOS,
    YouTubeClient.ANDROID_CREATOR,
    YouTubeClient.ANDROID_VR_NO_AUTH,
    YouTubeClient.MOBILE,
    YouTubeClient.ANDROID_NO_SDK,
    YouTubeClient.WEB,
)

private val WEB_CLIENTS = setOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5", "TVHTML5_SIMPLY", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")

// Age-restricted playability statuses
private val AGE_RESTRICTED_STATUSES = setOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "LOGIN_REQUIRED", "CONTENT_CHECK_REQUIRED")

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

private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    useArrayPolymorphism = true
    explicitNulls = false
}

suspend fun upsertSongInfo(videoId: String) {
    if (videoId == justInserted) return
    if (!fetchingSongInfos.add(videoId)) {
        Timber.tag(TAG).d("upsertSongInfo already in progress for $videoId, skipping duplicate.")
        return
    }

    try {
        Innertube.nextPage(NextBody(videoId = videoId))?.fold(
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
                        CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch {
                            try {
                                val artistPage = Innertube.artistPage(BrowseBody(browseId = artistId))?.getOrNull()
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
                    CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch {
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
                CoroutineScope(Dispatchers.IO).launch {
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
            CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch {
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
    CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch {
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
                    val headRequest = Request.Builder().head().url(streamUri.toString()).build()
                    NetworkClientFactory.getCachelessClient().newCall(headRequest).execute().use { resp ->
                        resp.header("Content-Length")?.toLongOrNull()
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
                    CoroutineScope(Dispatchers.IO).launch {
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

/**
 * Checks playability status and throws appropriate exception with the real reason.
 */
@UnstableApi
private fun checkPlayability(playabilityStatus: PlayerResponse.PlayabilityStatus?) {
    if (playabilityStatus?.status != "OK") {
        val reason = playabilityStatus?.reason ?: "Unknown reason (status=${playabilityStatus?.status})"
        when (playabilityStatus?.status) {
            "LOGIN_REQUIRED" -> throw LoginRequiredException("Login required: $reason")
            "UNPLAYABLE"     -> throw UnplayableException("Unplayable: $reason")
            else             -> throw UnknownException("${playabilityStatus?.status ?: "NULL"}: $reason")
        }
    }
}

private fun scoreCodec(mimeType: String): Int = when {
    mimeType.contains("opus", ignoreCase = true) -> 2
    mimeType.contains("mp4a", ignoreCase = true) -> 1
    else -> 0
}

/**
 * Picks the best audio format from a player response based on user quality preference.
 * Uses codec-aware scoring: opus > mp4a, stereo > mono, then bitrate as tiebreaker.
 */
private fun pickFormat(
    streamingData: PlayerResponse.StreamingData?,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): PlayerResponse.StreamingData.Format? {
    val formats = streamingData?.adaptiveFormats
        ?.filter { it.url != null || it.signatureCipher != null }
        ?.filter { it.isAudio }
        ?: return null

    if (formats.isEmpty()) return null

    fun scoreQuality(audioQuality: String?): Int = when (audioQuality) {
        "AUDIO_QUALITY_HIGH" -> 3
        "AUDIO_QUALITY_MEDIUM" -> 2
        "AUDIO_QUALITY_LOW" -> 1
        else -> 0
    }

    fun scoreFormat(format: PlayerResponse.StreamingData.Format): Int {
        var score = 0
        score += scoreQuality(format.audioQuality) * 10000
        score += (format.audioChannels ?: 2) * 1000
        score += scoreCodec(format.mimeType) * 100
        score += (format.bitrate ?: 0)
        return score
    }

    return when (audioQualityFormat) {
        AudioQualityFormat.High -> {
            // Prefer AUDIO_QUALITY_HIGH, then best scored
            formats.filter { it.audioQuality == "AUDIO_QUALITY_HIGH" }
                .maxByOrNull { scoreFormat(it) }
                ?: formats.maxByOrNull { scoreFormat(it) }
        }
        AudioQualityFormat.Medium -> {
            // Cap at 128kbps (YouTube Normal), prefer original uploads
            val cappedFormats = formats.filter { (it.bitrate ?: 0) <= 128000 }
            cappedFormats.filter { it.isOriginal }.maxByOrNull { it.bitrate ?: 0 }
                ?: cappedFormats.maxByOrNull { it.bitrate ?: 0 }
                ?: formats.filter { it.isOriginal }.minByOrNull { kotlin.math.abs((it.bitrate ?: 0).toDouble() - 128000.0) }
                ?: formats.minByOrNull { it.bitrate ?: 0 }
        }
        AudioQualityFormat.Low -> {
            // Cap at 48kbps (YouTube Low), prefer original uploads
            val cappedFormats = formats.filter { (it.bitrate ?: 0) <= 48000 }
            cappedFormats.filter { it.isOriginal }.maxByOrNull { it.bitrate ?: 0 }
                ?: cappedFormats.maxByOrNull { it.bitrate ?: 0 }
                ?: formats.filter { it.isOriginal }.minByOrNull { kotlin.math.abs((it.bitrate ?: 0).toDouble() - 48000.0) }
                ?: formats.minByOrNull { it.bitrate ?: 0 }
        }
        AudioQualityFormat.Auto -> {
            val targetBitrate = if (connectionMetered && isConnectionMeteredEnabled()) 128000.0 else (formats.maxOfOrNull { it.bitrate ?: 0 } ?: 128000).toDouble()
            val cappedFormats = formats.filter { (it.bitrate ?: 0) <= targetBitrate }
            cappedFormats.filter { it.isOriginal }.maxByOrNull { it.bitrate ?: 0 }
                ?: cappedFormats.maxByOrNull { it.bitrate ?: 0 }
                ?: formats.filter { it.isOriginal }.minByOrNull { kotlin.math.abs((it.bitrate ?: 0).toDouble() - targetBitrate) }
                ?: formats.maxByOrNull { it.bitrate ?: 0 }
        }
    }
}

/**
 * Resolves the stream URL for a given format.
 *
 * - For ANDROID_VR / ANDROID / IOS: uses the direct URL from the format.
 * - For WEB_REMIX / web clients: deobfuscates via CipherDeobfuscator + n-transform.
 */
private suspend fun resolveFormatUrl(
    videoId: String,
    format: PlayerResponse.StreamingData.Format,
    clientName: String
): Uri? {
    // Direct URL clients (no signature cipher, no n-transform)
    if (clientName !in WEB_CLIENTS) {
        val directUrl = format.url ?: return null
        Timber.tag(TAG).d("Direct URL for $clientName: ${directUrl.take(80)}...")
        return directUrl.toUri()
    }

    // signatureCipher needs deobfuscation (web clients)
    val sigCipher = format.signatureCipher ?: format.cipher
    val formatUrl = format.url
    val resolvedUri = if (sigCipher != null) {
        Timber.tag(TAG).d("signatureCipher detected, deobfuscating for $videoId")
        try {
            val deobfuscated = CipherDeobfuscator.deobfuscateStreamUrl(sigCipher, videoId)
            if (deobfuscated != null) {
                Timber.tag(TAG).d("CipherDeobfuscator success for $videoId")
                Uri.parse(deobfuscated)
            } else {
                Timber.tag(TAG).w("CipherDeobfuscator returned null, trying NewPipe getStreamUrl")
                // Dedicated NewPipe step: try NewPipeUtils.getStreamUrl first
                val newPipeStreamUrl = runCatching {
                    NewPipeUtils.getStreamUrl(format, videoId).getOrNull()
                }.getOrNull()
                if (newPipeStreamUrl != null) {
                    Timber.tag(TAG).d("NewPipe getStreamUrl success for $videoId")
                    Uri.parse(newPipeStreamUrl)
                } else {
                    Timber.tag(TAG).w("NewPipe getStreamUrl failed, trying manual cipher decode")
                    val newPipeUrl = runCatching {
                        val params = io.ktor.http.parseQueryString(sigCipher)
                        val s = params["s"] ?: throw Exception("No signature")
                        val sp = params["sp"] ?: throw Exception("No signature parameter")
                        val urlParam = params["url"] ?: throw Exception("No url")
                        val urlBuilder = URLBuilder(urlParam)
                        urlBuilder.parameters[sp] = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, s)
                        val decUrl = urlBuilder.buildString()
                        YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, decUrl)
                    }.onFailure {
                        Timber.tag(TAG).e(it, "Manual NewPipe fallback failed")
                    }.getOrNull()
                    newPipeUrl?.let { Uri.parse(it) }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Cipher deobfuscation failed for $videoId")
            null
        }
    } else if (formatUrl != null) {
        Uri.parse(formatUrl)
    } else {
        null
    }

    // Apply n-transform for web clients to avoid throttling/403
    if (resolvedUri != null && clientName in WEB_CLIENTS) {
        return try {
            val transformed = CipherDeobfuscator.transformNParamInUrl(resolvedUri.toString())
            Uri.parse(transformed)
        } catch (e: CancellationException) {
            throw e // request superseded/cancelled — propagate
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "N-transform failed for $clientName, using original URL")
            resolvedUri
        }
    }

    return resolvedUri
}

/**
 * Resolves the stream URI with retry logic.
 *
 * Wraps [resolveStreamUriInternal] with up to [MAX_RESOLVE_RETRIES] attempts,
 * invalidating the format cache between retries. Shows a toast when all
 * attempts are exhausted.
 */
@UnstableApi
private suspend fun resolveStreamUri(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    var lastException: Exception? = null

    for (attempt in 1..MAX_RESOLVE_RETRIES) {
        try {
            return resolveStreamUriInternal(videoId, audioQualityFormat, connectionMetered)
        } catch (e: Exception) {
            lastException = e
            Timber.tag(TAG).w("Resolve attempt $attempt/$MAX_RESOLVE_RETRIES failed for $videoId: ${e.message}")
            if (attempt < MAX_RESOLVE_RETRIES) {
                // Invalidate cached URL so next attempt fetches a fresh one
                formatCache.remove(videoId)
                delay(INITIAL_RETRY_DELAY_MS * attempt)
            }
        }
    }

    // All retries exhausted - show toast with the real reason
    Timber.tag(TAG).e("All $MAX_RESOLVE_RETRIES resolve attempts failed for $videoId")
    val errorDetail = lastException?.message ?: appContext().resources.getString(R.string.unknown_error)
    Toaster.e(R.string.error_all_stream_attempts_failed, formatArgs = arrayOf(errorDetail.take(100)))
    throw lastException ?: UnplayableException("All retries exhausted for $videoId")
}

/**
 * Core stream resolution logic (single attempt).
 *
 * Mirrors Metrolist's YTPlayerUtils.playerResponseForPlayback() flow:
 * 1. Fetch signatureTimestamp from player.js
 * 2. Generate PoToken (WebView)
 * 3. Try each client in order -> get player response -> resolve URL -> validate
 * 4. Return the first working stream URL
 */
@UnstableApi
private suspend fun resolveStreamUriInternal(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Uri {
    val locale = YouTubeLocale(
        gl = Locale.getDefault().country.takeIf { it.isNotEmpty() } ?: "US",
        hl = Locale.getDefault().language.takeIf { it.isNotEmpty() } ?: "en"
    )
    val visitorData = Store.getIosVisitorData() ?: Innertube.DEFAULT_VISITOR_DATA
    val parentalControlEnabled = appContext().preferences.getBoolean(parentalControlEnabledKey, false)

    // 1. Get signatureTimestamp — prefer cipher STS (avoids cross-player-generation 403s during A/B rollouts)
    val cipherSts = runCatching {
        CipherDeobfuscator.signatureTimestamp()
    }.getOrNull()
    Timber.tag(TAG).d("cipherSts: $cipherSts")

    // NewPipe STS as fallback + age-restriction detection
    var isAgeRestricted = false
    val newPipeSts = runCatching {
        NewPipeUtils.getSignatureTimestamp(videoId).getOrThrow()
    }.onFailure { error ->
        val ageRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
            error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
        if (ageRestricted) {
            isAgeRestricted = true
            Timber.tag(TAG).w("Age-restricted content detected early via NewPipe STS: videoId=$videoId")
        } else {
            Timber.tag(TAG).w(error, "NewPipe STS unavailable, using cipher STS")
        }
    }.getOrNull()

    val signatureTimestamp = cipherSts ?: newPipeSts
    Timber.tag(TAG).d("signatureTimestamp resolved: cipher=$cipherSts newpipe=$newPipeSts -> using $signatureTimestamp, isAgeRestricted=$isAgeRestricted")

    // 2. Generate PoToken for WEB_REMIX (web clients only)
    val poTokenResult: PoTokenResult? = runCatching {
        val sessionId = Store.getIosVisitorData() ?: ""
        poTokenGenerator.getWebClientPoToken(videoId, sessionId)
    }.getOrNull()
    val poToken: String? = poTokenResult?.playerRequestPoToken
    Timber.tag(TAG).d("poToken generated: ${poToken != null}, streamingDataPoToken: ${poTokenResult?.streamingDataPoToken != null}")

    val isLoggedIn = !Innertube.cookie.isNullOrBlank() && Innertube.cookie?.contains("SAPISID") == true

    // Track the last meaningful failure reason across all clients
    var lastFailureReason: String? = null
    var fallbackUri: Uri? = null
    var fallbackFormat: PlayerResponse.StreamingData.Format? = null

    // HIGH-quality best-fallback tracking
    var bestFallbackFormat: PlayerResponse.StreamingData.Format? = null
    var bestFallbackUri: Uri? = null
    val wantsHighQuality = audioQualityFormat == AudioQualityFormat.High

    val prefs = appContext().preferences
    
    // Build disabled clients set from individual preference keys
    val disabledClients = mutableSetOf<String>().apply {
        if (!prefs.getBoolean(streamClientWebRemixEnabledKey, true)) add("WEB_REMIX")
        if (!prefs.getBoolean(streamClientVisionosEnabledKey, true)) add("VISIONOS")
        if (!prefs.getBoolean(streamClientTvEmbeddedEnabledKey, true)) add("TVHTML5_SIMPLY_EMBEDDED_PLAYER")
        if (!prefs.getBoolean(streamClientTvSimplyEnabledKey, true)) add("TVHTML5_SIMPLY")
        if (!prefs.getBoolean(streamClientTvHtml5EnabledKey, true)) add("TVHTML5")
        if (!prefs.getBoolean(streamClientAndroidVrEnabledKey, true)) add("ANDROID_VR")
        if (!prefs.getBoolean(streamClientAndroidCreatorEnabledKey, true)) add("ANDROID_CREATOR")
        if (!prefs.getBoolean(streamClientIosEnabledKey, true)) add("IOS")
        if (!prefs.getBoolean(streamClientIpadosEnabledKey, true)) add("IPADOS")
        if (!prefs.getBoolean(streamClientWebEnabledKey, true)) add("WEB")
        if (!prefs.getBoolean(streamClientWebCreatorEnabledKey, true)) add("WEB_CREATOR")
        if (!prefs.getBoolean(streamClientMobileEnabledKey, true)) add("MOBILE")
        if (!prefs.getBoolean(streamClientAndroidEnabledKey, true)) add("ANDROID")
    }
    
    // Content-aware client ordering: reorder based on content type hints
    // This ensures live streams, kids content, explicit, and uploaded tracks get optimal client order
    val contentAwareBase = contentFallbackStrategy.resolveClients(
        ContentHints(
            isExplicit = parentalControlEnabled,
            isLive = false,
            isKidsContent = false,
            isUploaded = false,
        )
    )
    // Merge content-aware order with full fallback list: content-aware clients first, then remaining
    val contentAwareOrder = contentAwareBase.map { it.clientName } + FALLBACK_CLIENTS.map { it.clientName }
    val deduplicatedOrder = contentAwareOrder.distinct()
    val baseClients = deduplicatedOrder.mapNotNull { name -> FALLBACK_CLIENTS.find { it.clientName == name } }

    // Get preferred client and prioritize it
    val preferredClientName = prefs.getString(preferredStreamClientKey, "WEB_REMIX") ?: "WEB_REMIX"
    val filteredClients = baseClients.filter { it.clientName !in disabledClients }
    val preferredClient = filteredClients.find { it.clientName == preferredClientName }
    val clientsToTry = if (preferredClient != null) {
        Timber.tag(TAG).d("Preferred client: $preferredClientName — will be tried first")
        listOf(preferredClient) + filteredClients.filter { it.clientName != preferredClientName }
    } else {
        if (preferredClientName in disabledClients) {
            Timber.tag(TAG).w("Preferred client $preferredClientName is disabled — falling back to default order")
        }
        filteredClients.ifEmpty {
            Timber.tag(TAG).w("All stream clients are disabled — enabling WEB_REMIX as fallback")
            listOf(FALLBACK_CLIENTS.first { it.clientName == "WEB_REMIX" })
        }
    }

    if (disabledClients.isNotEmpty()) {
        Timber.tag(TAG).d("Disabled stream clients: $disabledClients")
    }
    Timber.tag(TAG).d("Clients to try (${clientsToTry.size}): ${clientsToTry.map { it.clientName }}")

    // Early age-restriction handling: if detected via NewPipe STS, skip to WEB_CREATOR
    if (isAgeRestricted && parentalControlEnabled && isLoggedIn) {
        Timber.tag(TAG).w("Age-restricted detected early via NewPipe STS, skipping to WEB_CREATOR")
        val webCreator = FALLBACK_CLIENTS.find { it.clientName == "WEB_CREATOR" }
        if (webCreator != null && "WEB_CREATOR" !in disabledClients) {
            // Skip the main loop and try only WEB_CREATOR
            val context = webCreator.toContext(
                locale = locale,
                visitorData = visitorData,
                dataSyncId = if (isLoggedIn) Innertube.dataSyncId else null
            )
            val httpResponse = runCatching {
                Innertube.playerRequest(
                    videoId = videoId,
                    playlistId = null,
                    signatureTimestamp = null, // Skip STS for age-restricted
                    poToken = null,
                    context = context
                )
            }.getOrNull()
            val playerResponse = httpResponse?.body<PlayerResponse>()
            if (playerResponse?.playabilityStatus?.status == "OK") {
                val format = pickFormat(playerResponse.streamingData, audioQualityFormat, connectionMetered)
                if (format != null) {
                    val uri = resolveFormatUrl(videoId, format, "WEB_CREATOR")
                    if (uri != null) {
                        Timber.tag(TAG).d("WEB_CREATOR success for age-restricted content: $videoId")
                        return uri.buildUpon()
                            .appendQueryParameter("range", "0-${format.contentLength ?: 1_000_000}")
                            .build()
                    }
                }
            }
            Timber.tag(TAG).w("WEB_CREATOR failed for age-restricted content, falling through to normal flow")
        }
    }

    // 3. Try each client in order
    for ((index, ytClient) in clientsToTry.withIndex()) {
        try {
            Timber.tag(TAG).d("Trying client (${index + 1}/${clientsToTry.size}): ${ytClient.clientName} for $videoId")

            if (ytClient.loginRequired && !isLoggedIn) {
                Timber.tag(TAG).d("Skipping client ${ytClient.clientName} - requires login but user is not logged in")
                continue
            }

            // Skip clients that have been marked as failed for this videoId (unless TTL expired)
            val clientFailures = clientFailedIds[ytClient.clientName]
            if (clientFailures != null && videoId in clientFailures) {
                if (!isClientFailureExpired(ytClient.clientName, videoId)) {
                    lastFailureReason = "${ytClient.clientName}: previously failed for $videoId — skipping"
                    Timber.tag(TAG).d(lastFailureReason)
                    continue
                }
                // TTL expired, clean up this entry
                cleanupExpiredFailures(ytClient.clientName)
            }

            val context = ytClient.toContext(
                locale = locale,
                visitorData = visitorData,
                dataSyncId = if (isLoggedIn) Innertube.dataSyncId else null
            )

            val sigTs = if (ytClient.useSignatureTimestamp) signatureTimestamp else null
            val pot   = if (ytClient.clientName == "WEB_REMIX") poToken else null

            val httpResponse = Innertube.playerRequest(
                videoId = videoId,
                playlistId = null,
                signatureTimestamp = sigTs,
                poToken = pot,
                context = context
            )

            // Check HTTP status before parsing — with expectSuccess=false, non-2xx responses
            // are returned as-is and may not be valid PlayerResponse JSON
            val httpStatus = httpResponse.status.value
            if (httpStatus !in 200..299) {
                lastFailureReason = "${ytClient.clientName}: HTTP $httpStatus for $videoId"
                Timber.tag(TAG).w(lastFailureReason)
                // Mark non-web clients as failed on HTTP errors
                if (ytClient.clientName !in WEB_CLIENTS) {
                    markClientFailed(ytClient.clientName, videoId)
                }
                continue
            }

            val playerResponse = runCatching {
                httpResponse.body<PlayerResponse>()
            }.getOrNull() ?: run {
                lastFailureReason = "${ytClient.clientName}: failed to parse PlayerResponse"
                Timber.tag(TAG).w(lastFailureReason)
                continue
            }

            // Check playability - capture the reason if not OK
            if (playerResponse.playabilityStatus?.status != "OK") {
                val status = playerResponse.playabilityStatus?.status ?: "NULL"
                val reason = playerResponse.playabilityStatus?.reason ?: "no reason provided"
                lastFailureReason = "$status: $reason"
                Timber.tag(TAG).w("${ytClient.clientName}: status=$status reason=$reason")

                // Age-restriction handling: only when parental control is enabled
                // If logged in and age-restricted, skip to WEB_CREATOR directly
                if (parentalControlEnabled && status in AGE_RESTRICTED_STATUSES && isLoggedIn) {
                    Timber.tag(TAG).w("Age-restricted content detected (status=$status), trying WEB_CREATOR directly")
                    val webCreator = FALLBACK_CLIENTS.find { it.clientName == "WEB_CREATOR" }
                    if (webCreator != null && ytClient.clientName != "WEB_CREATOR") {
                        break // Skip remaining clients and jump to WEB_CREATOR
                    }
                }
                continue
            }

            // Enrich player response with NewPipe stream URLs (better fallback)
            // Skip for age-restricted content (NewPipe can't access without auth)
            val enrichedResponse = if (isAgeRestricted) {
                Timber.tag(TAG).d("Skipping NewPipe enrichment for age-restricted content")
                null
            } else {
                runCatching {
                    NewPipeUtils.enrichWithNewPipe(videoId, playerResponse)
                }.getOrNull()
            }
            val responseToUse = enrichedResponse ?: playerResponse

            // Pick best audio format
            val format = pickFormat(responseToUse.streamingData, audioQualityFormat, connectionMetered)
            if (format == null) {
                lastFailureReason = "${ytClient.clientName}: no suitable audio format found"
                Timber.tag(TAG).w(lastFailureReason)
                continue
            }

            // Resolve URL
            var uri = resolveFormatUrl(videoId, format, ytClient.clientName) ?: run {
                lastFailureReason = "${ytClient.clientName}: could not resolve URL for format itag=${format.itag}"
                Timber.tag(TAG).w(lastFailureReason)
                continue
            }

            // HIGH-quality best-fallback tracking:
            // If user wants HIGH but we got MEDIUM/LOW, save as best fallback and continue
            if (wantsHighQuality && format.audioQuality != "AUDIO_QUALITY_HIGH") {
                val hasHighInResponse = responseToUse.streamingData?.adaptiveFormats
                    ?.any { it.audioQuality == "AUDIO_QUALITY_HIGH" && (it.url != null || it.signatureCipher != null) } == true
                if (hasHighInResponse) {
                    // Save best fallback using codec-aware scoring
                    fun scoreFallback(f: PlayerResponse.StreamingData.Format): Int {
                        var s = 0
                        s += when (f.audioQuality) {
                            "AUDIO_QUALITY_MEDIUM" -> 2000
                            "AUDIO_QUALITY_LOW" -> 1000
                            else -> 0
                        }
                        s += (f.audioChannels ?: 2) * 100
                        s += when {
                            f.mimeType.contains("opus", ignoreCase = true) -> 10
                            f.mimeType.contains("mp4a", ignoreCase = true) -> 5
                            else -> 0
                        }
                        s += (f.bitrate ?: 0)
                        return s
                    }
                    val isBetter = bestFallbackFormat == null || scoreFallback(format) > scoreFallback(bestFallbackFormat!!)
                    if (isBetter) {
                        bestFallbackFormat = format
                        bestFallbackUri = uri
                        Timber.tag(TAG).d("Saved best fallback: ${format.mimeType}, bitrate=${format.bitrate}, quality=${format.audioQuality}")
                    }
                    continue // Try next client for HIGH quality
                }
            }

            // Append streamingDataPoToken as pot= for web clients
            if (ytClient.useWebPoTokens && poTokenResult?.streamingDataPoToken != null) {
                val separator = if ("?" in uri.toString()) "&" else "?"
                uri = Uri.parse("${uri}${separator}pot=${android.net.Uri.encode(poTokenResult.streamingDataPoToken)}")
                Timber.tag(TAG).d("Appended pot= parameter for ${ytClient.clientName}")
            }

            // Validate (HEAD request)
            // WEB_REMIX CDN URLs can sometimes 403 on HEAD yet serve fine on byte-range GET.
            // For WEB clients: let ExoPlayer try anyway (HEAD can give false 403s).
            // For non-WEB clients (ANDROID_VR, IOS, etc.): HEAD 403 is real — skip to next client.
            // Also skip for last fallback client to guarantee at least one stream reaches ExoPlayer.
            val streamUrl = uri.toString()
            val isLastClient = index == clientsToTry.size - 1
            val isWebClient = ytClient.clientName in WEB_CLIENTS
            val shouldSkipValidation = isLastClient
            val isValid = if (shouldSkipValidation) {
                Timber.tag(TAG).d("Last fallback client ${ytClient.clientName} — skipping HEAD validation, letting ExoPlayer try directly")
                true
            } else {
                // Pass cookie for private tracks when logged in
                val cookie = if (isLoggedIn && ytClient.loginSupported) Innertube.cookie else null
                NetworkClientFactory.validateStreamUrl(streamUrl, ytClient.userAgent, cookie)
            }
            if (!isValid) {
                lastFailureReason = "${ytClient.clientName}: HEAD validation failed (403/expired?) for $videoId"
                Timber.tag(TAG).w(lastFailureReason)
                // Track WEB_REMIX failures so next resolve falls through to fallback clients faster
                if (ytClient.clientName == "WEB_REMIX") {
                    webRemixFailedIds.add(videoId)
                }
                // For non-WEB clients: HEAD 403 is real — mark as failed and skip to next client
                if (!isWebClient) {
                    markClientFailed(ytClient.clientName, videoId)
                    continue
                }
                // For WEB clients: trigger config refresh and let ExoPlayer try anyway
                // (HEAD can give false 403s for WEB_REMIX CDN URLs)
                CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch {
                    val configChanged = runCatching { CipherDeobfuscator.onStreamRejected() }.getOrNull() ?: false
                    if (configChanged) {
                        clearWebRemixFailures()
                    }
                }
            }

            // Check if metadata is missing. If so, save as fallback and try next client.
            if ((format.bitrate == null || format.bitrate == 0) && (format.contentLength?.toLong() ?: 0L) == 0L) {
                lastFailureReason = "${ytClient.clientName}: stream resolved but missing metadata"
                Timber.tag(TAG).w(lastFailureReason)
                if (fallbackUri == null) {
                    fallbackUri = uri
                    fallbackFormat = format
                }
                continue
            }

            // Success!
            // Validate stream expiry — null means incomplete player response
            val streamExpiresInSeconds = responseToUse.streamingData?.expiresInSeconds?.toLong()
            if (streamExpiresInSeconds == null) {
                lastFailureReason = "${ytClient.clientName}: stream expiration time not found for $videoId"
                Timber.tag(TAG).w(lastFailureReason)
                continue
            }

            Timber.tag(TAG).d("${ytClient.clientName}: stream resolved successfully for $videoId (expires in ${streamExpiresInSeconds}s)")
            val audioLoudnessDb = responseToUse.playerConfig?.audioConfig?.loudnessDb
            val perceptualLoudness = responseToUse.playerConfig?.audioConfig?.perceptualLoudnessDb
            val playbackUrl = responseToUse.playbackTracking?.videostatsPlaybackUrl?.baseUrl
            CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch { upsertSongFormat(videoId, format, perceptualLoudness, playbackUrl, audioLoudnessDb) }

            // Cache PlaybackData for metadata access (loudness, videoDetails, tracking)
            playbackDataCache[videoId] = PlaybackData(
                streamUrl = uri.toString(),
                format = format,
                loudnessDb = responseToUse.playerConfig?.audioConfig?.loudnessDb,
                videoDetails = responseToUse.videoDetails,
                playbackTracking = responseToUse.playbackTracking,
                streamExpiresInSeconds = streamExpiresInSeconds,
                streamClient = ytClient.clientName,
            )
            PlaybackDataStore.saveStreamClient(appContext(), videoId, ytClient.clientName)

            // Resolve content-length via HEAD if not available
            val contentLength = format.contentLength ?: runCatching {
                val headRequest = Request.Builder()
                    .head()
                    .url(uri.toString())
                    .build()
                NetworkClientFactory.getCachelessClient().newCall(headRequest).execute().use { response ->
                    response.header("Content-Length")?.toLongOrNull()
                }
            }.onFailure { Timber.tag(TAG).w(it, "HEAD request for content-length failed") }.getOrNull()
            val resolvedContentLength = contentLength ?: 1_000_000L

            return uri
                .buildUpon()
                .appendQueryParameter("range", "0-$resolvedContentLength")
                .build()

        } catch (e: LoginRequiredException) {
            lastFailureReason = "${ytClient.clientName}: ${e.message ?: appContext().resources.getString(R.string.login_required)}"
            Timber.tag(TAG).w(lastFailureReason)
            continue
        } catch (e: ResponseException) {
            lastFailureReason = "${ytClient.clientName}: HTTP ${e.response.status}"
            Timber.tag(TAG).w(lastFailureReason)
            continue
        } catch (e: Exception) {
            lastFailureReason = "${ytClient.clientName}: ${e::class.simpleName}: ${e.message}"
            Timber.tag(TAG).w(lastFailureReason)
            continue
        }
    }

    // HIGH-quality best-fallback: if we tracked a fallback and no HIGH was found, use it
    if (wantsHighQuality && bestFallbackFormat != null && bestFallbackUri != null) {
        Timber.tag(TAG).w("No HIGH quality found, using best fallback: ${bestFallbackFormat!!.mimeType}, bitrate=${bestFallbackFormat!!.bitrate}")
        CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch { upsertSongFormat(videoId, bestFallbackFormat!!) }
        return bestFallbackUri!!
            .buildUpon()
            .appendQueryParameter("range", "0-${bestFallbackFormat?.contentLength ?: 1_000_000}")
            .build()
    }

    if (fallbackUri != null && fallbackFormat != null) {
        Timber.tag(TAG).w("All clients failed to provide metadata. Using fallback stream for $videoId")
        CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch { upsertSongFormat(videoId, fallbackFormat!!) }
        return fallbackUri!!
            .buildUpon()
            .appendQueryParameter("range", "0-${fallbackFormat?.contentLength ?: 1_000_000}")
            .build()
    }

    // Last resort: try NewPipe extractor for full stream info
    Timber.tag(TAG).w("All clients exhausted, trying NewPipe extractor as last resort for $videoId")
    val newPipeStreams = runCatching {
        NewPipeUtils.newPipePlayer(videoId)
    }.getOrNull()
    if (!newPipeStreams.isNullOrEmpty()) {
        // Try to match the requested itag first, then fall back to any audio stream
        val requestedItag = fallbackFormat?.itag
        val matchedStream = if (requestedItag != null) {
            newPipeStreams.find { it.first == requestedItag }
        } else null
        val (itag, streamUrl) = matchedStream ?: newPipeStreams.first()
        Timber.tag(TAG).d("NewPipe fallback success: itag=$itag (requested=$requestedItag) for $videoId")
        return Uri.parse(streamUrl)
    }

    // All clients exhausted - throw with the last meaningful reason
    val finalReason = lastFailureReason ?: "All ${FALLBACK_CLIENTS.size} clients failed for $videoId"
    Timber.tag(TAG).e("resolveStreamUri FAILED: $finalReason")
    throw UnplayableException(finalReason)
}
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Cache + DataSpec integration
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Cache of resolved stream URLs by videoId.
 * Exposed internally so [PlayerServiceModern.onPlayerError] can invalidate stale entries.
 */
internal val formatCache = mutableMapOf<String, Uri>()

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
    formatCache.clear()
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

    val signatureTimestamp = runCatching {
        CipherDeobfuscator.signatureTimestamp()
    }.getOrNull()

    val sessionId = Store.getIosVisitorData() ?: Innertube.DEFAULT_VISITOR_DATA
    val poToken = runCatching {
        poTokenGenerator.getWebClientPoToken(videoId, sessionId)
    }.getOrNull()

    return Innertube.playerRequest(
        videoId = videoId,
        playlistId = playlistId,
        signatureTimestamp = signatureTimestamp,
        poToken = poToken?.playerRequestPoToken,
        context = YouTubeClient.WEB_REMIX.toContext(
            locale = YouTubeLocale(
                gl = Locale.getDefault().country.takeIf { it.isNotEmpty() } ?: "US",
                hl = Locale.getDefault().language.takeIf { it.isNotEmpty() } ?: "en"
            ),
            visitorData = sessionId,
        )
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
    connectionMetered: Boolean
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

            var formatUri = formatCache[videoId]

            if (formatUri != null) {
                val expireTime = formatUri.getQueryParameter("expire")?.toLongOrNull()?.times(1000)
                val isExpired = expireTime != null && System.currentTimeMillis() >= expireTime - 30_000
                val isUnknownMetadata = formatUri.getQueryParameter("range") == "0-1000000"

                if (isExpired || isUnknownMetadata) {
                    Timber.tag(TAG).d("formatCache entry invalid (expired=$isExpired, unknownMetadata=$isUnknownMetadata), removing for $videoId")
                    formatCache.remove(videoId)
                    formatUri = null
                }
            }

            if (formatUri == null) {
                formatUri = resolveStreamUri(videoId, audioQualityFormat, connectionMetered)
                formatCache[videoId] = formatUri
            }

            val newHeaders = mutableMapOf<String, String>()
            newHeaders.putAll(httpRequestHeaders)

            // DO NOT ADD Cookie or X-Goog-Visitor-Id here!
            // ExoPlayer sends these headers to the googlevideo.com CDN, which will reject them with 403 Forbidden.
            // The stream URL itself contains all necessary authentication tokens (sig, expire, id).

            buildUpon()
                .setUri(formatUri)
                .setHttpRequestHeaders(newHeaders)
                .setUriPositionOffset(uriPositionOffset)
                .build()
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

        CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch { upsertSongInfo(videoId) }

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
            CoroutineScope(PlaybackDispatchers.STREAM_RESOLVER).launch { upsertSongInfo(videoId) }
            val resolvedSpec = dataSpec.process(videoId, audioQualityFormat, appContext().isConnectionMetered())
            val resolvedUrl = resolvedSpec.uri.toString()
            val expireSeconds = resolvedUrl.substringAfter("expire=").substringBefore("&").toLongOrNull()
            val expiryMs = if (expireSeconds != null) {
                expireSeconds * 1000 - 60_000
            } else {
                System.currentTimeMillis() + 6 * 60 * 60 * 1000L
            }
            songUrlCache[videoId] = resolvedUrl to expiryMs
            return resolvedSpec.buildUpon().setKey(videoId).build()
        }

        // Check URL cache first
        val cached = songUrlCache[videoId]
        if (cached != null && cached.second > System.currentTimeMillis()) {
            return@Factory dataSpec.buildUpon()
                .setUri(cached.first.toUri())
                .setKey(videoId)
                .build()
        }

        try {
            resolveFresh()
        } catch (e: Exception) {
            Timber.tag("StreamResolver").w(e, "Download resolve failed for $videoId, invalidating URL cache and retrying")
            songUrlCache.remove(videoId)
            formatCache.remove(videoId)
            try { downloadCache.removeResource(videoId) } catch (_: Exception) {}
            resolveFresh()
        }
    }

    return CacheDataSource.Factory()
        .setCache(getDownloadCache(appContext()))
        .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
        .setCacheWriteDataSinkFactory(null)
}
