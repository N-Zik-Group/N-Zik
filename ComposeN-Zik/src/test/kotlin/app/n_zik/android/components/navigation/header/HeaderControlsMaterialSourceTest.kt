package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.glassChipBackground
import app.n_zik.android.surfaceColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeaderControlsMaterialSourceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `shared and standalone header controls use raw live scene and apply their film once`() {
        val shared = mutableStateOf(true)
        val scene = mutableStateOf(Color.Blue)
        val film = mutableStateOf(0f)
        var controlFilm = Color.Unspecified
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(enabled = true, film = film.value, blur = 0f,
                rim = 0f, tintStrength = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette.copy(background1 = Color.Yellow),
                    Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
            ) {
                controlFilm = surfaceColor(Color.Yellow)
                // Simulate the already filmed output exported by the header pane. Sampling
                // this output again at film .5 would produce .75 film instead of .5.
                val output = rememberCanvasBackdrop {
                    drawRect(controlFilm.copy(alpha = film.value).compositeOver(scene.value))
                }
                val raw = rememberCanvasBackdrop { drawRect(scene.value) }
                val stale = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(
                    LocalGlassBackdrop provides stale,
                    LocalGlassContainerBackdrop provides stale,
                    LocalGlassMaterialSource provides (if (shared.value) stale else raw),
                ) {
                    HeaderControlsGlassSources(output, ownsMaterial = true,
                        materialSource = raw.takeIf { shared.value }) {
                        Row {
                            HeaderIcon(R.drawable.search, tint = Color.Transparent,
                                modifier = Modifier.testTag("icon")) {}
                            Box(Modifier.size(36.dp).testTag("chip")
                                .glassChipBackground(controlFilm, shape, settings))
                        }
                    }
                }
            }
        }
        for (owner in listOf(true, false)) for (color in listOf(Color.Blue, Color.Green)) {
            for (opacity in listOf(0f, .5f)) {
                compose.runOnIdle { shared.value = owner; scene.value = color; film.value = opacity }
                assertControls(controlFilm.copy(alpha = opacity).compositeOver(color))
            }
        }
    }

    @Test fun `native header controls keep opaque header fill instead of inherited artwork`() {
        val native = mutableStateOf(Color.Blue)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(enabled = true, film = 0f, blur = 0f, rim = 0f,
                tintStrength = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
            val page = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalGlassBackdrop provides page,
                LocalGlassContainerBackdrop provides page,
                LocalGlassMaterialSource provides page,
            ) {
                val header = rememberHeaderControlsBackdrop(true, false, native.value)
                HeaderControlsGlassSources(header, ownsMaterial = false) {
                    Row {
                        HeaderIcon(R.drawable.search, tint = Color.Transparent,
                            modifier = Modifier.testTag("icon")) {}
                        Box(Modifier.size(36.dp).testTag("chip")
                            .glassChipBackground(Color.Yellow, shape, settings))
                    }
                }
            }
        }
        assertControls(Color.Blue)
        compose.runOnIdle { native.value = Color.Green }
        assertControls(Color.Green)
    }

    private fun assertControls(expected: Color) {
        for (tag in listOf("icon", "chip")) {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals("$tag red", expected.red, actual.red, .03f)
            assertEquals("$tag green", expected.green, actual.green, .03f)
            assertEquals("$tag blue", expected.blue, actual.blue, .03f)
        }
    }
}
