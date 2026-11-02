package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import app.n_zik.android.components.navigation.header.HeaderIcon
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
class HomeHeaderMaterialSourceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `shared Home controls sample the rendered pane and react to page and film changes`() = verifyOwner(true)

    @Test fun `native Home controls sample their opaque panel instead of covered page`() = verifyOwner(false)

    private fun verifyOwner(sharedOwner: Boolean) {
        val scene = mutableStateOf(Color.Blue)
        val film = mutableStateOf(.5f)
        val panel = mutableStateOf(Color.Red)
        val offset = mutableFloatStateOf(0f)
        val height = mutableIntStateOf(0)
        var controlFilm = Color.Unspecified
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(film = film.value, blur = 0f, rim = 0f,
                tintStrength = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
            val raw = rememberCanvasBackdrop { drawRect(scene.value) }
            val shared = rememberSharedHomeHeaderGlass(sharedOwner, "home")
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette.copy(background1 = Color.Yellow),
                    Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalGlassBackdrop provides raw,
                LocalGlassContainerBackdrop provides raw,
                LocalGlassMaterialSource provides raw,
                LocalSharedHomeHeaderGlass provides shared,
            ) {
                controlFilm = surfaceColor(Color.Yellow)
                Box(Modifier.size(200.dp, 200.dp)) {
                    Box(Modifier.fillMaxSize().sharedHomeGlassContent().background(scene.value))
                    HomeCollapsingHeader(offset, height, { 0.dp },
                        backgroundColor = panel.value, panelColor = panel.value, shape = shape) {
                        Row(Modifier.fillMaxWidth().height(80.dp)) {
                            HeaderIcon(R.drawable.search, tint = Color.Transparent,
                                modifier = Modifier.testTag("icon")) {}
                            Box(Modifier.size(36.dp).testTag("chip")
                                .glassChipBackground(controlFilm, shape, settings))
                        }
                    }
                }
            }
        }
        for (color in listOf(Color.Blue, Color.Green)) for (opacity in listOf(.5f, 0f)) {
            compose.runOnIdle { scene.value = color; film.value = opacity }
            val parent = if (sharedOwner) panel.value.copy(alpha = opacity).compositeOver(color) else panel.value
            assertControls(controlFilm.copy(alpha = opacity).compositeOver(parent))
        }
        compose.runOnIdle { panel.value = Color.Cyan }
        assertControls(Color.Cyan.takeUnless { sharedOwner } ?: Color.Green)
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
