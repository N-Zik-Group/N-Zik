package app.n_zik.android.listentogether

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import timber.log.Timber

/**
 * Central guest-lock guard for Listen Together (spec-listen-together-guest-lock-hardening,
 * architecture spine AD-1..AD-7).
 *
 * Delegating [Player] facade: every external entry point (UI / menus / library via
 * `Binder.player`, notification buttons, MediaSession / lockscreen / automotive) reaches
 * the shared ExoPlayer ONLY through this facade. While [listenTogetherGuestLock] is on,
 * every operation that can desynchronize the room is a no-op (fail-closed: any operation
 * not explicitly in the "passing" list is blocked) plus a throttled "the host controls
 * playback" toast. Play/pause, volume and queries always pass through — a guest's play
 * resynchronizes them to the host (Spotify Jam semantics).
 *
 * Two extra guarantees while locked:
 *  - **Blocked-op tail suppression.** User helpers (`forcePlay`, `playAtIndex`, `playNext`, …)
 *    call a blocked load/skip op and then — still in the same synchronous chain — call the
 *    pass-through tail `prepare()` / `volume = …` / `playWhenReady = true`. That tail would
 *    resume a paused track after a blocked "play another track" attempt (user report
 *    2026-09-29: clicking a song resumed the paused track). A per-thread call sequence
 *    suppresses exactly the tail of each blocked chain; a real guest play/pause/volume tap
 *    only ever lands on a later event-loop iteration and is never swallowed.
 *  - **Guest play/pause intent.** [onGuestPlayPause] observes the guest's own play/pause
 *    taps (host sync and service internals mutate the raw player directly and never pass
 *    through this facade) so the sync manager can keep a locally-paused guest paused across
 *    host skips and resync the guest to the host position on play.
 *
 * The policy reads [listenTogetherGuestLock] on EVERY call (AD-3): no lock state is cached
 * in the instance, the wrapper is a pure function of (raw player, lock State). The host
 * sync path and the service's internal logic mutate the raw player and are NEVER guarded
 * (AD-4).
 */
