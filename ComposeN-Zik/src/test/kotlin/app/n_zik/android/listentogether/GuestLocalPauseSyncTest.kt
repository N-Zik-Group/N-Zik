package app.n_zik.android.listentogether

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Contract tests for the guest play/pause intent (spec-listen-together-guest-lock-hardening,
 * "pause is kept across host skips" + "guest play resyncs to the host position").
 *
 * [ListenTogetherManager.guestPlayPause] is the single entry point fed by the guarded facade
 * ([ListenTogetherGuestGuardPlayer.onGuestPlayPause], wired in [PlayerServiceModern]). The
 * play intent must request a re-sync from the server (so the guest resumes at the host's
 * position); the pause intent must NOT request a sync (the local pause is remembered and the
 * host's PLAY / track changes must not resume the guest). Host and out-of-room calls are
 * no-ops. Robolectric is required because the manager constructor reads a persisted
 * preference through a real [Context]; the client is a relaxed mock so its WebSocket never
 * runs.
 *
 * The buffering-branch tests pin the regression where the host's streaming state (raw
 * `isPlaying = true`) replaced the pending sync state while the guest was buffering a new
 * track, so `applyPendingSyncIfReady` resumed a locally-paused guest as soon as the buffer
 * completed (user report: "il change de buffer pour charger, mais du coup il repart").
 *
 * The buffer-completion tests pin the complementary race: a guest pause landing *after* the
 * pending sync state was captured (during the buffer wait) must not be resurrected by the
 * stale host `isPlaying` when the buffer completes (audit finding).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GuestLocalPauseSyncTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val client = mockk<ListenTogetherClient>(relaxed = true)
    private val mockPlayer = mockk<ExoPlayer>(relaxed = true)

    private var originalSingleton: Any? = null
    private var originalBridgeService: Any? = null

    private fun newManager(): ListenTogetherManager = ListenTogetherManager(client, context)

    /**
     * Every [newManager] replaces the process-wide singleton (`init { setInstance(this) }`);
     * capture it before and restore it after so other test classes in the same JVM never see
     * a test manager from [ListenTogetherManager.getInstance()]. (The JVM backend hoists the
     * companion's private `instance` var to a static field on the outer class.)
     */
    @Before
    fun captureSingleton() {
        originalSingleton = singletonField().get(null)
        // The bridge resolves its player through the (private) service reference — point it at
        // a mock service exposing [mockPlayer] so sync applications become observable.
        originalBridgeService = bridgeServiceField().get(ListenTogetherPlayerBridge)
        val service = mockk<PlayerServiceModern>(relaxed = true)
        every { service.player } returns mockPlayer
        bridgeServiceField().set(ListenTogetherPlayerBridge, service)
    }

    @After
    fun restoreSingleton() {
        singletonField().set(null, originalSingleton)
        bridgeServiceField().set(ListenTogetherPlayerBridge, originalBridgeService)
    }

    private fun singletonField(): java.lang.reflect.Field =
        ListenTogetherManager::class.java.getDeclaredField("instance").apply { isAccessible = true }

    private fun bridgeServiceField(): java.lang.reflect.Field =
        ListenTogetherPlayerBridge::class.java.getDeclaredField("service").apply { isAccessible = true }

    private fun readPrivate(obj: Any, name: String): Any? {
        val field = obj::class.java.getDeclaredField(name).apply { isAccessible = true }
        return field.get(obj)
    }

    private fun setPrivate(obj: Any, name: String, value: Any?) {
        val field = obj::class.java.getDeclaredField(name).apply { isAccessible = true }
        field.set(obj, value)
    }

    private fun invokePrivate(obj: Any, name: String, vararg args: Any) {
        val types = args.map { it::class.javaPrimitiveType ?: it::class.java }.toTypedArray()
        val method = obj::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }
        method.invoke(obj, *args)
    }

    private fun hostPlayingState(): SyncStatePayload =
        SyncStatePayload(
            currentTrack = TrackInfo(id = TRACK_ID, title = "title", artist = "artist", duration = 0L),
            isPlaying = true,
            position = 5000L,
            lastUpdate = 1234L,
        )

    @Test
    fun `guest play intent resyncs to the host position`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        newManager().guestPlayPause(true)

        verify { client.requestSync() }
    }

    @Test
    fun `guest pause intent does not request a sync (stays paused locally)`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        newManager().guestPlayPause(false)

        verify(exactly = 0) { client.requestSync() }
    }

    @Test
    fun `host play intent is ignored (host is the source of truth)`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns true

        newManager().guestPlayPause(true)

        verify(exactly = 0) { client.requestSync() }
    }

    @Test
    fun `out-of-room play intent is ignored`() {
        every { client.isInRoom } returns false
        every { client.isHost } returns false

        newManager().guestPlayPause(true)

        verify(exactly = 0) { client.requestSync() }
    }

    @Test
    fun `host sync state while buffering does not resume a locally-paused guest`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        val manager = newManager()
        manager.guestPlayPause(false) // the guest paused locally

        // The guest is buffering the host's new track (CHANGE_TRACK path).
        setPrivate(manager, "bufferingTrackId", TRACK_ID)
        invokePrivate(manager, "handleSyncState", hostPlayingState(), false)

        val pending = readPrivate(manager, "pendingSyncState")
        assertNotNull("a pending sync state must have been stored", pending)
        assertFalse(
            "the host's isPlaying=true must not replace the pending state of a locally-paused guest",
            (pending as SyncStatePayload).isPlaying,
        )
    }

    @Test
    fun `host sync state while buffering keeps playing a non-paused guest`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        val manager = newManager()

        setPrivate(manager, "bufferingTrackId", TRACK_ID)
        invokePrivate(manager, "handleSyncState", hostPlayingState(), false)

        val pending = readPrivate(manager, "pendingSyncState")
        assertNotNull("a pending sync state must have been stored", pending)
        assertTrue("a non-paused guest must keep the host's playing state", (pending as SyncStatePayload).isPlaying)
    }

    @Test
    fun `buffer completion does not resume a guest who paused during the buffer wait`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        val manager = newManager()

        // The guest is buffering the host's new track (CHANGE_TRACK path); the host is playing,
        // so the pending sync state is captured with isPlaying = true.
        setPrivate(manager, "bufferingTrackId", TRACK_ID)
        invokePrivate(manager, "handleSyncState", hostPlayingState(), false)
        val pending = readPrivate(manager, "pendingSyncState") as SyncStatePayload
        assertTrue("the pending state must capture the host's playing state", pending.isPlaying)

        // The guest pauses AFTER the pending state was captured (the buffer-wait window).
        manager.guestPlayPause(false)

        // The buffer completes -> the pending sync is applied.
        setPrivate(manager, "bufferCompleteReceivedForTrack", TRACK_ID)
        invokePrivate(manager, "applyPendingSyncIfReady")

        verify(exactly = 0) { mockPlayer.play() }
    }

    @Test
    fun `buffer completion resumes a non-paused guest at the host position`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        val manager = newManager()

        setPrivate(manager, "bufferingTrackId", TRACK_ID)
        invokePrivate(manager, "handleSyncState", hostPlayingState(), false)

        setPrivate(manager, "bufferCompleteReceivedForTrack", TRACK_ID)
        invokePrivate(manager, "applyPendingSyncIfReady")

        verify(exactly = 1) { mockPlayer.play() }
    }

    // ── Review findings (verification-gap): the guestLocalPause overrides on the other two
    //    sync paths (handlePlaybackSync PLAY branch + applyPlaybackState) and the empty
    //    SYNC_QUEUE host-cleanup propagation, previously unverified. ────────────────────────

    @Test
    fun `host PLAY does not resume a locally-paused guest (position synced only)`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        val manager = newManager()
        manager.guestPlayPause(false) // the guest paused locally

        // The guest is on the same track as the host, paused:
        every { mockPlayer.currentMediaItem } returns MediaItem.Builder().setMediaId(TRACK_ID).build()
        every { mockPlayer.playWhenReady } returns false
        every { mockPlayer.currentPosition } returns 0L
        every { client.positionAtServerTime(any(), any(), any()) } returns 100_000L

        invokePrivate(
            manager,
            "handlePlaybackSync",
            PlaybackActionPayload(action = PlaybackActions.PLAY, trackId = TRACK_ID, position = 5000L, serverTime = 1234L),
        )

        // The host's PLAY (skip / resume / heartbeat) syncs the position but must NOT resume
        // the locally-paused guest — the reported user bug on its most common path:
        verify { mockPlayer.seekTo(100_000L) }
        verify(exactly = 0) { mockPlayer.play() }
    }

    @Test
    fun `full state sync does not resume a locally-paused guest (applyPlaybackState)`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        val manager = newManager()
        manager.guestPlayPause(false) // the guest paused locally

        // Manual sync / reconnect: the host state says "playing", the guest must stay paused:
        invokeApplyPlaybackState(manager, track(), true, 5000L, emptyList(), 0L, false)
        shadowOf(Looper.getMainLooper()).idle()

        verify { mockPlayer.pause() }
        verify(exactly = 0) { mockPlayer.play() }
        val pending = readPrivate(manager, "pendingSyncState") as SyncStatePayload
        assertFalse(
            "a locally-paused guest must stay paused in the pending sync state",
            pending.isPlaying,
        )
    }

    @Test
    fun `host player clean sends an empty SYNC_QUEUE to the guests`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns true

        val manager = newManager()
        val listener = readPrivate(manager, "playerListener") ?: return
        listener.javaClass
            .getMethod("onMediaItemTransition", MediaItem::class.java, Int::class.java)
            .invoke(listener, null, 0)

        verify { client.sendPlaybackAction(PlaybackActions.SYNC_QUEUE, queue = emptyList()) }
    }

    @Test
    fun `empty SYNC_QUEUE clears, pauses and resets the sync state machine`() {
        every { client.isInRoom } returns true
        every { client.isHost } returns false

        val manager = newManager()

        // Stale pre-clean sync state (the host was mid-track-change when it cleaned):
        setPrivate(manager, "pendingSyncState", hostPlayingState())
        setPrivate(manager, "bufferingTrackId", TRACK_ID)
        setPrivate(manager, "bufferCompleteReceivedForTrack", TRACK_ID)

        invokePrivate(
            manager,
            "handlePlaybackSync",
            PlaybackActionPayload(action = PlaybackActions.SYNC_QUEUE, queue = emptyList()),
        )

        // The host's clean is mirrored on the guest's player:
        verify { mockPlayer.clearMediaItems() }
        verify { mockPlayer.pause() }
        // and the sync state machine is reset (a stale pending sync / buffer-complete flag
        // would let applyPendingSyncIfReady fire early on a same-track restart):
        assertNull(readPrivate(manager, "pendingSyncState"))
        assertNull(readPrivate(manager, "bufferingTrackId"))
        assertNull(readPrivate(manager, "bufferCompleteReceivedForTrack"))
    }

    private fun track(): TrackInfo =
        TrackInfo(id = TRACK_ID, title = "title", artist = "artist", duration = 0L)

    /**
     * Reflection call for the private [applyPlaybackState] — [invokePrivate]'s mapping can't
     * express its exact JVM signature: `effectiveAtServerTime: Long?` erases to the boxed
     * `java.lang.Long` (`Long::class.javaObjectType` — note `Long::class.java` yields the
     * primitive `long` in Kotlin), and a null can't be passed through `vararg args: Any` anyway.
     */
    private fun invokeApplyPlaybackState(
        manager: Any,
        currentTrack: TrackInfo,
        isPlaying: Boolean,
        position: Long,
        queue: List<TrackInfo>,
        effectiveAtServerTime: Long?,
        bypassBuffer: Boolean,
    ) {
        val method =
            manager::class.java.getDeclaredMethod(
                "applyPlaybackState",
                TrackInfo::class.java,
                Boolean::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                List::class.java,
                Long::class.javaObjectType,
                Boolean::class.javaPrimitiveType,
            ).apply { isAccessible = true }
        method.invoke(manager, currentTrack, isPlaying, position, queue, effectiveAtServerTime, bypassBuffer)
    }

    companion object {
        private const val TRACK_ID = "track-1"
    }
}
