package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassQueueSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `only current queue row and revealed swipe use Glass over the native queue fill`() {
        val enabled = mutableStateOf(false)
        val currentId = mutableStateOf("first")
        val revealed = mutableStateOf(true)
        val nativeFill = Color.Green.copy(alpha = .5f)
        var isolatedSource: Backdrop? = null
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(enabled = enabled.value, source = GlassTintSource.Custom,
                customColor = 0xFFFFFF00.toInt(), film = 1f, rim = 1f)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            ), LocalGlassBackdrop provides rememberCanvasBackdrop { drawRect(Color.Blue) }) {
                Column {
                    GlassQueuePanel(settings = settings) {
                        val safeSource = rememberQueueRowGlassBackdrop(nativeFill)
                        isolatedSource = safeSource
                        NativeQueueRows(settings) {
                            CompositionLocalProvider(LocalQueueRowBackdrop provides safeSource) {
                                Column(Modifier.background(Color.Blue)) {
                                    for (id in listOf("first", "second")) {
                                        val row = if (id == currentId.value)
                                            Modifier.glassNowPlayingRow(Color.Transparent, Color.Red.copy(alpha = .25f), shape, settings)
                                        else Modifier.background(Color.Transparent)
                                        Box(Modifier.size(64.dp, 24.dp).testTag(id).then(row))
                                    }
                                    Box(Modifier.size(64.dp, 24.dp).testTag("swipe_queue")
                                        .glassSwipeActionBackground(Color.Red, shape, revealed.value, settings,
                                            backdropColor = Color.Blue))
                                    Box(Modifier.size(64.dp, 24.dp).testTag("native_chip")
                                        .glassChipBackground(Color.Red, shape, settings))
                                }
                            }
                        }
                    }
                    Box(Modifier.size(64.dp).testTag("isolated_queue_source").drawBehind {
                        isolatedSource?.let { source -> with(source) {
                            drawBackdrop(this@drawBehind, null, null)
                        } }
                    })
                }
            }
        }
        fun pixel(tag: String): Color {
            val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 2]
        }
        val nativeCurrent = pixel("first")
        val nativeOther = pixel("second")
        val nativeSwipe = pixel("swipe_queue")
        assertColorClose(nativeFill.compositeOver(Color.Blue), pixel("isolated_queue_source"))
        compose.runOnIdle { enabled.value = true }
        assertNotEquals(nativeCurrent, pixel("first"))
        assertEquals(nativeOther, pixel("second"))
        assertNotEquals(nativeSwipe, pixel("swipe_queue"))
        assertColorClose(Color.Red, pixel("native_chip"))
        compose.runOnIdle { currentId.value = "second"; revealed.value = false }
        assertEquals(nativeOther, pixel("first"))
        assertNotEquals(nativeCurrent, pixel("second"))
        assertEquals(nativeSwipe, pixel("swipe_queue"))
        compose.runOnIdle { enabled.value = false }
        assertEquals(nativeCurrent, pixel("second"))
    }

    @Test fun `queue mini remains painted above the fixed toolbar with Glass on and off`() {
        val enabled = mutableStateOf(true)
        var embedded = false
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                GlassQueuePanel(
                    modifier = Modifier.size(120.dp).background(Color.Blue).testTag("queue_overflow"),
                    settings = GlassSettings(enabled = enabled.value),
                ) {
                    GlassQueueToolbar(
                        modifier = Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter),
                        settings = GlassSettings(enabled = enabled.value),
                    ) {
                        QueueEmbeddedMini {
                            embedded = LocalEmbeddedQueueMini.current
                            Box(Modifier.fillMaxWidth().height(24.dp).offset(y = (-32).dp)
                                .background(Color.Red))
                        }
                    }
                }
            }
        }
        fun assertMiniPainted() {
            val panel = compose.onNodeWithTag("queue_overflow").captureToImage().toPixelMap()
            // The mini occupies y=48..72dp above the toolbar at y=80dp.
            assertColorClose(Color.Red, panel[panel.width / 2, panel.height / 2])
            compose.runOnIdle { assertTrue(embedded) }
        }
        assertMiniPainted()
        compose.runOnIdle { enabled.value = false }
        assertMiniPainted()
    }

    @Test fun `queue retains native transparency with only an edge and restores exact native pixels when disabled`() {
        val enabled = mutableStateOf(false)
        val original = Color.Red.copy(alpha = .5f)
        compose.setContent {
            val settings = GlassSettings(enabled = enabled.value, source = GlassTintSource.Custom,
                customColor = 0xFFFFFFFF.toInt(), rim = 1f, film = 0f, blur = 24f)
            Box(Modifier.background(Color.Blue)) {
                Box(Modifier.size(80.dp).testTag("panel").glassQueuePanelBackground(
                    original, RoundedCornerShape(0.dp), settings))
            }
        }
        val off = compose.onNodeWithTag("panel").captureToImage().toPixelMap()
        assertColorClose(original.compositeOver(Color.Blue), off[off.width / 2, off.height / 2])
        compose.runOnIdle { enabled.value = true }
        val on = compose.onNodeWithTag("panel").captureToImage().toPixelMap()
        assertColorClose(original.compositeOver(Color.Blue), on[on.width / 2, on.height / 2])
        assertNotEquals(on[on.width / 2, on.height / 2], on[on.width / 2, 0])
        compose.runOnIdle { enabled.value = false }
        val restored = compose.onNodeWithTag("panel").captureToImage().toPixelMap()
        for (y in 0 until off.height) for (x in 0 until off.width) assertEquals(off[x, y], restored[x, y])
    }

    @Test fun `transparent and opaque native queue fills are both preserved when Glass is enabled`() {
        val fill = mutableStateOf(Color.Transparent)
        compose.setContent {
            Box(Modifier.background(Color.Blue)) {
                Box(Modifier.size(64.dp).testTag("choice").glassQueuePanelBackground(fill.value,
                    RoundedCornerShape(0.dp), GlassSettings(enabled = true, rim = 0f)))
            }
        }
        val transparent = compose.onNodeWithTag("choice").captureToImage().toPixelMap()
        assertColorClose(Color.Blue, transparent[transparent.width / 2, transparent.height / 2])
        compose.runOnIdle { fill.value = Color.Red }
        val opaque = compose.onNodeWithTag("choice").captureToImage().toPixelMap()
        assertColorClose(Color.Red, opaque[opaque.width / 2, opaque.height / 2])
    }

    @Test fun `queue row highlights badges chips and swipe reveals retain native rendering`() {
        val enabled = mutableStateOf(false)
        var badge = 0
        compose.setContent {
            val settings = GlassSettings(enabled = enabled.value, source = GlassTintSource.Custom,
                customColor = 0xFF00FF00.toInt(), film = .1f, rim = 1f)
            NativeQueueRows(settings) {
                val shape = RoundedCornerShape(8.dp)
                badge = explicitBadgeIconId(settings)
                Column(Modifier.testTag("rows").background(Color.Blue)) {
                    Box(Modifier.size(64.dp, 24.dp).glassNowPlayingRow(Color.Transparent,
                        Color.Red.copy(alpha = .25f), shape, settings))
                    Box(Modifier.size(64.dp, 24.dp).glassChipBackground(Color.Red.copy(alpha = .3f), shape, settings))
                    Box(Modifier.size(64.dp, 24.dp).glassSwipeActionBackground(Color.Red, shape,
                        isRevealed = true, settings = settings, backdropColor = Color.Blue))
                    Box(Modifier.size(64.dp, 24.dp).glassExplicitBadgeBackground(settings))
                }
            }
        }
        val off = compose.onNodeWithTag("rows").captureToImage().toPixelMap()
        compose.runOnIdle { enabled.value = true }
        val on = compose.onNodeWithTag("rows").captureToImage().toPixelMap()
        compose.runOnIdle { assertEquals(R.drawable.explicit, badge) }
        for (y in 0 until off.height) for (x in 0 until off.width) assertEquals(off[x, y], on[x, y])
    }

    @Test fun `master changes retain queue state and row policy does not disable menu settings or fixed siblings`() {
        val enabled = mutableStateOf(false)
        var creations = 0
        var remembered = 0
        var queuePolicy = false
        var siblingPolicy = true
        var menuGlassEnabled = false
        compose.setContent {
            NativeQueueRows(GlassSettings(enabled = enabled.value)) {
                remembered = remember { ++creations }
                queuePolicy = LocalNativeQueueRows.current
                // Menu hosts still read the real settings; the row scope never overrides the store.
                menuGlassEnabled = rememberGlassSettings().enabled
            }
            siblingPolicy = LocalNativeQueueRows.current
        }
        compose.runOnIdle {
            assertEquals(1, remembered)
            assertFalse(queuePolicy)
            enabled.value = true
        }
        compose.runOnIdle {
            assertEquals(1, remembered)
            assertTrue(queuePolicy)
            assertFalse(siblingPolicy)
            assertTrue(menuGlassEnabled)
            enabled.value = false
        }
        compose.runOnIdle {
            assertEquals(1, creations)
            assertFalse(queuePolicy)
        }
    }

    @Test fun `mini receives the list recording without the overlay and escapes native row policy`() {
        var recorded: LayerBackdrop? = null
        var sampled: Backdrop? = null
        var miniSource: Any? = null
        var embedded = false
        var nativeRows = true
        compose.setContent {
            Column {
                val safeUnderlay = rememberCanvasBackdrop { drawRect(Color.Blue) }
                CompositionLocalProvider(LocalGlassBackdrop provides safeUnderlay) {
                    GlassQueuePanel(settings = GlassSettings(enabled = true)) {
                        val source = LocalQueueListBackdrop.current
                        recorded = source
                        sampled = LocalQueueGlassBackdrop.current
                        Box(Modifier.size(64.dp).glassQueueListBackdrop(rememberLazyListState())
                            .background(Color.Green.copy(alpha = .5f)))
                        // This sibling represents the mini/toolbar: it must not enter the list recording.
                        Box(Modifier.size(64.dp).background(Color.Magenta))
                        NativeQueueRows(GlassSettings(enabled = true)) {
                            QueueEmbeddedMini {
                                miniSource = LocalGlassContainerBackdrop.current
                                embedded = LocalEmbeddedQueueMini.current
                                nativeRows = LocalNativeQueueRows.current
                            }
                        }
                    }
                }
                val source = sampled
                Box(Modifier.size(64.dp).testTag("list_sample").drawBehind {
                    if (source != null) with(source) {
                        drawBackdrop(this@drawBehind, recorded?.layerCoordinates, null)
                    }
                })
            }
        }
        val sample = compose.onNodeWithTag("list_sample").captureToImage().toPixelMap()
        assertColorClose(Color.Green.copy(alpha = .5f).compositeOver(Color.Blue), sample[sample.width / 2, sample.height / 2])
        compose.runOnIdle {
            assertTrue(sampled === miniSource)
            assertTrue(embedded)
            assertFalse(nativeRows)
        }
    }

    @Test fun `queue sampling stays live while the list is scrolling`() {
        val rowColor = mutableStateOf(Color.Green)
        var recorded: LayerBackdrop? = null
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        var scrolling: Job? = null
        compose.setContent {
            listState = rememberLazyListState()
            scope = rememberCoroutineScope()
            Column {
                GlassQueuePanel(settings = GlassSettings(enabled = true)) {
                    recorded = LocalQueueListBackdrop.current
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.size(64.dp).glassQueueListBackdrop(listState).background(rowColor.value),
                    ) {
                        items(4) { Box(Modifier.size(64.dp)) }
                    }
                }
                Box(Modifier.size(64.dp).testTag("scrolling_list_sample").drawBehind {
                    recorded?.let { source ->
                        with(source) { drawBackdrop(this@drawBehind, source.layerCoordinates, null) }
                    }
                })
            }
        }
        fun assertSample(expected: Color) {
            val pixels = compose.onNodeWithTag("scrolling_list_sample").captureToImage().toPixelMap()
            assertColorClose(expected, pixels[pixels.width / 2, pixels.height / 2])
        }
        assertSample(Color.Green)
        compose.runOnIdle { scrolling = scope.launch { listState.scroll { awaitCancellation() } } }
        compose.runOnIdle {
            assertTrue(listState.isScrollInProgress)
            rowColor.value = Color.Red
        }
        assertSample(Color.Red)
        compose.runOnIdle {
            assertTrue(listState.isScrollInProgress)
            scrolling?.cancel()
        }
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .01f)
        assertEquals(expected.green, actual.green, .01f)
        assertEquals(expected.blue, actual.blue, .01f)
        assertEquals(expected.alpha, actual.alpha, .01f)
    }
}
