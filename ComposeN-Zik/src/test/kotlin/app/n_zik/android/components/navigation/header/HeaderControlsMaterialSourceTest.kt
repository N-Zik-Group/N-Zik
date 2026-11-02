package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassChipBackground
import app.n_zik.android.components.ui.header.HeaderBadgeBox
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

    @Test fun `Glass debug badge on native header excludes covered page and follows native fill changes`() {
        val page = mutableStateOf(Color.Red)
        val native = mutableStateOf(Color.Blue)
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(header = false, actionButtons = true, film = 0f,
                blur = 0f, rim = 0f, tintStrength = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
            val raw = rememberCanvasBackdrop { drawRect(page.value) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalGlassBackdrop provides raw,
            ) {
                // AppHeader paints the native fill before its children at rest.
                // Header-disabled still leaves the debug badge's ActionButtons Glass enabled.
                Box(Modifier.size(160.dp, 64.dp).background(page.value)) {
                    Box(Modifier.size(160.dp, 64.dp).background(native.value)) {
                        val source = rememberHeaderControlsBackdrop(false, native.value)
                        HeaderControlsGlassSources(source) {
                            HeaderBadgeBox("DEBUG", Color.Transparent,
                                modifier = Modifier.size(72.dp, 32.dp).testTag("native-badge"))
                        }
                    }
                }
            }
        }
        fun assertBadge(expected: Color) {
            val pixels = compose.onNodeWithTag("native-badge").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals("badge red", expected.red, actual.red, .03f)
            assertEquals("badge green", expected.green, actual.green, .03f)
            assertEquals("badge blue", expected.blue, actual.blue, .03f)
        }
        assertBadge(Color.Blue)
        compose.runOnIdle { page.value = Color.Yellow }
        assertBadge(Color.Blue)
        compose.runOnIdle { native.value = Color.Green }
        assertBadge(Color.Green)
    }

    @Test fun `standalone header controls sample the rendered live parent before applying their own film`() {
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
                val raw = rememberCanvasBackdrop { drawRect(scene.value) }
                val stale = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(
                    LocalGlassBackdrop provides stale,
                    LocalGlassContainerBackdrop provides stale,
                    LocalGlassMaterialSource provides raw,
                ) {
                    StandaloneHeaderGlass(settings, Color.Red, raw, 0f, 64f, { 0f }, WindowInsets(0)) { output ->
                        HeaderControlsGlassSources(output) {
                            Row(Modifier.fillMaxWidth().height(64.dp)) {
                                HeaderIcon(R.drawable.search, tint = Color.Transparent,
                                    modifier = Modifier.testTag("icon")) {}
                                Box(Modifier.size(36.dp).testTag("chip")
                                    .glassChipBackground(controlFilm, shape, settings))
                            }
                        }
                    }
                }
            }
        }
        for (color in listOf(Color.Blue, Color.Green)) {
            for (opacity in listOf(0f, .5f)) {
                compose.runOnIdle { scene.value = color; film.value = opacity }
                val parent = Color.Red.copy(alpha = opacity).compositeOver(color)
                assertControls(controlFilm.copy(alpha = opacity).compositeOver(parent))
            }
        }
    }

    @Test fun `Top header controls sample the real unified navigation pane`() {
        val scene = mutableStateOf(Color.Blue)
        val film = mutableStateOf(.5f)
        var controlFilm = Color.Unspecified
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(film = film.value, blur = 0f, rim = 0f,
                tintStrength = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
            val raw = rememberCanvasBackdrop { drawRect(scene.value) }
            val unified = rememberLayerBackdrop()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette.copy(background1 = Color.Yellow),
                    Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalGlassBackdrop provides raw,
                LocalGlassMaterialSource provides raw,
                LocalUnifiedTopGlassSurface provides unified,
            ) {
                controlFilm = surfaceColor(Color.Yellow)
                Box {
                    UnifiedTopNavigationGlass(settings, Color.Red, raw, { 0 }) {
                        Box(Modifier.fillMaxWidth().height(64.dp))
                    }
                    val parent = rememberHeaderControlsBackdrop(true, Color.Magenta)
                    HeaderControlsGlassSources(parent) {
                        Row(Modifier.fillMaxWidth().height(64.dp)) {
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
            val parent = Color.Red.copy(alpha = opacity).compositeOver(color)
            assertControls(controlFilm.copy(alpha = opacity).compositeOver(parent))
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
                val header = rememberHeaderControlsBackdrop(false, native.value)
                HeaderControlsGlassSources(header) {
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
