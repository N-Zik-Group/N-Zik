package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
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
class GlassSceneMaterialSourceTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(0.dp)
    private val clear = GlassSettings(enabled = true, film = 0f, blur = 0f,
        rim = 0f, vibrancy = 0f, depth = 0f, amount = 0f, tintStrength = 0f)

    @Composable private fun RedParent(content: @Composable () -> Unit) {
        val parent = rememberCanvasBackdrop { drawRect(Color.Red) }
        CompositionLocalProvider(
            LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
            LocalGlassBackdrop provides parent,
            LocalGlassContainerBackdrop provides parent,
            LocalGlassMaterialSource provides parent,
            LocalGlassPreviewSettings provides clear,
            content = content,
        )
    }

    @Composable private fun Sample(tag: String, settings: GlassSettings = clear) {
        Box(Modifier.size(64.dp).testTag(tag)
            .glassChipBackground(Color.Yellow, shape, settings))
    }

    private fun assertColor(tag: String, expected: Color) {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals("$tag red", expected.red, actual.red, .03f)
        assertEquals("$tag green", expected.green, actual.green, .03f)
        assertEquals("$tag blue", expected.blue, actual.blue, .03f)
        assertEquals("$tag alpha", expected.alpha, actual.alpha, .03f)
    }

    @Test fun `queue toolbar children sample native fill while mini uses live list`() {
        val listColor = mutableStateOf(Color.Blue)
        val film = mutableStateOf(0f)
        compose.setContent {
            RedParent {
                val list = rememberCanvasBackdrop { drawRect(listColor.value) }
                val settings = clear.copy(film = film.value)
                CompositionLocalProvider(LocalQueueGlassBackdrop provides list) {
                    Column {
                        GlassQueueToolbar(Modifier.size(80.dp), settings, systemBottomInset = 0.dp) {
                            Sample("toolbar", settings)
                        }
                        QueueEmbeddedMini {
                            val source = LocalGlassContainerBackdrop.current
                            Box(Modifier.size(80.dp).testTag("mini")
                                .glassMiniPlayerBackground(settings, shape, source, Color.Yellow,
                                    Color.Yellow, { 0f }))
                        }
                    }
                }
            }
        }
        for (scene in listOf(Color.Blue, Color.Green)) for (opacity in listOf(0f, .5f)) {
            compose.runOnIdle { listColor.value = scene; film.value = opacity }
            val expected = Color.Yellow.copy(alpha = opacity).compositeOver(scene)
            val toolbarExpected = Color.Yellow.copy(alpha = opacity).compositeOver(DefaultDarkColorPalette.background1)
            assertColor("toolbar", toolbarExpected)
            assertColor("mini", expected)
        }
    }

    @Test fun `artwork and floating action owners replace inherited raw source with their local scene`() {
        val scene = mutableStateOf(Color.Blue)
        compose.setContent {
            RedParent {
                Column {
                    GlassArtworkBackdrop(hasArtwork = true, settings = clear) { artwork ->
                        Box(Modifier.size(80.dp)) {
                            Box(Modifier.matchParentSize().then(artwork).background(scene.value))
                            Sample("artwork")
                        }
                    }
                    val list = rememberCanvasBackdrop { drawRect(scene.value) }
                    GlassFloatingActions(list) { Sample("floating") }
                }
            }
        }
        for (color in listOf(Color.Blue, Color.Green)) {
            compose.runOnIdle { scene.value = color }
            compose.mainClock.advanceTimeByFrame()
            assertColor("artwork", color)
            assertColor("floating", color)
        }
    }

    @Test fun `native row badges retain row fill while optical row badges do not repeat the parent film`() {
        val rowFill = mutableStateOf(Color.Blue)
        compose.setContent {
            RedParent {
                Column {
                    GlassSongRowSurface(rowFill.value, null, shape, clear,
                        useNowPlayingMaterial = false) { surface ->
                        Box(surface) { Sample("native.row") }
                    }
                    GlassSongRowSurface(rowFill.value, Color.Transparent, shape,
                        clear.copy(film = .75f)) { surface ->
                        Box(surface) { Sample("optical.row") }
                    }
                }
            }
        }
        for (color in listOf(Color.Blue, Color.Green)) {
            compose.runOnIdle { rowFill.value = color }
            assertColor("native.row", color)
            assertColor("optical.row", color)
        }
    }
}
