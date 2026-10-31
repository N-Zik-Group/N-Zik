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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
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

    @Test fun `queue panel rim follows every Menus area mode without changing native fill`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.size(64.dp).testTag("panel").glassQueuePanelBackground(
                    Color.Black, shape, GlassSettings(rim = 1f, areaModes = mapOf(GlassArea.Menus to mode.value))))
            }
        }
        for (value in GlassAreaMode.entries) {
            compose.runOnIdle { mode.value = value }
            val pixels = compose.onNodeWithTag("panel").captureToImage().toPixelMap()
            var bright = false
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > .05f || color.green > .05f || color.blue > .05f) bright = true
            }
            assertEquals("Rim mode $value", value.hasContour, bright)
            assertColorClose(Color.Black, pixels[pixels.width / 2, pixels.height / 2])
            assertColorClose(Color.Black, pixels[pixels.width / 2, pixels.height - 1])
        }
    }

    @Test fun `outer queue toolbar contour follows Menus mode and omits the physical bottom rim`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val enabled = mutableStateOf(true)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance,
                    film = 1f, rim = 1f, areaModes = mapOf(GlassArea.Menus to mode.value))
                GlassQueuePanel(Modifier.size(120.dp, 80.dp), settings) {
                    GlassQueueToolbar(Modifier.size(120.dp, 80.dp).testTag("outer_toolbar"),
                        settings, systemBottomInset = 24.dp) { }
                }
            }
        }
        fun pixels() = compose.onNodeWithTag("outer_toolbar").captureToImage().toPixelMap()
        for (value in GlassAreaMode.entries) {
            compose.runOnIdle { mode.value = value }
            val image = pixels()
            val x = image.width / 2
            val fill = image[x, image.height / 2]
            val top = image[x, 0]
            assertEquals("Outer toolbar contour in $value", value.hasContour,
                top.red > fill.red + .05f || top.green > fill.green + .05f || top.blue > fill.blue + .05f)
            assertColorClose(fill, image[x, image.height - 1])
            assertColorClose(fill, image[x, image.height - 4])
        }
        compose.runOnIdle { mode.value = GlassAreaMode.GlassAndContour; enabled.value = false }
        val off = pixels()
        assertColorClose(DefaultDarkColorPalette.background1, off[off.width / 2, 0])
        assertColorClose(DefaultDarkColorPalette.background1, off[off.width / 2, off.height - 1])
    }

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
        androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
            .preferences.edit().putBoolean(glassEnabledKey, true).commit()
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

    @Test fun `queue backing hands off the bottom inset independently of action button policy`() {
        val modes = GlassAreaMode.entries
        for (menuMode in modes) for (actionMode in modes) {
            val settings = GlassSettings(areaModes = mapOf(GlassArea.Menus to menuMode,
                GlassArea.ActionButtons to actionMode))
            assertEquals(menuMode.hasMaterial, queueToolbarUsesGlassMaterial(settings))
        }
        assertFalse(queueToolbarUsesGlassMaterial(GlassSettings(enabled = false)))
    }

    @Test fun `toolbar backing fills navigation inset while content and mini stay above it`() {
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(film = 1f, rim = 1f,
                areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled))
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                GlassQueuePanel(Modifier.size(120.dp).background(Color.Blue), settings) {
                    GlassQueueToolbar(Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter)
                        .testTag("inset_toolbar"), settings, systemBottomInset = 24.dp) {
                        Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Red))
                        QueueEmbeddedMini {
                            Box(Modifier.fillMaxWidth().height(16.dp).offset(y = (-20).dp).background(Color.Green))
                        }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("inset_toolbar").captureToImage().toPixelMap()
        assertColorClose(Color.Red, pixels[pixels.width / 2, pixels.height / 4])
        val bottom = pixels[pixels.width / 2, pixels.height - 1]
        assertColorClose(DefaultDarkColorPalette.background1, bottom)
        // Seamless backing has no second bright rim at the system navigation edge.
        assertColorClose(bottom, pixels[pixels.width / 2, pixels.height - 4])
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Test fun `queue uses Home action capsule with native total outside and separate arrow bubble`() {
        val enabled = mutableStateOf(true)
        val clicks = IntArray(3)
        val controls = List(3) { index -> object : app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon {
            override val iconId = R.drawable.musical_notes
            override fun onShortClick() { clicks[index]++ }
            @androidx.compose.runtime.Composable
            override fun ToolBarButton() {
                app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar.Icon(
                    iconId = iconId, modifier = Modifier.testTag("queue_control_$index"),
                    onShortClick = this::onShortClick, onLongClick = {},
                )
            }
        } }
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    // Represents the existing musical note and total: no Glass wrapper owns it.
                    Box(Modifier.size(32.dp).background(Color.Magenta).testTag("queue_native_total"))
                    GlassQueueToolbarActions(controls.take(2), controls.last(), GlassSettings(enabled = enabled.value))
                }
            }
        }
        val count = compose.onNodeWithTag("queue_native_total").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("queue.toolbar.actions").fetchSemanticsNode().boundsInRoot
        val arrow = compose.onNodeWithTag("queue.toolbar.arrow").fetchSemanticsNode().boundsInRoot
        assertTrue(count.right <= actions.left)
        assertTrue(actions.right <= arrow.left)
        val nativePixels = compose.onNodeWithTag("queue_native_total").captureToImage().toPixelMap()
        for (index in clicks.indices) compose.onNodeWithTag("queue_control_$index").performClick()
        compose.runOnIdle { enabled.value = false }
        for (index in clicks.indices) compose.onNodeWithTag("queue_control_$index").performClick()
        val offPixels = compose.onNodeWithTag("queue_native_total").captureToImage().toPixelMap()
        for (y in 0 until nativePixels.height) for (x in 0 until nativePixels.width)
            assertEquals(nativePixels[x, y], offPixels[x, y])
        compose.runOnIdle { for (count in clicks) assertEquals(2, count) }
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .01f)
        assertEquals(expected.green, actual.green, .01f)
        assertEquals(expected.blue, actual.blue, .01f)
        assertEquals(expected.alpha, actual.alpha, .01f)
    }
}
