package app.n_zik.android.components.dialog.common

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.n_zik.android.R
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class InProgressDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `outside dismissal and the cancel button invoke different callbacks`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = Typography.fallback,
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        val actions = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                InProgressDialog(
                    total = 2,
                    done = 1,
                    text = "Matching songs",
                    onDismiss = { actions += "dismiss" },
                    onCancel = { actions += "cancel" },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dialogFrameViewport").performTouchInput {
            click(Offset(1f, center.y))
        }
        composeRule.runOnIdle { assertEquals(listOf("dismiss"), actions) }

        composeRule.onNodeWithText(application.getString(R.string.common_action_cancel)).performClick()
        composeRule.runOnIdle { assertEquals(listOf("dismiss", "cancel"), actions) }
    }
}
