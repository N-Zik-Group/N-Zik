package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.ui.styling.favoritesOverlay
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassNowPlayingRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `mounted row repaints violet theme highlight after first asynchronous palette completes`() {
        val loadedAccent = CompletableDeferred<Color>()
        val host = GlassMenuHostState()
        val settings = GlassSettings(enabled = true, source = GlassTintSource.None,
            film = 0f, rim = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val palette = remember { mutableStateOf(dynamicColorPaletteOf(Color(0xFF8B5CF6), true)) }
            val pageBackdrop = rememberLayerBackdrop()
            val overlayBackdrop = rememberLayerBackdrop()
            LaunchedEffect(Unit) { palette.value = dynamicColorPaletteOf(loadedAccent.await(), true) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = palette.value,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape,
                    uiRoundnessShape = shape,
                    artistThumbnailShape = shape,
                ),
                LocalGlassMenuHost provides host,
                LocalGlassBackdrop provides pageBackdrop,
            ) {
                Box(Modifier.size(160.dp, 64.dp)
                    .layerBackdrop(overlayBackdrop, frozen = { !host.hasOverlays })) {
                    Box(Modifier.size(160.dp, 64.dp).layerBackdrop(pageBackdrop)) {
                        Box(Modifier.size(160.dp, 64.dp).testTag("late.theme.row")
                            .glassNowPlayingRow(Color.Black, palette.value.favoritesOverlay, shape, settings))
                    }
                }
            }
        }
        fun centre(): Color {
            val pixels = compose.onNodeWithTag("late.theme.row").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        val initial = centre()
        compose.runOnIdle { loadedAccent.complete(Color.Cyan) }
        compose.waitForIdle()
        val updated = centre()
        assertNotEquals("theme completion must repaint the mounted row", initial, updated)
        assertColor(dynamicColorPaletteOf(Color.Cyan, true).favoritesOverlay.compositeOver(Color.Black), updated)
    }

    @Test fun `mounted row repaints when initial asynchronous artwork completes without navigation`() {
        val loadedColor = CompletableDeferred<Color?>()
        val host = GlassMenuHostState()
        val settings = GlassSettings(enabled = true, source = GlassTintSource.Artwork,
            tintStrength = 1f, film = 1f, rim = 0f, blur = 0f, vibrancy = 0f,
            depth = 0f, amount = 0f)
        val highlight = Color(0xFF8B5CF6).copy(alpha = .4f)
        var publishedColor: Color? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val artworkColor = rememberGlassArtworkColor(
                GlassArtwork("initial", "file:///initial.png"), true, true,
            ) { _, _ -> loadedColor.await() }
            val pageBackdrop = rememberLayerBackdrop()
            val overlayBackdrop = rememberLayerBackdrop()
            SideEffect {
                publishedColor = artworkColor
                host.artworkColor = artworkColor
            }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape,
                    uiRoundnessShape = shape,
                    artistThumbnailShape = shape,
                ),
                LocalGlassArtworkColor provides artworkColor,
                LocalGlassMenuHost provides host,
                LocalGlassBackdrop provides pageBackdrop,
            ) {
                // Match the production capture hierarchy, including the frozen
                // overlay source. The same row remains mounted for the entire load.
                Box(Modifier.size(160.dp, 64.dp)
                    .layerBackdrop(overlayBackdrop, frozen = { !host.hasOverlays })) {
                    Box(Modifier.size(160.dp, 64.dp).layerBackdrop(pageBackdrop)) {
                        Box(Modifier.size(160.dp, 64.dp).testTag("late.artwork.row")
                            .glassNowPlayingRow(Color.Black, highlight, shape, settings))
                    }
                }
            }
        }
        fun settleFrame() {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        fun centre(): Color {
            val pixels = compose.onNodeWithTag("late.artwork.row").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        repeat(3) { settleFrame() }
        val initial = centre()
        compose.runOnIdle {
            assertEquals(null, publishedColor)
            loadedColor.complete(Color.Cyan)
        }
        repeat(3) { settleFrame() }
        compose.runOnIdle { assertEquals(Color.Cyan, publishedColor) }
        val updated = centre()
        assertNotEquals("artwork published cyan but row still renders ${updated.toArgb().toUInt().toString(16)}",
            initial, updated)
        assertColor(Color.Cyan, updated)
    }

    @Test fun `mounted row follows custom hue and artwork colour changes immediately`() {
        val settings = mutableStateOf(GlassSettings(enabled = true,
            source = GlassTintSource.Custom, customColor = Color.Blue.toArgb(),
            tintStrength = 1f, film = 1f, rim = 0f, blur = 0f,
            vibrancy = 0f, depth = 0f, amount = 0f))
        val artwork = mutableStateOf(Color.Green)
        val highlight = Color.Red.copy(alpha = .4f)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape,
                    uiRoundnessShape = shape,
                    artistThumbnailShape = shape,
                ),
                LocalGlassArtworkColor provides artwork.value,
            ) {
                Box(Modifier.size(160.dp, 64.dp).testTag("tinted.row")
                    .glassNowPlayingRow(Color.Black, highlight, shape, settings.value))
            }
        }
        fun centre(): Color {
            val pixels = compose.onNodeWithTag("tinted.row").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        assertColor(Color.Blue, centre())
        compose.runOnIdle { settings.value = settings.value.copy(customColor = Color.Cyan.toArgb()) }
        assertColor(Color.Cyan, centre())
        compose.runOnIdle { settings.value = settings.value.copy(source = GlassTintSource.Artwork) }
        assertColor(Color.Green, centre())
        compose.runOnIdle { artwork.value = Color.Yellow }
        assertColor(Color.Yellow, centre())
        val strongTint = centre()
        compose.runOnIdle { settings.value = settings.value.copy(tintStrength = 0f) }
        assertNotEquals(strongTint, centre())
    }

    @Test fun `explicit glass sources ignore delayed native highlight including at zero tint strength`() {
        val settings = mutableStateOf(GlassSettings(enabled = true,
            source = GlassTintSource.Artwork, customColor = Color.Blue.toArgb(),
            tintStrength = 1f, film = 1f, rim = 0f, blur = 0f, vibrancy = 0f,
            depth = 0f, amount = 0f))
        val nativeHighlight = mutableStateOf(Color(0xFF8B5CF6).copy(alpha = .4f))
        var expectedFilm = Color.Unspecified
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape,
                    uiRoundnessShape = shape,
                    artistThumbnailShape = shape,
                ),
                LocalGlassArtworkColor provides Color.Cyan,
            ) {
                expectedFilm = glassSurfaceTint(settings.value)
                Box(Modifier.size(160.dp, 64.dp).testTag("single.source.row")
                    .glassNowPlayingRow(Color.Black, nativeHighlight.value, shape, settings.value))
            }
        }
        fun centre(): Color {
            val pixels = compose.onNodeWithTag("single.source.row").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        listOf(GlassTintSource.Artwork, GlassTintSource.Custom, GlassTintSource.SystemAccent,
            GlassTintSource.ThemeAccent).forEach { source ->
            listOf(1f, 0f).forEach { strength ->
                compose.runOnIdle {
                    settings.value = settings.value.copy(source = source, tintStrength = strength)
                    nativeHighlight.value = Color(0xFF8B5CF6).copy(alpha = .4f)
                }
                val beforeGlobalPalette = centre()
                compose.runOnIdle { nativeHighlight.value = Color.Red.copy(alpha = .4f) }
                assertColor(beforeGlobalPalette, centre())
                if (strength == 0f) {
                    assertColor(expectedFilm, centre())
                }
            }
        }
    }

    @Test fun `glass toggles preserve foreground and clicks without sampling containing page`() {
        val enabled = mutableStateOf(false)
        val nowPlaying = mutableStateOf(true)
        val background = Color(0xFF202020)
        val highlight = Color.Red.copy(alpha = .4f)
        var clicks = 0
        val containingPage = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) { error("A song row must never sample the page containing itself") }
        }
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape,
                    uiRoundnessShape = shape,
                    artistThumbnailShape = shape,
                ),
                LocalGlassBackdrop provides containingPage,
            ) {
                Box(
                    Modifier.size(160.dp, 64.dp).testTag("active.row")
                        .glassNowPlayingRow(background, highlight, shape,
                            GlassSettings(enabled = enabled.value, nowPlaying = nowPlaying.value,
                                blur = 24f, rim = 0f, depth = 0f, amount = 0f))
                        .clickable { clicks++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(20.dp).background(Color.Magenta))
                }
            }
        }
        listOf(false to true, true to true, true to false, true to true, false to true)
            .forEachIndexed { index, (glassEnabled, rowEnabled) ->
            compose.runOnIdle {
                enabled.value = glassEnabled
                nowPlaying.value = rowEnabled
            }
            val row = compose.onNodeWithTag("active.row")
            val pixels = row.captureToImage().toPixelMap()
            assertColor(Color.Magenta, pixels[pixels.width / 2, pixels.height / 2])
            if (!glassEnabled || !rowEnabled) {
                assertColor(highlight.compositeOver(background), pixels[pixels.width / 4, pixels.height / 2])
            }
            row.performClick()
            compose.runOnIdle { assertEquals(index + 1, clicks) }
        }
    }

    private fun assertColor(expected: Color, actual: Color) {
        val colors = "expected #${expected.toArgb().toUInt().toString(16)}, actual #${actual.toArgb().toUInt().toString(16)}"
        assertEquals("$colors (red)", expected.red, actual.red, .01f)
        assertEquals("$colors (green)", expected.green, actual.green, .01f)
        assertEquals("$colors (blue)", expected.blue, actual.blue, .01f)
        assertEquals("$colors (alpha)", expected.alpha, actual.alpha, .01f)
    }
}
