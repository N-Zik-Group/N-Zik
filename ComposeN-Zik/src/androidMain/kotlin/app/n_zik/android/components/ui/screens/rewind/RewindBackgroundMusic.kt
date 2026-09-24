package app.n_zik.android.components.ui.screens.rewind

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.ExternalUris
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.createBackgroundMusicDataSourceFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import it.fast4x.innertube.YtMusic
import java.util.Random
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

// ── Frozen contract constants (spec "Deck Rewind — musique de fond par card") ──────

/** Session pool cap: no repetition while the pool is not exhausted (spec: cap 30). */
const val REWIND_BGM_POOL_CAP = 30

/** A settle less than this long after the previous one is skipped — no new track. */
const val REWIND_BGM_SETTLE_DEBOUNCE_MS = 800L

/** Downloaded tracks never start less than this far from the end (the track may not die
 *  a few seconds after it starts). */
const val REWIND_BGM_MIN_REMAINING_MS = 20_000L

/** Fade out/in duration of the A/B swap when the standby was READY at settle time
 *  (v4: 400 ms — a softer, fully felt transition; sqrt ramp like the crossfade). */
const val REWIND_BGM_FADE_MS = 400L

/** A/B swap fade when the standby only became ready AFTER the settle (the current track
 *  kept playing while the new one loaded): longer, so the hand-over between two tracks
 *  the user heard apart is a soft crossfade, not a switch (v5: "si pas cached il coupe"). */
const val REWIND_BGM_DELAYED_FADE_MS = 700L

/** Yield/mute state fades: the main player starts/stops, or the mute button flips. */
private const val REWIND_BGM_STATE_FADE_MS = 300L

/** The deck-exit fade-out before the players are released (no hard cut on back). */
private const val REWIND_BGM_RELEASE_FADE_MS = 400L

private const val REWIND_BGM_TAG = "RewindBackgroundMusic"
private const val REWIND_BGM_STATE_FADE_STEPS = 10
private const val REWIND_BGM_RELEASE_FADE_STEPS = 8

/**
 * The page the pager is heading to while a scroll is in progress, from the pager's
 * [androidx.compose.foundation.pager.PagerState.currentPageOffsetFraction]: negative in
 * the first half of a forward scroll (the current page is dragged toward the layout start),
 * positive in the first half of a backward scroll — and after the page flips mid-scroll,
 * a positive offset means the (new) [page] is the landing page. An offset still inside the
 * dead zone (swipe just started) defaults to the deck's forward flow. Clamping to the deck
 * range happens at the call site. (v5: the standby preloads the target as soon as the
 * swipe starts — "il precharge pas le prochain a l'avance".)
 */
fun scrollTargetPage(page: Int, offset: Float, deadZone: Float = 0.05f): Int = when {
    offset < -deadZone -> page + 1 // first half of a forward scroll
    offset > deadZone -> page // second half of a forward scroll (the landing page) or first half of a backward one (the settle path re-targets)
    else -> page + 1 // just started: the deck flows forward
}

/** The page the standby must prepare at the fade tail: a target requested mid-fade
 *  (queued by the fade guard) wins over the default next card. */
fun fadeTailPreparePage(deferredPage: Int?, defaultNextPage: Int): Int =
    deferredPage ?: defaultNextPage

/**
 * Builds the session background-music pool from the deck window's top songs (the same source
 * as the "Top songs" slide — spec: no extra fetch): distinct by song id, blank ids dropped,
 * downloaded songs first (whole file guaranteed locally — instant start + mid-track offset),
 * each partition shuffled with [random] (seed it for tests), capped at [cap].
 */
fun buildBackgroundPool(
    songs: List<Song>,
    isDownloaded: (String) -> Boolean,
    cap: Int = REWIND_BGM_POOL_CAP,
    random: Random = Random()
): List<Song> {
    val distinct = songs.distinctBy { it.id }.filter { it.id.isNotBlank() }
    val downloaded = distinct.filter { isDownloaded(it.id) }.shuffled(random)
    val streamed = distinct.filter { !isDownloaded(it.id) }.shuffled(random)
    return (downloaded + streamed).take(cap)
}

