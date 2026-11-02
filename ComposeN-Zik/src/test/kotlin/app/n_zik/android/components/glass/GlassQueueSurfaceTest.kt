package app.n_zik.android.components.glass

import android.app.Application
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
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
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import app.it.fast4x.rimusic.ui.screens.player.QueueToolBar
import app.it.fast4x.rimusic.ui.screens.player.QueueToolBarState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassQueueSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `queue player source retains real player objects beneath the live native scrim`() {
        val expansion = mutableStateOf(0f)
        var compositions = 0
        compose.setContent {
            androidx.compose.runtime.SideEffect { compositions++ }
            val player = rememberCanvasBackdrop {
                drawRect(Color.Red)
                drawRect(Color.White, androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                    androidx.compose.ui.geometry.Size(size.width / 2f, size.height))
            }
            val source = app.n_zik.android.components.player.rememberPlayerQueueBackdrop(player) { expansion.value }
            Box(Modifier.size(64.dp).testTag("queue.player.scrim").drawBehind {
                with(source) { drawBackdrop(this@drawBehind, null, null) }
            })
        }
        compose.waitForIdle()
        val settled = compositions
        for (fraction in listOf(0f, .325f, .65f, 1f)) {
            compose.runOnIdle { expansion.value = fraction }
            val pixels = compose.onNodeWithTag("queue.player.scrim").captureToImage().toPixelMap()
            val scrim = Color.Black.copy(alpha = (fraction / .65f).coerceIn(0f, 1f) * .5f)
            assertColorClose(scrim.compositeOver(Color.Red), pixels[16, 32])
            assertColorClose(scrim.compositeOver(Color.White), pixels[48, 32])
        }
        assertEquals("scrim animation is observed during draw", settled, compositions)
    }

    @Test fun `queue mini and row sources retain the native panel beneath the translucent list`() {
        val panelFill = mutableStateOf(Color.Blue.copy(alpha = .5f))
        val listFill = Color.Green.copy(alpha = .25f)
        var list: LayerBackdrop? = null
        var miniSource: Backdrop? = null
        var rowSource: Backdrop? = null
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(enabled = true)
            val player = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape), LocalGlassBackdrop provides player) {
                Column {
                    GlassQueuePanel(Modifier.size(64.dp).glassQueuePanelBackground(panelFill.value, shape, settings), settings,
                        panelBackgroundColor = panelFill.value, panelShape = shape) {
                        val currentList = LocalQueueListBackdrop.current
                        val currentRowSource = rememberQueueRowGlassBackdrop(listFill, settings)
                        androidx.compose.runtime.SideEffect { list = currentList; rowSource = currentRowSource }
                        Box(Modifier.size(64.dp).glassQueueListBackdrop(rememberLazyListState())
                            .glassQueueListBackground(listFill, settings))
                        QueueEmbeddedMini {
                            val source = LocalGlassContainerBackdrop.current
                            androidx.compose.runtime.SideEffect { miniSource = source }
                        }
                    }
                    Box(Modifier.size(64.dp).testTag("panel.mini.source").drawBehind {
                        miniSource?.let { with(it) { drawBackdrop(this@drawBehind, list?.layerCoordinates, null) } }
                    })
                    Box(Modifier.size(64.dp).testTag("panel.row.source").drawBehind {
                        rowSource?.let { with(it) { drawBackdrop(this@drawBehind, list?.layerCoordinates, null) } }
                    })
                }
            }
        }
        for (alpha in listOf(.5f, 1f, 0f)) {
            compose.runOnIdle { panelFill.value = Color.Blue.copy(alpha = alpha) }
            val expected = listFill.compositeOver(panelFill.value.compositeOver(Color.Red))
            for (tag in listOf("panel.mini.source", "panel.row.source")) {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                assertColorClose(expected, pixels[pixels.width / 2, pixels.height / 2])
            }
        }
    }

    private fun setAppearanceContent(content: @Composable () -> Unit) {
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) { content() }
        }
    }

    @Test fun `queue panel rim follows every Menus area mode independently of clear film`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.background(Color.Black)) {
                    Box(Modifier.size(64.dp).testTag("panel").glassQueuePanelBackground(
                        Color.Black, shape, GlassSettings(film = 0f, rim = 1f,
                            areaModes = mapOf(GlassArea.Menus to mode.value))))
                }
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
        var listCoordinates: androidx.compose.ui.layout.LayoutCoordinates? = null
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(enabled = enabled.value, source = GlassTintSource.Custom,
                customColor = 0xFFFFFF00.toInt(), film = 1f, rim = 1f)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            ), LocalGlassBackdrop provides rememberCanvasBackdrop { drawRect(Color.Blue) }) {
                Column {
                    GlassQueuePanel(settings = settings) {
                        val safeSource = rememberQueueRowGlassBackdrop(nativeFill, settings)
                        isolatedSource = safeSource
                        NativeQueueRows(settings) {
                            CompositionLocalProvider(LocalQueueRowBackdrop provides safeSource) {
                                Column(Modifier.background(Color.Blue).then(Modifier.onGloballyPositioned { listCoordinates = it })) {
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
                            drawBackdrop(this@drawBehind, listCoordinates, null)
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

    @Test fun `queue backgrounds preserve exact native fills across Glass film and live palettes`() {
        val enabled = mutableStateOf(false)
        val film = mutableStateOf(0f)
        val fill = mutableStateOf(Color.Transparent)
        val palettes = listOf(
            DefaultDarkColorPalette.copy(background0 = Color(0xFF184838), background2 = Color(0xFF684028)),
            DefaultDarkColorPalette.copy(background0 = Color(0xFFE0D8F0), background2 = Color(0xFFB8D8E8), isDark = false),
        )
        val palette = mutableStateOf(palettes.first())
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance,
                film = film.value, rim = 1f, source = GlassTintSource.Custom,
                customColor = 0xFFFF00FF.toInt(), tintStrength = 1f)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                palette.value, Typography.fallback, shape, shape, shape,
            )) {
                Column(Modifier.background(Color.Blue).testTag("native.backgrounds")) {
                    Box(Modifier.size(64.dp).glassQueuePanelBackground(fill.value, shape, settings))
                    Box(Modifier.size(64.dp).glassQueueListBackground(fill.value, settings))
                    Box(Modifier.size(64.dp).background(queueContentBackground(fill.value, settings)))
                }
            }
        }
        for (theme in palettes) {
            compose.runOnIdle { palette.value = theme }
            for (nativeFill in listOf(Color.Transparent, theme.background2.copy(alpha = .5f), theme.background2)) {
                for (glassEnabled in listOf(false, true)) for (opacity in listOf(0f, .5f, 1f)) {
                    compose.runOnIdle { fill.value = nativeFill; enabled.value = glassEnabled; film.value = opacity }
                    val image = compose.onNodeWithTag("native.backgrounds").captureToImage().toPixelMap()
                    val expected = nativeFill.compositeOver(Color.Blue)
                    for (index in 0..2) {
                        assertColorClose(expected, image[image.width / 2, image.height * (index * 2 + 1) / 6])
                    }
                    assertEquals(nativeFill, queueContentBackground(nativeFill,
                        GlassSettings(enabled = glassEnabled, film = opacity)))
                }
            }
        }
    }

    @Test fun `Modern transparency and Essential opaque native fills survive Glass over contrasting underlays`() {
        val enabled = mutableStateOf(false)
        val film = mutableStateOf(0f)
        val underlay = mutableStateOf(Color.Blue)
        val modern = mutableStateOf(true)
        val nativeList = Color(0xFF80B050)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Balanced,
                film = film.value, rim = 1f, source = GlassTintSource.Custom,
                customColor = 0xFFFF00FF.toInt(), tintStrength = 1f,
                blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
            val backdrop = rememberCanvasBackdrop { drawRect(underlay.value) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    Typography.fallback, shape, shape, shape),
                LocalGlassBackdrop provides backdrop,
            ) {
                Box(Modifier.background(underlay.value)) {
                    GlassQueuePanel(Modifier.size(64.dp, 128.dp).testTag("modern.queue")
                        .glassQueuePanelBackground(if (modern.value) Color.Transparent
                            else DefaultDarkColorPalette.background2, shape, settings), settings) {
                        Box(Modifier.size(64.dp).align(Alignment.BottomCenter)
                            .glassQueueListBackground(nativeList.copy(alpha = if (modern.value) .5f else 1f), settings))
                    }
                }
            }
        }
        for (isModern in listOf(true, false)) for (background in listOf(Color.Blue, Color.Red)) {
            compose.runOnIdle { enabled.value = false; underlay.value = background; modern.value = isModern }
            val off = compose.onNodeWithTag("modern.queue").captureToImage().toPixelMap()
            val x = off.width / 2
            assertColorClose(if (isModern) background else DefaultDarkColorPalette.background2,
                off[x, off.height / 4])
            val expectedList = if (isModern) nativeList.copy(alpha = .5f).compositeOver(background) else nativeList
            assertColorClose(expectedList, off[x, off.height * 3 / 4])
            for (opacity in listOf(0f, .5f, 1f)) {
                compose.runOnIdle { enabled.value = true; film.value = opacity }
                val on = compose.onNodeWithTag("modern.queue").captureToImage().toPixelMap()
                // Compare interiors only: Glass may add the requested outer contour.
                for (y in off.height / 8 until off.height * 7 / 8)
                    assertEquals("Native fill modern=$isModern film=$opacity y=$y", off[x, y], on[x, y])
            }
            compose.runOnIdle { enabled.value = false }
            val restored = compose.onNodeWithTag("modern.queue").captureToImage().toPixelMap()
            for (y in 0 until off.height) for (pixelX in 0 until off.width) {
                assertEquals("Restored modern=$isModern at $pixelX,$y", off[pixelX, y], restored[pixelX, y])
            }
        }
    }

    @Test fun `queue keeps native rows while explicit badge matches standalone in every action mode`() {
        val enabled = mutableStateOf(false)
        val mode = mutableStateOf(GlassAreaMode.GlassAndContour)
        var badge = 0
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(enabled = enabled.value, source = GlassTintSource.Custom,
                customColor = 0xFF00FF00.toInt(), film = .5f, rim = 1f,
                areaModes = mapOf(GlassArea.ActionButtons to mode.value))
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                Column {
                    NativeQueueRows(settings) {
                        badge = explicitBadgeIconId(settings)
                        Column(Modifier.testTag("rows").background(Color.Blue)) {
                            Box(Modifier.size(64.dp, 24.dp).glassNowPlayingRow(Color.Transparent,
                                Color.Red.copy(alpha = .25f), shape, settings))
                            Box(Modifier.size(64.dp, 24.dp).glassChipBackground(Color.Red.copy(alpha = .3f), shape, settings))
                            Box(Modifier.size(64.dp, 24.dp).glassSwipeActionBackground(Color.Red, shape,
                                isRevealed = true, settings = settings, backdropColor = Color.Blue))
                            Box(Modifier.size(64.dp, 24.dp).testTag("queue.explicit")
                                .glassExplicitBadgeBackground(settings))
                        }
                    }
                    Box(Modifier.background(Color.Blue)) {
                        Box(Modifier.size(64.dp, 24.dp).testTag("standalone.explicit")
                            .glassExplicitBadgeBackground(settings))
                    }
                }
            }
        }
        val native = compose.onNodeWithTag("rows").captureToImage().toPixelMap()
        val nativeBadge = compose.onNodeWithTag("queue.explicit").captureToImage().toPixelMap()
        val unchangedRowsHeight = native.height * 3 / 4
        for (value in GlassAreaMode.entries) {
            compose.runOnIdle { enabled.value = true; mode.value = value }
            val rows = compose.onNodeWithTag("rows").captureToImage().toPixelMap()
            for (y in 0 until unchangedRowsHeight) for (x in 0 until native.width)
                assertEquals("Native queue row in $value at $x,$y", native[x, y], rows[x, y])
            val actual = compose.onNodeWithTag("queue.explicit").captureToImage().toPixelMap()
            val reference = compose.onNodeWithTag("standalone.explicit").captureToImage().toPixelMap()
            var changed = false
            for (y in 0 until actual.height) for (x in 0 until actual.width) {
                assertEquals("Explicit surface parity in $value at $x,$y", reference[x, y], actual[x, y])
                if (actual[x, y] != nativeBadge[x, y]) changed = true
            }
            assertEquals("Explicit surface follows $value", value != GlassAreaMode.Disabled, changed)
            val center = actual[actual.width / 2, actual.height / 2]
            assertEquals("Only material changes the badge film in $value", value.hasMaterial,
                center != nativeBadge[nativeBadge.width / 2, nativeBadge.height / 2])
            compose.runOnIdle {
                assertEquals(if (value == GlassAreaMode.Disabled) R.drawable.explicit
                    else R.drawable.explicit_glass_glyph, badge)
            }
            compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        }
        compose.runOnIdle { enabled.value = false }
        val restored = compose.onNodeWithTag("rows").captureToImage().toPixelMap()
        for (y in 0 until native.height) for (x in 0 until native.width)
            assertEquals(native[x, y], restored[x, y])
        compose.runOnIdle { assertEquals(R.drawable.explicit, badge) }
    }

    @Test fun `hosted and standalone queue preserve the same single native fill in every Menus mode`() {
        val mode = mutableStateOf(GlassAreaMode.GlassOnly)
        val enabled = mutableStateOf(true)
        val film = mutableStateOf(.5f)
        val fill = mutableStateOf(Color.Red)
        setAppearanceContent {
            val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance,
                film = film.value, rim = 1f,
                areaModes = mapOf(GlassArea.Menus to GlassAreaMode.Disabled),
                contextModes = mapOf(GlassContextArea(GlassScope.Queue, GlassArea.Menus) to mode.value))
            val shape = RoundedCornerShape(0.dp)
            Column(Modifier.background(Color.Blue)) {
                // A Modern host is transparent; its list owns the one native fill.
                GlassQueuePanel(Modifier.size(64.dp).testTag("hosted.native")
                    .glassQueuePanelBackground(Color.Transparent, shape, settings), settings) {
                    Box(Modifier.size(64.dp).glassQueueListBackground(fill.value, settings))
                }
                Box(Modifier.size(64.dp).testTag("standalone.native")
                    .glassQueueListBackground(fill.value, settings))
            }
        }
        for (nativeFill in listOf(Color.Red, Color.Red.copy(alpha = .5f), Color.Transparent)) {
            for (glassEnabled in listOf(false, true)) for (value in GlassAreaMode.entries) {
                for (opacity in listOf(0f, .5f, 1f)) {
                    compose.runOnIdle {
                        fill.value = nativeFill; enabled.value = glassEnabled; mode.value = value; film.value = opacity
                    }
                    val expected = nativeFill.compositeOver(Color.Blue)
                    val hosted = compose.onNodeWithTag("hosted.native").captureToImage().toPixelMap()
                    val standalone = compose.onNodeWithTag("standalone.native").captureToImage().toPixelMap()
                    assertColorClose(expected, hosted[hosted.width / 2, hosted.height / 2])
                    assertColorClose(expected, standalone[standalone.width / 2, standalone.height / 2])
                    assertEquals(hosted.width, standalone.width)
                    assertEquals(hosted.height, standalone.height)
                    for (y in 0 until hosted.height) for (x in 0 until hosted.width) {
                        assertEquals("Single contour $value fill=$nativeFill at $x,$y", hosted[x, y], standalone[x, y])
                    }
                }
            }
        }
    }

    @Test fun `master changes retain queue state and row policy does not disable menu settings or fixed siblings`() {
        ApplicationProvider.getApplicationContext<Application>()
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

    @Test fun `queue toolbar owns bottom inset only when its scoped panel material is active`() {
        val modes = GlassAreaMode.entries
        for (menuMode in modes) for (actionMode in modes) {
            val settings = GlassSettings(areaModes = mapOf(GlassArea.Menus to menuMode,
                GlassArea.ActionButtons to actionMode))
            assertEquals(menuMode.hasMaterial, queueToolbarUsesGlassMaterial(settings))
        }
        assertFalse(queueToolbarUsesGlassMaterial(GlassSettings(enabled = false)))
    }

    @Test
    @Config(qualifiers = "land")
    fun `real queue toolbar reserves navigation once and extends material through its inset`() {
        val settings = mutableStateOf(GlassSettings(quality = GlassQuality.Performance))
        lateinit var view: View
        var observedNavigationInset = -1
        var extraToolbarInset = (-1).dp
        var materialOwnsInset = true
        var nativeHeightPx = 0f
        QueueToolBarState.reset()
        setAppearanceContent {
            view = LocalView.current
            val density = LocalDensity.current
            observedNavigationInset = WindowInsets.navigationBars.getBottom(density)
            materialOwnsInset = usesQueueToolbarGlass(settings.value)
            nativeHeightPx = with(density) { 60.dp.toPx() }
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings.value) {
                extraToolbarInset = queueToolbarBottomInset()
                Box(Modifier.size(300.dp, 160.dp).testTag("native.queue.viewport")) {
                    val hostInsets = if (materialOwnsInset) WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                        else WindowInsets.navigationBars
                    GlassQueuePanel(Modifier.fillMaxSize().windowInsetsPadding(hostInsets), settings.value) {
                        QueueToolBar(Modifier.align(Alignment.BottomCenter).testTag("native.queue.toolbar"))
                    }
                }
            }
        }
        for (enabled in listOf(false, true)) for (mode in GlassAreaMode.entries) {
            for (insetPx in listOf(48, 24, 0)) {
                compose.runOnIdle {
                    settings.value = settings.value.copy(enabled = enabled, areaModes = mapOf(GlassArea.Menus to mode))
                    ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, insetPx))
                        .setVisible(WindowInsetsCompat.Type.navigationBars(), insetPx > 0)
                        .build())
                }
                compose.runOnIdle {
                    assertEquals(insetPx, observedNavigationInset)
                    assertEquals(enabled && mode.hasMaterial, materialOwnsInset)
                    assertEquals(if (materialOwnsInset) insetPx.toFloat() else 0f,
                        extraToolbarInset.value * (nativeHeightPx / 60f), .5f)
                }
                val viewport = compose.onNodeWithTag("native.queue.viewport").fetchSemanticsNode().boundsInRoot
                val toolbar = compose.onNodeWithTag("native.queue.toolbar").fetchSemanticsNode().boundsInRoot
                assertEquals("Toolbar height for $mode", nativeHeightPx + if (materialOwnsInset) insetPx else 0,
                    toolbar.height, .5f)
                assertEquals("Inset reserved once for $mode", viewport.bottom - if (materialOwnsInset) 0 else insetPx,
                    toolbar.bottom, .5f)
            }
        }
        QueueToolBarState.reset()
    }

    @Test fun `native toolbar applies no inner navigation inset and retains live palette backing`() {
        val palette = mutableStateOf(DefaultDarkColorPalette)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(film = 1f, rim = 1f,
                areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled))
            CompositionLocalProvider(LocalAppearance provides Appearance(
                palette.value, Typography.fallback, shape, shape, shape,
            )) {
                GlassQueuePanel(Modifier.size(120.dp).background(Color.Blue), settings) {
                    GlassQueueToolbar(Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter)
                        .testTag("inset_toolbar"), settings, systemBottomInset = 24.dp) {
                        Box(Modifier.fillMaxWidth().height(16.dp).align(Alignment.BottomEnd)
                            .background(Color.Red))
                        QueueEmbeddedMini {
                            Box(Modifier.fillMaxWidth().height(16.dp).offset(y = (-20).dp).background(Color.Green))
                        }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("inset_toolbar").captureToImage().toPixelMap()
        assertColorClose(DefaultDarkColorPalette.background1, pixels[pixels.width / 2, pixels.height / 2])
        assertColorClose(Color.Red, pixels[pixels.width / 2, pixels.height - 1])
        val updated = DefaultDarkColorPalette.copy(background1 = Color(0xFFB8D8E8), isDark = false)
        compose.runOnIdle { palette.value = updated }
        val themed = compose.onNodeWithTag("inset_toolbar").captureToImage().toPixelMap()
        assertColorClose(updated.background1, themed[themed.width / 2, themed.height / 2])
        assertColorClose(Color.Red, themed[themed.width / 2, themed.height - 1])
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test fun `queue uses Home action capsule with native total outside and separate arrow bubble`() {
        val enabled = mutableStateOf(true)
        val clicks = IntArray(3)
        val controls = List(3) { index -> object : Icon {
            override val iconId = R.drawable.musical_notes
            override fun onShortClick() { clicks[index]++ }
            @Composable
            override fun ToolBarButton() {
                TabToolBar.Icon(
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
                Row(Modifier.fillMaxWidth(),
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