@UnstableApi
class ListenTogetherGuestGuardPlayer(
    private val upstream: Player,
    private val appContext: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val toastSink: (String) -> Unit = { message -> Toaster.i(message) },
) : ForwardingPlayer(upstream) {

    companion object {
        private const val TAG = "LTGuestGuard"

        /** Minimum interval between "the host controls playback" toasts (spam-free on repeated taps). */
        const val TOAST_THROTTLE_MS = 2000L

        /**
         * Blocked-op tail suppression (see class KDoc). A blocked head op opens a window of
         * [TAIL_WINDOW_MS]: inside it, the pass-through state ops that user helpers chain after
         * the head (`prepare()` / `volume = …` / `playWhenReady = true`) are no-ops, so the
         * blocked op stays atomic. The window is deliberately tiny — the extension chains are
         * synchronous in-memory calls (microseconds) while any real user tap lands on a later
         * event-loop iteration, well beyond 50 ms (human reaction time is ~200 ms).
         */
        const val TAIL_WINDOW_MS = 50L

        /**
         * Guard op labels of the "head" operations that user helpers follow with the
         * pass-through tail `prepare()` / `volume = …` / `playWhenReady = true`
         * (`forcePlay`, `playAtMedia`, `playAtIndex`, `forcePlayAtIndex`, `playNext`,
         * `playPrevious`, `playVideo`, `forceSeekToNext/Previous`, …). Blocking one opens the
         * tail-suppression window; blocking a queue-add/remove/mode op does not (no play tail).
         */
        private val TAIL_HEAD_OPS = setOf(
            "setMediaItem",
            "setMediaItems",
            "seekTo",
            "seekToDefaultPosition",
            "seekBack",
            "seekForward",
            "seekToPrevious",
            "seekToPreviousMediaItem",
            "seekToNext",
            "seekToNextMediaItem",
        )

        /**
         * Shared "the host controls playback" toast for entry points blocked at the UI
         * layer (e.g. the player-sheet dismiss swipe) whose operation never reaches the
         * per-facade [guard] — same string as the guard, independently throttled (this companion
         * window is process-wide real-time; the guard's is per-facade and clock-injectable for
         * tests), so no blocked attempt is silent (spec-listen-together-guest-lock-hardening).
         */
        fun reportUiBlockedOp(appContext: Context) {
            val now = System.currentTimeMillis()
            val last = lastUiBlockedToastAtMs
            if (last != null && now - last < TOAST_THROTTLE_MS) return
            lastUiBlockedToastAtMs = now
            Toaster.i(appContext.getString(R.string.listen_together_guest_blocked))
            Timber.tag(TAG).d("blocked: UI-layer op (guest locked)")
        }

        /** Throttle state of [reportUiBlockedOp] (`null` = never toasted). */
        @Volatile
        private var lastUiBlockedToastAtMs: Long? = null

        /**
         * Test hook: [reportUiBlockedOp]'s throttle state is process-wide, so back-to-back unit
         * tests would otherwise share the 2 s window and the second blocked toast would be
         * swallowed nondeterministically.
         */
        internal fun clearUiBlockedOpThrottleForTests() {
            lastUiBlockedToastAtMs = null
        }

        /**
         * Player commands mapping to guest-blocked operations (AD-2). While the lock is on they
         * are removed from the available commands, so the next/prev/shuffle/stop buttons are
         * hidden from the notification, lockscreen and automotive (AD-6).
         */
        val GUEST_LOCKED_HIDDEN_COMMANDS = intArrayOf(
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_SET_PLAYLIST_METADATA,
            Player.COMMAND_SET_AUDIO_ATTRIBUTES,
            Player.COMMAND_SET_VIDEO_SURFACE,
        )
    }

    /**
     * Timestamp (per [clock]) of the last blocked-toast emission — throttle state only, never lock
     * state. `null` = never toasted, so the very first blocked op is never swallowed by the window.
     */
    @Volatile
    private var lastBlockedToastAtMs: Long? = null

    /**
     * End timestamp (per [clock]) of the blocked-op tail-suppression window. Set by
     * [armTailSuppression] when a "head" load/skip op is blocked; the pass-through state ops
     * (`play`/`pause`/`prepare`/`setVolume`/`setPlayWhenReady`) that user helpers chain after the
     * head are no-ops until then, so a blocked op stays atomic (see class KDoc). `0L` = no window.
     */
    @Volatile
    private var tailSuppressUntilMs: Long = 0L

    /**
     * Listeners registered on this facade (kept in parallel with the upstream registration) so
     * [announceAvailableCommandsChanged] can notify them when the guest lock toggles — MediaSession
     * only re-evaluates its available commands on that event (no re-polling in media3 1.10.1).
     */
    private val commandListeners = mutableListOf<Player.Listener>()

    // ── Available commands (AD-6: hide next/prev/shuffle/stop for guests) ─────────────────────

    override fun getAvailableCommands(): Player.Commands {
        // Same "all commands" behavior as the service's legacy forwarding player (pre-Android-13
        // compatibility), minus the commands mapping to guest-blocked operations. Commands are
        // removed one at a time (identical FlagSet result to removeAll) so the guest-lock policy
        // is unit-testable on the JVM, where Commands' android.util.SparseBooleanArray cannot run.
        var builder = upstream.getAvailableCommands().buildUpon().addAllCommands()
        if (listenTogetherGuestLock.value) {
            for (command in GUEST_LOCKED_HIDDEN_COMMANDS) {
                builder = builder.remove(command)
            }
        }
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        if (listenTogetherGuestLock.value && command in GUEST_LOCKED_HIDDEN_COMMANDS) return false
        return super.isCommandAvailable(command)
    }

    /**
     * Notifies every registered listener of the current available commands. The player service
     * calls this when the guest lock toggles so the MediaSession (lockscreen / automotive)
     * hides or restores the transport buttons.
     */
    fun announceAvailableCommandsChanged() {
        val commands = getAvailableCommands()
        synchronized(commandListeners) {
            for (listener in commandListeners) {
                runCatching { listener.onAvailableCommandsChanged(commands) }
                    .onFailure { Timber.tag(TAG).e(it, "Failed to announce available commands change") }
            }
        }
    }

    override fun addListener(listener: Player.Listener) {
        synchronized(commandListeners) {
            if (!commandListeners.contains(listener)) commandListeners.add(listener)
        }
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        synchronized(commandListeners) {
            commandListeners.remove(listener)
        }
        super.removeListener(listener)
    }

    // ── Guarded operations (AD-2 matrix) ─────────────────────────────────────────────────────
    // Passing while locked (inherited [ForwardingPlayer] delegation, never overridden):
    // mute/unmute, device volume (setDeviceVolume/increaseDeviceVolume/decreaseDeviceVolume/
    // setDeviceMuted — a guest's own output volume stays theirs, "volume stays available"),
    // every reader/query, addListener/removeListener, release, audioSessionId.
    // The play-state pass-throughs (play/pause/prepare/setVolume/setPlayWhenReady) ARE overridden
    // below — still passing while locked, but no-ops inside the blocked-op tail window
    // (see [inTailSuppression]) and the observation point for the guest's play/pause intent
    // (see [onGuestPlayPause]).

    // Play-state pass-throughs (tail-suppressed + guest play/pause intent)
    override fun play() {
        if (inTailSuppression()) {
            Timber.tag(TAG).d("suppressed tail: play (blocked-op window)")
            return
        }
        super.play()
        registerGuestPlayPause(true)
    }

    override fun pause() {
        if (inTailSuppression()) {
            Timber.tag(TAG).d("suppressed tail: pause (blocked-op window)")
            return
        }
        super.pause()
        registerGuestPlayPause(false)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (inTailSuppression()) {
            Timber.tag(TAG).d("suppressed tail: setPlayWhenReady (blocked-op window)")
            return
        }
        super.setPlayWhenReady(playWhenReady)
        registerGuestPlayPause(playWhenReady)
    }

    override fun prepare() {
        if (inTailSuppression()) {
            Timber.tag(TAG).d("suppressed tail: prepare (blocked-op window)")
            return
        }
        super.prepare()
    }

    override fun setVolume(volume: Float) {
        if (inTailSuppression()) {
            Timber.tag(TAG).d("suppressed tail: setVolume (blocked-op window)")
            return
        }
        super.setVolume(volume)
    }

    /**
     * The guest's own play/pause intent, observed at the facade while locked (host sync and
     * service internals mutate the raw player directly and never pass through this facade):
     * `true` on a guest PLAY tap, `false` on a guest PAUSE tap. The player service wires this to
     * the sync manager, which (1) keeps a locally-paused guest paused across host track changes
     * and (2) resyncs the guest to the host position on play (spec-listen-together-guest-lock-hardening).
     */
    var onGuestPlayPause: ((Boolean) -> Unit)? = null

    /**
     * Registers [playWhenReady] as the guest's own play/pause intent — only while locked and
     * outside the tail window (a suppressed tail call is not an intent).
     */
    private fun registerGuestPlayPause(playWhenReady: Boolean) {
        if (!listenTogetherGuestLock.value) return
        onGuestPlayPause?.invoke(playWhenReady)
    }

    // Queue
    override fun setMediaItems(mediaItems: List<MediaItem>) =
        guard("setMediaItems") { super.setMediaItems(mediaItems) }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) =
        guard("setMediaItems") { super.setMediaItems(mediaItems, resetPosition) }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) =
        guard("setMediaItems") { super.setMediaItems(mediaItems, startIndex, startPositionMs) }

    override fun setMediaItem(mediaItem: MediaItem) =
        guard("setMediaItem") { super.setMediaItem(mediaItem) }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) =
        guard("setMediaItem") { super.setMediaItem(mediaItem, startPositionMs) }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) =
        guard("setMediaItem") { super.setMediaItem(mediaItem, resetPosition) }

    override fun addMediaItem(mediaItem: MediaItem) =
        guard("addMediaItem") { super.addMediaItem(mediaItem) }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) =
        guard("addMediaItem") { super.addMediaItem(index, mediaItem) }

    override fun addMediaItems(mediaItems: List<MediaItem>) =
        guard("addMediaItems") { super.addMediaItems(mediaItems) }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) =
        guard("addMediaItems") { super.addMediaItems(index, mediaItems) }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) =
        guard("moveMediaItem") { super.moveMediaItem(currentIndex, newIndex) }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) =
        guard("moveMediaItems") { super.moveMediaItems(fromIndex, toIndex, newIndex) }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) =
        guard("replaceMediaItem") { super.replaceMediaItem(index, mediaItem) }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) =
        guard("replaceMediaItems") { super.replaceMediaItems(fromIndex, toIndex, mediaItems) }

    override fun removeMediaItem(index: Int) =
        guard("removeMediaItem") { super.removeMediaItem(index) }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) =
        guard("removeMediaItems") { super.removeMediaItems(fromIndex, toIndex) }

    override fun clearMediaItems() = guard("clearMediaItems") { super.clearMediaItems() }

    // Position / track
    override fun seekToDefaultPosition() =
        guard("seekToDefaultPosition") { super.seekToDefaultPosition() }

    override fun seekToDefaultPosition(mediaItemIndex: Int) =
        guard("seekToDefaultPosition") { super.seekToDefaultPosition(mediaItemIndex) }

    override fun seekTo(positionMs: Long) = guard("seekTo") { super.seekTo(positionMs) }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) =
        guard("seekTo") { super.seekTo(mediaItemIndex, positionMs) }

    override fun seekBack() = guard("seekBack") { super.seekBack() }

    override fun seekForward() = guard("seekForward") { super.seekForward() }

    override fun seekToPreviousMediaItem() =
        guard("seekToPreviousMediaItem") { super.seekToPreviousMediaItem() }

    override fun seekToPrevious() = guard("seekToPrevious") { super.seekToPrevious() }

    override fun seekToNextMediaItem() =
        guard("seekToNextMediaItem") { super.seekToNextMediaItem() }

    override fun seekToNext() = guard("seekToNext") { super.seekToNext() }

    // Mode / speed
    override fun setRepeatMode(repeatMode: Int) =
        guard("setRepeatMode") { super.setRepeatMode(repeatMode) }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) =
        guard("setShuffleModeEnabled") { super.setShuffleModeEnabled(shuffleModeEnabled) }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) =
        guard("setPlaybackParameters") { super.setPlaybackParameters(playbackParameters) }

    override fun setPlaybackSpeed(speed: Float) =
        guard("setPlaybackSpeed") { super.setPlaybackSpeed(speed) }

    // State
    override fun stop() = guard("stop") { super.stop() }

    // Fail-closed: every other mutation is blocked while the lock is on as well.
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) =
        guard("setPlaylistMetadata") { super.setPlaylistMetadata(mediaMetadata) }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) =
        guard("setTrackSelectionParameters") { super.setTrackSelectionParameters(parameters) }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) =
        guard("setAudioAttributes") { super.setAudioAttributes(audioAttributes, handleAudioFocus) }

    // Device volume (setDeviceVolume/increaseDeviceVolume/decreaseDeviceVolume/setDeviceMuted)
    // is intentionally NOT guarded: it is a pass-through like setVolume — a guest's own output
    // volume is theirs to control ("volume stays available"), and it never desynchronizes the
    // room (review finding: the hidden/blocked device-volume commands exceeded that scope).

    override fun setVideoSurface(surface: Surface?) =
        guard("setVideoSurface") { super.setVideoSurface(surface) }

    override fun clearVideoSurface() = guard("clearVideoSurface") { super.clearVideoSurface() }

    override fun clearVideoSurface(surface: Surface?) =
        guard("clearVideoSurface") { super.clearVideoSurface(surface) }

    override fun setVideoSurfaceHolder(holder: SurfaceHolder?) =
        guard("setVideoSurfaceHolder") { super.setVideoSurfaceHolder(holder) }

    override fun clearVideoSurfaceHolder(holder: SurfaceHolder?) =
        guard("clearVideoSurfaceHolder") { super.clearVideoSurfaceHolder(holder) }

    override fun setVideoSurfaceView(view: SurfaceView?) =
        guard("setVideoSurfaceView") { super.setVideoSurfaceView(view) }

    override fun clearVideoSurfaceView(view: SurfaceView?) =
        guard("clearVideoSurfaceView") { super.clearVideoSurfaceView(view) }

    override fun setVideoTextureView(view: TextureView?) =
        guard("setVideoTextureView") { super.setVideoTextureView(view) }

    override fun clearVideoTextureView(view: TextureView?) =
        guard("clearVideoTextureView") { super.clearVideoTextureView(view) }

    override fun release() {
        synchronized(commandListeners) {
            commandListeners.clear()
        }
        super.release()
    }

    // ── Policy ────────────────────────────────────────────────────────────────────────────────

    /**
     * Runs [block] only while the guest lock is off; while the lock is on the operation becomes a
     * no-op (throttled toast + log) and never throws. Reads [listenTogetherGuestLock] on every
     * call (AD-3 — no cache).
     */
    private fun guard(op: String, block: () -> Unit) {
        if (listenTogetherGuestLock.value) {
            showBlockedToastIfNeeded()
            Timber.tag(TAG).d("blocked: $op (guest locked)")
            if (op in TAIL_HEAD_OPS) armTailSuppression()
        } else {
            block()
        }
    }

    /**
     * Opens the tail-suppression window ([TAIL_WINDOW_MS] from [clock]) after a blocked head
     * op, so the synchronous `prepare()` / `volume = …` / `playWhenReady = …` tail that user
     * helpers append after the head cannot change the play state of the current track
     * (spec-listen-together-guest-lock-hardening, resume-leak fix).
     */
    private fun armTailSuppression() {
        tailSuppressUntilMs = clock() + TAIL_WINDOW_MS
    }

    /** True while locked and inside the tail-suppression window opened by a blocked head op. */
    private fun inTailSuppression(): Boolean =
        listenTogetherGuestLock.value && clock() < tailSuppressUntilMs

    /**
     * Emits the throttled "the host controls playback" toast for an operation that lives on the
     * ExoPlayer-only surface (e.g. `setPreferredAudioDevice`) and therefore cannot be expressed on
     * the [Player] interface — the fail-closed policy (AD-2) and the toast throttle stay in one place.
     */
    fun reportBlockedOp() {
        showBlockedToastIfNeeded()
        Timber.tag(TAG).d("blocked: ExoPlayer-only op (guest locked)")
    }

    /**
     * Emits the "the host controls playback" toast at most once per [TOAST_THROTTLE_MS] so repeated
     * blocked taps (widgets, medley auto-skip, repeated next taps) do not spam the screen.
     */
    private fun showBlockedToastIfNeeded() {
        val now = clock()
        val last = lastBlockedToastAtMs
        if (last != null && now - last < TOAST_THROTTLE_MS) return
        lastBlockedToastAtMs = now
        toastSink(appContext.getString(R.string.listen_together_guest_blocked))
    }
}
