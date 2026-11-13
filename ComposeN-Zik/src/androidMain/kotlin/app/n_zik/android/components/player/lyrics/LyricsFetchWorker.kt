package app.n_zik.android.components.player.lyrics

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import app.n_zik.android.cleanPrefix
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import app.n_zik.android.utils.coroutines.NzikDispatchers
import com.metrolist.music.betterlyrics.BetterLyrics
import com.metrolist.music.betterlyrics.TTMLParser
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.lyrics
import app.n_zik.kugou.KuGou
import app.n_zik.lrclib.LrcLib
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "LyricsFetcher"
private const val DURATION_WAIT_MILLIS = 5_000L

/**
 * Observes the cache and fetches from the enabled providers for the current mode.
 * The network, parsing and UI callbacks stay within the caller's per-song coroutine.
 */
class LyricsFetchWorker(
    private val dataDispatcher: CoroutineDispatcher = NzikDispatchers.DATA,
    private val mediaDispatcher: CoroutineDispatcher = NzikDispatchers.MEDIA,
    private val uiDispatcher: CoroutineDispatcher = NzikDispatchers.UI,
    // Injection avoids mocking suspend functions returning Kotlin Result.
    private val lrcLibLyrics: suspend (String, String, Duration, String?) -> Result<LrcLib.Lyrics?>? =
        { artist, title, duration, album -> LrcLib.lyrics(artist, title, duration, album) },
    private val lrcLibLyricsUnsynced: suspend (String, String, Duration, String?) -> Result<LrcLib.Lyrics?>? =
        { artist, title, duration, album -> LrcLib.lyricsUnsynced(artist, title, duration, album) },
    private val kuGouLyrics: suspend (String, String, Long) -> Result<KuGou.Lyrics?>? =
        { artist, title, duration -> KuGou.lyrics(artist, title, duration) },
    private val innertubeLyrics: suspend (String) -> Result<String?>? =
        { videoId -> Innertube.lyrics(videoId) }
) {
    private enum class Lookup(
        val provider: LyricsProvider,
        val label: Int,
        val brand: Int,
        val type: LyricsType
    ) {
        BetterKaraoke(LyricsProvider.BetterLyrics, R.string.lyrics_provider_betterlyrics_karaoke_label,
            R.string.lyrics_brand_betterlyrics, LyricsType.Karaoke),
        BetterSynced(LyricsProvider.BetterLyrics, R.string.lyrics_provider_betterlyrics_synced_label,
            R.string.lyrics_brand_betterlyrics, LyricsType.Synced),
        LrcSynced(LyricsProvider.LrcLib, R.string.lyrics_provider_lrclib_synced_label,
            R.string.lyrics_brand_lrclib, LyricsType.Synced),
        KuGouSynced(LyricsProvider.KuGou, R.string.lyrics_provider_kugou_synced_label,
            R.string.lyrics_brand_kugou, LyricsType.Synced),
        LrcUnsynced(LyricsProvider.LrcLib, R.string.lyrics_provider_lrclib_unsynced_label,
            R.string.lyrics_brand_lrclib, LyricsType.Unsynced),
        YouTubeUnsynced(LyricsProvider.YouTube, R.string.lyrics_provider_youtube_unsynced_label,
            R.string.onboarding_accounts_youtube, LyricsType.Unsynced)
    }

    /** Observe the current cache and attempt an ordered, mode-specific lookup when needed. */
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
        onFetchingStateChanged: (Boolean) -> Unit,
        enabledProviders: Set<LyricsProvider> = LyricsProvider.entries.toSet()
    ) = withContext(dataDispatcher) {
        // Room emissions from our own writes must not retry a stale row during this collection.
        val attempted = mutableSetOf<Lookup>()
        Database.lyricsTable.findAllBySongId(mediaId).collect { allLyrics ->
            val needs = LyricsDecisionMaker.evaluateFetchNeeds(
                mediaId = mediaId,
                lyricsType = lyricsType,
                allLyrics = allLyrics.filterNot { it.data.isNullOrBlank() },
                globalLastKaraokeAttemptMediaId = globalLastKaraokeAttemptMediaId,
                globalLastSyncedAttemptMediaId = globalLastSyncedAttemptMediaId,
                globalLastUnSyncedAttemptMediaId = globalLastUnSyncedAttemptMediaId,
                now = System.currentTimeMillis()
            )
            val cached = preferredCache(lyricsType, needs.currentLyrics, allLyrics)
            val lookups = lookupsFor(lyricsType, needs).filter { lookup ->
                lookup.provider in enabledProviders && lookup !in attempted
            }
            withContext(uiDispatcher) {
                onLyricsUpdated(cached)
                onFetchingStateChanged(lookups.isNotEmpty())
                if (lookups.isEmpty() && cached == null) {
                    onErrorUpdated(true)
                    onCheckedLrcUpdated(true)
                    onCheckedKugouUpdated(true)
                    onCheckedInnertubeUpdated(true)
                }
            }
            if (lookups.isEmpty()) return@collect

            try {
                val needsTrackMetadata = lookups.any { it != Lookup.YouTubeUnsynced }
                val duration = if (needsTrackMetadata) awaitDuration(durationProvider) else 0L

                val album = if (needsTrackMetadata) {
                    mediaMetadata.albumTitle?.toString()
                        ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                } else {
                    null
                }
                var found = false
                for ((index, lookup) in lookups.withIndex()) {
                    val lyrics = lookupLyrics(
                        lookup, lyricsType, mediaId, artistName.orEmpty(), title.orEmpty(), album, duration
                    )
                    currentCoroutineContext().ensureActive()
                    attempted.add(lookup)
                    // A line-only BetterLyrics response is useful in Auto, but should not replace a
                    // Synced row that was not due for a refresh.
                    val keepCachedSync = lyricsType == LyricsType.Auto && lookup == Lookup.BetterKaraoke &&
                        lyrics?.type == LyricsType.Synced.name && !needs.needSyncedFetch &&
                        allLyrics.any { it.type == LyricsType.Synced.name && !it.data.isNullOrBlank() }
                    found = lyrics != null && (keepCachedSync || saveLyricsSafe(lyrics))
                    if (found || lookups.getOrNull(index + 1)?.type != lookup.type) {
                        markAttempted(mediaId, lookup.type)
                    }
                    withContext(uiDispatcher) {
                        when (lookup.provider) {
                            LyricsProvider.BetterLyrics, LyricsProvider.LrcLib -> onCheckedLrcUpdated(true)
                            LyricsProvider.KuGou -> onCheckedKugouUpdated(true)
                            LyricsProvider.YouTube -> onCheckedInnertubeUpdated(true)
                        }
                        if (playerEnableLyricsPopupMessage) {
                            val labelRes = if (lookup == Lookup.BetterKaraoke && lyrics?.type == LyricsType.Synced.name) {
                                Lookup.BetterSynced.label
                            } else {
                                lookup.label
                            }
                            val label = context.getString(labelRes, context.getString(lookup.brand))
                            if (found) {
                                Toaster.s(R.string.lyrics_provider_found_format, label)
                            } else {
                                Toaster.e(R.string.lyrics_provider_missing_format, label, duration = Toast.LENGTH_LONG)
                            }
                        }
                    }
                    if (found) {
                        if (lookup == Lookup.BetterKaraoke && lyrics?.type == LyricsType.Synced.name) {
                            markAttempted(mediaId, LyricsType.Synced)
                        }
                        // A successful lookup ends this cascade. Suppress its remaining candidates
                        // for this observation without marking them fetched for future sessions.
                        attempted.addAll(lookups)
                        break
                    }
                }
                withContext(uiDispatcher) {
                    onErrorUpdated(!found && cached == null)
                    if (!found) {
                        onCheckedLrcUpdated(true)
                        onCheckedKugouUpdated(true)
                        onCheckedInnertubeUpdated(true)
                    }
                }
            } finally {
                withContext(uiDispatcher) { onFetchingStateChanged(false) }
            }
        }
    }

    private fun preferredCache(mode: LyricsType, current: Lyrics?, rows: List<Lyrics>): Lyrics? {
        if (current == null || mode != LyricsType.Auto || current.type != LyricsType.Karaoke.name || current.isEdited ||
            hasWordTimings(current.data.orEmpty())
        ) return current
        // Older Auto runs could store a BetterLyrics line-only result in the Karaoke row.
        return rows.find { it.type == LyricsType.Synced.name && it.isEdited && !it.data.isNullOrBlank() }
            ?: current
    }

    private fun lookupsFor(type: LyricsType, needs: FetchNeeds): List<Lookup> = buildList {
        if ((type == LyricsType.Auto || type == LyricsType.Karaoke) && needs.needKaraokeFetch) {
            add(Lookup.BetterKaraoke)
        }
        if (needs.needSyncedFetch) {
            if (type == LyricsType.Synced) add(Lookup.BetterSynced)
            add(Lookup.LrcSynced)
            add(Lookup.KuGouSynced)
        }
        if (needs.needUnsyncedFetch) {
            add(Lookup.LrcUnsynced)
            add(Lookup.YouTubeUnsynced)
        }
    }

    private fun markAttempted(mediaId: String, type: LyricsType) {
        when (type) {
            LyricsType.Karaoke -> globalLastKaraokeAttemptMediaId = mediaId
            LyricsType.Synced -> globalLastSyncedAttemptMediaId = mediaId
            LyricsType.Unsynced -> globalLastUnSyncedAttemptMediaId = mediaId
            LyricsType.Auto -> Unit
        }
    }

    private suspend fun lookupLyrics(
        lookup: Lookup,
        mode: LyricsType,
        mediaId: String,
        artist: String,
        title: String,
        album: String?,
        duration: Long
    ): Lyrics? {
        val text = try {
            when (lookup) {
                Lookup.BetterKaraoke, Lookup.BetterSynced -> fetchBetterLyrics(
                    artist, title, album, duration, synced = lookup == Lookup.BetterSynced
                )
                Lookup.LrcSynced -> lrcLibLyrics(artist, title, duration.milliseconds, album)?.getOrThrow()?.text
                Lookup.KuGouSynced -> if (duration > 0) {
                    kuGouLyrics(artist, cleanPrefix(title), duration / 1000)?.getOrThrow()?.value
                } else {
                    null
                }
                Lookup.LrcUnsynced -> lrcLibLyricsUnsynced(artist, title, duration.milliseconds, album)
                    ?.getOrThrow()?.let { lrc -> lrc.plainText.ifBlank { lrc.text } }
                Lookup.YouTubeUnsynced -> innertubeLyrics(mediaId)?.getOrThrow()
            }
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Timber.tag(TAG).w(timeout, "%s lookup timed out", lookup)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Timber.tag(TAG).w(failure, "%s lookup failed", lookup)
            null
        }
        if (text.isNullOrBlank()) return null
        if (lookup.type != LyricsType.Unsynced) {
            val hasTimedLyrics = withContext(mediaDispatcher) {
                parseSyncedSentences(text).any { it.text.isNotBlank() }
            }
            if (!hasTimedLyrics) return null
        }
        val type = if (lookup == Lookup.BetterKaraoke && mode == LyricsType.Auto && !hasWordTimings(text)) {
            LyricsType.Synced
        } else {
            lookup.type
        }
        return Lyrics(songId = mediaId, type = type.name, data = text)
    }

    private fun hasWordTimings(text: String): Boolean =
        text.lineSequence().any { it.trimStart().startsWith("<") && ':' in it && '>' in it }

    private suspend fun awaitDuration(readDuration: () -> Long): Long =
        withTimeoutOrNull(DURATION_WAIT_MILLIS) {
            var duration = withContext(uiDispatcher) { readDuration() }
            while (duration == C.TIME_UNSET) {
                delay(100)
                duration = withContext(uiDispatcher) { readDuration() }
            }
            duration
        } ?: 0L

    private suspend fun fetchBetterLyrics(
        artist: String,
        title: String,
        album: String?,
        duration: Long,
        synced: Boolean
    ): String? {
        val ttml = BetterLyrics.fetchTTML(
            artist = artist,
            title = cleanPrefix(title),
            duration = duration.milliseconds.inWholeSeconds.toInt(),
            album = album
        ) ?: return null
        return withContext(mediaDispatcher) {
            val lines = TTMLParser.parseTTML(ttml)
            val output = if (synced) {
                lines.filterNot { it.isBackground }.map {
                    it.copy(words = emptyList(), agent = null, backgroundLines = emptyList())
                }
            } else {
                lines
            }
            TTMLParser.toLRC(output)
        }
    }

    /** Recheck inside the write transaction so a concurrent manual edit always wins. */
    private suspend fun upsertUnlessEdited(lyrics: Lyrics) = Database.transaction {
        val stored = lyricsTable.findBySongIdAndType(lyrics.songId, lyrics.type).firstOrNull()
        if (stored?.isEdited == true && !stored.data.isNullOrEmpty()) return@transaction
        lyricsTable.upsert(
            Lyrics(
                songId = lyrics.songId,
                type = lyrics.type,
                data = lyrics.data,
                lastFetchedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun saveLyricsSafe(lyrics: Lyrics): Boolean = try {
        upsertUnlessEdited(lyrics)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (constraint: SQLiteConstraintException) {
        Timber.tag(TAG).w(constraint, "Lyrics song row not ready, retrying in five seconds")
        delay(5000)
        try {
            upsertUnlessEdited(lyrics)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Timber.tag(TAG).e(failure, "Could not save lyrics after retry")
            false
        }
    } catch (failure: Exception) {
        Timber.tag(TAG).e(failure, "Could not save lyrics")
        false
    }
}
