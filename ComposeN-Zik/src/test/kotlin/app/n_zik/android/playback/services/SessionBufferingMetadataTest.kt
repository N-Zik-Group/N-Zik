package app.n_zik.android.playback.services

import java.time.Duration
import java.util.concurrent.TimeUnit

import android.app.Application
import android.content.Context
import android.media.MediaMetadata as PlatformMediaMetadata
import android.media.MediaMetadata.METADATA_KEY_DURATION
import android.media.MediaMetadata.METADATA_KEY_TITLE
import android.media.session.MediaController
import android.media.session.MediaSession as PlatformMediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaController as Media3Controller
import app.n_zik.android.appContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.RealObject
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowMediaSession

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [SessionBufferingMetadataTest.PublishingSessionShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class SessionBufferingMetadataTest {
    private lateinit var context: Context
    private lateinit var upstream: PublishingPlayer
    private lateinit var facade: SessionArtworkPlayer
    private var session: MediaSession? = null
    private var controller: MediaController? = null
    private var transport: Media3Controller? = null
    private val metadataDurations = mutableListOf<Long>()
    private val nativeStates = mutableListOf<Int>()
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: PlatformMediaMetadata?) {
            metadata?.let { metadataDurations += it.getLong(METADATA_KEY_DURATION) }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state?.let { nativeStates += it.state }
        }
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        upstream = PublishingPlayer()
        facade = SessionArtworkPlayer(upstream)
    }

    @After
    fun tearDown() {
        controller?.unregisterCallback(callback)
        transport?.release()
        session?.release()
        upstream.release()
        shadowOf(Looper.getMainLooper()).idle()
        unmockkAll()
        PublishingSessionShadow.resetPublications()
    }

    @Test
    fun `platform session retains duration during loading source resolution and rebuffering`() {
        val native = connect()
        val track = item("finite", "3:00")
        upstream.publish(track)
        pumpUntil("initial buffering metadata") {
            native.metadata?.getLong(METADATA_KEY_DURATION) == 180_000L &&
                native.playbackState?.state == PlaybackState.STATE_BUFFERING &&
                180_000L in metadataDurations && PlaybackState.STATE_BUFFERING in nativeStates
        }
        assertEquals(C.TIME_UNSET, facade.duration)
        assertEquals(Player.STATE_BUFFERING, facade.playbackState)
        assertTrue(facade.playWhenReady)
        assertFalse(facade.isPlaying)
        assertTrue(requireNotNull(native.playbackState).actions and PlaybackState.ACTION_SEEK_TO != 0L)

        upstream.publish(track, durationMs = 192_000L, playbackState = Player.STATE_READY)
        pumpUntil("source duration resolves") {
            native.metadata?.getLong(METADATA_KEY_DURATION) == 192_000L &&
                native.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        upstream.publish(track, durationMs = 192_000L, positionMs = 45_000L, bufferedMs = 52_000L)
        pumpUntil("same item rebuffering") {
            native.playbackState?.state == PlaybackState.STATE_BUFFERING &&
                native.playbackState?.position == 45_000L
        }
        assertEquals(192_000L, requireNotNull(native.metadata).getLong(METADATA_KEY_DURATION))
        assertEquals(52_000L, requireNotNull(native.playbackState).bufferedPosition)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(45_000L, facade.currentPosition)
        assertEquals(45_000L, requireNotNull(native.playbackState).position)
        assertEquals(0f, requireNotNull(native.playbackState).playbackSpeed, 0f)

        requireNotNull(transport).pause()
        pumpUntil("pause while buffering") { !upstream.playWhenReady }
        assertEquals(Player.STATE_BUFFERING, facade.playbackState)
        requireNotNull(transport).seekTo(60_000L)
        pumpUntil("seek while buffering") { upstream.currentPosition == 60_000L }
        assertTrue(upstream.commands.contains("seek:60000"))
        assertFalse(upstream.playWhenReady)
        assertEquals(Player.STATE_BUFFERING, facade.playbackState)
    }

    @Test
    fun `new unknown and live items never inherit a finite duration`() {
        val native = connect()
        upstream.publish(item("finite", "3:00"))
        pumpUntil("finite duration") { native.metadata?.getLong(METADATA_KEY_DURATION) == 180_000L }
        for (invalid in listOf(null, "LIVE", "-1:30", "0:00", "1:99", "9999999999999999999:00")) {
            val next = item("unknown-$invalid", invalid)
            upstream.publish(next)
            pumpUntil("unknown duration for $invalid") {
                native.metadata?.getString(METADATA_KEY_TITLE) == next.mediaId &&
                    native.metadata?.getLong(METADATA_KEY_DURATION) == -1L
            }
            assertNull(facade.mediaMetadata.durationMs)
            assertEquals(C.TIME_UNSET, facade.duration)
        }
        upstream.publish(item("live", "3:00", declaredMs = 180_000L), live = true, seekable = false)
        pumpUntil("live metadata") {
            native.metadata?.getString(METADATA_KEY_TITLE) == "live"
        }
        assertEquals(-1L, requireNotNull(native.metadata).getLong(METADATA_KEY_DURATION))
        assertNull(facade.mediaMetadata.durationMs)
        assertTrue(facade.isCurrentMediaItemLive)
        assertEquals(Player.STATE_BUFFERING, facade.playbackState)
        assertFalse(requireNotNull(native.playbackState).actions and PlaybackState.ACTION_SEEK_TO != 0L)
    }

    @Test
    fun `reserved extras cannot overwrite actual duration or mutate original metadata`() {
        val native = connect()
        for (reserved in listOf(0L, 999_000L)) {
            val track = item("reserved-$reserved", "3:00", declaredMs = 180_000L, reservedMs = reserved)
            val originalExtras = requireNotNull(track.mediaMetadata.extras)
            upstream.publish(track, durationMs = 192_000L)
            pumpUntil("actual duration with reserved extra $reserved") {
                native.metadata?.getString(METADATA_KEY_TITLE) == track.mediaId &&
                    native.metadata?.getLong(METADATA_KEY_DURATION) == 192_000L
            }
            assertEquals(reserved, originalExtras.getLong(METADATA_KEY_DURATION))
            assertSame(originalExtras, track.mediaMetadata.extras)
            assertFalse(requireNotNull(facade.mediaMetadata.extras).containsKey(METADATA_KEY_DURATION))
            assertEquals("3:00", facade.mediaMetadata.extras?.getString("durationText"))
            assertEquals("kept", facade.mediaMetadata.extras?.getString("custom"))
        }
        upstream.publish(item("declared", null, declaredMs = 201_000L, reservedMs = 0L))
        pumpUntil("standard declared duration fallback") {
            native.metadata?.getLong(METADATA_KEY_DURATION) == 201_000L
        }
    }

    @Test
    fun `duration fallback does not grant restricted seek and preserves pause play`() {
        upstream.publish(item("restricted", "3:00"), seekable = false)
        val native = connect()
        pumpUntil("restricted buffering metadata") {
            native.metadata?.getLong(METADATA_KEY_DURATION) == 180_000L && native.playbackState != null
        }
        assertFalse(facade.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertFalse(requireNotNull(native.playbackState).actions and PlaybackState.ACTION_SEEK_TO != 0L)
        requireNotNull(transport).seekTo(10_000L)
        requireNotNull(transport).pause()
        pumpUntil("restricted pause") { !upstream.playWhenReady }
        assertFalse(upstream.commands.any { it.startsWith("seek:") })
        assertEquals(0L, upstream.currentPosition)
        requireNotNull(transport).play()
        pumpUntil("restricted play") { upstream.playWhenReady }
        assertEquals(Player.STATE_BUFFERING, facade.playbackState)
        assertEquals(180_000L, requireNotNull(native.metadata).getLong(METADATA_KEY_DURATION))
    }

    private fun connect(): MediaController {
        val realSession = MediaSession.Builder(context, facade).build().also { session = it }
        val transportFuture = Media3Controller.Builder(context, realSession.token).buildAsync()
        pumpUntil("Media3 transport connection") { transportFuture.isDone }
        transport = transportFuture.get(1, TimeUnit.SECONDS)
        return MediaController(context, realSession.platformToken).also {
            controller = it
            it.registerCallback(callback, Handler(Looper.getMainLooper()))
            PublishingSessionShadow.connect(it)
        }
    }

    private fun pumpUntil(checkpoint: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Platform session did not converge at $checkpoint; " +
            "duration=${controller?.metadata?.getLong(METADATA_KEY_DURATION)}, " +
            "state=${controller?.playbackState?.state}", condition())
    }

    private fun item(id: String, text: String?, declaredMs: Long? = null, reservedMs: Long? = null): MediaItem =
        MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(id).setArtist("Artist")
                .setDurationMs(declaredMs)
                .setExtras(Bundle().apply {
                    putString("durationText", text)
                    putString("custom", "kept")
                    reservedMs?.let { putLong(METADATA_KEY_DURATION, it) }
                }).build()).build()

    // Robolectric's session service is a no-op and its controller getters read shadow fields only.
    // Bridge actual platform setter arguments, after Media3 conversion, without deriving any metadata.
    @Implements(PlatformMediaSession::class)
    class PublishingSessionShadow : ShadowMediaSession() {
        @RealObject
        private lateinit var realSession: PlatformMediaSession

        @Implementation
        protected fun setMetadata(metadata: PlatformMediaMetadata?) {
            val publication = publications.getOrPut(realSession.sessionToken) { Publication() }
            publication.metadata = metadata
            publication.controllers.forEach { shadowOf(it).executeOnMetadataChanged(metadata) }
        }

        @Implementation
        protected fun setPlaybackState(state: PlaybackState?) {
            val publication = publications.getOrPut(realSession.sessionToken) { Publication() }
            publication.state = state
            publication.controllers.forEach { shadowOf(it).executeOnPlaybackStateChanged(state) }
        }

        private class Publication {
            var metadata: PlatformMediaMetadata? = null
            var state: PlaybackState? = null
            val controllers = mutableListOf<MediaController>()
        }

        companion object {
            private val publications = mutableMapOf<PlatformMediaSession.Token, Publication>()

            fun connect(controller: MediaController) {
                val publication = publications.getOrPut(controller.sessionToken) { Publication() }
                publication.controllers += controller
                publication.metadata?.let { shadowOf(controller).executeOnMetadataChanged(it) }
                publication.state?.let { shadowOf(controller).executeOnPlaybackStateChanged(it) }
            }

            @JvmStatic
            @Resetter
            fun resetPublications() = publications.clear()
        }
    }

    private class PublishingPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        val commands = mutableListOf<String>()
        private var state = State.Builder().setAvailableCommands(availableCommands(true)).build()

        override fun getState(): State = state

        fun publish(
            item: MediaItem,
            durationMs: Long = C.TIME_UNSET,
            playbackState: Int = Player.STATE_BUFFERING,
            positionMs: Long = 0L,
            bufferedMs: Long = positionMs,
            live: Boolean = false,
            seekable: Boolean = true,
        ) {
            state = state.buildUpon().setAvailableCommands(availableCommands(seekable))
                .setPlaylist(listOf(MediaItemData.Builder(item.mediaId).setMediaItem(item)
                    .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1000)
                    .setLiveConfiguration(if (live) MediaItem.LiveConfiguration.Builder().build() else null)
                    .setIsSeekable(seekable).build()))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(positionMs)
                .setContentBufferedPositionMs(PositionSupplier.getConstant(bufferedMs))
                .setPlaybackState(playbackState)
                .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
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
            state = state.buildUpon().setContentPositionMs(positionMs).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

        private fun availableCommands(seekable: Boolean): Player.Commands = Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_RELEASE,
        ).apply { if (seekable) add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }.build()
    }
}
