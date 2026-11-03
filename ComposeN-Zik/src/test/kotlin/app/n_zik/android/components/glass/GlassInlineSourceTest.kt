package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.search.SearchBarBubble
import app.n_zik.android.components.search.SearchBarBubbleButton
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassInlineSourceTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(0.dp)
    private val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
    private val forbidden = object : Backdrop {
        override val isCoordinatesDependent = false
        override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
            error("Inline surfaces must never sample their own page or an unrelated parent")
        }
    }

    @Test fun `inline cards search mood and search actions sample the changing raw source`() {
        val sourceColor = mutableStateOf(Color.Green)
        val settings = GlassSettings(enabled = true, film = 0f, blur = 0f, rim = 0f, vibrancy = 0f)
        compose.setContent {
            val source = rememberCanvasBackdrop { drawRect(sourceColor.value) }
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassBackdrop provides forbidden, LocalGlassContainerBackdrop provides forbidden,
                LocalGlassMaterialSource provides source, LocalGlassPreviewSettings provides settings) {
                Row {
                    SettingsCardSurface(Modifier.size(64.dp).testTag("card"), shape, Color.Blue, Color.Black, Color.Transparent, settings = settings) { }
                    Box(Modifier.size(64.dp).testTag("search").glassSearchBarBackground(Color.Black, Color.Blue, shape, Color.Transparent, settings = settings))
                    Box(Modifier.size(64.dp).testTag("mood").glassMoodCardBackground(Color.Black, Color.Blue, shape, settings))
                    SearchBarBubbleButton(SearchBarBubble(R.drawable.close, R.string.common_action_clear, onClick = {}), settings)
                }
            }
        }
        fun assertSource(expected: Color) {
            listOf("card", "search", "mood", "unified_search_bubble_${R.drawable.close}").forEach { tag ->
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                assertColor(expected, pixels[pixels.width / 4, pixels.height / 4])
            }
        }
        assertSource(Color.Green)
        compose.runOnIdle { sourceColor.value = Color.Red }
        assertSource(Color.Red)
    }

    @Test fun `nested badge composites over its rendered Glass card and retains native container when off`() {
        val settings = mutableStateOf(GlassSettings(enabled = true, film = .5f, blur = 0f, rim = 0f, vibrancy = 0f, tintStrength = 0f))
        compose.setContent {
            val source = rememberCanvasBackdrop { drawRect(Color.Green) }
            val current = settings.value
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassMaterialSource provides source,
                LocalGlassBackdrop provides forbidden, LocalGlassPreviewSettings provides current) {
                SettingsCardSurface(Modifier.size(80.dp), shape, Color.Blue, Color.Black, Color.Transparent, settings = current) {
                    Box(Modifier.size(64.dp).testTag("badge").glassChipBackground(Color.Red.copy(alpha = .1f), shape, current))
                }
            }
        }
        fun badgeColor(): Color {
            val pixels = compose.onNodeWithTag("badge").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        val renderedCard = Color.Blue.copy(alpha = .5f).compositeOver(Color.Green)
        assertColor(Color.Red.copy(alpha = .5f).compositeOver(renderedCard), badgeColor())
        compose.runOnIdle { settings.value = settings.value.copy(settingsCards = false) }
        assertColor(Color.Red.copy(alpha = .5f).compositeOver(Color.Blue), badgeColor())
    }

    @Test fun `native inline surfaces retain original fill and never sample raw or page captures`() {
        val settings = GlassSettings(enabled = false)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassMaterialSource provides forbidden,
                LocalGlassBackdrop provides forbidden, LocalGlassContainerBackdrop provides forbidden) {
                Row(Modifier.background(Color.White)) {
                    SettingsCardSurface(Modifier.size(64.dp).testTag("card"), shape, Color.Blue, Color.Black, Color.Transparent, settings = settings) { }
                    Box(Modifier.size(64.dp).testTag("search").glassSearchBarBackground(Color.Black, Color.Blue, shape, Color.Transparent, settings = settings))
                    Box(Modifier.size(64.dp).testTag("mood").glassMoodCardBackground(Color.Black, Color.Blue, shape, settings))
                }
            }
        }
        listOf("card", "search", "mood").forEach { tag ->
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            assertColor(Color.Blue, pixels[pixels.width / 2, pixels.height / 2])
        }
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .03f)
        assertEquals(expected.green, actual.green, .03f)
        assertEquals(expected.blue, actual.blue, .03f)
        assertEquals(expected.alpha, actual.alpha, .03f)
    }
}
