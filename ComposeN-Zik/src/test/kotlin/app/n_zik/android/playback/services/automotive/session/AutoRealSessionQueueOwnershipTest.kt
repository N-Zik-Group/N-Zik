package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import app.it.fast4x.rimusic.models.QueuedMediaItem
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.fadeInEffect
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.QueuedMediaItemTable
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.playback.services.pauseWithFade
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Real modern MediaController/MediaSession queue-application regressions. No audible or legacy transport claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AutoRealSessionQueueOwnershipTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var session: MediaSession
    private lateinit var player: RecordingPlayer
    private lateinit var first: MediaController
    private lateinit var second: MediaController
    private lateinit var transport: MediaController
    private lateinit var sharedGuard: ListenTogetherGuestGuardPlayer
    private var guardClock = 1000L
    private lateinit var observed: ObservedCallback
    private val gate = CompletableDeferred<Unit>()
    private val entered = CountDownLatch(1)
    private val sourceFinished = CountDownLatch(1)
    private var gatedId = "explicit"
    private var failGatedSelection = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        listenTogetherGuestLock.value = false
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        context.preferences.edit().clear().putBoolean(persistentQueueKey, true).commit()
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        mockkObject(Database)
        val queue = mockk<QueuedMediaItemTable>()
        every { Database.queueTable } returns queue
        every { queue.all(any()) } returns flowOf(listOf(QueuedMediaItem(mediaItem = item("saved"), position = 456L)))
        mockkConstructor(AutoAccessPolicy::class)
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            val items = firstArg<List<MediaItem>>()
            if (items.firstOrNull()?.mediaId == gatedId) {
                entered.countDown()
                try { withContext(NonCancellable) { gate.await() } } finally { sourceFinished.countDown() }
                if (failGatedSelection) throw IllegalStateException("selection eligibility failed")
            }
            items.withIndex().toList()
        }
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        observed = ObservedCallback(callback)
        player = RecordingPlayer()
        session = MediaSession.Builder(context, guardedSessionPlayer()).setCallback(observed).build()
        val firstFuture = MediaController.Builder(context, session.token).buildAsync()
        val secondFuture = MediaController.Builder(context, session.token).buildAsync()
        val transportFuture = MediaController.Builder(context, session.token).buildAsync()
        pumpUntil { firstFuture.isDone && secondFuture.isDone && transportFuture.isDone }
        transport = transportFuture.get(1, TimeUnit.SECONDS)
        first = firstFuture.get(1, TimeUnit.SECONDS)
        second = secondFuture.get(1, TimeUnit.SECONDS)
        assertTrue(first.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertTrue(second.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
    }

    @After
    fun tearDown() {
        gate.complete(Unit)
        listenTogetherGuestLock.value = false
        if (::first.isInitialized) first.release()
        if (::second.isInitialized) second.release()
        if (::transport.isInitialized) transport.release()
        if (::session.isInitialized) session.release()
        if (::callback.isInitialized) callback.release()
        if (::player.isInitialized) player.release()
        shadowOf(Looper.getMainLooper()).idle()
        if (::context.isInitialized) context.preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test
    fun `completed selection owns queue until real cross controller application`() {
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        val selection = requireNotNull(observed.selection)
        assertFalse(selection.isDone)
        // Modern controllers queue a real flush message at the end of their transaction.
        // Queue that message before DATA completion posts the queue-application runnable.
        second.play()
        assertTrue(observed.resumptions.isEmpty())
        gate.complete(Unit)
        selection.get(15, TimeUnit.SECONDS)
        assertEquals(0, player.mediaItemCount)
        pumpUntil { observed.resumptions.isNotEmpty() && player.playWhenReady }
        assertEquals(listOf("[explicit] index=0 position=999"), player.setCalls)
        assertEquals("explicit", player.currentMediaItem?.mediaId)
        assertEquals(999L, player.currentPosition)
        assertSame(observed.selection, observed.resumptions.single())
        report("completed-before-application", "selectionDoneBeforePumping=true")
    }

    @Test
    fun `pending selection shared by real consumers applies once and plays`() {
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        second.play()
        pumpUntil { observed.resumptions.isNotEmpty() }
        assertSame(observed.selection, observed.resumptions.single())
        assertFalse(requireNotNull(observed.selection).isDone)
        gate.complete(Unit)
        pumpUntil { player.setCalls.isNotEmpty() && player.playWhenReady }
        assertEquals(listOf("[explicit] index=0 position=999"), player.setCalls)
        report("shared-future", "sameFuture=true")
    }

    @Test
    fun `invalidated resumption cannot prepare or play intervening paused queue`() {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        val resume = observed.resumptions.single()
        assertFalse(resume.isDone)
        player.setMediaItems(listOf(item("intervening")), 0, 321L)
        assertFalse(player.playWhenReady)
        callback.invalidatePublication()
        assertTrue(resume.isCancelled)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(player.playWhenReady)
        assertFalse(player.trace.contains("prepare"))
        gate.complete(Unit)
        assertTrue(sourceFinished.await(15, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("[intervening] index=0 position=321"), player.setCalls)
        second.play()
        pumpUntil { player.playWhenReady }
        assertEquals(321L, player.currentPosition)
        report("invalidation-fallback", "resumptionCancelled=true; interveningQueueInitiallyPaused=true")
    }

    @Test
    fun `completed data invalidated before application cannot replace queue and later choice still applies`() {
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        gate.complete(Unit)
        requireNotNull(observed.selection).get(15, TimeUnit.SECONDS)
        assertTrue(player.setCalls.isEmpty())
        callback.invalidatePublication()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(player.setCalls.isEmpty())
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { player.setCalls.size == 1 }
        second.play()
        pumpUntil { player.playWhenReady }
        assertEquals("explicit", player.currentMediaItem?.mediaId)
    }

    @Test
    fun `identical deliberate selections apply twice across player facade recreation`() {
        gate.complete(Unit)
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { player.setCalls.size == 1 }
        // Service crossfade replaces the player facade while retaining this callback owner.
        session.player = guardedSessionPlayer()
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { player.setCalls.size == 2 }
        assertEquals(listOf("[explicit] index=0 position=999", "[explicit] index=0 position=999"), player.setCalls)
        second.play()
        pumpUntil { player.playWhenReady }
    }

    @Test
    fun `raw phone playlist retires pending saved resume without policy invalidation`() {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        player.setMediaItems(listOf(item("phone")), 0, 222L)
        gate.complete(Unit)
        assertTrue(sourceFinished.await(15, TimeUnit.SECONDS))
        requireNotNull(observed.resumptions.single()).get(15, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("[phone] index=0 position=222"), player.setCalls)
        assertFalse(player.playWhenReady)
        assertFalse(player.trace.contains("prepare"))
        second.play()
        pumpUntil { player.playWhenReady }
        assertEquals("phone", player.currentMediaItem?.mediaId)
    }

    @Test
    fun `old upstream timeline after facade rebind cannot retire new pending choice`() {
        val previousPlayer = player
        player = RecordingPlayer()
        session.player = guardedSessionPlayer()
        try {
            first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
            pumpUntil { entered.count == 0L }
            previousPlayer.setMediaItems(listOf(item("old-upstream")), 0, 11L)
            gate.complete(Unit)
            pumpUntil { player.setCalls.size == 1 }
            assertEquals("explicit", player.currentMediaItem?.mediaId)
            second.play()
            pumpUntil { player.playWhenReady }
        } finally {
            previousPlayer.release()
        }
    }

    @Test fun `failed real explicit selection permits next Play to recover saved history`() {
        failGatedSelection = true
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        gate.complete(Unit)
        assertThrows(ExecutionException::class.java) {
            requireNotNull(observed.selection).get(15, TimeUnit.SECONDS)
        }
        assertFalse(requireNotNull(observed.selection).isCancelled)
        second.play()
        pumpUntil { player.playWhenReady && player.setCalls.isNotEmpty() }
        assertEquals(listOf("[saved] index=0 position=456"), player.setCalls)
        assertEquals("saved", player.currentMediaItem?.mediaId)
    }

    @Test fun `Pause during saved resumption retains saved queue without late playback`() =
        assertLatestTransport(stop = false, explicit = false)

    @Test fun `Stop during saved resumption retains saved queue without late playback`() =
        assertLatestTransport(stop = true, explicit = false)

    @Test fun `Pause during explicit selection and Play retains chosen queue without saved fallback`() =
        assertLatestTransport(stop = false, explicit = true)

    @Test fun `Stop during explicit selection and Play retains chosen queue without saved fallback`() =
        assertLatestTransport(stop = true, explicit = true)

    private fun assertLatestTransport(stop: Boolean, explicit: Boolean) {
        gatedId = if (explicit) "explicit" else "saved"
        if (explicit) {
            first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
            pumpUntil { entered.count == 0L }
        }
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        val resume = observed.resumptions.single()
        if (explicit) assertSame(observed.selection, resume)
        assertTrue(transport.isCommandAvailable(if (stop) Player.COMMAND_STOP else Player.COMMAND_PLAY_PAUSE))
        // A separate controller delivers the newer transport command while Play awaits data.
        if (stop) transport.stop() else transport.pause()
        pumpUntil { if (stop) "stop" in player.trace else player.trace.any { it.startsWith("play(false)") } }
        gate.complete(Unit)
        resume.get(15, TimeUnit.SECONDS)
        pumpUntil { player.setCalls.size == 1 }
        assertFalse(player.playWhenReady)
        assertFalse("Old completion must not prepare after Pause/Stop", "prepare" in player.trace)
        assertEquals(if (explicit) "explicit" else "saved", player.currentMediaItem?.mediaId)
        assertEquals(if (explicit) 999L else 456L, player.currentPosition)
        // Policy invalidation after the data was chosen cannot revive the old Play fallback.
        callback.invalidatePublication()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(player.playWhenReady)
        second.play()
        pumpUntil { player.playWhenReady }
        assertEquals(1, player.setCalls.size)
        assertEquals(if (explicit) "explicit" else "saved", player.currentMediaItem?.mediaId)
        assertEquals(if (explicit) 999L else 456L, player.currentPosition)
    }

    @Test fun `new Play after Pause before explicit completion reuses chosen queue and plays`() {
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        second.play()
        pumpUntil { observed.resumptions.size == 1 }
        transport.pause()
        pumpUntil { player.trace.any { it.startsWith("play(false)") } }
        transport.play()
        pumpUntil { observed.resumptions.size == 2 }
        assertSame(observed.selection, observed.resumptions.last())
        gate.complete(Unit)
        pumpUntil { player.playWhenReady && player.setCalls.size == 1 }
        assertEquals("explicit", player.currentMediaItem?.mediaId)
        assertEquals(999L, player.currentPosition)
    }

    @Test fun `shared phone Pause on empty paused player suppresses late saved resumption`() =
        assertSharedTransport(stop = false, explicit = false)

    @Test fun `phone graceful Pause without fade suppresses pending saved resumption and graceful Play prepares`() =
        assertGracefulPauseDuringResumption(duration = 0L)

    @Test fun `phone graceful Pause with fade configured suppresses pending saved resumption and graceful Play prepares`() =
        assertGracefulPauseDuringResumption(duration = 500L)

    private fun assertGracefulPauseDuringResumption(duration: Long) {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        assertEquals(0, player.mediaItemCount)
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertFalse(player.isPlaying)
        assertFalse(player.playWhenReady)
        // Same modern adapter as Binder.gracefulPause, including the legacy no-audio branch.
        sharedGuard.pauseWithFade(duration)
        gate.complete(Unit)
        observed.resumptions.single().get(15, TimeUnit.SECONDS)
        pumpUntil { player.setCalls.size == 1 }
        assertEquals(listOf("[saved] index=0 position=456"), player.setCalls)
        assertFalse(player.playWhenReady)
        assertFalse("Late resumption must not prepare", "prepare" in player.trace)
        // Binder.gracefulPlay uses this actual helper; merely setting playWhenReady would
        // leave the queued player IDLE and would not prove a usable later phone Play.
        sharedGuard.fadeInEffect(duration)
        assertTrue(player.playWhenReady)
        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(1, player.trace.count { it == "prepare" })
        assertEquals(1, player.setCalls.size)
        assertEquals("saved", player.currentMediaItem?.mediaId)
        assertEquals(456L, player.currentPosition)
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
    }

    @Test fun `shared phone Stop preserves explicit queue without late playback`() =
        assertSharedTransport(stop = true, explicit = true)

    @Test fun `shared readiness false preserves explicit queue without late playback`() =
        assertSharedTransport(stop = false, explicit = true, readiness = true)

    private fun assertSharedTransport(stop: Boolean, explicit: Boolean, readiness: Boolean = false) {
        gatedId = if (explicit) "explicit" else "saved"
        if (explicit) {
            first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
            pumpUntil { entered.count == 0L }
        }
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        assertEquals(0, player.mediaItemCount)
        assertFalse(player.playWhenReady)
        // Exactly the guard exposed by Binder.player and used by the noisy-audio receiver.
        // SimpleBasePlayer may no-op an already-paused empty queue; intent must still count.
        if (stop) sharedGuard.stop() else if (readiness) sharedGuard.playWhenReady = false else sharedGuard.pause()
        gate.complete(Unit)
        observed.resumptions.single().get(15, TimeUnit.SECONDS)
        pumpUntil { player.setCalls.size == 1 }
        assertFalse(player.playWhenReady)
        assertFalse("Old completion must not prepare after shared transport", "prepare" in player.trace)
        assertEquals(if (explicit) "explicit" else "saved", player.currentMediaItem?.mediaId)
        assertEquals(if (explicit) 999L else 456L, player.currentPosition)
        sharedGuard.play()
        assertTrue(player.playWhenReady)
        assertEquals(1, player.setCalls.size)
    }

    @Test fun `guest blocked Stop does not veto pending valid Play after unlock`() {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        listenTogetherGuestLock.value = true
        sharedGuard.stop()
        assertFalse("stop" in player.trace)
        listenTogetherGuestLock.value = false
        gate.complete(Unit)
        pumpUntil { player.setCalls.size == 1 && player.playWhenReady }
        assertEquals("saved", player.currentMediaItem?.mediaId)
    }

    @Test fun `guest blocked helper tail cannot rearm paused pending completion`() {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        val guestIntents = mutableListOf<Boolean>()
        sharedGuard.onGuestPlayPause = { guestIntents += it }
        listenTogetherGuestLock.value = true
        sharedGuard.pause()
        sharedGuard.setMediaItem(item("blocked"))
        sharedGuard.prepare()
        sharedGuard.playWhenReady = true
        sharedGuard.play()
        assertEquals(listOf(false), guestIntents)
        listenTogetherGuestLock.value = false
        gate.complete(Unit)
        pumpUntil { player.setCalls.size == 1 }
        assertFalse(player.playWhenReady)
        assertFalse("prepare" in player.trace)
        assertEquals("saved", player.currentMediaItem?.mediaId)
        listenTogetherGuestLock.value = true
        guardClock += ListenTogetherGuestGuardPlayer.TAIL_WINDOW_MS + 1
        sharedGuard.play()
        assertTrue(player.playWhenReady)
        assertEquals(listOf(false, true), guestIntents)
    }

    private fun guardedSessionPlayer(): Player {
        sharedGuard = ListenTogetherGuestGuardPlayer(player, context, clock = { guardClock }, toastSink = {})
        return callback.wrapPlayer(sharedGuard)
    }

    private fun capturedExplicit(): MediaItem {
        val field = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply { isAccessible = true }
        val tree = field.get(callback) as AutoBrowseTree
        return tree.selectionStore.capture("album/real-session", listOf(item("explicit"))).single()
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id")
        .setCustomCacheKey(id).setMediaMetadata(MediaMetadata.Builder().setTitle(id).setIsPlayable(true).build()).build()

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Real session did not reach expected diagnostic checkpoint: ${player.trace}", condition())
    }

    private fun report(name: String, setup: String) {
        val path = File("build/reports/automotive-real-session/$name.txt")
        requireNotNull(path.parentFile).mkdirs()
        path.writeText(buildString {
            appendLine("QUEUE APPLICATION REGRESSION - ASSERTIONS PASSED")
            appendLine("Transport: real in-process modern MediaController + MediaSession + SimpleBasePlayer")
            appendLine("Legacy Android platform/Compat transport: unsupported by this fixture")
            appendLine(setup)
            appendLine("setCalls=${player.setCalls}")
            appendLine("finalQueue=${(0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }}")
            appendLine("finalIndex=${player.currentMediaItemIndex}; finalPosition=${player.currentPosition}; playWhenReady=${player.playWhenReady}")
            appendLine("trace:")
            player.trace.forEach { appendLine(it) }
        })
    }

    private class ObservedCallback(private val delegate: AutoSessionCallback) : MediaSession.Callback by delegate {
        var selection: ListenableFuture<MediaSession.MediaItemsWithStartPosition>? = null
        val resumptions = mutableListOf<ListenableFuture<MediaSession.MediaItemsWithStartPosition>>()
        override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
            items: MutableList<MediaItem>, index: Int, positionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            delegate.onSetMediaItems(session, controller, items, index, positionMs).also { selection = it }

        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            delegate.onPlaybackResumption(session, controller, isForPlayback).also { resumptions += it }
    }

    private class RecordingPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        val trace = mutableListOf<String>()
        val setCalls = mutableListOf<String>()
        private var queueIds = emptyList<String>()
        private var state = State.Builder().setAvailableCommands(Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP, Player.COMMAND_RELEASE
        ).build()).build()

        init {
            addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) { trace += "timeline reason=$reason count=${timeline.windowCount}" }
                override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                    trace += "discontinuity reason=$reason ${old.positionMs}->${new.positionMs}"
                }
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) { trace += "transition reason=$reason id=${item?.mediaId}" }
                override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) { trace += "playWhenReady=$value reason=$reason" }
            })
        }

        override fun getState(): State = state
        override fun handleSetMediaItems(items: MutableList<MediaItem>, index: Int, positionMs: Long): ListenableFuture<*> {
            val call = "${items.map { it.mediaId }} index=$index position=$positionMs"
            setCalls += call
            trace += "set $call"
            queueIds = items.map { it.mediaId }
            // Distinct timeline UIDs deliberately model a player rebuilding its queue on every set.
            state = state.buildUpon().setPlaylist(items.map { MediaItemData.Builder(Any()).setMediaItem(it).build() })
                .setCurrentMediaItemIndex(if (index == C.INDEX_UNSET) 0 else index)
                .setContentPositionMs(if (positionMs == C.TIME_UNSET) 0 else positionMs).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(value: Boolean): ListenableFuture<*> {
            trace += "play($value) queue=$queueIds"
            state = state.buildUpon().setPlayWhenReady(value, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> {
            trace += "prepare"
            state = state.buildUpon().setPlaybackState(Player.STATE_READY).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            trace += "stop"
            // Player.stop retains the playlist and position, and returns to IDLE.
            state = state.buildUpon().setPlaybackState(Player.STATE_IDLE).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> {
            trace += "release"
            return Futures.immediateVoidFuture()
        }
    }
}