/** Card P → pool[P mod pool.size] (cyclic; null on an empty pool = silence). */
fun trackForPage(pool: List<Song>, page: Int): Song? =
    if (pool.isEmpty()) null else pool[page.mod(pool.size)]

/**
 * Per-card background-music content (spec 3 content mapping): the card that displays a
 * specific song, artist or album gets a track tied to that content; every other card
 * rolls from the session pool. The deck page order is the one of [RewindScreen]'s pager.
 */
sealed interface BgmContentTrack {
    /** Play [songId] directly — the deck data already holds the song. */
    data class SongTrack(val songId: String) : BgmContentTrack

    /** Fetch [albumId]'s track list (one metadata call, no playback tracking) and play its first track. */
    data class AlbumTrack(val albumId: String) : BgmContentTrack

    /** No content to tie to: roll from the pool. */
    data object None : BgmContentTrack
}

/**
 * Card → content track. The track plays from what the card's stats display (spec v4:
 * "les musiques ne matchent pas à ce que rewind met en stats"):
 *
 * - the **important single-item cards play their item — always the period's #1**: the
 *   Top song card and the finale (bookend), the artist spotlight (the #1 artist's
 *   most-played song), the Top album (the #1 album's first track);
 * - the **list cards roll a random item from the five they display** (top artists,
 *   top songs, deep cuts, albums) — the track stays inside the card's stats with
 *   variety between visits;
 * - stats-only cards return [BgmContentTrack.None] (pool roll).
 *
 * The rolls are per-resolution: revisiting a list card rolls a fresh item from its
 * displayed list (still matching the card). [random] is seeded in tests.
 */
fun contentTrackForPage(
    data: RewindData,
    page: Int,
    random: Random = Random()
): BgmContentTrack = when (page) {
    // The important single-item cards: always the period's #1
    3 -> songTrack(data.topSongs.getOrNull(0))
    5 -> songTrackId(artistTopSongId(data.topSongs, data.topArtists.getOrNull(0)?.artist))
    11 -> albumTrack(data.topAlbums.getOrNull(0))
    15 -> songTrack(data.topSongs.getOrNull(0))
    // The list cards: a random one of the five items they display
    4 -> {
        val candidates = data.topArtists
            .take(5)
            .mapNotNull { artist -> artistTopSongId(data.topSongs, artist.artist) }
        songTrackId(candidates.roll(random))
    }
    6 -> songTrack(data.topSongs.take(5).roll(random))
    // Deep cuts: the card lists topSongs.drop(5).take(5)
    7 -> songTrack(data.topSongs.drop(5).take(5).roll(random))
    12 -> albumTrack(data.topAlbums.take(5).roll(random))
    else -> BgmContentTrack.None
}

/** A random element of the list (its [java.util.Random], consistent with the pool), or null when empty. */
private fun <T> List<T>.roll(random: Random): T? = if (isEmpty()) null else get(random.nextInt(size))

private fun songTrack(entry: TopSong?): BgmContentTrack =
    entry?.song?.id?.takeIf { it.isNotBlank() }?.let { BgmContentTrack.SongTrack(it) } ?: BgmContentTrack.None

private fun songTrackId(id: String?): BgmContentTrack =
    id?.takeIf { it.isNotBlank() }?.let { BgmContentTrack.SongTrack(it) } ?: BgmContentTrack.None

private fun albumTrack(entry: TopAlbum?): BgmContentTrack =
    entry?.album?.id?.takeIf { it.isNotBlank() }?.let { BgmContentTrack.AlbumTrack(it) } ?: BgmContentTrack.None

/**
 * [artist]'s most-played song, matched locally against the top songs (no network).
 * User-renamed ("modified") artists carry the prefix on the name — stripped on both
 * sides so a renamed artist still matches its songs.
 */
