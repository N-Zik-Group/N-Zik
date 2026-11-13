package app.n_zik.android.playback.services.automotive.session

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

import android.app.Application
import android.content.Context
import android.media.MediaMetadata as PlatformMetadata
import android.media.session.MediaSession as PlatformSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import app.n_zik.android.utils.preferences
import app.n_zik.android.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.SessionArtworkPlayer
import app.n_zik.android.playback.services.SeekCoalescingPlayer
import app.n_zik.android.playback.services.diagnostics.DiagExoPlayer
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.annotation.Resetter
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowMediaSession

/**
 * Exercises Media3's actual legacy callback and native publication conversion with production
 * automotive queue ownership. Robolectric has no media-service Binder transport: the shadow calls
 * the framework callback registered by Media3, on its registered handler, with an AA caller identity.
 * It does not call AutoSessionCallback directly or claim head-unit/SystemUI rendering coverage.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [AutoLegacyPlaybackStateTest.LegacySessionShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class AutoLegacyPlaybackStateTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var session: MediaLibrarySession
    private lateinit var raw: PublishingPlayer
    private lateinit var coalescer: SeekCoalescingPlayer
    private lateinit var guard: ListenTogetherGuestGuardPlayer
    private lateinit var facade: SessionArtworkPlayer

    @Before
    fun setUp() {
        LegacySessionShadow.resetPublications()
        context = RuntimeEnvironment.getApplication()
        listenTogetherGuestLock.value = false
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        context.preferences.edit().clear().commit()
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        mockkObject(Database)
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        every { songs.findAllTitleArtistContains(any()) } answers {
            val id = firstArg<String>()
            flowOf(listOf(Song(id, id, durationText = "3:00", thumbnailUrl = null)))
        }
        every { songs.findById(any()) } answers {
            val id = firstArg<String>()
            flowOf(Song(id, id, durationText = "3:00", thumbnailUrl = null))
        }
        mockkConstructor(AutoAccessPolicy::class)
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            firstArg<List<MediaItem>>().withIndex().toList()
        }
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        raw = PublishingPlayer()
        coalescer = SeekCoalescingPlayer(DiagExoPlayer(exoPlayerAdapter(raw)))
        guard = ListenTogetherGuestGuardPlayer(coalescer, context, toastSink = {})
        facade = SessionArtworkPlayer(callback.wrapPlayer(guard))
        session = MediaLibrarySession.Builder(context, facade, callback).build()
        LegacySessionShadow.selectSession(session.platformToken)
        raw.addListener(publicationTrace("raw"))
        facade.addListener(publicationTrace("facade"))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        try {
            if (::session.isInitialized) session.release()
            if (::callback.isInitialized) callback.release()
            if (::coalescer.isInitialized) coalescer.release()
            else if (::raw.isInitialized) raw.release()
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            listenTogetherGuestLock.value = false
            try {
                if (::context.isInitialized) context.preferences.edit().clear().commit()
            } finally {
                unmockkAll()
                LegacySessionShadow.resetPublications()
            }
        }
    }

    @Test
    fun `unrelated platform session cannot overwrite selected publication or transport`() {
        guard.setMediaItem(item("selected-session", 210_000L))
        guard.prepare()
        guard.play()
        assertBuffering("selected-session", 210_000L)
        val unrelated = PlatformSession(context, "unrelated-session")
        try {
            unrelated.setCallback(object : PlatformSession.Callback() {}, Handler(Looper.getMainLooper()))
            unrelated.setMetadata(PlatformMetadata.Builder()
                .putString(PlatformMetadata.METADATA_KEY_MEDIA_ID, "unrelated-track")
                .putLong(PlatformMetadata.METADATA_KEY_DURATION, 192_000L).build())
            unrelated.setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 0L, 0f).build())
            assertEquals("selected-session", LegacySessionShadow.metadata?.getString(PlatformMetadata.METADATA_KEY_MEDIA_ID))
            assertEquals(210_000L, LegacySessionShadow.metadata?.getLong(PlatformMetadata.METADATA_KEY_DURATION))
            assertEquals(PlaybackState.STATE_BUFFERING, LegacySessionShadow.state?.state)
            LegacySessionShadow.playFromMediaId(capture(item("selected-replacement", 220_000L)))
            assertBuffering("selected-replacement", 220_000L)
        } finally {
            unrelated.release()
        }
    }

    @Test
    fun `stale AA snapshot publishes stored duration from its first native metadata event`() {
        val track = item("stale-duration", 180_000L).buildUpon()
            .setMediaMetadata(item("stale-duration", 180_000L).mediaMetadata.buildUpon().setDurationMs(null).build()).build()
        val captured = capture(track)
        every { Database.songTable.findByIdsDirect(listOf("stale-duration")) } returns
            listOf(Song("stale-duration", "Database title", durationText = "3:35", thumbnailUrl = null))
        LegacySessionShadow.playFromMediaId(captured)
        assertBuffering("stale-duration", 215_000L)
        val events = LegacySessionShadow.metadataHistory.filter {
            it.getString(PlatformMetadata.METADATA_KEY_MEDIA_ID) == "stale-duration"
        }
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.getLong(PlatformMetadata.METADATA_KEY_DURATION) == 215_000L })
        assertEquals(track.localConfiguration, raw.currentMediaItem?.localConfiguration)
        assertEquals(track.mediaMetadata.title, raw.currentMediaItem?.mediaMetadata?.title)
    }

    @Test
    fun `truly unknown AA duration remains unknown until the source resolves it`() {
        val known = item("unknown-duration", 180_000L)
        val track = known.buildUpon().setMediaMetadata(known.mediaMetadata.buildUpon().setDurationMs(null).build()).build()
        every { Database.songTable.findByIdsDirect(listOf("unknown-duration")) } returns emptyList()
        LegacySessionShadow.playFromMediaId(capture(track))
        pumpUntil("unknown AA duration") {
            raw.currentMediaItem?.mediaId == track.mediaId &&
                LegacySessionShadow.state?.state == PlaybackState.STATE_BUFFERING
        }
        assertEquals(-1L, requireNotNull(LegacySessionShadow.metadata).getLong(PlatformMetadata.METADATA_KEY_DURATION))
        raw.publishReady(215_241L)
        assertReady(215_241L)
    }

    @Test
    fun `legacy AA start publishes buffering duration and ready state through full wrapper chain`() {
        val track = item("first", 180_000L)
        LegacySessionShadow.playFromMediaId(capture(track))
        assertBuffering("first", 180_000L)
        assertEquals(1, LegacySessionShadow.playFromMediaIdCalls)
        assertEquals(listOf("first"), raw.setCalls)
        raw.publishReady(192_000L)
        assertReady(192_000L)

        LegacySessionShadow.playFromMediaId(capture(item("replacement", 210_000L)))
        assertBuffering("replacement", 210_000L)
        assertEquals(listOf("first", "replacement"), raw.setCalls)
        raw.publishReady(219_000L)
        assertReady(219_000L)
    }

    @Test
    fun `phone start and subsequent legacy AA replacement retain identical platform state contract`() {
        guard.setMediaItem(item("phone", 180_000L))
        guard.prepare()
        guard.play()
        assertBuffering("phone", 180_000L)
        raw.publishReady(192_000L)
        assertReady(192_000L)

        LegacySessionShadow.playFromMediaId(capture(item("automotive", 210_000L)))
        assertBuffering("automotive", 210_000L)
        raw.publishReady(219_000L)
        assertReady(219_000L)
    }

    @Test
    fun `legacy voice play resolves metadata and publishes buffering then ready`() {
        LegacySessionShadow.request { onPlayFromSearch("voiceplay01", null) }
        assertBuffering("voiceplay01", 180_000L)
        raw.publishReady(192_000L)
        assertReady(192_000L)
    }

    @Test
    fun `legacy URI play resolves metadata and publishes buffering then ready`() {
        LegacySessionShadow.request { onPlayFromUri(Uri.parse("https://youtu.be/uriplay0001"), null) }
        assertBuffering("uriplay0001", 180_000L)
        raw.publishReady(192_000L)
        assertReady(192_000L)
    }

    @Test
    fun `legacy voice prepare loads duration without starting playback`() {
        LegacySessionShadow.request { onPrepareFromSearch("voiceprep01", null) }
        assertPrepared("voiceprep01")
    }

    @Test
    fun `legacy URI prepare loads duration without starting playback`() {
        LegacySessionShadow.request { onPrepareFromUri(Uri.parse("https://youtu.be/uriprep0001"), null) }
        assertPrepared("uriprep0001")
    }

    private fun assertPrepared(id: String) {
        pumpUntil("prepared $id") {
            raw.currentMediaItem?.mediaId == id && raw.playbackState == Player.STATE_BUFFERING &&
                LegacySessionShadow.metadata?.getLong(PlatformMetadata.METADATA_KEY_DURATION) == 180_000L &&
                LegacySessionShadow.state?.state == PlaybackState.STATE_PAUSED
        }
        assertFalse(raw.playWhenReady)
        assertEquals(listOf(id), raw.setCalls)
        raw.publishReady(192_000L)
        pumpUntil("prepared duration") {
            LegacySessionShadow.metadata?.getLong(PlatformMetadata.METADATA_KEY_DURATION) == 192_000L
        }
        assertEquals(PlaybackState.STATE_PAUSED, requireNotNull(LegacySessionShadow.state).state)
        assertFalse(facade.isPlaying)
    }

    // DiagExoPlayer requires ExoPlayer; all exercised Player methods still reach the stateful engine.
    private fun exoPlayerAdapter(player: Player): ExoPlayer = Proxy.newProxyInstance(
        ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "PublishingExoPlayer"
            else -> try {
                Player::class.java.getMethod(method.name, *method.parameterTypes)
                    .invoke(player, *(args ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        }
    } as ExoPlayer

    private fun assertBuffering(id: String, declaredMs: Long) {
        pumpUntil("buffering $id") {
            raw.currentMediaItem?.mediaId == id && raw.playWhenReady &&
                LegacySessionShadow.metadata?.getLong(PlatformMetadata.METADATA_KEY_DURATION) == declaredMs &&
                LegacySessionShadow.state?.state == PlaybackState.STATE_BUFFERING
        }
        assertEquals(Player.STATE_BUFFERING, facade.playbackState)
        assertFalse(facade.isPlaying)
        assertEquals(C.TIME_UNSET, facade.duration)
        assertEquals(0f, requireNotNull(LegacySessionShadow.state).playbackSpeed, 0f)
        assertTrue(requireNotNull(LegacySessionShadow.state).actions and PlaybackState.ACTION_SEEK_TO != 0L)
    }

    private fun assertReady(actualMs: Long) {
        pumpUntil("ready") {
            LegacySessionShadow.state?.state == PlaybackState.STATE_PLAYING &&
                LegacySessionShadow.metadata?.getLong(PlatformMetadata.METADATA_KEY_DURATION) == actualMs
        }
        assertEquals(Player.STATE_READY, facade.playbackState)
        assertTrue(facade.isPlaying)
        assertEquals(actualMs, facade.duration)
        assertEquals(1f, requireNotNull(LegacySessionShadow.state).playbackSpeed, 0f)
    }

    private fun capture(item: MediaItem): String {
        val field = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply { isAccessible = true }
        val tree = field.get(callback) as AutoBrowseTree
        return tree.selectionStore.capture("album/legacy-state", listOf(item)).single().mediaId
    }

    private fun item(id: String, durationMs: Long): MediaItem = MediaItem.Builder()
        .setMediaId(id).setUri("https://example.invalid/$id").setCustomCacheKey(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(id).setArtist("Artist")
            .setDurationMs(durationMs).setIsPlayable(true).build()).build()

    private fun pumpUntil(checkpoint: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("$checkpoint: rawState=${raw.playbackState}, playWhenReady=${raw.playWhenReady}, " +
            "nativeState=${LegacySessionShadow.state?.state}, " +
            "duration=${LegacySessionShadow.metadata?.getLong(PlatformMetadata.METADATA_KEY_DURATION)}, " +
            "rawId=${raw.currentMediaItem?.mediaId}, facadeId=${facade.currentMediaItem?.mediaId}, " +
            "rawDuration=${raw.duration}, facadeDuration=${facade.duration}, " +
            "facadeMetadataDuration=${facade.mediaMetadata.durationMs}, rawMetadataDuration=${raw.mediaMetadata.durationMs}, " +
            "itemMetadataDuration=${raw.currentMediaItem?.mediaMetadata?.durationMs}, " +
            "nativeId=${LegacySessionShadow.metadata?.getString(PlatformMetadata.METADATA_KEY_MEDIA_ID)}, " +
            "nativeError=${LegacySessionShadow.state?.errorMessage}, " +
            "publications=${LegacySessionShadow.publicationTrace}, " +
            "setCalls=${raw.setCalls}", condition())
    }

    private fun publicationTrace(source: String) = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            LegacySessionShadow.trace("$source timeline:$reason id=${raw.currentMediaItem?.mediaId} duration=${raw.duration}")
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            LegacySessionShadow.trace("$source metadata:${mediaMetadata.title} duration=${mediaMetadata.durationMs}")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            LegacySessionShadow.trace("$source item:${mediaItem?.mediaId} reason=$reason")
        }
    }

    /** Captures framework publications unchanged and supplies only the missing Binder entry point. */
    @Implements(PlatformSession::class)
    class LegacySessionShadow : ShadowMediaSession() {
        @RealObject
        private lateinit var realSession: PlatformSession

        private val publication: Publication
            get() = publications.getOrPut(realSession.sessionToken) { Publication() }

        @Implementation
        protected fun setCallback(callback: PlatformSession.Callback?, handler: Handler?) {
            publication.frameworkCallback = callback
            publication.callbackHandler = handler
        }

        @Implementation
        protected fun getCurrentControllerInfo(): MediaSessionManager.RemoteUserInfo =
            MediaSessionManager.RemoteUserInfo("com.google.android.projection.gearhead", 123, android.os.Process.myUid())

        @Implementation
        protected fun setMetadata(value: PlatformMetadata?) {
            trace("native@${System.identityHashCode(this)} metadata:" +
                "${value?.getString(PlatformMetadata.METADATA_KEY_MEDIA_ID)} " +
                "duration=${value?.getLong(PlatformMetadata.METADATA_KEY_DURATION)}")
            publication.metadata = value
            value?.let { publication.metadataHistory += it }
        }

        @Implementation
        protected fun setPlaybackState(value: PlaybackState?) {
            trace("native@${System.identityHashCode(this)} state:${value?.state} error=${value?.errorMessage}")
            publication.state = value
        }

        private class Publication {
            val metadataHistory = mutableListOf<PlatformMetadata>()
            var metadata: PlatformMetadata? = null
            var state: PlaybackState? = null
            var frameworkCallback: PlatformSession.Callback? = null
            var callbackHandler: Handler? = null
        }

        companion object {
            private val publications = mutableMapOf<PlatformSession.Token, Publication>()
            private var selectedToken: PlatformSession.Token? = null
            private val selectedPublication: Publication
                get() = requireNotNull(publications[selectedToken]) { "Select the platform session before observing it" }
            val metadataHistory: List<PlatformMetadata> get() = selectedPublication.metadataHistory
            val publicationTrace = mutableListOf<String>()
            fun trace(event: String) {
                if (publicationTrace.size == 100) publicationTrace.removeAt(0)
                publicationTrace += event
            }
            val metadata: PlatformMetadata? get() = selectedPublication.metadata
            val state: PlaybackState? get() = selectedPublication.state
            var playFromMediaIdCalls = 0
                private set

            fun selectSession(token: PlatformSession.Token) {
                selectedToken = token
                publications.getOrPut(token) { Publication() }
            }

            fun playFromMediaId(id: String) {
                request {
                    playFromMediaIdCalls++
                    onPlayFromMediaId(id, null)
                }
            }

            fun request(action: PlatformSession.Callback.() -> Unit) {
                val publication = selectedPublication
                val target = requireNotNull(publication.frameworkCallback)
                requireNotNull(publication.callbackHandler).post { target.action() }
            }

            @JvmStatic
            @Resetter
            fun resetPublications() {
                publicationTrace.clear()
                publications.clear()
                selectedToken = null
                playFromMediaIdCalls = 0
            }
        }
    }

    private class PublishingPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        val setCalls = mutableListOf<String>()
        private var item: MediaItem? = null
        private var state = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build()).build()

        override fun getState(): State = state

        override fun handleSetMediaItems(items: MutableList<MediaItem>, index: Int, positionMs: Long): ListenableFuture<*> {
            item = items[if (index == C.INDEX_UNSET) 0 else index]
            setCalls += requireNotNull(item).mediaId
            state = state.buildUpon().setPlaylist(listOf(data(C.TIME_UNSET)))
                .setCurrentMediaItemIndex(0).setContentPositionMs(0L)
                .setPlaybackState(if (state.playbackState == Player.STATE_IDLE) Player.STATE_IDLE else Player.STATE_BUFFERING)
                .build()
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> {
            state = state.buildUpon().setPlaybackState(Player.STATE_BUFFERING).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(value: Boolean): ListenableFuture<*> {
            state = state.buildUpon().setPlayWhenReady(value, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

        fun publishReady(durationMs: Long) {
            state = state.buildUpon().setPlaylist(listOf(data(durationMs * 1000)))
                .setPlaybackState(Player.STATE_READY).build()
            invalidateState()
        }

        private fun data(durationUs: Long): MediaItemData = MediaItemData.Builder(requireNotNull(item).mediaId)
            .setMediaItem(requireNotNull(item)).setDurationUs(durationUs).setIsSeekable(true).build()
    }
}
