package app.n_zik.android.listentogether

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Policy matrix for [ListenTogetherGuestGuardPlayer]
 * (spec-listen-together-guest-lock-hardening, spine AD-2/AD-3/AD-6):
 * locked/unlocked x operation, fail-closed default, and the throttled blocked-toast.
 * The upstream is a fake [Player] (mockk) — the guard itself holds no lock state, so the
 * tests flip [listenTogetherGuestLock] directly on the same guard instance.
 */
@OptIn(UnstableApi::class)
class ListenTogetherGuestGuardPlayerTest {

    private val upstream = mockk<Player>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val toasts = mutableListOf<String>()
    private var now = 0L
    private lateinit var guard: ListenTogetherGuestGuardPlayer

    @BeforeEach
    fun setup() {
        toasts.clear()
        now = 0L
        every { context.getString(any()) } returns "The host controls playback"
        guard = ListenTogetherGuestGuardPlayer(
            upstream = upstream,
            appContext = context,
            clock = { now },
            toastSink = { message -> toasts.add(message) },
        )
        listenTogetherGuestLock.value = false
    }

    @AfterEach
    fun teardown() {
        listenTogetherGuestLock.value = false
    }

    private fun item() = item("video1")

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    /**
     * A [Player.Commands] and its [Player.Commands.Builder] wired as mocks so the guard's
     * `buildUpon().addAllCommands().remove(...)….build()` chain runs on the JVM — the real
     * implementation depends on android.util.SparseBooleanArray, which unit tests cannot execute.
     * The builder echoes itself for every mutation; [built] is what the final `.build()` returns.
     */
    private class CommandsStub(
        val upstream: Player.Commands,
        val builder: Player.Commands.Builder,
        val built: Player.Commands,
    )

    private fun commandsStub(): CommandsStub {
        val builder = mockk<Player.Commands.Builder>(relaxed = true)
        val upstreamCommands = mockk<Player.Commands>(relaxed = true)
        val built = mockk<Player.Commands>(relaxed = true)
        every { upstreamCommands.buildUpon() } returns builder
        every { builder.addAllCommands() } returns builder
        every { builder.remove(any()) } returns builder
        every { builder.build() } returns built
        return CommandsStub(upstreamCommands, builder, built)
    }

    // ── Locked: AD-2 matrix ops are no-ops ─────────────────────────────────────────────

    @Test
    fun `locked queue ops are no-ops`() {
        listenTogetherGuestLock.value = true
        guard.setMediaItems(listOf(item()))
        guard.setMediaItems(listOf(item()), true)
        guard.setMediaItems(listOf(item()), 0, 0L)
        guard.setMediaItem(item())
        guard.setMediaItem(item(), 0L)
        guard.setMediaItem(item(), true)
        guard.addMediaItem(item())
        guard.addMediaItem(0, item())
        guard.addMediaItems(listOf(item()))
        guard.addMediaItems(0, listOf(item()))
        guard.moveMediaItem(0, 1)
        guard.moveMediaItems(0, 1, 2)
        guard.replaceMediaItem(0, item())
        guard.replaceMediaItems(0, 1, listOf(item()))
        guard.removeMediaItem(0)
        guard.removeMediaItems(0, 1)
        guard.clearMediaItems()

        verify(exactly = 0) {
            upstream.setMediaItems(any())
            upstream.setMediaItems(any(), any())
            upstream.setMediaItems(any(), any(), any())
            upstream.setMediaItem(any())
            upstream.setMediaItem(any<MediaItem>(), any<Long>())
            upstream.setMediaItem(any<MediaItem>(), any<Boolean>())
            upstream.addMediaItem(any())
            upstream.addMediaItem(any(), any())
            upstream.addMediaItems(any())
            upstream.addMediaItems(any(), any())
            upstream.moveMediaItem(any(), any())
            upstream.moveMediaItems(any(), any(), any())
            upstream.replaceMediaItem(any(), any())
            upstream.replaceMediaItems(any(), any(), any())
            upstream.removeMediaItem(any())
            upstream.removeMediaItems(any(), any())
            upstream.clearMediaItems()
        }
        assertEquals(1, toasts.size)
    }

