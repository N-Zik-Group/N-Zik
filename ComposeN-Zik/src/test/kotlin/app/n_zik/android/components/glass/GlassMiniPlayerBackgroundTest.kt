package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMiniPlayerBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `body fades with sheet displacement and reverses without fading the foreground`() {
        var progress by mutableStateOf(0f)
        var enabled by mutableStateOf(true)
        compose.setContent {
            val settings = GlassSettings(enabled = enabled, film = .5f, rim = 1f,
                tintStrength = 0f, quality = GlassQuality.Performance)
            val shape = RoundedCornerShape(0.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.size(100.dp, 60.dp).background(Color.Black).testTag("transition_mini")) {
                    Box(Modifier.matchParentSize().glassSwipeActionBackground(
                        originalColor = miniPlayerSwipeBackgroundColor(settings, false, Color.Green),
                        shape = shape, isRevealed = false, settings = settings, area = GlassArea.MiniPlayer,
                    ))
                    Box(Modifier.matchParentSize()
                        .glassMiniPlayerBodyVisibility(settings) { progress }
                        .glassMiniPlayerBackground(settings, shape, null, Color.Blue))
                    Box(Modifier.align(Alignment.Center).size(12.dp).background(Color.White))
                }
            }
        }
        fun capture() = compose.onNodeWithTag("transition_mini").captureToImage().toPixelMap()
        var previousBlue = Float.POSITIVE_INFINITY
        listOf(0f, .03f, .06f, .1f, .12f, .45f, 1f).forEach { position ->
            compose.runOnIdle { progress = position }
            val pixels = capture()
            val body = pixels[pixels.width / 4, pixels.height / 2]
            assertTrue(body.blue <= previousBlue)
            previousBlue = body.blue
            assertColor(Color.White, pixels[pixels.width / 2, pixels.height / 2])
            if (position >= .12f) {
                assertColor(Color.Black, body)
                assertColor(Color.Black, pixels[0, pixels.height / 2])
            }
        }
        listOf(.1f, .06f, .03f, 0f).forEach { position ->
            compose.runOnIdle { progress = position }
            val pixels = capture()
            val body = pixels[pixels.width / 4, pixels.height / 2]
            assertTrue(body.blue >= previousBlue)
            previousBlue = body.blue
            assertColor(Color.White, pixels[pixels.width / 2, pixels.height / 2])
        }
        compose.runOnIdle { enabled = false; progress = 1f }
        val native = capture()
        assertColor(Color.Blue, native[native.width / 4, native.height / 2])
    }

    @Test fun `body alpha clamps overshoot dismissal and invalid sheet positions`() {
        assertEquals(1f, miniPlayerGlassBodyAlpha(-1f), 0f)
        assertEquals(1f, miniPlayerGlassBodyAlpha(Float.NaN), 0f)
        assertEquals(1f, miniPlayerGlassBodyAlpha(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, miniPlayerGlassBodyAlpha(2f), 0f)
        assertEquals(.5f, miniPlayerGlassBodyAlpha(.06f), .001f)
        assertEquals(0f, miniPlayerGlassBodyAlpha(.12f), 0f)
    }

    @Test fun `resting swipe underlay is transparent for mini glass while native and active reveals retain fill`() {
        val glass = GlassSettings(enabled = true)
        assertEquals(Color.Transparent, miniPlayerSwipeBackgroundColor(glass, false, Color.Green))
        assertEquals(Color.Green, miniPlayerSwipeBackgroundColor(glass, true, Color.Green))
        assertEquals(Color.Green, miniPlayerSwipeBackgroundColor(glass.copy(enabled = false), false, Color.Green))
        assertEquals(Color.Green, miniPlayerSwipeBackgroundColor(
            glass.copy(areaModes = mapOf(GlassArea.MiniPlayer to GlassAreaMode.Disabled)), false, Color.Green))
    }

    @Test fun `progress shares translucent film across zero half full modes and live roundness`() {
        var settings by mutableStateOf(GlassSettings(enabled = true, film = .5f, rim = 1f,
            tintStrength = 0f, quality = GlassQuality.Performance))
        var fraction by mutableStateOf(0f)
        var radius by mutableStateOf(0.dp)
        val base = Color.Blue
        val progress = Color.Red.copy(alpha = .4f)
        val progressed = progress.compositeOver(base)
        compose.setContent {
            val shape = RoundedCornerShape(radius)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.size(100.dp, 60.dp).background(Color.Black).testTag("mini")) {
                    Box(Modifier.size(100.dp, 60.dp).clip(shape).glassMiniPlayerBackground(
                        settings, shape, null, base, progressColor = progress,
                        progressFraction = { fraction },
                    ))
                }
            }
        }
        fun capture() = compose.onNodeWithTag("mini").captureToImage().toPixelMap()
        GlassAreaMode.entries.forEach { mode ->
            compose.runOnIdle { settings = settings.copy(areaModes = mapOf(GlassArea.MiniPlayer to mode)) }
            listOf(0f, .5f, 1f).forEach { position ->
                compose.runOnIdle { fraction = position }
                val pixels = capture()
                listOf(.25f, .75f).forEach { horizontal ->
                    val film = if (horizontal < position) progressed else base
                    val alpha = if (horizontal < position) .7f else .5f
                    val expected = if (mode.hasMaterial) film.copy(alpha = alpha).compositeOver(Color.Black) else film
                    assertColor(expected, pixels[(pixels.width * horizontal).toInt(), pixels.height / 2])
                }
                val edge = pixels[0, pixels.height / 2]
                val film = if (position > 0f) progressed else base
                val alpha = if (position > 0f) .7f else .5f
                val expectedEdge = if (mode.hasMaterial) film.copy(alpha = alpha).compositeOver(Color.Black) else film
                if (mode.hasContour) assertNotEquals(expectedEdge, edge) else assertColor(expectedEdge, edge)
            }
        }
        compose.runOnIdle {
            settings = settings.copy(areaModes = mapOf(GlassArea.MiniPlayer to GlassAreaMode.GlassOnly))
            radius = 24.dp
        }
        assertEquals(Color.Black, capture()[0, 0])
        compose.runOnIdle { settings = settings.copy(film = 0f) }
        val clear = capture()
        assertEquals(Color.Black, clear[clear.width / 4, clear.height / 2])
        compose.runOnIdle { settings = settings.copy(enabled = false) }
        val native = capture()
        assertColor(progressed, native[native.width / 4, native.height / 2])
        assertEquals(Color.Black, native[0, 0])
    }

    @Test fun `fully applied global tint retains visible played fraction without a hue change`() {
        var fraction by mutableStateOf(.5f)
        var film by mutableStateOf(.5f)
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.size(100.dp, 60.dp).background(Color.Black).testTag("tinted_mini")) {
                    Box(Modifier.size(100.dp, 60.dp).clip(shape).glassMiniPlayerBackground(
                        GlassSettings(enabled = true, film = film, rim = 0f,
                            quality = GlassQuality.Performance, source = GlassTintSource.Custom,
                            customColor = 0xFF00FF00.toInt(), tintStrength = 1f),
                        shape, null, Color.Blue, progressColor = Color.Red.copy(alpha = .4f),
                        progressFraction = { fraction },
                    ))
                }
            }
        }
        fun capture() = compose.onNodeWithTag("tinted_mini").captureToImage().toPixelMap()
        listOf(0f, .5f, 1f).forEach { position ->
            compose.runOnIdle { fraction = position }
            val pixels = capture()
            listOf(.25f, .75f).forEach { horizontal ->
                val alpha = if (horizontal < position) .7f else .5f
                assertColor(Color.Green.copy(alpha = alpha).compositeOver(Color.Black),
                    pixels[(pixels.width * horizontal).toInt(), pixels.height / 2])
            }
            if (position == .5f) {
                assertTrue(pixels[pixels.width / 4, pixels.height / 2].green -
                    pixels[pixels.width * 3 / 4, pixels.height / 2].green > .15f)
            }
            assertEquals(Color.Black, pixels[0, 0])
        }
        compose.runOnIdle { film = 0f; fraction = .5f }
        val clear = capture()
        assertEquals(Color.Black, clear[clear.width / 4, clear.height / 2])
        assertEquals(Color.Black, clear[clear.width * 3 / 4, clear.height / 2])
        // The progress remains part of the film, with no separate rail at the bottom.
        val railY = clear.height - 6
        assertEquals(Color.Black, clear[clear.width / 4, railY])
        assertEquals(Color.Black, clear[clear.width * 3 / 4, railY])
        compose.runOnIdle { fraction = .9f }
        val advanced = capture()
        assertEquals(Color.Black, advanced[advanced.width * 3 / 4, railY])
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .025f)
        assertEquals(expected.green, actual.green, .025f)
        assertEquals(expected.blue, actual.blue, .025f)
        assertEquals(expected.alpha, actual.alpha, .025f)
    }
}
