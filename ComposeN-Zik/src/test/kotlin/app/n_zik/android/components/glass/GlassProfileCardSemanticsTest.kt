package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.ui.screens.profiles.ProfileCardSurface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassProfileCardSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `profile card retains its spoken label and independent child action with Glass on and off`() {
        val glass = mutableStateOf(true)
        var cardClicks = 0
        var childClicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            val settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            ), LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    ProfileCardSurface(
                        Modifier.size(240.dp, 100.dp).testTag("card"), settings = settings,
                        onClick = { cardClicks++ },
                    ) {
                        BasicText("Music profile")
                        GlassActionGroup(settings, GlassArea.ActionButtons) {
                            GlassActionGroupItem({ childClicks++ }, Modifier.testTag("child"),
                                nativeIndication = !glass.value) {
                                Box(Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        listOf(true, false).forEachIndexed { index, enabled ->
            compose.runOnIdle { glass.value = enabled }
            val card = compose.onNodeWithTag("card")
            card.assertTextContains("Music profile")
            // One aggregated card action and one independently focused child; no empty skin stop.
            compose.onAllNodes(hasClickAction()).assertCountEquals(2)
            card.performClick()
            compose.onNodeWithTag("child").performTouchInput { click() }
            card.performTouchInput { click(Offset(200f, 20f)) }
            compose.runOnIdle {
                assertEquals((index + 1) * 2, cardClicks)
                assertEquals(index + 1, childClicks)
            }
        }
    }
}