    @Test
    fun `locked seek and track ops are no-ops`() {
        listenTogetherGuestLock.value = true
        guard.seekTo(1234L)
        guard.seekTo(0, 1234L)
        guard.seekBack()
        guard.seekForward()
        guard.seekToDefaultPosition()
        guard.seekToDefaultPosition(0)
        guard.seekToNext()
        guard.seekToNextMediaItem()
        guard.seekToPrevious()
        guard.seekToPreviousMediaItem()

        verify(exactly = 0) {
            upstream.seekTo(any())
            upstream.seekTo(any(), any())
            upstream.seekBack()
            upstream.seekForward()
            upstream.seekToDefaultPosition()
            upstream.seekToDefaultPosition(any())
            upstream.seekToNext()
            upstream.seekToNextMediaItem()
            upstream.seekToPrevious()
            upstream.seekToPreviousMediaItem()
        }
        assertEquals(1, toasts.size)
    }

    @Test
    fun `locked mode speed and stop ops are no-ops`() {
        listenTogetherGuestLock.value = true
        guard.setRepeatMode(Player.REPEAT_MODE_ONE)
        guard.setShuffleModeEnabled(true)
        guard.setPlaybackParameters(PlaybackParameters(1.5f, 1f))
        guard.setPlaybackSpeed(1.5f)
        guard.stop()

        verify(exactly = 0) {
            upstream.setRepeatMode(any())
            upstream.setShuffleModeEnabled(any())
            upstream.setPlaybackParameters(any())
            upstream.setPlaybackSpeed(any())
            upstream.stop()
        }
        assertEquals(1, toasts.size)
    }

    @Test
    fun `locked unlisted mutation ops are fail-closed no-ops`() {
        // AD-2 fail-closed: every operation not explicitly listed as "passing" is blocked.
        // (Device volume is deliberately NOT on this list — it is a pass-through like
        // setVolume: a guest's own output volume is theirs, "volume stays available".)
        listenTogetherGuestLock.value = true
        guard.setTrackSelectionParameters(TrackSelectionParameters.DEFAULT)
        guard.setPlaylistMetadata(MediaMetadata.EMPTY)
        guard.setAudioAttributes(AudioAttributes.DEFAULT, false)

        verify(exactly = 0) {
            upstream.setTrackSelectionParameters(any())
            upstream.setPlaylistMetadata(any())
            upstream.setAudioAttributes(any(), any())
        }
        assertEquals(1, toasts.size)
    }

    // ── Locked: play/pause/volume/queries still pass through ──────────────────────────

