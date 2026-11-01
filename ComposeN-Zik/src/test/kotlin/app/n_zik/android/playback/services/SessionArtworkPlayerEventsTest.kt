package app.n_zik.android.playback.services

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SessionArtworkPlayerEventsTest {
    private lateinit var context: Context
    private lateinit var upstream: PublishingPlayer
    private lateinit var facade: SessionArtworkPlayer
    private var previousCrop: Boolean? = null
    private var session: MediaSession? = null
    private val controllers = mutableListOf<MediaController>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        previousCrop = context.preferences.takeIf { it.contains(cropVideoThumbnailsKey) }
            ?.getBoolean(cropVideoThumbnailsKey, true)
        context.preferences.edit().putBoolean(cropVideoThumbnailsKey, true).commit()
        upstream = PublishingPlayer()
        facade = SessionArtworkPlayer(upstream)
    }

    @After
    fun tearDown() {
        controllers.forEach { it.release() }
        session?.release()
        if (::upstream.isInitialized) upstream.release()
        shadowOf(Looper.getMainLooper()).idle()
        if (::context.isInitialized) {
            context.preferences.edit().apply {
                previousCrop?.let { putBoolean(cropVideoThumbnailsKey, it) }
                    ?: remove(cropVideoThumbnailsKey)
            }.commit()
        }
        unmockkAll()
    }

    @Test
    fun `connected empty session receives upstream playback artwork refresh and transport`() {
        val realSession = MediaSession.Builder(context, facade).build().also { session = it }
        val transport = connect(realSession)
        val observer = connect(realSession)
        assertTrue(observer.currentTimeline.isEmpty)
        assertEquals(Player.STATE_IDLE, observer.playbackState)
        assertFalse(observer.playWhenReady)

        // No controller command may mask the broken session's cached initial state.
        upstream.publish(item("video"))
        pumpUntil("upstream timeline and playback state") {
            observer.mediaItemCount == 1 && observer.playbackState == Player.STATE_READY && observer.isPlaying
        }
        assertEquals("video", observer.currentMediaItem?.mediaId)
        assertTrue(observer.playWhenReady)
        assertEquals("Title video", observer.mediaMetadata.title.toString())
        val originalArtwork = requireNotNull(observer.mediaMetadata.artworkUri)
        assertEquals("content", originalArtwork.scheme)
        assertEquals(ArtworkContentProvider.AUTHORITY, originalArtwork.authority)
        assertEquals("remote", originalArtwork.pathSegments.single())
        assertEquals("true", originalArtwork.getQueryParameter("c"))
        assertEquals(upstream.mediaMetadata.artworkUri.toString(), originalArtwork.getQueryParameter("u"))

        context.preferences.edit().putBoolean(cropVideoThumbnailsKey, false).commit()
        facade.refreshArtwork()
        pumpUntil("live crop artwork refresh") {
            observer.mediaMetadata.artworkUri?.getQueryParameter("c") == "false"
        }
        val uncroppedArtwork = requireNotNull(observer.mediaMetadata.artworkUri)
        assertNotEquals(originalArtwork, uncroppedArtwork)
        ArtworkRevisions.invalidate()
        facade.refreshArtwork()
        pumpUntil("live revision artwork refresh") {
            observer.mediaMetadata.artworkUri?.getQueryParameter("r") != uncroppedArtwork.getQueryParameter("r")
        }
        assertEquals("false", observer.mediaMetadata.artworkUri?.getQueryParameter("c"))
        assertTrue(observer.isPlaying)
        assertEquals("video", observer.currentMediaItem?.mediaId)
        assertTrue("Artwork refresh must not restart playback", upstream.commands.isEmpty())
        assertEquals(1, upstream.publications)

        // The second controller never issues commands, so its state cannot be optimistic masking.
        transport.pause()
        pumpUntil("remote pause") { upstream.commands.contains("play:false") && !observer.playWhenReady }
        transport.play()
        pumpUntil("remote play") { upstream.commands.contains("play:true") && observer.isPlaying }
        transport.pause()
        pumpUntil("pause before seek") { !upstream.playWhenReady && !observer.playWhenReady }
        transport.seekTo(4321L)
        pumpUntil("remote seek") { upstream.commands.contains("seek:4321") && observer.currentPosition == 4321L }
        transport.stop()
        pumpUntil("remote stop") { upstream.commands.contains("stop") && observer.playbackState == Player.STATE_IDLE }
        assertEquals("video", observer.currentMediaItem?.mediaId)
    }

    @Test
    fun `listener identity duplicate registration removal and reattachment preserve event delivery`() {
        val first = RecordingListener()
        val second = RecordingListener()
        assertEquals(first, second)
        facade.addListener(first)
        facade.addListener(first)
        facade.addListener(second)
        assertEquals(Player.STATE_IDLE, facade.playbackState)
        upstream.publish(item("first"))
        shadowOf(Looper.getMainLooper()).idle()

        listOf(first, second).forEach { listener ->
            assertEquals(listOf(1), listener.timelineSizes)
            assertEquals(listOf(Player.STATE_READY), listener.states)
            assertEquals(listOf(true), listener.readiness)
            assertEquals(listOf(true), listener.playing)
            assertEquals(listOf("first"), listener.transitions)
            assertEquals(1, listener.metadata.size)
            assertEquals(ArtworkContentProvider.AUTHORITY, listener.metadata.single().artworkUri?.authority)
            assertEquals(1, listener.eventPlayers.size)
            assertSame(facade, listener.eventPlayers.single())
            assertTrue(Player.EVENT_TIMELINE_CHANGED in listener.eventFlags)
            assertTrue(Player.EVENT_PLAYBACK_STATE_CHANGED in listener.eventFlags)
            assertTrue(Player.EVENT_PLAY_WHEN_READY_CHANGED in listener.eventFlags)
        }

        facade.removeListener(first)
        facade.removeListener(first)
        upstream.publish(item("second"))
        shadowOf(Looper.getMainLooper()).idle()
        facade.refreshArtwork()
        assertEquals(listOf("first"), first.transitions)
        assertEquals(1, first.metadata.size)
        assertEquals(1, first.eventPlayers.size)
        assertEquals(listOf("first", "second"), second.transitions)
        assertEquals(3, second.metadata.size)
        assertEquals(3, second.eventPlayers.size)

        facade.addListener(first)
        upstream.publish(item("third"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("first", "third"), first.transitions)
        assertEquals(listOf("first", "second", "third"), second.transitions)
        assertEquals(2, first.metadata.size)
        facade.removeListener(first)
        facade.removeListener(second)
        facade.refreshArtwork()
        assertEquals(2, first.metadata.size)
        assertEquals(4, second.metadata.size)
    }

    private fun connect(realSession: MediaSession): MediaController {
        val future = MediaController.Builder(context, realSession.token).buildAsync()
        pumpUntil("controller connection") { future.isDone }
        return future.get(1, TimeUnit.SECONDS).also { controllers += it }
    }

    private fun pumpUntil(checkpoint: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Session did not converge at $checkpoint; upstream commands=${upstream.commands}", condition())
    }

    private fun item(id: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://example.invalid/$id")
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle("Title $id")
            .setArtist("Artist")
            .setArtworkUri(Uri.parse("https://i.ytimg.com/vi/$id/maxresdefault.jpg"))
            .build())
        .build()

    private class RecordingListener : Player.Listener {
        val timelineSizes = mutableListOf<Int>()
        val states = mutableListOf<Int>()
        val readiness = mutableListOf<Boolean>()
        val playing = mutableListOf<Boolean>()
        val transitions = mutableListOf<String?>()
        val metadata = mutableListOf<MediaMetadata>()
        val eventPlayers = mutableListOf<Player>()
        val eventFlags = mutableSetOf<Int>()

        override fun onTimelineChanged(timeline: Timeline, reason: Int) { timelineSizes += timeline.windowCount }
        override fun onPlaybackStateChanged(playbackState: Int) { states += playbackState }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { readiness += playWhenReady }
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing += isPlaying }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions += mediaItem?.mediaId }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { metadata += mediaMetadata }
        override fun onEvents(player: Player, events: Player.Events) {
            eventPlayers += player
            repeat(events.size()) { eventFlags += events.get(it) }
        }

        override fun equals(other: Any?): Boolean = other is RecordingListener
        override fun hashCode(): Int = 1
    }

    private class PublishingPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        val commands = mutableListOf<String>()
        var publications = 0
            private set
        private var state = State.Builder().setAvailableCommands(Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_RELEASE
        ).build()).build()

        override fun getState(): State = state

        fun publish(item: MediaItem) {
            publications++
            state = state.buildUpon()
                .setPlaylist(listOf(MediaItemData.Builder(Any()).setMediaItem(item)
                    .setIsSeekable(true).setDurationUs(120_000_000L).build()))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(0L)
                .setPlaybackState(Player.STATE_READY)
                .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
            invalidateState()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            commands += "play:$playWhenReady"
            state = state.buildUpon()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            commands += "seek:$positionMs"
            state = state.buildUpon().setCurrentMediaItemIndex(mediaItemIndex).setContentPositionMs(positionMs).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            commands += "stop"
            state = state.buildUpon().setPlaybackState(Player.STATE_IDLE).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }
}
