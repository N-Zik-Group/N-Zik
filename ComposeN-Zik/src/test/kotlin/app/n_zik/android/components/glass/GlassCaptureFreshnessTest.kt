package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
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
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.glass.backdrop.effects.blur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCaptureFreshnessTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `film repaints reuse the optical capture while source and geometry stay fresh`() {
        val sourceColor = mutableStateOf(Color.Red)
        val film = mutableStateOf(Color.Transparent)
        val shape = mutableStateOf<Shape>(RoundedCornerShape(12.dp))
        val radius = mutableFloatStateOf(0f)
        val scale = mutableFloatStateOf(1f)
        var sourceRecords = 0
        var captures = 0
        compose.setContent {
            val record: ContentDrawScope.() -> Unit = remember { { sourceRecords++; drawContent() } }
            val source = rememberLayerBackdrop(onDraw = record)
            val paintSource: DrawScope.() -> Unit = remember { { drawRect(sourceColor.value) } }
            val sample: DrawScope.(DrawScope.() -> Unit) -> Unit = remember { { draw -> captures++; draw() } }
            val currentShape = shape.value
            val shapeBlock = remember(currentShape) { { currentShape } }
            val currentRadius = radius.floatValue
            val effects: BackdropEffectScope.() -> Unit = remember(currentRadius) {
                { if (currentRadius > 0f) blur(currentRadius) }
            }
            val currentFilm = film.value
            val paintFilm: DrawScope.() -> Unit = remember(currentFilm) { { drawRect(currentFilm) } }
            Box(Modifier.size(160.dp, 80.dp)) {
                Box(Modifier.size(160.dp, 80.dp).graphicsLayer().layerBackdrop(source).drawBehind(paintSource))
                Box(Modifier.size(160.dp, 80.dp).testTag("sample").drawPlainBackdrop(
                    source, shapeBlock, effects, onDrawBackdrop = sample,
                    onDrawSurface = paintFilm, backdropScale = scale.floatValue,
                ))
            }
        }
        fun pixel(): Color {
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("sample").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        repeat(3) { pixel() }
        assertEquals(Color.Red, pixel())
        val initialCaptures = captures
        val initialSourceRecords = sourceRecords
        compose.runOnIdle { film.value = Color.Green }
        assertEquals(Color.Green, pixel())
        assertEquals("film-only repaint must reuse the consumer capture", initialCaptures, captures)
        assertEquals("film-only repaint must not re-record the source", initialSourceRecords, sourceRecords)
        compose.runOnIdle { film.value = Color.Transparent }
        assertEquals(Color.Red, pixel())
        compose.runOnIdle { sourceColor.value = Color.Blue }
        assertEquals("draw-only source changes must not need a gesture to become visible", Color.Blue, pixel())
        assertTrue(sourceRecords > initialSourceRecords)
        fun recaptures(change: () -> Unit) {
            val before = captures
            compose.runOnIdle(change)
            pixel()
            assertTrue("an optical dependency must refresh the capture", captures > before)
        }
        recaptures { shape.value = RoundedCornerShape(24.dp) }
        recaptures { radius.floatValue = 2f }
        recaptures { scale.floatValue = .5f }
        val settledCaptures = captures
        val settledSourceRecords = sourceRecords
        repeat(3) { pixel() }
        assertEquals("settled consumer must stop recording", settledCaptures, captures)
        assertEquals("settled source must stop recording", settledSourceRecords, sourceRecords)
    }

    @Test fun `real material keeps its optical adapter across film changes and displays current source`() {
        val film = mutableStateOf(Color.Transparent)
        val sourceColor = mutableStateOf(Color.Red)
        val enabled = mutableStateOf(true)
        var adapter: Any? = null
        compose.setContent {
            val shape = remember { RoundedCornerShape(12.dp) }
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape), LocalGlassOpticsEnabled provides enabled.value) {
                val source = rememberLayerBackdrop()
                val paintSource: DrawScope.() -> Unit = remember { { drawRect(sourceColor.value) } }
                val material = Modifier.glassMaterial(
                    GlassSettings(film = 1f, rim = 0f, blur = 0f, depth = 0f, amount = 0f, vibrancy = 0f),
                    shape, ownBackdrop = source, filmColor = film.value, drawShadow = false,
                )
                // Inspect the real renderer's adapter, rather than a second implementation
                // of its remember keys. The first test measures actual capture counts.
                val opticalAdapter = material.foldIn<Any?>(null) { found, element ->
                    if (element.javaClass.simpleName == "DrawBackdropElement") {
                        element.javaClass.getDeclaredField("onDrawBackdrop").apply { isAccessible = true }.get(element)
                    } else found
                }
                SideEffect { adapter = opticalAdapter }
                Box(Modifier.size(160.dp, 80.dp)) {
                    Box(Modifier.size(160.dp, 80.dp).graphicsLayer().layerBackdrop(source).drawBehind(paintSource))
                    Box(Modifier.size(160.dp, 80.dp).testTag("material").then(material))
                }
            }
        }
        fun pixel(): Color {
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("material").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        assertEquals(Color.Red, pixel())
        val firstAdapter = requireNotNull(adapter)
        compose.runOnIdle { film.value = Color.Green }
        assertEquals(Color.Green, pixel())
        assertSame("film changes must retain the real material's capture adapter", firstAdapter, adapter)
        compose.runOnIdle { film.value = Color.Transparent; sourceColor.value = Color.Blue }
        assertEquals(Color.Blue, pixel())
        assertSame(firstAdapter, adapter)
        compose.runOnIdle { enabled.value = false }
        compose.mainClock.advanceTimeBy(GLASS_OPTICS_FADE_MILLIS.toLong() + 32)
        pixel()
        // The zero budget retains its optical node/adapter for a continuous
        // restoration; separate fade tests verify that it samples nothing here.
        assertSame("completed coverage must retain the optical adapter", firstAdapter, adapter)
    }
}