    @Test
    fun `locked play pause prepare and volume still delegate`() {
        listenTogetherGuestLock.value = true
        guard.play()
        guard.pause()
        guard.prepare()
        guard.setPlayWhenReady(false)
        guard.setVolume(0.5f)
        guard.mute()
        guard.unmute()

        verify {
            upstream.play()
            upstream.pause()
            upstream.prepare()
            upstream.setPlayWhenReady(false)
            upstream.setVolume(0.5f)
            upstream.mute()
            upstream.unmute()
        }
        assertTrue(toasts.isEmpty())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `locked device volume still delegates (a guest's own output stays theirs)`() {
        // Review finding: the device-volume MediaSession commands were hidden + blocked,
        // exceeding the spec's "volume stays available" scope — device volume is pass-through
        // like setVolume and never desynchronizes the room.
        listenTogetherGuestLock.value = true
        guard.setDeviceVolume(42)
        guard.setDeviceVolume(42, 0)
        guard.increaseDeviceVolume()
        guard.increaseDeviceVolume(0)
        guard.decreaseDeviceVolume()
        guard.decreaseDeviceVolume(0)
        guard.setDeviceMuted(true)
        guard.setDeviceMuted(true, 0)

        verify {
            upstream.setDeviceVolume(42)
            upstream.setDeviceVolume(42, 0)
            upstream.increaseDeviceVolume()
            upstream.increaseDeviceVolume(0)
            upstream.decreaseDeviceVolume()
            upstream.decreaseDeviceVolume(0)
            upstream.setDeviceMuted(true)
            upstream.setDeviceMuted(true, 0)
        }
        assertTrue(toasts.isEmpty(), "device volume is a pass-through op — no blocked toast")
    }

    @Test
    fun `locked readers still delegate`() {
        every { upstream.currentPosition } returns 42L
        every { upstream.duration } returns 100L
        every { upstream.isPlaying } returns true
        every { upstream.currentMediaItem } returns item()

        listenTogetherGuestLock.value = true

        assertEquals(42L, guard.currentPosition)
        assertEquals(100L, guard.duration)
        assertTrue(guard.isPlaying)
        assertEquals("video1", guard.currentMediaItem?.mediaId)
        assertTrue(toasts.isEmpty())
    }

    // ── Unlocked: full delegation, no toast ────────────────────────────────────────────

    @Test
    fun `unlocked ops delegate to the upstream player`() {
        listenTogetherGuestLock.value = false
        guard.seekTo(1234L)
        guard.seekToNextMediaItem()
        guard.setRepeatMode(Player.REPEAT_MODE_ONE)
        guard.setShuffleModeEnabled(true)
        guard.setPlaybackParameters(PlaybackParameters(1.5f, 1f))
        guard.addMediaItems(listOf(item()))
        guard.stop()

        verify {
            upstream.seekTo(1234L)
            upstream.seekToNextMediaItem()
            upstream.setRepeatMode(Player.REPEAT_MODE_ONE)
            upstream.setShuffleModeEnabled(true)
            upstream.setPlaybackParameters(PlaybackParameters(1.5f, 1f))
            upstream.addMediaItems(listOf(item()))
            upstream.stop()
        }
        assertTrue(toasts.isEmpty())
    }

    // ── AD-3: lock re-read on every call, no cache, no re-creation ─────────────────────

    @Test
    fun `lock state is re-read on every call on the same guard instance`() {
        listenTogetherGuestLock.value = true
        guard.seekTo(100L)
        verify(exactly = 0) { upstream.seekTo(any()) }

        // Same guard instance, no re-creation — only the shared State changed.
        listenTogetherGuestLock.value = false
        guard.seekTo(200L)
        verify(exactly = 1) { upstream.seekTo(200L) }

        listenTogetherGuestLock.value = true
        guard.seekTo(300L)
        verify(exactly = 0) { upstream.seekTo(300L) }
    }

    // ── Throttled toast ─────────────────────────────────────────────────────────────────

    @Test
    fun `blocked ops toast at most once per throttle window`() {
        listenTogetherGuestLock.value = true
        guard.seekTo(0L)
        guard.seekTo(0L)
        guard.seekToNext()
        guard.stop()
        assertEquals(1, toasts.size, "first blocked op toasts, the rest within the window are throttled")
        assertEquals("The host controls playback", toasts.first())

        now = ListenTogetherGuestGuardPlayer.TOAST_THROTTLE_MS // 2s later
        guard.seekTo(0L)
        assertEquals(2, toasts.size, "toast is allowed again after the throttle window")
    }

    // ── AD-6: available commands hide next/prev/shuffle/stop for guests ────────────────

    @Test
    fun `locked available commands strip the guest-blocked commands from the builder`() {
        val stub = commandsStub()
        every { upstream.getAvailableCommands() } returns stub.upstream
        listenTogetherGuestLock.value = true

        val commands = guard.getAvailableCommands()

        assertSame(stub.built, commands)
        // Every guest-blocked command (AD-2/AD-6) is stripped exactly once…
        ListenTogetherGuestGuardPlayer.GUEST_LOCKED_HIDDEN_COMMANDS.forEach { command ->
            verify(exactly = 1) { stub.builder.remove(command) }
        }
        // …while play/pause and prepare (guest-allowed) are never stripped.
        verify(exactly = 0) { stub.builder.remove(Player.COMMAND_PLAY_PAUSE) }
        verify(exactly = 0) { stub.builder.remove(Player.COMMAND_PREPARE) }
    }

    @Test
    fun `unlocked available commands keep every transport command`() {
        val stub = commandsStub()
        every { upstream.getAvailableCommands() } returns stub.upstream
        listenTogetherGuestLock.value = false

        val commands = guard.getAvailableCommands()

        assertSame(stub.built, commands)
        // Unlocked: nothing is stripped — the full command set is forwarded as-is.
        verify(exactly = 0) { stub.builder.remove(any()) }
    }

    @Test
    fun `locked isCommandAvailable denies blocked commands only`() {
        every { upstream.isCommandAvailable(any()) } returns true
        listenTogetherGuestLock.value = true

        assertFalse(guard.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(guard.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))
        assertFalse(guard.isCommandAvailable(Player.COMMAND_STOP))
        assertTrue(guard.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(guard.isCommandAvailable(Player.COMMAND_SET_VOLUME))
    }

    @Test
    fun `announceAvailableCommandsChanged notifies registered listeners with the guarded commands`() {
        val stub = commandsStub()
        every { upstream.getAvailableCommands() } returns stub.upstream
        listenTogetherGuestLock.value = true
        val listener = mockk<Player.Listener>()

        guard.addListener(listener)
        guard.announceAvailableCommandsChanged()

        // The listener receives the exact commands object the guard built (the guarded set).
        verify { listener.onAvailableCommandsChanged(stub.built) }

        guard.removeListener(listener)
        guard.announceAvailableCommandsChanged()
        verify(exactly = 1) { listener.onAvailableCommandsChanged(any()) }
    }

    // ── Explicit user requirements (2026-09-29) ────────────────────────────────────────────

    @Test
    fun `play another track is blocked for a guest (PLAY_ANOTHER_GUEST)`() {
        // A guest tapping any track (library, search, playlists, queue item, "play from
        // anywhere") must not start it: the current track stays, nothing is loaded, and the
        // attempt is explained by the throttled toast.
        every { upstream.currentMediaItem } returns item()
        listenTogetherGuestLock.value = true

        guard.setMediaItem(item("another"))
        guard.setMediaItem(item("another"), true)
        guard.setMediaItems(listOf(item("a"), item("b")))
        guard.addMediaItem(0, item("c"))
        guard.addMediaItems(listOf(item("d")))

        verify(exactly = 0) {
            upstream.setMediaItem(any())
            upstream.setMediaItem(any<MediaItem>(), any<Boolean>())
            upstream.setMediaItems(any())
            upstream.addMediaItem(any(), any())
            upstream.addMediaItems(any())
        }
        assertEquals("video1", guard.currentMediaItem?.mediaId, "the current track must not change")
        assertEquals(1, toasts.size, "the blocked attempt is explained by a toast")
    }

    @Test
    fun `queue stays read-only for a guest (QUEUE_READONLY_GUEST)`() {
        // Every queue mutation is a no-op for a locked guest, while the queue content stays
        // fully readable (queries pass through).
        every { upstream.mediaItemCount } returns 2
        every { upstream.getMediaItemAt(0) } returns item()
        every { upstream.getMediaItemAt(1) } returns item("second")
        listenTogetherGuestLock.value = true

        guard.moveMediaItem(0, 1)
        guard.moveMediaItems(0, 1, 2)
        guard.removeMediaItem(1)
        guard.removeMediaItems(0, 1)
        guard.replaceMediaItem(0, item("replacement"))
        guard.clearMediaItems()
        guard.setShuffleModeEnabled(true)

        verify(exactly = 0) {
            upstream.moveMediaItem(any(), any())
            upstream.moveMediaItems(any(), any(), any())
            upstream.removeMediaItem(any())
            upstream.removeMediaItems(any(), any())
            upstream.replaceMediaItem(any(), any())
            upstream.clearMediaItems()
            upstream.setShuffleModeEnabled(any())
        }
        // Read-only: the queue is still fully readable while locked
        assertEquals(2, guard.mediaItemCount)
        assertEquals("video1", guard.getMediaItemAt(0).mediaId)
        assertEquals("second", guard.getMediaItemAt(1).mediaId)
        assertEquals(1, toasts.size, "the blocked mutation is explained by a toast")
    }

    // ── Blocked-op tail suppression (resume-leak fix, user report 2026-09-29) ─────────────

    @Test
    fun `a blocked play-another chain cannot resume the paused track`() {
        // User report: clicking a song (blocked) resumed the paused track because the
        // forcePlay chain keeps running its pass-through tail: prepare + volume + playWhenReady.
        every { upstream.currentMediaItem } returns item()
        listenTogetherGuestLock.value = true

        guard.setMediaItem(item("another"), true) // blocked head (forcePlay)
        guard.prepare()
        guard.setVolume(0.5f)
        guard.setPlayWhenReady(true)

        verify(exactly = 0) {
            upstream.setMediaItem(any<MediaItem>(), any<Boolean>())
            upstream.prepare()
            upstream.setVolume(any())
            upstream.setPlayWhenReady(any())
        }
        assertEquals(1, toasts.size, "one toast explains the whole blocked chain")
        assertEquals("video1", guard.currentMediaItem?.mediaId, "the paused track must stay")
    }

    @Test
    fun `a real guest play tap after a blocked chain still works`() {
        listenTogetherGuestLock.value = true
        val intents = mutableListOf<Boolean>()
        guard.onGuestPlayPause = { intents.add(it) }

        // Blocked click-song chain at t=0.
        guard.setMediaItem(item("another"), true)
        guard.prepare()
        guard.setVolume(0.5f)
        guard.setPlayWhenReady(true)

        // The guest's own play tap, well beyond the 50 ms tail window.
        now = ListenTogetherGuestGuardPlayer.TAIL_WINDOW_MS + 1L
        guard.play()

        verify(exactly = 1) { upstream.play() }
        assertEquals(listOf(true), intents, "the real play tap registers the guest intent")
    }

    @Test
    fun `consecutive blocked chains stay inert`() {
        // Two click-song attempts inside the tail window: the window re-arms on each blocked
        // head, so the second chain's tail is suppressed too.
        listenTogetherGuestLock.value = true

        guard.setMediaItem(item("a"), true)
        guard.prepare()
        guard.setVolume(0.5f)
        guard.setPlayWhenReady(true)
        guard.setMediaItem(item("b"), true)
        guard.prepare()
        guard.setVolume(0.5f)
        guard.setPlayWhenReady(true)

        verify(exactly = 0) {
            upstream.setMediaItem(any<MediaItem>(), any<Boolean>())
            upstream.prepare()
            upstream.setVolume(any())
            upstream.setPlayWhenReady(any())
        }
    }

    @Test
    fun `a blocked skip chain (playNext) cannot resume the paused track`() {
        // playNext = seekToNextMediaItem (blocked head) + prepare + volume + playWhenReady —
        // the swipe/skip entry points leak the same way as click-song.
        listenTogetherGuestLock.value = true

        guard.seekToNextMediaItem()
        guard.prepare()
        guard.setVolume(0.5f)
        guard.setPlayWhenReady(true)

        verify(exactly = 0) {
            upstream.seekToNextMediaItem()
            upstream.prepare()
            upstream.setVolume(any())
            upstream.setPlayWhenReady(any())
        }
    }

    @Test
    fun `guest play pause intent fires only for real taps while locked`() {
        val intents = mutableListOf<Boolean>()
        guard.onGuestPlayPause = { intents.add(it) }
        listenTogetherGuestLock.value = true

        guard.play()
        guard.pause()
        guard.setPlayWhenReady(true)
        assertEquals(listOf(true, false, true), intents, "each real play/pause tap registers its intent")

        // Unlocked: intents stop firing (host / no-room behavior).
        listenTogetherGuestLock.value = false
        guard.play()
        guard.pause()
        assertEquals(listOf(true, false, true), intents, "no intent while unlocked")

        // A suppressed tail setPlayWhenReady is not an intent.
        listenTogetherGuestLock.value = true
        guard.setMediaItem(item("another"), true)
        guard.setPlayWhenReady(true)
        assertEquals(listOf(true, false, true), intents, "the suppressed tail is not an intent")
    }

    @Test
    fun `a freshly created facade (crossfade swap) inherits the active lock`() {
        // CROSSFADE_SWAP: the service re-creates the facade on every playerUpdateTrigger.
        // The lock is a shared State, never an instance field — the fresh facade must block
        // from its very first call, with its own toast throttle.
        listenTogetherGuestLock.value = true
        val swappedUpstream = mockk<Player>(relaxed = true)
        val freshFacade = ListenTogetherGuestGuardPlayer(
            upstream = swappedUpstream,
            appContext = context,
            clock = { now },
            toastSink = { message -> toasts.add(message) },
        )

        freshFacade.seekTo(100L)

        verify(exactly = 0) { swappedUpstream.seekTo(any()) }
        assertEquals(1, toasts.size, "the fresh facade blocks and toasts from its first call")
    }
}
