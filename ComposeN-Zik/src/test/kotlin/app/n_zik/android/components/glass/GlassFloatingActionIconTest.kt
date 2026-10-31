package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class GlassFloatingActionIconTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native floating film stays live in enabled disabled and fallback renderers`() {
        val enabled = mutableStateOf(true)
        val film = mutableStateOf(Color.Red)
        val quality = mutableStateOf(GlassQuality.Balanced)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GlassFloatingActionIcon(
                    painterResource(R.drawable.chevron_up), {},
                    Modifier.testTag("floating"), enabled = enabled.value,
                    settings = GlassSettings(quality = quality.value, film = 1f, rim = 0f,
                        blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f),
                    filmColor = film.value,
                )
            }
        }
        fun assertFilm(expected: Color) {
            val pixels = compose.onNodeWithTag("floating").captureToImage().toPixelMap()
            // Read clear material above the central glyph, away from its outer shadow.
            val actual = pixels[pixels.width / 2, pixels.height / 4]
            assertEquals(expected.red, actual.red, .03f)
            assertEquals(expected.green, actual.green, .03f)
            assertEquals(expected.blue, actual.blue, .03f)
        }
        for (profile in listOf(GlassQuality.Balanced, GlassQuality.Performance)) {
            for (interactive in listOf(true, false)) {
                compose.runOnIdle { quality.value = profile; enabled.value = interactive; film.value = Color.Red }
                assertFilm(Color.Red)
                compose.runOnIdle { film.value = Color.Blue }
                assertFilm(Color.Blue)
            }
        }
    }

    @Test fun `canvas backdrop fills the complete logical surface at reduced recording scales`() {
        val scale = mutableStateOf(.4f)
        val background = mutableStateOf(Color.Red)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            val backdrop = rememberCanvasBackdrop { drawRect(background.value) }
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.size(100.dp, 80.dp).testTag("canvas")
                    .glassMaterial(
                        settings = GlassSettings(film = 0f, rim = 0f, blur = 0f,
                            vibrancy = 0f, depth = 0f, amount = 0f),
                        shape = RoundedCornerShape(8.dp), ownBackdrop = backdrop,
                        backdropScale = scale.value, drawShadow = false,
                    ))
            }
        }
        fun assertFilled(expected: Color) {
            val pixels = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()
            for (x in listOf(pixels.width / 4, pixels.width * 3 / 4)) {
                for (y in listOf(pixels.height / 4, pixels.height * 3 / 4)) {
                    val actual = pixels[x, y]
                    assertEquals(expected.red, actual.red, .03f)
                    assertEquals(expected.green, actual.green, .03f)
                    assertEquals(expected.blue, actual.blue, .03f)
                }
            }
        }
        assertFilled(Color.Red)
        compose.runOnIdle { background.value = Color.Blue }
        assertFilled(Color.Blue)
        compose.runOnIdle { scale.value = 1f }
        assertFilled(Color.Blue)
    }

    @Test fun `floating action samples live list sibling without capturing itself or containing page`() {
        val background = mutableStateOf(Color.Red)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        val settings = GlassSettings(enabled = true, film = 0f, rim = 0f, blur = 0f,
            vibrancy = 0f, depth = 0f, amount = 0f)
        compose.setContent {
            val page = rememberCanvasBackdrop { error("Floating action must not sample its containing page") }
            val backdrop = rememberGlassFloatingActionBackdrop(settings)
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassBackdrop provides page) {
                Box(Modifier.size(160.dp)) {
                    Box(Modifier.size(160.dp).glassFloatingActionContent(backdrop).background(background.value))
                    Box(Modifier.size(160.dp).background(Color.Green))
                    GlassFloatingActions(backdrop) {
                        GlassFloatingActionIcon(painterResource(R.drawable.chevron_up), {},
                            Modifier.testTag("floating"), settings = settings)
                    }
                }
            }
        }
        fun assertBackground(expected: Color) {
            val pixels = compose.onNodeWithTag("floating").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 4]
            assertEquals(expected.red, actual.red, .03f)
            assertEquals(expected.green, actual.green, .03f)
            assertEquals(expected.blue, actual.blue, .03f)
        }
        assertBackground(Color.Red)
        compose.runOnIdle { background.value = Color.Blue }
        assertBackground(Color.Blue)
    }

    @Test fun `floating content capture is absent when glass action material is off`() {
        var capturePresent = true
        compose.setContent {
            capturePresent = rememberGlassFloatingActionBackdrop(GlassSettings(enabled = false)) != null
        }
        compose.runOnIdle { assertFalse(capturePresent) }
    }

    @Test fun `movable floating blur samples its current position in an offset content sibling`() {
        val position = mutableStateOf(IntOffset.Zero)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        val settings = GlassSettings(enabled = true, film = 0f, rim = 0f, blur = 2f,
            vibrancy = 0f, depth = 0f, amount = 0f)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val backdrop = rememberGlassFloatingActionBackdrop(settings)
                Box(Modifier.size(320.dp, 180.dp)) {
                    Box(Modifier.offset(16.dp, 20.dp).size(288.dp, 140.dp)
                        .glassFloatingActionContent(backdrop).drawBehind {
                            drawRect(Color.Red)
                            drawRect(Color.Blue, Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height))
                        })
                    Box(Modifier.size(320.dp, 180.dp).background(Color.Green))
                    GlassFloatingActions(backdrop) {
                        GlassFloatingActionIcon(
                            painterResource(R.drawable.chevron_up), {},
                            Modifier.offset(32.dp, 40.dp).offset { position.value }.testTag("floating"),
                            movable = true, settings = settings,
                        )
                    }
                }
            }
        }
        fun assertSample(expected: Color) {
            val pixels = compose.onNodeWithTag("floating").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 4]
            assertEquals(expected.red, actual.red, .03f)
            assertEquals(expected.green, actual.green, .03f)
            assertEquals(expected.blue, actual.blue, .03f)
        }
        assertSample(Color.Red)
        compose.runOnIdle { position.value = IntOffset(with(compose.density) { 188.dp.roundToPx() }, 0) }
        assertSample(Color.Blue)
        compose.runOnIdle { position.value = IntOffset.Zero }
        assertSample(Color.Red)
    }

    @Test fun `movable floating action lets its parent reposition after long press and clicks only once`() {
        val offset = mutableStateOf(Offset.Zero)
        var clicks = 0
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.size(300.dp)) {
                    Box(Modifier.offset { IntOffset(offset.value.x.toInt(), offset.value.y.toInt()) }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress { change, amount ->
                                change.consume()
                                offset.value += amount
                            }
                        }) {
                        GlassFloatingActionIcon(
                            painterResource(R.drawable.chevron_up), { clicks++ },
                            Modifier.testTag("floating"), movable = true,
                            settings = GlassSettings(enabled = true, quality = GlassQuality.Performance),
                        )
                    }
                }
            }
        }
        val before = compose.onNodeWithTag("floating").fetchSemanticsNode().boundsInRoot.topLeft
        compose.onNodeWithTag("floating").performTouchInput {
            down(center)
            advanceEventTime(1000)
            moveBy(Offset(45f, 35f), 100)
            moveBy(Offset(45f, 35f), 100)
            up()
        }
        compose.runOnIdle {
            assertEquals(90f, offset.value.x, 1f)
            assertEquals(70f, offset.value.y, 1f)
            assertEquals(0, clicks)
        }
        val after = compose.onNodeWithTag("floating").fetchSemanticsNode().boundsInRoot.topLeft
        assertEquals(90f, after.x - before.x, 1f)
        assertEquals(70f, after.y - before.y, 1f)
        compose.onNodeWithTag("floating").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun `floating material samples the changing safe container instead of containing page`() {
        val background = mutableStateOf(Color.Red)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            val page = rememberCanvasBackdrop { error("Floating action must not sample its containing page") }
            val container = rememberCanvasBackdrop { drawRect(background.value) }
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassBackdrop provides page, LocalGlassContainerBackdrop provides container) {
                GlassFloatingActionIcon(painterResource(R.drawable.chevron_up), {},
                    Modifier.testTag("floating"), settings = GlassSettings(enabled = true,
                        film = 0f, rim = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f))
            }
        }
        fun assertBackground(expected: Color) {
            val pixels = compose.onNodeWithTag("floating").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 4]
            assertEquals(expected.red, actual.red, .03f)
            assertEquals(expected.green, actual.green, .03f)
            assertEquals(expected.blue, actual.blue, .03f)
        }
        assertBackground(Color.Red)
        compose.runOnIdle { background.value = Color.Blue }
        assertBackground(Color.Blue)
    }

    @Test fun `floating hook keeps size callbacks disabled state and native fallback`() {
        val enabled = mutableStateOf(true)
        val glass = mutableStateOf(true)
        var handled = false
        var clicks = 0
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                handled = GlassFloatingActionIcon(
                    icon = painterResource(R.drawable.chevron_up),
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("floating"),
                    enabled = enabled.value,
                    contentDescription = "Scroll to top",
                    settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance),
                )
            }
        }
        compose.onNodeWithTag("floating").assertWidthIsEqualTo(62.dp).assertHeightIsEqualTo(62.dp)
            .performTouchInput { click() }
        compose.runOnIdle { assertTrue(handled); assertEquals(1, clicks); enabled.value = false }
        compose.onNodeWithTag("floating").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks); glass.value = false }
        compose.onNodeWithTag("floating").assertDoesNotExist()
        compose.runOnIdle { assertFalse(handled) }
    }
}
