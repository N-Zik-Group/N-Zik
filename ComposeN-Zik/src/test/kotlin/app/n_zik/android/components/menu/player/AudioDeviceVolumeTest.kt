package app.n_zik.android.components.menu.player

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w400dp-h800dp")
class AudioDeviceVolumeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `grid volume applies during drag and stays closed when re-enabled`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val volume = mutableFloatStateOf(5f)
        val enabled = mutableStateOf(true)
        var completions = 0
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)

        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GridMenu.Menu {
                    item {
                        VolumeGridEntry(
                            currentVolume = volume.floatValue,
                            maxVolume = 10f,
                            enabled = enabled.value,
                            onVolumeChange = { volume.floatValue = it },
                            onVolumeChangeComplete = { completions++ },
                        )
                    }
                }
            }
        }

        val tile = compose.onNodeWithText(context.getString(R.string.player_statistics_volume_label))
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        tile.performClick()
        slider.performTouchInput {
            down(center)
            moveTo(Offset(width * .65f, center.y))
            moveTo(Offset(width * .8f, center.y))
        }
        compose.runOnIdle {
            assertTrue(volume.floatValue > 5f)
            assertEquals(0, completions)
        }
        slider.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, completions) }

        compose.runOnIdle { enabled.value = false }
        slider.assertDoesNotExist()
        compose.runOnIdle { enabled.value = true }
        slider.assertDoesNotExist()
        tile.performClick()
        slider.assertExists()
    }
}
