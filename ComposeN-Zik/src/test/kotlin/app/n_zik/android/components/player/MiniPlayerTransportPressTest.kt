package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.buttonzoomoutKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MiniPlayerTransportPressTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences
    @Before fun setup() { preferences.edit().putBoolean(buttonzoomoutKey, true).commit() }
    @After fun cleanup() { preferences.edit().remove(buttonzoomoutKey).commit() }

    @Test fun `actual mini transport buttons animate short taps with and without glass`() {
        val mode = mutableStateOf(false to 0)
        var clicks = 0
        val shape = RoundedCornerShape(12.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    val (glass, action) = mode.value
                    val settings = GlassSettings(enabled = glass)
                    if (action == 1) GlassMiniPlayerPlayPauseButton(
                        onClick = { clicks++ }, backgroundColor = Color.DarkGray,
                        contentDescription = "transport", settings = settings,
                    ) {
                        Box(Modifier.align(Alignment.Center).size(24.dp).testTag("glyph"))
                    } else GlassMiniPlayerIconButton(
                        icon = if (action == 0) R.drawable.play_skip_back else R.drawable.play_skip_forward,
                        color = Color.White, onClick = { clicks++ }, contentDescription = "transport",
                        settings = settings,
                    )
                }
            }
        }
        for (glass in listOf(false, true)) for (action in 0..2) {
            compose.runOnIdle { mode.value = glass to action }
            repeat(2) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            compose.mainClock.advanceTimeBy(1500)
            val tag = if (action == 1) "glyph" else "mini.player.glyph"
            fun glyphWidth() = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
            val before = glyphWidth()
            val clicksBefore = clicks
            compose.onNodeWithContentDescription("transport").performTouchInput { click() }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(64)
            assertTrue("Short tap must visibly shrink action=$action glass=$glass", glyphWidth() < before * .98f)
            compose.mainClock.advanceTimeBy(1500)
            assertEquals(before, glyphWidth(), 1f)
            compose.runOnIdle { assertEquals(clicksBefore + 1, clicks) }
        }
    }
}
