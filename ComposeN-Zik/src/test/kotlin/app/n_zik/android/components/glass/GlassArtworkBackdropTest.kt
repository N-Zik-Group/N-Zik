package app.n_zik.android.components.glass

import android.app.Application
import app.n_zik.android.components.playlist.LocalPlaylistHeaderSurfaceScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassArtworkBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `artwork source excludes controls and follows artwork orientation and glass off`() {
        val artworkColor = mutableStateOf(Color.Red)
        val hasArtwork = mutableStateOf(true)
        val glass = mutableStateOf(true)
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            val inherited = rememberCanvasBackdrop { drawRect(Color.Blue) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassBackdrop provides inherited,
            ) {
                GlassArtworkBackdrop(hasArtwork.value, GlassSettings(enabled = glass.value)) { artwork ->
                    val source = requireNotNull(LocalGlassBackdrop.current)
                    if (!glass.value) assertSame(inherited, source)
                    else assertSame(source, LocalGlassContainerBackdrop.current)
                    Box(Modifier.size(100.dp)) {
                        if (hasArtwork.value) Box(Modifier.size(100.dp).then(artwork).background(artworkColor.value))
                        // This sibling is visibly green but must never enter the optical source.
                        Box(Modifier.size(100.dp).background(Color.Green))
                        Box(Modifier.size(64.dp).testTag("sample")
                            .drawPlainBackdrop(source, { RectangleShape }, effects = {}))
                    }
                }
            }
        }
        assertSample(Color.Red)
        compose.runOnIdle { artworkColor.value = Color.Magenta }
        assertSample(Color.Magenta)
        compose.runOnIdle { hasArtwork.value = false }
        assertSample(DefaultDarkColorPalette.background0)
        compose.runOnIdle { glass.value = false }
        assertSample(Color.Blue)
    }

    @Test fun `local playlist inline controls use the panel fill instead of the page fill`() {
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            val page = rememberCanvasBackdrop { drawRect(DefaultDarkColorPalette.background0) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette,
                    typography = typographyOf(Color.White, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassBackdrop provides page,
                LocalGlassContainerBackdrop provides page,
            ) {
                LocalPlaylistHeaderSurfaceScope(GlassSettings(enabled = false)) {
                    val panel = requireNotNull(LocalGlassContainerBackdrop.current)
                    Box(Modifier.size(64.dp).testTag("sample")
                        .drawPlainBackdrop(panel, { RectangleShape }, effects = {}))
                }
            }
        }
        org.junit.Assert.assertNotEquals(DefaultDarkColorPalette.background0, DefaultDarkColorPalette.background1)
        assertSample(DefaultDarkColorPalette.background1)
    }

    private fun assertSample(expected: Color) {
        val pixels = compose.onNodeWithTag("sample").captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals(expected.red, actual.red, .015f)
        assertEquals(expected.green, actual.green, .015f)
        assertEquals(expected.blue, actual.blue, .015f)
    }
}
