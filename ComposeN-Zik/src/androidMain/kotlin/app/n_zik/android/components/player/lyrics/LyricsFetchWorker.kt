package app.n_zik.android.components.player.lyrics

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.cleanPrefix
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import app.n_zik.android.utils.coroutines.NzikDispatchers
import com.metrolist.music.betterlyrics.BetterLyrics
import com.metrolist.music.betterlyrics.TTMLParser
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.lyrics
import it.fast4x.kugou.KuGou
import it.fast4x.lrclib.LrcLib
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "LyricsFetcher"

/**
 * Structured lyrics fetch chain (issue #606).
 *
 * Network fetches and DAO work run on [dataDispatcher]; one-shot LRC/TTML parsing on
 * [mediaDispatcher]; every UI callback, [Toaster] call and [durationProvider] read on
 * [uiDispatcher]. The whole chain (Innertube fallbacks, [saveLyricsSafe] retry included)
 * is a structured child of [fetch], so it is cancelled together with the per-song effect
 * that launches it — no stale callbacks from a previous song survive a song change.
 */
class LyricsFetchWorker(
    private val dataDispatcher: CoroutineDispatcher = NzikDispatchers.DATA,
    private val mediaDispatcher: CoroutineDispatcher = NzikDispatchers.MEDIA,
    private val uiDispatcher: CoroutineDispatcher = NzikDispatchers.UI,
    // Injectable seams for LrcLib/KuGou/Innertube (issue #606): MockK cannot reliably mock
    // `suspend fun`s returning `kotlin.Result<T>` (ABI-level limitation that corrupts the
//
    // value at runtime). Constructor injection with a real default (AD-1, like the
    // dispatchers above) lets tests pass plain lambdas instead, sidestepping MockK.
    private val lrcLibLyrics: suspend (String, String, Duration, String?) -> Result<LrcLib.Lyrics?>? =
        { artist, title, duration, album -> LrcLib.lyrics(artist, title, duration, album) },
    private val lrcLibLyricsUnsynced: suspend (String, String, Duration, String?) -> Result<LrcLib.Lyrics?>? =
        { artist, title, duration, album -> LrcLib.lyricsUnsynced(artist, title, duration, album) },
    private val kuGouLyrics: suspend (String, String, Long) -> Result<KuGou.Lyrics?>? =
        { artist, title, duration -> KuGou.lyrics(artist, title, duration) },
    private val innertubeLyrics: suspend (String) -> Result<String?>? =
        { videoId -> Innertube.lyrics(videoId) }
) {

    suspend fun fetch(
        context: Context,
        mediaId: String,
        lyricsType: LyricsType,
        artistName: String?,
        title: String?,
        mediaMetadata: MediaMetadata,
        durationProvider: () -> Long,
        playerEnableLyricsPopupMessage: Boolean,
        onLyricsUpdated: (Lyrics?) -> Unit,
        onErrorUpdated: (Boolean) -> Unit,
        onCheckedLrcUpdated: (Boolean) -> Unit,
        onCheckedKugouUpdated: (Boolean) -> Unit,
        onCheckedInnertubeUpdated: (Boolean) -> Unit,
        onFetchingStateChanged: (Boolean) -> Unit
    ) {
        try {
            withContext(dataDispatcher) {
                Database.lyricsTable.findAllBySongId(mediaId).collect { allLyrics ->
                    handleEmission(
                        context = context,
                        mediaId = mediaId,
                        lyricsType = lyricsType,
                        artistName = artistName,
                        title = title,
                        mediaMetadata = mediaMetadata,
                        durationProvider = durationProvider,
                        playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                        allLyrics = allLyrics,
                        onLyricsUpdated = onLyricsUpdated,
                        onErrorUpdated = onErrorUpdated,
                        onCheckedLrcUpdated = onCheckedLrcUpdated,
                        onCheckedKugouUpdated = onCheckedKugouUpdated,
                        onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                        onFetchingStateChanged = onFetchingStateChanged
                    )
                }
            }
        } catch (e: CancellationException) {
            Timber.tag(TAG).d("Lyrics fetch chain cancelled for $mediaId")
            throw e
        }
    }

    private suspend fun <T> withUi(block: () -> T): T = withContext(uiDispatcher) { block() }

    private suspend fun handleEmission(
        context: Context,
        mediaId: String,
        lyricsType: LyricsType,
        artistName: String?,
        title: String?,
        mediaMetadata: MediaMetadata,
        durationProvider: () -> Long,
        playerEnableLyricsPopupMessage: Boolean,
        allLyrics: List<Lyrics>,
        onLyricsUpdated: (Lyrics?) -> Unit,
        onErrorUpdated: (Boolean) -> Unit,
        onCheckedLrcUpdated: (Boolean) -> Unit,
        onCheckedKugouUpdated: (Boolean) -> Unit,
        onCheckedInnertubeUpdated: (Boolean) -> Unit,
        onFetchingStateChanged: (Boolean) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val fetchNeeds = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = mediaId,
            lyricsType = lyricsType,
            allLyrics = allLyrics,
            globalLastKaraokeAttemptMediaId = globalLastKaraokeAttemptMediaId,
            globalLastSyncedAttemptMediaId = globalLastSyncedAttemptMediaId,
            globalLastUnSyncedAttemptMediaId = globalLastUnSyncedAttemptMediaId,
            now = now
        )

        val isAuto = lyricsType == LyricsType.Auto
        val wantKaraoke = lyricsType == LyricsType.Karaoke || lyricsType == LyricsType.Synced || isAuto

        val currentLyrics = fetchNeeds.currentLyrics
        val needKaraokeFetch = fetchNeeds.needKaraokeFetch
        val needSyncedFetch = fetchNeeds.needSyncedFetch
        val needUnsyncedFetch = fetchNeeds.needUnsyncedFetch

        if (needKaraokeFetch || needSyncedFetch || needUnsyncedFetch) {
            withUi { onFetchingStateChanged(true) }
            try {
                if (needKaraokeFetch) {
                    globalLastKaraokeAttemptMediaId = mediaId
                }
                if (needSyncedFetch) {
                    globalLastSyncedAttemptMediaId = mediaId
                }
                if (needUnsyncedFetch) {
                    globalLastUnSyncedAttemptMediaId = mediaId
                }

                var duration = withUi { durationProvider() }
                while (duration == C.TIME_UNSET) {
                    delay(100)
                    duration = withUi { durationProvider() }
                }

                // Unsync mode → skip synced/karaoke fetch, go directly to unsynced
                if (lyricsType == LyricsType.Unsynced && needUnsyncedFetch) {
                    var foundUnsynced = false
                    val lrcResult: Result<LrcLib.Lyrics?>? =
                        lrcLibLyricsUnsynced(
                            artistName ?: "",
                            title ?: "",
                            duration.milliseconds,
                            null
                        )
                    kotlin.runCatching {
                        lrcResult?.let { r ->
                            r.onSuccess { lrc ->
                                if (lrc?.text?.isNotEmpty() == true && playerEnableLyricsPopupMessage)
                                    withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_lrclib_unsynced)) }
                                if (lrc?.text?.isNotEmpty() != true)
                                    if (playerEnableLyricsPopupMessage)
                                        withUi { Toaster.e(R.string.info_lyrics_not_found_on_s, context.getString(R.string.source_lrclib_unsynced)) }
                                withUi { onErrorUpdated(lrc?.text?.isNotEmpty() != true) }
                                withUi { onCheckedLrcUpdated(true) }
                                saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = lrc?.text))
                                withUi { onLyricsUpdated(currentLyrics) }
                                foundUnsynced = true
                            }
                            r.onFailure {
                                Timber.tag(TAG).e("→ LrcLib UNSYNCED ERROR: ${it.stackTraceToString()}")
                            }
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        Timber.tag(TAG).e("→ LrcLib UNSYNCED ERROR: ${e.stackTraceToString()}")
                    }
                    if (!foundUnsynced) {
                        tryYouTubeUnsynced(
                            mediaId = mediaId,
                            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                            onErrorUpdated = onErrorUpdated,
                            onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                            onLyricsUpdated = onLyricsUpdated,
                            currentLyrics = currentLyrics,
                            context = context
                        )
                    }
                } else {
                    // BetterLyrics is the Karaoke check's source: in Auto/Karaoke it rewrites and
                    // re-stamps the Karaoke row, so it only runs when that check is needed (a fresh
//
                    // word-timed row must not be refetched). Synced mode keeps it as the first source
                    // of its Synced check; it never writes a Karaoke row there.
                    if (wantKaraoke && (needKaraokeFetch || lyricsType == LyricsType.Synced)) {
                        fetchBetterLyrics(
                            context = context,
                            mediaId = mediaId,
                            lyricsType = lyricsType,
                            isAuto = isAuto,
                            artistName = artistName,
                            title = title,
                            mediaMetadata = mediaMetadata,
                            duration = duration,
                            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                            onErrorUpdated = onErrorUpdated,
                            onCheckedLrcUpdated = onCheckedLrcUpdated,
                            onCheckedKugouUpdated = onCheckedKugouUpdated,
                            onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                            onLyricsUpdated = onLyricsUpdated,
                            fetchLrcLibAndKugou = {
                                fetchLrcLibAndKugou(
                                    context = context,
                                    mediaId = mediaId,
                                    artistName = artistName,
                                    title = title,
                                    mediaMetadata = mediaMetadata,
                                    duration = duration,
                                    playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                                    currentLyrics = currentLyrics,
                                    needSyncedFetch = needSyncedFetch,
                                    onErrorUpdated = onErrorUpdated,
                                    onCheckedLrcUpdated = onCheckedLrcUpdated,
                                    onCheckedKugouUpdated = onCheckedKugouUpdated,
                                    onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                                    onLyricsUpdated = onLyricsUpdated
                                )
                            }
                        )
                    } else if (needSyncedFetch) {
                        fetchLrcLibAndKugou(
                            context = context,
                            mediaId = mediaId,
                            artistName = artistName,
                            title = title,
                            mediaMetadata = mediaMetadata,
                            duration = duration,
                            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                            currentLyrics = currentLyrics,
                            needSyncedFetch = needSyncedFetch,
                            onErrorUpdated = onErrorUpdated,
                            onCheckedLrcUpdated = onCheckedLrcUpdated,
                            onCheckedKugouUpdated = onCheckedKugouUpdated,
                            onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                            onLyricsUpdated = onLyricsUpdated
                        )
                    } else {
                        // Only the Unsynced check is needed: run its own LrcLib -> YouTube chain.
                        tryLrcLibUnsyncedThenYouTube(
                            context = context,
                            mediaId = mediaId,
                            artistName = artistName,
                            title = title,
                            mediaMetadata = mediaMetadata,
                            duration = duration,
                            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                            currentLyrics = currentLyrics,
                            onErrorUpdated = onErrorUpdated,
                            onCheckedLrcUpdated = onCheckedLrcUpdated,
                            onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                            onLyricsUpdated = onLyricsUpdated,
                            toastOnFailure = false
                        )
                    }
                }
            } finally {
                withUi { onFetchingStateChanged(false) }
            }
        } else if (!currentLyrics?.data.isNullOrEmpty()) {
            withUi { onFetchingStateChanged(false) }
            withUi { onLyricsUpdated(currentLyrics) }
        } else {
            withUi { onFetchingStateChanged(false) }
        }

        if (lyricsType == LyricsType.Unsynced && currentLyrics?.data == null && globalLastUnSyncedAttemptMediaId != mediaId) {
            globalLastUnSyncedAttemptMediaId = mediaId
            withUi { onErrorUpdated(false) }
            withUi { onLyricsUpdated(null) }

            var foundUnsynced = false
            var duration = withUi { durationProvider() }
            while (duration == C.TIME_UNSET) {
                delay(100)
                duration = withUi { durationProvider() }
            }

            val lrcResult: Result<LrcLib.Lyrics?>? =
                lrcLibLyricsUnsynced(
                    artistName ?: "",
                    title ?: "",
                    duration.milliseconds,
                    mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                )
            kotlin.runCatching {
                lrcResult?.let { r ->
                    r.onSuccess { lrc ->
                        val hasContent = lrc?.plainText?.isNotEmpty() == true
                        if (hasContent) {
                            if (playerEnableLyricsPopupMessage)
                                withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_lrclib_unsynced)) }
                            foundUnsynced = true
                            saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = lrc?.plainText.orEmpty()))
                        }
                    }
                    r.onFailure {
                        if (playerEnableLyricsPopupMessage)
                            withUi { Toaster.e(R.string.info_lyrics_not_found_on_s_try_on_s, context.getString(R.string.source_lrclib_unsynced), context.getString(R.string.source_youtube_unsynced)) }
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Timber.tag(TAG).e("→ LrcLib(U) ERROR: ${e.stackTraceToString()}")
                if (playerEnableLyricsPopupMessage)
                    withUi { Toaster.e(R.string.info_lyrics_not_found_on_s_try_on_s, context.getString(R.string.source_lrclib_unsynced), context.getString(R.string.source_youtube_unsynced)) }
            }

            if (!foundUnsynced) {
                val youtubeResult: Result<String?>? = innertubeLyrics(mediaId)
                kotlin.runCatching {
                    youtubeResult?.let { r ->
                        r.onSuccess { fixedLyrics ->
                            if (fixedLyrics?.isNotEmpty() == true && playerEnableLyricsPopupMessage) {
                                withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_youtube_unsynced)) }
                            } else if (playerEnableLyricsPopupMessage) {
                                withUi { Toaster.e(R.string.info_lyrics_not_found_on_s, context.getString(R.string.source_youtube_unsynced), duration = Toast.LENGTH_LONG) }
                            }
                            if (!fixedLyrics.isNullOrEmpty()) {
                                saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = fixedLyrics))
                            } else {
                                withUi { onErrorUpdated(true) }
                            }
                        }
                        r.onFailure {
                            withUi { onErrorUpdated(true) }
                        }
                    }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    Timber.tag(TAG).e("→ YouTube(U) ERROR: ${e.stackTraceToString()}")
                }
            }
            withUi { onCheckedLrcUpdated(true) }
            withUi { onCheckedKugouUpdated(true) }
            withUi { onCheckedInnertubeUpdated(true) }
        } else {
            withUi { onLyricsUpdated(currentLyrics) }
        }
    }

    private suspend fun fetchBetterLyrics(
        context: Context,
        mediaId: String,
        lyricsType: LyricsType,
        isAuto: Boolean,
        artistName: String?,
        title: String?,
        mediaMetadata: MediaMetadata,
        duration: Long,
        playerEnableLyricsPopupMessage: Boolean,
        onErrorUpdated: (Boolean) -> Unit,
        onCheckedLrcUpdated: (Boolean) -> Unit,
        onCheckedKugouUpdated: (Boolean) -> Unit,
        onCheckedInnertubeUpdated: (Boolean) -> Unit,
        onLyricsUpdated: (Lyrics?) -> Unit,
        fetchLrcLibAndKugou: suspend () -> Unit
    ) {
        val betterLyricsResult: Result<String> = runCatching {
            val ttmlStr: String = withContext(dataDispatcher) {
                BetterLyrics.fetchTTML(
                    artist = artistName ?: "",
                    title = cleanPrefix(title ?: ""),
                    duration = duration.milliseconds.inWholeSeconds.toInt(),
                    album = mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                ) ?: throw IllegalStateException("Lyrics unavailable")
            }
            val parsedLines = withContext(mediaDispatcher) { TTMLParser.parseTTML(ttmlStr) }
            if (parsedLines.isEmpty()) throw IllegalStateException("Failed to parse lyrics")
            withContext(mediaDispatcher) { TTMLParser.toLRC(parsedLines) }
        }
        val betterLyricsFailure = betterLyricsResult.exceptionOrNull()
        if (betterLyricsFailure is CancellationException) throw betterLyricsFailure

        kotlin.runCatching {
            betterLyricsResult.onSuccess { lrcStr ->
                val hasKaraokeTimings = lrcStr.lines().any { it.trim().startsWith("<") && it.contains(":") && it.contains(">") }
                if (lrcStr.isNotEmpty()) {
                    if (hasKaraokeTimings) {
                        if (lyricsType == LyricsType.Synced) {
                            // The user explicitly requested Synced, not Karaoke.
                            // BetterLyrics returned Karaoke timings, so we reject it and fallback.
                            fetchLrcLibAndKugou()
                        } else {
                            if (playerEnableLyricsPopupMessage) {
                                withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_betterlyrics_karaoke)) }
                            }
                            withUi { onErrorUpdated(false) }
                            withUi { onCheckedLrcUpdated(true) }
                            saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = lrcStr))
                        }
                    } else {
                        // BetterLyrics found synced lyrics (no word timings)
                        if (lyricsType == LyricsType.Synced) {
                            if (playerEnableLyricsPopupMessage) {
                                withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_betterlyrics_synced)) }
                            }
                            withUi { onErrorUpdated(false) }
                            withUi { onCheckedLrcUpdated(true) }
                            saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = lrcStr))
                        } else {
                            if (playerEnableLyricsPopupMessage) {
                                withUi { Toaster.w(R.string.info_karaoke_not_found_showing_sync, context.getString(R.string.source_betterlyrics_karaoke), context.getString(R.string.source_betterlyrics_synced)) }
                            }
                            withUi { onErrorUpdated(false) }
                            withUi { onCheckedLrcUpdated(true) }
                            saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = lrcStr))
                        }
                    }
                } else {
                    if (playerEnableLyricsPopupMessage) {
                        withUi { Toaster.e(R.string.info_lyrics_not_found_on_s_try_on_s, context.getString(R.string.source_betterlyrics_karaoke), context.getString(R.string.source_betterlyrics_synced)) }
                    }
                    if (isAuto || lyricsType == LyricsType.Synced) {
                        fetchLrcLibAndKugou()
                    } else {
                        withUi {
                            onCheckedLrcUpdated(true)
                            onCheckedKugouUpdated(true)
                            onCheckedInnertubeUpdated(true)
                            onErrorUpdated(true)
                        }
                    }
                }
            }.onFailure {
                if (playerEnableLyricsPopupMessage) {
                    withUi { Toaster.e(R.string.info_lyrics_not_found_on_s_try_on_s, context.getString(R.string.source_betterlyrics_karaoke), context.getString(R.string.source_betterlyrics_synced)) }
                }
                if (isAuto || lyricsType == LyricsType.Synced) {
                    fetchLrcLibAndKugou()
                } else {
                    withUi {
                        onCheckedLrcUpdated(true)
                        onCheckedKugouUpdated(true)
                        onCheckedInnertubeUpdated(true)
                        onErrorUpdated(true)
                    }
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Timber.tag(TAG).e("→ BetterLyrics KARAOKE ERROR: ${e.stackTraceToString()}")
            if (playerEnableLyricsPopupMessage) {
                withUi { Toaster.e(R.string.info_lyrics_not_found_on_s_try_on_s, context.getString(R.string.source_betterlyrics_karaoke), context.getString(R.string.source_betterlyrics_synced)) }
            }
            if (isAuto || lyricsType == LyricsType.Synced) {
                fetchLrcLibAndKugou()
            } else {
                withUi {
                    onCheckedLrcUpdated(true)
                    onCheckedKugouUpdated(true)
                    onCheckedInnertubeUpdated(true)
                    onErrorUpdated(true)
                }
            }
        }
    }

    private suspend fun fetchLrcLibAndKugou(
        context: Context,
        mediaId: String,
        artistName: String?,
        title: String?,
        mediaMetadata: MediaMetadata,
        duration: Long,
        playerEnableLyricsPopupMessage: Boolean,
        currentLyrics: Lyrics?,
        needSyncedFetch: Boolean,
        onErrorUpdated: (Boolean) -> Unit,
        onCheckedLrcUpdated: (Boolean) -> Unit,
        onCheckedKugouUpdated: (Boolean) -> Unit,
        onCheckedInnertubeUpdated: (Boolean) -> Unit,
        onLyricsUpdated: (Lyrics?) -> Unit
    ) {
        if (currentLyrics?.data.isNullOrEmpty() || needSyncedFetch) {
            val lrcResult: Result<LrcLib.Lyrics?>? =
                lrcLibLyrics(
                    artistName ?: "",
                    title ?: "",
                    duration.milliseconds,
                    mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                )
            kotlin.runCatching {
                lrcResult?.let { r ->
                    r.onSuccess { lrc ->
                        if ((lrc?.text?.isNotEmpty() == true || lrc?.sentences?.isNotEmpty() == true)
                            && playerEnableLyricsPopupMessage
                        )
                            withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_lrclib_synced)) }
                        else
                            if (playerEnableLyricsPopupMessage)
                                withUi {
                                    Toaster.e(
                                        R.string.info_lyrics_not_found_on_s,
                                        context.getString(R.string.source_lrclib_synced),
                                        duration = Toast.LENGTH_LONG
                                    )
                                }

                        withUi { onErrorUpdated(false) }
                        withUi { onCheckedLrcUpdated(true) }

                        saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = lrc?.text.orEmpty()))
                    }
                    r.onFailure {
                        if (playerEnableLyricsPopupMessage)
                            withUi {
                                Toaster.e(
                                    R.string.info_lyrics_not_found_on_s_try_on_s,
                                    context.getString(R.string.source_lrclib_synced), context.getString(R.string.source_kugou_synced),
                                    duration = Toast.LENGTH_LONG
                                )
                            }

                        withUi { onCheckedLrcUpdated(true) }

                        tryKugouSynced(
                            context = context,
                            mediaId = mediaId,
                            artistName = artistName,
                            title = title,
                            mediaMetadata = mediaMetadata,
                            duration = duration,
                            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                            currentLyrics = currentLyrics,
                            onErrorUpdated = onErrorUpdated,
                            onCheckedLrcUpdated = onCheckedLrcUpdated,
                            onCheckedKugouUpdated = onCheckedKugouUpdated,
                            onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                            onLyricsUpdated = onLyricsUpdated
                        )
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                Timber.tag(TAG).e("→ LrcLib ERROR: ${e.stackTraceToString()}")
                if (!currentLyrics?.data.isNullOrEmpty()) {
                    withUi { onLyricsUpdated(currentLyrics) }
                }
            }
        }
    }

    private suspend fun tryKugouSynced(
        context: Context,
        mediaId: String,
        artistName: String?,
        title: String?,
        mediaMetadata: MediaMetadata,
        duration: Long,
        playerEnableLyricsPopupMessage: Boolean,
        currentLyrics: Lyrics?,
        onErrorUpdated: (Boolean) -> Unit,
        onCheckedLrcUpdated: (Boolean) -> Unit,
        onCheckedKugouUpdated: (Boolean) -> Unit,
        onCheckedInnertubeUpdated: (Boolean) -> Unit,
        onLyricsUpdated: (Lyrics?) -> Unit
    ) {
        kotlin.runCatching {
            val kugouResult: Result<KuGou.Lyrics?>? =
                kuGouLyrics(
                    mediaMetadata.artist?.toString() ?: "",
                    cleanPrefix(mediaMetadata.title?.toString() ?: ""),
                    duration / 1000
                )
            kugouResult?.let { r ->
                r.onSuccess { kugou ->
                    val hasContent = kugou?.value?.isNotEmpty() == true || kugou?.sentences?.isNotEmpty() == true
                    if (hasContent) {
                        if (playerEnableLyricsPopupMessage)
                            withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_kugou_synced)) }
                        withUi { onErrorUpdated(false) }
                        withUi { onCheckedKugouUpdated(true) }
                        saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = kugou?.value.orEmpty()))
                    } else {
                        if (playerEnableLyricsPopupMessage)
                            withUi {
                                Toaster.e(
                                    R.string.info_lyrics_not_found_on_s_try_on_s,
                                    context.getString(R.string.source_kugou_synced),
                                    context.getString(R.string.source_lrclib_unsynced)
                                )
                            }
                        withUi { onCheckedKugouUpdated(true) }
                        tryLrcLibUnsyncedThenYouTube(
                            context = context,
                            mediaId = mediaId,
                            artistName = artistName,
                            title = title,
                            mediaMetadata = mediaMetadata,
                            duration = duration,
                            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                            currentLyrics = currentLyrics,
                            onErrorUpdated = onErrorUpdated,
                            onCheckedLrcUpdated = onCheckedLrcUpdated,
                            onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                            onLyricsUpdated = onLyricsUpdated,
                            toastOnFailure = false
                        )
                    }
                }
                r.onFailure {
                    if (playerEnableLyricsPopupMessage)
                        withUi {
                            Toaster.e(
                                R.string.info_lyrics_not_found_on_s_try_on_s,
                                context.getString(R.string.source_kugou_synced),
                                context.getString(R.string.source_lrclib_unsynced)
                            )
                        }
                    tryLrcLibUnsyncedThenYouTube(
                        context = context,
                        mediaId = mediaId,
                        artistName = artistName,
                        title = title,
                        mediaMetadata = mediaMetadata,
                        duration = duration,
                        playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                        currentLyrics = currentLyrics,
                        onErrorUpdated = onErrorUpdated,
                        onCheckedLrcUpdated = onCheckedLrcUpdated,
                        onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                        onLyricsUpdated = onLyricsUpdated,
                        toastOnFailure = true
                    )
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Timber.tag(TAG).e("→ KuGou ERROR: ${e.stackTraceToString()}")
            if (!currentLyrics?.data.isNullOrEmpty()) {
                withUi { onLyricsUpdated(currentLyrics) }
            }
        }
    }

    private suspend fun tryLrcLibUnsyncedThenYouTube(
        context: Context,
        mediaId: String,
        artistName: String?,
        title: String?,
        mediaMetadata: MediaMetadata,
        duration: Long,
        playerEnableLyricsPopupMessage: Boolean,
        currentLyrics: Lyrics?,
        onErrorUpdated: (Boolean) -> Unit,
        onCheckedLrcUpdated: (Boolean) -> Unit,
        onCheckedInnertubeUpdated: (Boolean) -> Unit,
        onLyricsUpdated: (Lyrics?) -> Unit,
        toastOnFailure: Boolean
    ) {
        val lrcResult: Result<LrcLib.Lyrics?>? =
            lrcLibLyricsUnsynced(
                artistName ?: "",
                title ?: "",
                duration.milliseconds,
                mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
            )
        kotlin.runCatching {
            lrcResult?.let { r ->
                r.onSuccess { lrc ->
                    val hasContent = lrc?.plainText?.isNotEmpty() == true
                    if (hasContent) {
                        if (playerEnableLyricsPopupMessage)
                            withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_lrclib_unsynced)) }
                        withUi { onErrorUpdated(false) }
                        withUi { onCheckedLrcUpdated(true) }
                        saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = lrc?.plainText.orEmpty()))
                    } else {
                        withUi { onCheckedLrcUpdated(true) }
                        tryYouTubeUnsynced(
                            mediaId = mediaId,
                            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                            onErrorUpdated = onErrorUpdated,
                            onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                            onLyricsUpdated = onLyricsUpdated,
                            currentLyrics = currentLyrics,
                            context = context
                        )
                    }
                }
                r.onFailure {
                    if (toastOnFailure && playerEnableLyricsPopupMessage)
                        withUi {
                            Toaster.e(
                                R.string.info_lyrics_not_found_on_s_try_on_s,
                                context.getString(R.string.source_lrclib_unsynced),
                                context.getString(R.string.source_youtube_unsynced)
                            )
                        }
                    withUi { onCheckedLrcUpdated(true) }
                    tryYouTubeUnsynced(
                        mediaId = mediaId,
                        playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                        onErrorUpdated = onErrorUpdated,
                        onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                        onLyricsUpdated = onLyricsUpdated,
                        currentLyrics = currentLyrics,
                        context = context
                    )
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Timber.tag(TAG).e("→ LrcLib(U) ERROR: ${e.stackTraceToString()}")
            if (toastOnFailure && playerEnableLyricsPopupMessage)
                withUi {
                    Toaster.e(
                        R.string.info_lyrics_not_found_on_s_try_on_s,
                        context.getString(R.string.source_lrclib_unsynced),
                        context.getString(R.string.source_youtube_unsynced)
                    )
                }
            withUi { onCheckedLrcUpdated(true) }
            tryYouTubeUnsynced(
                mediaId = mediaId,
                playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
                onErrorUpdated = onErrorUpdated,
                onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
                onLyricsUpdated = onLyricsUpdated,
                currentLyrics = currentLyrics,
                context = context
            )
        }
    }

    private suspend fun tryYouTubeUnsynced(
        mediaId: String,
        playerEnableLyricsPopupMessage: Boolean,
        onErrorUpdated: (Boolean) -> Unit,
        onCheckedInnertubeUpdated: (Boolean) -> Unit,
        onLyricsUpdated: (Lyrics?) -> Unit,
        currentLyrics: Lyrics?,
        context: Context
    ) {
        val youtubeResult: Result<String?>? = innertubeLyrics(mediaId)
        kotlin.runCatching {
            youtubeResult?.let { r ->
                r.onSuccess { fixedLyrics ->
                    if (fixedLyrics?.isNotEmpty() == true && playerEnableLyricsPopupMessage) {
                        withUi { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_youtube_unsynced)) }
                    } else if (playerEnableLyricsPopupMessage) {
                        withUi { Toaster.e(R.string.info_lyrics_not_found_on_s, context.getString(R.string.source_youtube_unsynced), duration = Toast.LENGTH_LONG) }
                    }
                    withUi { onCheckedInnertubeUpdated(true) }
                    if (!fixedLyrics.isNullOrEmpty()) {
                        saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = fixedLyrics))
                    } else {
                        withUi { onErrorUpdated(true) }
                    }
                }
                r.onFailure {
                    withUi { onCheckedInnertubeUpdated(true) }
                    withUi { onErrorUpdated(true) }
                    if (!currentLyrics?.data.isNullOrEmpty()) {
                        withUi { onLyricsUpdated(currentLyrics) }
                    }
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Timber.tag(TAG).e("→ YouTube(U) ERROR: ${e.stackTraceToString()}")
            withUi { onCheckedInnertubeUpdated(true) }
            withUi { onErrorUpdated(true) }
            if (!currentLyrics?.data.isNullOrEmpty()) {
                withUi { onLyricsUpdated(currentLyrics) }
            }
        }
    }

    /**
     * Writes fetched [lyrics] unless the stored row with the same key is a non-empty user edit
     * (gh-765): the edit may have landed while the network fetch was in flight. An empty fetch
     * result never clears an existing non-empty row (a source with no lyrics must not wipe stored
     * data). Stamps [Lyrics.lastFetchedAt] with the write time to feed the per-row refetch TTL.
     *
     * @return `false` when the write was skipped (user edit or empty result over existing data)
     */
    private suspend fun upsertUnlessEdited(lyrics: Lyrics): Boolean {
        val stored = Database.lyricsTable.findBySongIdAndType(lyrics.songId, lyrics.type).firstOrNull()
        if (stored?.isEdited == true && !stored.data.isNullOrEmpty()) {
            Timber.tag(TAG).d("Skipping fetched ${lyrics.type} lyrics for ${lyrics.songId}: user-edited lyrics are kept")
            return false
        }
        if (lyrics.data.isNullOrEmpty() && !stored?.data.isNullOrEmpty()) {
            Timber.tag(TAG).d("Skipping empty fetched ${lyrics.type} lyrics for ${lyrics.songId}: existing lyrics are kept")
            return false
        }
        Database.lyricsTable.upsert(
            Lyrics(
                songId = lyrics.songId,
                type = lyrics.type,
                data = lyrics.data,
                isEdited = lyrics.isEdited,
                lastFetchedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    private suspend fun saveLyricsSafe(lyrics: Lyrics) {
        runCatching {
            upsertUnlessEdited(lyrics)
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e
                is SQLiteConstraintException -> {
                    Timber.tag(TAG).w("Foreign key constraint failed for songId ${lyrics.songId}. Retrying in 5 seconds...")
                    delay(5000)
                    runCatching {
                        upsertUnlessEdited(lyrics)
                    }.onFailure { e2 ->
                        if (e2 !is CancellationException) {
                            Timber.tag(TAG).e("Failed to save lyrics even after delay: ${e2.message}")
                        }
                    }
                }
                else -> {
                    Timber.tag(TAG).e("Error saving lyrics: ${e.message}")
                }
            }
        }
    }
}
