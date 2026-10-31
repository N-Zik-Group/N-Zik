package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgress
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgressProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MiniPlayerActionCanvasTest {
    @get:Rule val compose = createComposeRule()

    private data class State(val glass: Boolean = false, val fold: Float = 0f,
        val dock: Boolean = false, val action: Int = 0)

    @Test fun `resource and vector commands click at all canvas edges while retaining glyph and seat centers`() {
        val state = mutableStateOf(State())
        val clicks = IntArray(4)
        val shape = RoundedCornerShape(12.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        compose.setContent {
            val current = state.value
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalCompactMiniPlayerProgress provides current.fold,
                LocalCompactMiniPlayerProgressProvider provides if (current.dock) ({ current.fold }) else null) {
                Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                    val settings = GlassSettings(enabled = current.glass)
                    if (current.action == 3) GlassMiniPlayerIconButton(Icons.Filled.Computer, Color.White,
                        { clicks[3]++ }, "command", settings = settings)
                    else GlassMiniPlayerIconButton(
                        icon = listOf(R.drawable.play_skip_back, R.drawable.play_skip_forward, R.drawable.reorder)[current.action],
                        color = Color.White, onClick = { clicks[current.action]++ }, contentDescription = "command",
                        settings = settings, preserveNativeGeometry = current.action == 2)
                }
            }
        }
        for (glass in listOf(false, true)) for ((fold, dock) in listOf(0f to false, 1f to false,
            0f to true, .5f to true, 1f to true)) for (action in 0..3) {
            compose.runOnIdle { state.value = State(glass, fold, dock, action) }
            val canvas = miniPlayerControlSize(fold, dock)
            val owner = compose.onNodeWithContentDescription("command")
            owner.assertWidthIsEqualTo(canvas).assertHeightIsEqualTo(canvas)
            val glyph = compose.onNodeWithTag("mini.player.glyph", useUnmergedTree = true)
            val glyphSize = if (glass && action != 2) 22.dp else 24.dp
            glyph.assertWidthIsEqualTo(glyphSize).assertHeightIsEqualTo(glyphSize)
            val canvasCenter = owner.fetchSemanticsNode().boundsInRoot.center
            val glyphCenter = glyph.fetchSemanticsNode().boundsInRoot.center
            // Odd-pixel interpolated dock canvases center an even-pixel glyph within half a pixel.
            assertEquals(canvasCenter.x, glyphCenter.x, .5f)
            assertEquals(canvasCenter.y, glyphCenter.y, .5f)
            val before = clicks[action]
            for (edge in 0..3) owner.performTouchInput {
                click(when (edge) {
                    0 -> Offset(1f, center.y)
                    1 -> Offset(width - 1f, center.y)
                    2 -> Offset(center.x, 1f)
                    else -> Offset(center.x, height - 1f)
                })
            }
            compose.runOnIdle { assertEquals(before + 4, clicks[action]) }
        }
    }

    @Test fun `enlarged canvas leaves native and glass reserved geometry unchanged`() {
        assertEquals(MiniPlayerControlGeometry(42.dp, 28.dp, 40.dp), miniPlayerIconGeometry(0f, false, false))
        assertEquals(MiniPlayerControlGeometry(42.dp, 46.dp, 42.dp), miniPlayerIconGeometry(0f, false, true))
        assertEquals(MiniPlayerControlGeometry(48.dp, 48.dp, 48.dp), miniPlayerIconGeometry(1f, false, false))
        assertEquals(MiniPlayerControlGeometry(36.dp, 36.dp, 36.dp), miniPlayerIconGeometry(1f, true, false))
        assertEquals(MiniPlayerControlGeometry(36.dp, 36.dp, 36.dp), miniPlayerIconGeometry(1f, true, true))
    }
}
