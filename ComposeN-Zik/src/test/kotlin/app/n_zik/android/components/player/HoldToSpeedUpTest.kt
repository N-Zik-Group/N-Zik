package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.enums.FontType
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.screens.player.Thumbnail
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.holdToSpeedUpKey
import app.n_zik.android.utils.holdToSpeedUpRateKey
import app.n_zik.android.utils.preferences
import coil3.ImageLoader
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalAnimationApi::class, UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class HoldToSpeedUpTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var imageLoader: ImageLoader

    @Before
    fun setUpArtworkLoader() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        imageLoader = ImageLoader.Builder(context).diskCache(null).build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns imageLoader
    }

    @After
    fun tearDownArtworkLoader() {
        unmockkObject(ImageCacheFactory)
        unmockkStatic("app.n_zik.android.GlobalVarsKt")
        imageLoader.shutdown()
    }

    @Test
    fun testHoldToSpeedUpPreferenceKeys() {
        assertEquals("holdToSpeedUp", holdToSpeedUpKey)
        assertEquals("holdToSpeedUpRate", holdToSpeedUpRateKey)
    }

    @Test
    fun testHoldingArtworkUsesUpdatedBoostRateAndRestoresOnRelease() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = context.preferences
        preferences.edit().remove(holdToSpeedUpKey).remove(holdToSpeedUpRateKey).apply()
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val mediaItem = MediaItem.Builder().setMediaId("test_track_1").build()

        val speedChanges = mutableListOf<Float>()
        var currentSpeed = 1f
        val playbackParamsSlot = slot<PlaybackParameters>()

        every { binder.player } returns player
        every { player.isPlaying } returns true
        every { player.playbackParameters } answers { PlaybackParameters(currentSpeed, 1f) }
        every { player.playbackParameters = capture(playbackParamsSlot) } answers {
            currentSpeed = playbackParamsSlot.captured.speed
            speedChanges.add(currentSpeed)
        }
        every { player.currentTimeline } returns SinglePeriodTimeline(90_000_000L, true, false, false, null, mediaItem)
        every { player.currentMediaItem } returns mediaItem
        every { player.mediaItemCount } returns 1
        every { player.playerError } returns null

        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val appearance = Appearance(
                DefaultDarkColorPalette,
                typographyOf(Color.White, true, false, FontType.Rubik),
                shape, shape, shape
            )
            CompositionLocalProvider(
                LocalPlayerServiceBinder provides binder,
                LocalAppearance provides appearance
            ) {
                Thumbnail(
                    thumbnailTapEnabledKey = true,
                    isShowingLyrics = false,
                    onShowLyrics = {},
                    isShowingStatsForNerds = false,
                    onShowStatsForNerds = {},
                    isShowingVisualizer = false,
                    onShowEqualizer = {},
                    onMaximize = {},
                    onDoubleTap = {},
                    showthumbnail = true,
                    modifier = Modifier.size(200.dp).testTag("player.thumbnail")
                )
            }
        }

        val thumbnail = compose.onNodeWithTag("player.thumbnail")
        thumbnail.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(emptyList<Float>(), speedChanges)
            preferences.edit().putBoolean(holdToSpeedUpKey, true).apply()
        }

        thumbnail.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(2f, 1f), speedChanges)
            preferences.edit().putFloat(holdToSpeedUpRateKey, 4f).apply()
        }

        thumbnail.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(2f, 1f, 4f, 1f), speedChanges)
            currentSpeed = 4.2f
        }

        thumbnail.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(2f, 1f, 4f, 1f), speedChanges)
            assertEquals(4.2f, currentSpeed, 0.01f)
        }
    }

    @Test
    fun testHoldingWhilePausedDoesNotBoostSpeed() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.preferences.edit().putBoolean(holdToSpeedUpKey, true).apply()
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val mediaItem = MediaItem.Builder().setMediaId("test_track_2").build()

        var currentSpeed = 1f
        val playbackParamsSlot = slot<PlaybackParameters>()

        every { binder.player } returns player
        every { player.isPlaying } returns false // Paused!
        every { player.playbackParameters } answers { PlaybackParameters(currentSpeed, 1f) }
        every { player.playbackParameters = capture(playbackParamsSlot) } answers {
            currentSpeed = playbackParamsSlot.captured.speed
        }
        every { player.currentTimeline } returns SinglePeriodTimeline(90_000_000L, true, false, false, null, mediaItem)
        every { player.currentMediaItem } returns mediaItem
        every { player.mediaItemCount } returns 1
        every { player.playerError } returns null

        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val appearance = Appearance(
                DefaultDarkColorPalette,
                typographyOf(Color.White, true, false, FontType.Rubik),
                shape, shape, shape
            )
            CompositionLocalProvider(
                LocalPlayerServiceBinder provides binder,
                LocalAppearance provides appearance
            ) {
                Thumbnail(
                    thumbnailTapEnabledKey = true,
                    isShowingLyrics = false,
                    onShowLyrics = {},
                    isShowingStatsForNerds = false,
                    onShowStatsForNerds = {},
                    isShowingVisualizer = false,
                    onShowEqualizer = {},
                    onMaximize = {},
                    onDoubleTap = {},
                    showthumbnail = true,
                    modifier = Modifier.size(200.dp).testTag("player.thumbnail.paused")
                )
            }
        }

        // Press and hold while paused
        compose.onNodeWithTag("player.thumbnail.paused").performTouchInput {
            down(center)
            advanceEventTime(600)
            up()
        }

        // Speed should NOT change when player is paused
        assertEquals(1f, currentSpeed, 0.01f)
    }
}
