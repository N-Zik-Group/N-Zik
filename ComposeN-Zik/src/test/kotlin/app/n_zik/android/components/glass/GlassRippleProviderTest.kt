package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlaylistSwipeAction
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassRippleProviderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `off preserves native local identities and foundation pressed pixels`() {
        val source = MutableInteractionSource()
        val wrapped = mutableStateOf(false)
        val nativeConfiguration = RippleConfiguration(Color.Black)
        val nativeIndication = ripple(color = Color.Black)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalRippleConfiguration provides nativeConfiguration,
                LocalIndication provides nativeIndication,
            ) {
                if (wrapped.value) {
                    GlassRippleProvider(GlassSettings(enabled = false)) {
                        val configuration = LocalRippleConfiguration.current
                        val indication = LocalIndication.current
                        SideEffect {
                            assertSame(nativeConfiguration, configuration)
                            assertSame(nativeIndication, indication)
                        }
                        Samples("sample", source)
                    }
                } else {
                    Samples("sample", source)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        fun heldPixels() = run {
            compose.mainClock.advanceTimeByFrame()
            val press = PressInteraction.Press(Offset(40f, 24f))
            compose.runOnIdle { assertTrue(source.tryEmit(press)) }
            compose.mainClock.advanceTimeBy(800)
            val pixels = listOf("row").associateWith {
                compose.onNodeWithTag("sample.$it").captureToImage().toPixelMap()
            }
            compose.runOnIdle { assertTrue(source.tryEmit(PressInteraction.Release(press))) }
            compose.mainClock.advanceTimeBy(1600)
            pixels
        }
        val nativePixels = heldPixels()
        compose.runOnIdle { wrapped.value = true }
        val offPixels = heldPixels()
        listOf("row").forEach { kind ->
            val native = nativePixels.getValue(kind)
            val off = offPixels.getValue(kind)
            assertEquals(native.width, off.width)
            assertEquals(native.height, off.height)
            for (y in 0 until native.height) for (x in 0 until native.width) {
                assertEquals("$kind pixel $x,$y", native[x, y], off[x, y])
            }
            assertEquals("$kind opaque interior", native[native.width / 2, native.height / 2], off[off.width / 2, off.height / 2])
        }
    }

    @Suppress("DEPRECATION")
    @Test fun `glass configures material white and foundation held highlight then restores native locals`() {
        val source = MutableInteractionSource()
        val enabled = mutableStateOf(true)
        val nativeConfiguration = RippleConfiguration(Color.Black)
        val nativeIndication = ripple(color = Color.Black)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalRippleConfiguration provides nativeConfiguration,
                LocalIndication provides nativeIndication,
            ) {
                GlassRippleProvider(GlassSettings(enabled = enabled.value)) {
                    val configuration = LocalRippleConfiguration.current
                    val indication = LocalIndication.current
                    SideEffect {
                        if (enabled.value) {
                            assertEquals(Color.White, configuration?.color)
                            assertEquals(.12f, configuration?.rippleAlpha?.pressedAlpha)
                        } else {
                            assertSame(nativeConfiguration, configuration)
                            assertSame(nativeIndication, indication)
                        }
                    }
                    Samples("glass", source)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val original = listOf("row").associateWith {
            compose.onNodeWithTag("glass.$it").captureToImage().toPixelMap()
        }
        val press = PressInteraction.Press(Offset(40f, 24f))
        compose.runOnIdle { assertTrue(source.tryEmit(press)) }
        compose.mainClock.advanceTimeBy(1200)
        original.forEach { (kind, before) ->
            val held = compose.onNodeWithTag("glass.$kind").captureToImage().toPixelMap()
            val start = before[before.width / 4, before.height / 2]
            val end = held[held.width / 4, held.height / 2]
            assertTrue("$kind held ripple lightens", end.red > start.red + .02f)
            assertTrue("$kind held ripple stays white", end.green > start.green && end.blue > start.blue)
            assertTrue("$kind held highlight remains translucent", end.red - start.red < .3f)
        }
        compose.runOnIdle { source.tryEmit(PressInteraction.Release(press)); enabled.value = false }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun `list retains click long click and scroll ownership with glass on and off`() {
        val enabled = mutableStateOf(true)
        var clicks = 0
        var holds = 0
        var scrolled = false
        compose.setContent {
            GlassRippleProvider(GlassSettings(enabled = enabled.value)) {
                val state = rememberLazyListState()
                val hasScrolled = state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0
                SideEffect { scrolled = hasScrolled }
                LazyColumn(Modifier.size(160.dp, 160.dp).testTag("list"), state = state) {
                    items(30, key = { it }, contentType = { "playlist" }) { index ->
                        Box(Modifier.size(160.dp, 56.dp).testTag("item.$index")
                            .combinedClickable(onClick = { clicks++ }, onLongClick = { holds++ }))
                    }
                }
            }
        }
        compose.onNodeWithTag("item.0").performTouchInput { click() }
        compose.onNodeWithTag("item.0").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, holds) }
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.runOnIdle {
            assertTrue(scrolled)
            assertEquals(1, clicks)
            assertEquals(1, holds)
            enabled.value = false
        }
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, holds) }
    }

    @Composable private fun Samples(prefix: String, source: MutableInteractionSource) {
        val color = Color(0xFF402060)
        Column {
            Button({}, Modifier.size(160.dp, 48.dp).testTag("$prefix.button"),
                colors = ButtonDefaults.buttonColors(containerColor = color), elevation = null,
                interactionSource = source) {}
            Card({}, Modifier.size(160.dp, 48.dp).testTag("$prefix.card"),
                colors = CardDefaults.cardColors(containerColor = color), interactionSource = source) {}
            Box(Modifier.size(160.dp, 48.dp).background(color).testTag("$prefix.row")
                .combinedClickable(interactionSource = source, indication = LocalIndication.current,
                    onLongClick = {}, onClick = {}))
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `real song in swipeable playlist and real playlist item inherit glass hold without changing callbacks`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(disableScrollingTextKey, true)
            .putBoolean(glassEnabledKey, false).commit()
        var clicks = 0
        var holds = 0
        val fill = Color(0xFF402060)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalIndication provides ripple(color = Color.Black),
                LocalRippleConfiguration provides RippleConfiguration(Color.Black),
            ) {
                GlassRippleProvider(GlassSettings(enabled = true)) {
                    LazyColumn(Modifier.size(280.dp, 220.dp).testTag("real.list").background(fill)) {
                        item {
                            SwipeablePlaylistItem(
                                mediaItem = MediaItem.Builder().setMediaId("local:ripple").build(),
                                likeState = false, downloadStateParam = Download.STATE_STOPPED,
                                downloadedStateMediaParam = DownloadedStateMedia.DOWNLOADED,
                                swipeLeftActionParam = PlaylistSwipeAction.NoAction,
                                swipeRightActionParam = PlaylistSwipeAction.NoAction,
                            ) {
                                SongItem(
                                    Song("local:ripple", "Real song", durationText = "1:00", thumbnailUrl = null),
                                    isLiked = false, inPlaylist = false, showThumbnail = false,
                                    backgroundColor = fill, modifier = Modifier.testTag("real.song"),
                                    onClick = { clicks++ }, onLongClick = { holds++ },
                                )
                            }
                        }
                        item {
                            PlaylistItem(
                                thumbnailContent = {}, songCount = null, name = "Real playlist",
                                channelName = null, thumbnailSizeDp = 48.dp, disableScrollingText = true,
                                modifier = Modifier.testTag("real.playlist").combinedClickable(
                                    onClick = { clicks++ }, onLongClick = { holds++ },
                                ),
                            )
                        }
                        items(20) { index ->
                            PlaylistItem(
                                thumbnailContent = {}, songCount = null, name = "Playlist $index",
                                channelName = null, thumbnailSizeDp = 48.dp, disableScrollingText = true,
                                modifier = Modifier.combinedClickable(onClick = { clicks++ }, onLongClick = { holds++ }),
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        listOf("real.song", "real.playlist").forEach { tag ->
            val node = compose.onNodeWithTag(tag)
            val before = node.captureToImage().toPixelMap()
            node.performTouchInput { down(Offset(width - 8f, height / 2f)) }
            // Scrollable clickables delay PressInteraction on Android's main looper.
            shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1200)
            val held = node.captureToImage().toPixelMap()
            val x = before.width - 8
            val y = before.height / 2
            assertTrue("$tag Glass hold lightens actual row: ${before[x, y]} -> ${held[x, y]}", held[x, y].red > before[x, y].red + .02f)
            assertTrue("$tag Glass hold is white", held[x, y].green > before[x, y].green && held[x, y].blue > before[x, y].blue)
            node.performTouchInput { advanceEventTime(1200); up() }
            compose.mainClock.advanceTimeBy(1600)
            node.performTouchInput { click(Offset(width - 8f, height / 2f)) }
            compose.mainClock.advanceTimeBy(1600)
        }
        compose.runOnIdle { assertEquals(2, holds); assertEquals(2, clicks) }
        compose.onNodeWithTag("real.list").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithTag("real.song").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, holds); assertEquals(2, clicks) }
    }
}
