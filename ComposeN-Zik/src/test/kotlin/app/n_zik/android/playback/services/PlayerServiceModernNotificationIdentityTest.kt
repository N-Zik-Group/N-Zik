package app.n_zik.android.playback.services

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NotificationButtons
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.manageDownload
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.listentogether.listenTogetherGuestLock
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real service button builder and session publication with delayed data sources. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerServiceModernNotificationIdentityTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val rows = mutableMapOf<String, MutableSharedFlow<Song?>>()
    private val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    private lateinit var originalDownloads: MutableStateFlow<Map<String, Download>>
    private val published = mutableListOf<List<CommandButton>>()
    private lateinit var service: PlayerServiceModern

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns ApplicationProvider.getApplicationContext<Application>()
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        // Inject the real flow so the service observes the same source its actions read.
        originalDownloads = MyDownloadHelper.downloads
        MyDownloadHelper.downloads = downloads
        listenTogetherGuestLock.value = false
        service = spyk(Robolectric.buildService(PlayerServiceModern::class.java).get())
        // A spy copies fields, but the original inner Binder still captures its old service.
        field("binder").set(service, service.Binder())
        // Do not start audio, network, widgets or the service's production DATA scope.
        (field("coroutineScope").get(service) as CoroutineScope).cancel()
        field("coroutineScope").set(service, scope)
        field("crossfadeClockMs").set(service, { scheduler.currentTime })
        field("currentSong").set(service, notificationSongStates(service.currentMediaItem) { id ->
            rows.getOrPut(id) { MutableSharedFlow(extraBufferCapacity = 8) }
        }.stateIn(scope, SharingStarted.Lazily, NotificationSongState(null, null)))
        every { service.updateWidgets() } just Runs
        service.player = mockk<ExoPlayer> {
            every { repeatMode } returns Player.REPEAT_MODE_OFF
            every { shuffleModeEnabled } returns false
        }
        val session = mockk<MediaLibrarySession>()
        every { session.setMediaButtonPreferences(any<List<CommandButton>>()) } answers {
            published += firstArg<List<CommandButton>>().toList()
        }
        field("mediaSession").set(service, session)
        invoke("observeNotificationState")
        scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        scope.cancel()
        if (::originalDownloads.isInitialized) MyDownloadHelper.downloads = originalDownloads
        listenTogetherGuestLock.value = false
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `transition publishes B before Room responds and never republishes retained A`() {
        transition("A")
        row("A", 42L)
        download("A", Download.STATE_COMPLETED)
        assertIcons(R.drawable.heart, R.drawable.downloaded)

        // Publish before the flow switch runs: this is also the artwork-refresh race window.
        val publicationsBefore = published.size
        service.currentMediaItem.value = item("B")
        invoke("updateDefaultNotification")
        scheduler.runCurrent()
        assertIcons(R.drawable.heart_outline, R.drawable.download)
        assertTrue(published.drop(publicationsBefore).all { icon(it, NotificationButtons.Favorites) == R.drawable.heart_outline })
        row("B", -1L)
        assertIcons(R.drawable.heart_dislike, R.drawable.download)
    }

    @Test
    fun `late A row and download completion cannot change B buttons`() {
        transition("A")
        row("A", 42L)
        transition("B")
        row("A", 84L)
        download("A", Download.STATE_COMPLETED)
        assertIcons(R.drawable.heart_outline, R.drawable.download)
        assertEquals(Download.STATE_STOPPED, service.currentSongStateDownload.value)
    }

    @Test
    fun `B download changes and removal publish without any Room row`() {
        transition("B")
        for (state in listOf(Download.STATE_QUEUED, Download.STATE_DOWNLOADING)) {
            download("B", state)
            assertIcons(R.drawable.heart_outline, R.drawable.download_progress)
        }
        download("B", Download.STATE_COMPLETED)
        assertIcons(R.drawable.heart_outline, R.drawable.downloaded)
        downloads.value = emptyMap()
        scheduler.runCurrent()
        assertIcons(R.drawable.heart_outline, R.drawable.download)
    }

    @Test
    fun `rapid ABC transitions accept only C and empty queue resets both icons`() {
        transition("A")
        row("A", 42L)
        transition("B")
        service.currentMediaItem.value = item("C")
        row("B", 84L)
        download("A", Download.STATE_COMPLETED)
        row("C", 123L)
        assertIcons(R.drawable.heart, R.drawable.download)
        row("A", -1L)
        assertIcons(R.drawable.heart, R.drawable.download)
        service.currentMediaItem.value = null
        service.updateDownloadedState()
        scheduler.runCurrent()
        assertIcons(R.drawable.heart_outline, R.drawable.download)
    }

    @Test
    fun `download command captures B state instead of the stale cached A state`() {
        mockkStatic("app.it.fast4x.rimusic.utils.DownloadUtilsKt")
        every { manageDownload(any(), any(), any()) } just Runs
        val b = item("B")
        service.currentMediaItem.value = b
        service.currentSongStateDownload.value = Download.STATE_COMPLETED
        service.toggleDownload()
        verify(exactly = 1) { manageDownload(service, b, false) }

        download("B", Download.STATE_COMPLETED)
        service.currentSongStateDownload.value = Download.STATE_STOPPED
        service.toggleDownload()
        verify(exactly = 1) { manageDownload(service, b, true) }
    }

    @Test
    fun `prefixed Room lookup preserves full identity and session command IDs`() {
        transition("album/A")
        row("A", 42L)
        assertIcons(R.drawable.heart, R.drawable.download)
        transition("playlist/A")
        assertIcons(R.drawable.heart_outline, R.drawable.download)
        row("A", 84L)
        assertIcons(R.drawable.heart, R.drawable.download)
        assertEquals(NotificationButtons.entries.map { it.sessionCommand.customAction }.toSet(),
            published.last().map { it.sessionCommand?.customAction }.toSet())
        assertEquals(R.drawable.repeat, icon(published.last(), NotificationButtons.Repeat))
        assertEquals(R.drawable.shuffle, icon(published.last(), NotificationButtons.Shuffle))
    }

    @Test
    fun `real crossfade handoff publishes incoming notification identity and preserves playback settings`() {
        transition("A")
        row("A", 42L)
        download("A", Download.STATE_COMPLETED)
        val (outgoing, incoming) = prepareHandoff("B")

        invoke("startCrossfade")
        scheduler.runCurrent()

        assertSame(incoming, service.player)
        assertEquals("B", service.currentMediaItem.value?.mediaId)
        assertIcons(R.drawable.heart_outline, R.drawable.download)
        verify { service.onMediaItemTransition(match { it?.mediaId == "B" }, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) }
        verify { outgoing.pauseAtEndOfMediaItems = true }
        verify { outgoing.repeatMode = Player.REPEAT_MODE_OFF }
        verify { incoming.playbackParameters = PlaybackParameters(1.5f) }
        verify { incoming.skipSilenceEnabled = true }
        // Refresh settings at handoff as well as preload.
        verify { incoming.repeatMode = Player.REPEAT_MODE_ALL }
        verify { incoming.shuffleModeEnabled = true }
        assertTrue(field("isCrossfading").getBoolean(service))
    }

    @Test
    fun `incoming resume cannot restart outgoing after end of item`() {
        val outgoingListeners = mutableListOf<Player.Listener>()
        val incomingListeners = mutableListOf<Player.Listener>()
        val (outgoing, _) = prepareHandoff("B", outgoingListeners, incomingListeners)
        invoke("startCrossfade")
        outgoingListeners.last().onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)
        // Simulates buffering and a subsequent resume of the incoming track.
        incomingListeners.first().onIsPlayingChanged(false)
        incomingListeners.first().onIsPlayingChanged(true)
        verify(exactly = 0) { outgoing.play() }
        verify(atLeast = 1) { outgoing.pause() }
    }

    @Test
    fun `completed fade explicitly detaches both pair listeners and releases only outgoing`() {
        val outgoingListeners = mutableListOf<Player.Listener>()
        val incomingListeners = mutableListOf<Player.Listener>()
        val (outgoing, incoming) = prepareHandoff("B", outgoingListeners, incomingListeners)
        invoke("startCrossfade")
        scheduler.advanceTimeBy(3001)
        scheduler.runCurrent()
        verify(exactly = 1) { outgoing.release() }
        verify(exactly = 0) { incoming.release() }
        verify { incoming.removeListener(incomingListeners.first()) }
        verify { outgoing.removeListener(outgoingListeners.last()) }
        assertFalse(field("isCrossfading").getBoolean(service))
    }

    @Test
    fun `cancelled job cannot clean up replacement fade or mutate replacement players`() {
        val (a, b) = prepareHandoff("B")
        invoke("startCrossfade")
        scheduler.runCurrent() // Ensure the first fade entered try-finally before cancellation.
        invoke("cancelCrossfadeAndReset")
        val (_, c) = prepareHandoff("C", outgoing = b)
        invoke("startCrossfade")
        scheduler.runCurrent() // Runs the cancelled first job's finally after the second swap.
        assertSame(c, service.player)
        assertTrue(field("isCrossfading").getBoolean(service))
        verify(exactly = 1) { a.release() }
        verify(exactly = 0) { b.release() }
        verify(exactly = 0) { c.release() }
        scheduler.advanceTimeBy(3001)
        scheduler.runCurrent()
        verify(exactly = 1) { b.release() }
        verify(exactly = 0) { c.release() }
    }

    @Test
    fun `repeat-one handoff publishes repeat reason so radio does not append another track`() {
        val (outgoing, _) = prepareHandoff("A")
        every { outgoing.repeatMode } returns Player.REPEAT_MODE_ONE
        invoke("startCrossfade")
        verify { service.onMediaItemTransition(match { it?.mediaId == "A" }, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) }
        verify(exactly = 0) { service.onMediaItemTransition(any(), Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) }
    }

    @Test
    fun `throwing transition restores incoming volume clears guard and cleans up outgoing`() {
        val (outgoing, incoming) = prepareHandoff("B")
        every { service.onMediaItemTransition(any(), any()) } throws IllegalStateException("transition side effect failed")
        invoke("startCrossfade")
        scheduler.runCurrent()
        assertSame(incoming, service.player)
        assertFalse(field("isInternalCrossfadeSeek").getBoolean(service))
        assertFalse(field("isCrossfading").getBoolean(service))
        verify { incoming.volume = 0.8f }
        verify(exactly = 1) { outgoing.release() }
        verify(exactly = 0) { incoming.release() }
    }

    private fun prepareHandoff(
        id: String,
        outgoingListeners: MutableList<Player.Listener> = mutableListOf(),
        incomingListeners: MutableList<Player.Listener> = mutableListOf(),
        outgoing: ExoPlayer = fadePlayer("A", outgoingListeners),
    ): Pair<ExoPlayer, ExoPlayer> {
        val incoming = fadePlayer(id, incomingListeners)
        service.player = outgoing
        field("secondaryPlayer").set(service, incoming)
        // Keep the real handoff and shared identity/button publication. The remaining transition
        // work needs the running service's DB, radio, download cache and artwork infrastructure.
        every { service.onMediaItemTransition(any(), any()) } answers {
            PlayerServiceModern::class.java.getDeclaredMethod("publishCurrentMediaItem", MediaItem::class.java)
                .apply { isAccessible = true }.invoke(service, firstArg<MediaItem?>())
            Unit
        }
        val session = field("mediaSession").get(service) as MediaLibrarySession
        every { session.player = any() } just Runs
        return outgoing to incoming
    }

    private fun fadePlayer(id: String, listeners: MutableList<Player.Listener>) = mockk<ExoPlayer>(relaxed = true) {
        every { audioSessionId } returns 77
        every { currentMediaItem } returns item(id)
        every { volume } returns 0.8f
        every { playWhenReady } returns true
        every { repeatMode } returns Player.REPEAT_MODE_ALL
        every { shuffleModeEnabled } returns true
        every { playbackParameters } returns PlaybackParameters(1.5f)
        every { skipSilenceEnabled } returns true
        every { playbackState } returns Player.STATE_READY
        every { addListener(any()) } answers { listeners += firstArg<Player.Listener>(); Unit }
    }

    private fun transition(id: String) {
        service.currentMediaItem.value = item(id)
        service.updateDownloadedState()
        scheduler.runCurrent()
    }

    private fun row(id: String, likedAt: Long?) {
        rows.getOrPut(id) { MutableSharedFlow(extraBufferCapacity = 8) }
            .tryEmit(Song.makePlaceholder(id).copy(likedAt = likedAt))
        scheduler.runCurrent()
    }

    private fun download(id: String, state: Int) {
        val request = DownloadRequest.Builder(id, Uri.parse("https://example.com/$id")).build()
        val download = Download(request, state, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
        downloads.value = downloads.value + (id to download)
        scheduler.runCurrent()
    }

    private fun assertIcons(like: Int, download: Int) {
        assertEquals(like, icon(published.last(), NotificationButtons.Favorites))
        assertEquals(download, icon(published.last(), NotificationButtons.Download))
    }

    private fun icon(buttons: List<CommandButton>, button: NotificationButtons): Int =
        buttons.single { it.sessionCommand == button.sessionCommand }.iconResId

    private fun item(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()

    private fun field(name: String) = PlayerServiceModern::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun invoke(name: String) = PlayerServiceModern::class.java.getDeclaredMethod(name).apply {
        isAccessible = true
    }.invoke(service)
}
