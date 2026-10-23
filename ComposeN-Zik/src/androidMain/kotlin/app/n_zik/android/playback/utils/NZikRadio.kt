package app.n_zik.android.playback.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.PlayerServiceModern
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.enums.DislikeMode
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.nextPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.listenTogetherGuestLock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastMap
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.mediaItems
import timber.log.Timber

@OptIn(UnstableApi::class)
class NZikRadio(
    private val context: Context,
    private val binder: PlayerServiceModern.Binder,
    private val coroutineScope: CoroutineScope
) {
    var isRadioActive by mutableStateOf(false)
        private set

    private var radioJob: Job? = null

    /**
     * Test seam (issue #880): the dispatcher the fetch job runs on. Overridden with an
     * `UnconfinedTestDispatcher()` in `NZikRadioTest` so the fetch runs to completion
     * deterministically on the test thread instead of racing the real `NzikDispatchers.DATA`
     * pool (same pattern as [Shuffler.backgroundDispatcher]). No public API.
     */
    internal var fetchDispatcher: CoroutineDispatcher = NzikDispatchers.DATA

    private var reminderShown = false
    
    // Tracks if radio is currently fetching data (to prevent double-fetching)
    var isLoading by mutableStateOf(false)
        private set

    val isDiscoverEnabled: Boolean
        get() = context.preferences.getBoolean(discoverKey, false)

    val isAutoFillEnabled: Boolean
        get() = context.preferences.getBoolean(autoLoadSongsInQueueKey, true)

    val radioActionTextRes: Int
        get() = if (isRadioActive) R.string.stop_radio else R.string.start_radio


    /**
     * Toggles the discover filter ON/OFF and shows a toast.
     *
     * Guest lock (spec-listen-together-guest-lock-hardening): Discover is a stateful ON/OFF
     * control owned by the host room — a locked guest gets the shared blocked toast and no
     * state change (no preference write, no "Discover ON/OFF" toast).
     */
    fun toggleDiscover() {
        if (listenTogetherGuestLock.value) {
            ListenTogetherGuestGuardPlayer.reportUiBlockedOp(context)
            return
        }
        if (!isRadioActive && !isAutoFillEnabled) {
            Toaster.e(R.string.state_discover_error)
            return
        }

        val newState = !isDiscoverEnabled
        context.preferences.edit().putBoolean(discoverKey, newState).apply()
        
        if (newState) {
            Toaster.i(R.string.state_discover_on)
        } else {
            Toaster.i(R.string.state_discover_off)
        }
    }

    /**
     * Explicit Radio activation (Toggle)
     *
     * Guest lock (spec-listen-together-guest-lock-hardening): Radio is a stateful ON/OFF
     * control — a locked guest gets the shared blocked toast and nothing else: no
     * `isRadioActive` state change, no "Radio ON/OFF" toast, no `forcePlay`, no fetch/inject.
     * (An already-active radio stays inert while locked: its queue injections are blocked by
     * the central guard.)
     */
    fun startRadio(
        mediaItem: MediaItem,
        append: Boolean = false,
        endpoint: NavigationEndpoint.Endpoint.Watch? = null,
        isExplicit: Boolean = false
    ) {
        if (listenTogetherGuestLock.value) {
            ListenTogetherGuestGuardPlayer.reportUiBlockedOp(context)
            return
        }
        if (isExplicit) {
            // Issue #880 (scene 2): the "Stop radio" label is global on every entry point, so an
            // explicit tap while the radio is active stops it UNCONDITIONALLY on the first tap —
//
            // regardless of which song was tapped (the current one or another) and even when
            // `currentMediaItem` is null. The previous mediaId equality check only stopped the
//
            // radio for the current song; tapping "Stop radio" from any other song re-started the
//
            // radio on that song instead (two-tap stop, wrong queue).
            if (isRadioActive) {
                stopRadio(showToast = true)
                return
            }

            stopRadio(showToast = false) // Clean state before starting
            isRadioActive = true

            // Toast: Radio ON
            if (isDiscoverEnabled) {
                Toaster.i(R.string.state_radio_on_discover)
            } else {
                Toaster.i(R.string.state_radio_on_classic)
            }
        } else {
            // Implicit start (fetching songs silently for queue/autofill)
            stopRadio(showToast = false)
        }

        // Play song immediately
        if (binder.player.currentMediaItem?.mediaId != mediaItem.mediaId) {
            binder.player.forcePlay(mediaItem)
        }

        fetchAndInject(
            mediaItem.mediaId,
            endpoint?.playlistId,
            append,
            isExplicit,
            if (isExplicit) "explicit start" else "implicit start"
        )
    }

    fun stopRadio(showToast: Boolean = false) {
        // Guest lock (spec-listen-together-guest-lock-hardening): stopping the radio flips the
        // host-owned isRadioActive and cancels the radio job — a locked guest gets the shared
//
        // blocked toast and nothing else, same policy as startRadio().
        if (listenTogetherGuestLock.value) {
            ListenTogetherGuestGuardPlayer.reportUiBlockedOp(context)
            return
        }
        isLoading = false
        isRadioActive = false
        radioJob?.cancel()
        radioJob = null
        
        if (showToast) {
            Toaster.i(R.string.state_radio_off)
        }
    }

    /**
     * Silent Auto-Fill (When queue is about to end)
     */
    fun autoFillQueue() {
        // Guest lock (spec-listen-together-guest-lock-hardening): in a room the host owns the
        // queue. The auto-fill fetch+inject would only be no-op'd by the central guard (a
//
        // throttled "host controls playback" toast + a wasted Innertube fetch and DB insert on
        // every host-side track transition), so skip it entirely while locked.
        if (listenTogetherGuestLock.value) return
        // If neither Radio nor AutoFill is enabled, do nothing.
        if ((!isRadioActive && !isAutoFillEnabled) || isLoading) return
        
        val currentMediaItem = binder.player.currentMediaItem ?: return
        
        // AutoFill: Silent fetch (No toast)
        fetchAndInject(currentMediaItem.mediaId, null, append = true, source = "auto-fill")
    }

    fun showReminderIfNeeded() {
        if (!reminderShown) {
            reminderShown = true

            if (isRadioActive) return // No reminder needed if they explicitly started radio

            // Guest lock (spec-listen-together-guest-lock-hardening): the auto-correction below
            // mutates the host-owned Discover setting — no state change (and no toast) for a
//
            // locked guest, same policy as toggleDiscover().
            if (listenTogetherGuestLock.value) return

            if (isDiscoverEnabled) {
                if (isAutoFillEnabled) {
                    Toaster.i(R.string.state_reminder_discover)
                } else {
                    // Auto-correction: if Discover is ON but AutoFill is OFF and Radio is OFF
                    context.preferences.edit().putBoolean(discoverKey, false).apply()
                }
            }
        }
    }

    private fun fetchAndInject(
        videoId: String,
        initialPlaylistId: String?,
        append: Boolean,
        isExplicit: Boolean = false,
        source: String = "start"
    ) {
        radioJob = coroutineScope.launch(fetchDispatcher) {
            // F4 (offline audit, 2026-10-22): offline the fetch can only fail at DNS — skip the
            // wasted attempt (the player gate toasts the explicit start; the auto-fill retries online).
            if (!isNetworkConnected(context)) {
                Timber.tag("NZikRadio").d("Radio fetch skipped, no network (source=%s, videoId=%s)", source, videoId)
                return@launch
            }
            isLoading = true

            var playlistId = initialPlaylistId
            var mediaItems = emptyList<MediaItem>()

            // Dynamic Seed: Always fetch based on videoId rather than continuation
            if (playlistId == null) {
                // Try to get playlistId from next endpoint
                playlistId = Innertube.nextPage(videoId = videoId)
                    ?.getOrNull()?.itemsPage?.items?.firstOrNull()
                    ?.info?.endpoint?.playlistId
            }
                
            if (!playlistId.isNullOrBlank()) {
                Innertube.nextPage(videoId = videoId, playlistId = playlistId)?.getOrNull()?.let { page ->
                    mediaItems = page.itemsPage?.items?.map { it.asMediaItem } ?: emptyList()
                    // Fallback: If there's a continuation, we can grab it, but usually the first request just sets up the queue
                }
            } else {
                // FALLBACK: If YouTube doesn't return a playlistId, use related songs directly!
                Innertube.nextPage(videoId = videoId)?.getOrNull()?.let { page ->
                    mediaItems = page.itemsPage?.items?.map { it.asMediaItem } ?: emptyList()
                }
            }

            if (mediaItems.isEmpty()) {
                // Liveness guard: a stopRadio() cancel that lands after the fetch returned must
                // not let this stale job trace or reset isLoading — resetting the flag would
//
                // defeat the double-fetch guard of the job that replaces it (two concurrent
                // fetches could both inject).
                if (!coroutineContext.isActive) return@launch
                // Issue #880: a pre-filter empty fetch (Innertube error or legitimately empty
                // page) must be traced — the queue is left intact and nothing is injected. The
//
                // auto-fill retries at the next non-repeat track transition only while the
                // queue has >1 item (no transition, no retry, on an exhausted 1-item queue;
//
                // with the radio OFF the AutoFill setting must be enabled as well).
                traceEmptyFetch(videoId, playlistId, isExplicit, source, "the Innertube fetch came back empty")
                isLoading = false
                return@launch
            }

            // 2. Insert into DB to save history/cache
            Database.asyncTransaction {
                mediaItems.forEach { Database.insertIgnore(it) }
            }

            // 3. Filter Discover and Deduplicate
            val filteredItems = discoverFilter(mediaItems)

            if (filteredItems.isEmpty()) {
                // Liveness guard: same as the pre-filter branch — a stale canceled job must not
                // trace or reset isLoading after a stop.
                if (!coroutineContext.isActive) return@launch
                // Issue #880 (EMPTY_AFTER_FILTERS): the discover/dislike filters removed every
                // song — same trace policy as a pre-filter empty fetch: no trim, nothing
//
                // injected; toast only for explicit starts (a silent retry happens at the next
                // non-repeat track transition only while the queue has >1 item).
                traceEmptyFetch(videoId, playlistId, isExplicit, source, "the discover/dislike filters removed every song")
                isLoading = false
                return@launch
            }

            // Liveness guard: same as the empty branches — a stopRadio() cancel that lands after
            // the last suspension must not let this stale job inject into the queue.
            if (!coroutineContext.isActive) return@launch

            // 4. Inject into Player
            withContext(NzikDispatchers.UI) {
                injectIntoPlayer(filteredItems, append)
            }

            isLoading = false
        }
    }

    /**
     * Trace policy for an empty radio fetch (issue #880), shared by the pre-filter empty fetch
     * and the list emptied by the discover/dislike filters: a `Timber.tag("NZikRadio")` log
     * (fetch identity + path-aware radio-state claim), the queue left intact, and a toast ONLY
     * for an explicit start — implicit paths (quick picks, search) and [autoFillQueue] stay
     * silent so a repeated empty playlist doesn't spam a toast. A5a (offline audit, 2026-10-22):
     * the explicit-start toast is "No Connection" while offline, else `state_radio_fetch_error`;
     * an offline fetch is skipped up front in [fetchAndInject] (F4), so this offline branch only
     * covers a network drop mid-fetch (the fetch started online; the player may not have
     * errored yet). The auto-fill retry is
     * CONDITIONAL (next non-repeat track transition, queue >1 item; AutoFill setting when the
     * radio is OFF), hence no "the radio will retry" wording. The toast hops to the UI
     * dispatcher (Toaster is @MainThread; the fetch runs on the DATA pool).
     */
    private suspend fun traceEmptyFetch(videoId: String, playlistId: String?, isExplicit: Boolean, source: String, reason: String) {
        val radioOutcome = when (source) {
            "explicit start" ->
                "radio stays ON; auto-fill retries at the next non-repeat track transition only while the queue has >1 item"
            "auto-fill" ->
                "silent fetch (no toast), radio left as the caller set it; retries at the next non-repeat track transition only while the queue has >1 item"
            else ->
                "silent fetch (no toast), the implicit start stopped the radio first; a retry needs the AutoFill (auto-continue) setting enabled, the next non-repeat track transition, and the queue to have >1 item"
        }
        Timber.tag("NZikRadio").e(
            "Radio fetch is empty (videoId=%s, playlistId=%s, source=%s): %s — queue left intact, %s",
            videoId,
            playlistId,
            source,
            reason,
            radioOutcome
        )
        if (isExplicit) {
            // A5a (offline audit, 2026-10-22): offline -> "No Connection" (a network problem, not
            // a radio fetch problem). Toaster is @MainThread, so the toast hops to the UI dispatcher.
            withContext(NzikDispatchers.UI) {
                if (isRadioFetchNetworkFailure(context)) Toaster.noInternet()
                else Toaster.e(R.string.state_radio_fetch_error)
            }
        }
    }

    private suspend fun discoverFilter(items: List<MediaItem>): List<MediaItem> {
        // Any call to player must happen on Main thread
        val currentQueueIds = withContext(NzikDispatchers.UI) {
            binder.player.mediaItems.fastMap { it.mediaId }
        }

        // Deduplicate
        var filtered = items.filter { it.mediaId !in currentQueueIds }

        // Apply Discover Filter (remove known songs)
        if (isDiscoverEnabled) {
            filtered = filtered.filter { item ->
                val isMapped = Database.songPlaylistMapTable.isMappedSync(item.mediaId)
                val isLiked = Database.songTable.isLikedDirect(item.mediaId)
                !(isMapped && isLiked) // Keep if NOT (mapped AND liked)
            }
        }

        // Apply Dislike Filter
        val preferences = context.preferences
        val excludeDislikedSongs = preferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedSongs.isEnabled) {
            val dislikedSongIds = Database.songTable.getAllDislikedIds()
            if (dislikedSongIds.isNotEmpty()) {
                filtered = filtered.filter { !dislikedSongIds.contains(it.mediaId) }
            }
        }

        val excludeDislikedArtists = preferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedArtists.isEnabled) {
            val dislikedArtistSongIds = Database.songTable.getSongsByDislikedArtists()
            if (dislikedArtistSongIds.isNotEmpty()) {
                filtered = filtered.filter { !dislikedArtistSongIds.contains(it.mediaId) }
            }
        }

        val excludeDislikedAlbums = preferences.getString(excludeDislikedAlbumsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedAlbums.isEnabled) {
            val dislikedAlbumSongIds = Database.songTable.getSongsByDislikedAlbums()
            if (dislikedAlbumSongIds.isNotEmpty()) {
                filtered = filtered.filter { !dislikedAlbumSongIds.contains(it.mediaId) }
            }
        }

        return Shuffler.shuffle(filtered)
    }

    @MainThread
    private fun injectIntoPlayer(items: List<MediaItem>, append: Boolean) {
        // Issue #880: nothing to inject -> the remaining queue must be left intact (no trim, no
        // injection). The trace (Timber log, toast for explicit starts) happens in
//
        // fetchAndInject before this call.
        if (items.isEmpty()) return

        val player = binder.player
        val curIndex = player.currentMediaItemIndex
        val endIndex = player.mediaItemCount

        if (!append && player.mediaItemCount > 1) {
            // Replace remaining queue (issue #880 scene 1): move the current song to the front,
            // then remove everything AFTER it. The previous `removeMediaItems(curIndex + 1,
//
            // endIndex)` left the list songs BEFORE the current song in the queue, so they
            // played before the radio.
            player.moveMediaItem(curIndex, 0)
            player.removeMediaItems(1, endIndex)
        }

        player.addMediaItems(items)
    }
}

/** A5a (offline audit, 2026-10-22): true when an empty explicit radio fetch is a network problem (a fresh check says offline) — the caller toasts "No Connection", not the fetch error. */
internal fun isRadioFetchNetworkFailure(context: Context): Boolean = !isNetworkConnected(context)
