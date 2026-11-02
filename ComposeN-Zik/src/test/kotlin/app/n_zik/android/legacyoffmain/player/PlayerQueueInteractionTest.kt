package app.n_zik.android.legacyoffmain.player

import android.app.Application
import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.screens.player.Queue
import app.it.fast4x.rimusic.ui.screens.player.QueueToolBarState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.kreate.android.me.knighthat.utils.Toaster
import app.kreate.android.themed.rimusic.component.playlist.PositionLock
import app.n_zik.android.Dependencies
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.MainApplication
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the rendered Queue row consumers while their visual snapshot is retained. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class, ExperimentalTextApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerQueueInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private val listeners = mutableListOf<Player.Listener>()
    private val first = window("first", "local:101", "First duplicate")
    private val second = window("second", "local:101", "Second duplicate")
    private val third = window("third", "local:103", "Third")
    private val fourth = window("fourth", "local:104", "Fourth")
    private var live = listOf(first, second, third, fourth)
    private var mediaRelease: CountDownLatch? = null
    private val timeline = mockk<Timeline>()

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear()
            .putBoolean(glassEnabledKey, false)
            .putBoolean(disableScrollingTextKey, true).commit()
        listenTogetherGuestLock.value = false
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val songs = mockk<SongTable>()
        every { songs.likeState(any()) } returns flowOf(null)
        mockkObject(Database, LikeStateManager, PlaylistStateManager, Toaster)
        every { Toaster.s(any<String>(), any()) } just Runs
        every { Database.songTable } returns songs
        every { LikeStateManager.getLikeStates(any()) } returns flowOf(emptyMap())
        every { PlaylistStateManager.getPlaylistStates(any()) } returns flowOf(emptyMap())
        every { timeline.windowCount } answers { live.size }
        every { timeline.getWindow(any(), any()) } answers { live[firstArg()] }
        every { player.currentTimeline } returns timeline
        every { player.mediaItemCount } answers { live.size }
        every { player.getMediaItemAt(any()) } answers { live[firstArg()].mediaItem }
        every { player.currentMediaItemIndex } returns 0
        every { player.currentMediaItem } returns first.mediaItem
        every { player.playbackState } returns Player.STATE_READY
        every { player.playWhenReady } returns true
        every { player.addListener(any()) } answers { listeners.add(firstArg()); Unit }
        every { player.removeListener(any()) } answers { listeners.remove(firstArg()); Unit }
        every { player.removeMediaItem(any()) } just Runs
        every { binder.player } returns player
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        every { binder.service.nzikRadio.isRadioActive } returns false
    }

    @After fun tearDown() {
        mediaRelease?.countDown()
        listenTogetherGuestLock.value = false
        unmockkAll()
    }

    @Test fun `retained duplicate row click seeks its live uid and vanished row does nothing`() {
        renderQueue()
        retainVisualRowsWithLiveOrder(listOf(first, third, fourth, second))
        row().performClick()
        verify(exactly = 1) { player.seekToDefaultPosition(3) }
        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.play() }
        replaceLive(listOf(first, third, fourth))
        row().performClick()
        verify(exactly = 1) { player.seekToDefaultPosition(any()) }
        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.play() }
    }

    @Test fun `retained duplicate row removal uses its live uid and vanished row does nothing`() {
        renderQueue()
        retainVisualRowsWithLiveOrder(listOf(first, third, fourth, second))
        row().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        verify(exactly = 1) { player.removeMediaItem(3) }
        replaceLive(listOf(first, third, fourth))
        row().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        verify(exactly = 1) { player.removeMediaItem(any()) }
    }

    @Test fun `retained duplicate row play next uses its live uid and vanished row does nothing`() {
        renderQueue()
        retainVisualRowsWithLiveOrder(listOf(first, third, fourth, second))
        row().performTouchInput { swipeRight() }
        compose.waitForIdle()
        verify(exactly = 1) { player.moveMediaItem(3, 1) }
        replaceLive(listOf(first, third, fourth))
        row().performTouchInput { swipeRight() }
        compose.waitForIdle()
        verify(exactly = 1) { player.moveMediaItem(any(), any()) }
    }

    @Test fun `dragged row keeps its new position after release while normalization is blocked`() {
        // Observe publication only to know when background work has finished. The
        // regression assertions below inspect the actual rendered row positions.
        val publishedIds = AtomicReference<List<String>>(emptyList())
        every { LikeStateManager.getLikeStates(any()) } answers {
            publishedIds.set(firstArg<List<String>>().toList())
            flowOf(emptyMap())
        }
        every { player.moveMediaItem(any(), any()) } answers {
            val from = firstArg<Int>()
            val to = secondArg<Int>()
            live = live.toMutableList().apply { add(to, removeAt(from)) }
            listeners.toList().forEach {
                it.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            }
        }
        renderQueue()
        compose.runOnIdle {
            QueueToolBarState.buttons.filterIsInstance<PositionLock>().single().onShortClick()
        }
        compose.waitForIdle()

        fun centerY(title: String) = compose.onNodeWithText(title)
            .fetchSemanticsNode().boundsInRoot.center.y
        fun assertReordered(phase: String) {
            val firstY = centerY("First duplicate")
            val thirdY = centerY("Third")
            val secondY = centerY("Second duplicate")
            val fourthY = centerY("Fourth")
            assertTrue("$phase: rows must remain first, third, second, fourth; Y=$firstY,$thirdY,$secondY,$fourthY",
                firstY < thirdY && thirdY < secondY && secondY < fourthY)
        }

        val secondBounds = row().fetchSemanticsNode().boundsInRoot
        val rootOrigin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        // The production handle occupies the trailing 24dp, inside a 16dp end inset.
        val handleX = secondBounds.right - with(compose.density) { 28.dp.toPx() }
        val start = Offset(handleX, secondBounds.center.y) - rootOrigin
        val target = Offset(handleX, centerY("Third") + with(compose.density) { 8.dp.toPx() }) - rootOrigin
        assertTrue("Initial queue order", centerY("Second duplicate") < centerY("Third"))
        val release = blockQueueNormalization()
        try {
            compose.onRoot().performTouchInput {
                down(start)
                moveTo(start + Offset(0f, with(compose.density) { 24.dp.toPx() }), delayMillis = 100)
            }
            compose.waitForIdle()
            compose.onRoot().performTouchInput { moveTo(target, delayMillis = 300) }
            compose.waitForIdle()
            assertReordered("Finger held")

            compose.onRoot().performTouchInput { up() }
            compose.waitForIdle()
            assertEquals("Normalization remains held", 1L, release.count)
            assertReordered("Finger released before normalization")
            compose.runOnIdle {
                assertEquals(listOf("first", "third", "second", "fourth"), live.map { it.uid })
            }

            release.countDown()
            val reorderedIds = listOf(first, third, second, fourth).map { it.mediaItem.mediaId }
            compose.waitUntil(5_000) { publishedIds.get() == reorderedIds }
            compose.waitForIdle()
            assertReordered("Normalization published")
        } finally {
            release.countDown()
        }
    }

    private fun row() = compose.onNodeWithText("Second duplicate")

    private fun renderQueue() {
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val palette = DefaultDarkColorPalette
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = palette,
                    typography = typographyOf(palette.text, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalPlayerServiceBinder provides binder,
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
            ) {
                val navigation = rememberNavController()
                Box(Modifier.fillMaxSize()) { Queue(navigation, {}, {}) }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Second duplicate"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        row().assertIsDisplayed()
    }

    private fun retainVisualRowsWithLiveOrder(windows: List<Timeline.Window>) {
        blockQueueNormalization()
        replaceLive(windows)
    }

    private fun blockQueueNormalization(): CountDownLatch {
        // Hold both MEDIA workers so production normalization cannot replace visible rows.
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        mediaRelease = release
        repeat(2) {
            NzikDispatchers.MEDIA.dispatch(EmptyCoroutineContext, Runnable {
                entered.countDown()
                check(release.await(30, TimeUnit.SECONDS))
            })
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        return release
    }

    private fun replaceLive(windows: List<Timeline.Window>) {
        compose.runOnIdle {
            live = windows
            listeners.toList().forEach { it.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        row().assertIsDisplayed()
    }

    private fun window(uid: String, id: String, title: String) = Timeline.Window().apply {
        this.uid = uid
        mediaItem = MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist("Artist").build()
        ).build()
    }
}
