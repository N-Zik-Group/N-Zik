package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.LocalQueuePlayerBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.player.lyrics.LyricsGlassScene
import org.junit.Assert.assertEquals
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
class PlayerLocalSceneSourcesTest {
    @get:Rule val compose = createComposeRule()
    private val clear = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
        depth = 0f, amount = 0f, vibrancy = 0f)
    private val shape = RoundedCornerShape(0.dp)

    @Composable private fun AppearanceScope(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
            Typography.fallback, shape, shape, shape), LocalGlassPreviewSettings provides clear,
            content = content)
    }

    @Composable private fun Sample(source: Backdrop?) {
        Box(Modifier.size(80.dp).testTag("sample").glassMaterial(clear, shape, ownBackdrop = source))
    }

    private fun assertSample(expected: Color) {
        val pixels = compose.onNodeWithTag("sample").captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals("red", expected.red, actual.red, .03f)
        assertEquals("green", expected.green, actual.green, .03f)
        assertEquals("blue", expected.blue, actual.blue, .03f)
    }

    @Test fun `mini queue owner exports live preceding scene without its overlay`() {
        val color = mutableStateOf(Color.Red)
        compose.setContent { AppearanceScope {
            Box(Modifier.size(100.dp)) {
                MiniPlayerQueueScene(queueVisible = true, scene = { Box(Modifier.fillMaxSize().background(color.value)) },
                    overlay = {
                        Box(Modifier.fillMaxSize().background(Color.Magenta))
                        Sample(LocalQueuePlayerBackdrop.current)
                    })
            }
        } }
        assertSample(Color.Red)
        compose.runOnIdle { color.value = Color.Green }
        assertSample(Color.Green)
    }

    @Test fun `lyrics scene includes changing native content but excludes utility foreground`() {
        val color = mutableStateOf(Color.Red)
        compose.setContent { AppearanceScope {
            LyricsGlassScene(Color.Black, Modifier.size(100.dp),
                scene = { Box(Modifier.fillMaxSize().background(color.value)) },
                controls = {
                    Box(Modifier.fillMaxSize().background(Color.Magenta))
                    Sample(LocalGlassContainerBackdrop.current)
                })
        } }
        assertSample(Color.Red)
        compose.runOnIdle { color.value = Color.Green }
        assertSample(Color.Green)
    }

    @Test fun `retained optical consumer updates when only queue expansion changes`() {
        val expansion = mutableStateOf(0f)
        compose.setContent { AppearanceScope {
            val player = rememberLayerBackdrop()
            val source = rememberPlayerQueueBackdrop(player) { expansion.value }
            Box(Modifier.size(100.dp)) {
                Box(Modifier.fillMaxSize().layerBackdrop(player).background(Color.Red))
                Sample(source)
            }
        } }
        assertSample(Color.Red)
        compose.runOnIdle { expansion.value = .65f }
        assertSample(Color(.5f, 0f, 0f))
        compose.runOnIdle { expansion.value = 0f }
        assertSample(Color.Red)
    }

    @Test fun `lyrics utility samples measured transport gradient including live alpha and bounds`() {
        val height = mutableStateOf(40.dp)
        val shade = mutableStateOf(Color.Black.copy(alpha = .5f))
        compose.setContent { AppearanceScope {
            LyricsGlassScene(Color.Red, Modifier.size(100.dp).testTag("gradient.scene"),
                transportHeight = height.value, transportShade = shade.value,
                scene = {}, controls = { Sample(LocalGlassContainerBackdrop.current) })
        } }
        fun assertGradient(white: Boolean) {
            val pixels = compose.onNodeWithTag("gradient.scene").captureToImage().toPixelMap()
            val scale = pixels.width / 100f
            val x = (50 * scale).toInt()
            val outside = (2 * scale).toInt()
            for (yDp in listOf(20, 85)) {
                val y = (yDp * scale).toInt()
                val expected = pixels[outside, y]
                val actual = pixels[x, y]
                assertEquals("gradient red at $yDp", expected.red, actual.red, .03f)
                assertEquals("gradient green at $yDp", expected.green, actual.green, .03f)
                assertEquals("gradient blue at $yDp", expected.blue, actual.blue, .03f)
            }
            val bottom = pixels[outside, (85 * scale).toInt()]
            assertTrue("Fixture contains a visible gradient", if (white) bottom.green > .05f else bottom.red < .95f)
            assertEquals("Gradient cannot fill above its actual bounds", Color.Red, pixels[outside, (20 * scale).toInt()])
        }
        assertGradient(false)
        compose.runOnIdle { height.value = 20.dp; shade.value = Color.White.copy(alpha = .4f) }
        assertGradient(true)
    }
}
