package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassMiniPlayerIconButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `central control retains size centered content and click while glass settings change`() {
        var settings by mutableStateOf(GlassSettings(enabled = true))
        var buffering by mutableStateOf(false)
        var clicks = 0
        val shape = RoundedCornerShape(12.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GlassMiniPlayerPlayPauseButton(
                    onClick = { clicks++ },
                    backgroundColor = Color.Black,
                    contentDescription = "Play or pause",
                    settings = settings,
                ) {
                    Box(Modifier.align(Alignment.Center).size(24.dp)
                        .testTag(if (buffering) "buffering" else "play-pause-icon"))
                }
            }
        }
        (GlassAreaMode.entries.map { mode ->
            GlassSettings(enabled = true, areaModes = mapOf(GlassArea.MiniPlayer to mode))
        } + listOf(
            GlassSettings(enabled = false),
            GlassSettings(enabled = true, miniPlayer = false),
        )).forEachIndexed { index, config ->
            compose.runOnIdle { settings = config; buffering = index % 2 == 0 }
            val button = compose.onNodeWithContentDescription("Play or pause")
            button.assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
                .performTouchInput { click() }
            val content = compose.onNodeWithTag(
                if (buffering) "buffering" else "play-pause-icon", useUnmergedTree = true,
            )
            content.assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
            assertEquals(button.fetchSemanticsNode().boundsInRoot.center, content.fetchSemanticsNode().boundsInRoot.center)
            compose.runOnIdle { assertEquals(index + 1, clicks) }
        }
    }

    @Test
    fun `guest lock and device callbacks survive both glass preference changes`() {
        var settings by mutableStateOf(GlassSettings(enabled = true))
        var enabled by mutableStateOf(true)
        var skips = 0
        var outputs = 0
        val shape = RoundedCornerShape(12.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Row {
                    GlassMiniPlayerIconButton(
                        icon = R.drawable.play_skip_forward,
                        color = Color.White,
                        onClick = { skips++ },
                        contentDescription = "Next track",
                        enabled = enabled,
                        settings = settings,
                    )
                    GlassMiniPlayerIconButton(
                        icon = Icons.Filled.Computer,
                        color = Color.White,
                        onClick = { outputs++ },
                        contentDescription = "Audio output",
                        settings = settings,
                    )
                }
            }
        }
        (GlassAreaMode.entries.map { mode ->
            GlassSettings(enabled = true, areaModes = mapOf(GlassArea.MiniPlayer to mode))
        } + listOf(
            GlassSettings(enabled = false),
            GlassSettings(enabled = true, miniPlayer = false),
        )).forEachIndexed { index, config ->
            compose.runOnIdle { settings = config; enabled = true }
            compose.onNodeWithContentDescription("Next track").assertIsEnabled()
                .performTouchInput { click() }
            compose.runOnIdle { enabled = false }
            compose.onNodeWithContentDescription("Next track").assertIsNotEnabled()
                .performTouchInput { click() }
            compose.onNodeWithContentDescription("Audio output").assertIsEnabled()
                .performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(index + 1, skips)
                assertEquals(index + 1, outputs)
            }
        }
    }
}