private fun artistTopSongId(topSongs: List<TopSong>, artist: Artist?): String? {
    val name = artist?.name?.removePrefix(MODIFIED_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    return topSongs.firstOrNull { entry ->
        entry.song.artistsText
            ?.split(',')
            ?.map { it.trim().removePrefix(MODIFIED_PREFIX) }
            ?.any { it.equals(name, ignoreCase = true) } == true
    }?.song?.id?.takeIf { it.isNotBlank() }
}

/**
 * The first track of [albumId]'s track list. **Local first**: if the app already fetched
 * this album's tracklist (streaming/shuffle — `SongAlbumMap`), the mapping is in the DB
 * and the resolution costs zero network. Otherwise one album-page fetch through the app's
 * standard [YtMusic] API (metadata only: the hidden background players never register
 * playback, so the deck's stats stay untouched). Null on failure or an empty album:
 * the controller falls back to the session pool.
 */
suspend fun albumTrackForBgm(albumId: String): String? = withContext(NzikDispatchers.DATA) {
    Database.songAlbumMapTable.allSongsOfDirect(albumId, limit = 1).firstOrNull()?.id
        ?: YtMusic.getAlbum(albumId.removePrefix(MODIFIED_PREFIX)).getOrNull()
            ?.songs?.firstOrNull()?.info?.endpoint?.videoId?.removePrefix(MODIFIED_PREFIX)
}

/** A settle [elapsedSinceLastSettleMs] after the previous one switches tracks only past the debounce. */
fun shouldSwitchTrack(
    elapsedSinceLastSettleMs: Long,
    debounceMs: Long = REWIND_BGM_SETTLE_DEBOUNCE_MS
): Boolean = elapsedSinceLastSettleMs >= debounceMs

/**
 * Start offset of a swap, computed once the duration is known from the prepared standby
 * (spec: zero added latency). Downloaded track (whole file guaranteed): mean of two uniform
 * draws — a triangular distribution peaking at the middle — clamped so the track never
 * starts less than [minRemainingMs] before the end; unknown or too-short duration → 0.
 * Streamed track: always 0 (the stream cache's available ranges are unknown — no probing;
 * the beginning is the most likely already-cached part, and the preload absorbs the rest).
 */
fun startOffset(
    durationMs: Long,
    isDownloaded: Boolean,
    random: Random,
    minRemainingMs: Long = REWIND_BGM_MIN_REMAINING_MS
): Long {
    if (!isDownloaded) return 0L
    if (durationMs <= minRemainingMs) return 0L
    val hi = durationMs - minRemainingMs
    val a = random.nextLong().and(Long.MAX_VALUE) % (hi + 1)
    val b = random.nextLong().and(Long.MAX_VALUE) % (hi + 1)
    return ((a + b) / 2).coerceIn(0L, hi)
}

/**
 * Yield decision against the main player (spec: "si l'utilisateur joue de la musique dans le
 * player principal → la fond se pause"). Pure, so the state machine is unit-testable without
 * any player.
 */
sealed interface BgmYieldDecision {
    /** The main player started: both background players pause (position kept). */
    data object PauseAll : BgmYieldDecision

    /** The main player stopped: the active background player resumes. */
    data object ResumeActive : BgmYieldDecision

    /** No state change. */
    data object None : BgmYieldDecision
}

fun bgmYieldDecision(wasMainPlaying: Boolean, isMainPlaying: Boolean): BgmYieldDecision = when {
    !wasMainPlaying && isMainPlaying -> BgmYieldDecision.PauseAll
    wasMainPlaying && !isMainPlaying -> BgmYieldDecision.ResumeActive
    else -> BgmYieldDecision.None
}

/**
 * The BGM-dedicated media-source chain
 * ([PlayerServiceModern.createBackgroundMusicDataSourceFactory]): resolving source on the
 * okHttp upstream, then the stream LRU cache, then the download cache — the same
 * cache-first behavior as the main player, parental-control gate included.
 *
 * Unlike the standard factory, it launches **no metadata maintenance** (no
 * `upsertSongInfo` artist/album pre-caching, no `fetchFormatIfMissing`): the hidden
 * background players only read the music — they never feed the library caches, so the
 * deck's stats stay untouched (spec v4: "c'est vraiment que lire la musique").
 */
fun backgroundMusicDataSourceFactory(binder: PlayerServiceModern.Binder): DataSource.Factory =
    binder.service.createBackgroundMusicDataSourceFactory()

/**
 * Controller of the Rewind deck's background music (spec 3): two hidden [ExoPlayer]s in an
 * A/B configuration — the active one plays the current card's track, the standby preloads
 * the next card's track while the current card is on screen — so the per-card swap never
 * goes silent. Each card's track comes from the deck's content ([contentTrackForPage] —
 * the card's own song, artist or album) with the session pool as fallback. No notification,
 * no MediaSession, no playback registration (the deck's stats stay untouched); audio focus
 * is handled by the players themselves (phone call = automatic pause, resume on focus gain).
 * [release] must be called when the deck leaves the composition (no audio, no leak after exit).
 */
class RewindBackgroundMusicController(
    private val context: Context,
    private val dataSourceFactory: DataSource.Factory,
    private val isDownloaded: (String) -> Boolean,
    private val random: Random = Random()
) {
    private val scope = CoroutineScope(
        NzikDispatchers.UI + SupervisorJob() + CoroutineName("RewindBgm")
    )

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var activeIsA = true

    private var pool: List<Song> = emptyList()
    /** Card → content track id (null → pool roll); null before [start]. */
    private var contentTrack: (suspend (Int) -> String?)? = null
    /** Cards whose content track failed once: they roll from the pool for the deck (no retry loop). */
    private val failedContentPages = mutableSetOf<Int>()
    private var startedPeriod: Any? = null
    private var started = false

    /** Last settled card; -1 until [start]. */
    private var currentPage = -1
    /** Last settle time (valid or skipped — the debounce measures against every settle). */
    private var lastSettleAtMs = 0L
    /** The card whose track the standby is preparing/holding, or null while re-preparing. */
    private var standbyPreparedPage: Int? = null
    /** The card + track the standby actually holds (set right before the player's prepare). */
    private var standbyHold: StandbyHold? = null
    /** The in-flight track resolution (cancelled when the deck moves on). */
    private var standbyPrepareJob: Job? = null
    /** The card waiting for its standby replacement (standby-miss path), or null. */
    private var pendingSwapPage: Int? = null
    /** The card whose track the active player is playing (guard against same-card re-seeks). */
    private var activeTrackPage: Int? = null
    private var fadeJob: Job? = null
    /** The in-flight yield/mute state fade (v4: no hard cut on pause/resume/mute). */
    private var yieldJob: Job? = null
    /**
     * A standby prepare requested while the swap fade is still running (v5): queued here
     * and run at the fade tail — a `setMediaItem` mid-fade would kill the outgoing track's
     * audio (a hard cut), so the standby must stay untouched until the fade is over.
     */
    private var deferredPreparePage: Int? = null

    private var muted = false
    private var targetVolume = 0.7f
    private var mainPlayerPlaying = false

    private val active: ExoPlayer? get() = if (activeIsA) playerA else playerB
    private val standby: ExoPlayer? get() = if (activeIsA) playerB else playerA

    /**
     * Starts (or resumes) the background music for [period]'s deck. [topSongs] builds the
     * session pool (the same source as the Top songs slide — no extra fetch); [contentTrack]
     * maps each card to its content track and returns null for the cards that roll from the
     * pool. Idempotent for the same period (a mid-deck re-enable of the toggle just resumes,
     * the resolver refreshed against the deck data); a new period or a first call rebuilds
     * the pool and routes the first card through the standby like any other swap — the
     * fade-in from silence replaces a hard start, and the start offset is applied once the
     * duration is known. An empty window stays silent (no player, no crash).
     */
    fun start(
        topSongs: List<Song>,
        contentTrack: suspend (Int) -> String?,
        period: Any
    ) {
        if (topSongs.isEmpty()) {
            Timber.tag(REWIND_BGM_TAG).i("Empty top-song pool: background music stays silent")
            return
        }
        this.contentTrack = contentTrack
        if (started && startedPeriod == period) {
            syncPlayback()
            return
        }
        pool = buildBackgroundPool(topSongs, isDownloaded, random = random)
        startedPeriod = period
        if (playerA == null) createPlayers() else resetPlayers()
        failedContentPages.clear()
        currentPage = 0
        lastSettleAtMs = SystemClock.elapsedRealtime()
        started = true
        pendingSwapPage = 0
        prepareStandby(0)
    }

    /**
     * The deck started scrolling toward [targetPage] (before the settle): the standby
     * prepares that page's track right away, so by the time the card settles the track is
     * usually already READY and the swap is a true crossfade — even for a non-cached
     * stream (v5: "il precharge pas le prochain a l'avance"). A no-op when the standby
     * already holds that page (the settle path or the fade tail prepared it).
     */
    fun onScrollStarted(targetPage: Int) {
        if (!started) return
        prepareStandby(targetPage)
    }

    /**
     * Called by the deck on every pager settle (valid or skipped). A settle inside the
     * debounce window is skipped — no new track for that card, the current one continues
     * (spec: "si on change trop vite il lit pas"). Otherwise: the standby already holds
     * track(P) → fade swap now; standby miss (flick landed on an unpreloaded card) → the
     * standby prepares track(P) and the current track keeps playing until the replacement
     * is ready (never silent; the swap is then faded with the longer delayed fade).
     */
    fun onPageSettled(page: Int) {
        if (!started) return
        if (activeTrackPage == page) return // this card's track is already the active one
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (lastSettleAtMs == 0L) Long.MAX_VALUE else now - lastSettleAtMs
        lastSettleAtMs = now
        currentPage = page
        if (!shouldSwitchTrack(elapsed)) {
            Timber.tag(REWIND_BGM_TAG).d("Settle on card $page skipped (debounce) — current track continues")
            return
        }
        val standby = standby ?: return
        // The standby must be READY *and* actually holding this page's track —
        // standbyPreparedPage alone would also match an in-flight (async) resolution
        if (standby.playbackState == Player.STATE_READY && standbyHold?.page == page) {
            doSwap(page)
        } else {
            Timber.tag(REWIND_BGM_TAG).d("Standby miss on card $page — preparing its track, current keeps playing")
            pendingSwapPage = page
            prepareStandby(page)
        }
    }

    /** The main player's public play state changed (observed by the deck). The pause and
     *  the resume are faded — no hard cut (spec v4: "laisse bien fondre"). */
    fun onMainPlayerPlayingChanged(isPlaying: Boolean) {
        val decision = bgmYieldDecision(mainPlayerPlaying, isPlaying)
        mainPlayerPlaying = isPlaying
        val player = active
        when (decision) {
            BgmYieldDecision.PauseAll -> {
                Timber.tag(REWIND_BGM_TAG).i("Yield: main player started — background fades out and pauses")
                if (player != null && player.playWhenReady) {
                    fadeVolume(player, 0f, REWIND_BGM_STATE_FADE_MS) { player.playWhenReady = false }
                }
            }
            BgmYieldDecision.ResumeActive -> {
                Timber.tag(REWIND_BGM_TAG).i("Yield released: main player stopped — background fades back in")
                if (player != null && !muted) {
                    player.playWhenReady = true
                    fadeVolume(player, currentVolume(), REWIND_BGM_STATE_FADE_MS)
                }
            }
            BgmYieldDecision.None -> Unit
        }
    }

    /** The deck mute button / settings toggle (same key on both sides). The state change
     *  is faded — no hard cut (spec v4). */
    fun setMuted(muted: Boolean) {
        if (this.muted == muted) return
        this.muted = muted
        val player = active
        if (player != null) {
            if (muted) {
                if (player.playWhenReady) {
                    fadeVolume(player, 0f, REWIND_BGM_STATE_FADE_MS) { player.playWhenReady = false }
                }
            } else if (!mainPlayerPlaying) {
                player.playWhenReady = true
                fadeVolume(player, currentVolume(), REWIND_BGM_STATE_FADE_MS)
            }
        }
    }

    /** The deck volume slider (0..1 = 0-100 persisted). Immediate effect on the players. */
    fun setVolume(volume0to1: Float) {
        targetVolume = volume0to1.coerceIn(0f, 1f)
        syncPlayback()
    }

    /**
     * Releases both hidden players and the controller scope. Idempotent. The audible
     * player fades out first — no hard cut when the deck leaves (spec v4: "laisse bien
     * fondre"); a silent deck (paused/muted/yielded) releases instantly.
     */
    fun release() {
        fadeJob?.cancel()
        fadeJob = null
        yieldJob?.cancel()
        yieldJob = null
        deferredPreparePage = null
        standbyPrepareJob?.cancel()
        standbyPrepareJob = null
        standbyPreparedPage = null
        standbyHold = null
        pendingSwapPage = null
        activeTrackPage = null
        pool = emptyList()
        contentTrack = null
        failedContentPages.clear()
        started = false
        startedPeriod = null
        scope.cancel()
        val outgoing = playerA
        val standbyPlayer = playerB
        playerA = null
        playerB = null
        val audible = outgoing?.takeIf { it.playWhenReady }
        if (audible == null) {
            outgoing?.release()
            standbyPlayer?.release()
        } else {
            val player = audible
            // Its own scope: the controller's scope is cancelled above, the fade must
            // outlive it
            CoroutineScope(
                NzikDispatchers.UI + SupervisorJob() + CoroutineName("RewindBgmRelease")
            ).launch {
                val stepMs = REWIND_BGM_RELEASE_FADE_MS / REWIND_BGM_RELEASE_FADE_STEPS
                for (i in 1..REWIND_BGM_RELEASE_FADE_STEPS) {
                    delay(stepMs)
                    player.volume = targetVolume * (1f - i / REWIND_BGM_RELEASE_FADE_STEPS.toFloat())
                }
                player.volume = 0f
                player.release()
                standbyPlayer?.release()
            }
        }
        Timber.tag(REWIND_BGM_TAG).d("Background music released (deck left)")
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────

    private fun createPlayers() {
        // Only the ACTIVE player handles audio focus (v6): a standby that requests focus
        // the moment it starts playing mid-swap would steal it from the outgoing player,
        // and ExoPlayer would pause the outgoing on AUDIOFOCUS_LOSS — the crossfade
        // becomes a hard cut. The hand-over happens at the fade tail of [doSwap].
        playerA = createPlayer(handleFocus = true) // the initial active
        playerB = createPlayer(handleFocus = false) // the initial standby
        activeIsA = true
    }

    private fun createPlayer(handleFocus: Boolean): ExoPlayer {
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Spec 3 (renegotiation v1): the active background player handles system
                // audio focus — a phone call pauses it automatically, focus gain resumes.
                // The standby is built without focus handling (v6 — see [createPlayers]):
                // only the player that is actually the active track handles focus;
                // [doSwap] hands the handling over at the fade tail. No ping-pong with
                // the main player: the background only plays while the main player is
                // idle (yield), and the app-level gate below is idempotent with the
                // system pause.
                handleFocus
            )
            .setUsePlatformDiagnostics(false)
            .build()
        // A card may stay on screen longer than its track: loop it instead of going
        // silent (the per-card swap still takes over on the next settled card)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(playerListener(player))
        return player
    }

    private fun resetPlayers() {
        fadeJob?.cancel()
        fadeJob = null
        deferredPreparePage = null
        standbyPreparedPage = null
        standbyHold = null
        standbyPrepareJob?.cancel()
        standbyPrepareJob = null
        pendingSwapPage = null
        activeTrackPage = null
        playerA?.stop()
        playerB?.stop()
    }

    private fun playerListener(player: ExoPlayer) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) standbyBecameReady(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (player === standby) standbyFailed(error) else activeFailed(error)
        }
    }

    /**
     * Card [page]'s track: the deck content first (song/artist/album pages), the session
     * pool as fallback. A page whose content track failed once rolls from the pool for
     * the whole deck (no retry loop on the same broken track).
     */
    private suspend fun resolveTrackForPage(page: Int): StandbyHold? {
        if (page !in failedContentPages) {
            val contentId = runCatching { contentTrack?.invoke(page) }.getOrNull()
            if (contentId != null && contentId.isNotBlank()) {
                return StandbyHold(page, contentId, fromContent = true)
            }
        }
        val fallback = trackForPage(pool, page) ?: return null
        return StandbyHold(page, fallback.id, fromContent = false)
    }

    /**
     * Preloads [page]'s track on the standby player (playWhenReady stays false). Track
     * resolution is async — album cards fetch their track list — so the job is cancelled
     * when the deck moves on and a stale resolution never touches the player.
     *
     * Fade guard (v5): while the swap fade is still running, the standby is the outgoing
     * player still fading out — a `setMediaItem` now would kill its audio (a hard cut).
     * The prepare is then queued in [deferredPreparePage] and run at the fade tail
     * ([fromFadeTail] skips the guard, the fade is over by then).
     */
    private fun prepareStandby(page: Int, fromFadeTail: Boolean = false) {
        val player = standby ?: return
        if (standbyPreparedPage == page) return // already preparing or holding this page's track
        if (!fromFadeTail && fadeJob?.isActive == true) {
            deferredPreparePage = page
            Timber.tag(REWIND_BGM_TAG).d("Standby prepare of card $page deferred — swap fade still running")
            return
        }
        standbyPreparedPage = page
        standbyPrepareJob?.cancel()
        standbyPrepareJob = scope.launch {
            val hold = resolveTrackForPage(page) ?: run {
                standbyPreparedPage = null
                return@launch
            }
            if (standbyPreparedPage != page) return@launch // the deck moved on — stale resolution
            player.setMediaItem(MediaItem.fromUri(ExternalUris.youtubeMusic(hold.trackId)))
            standbyHold = hold
            player.volume = 0f
            player.playWhenReady = false
            player.prepare()
        }
    }

    private fun standbyBecameReady(player: ExoPlayer) {
        if (player !== standby) return
        val page = pendingSwapPage ?: return
        // The standby must actually hold this page's prepared track (an async resolution
        // can leave a stale READY for an item that was already replaced)
        if (standbyHold?.page != page) return
        // The track only became ready AFTER the settle (the current track kept playing
        // while it loaded): use the longer fade so the hand-over is a soft crossfade
        // between two tracks the user heard apart, not a switch (v5)
        doSwap(page, fadeMs = REWIND_BGM_DELAYED_FADE_MS)
    }

    /**
     * Fades the active track out while the standby's track fades in (sqrt ramp — the light
     * adaptation of the main player's crossfade ramp). The roles swap immediately; the
     * outgoing player becomes the standby and preloads the next card once the fade is over
     * (the debounce guarantees no earlier valid settle). A target queued mid-fade
     * ([deferredPreparePage]) wins over the default next card at the tail.
     */
    private fun doSwap(page: Int, fadeMs: Long = REWIND_BGM_FADE_MS) {
        val incoming = standby ?: return
        val outgoing = active ?: return
        // The track is the one the standby actually prepared (content or pool) — not a
        // fresh pool roll, which could pick a different track than the prepared item
        val hold = standbyHold?.takeIf { it.page == page } ?: return
        pendingSwapPage = null
        activeTrackPage = page
        // The duration is known: the standby prepared the item before becoming ready
        val offset = startOffset(incoming.duration, isDownloaded(hold.trackId), random)
        incoming.seekTo(offset)
        incoming.volume = 0f
        activeIsA = !activeIsA
        incoming.playWhenReady = shouldPlay()
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = (fadeMs / 25L).coerceAtLeast(8L).toInt()
            val stepMs = fadeMs / steps
            for (i in 1..steps) {
                delay(stepMs)
                if (!isActive) break
                val progress = i / steps.toFloat()
                incoming.volume = currentVolume() * sqrt(progress)
                outgoing.volume = currentVolume() * sqrt(1f - progress)
            }
            incoming.volume = currentVolume()
            // The swap is over: hand the audio-focus handling to the new active player
            // (a phone call auto-pauses it from now on). It deliberately started playing
            // with focus handling OFF — if it had requested focus the moment it started,
            // the outgoing player would have received AUDIOFOCUS_LOSS and ExoPlayer would
            // have paused it, turning the crossfade into a hard cut (v6: "le fade marche
            // pas au swap"). The old active gives up its handling first (it keeps playing
            // until the explicit pause below — focus is abandoned, playback untouched),
            // then the new active takes over and requests the focus.
            outgoing.setAudioAttributes(outgoing.audioAttributes, false)
            incoming.setAudioAttributes(incoming.audioAttributes, true)
            outgoing.pause()
            outgoing.volume = 0f
            Timber.tag(REWIND_BGM_TAG).d("Swap done on card $page — focus handed to the new active player")
            standbyPreparedPage = null
            prepareStandby(fadeTailPreparePage(deferredPreparePage, page + 1), fromFadeTail = true)
            deferredPreparePage = null
        }
    }

    /** The standby track failed to resolve: the current track continues. A pool track is
     *  dropped (the same card re-rolls the next pool entry); a content track marks its page
     *  as failed for the deck, so the card re-rolls from the pool instead of retrying the
     *  same broken track. */
    private fun standbyFailed(error: PlaybackException) {
        val page = standbyPreparedPage ?: return
        val hold = standbyHold?.takeIf { it.page == page }
        Timber.tag(REWIND_BGM_TAG).w(error, "Standby track failed (card $page, ${hold?.trackId}) — current track continues, next taken from the pool")
        standbyPreparedPage = null
        standbyHold = null
        if (hold != null) {
            if (hold.fromContent) failedContentPages.add(page)
            else pool = pool.filter { it.id != hold.trackId }
        }
        if (pool.isEmpty()) {
            pendingSwapPage = null
            Timber.tag(REWIND_BGM_TAG).i("Pool exhausted after standby failure: background music silent")
            return
        }
        // The deck may have moved on while this load was in flight: re-target the standby
        // at the page it actually wants — never leave it idle while a swap is pending (v5)
        pendingSwapPage?.let { prepareStandby(it) }
    }

    /** The active track failed: the standby replaces it for the same card (never silent
     *  beyond the replace time; a same-track failure shrinks the pool via [standbyFailed]). */
    private fun activeFailed(error: PlaybackException) {
        Timber.tag(REWIND_BGM_TAG).w(error, "Active track failed — replacing from the standby for card $currentPage")
        val standby = standby ?: return
        // Same guard as onPageSettled: the standby must actually hold this card's track
        if (standby.playbackState == Player.STATE_READY && standbyHold?.page == currentPage) {
            doSwap(currentPage)
        } else {
            pendingSwapPage = currentPage
            prepareStandby(currentPage)
        }
    }

    private fun shouldPlay(): Boolean = started && !muted && !mainPlayerPlaying

    private fun currentVolume(): Float = if (muted) 0f else targetVolume

    private fun syncPlayback() {
        active?.playWhenReady = shouldPlay()
        active?.volume = currentVolume()
        standby?.playWhenReady = false
    }

    /**
     * Fades [player]'s volume from its current value to [to] over [ms] and runs [onEnd]
     * — the yield/mute transitions (spec v4: "laisse bien fondre"). A zero-length fade
     * applies the target instantly.
     */
    private fun fadeVolume(player: ExoPlayer, to: Float, ms: Long, onEnd: (() -> Unit)? = null) {
        yieldJob?.cancel()
        yieldJob = null
        if (ms <= 0L) {
            player.volume = to
            onEnd?.invoke()
            return
        }
        val from = player.volume
        yieldJob = scope.launch {
            val stepMs = ms / REWIND_BGM_STATE_FADE_STEPS
            for (i in 1..REWIND_BGM_STATE_FADE_STEPS) {
                delay(stepMs)
                if (!isActive) break
                player.volume = from + (to - from) * (i / REWIND_BGM_STATE_FADE_STEPS.toFloat())
            }
            player.volume = to
            onEnd?.invoke()
        }
    }

    /** The track the standby player holds for [page] ([fromContent]: content mapping vs pool roll). */
    private class StandbyHold(val page: Int, val trackId: String, val fromContent: Boolean)
}
