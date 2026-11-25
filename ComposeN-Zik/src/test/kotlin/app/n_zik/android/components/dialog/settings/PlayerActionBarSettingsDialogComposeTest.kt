package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class PlayerActionBarSettingsDialogComposeTest {
    @get:Rule val compose = createComposeRule()

    @After
    fun hideDialog() {
        PlayerActionBarSettingsDialog.hideDialog()
    }

    @Test
    fun allThreeTopSlotsFitInThePhoneDialog() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        showDialog()

        listOf(
            R.string.settings_toolbar_player_left_slot,
            R.string.settings_toolbar_player_center_slot,
            R.string.settings_toolbar_player_right_slot,
        ).forEach { label -> compose.onNodeWithText(app.getString(label)).assertIsDisplayed() }
    }

    @Test
    fun tabFadeKeepsDialogTabsAndFooterInPlace() {
        showDialog()
        val tags = listOf(
            "dialogFrameCard",
            "settingsDialogTabRow",
            "toggleListDialogTabContent",
            "dialogFrameFooter",
        )
        val originalBounds = tags.map { compose.onNodeWithTag(it).getBoundsInRoot() }

        compose.onNodeWithTag("settingsDialogTab1").performSemanticsAction(SemanticsActions.OnClick) {
            assertTrue(it())
        }
        advanceTime(48)
        assertEquals(originalBounds, tags.map { compose.onNodeWithTag(it).getBoundsInRoot() })

        advanceTime(240)
        compose.onNodeWithTag("settingsDialogTab1").assertIsSelected()
        compose.onNodeWithTag("toggleListDialogItem.0").assertIsDisplayed()
        assertEquals(originalBounds, tags.map { compose.onNodeWithTag(it).getBoundsInRoot() })

        compose.onNodeWithTag("settingsDialogTab0").performSemanticsAction(SemanticsActions.OnClick) {
            assertTrue(it())
        }
        advanceTime(300)
        compose.onNodeWithTag("settingsDialogTab0").assertIsSelected()
        compose.onNodeWithTag("toggleListDialogItem.0").assertDoesNotExist()
        assertEquals(originalBounds, tags.map { compose.onNodeWithTag(it).getBoundsInRoot() })
    }

    private fun showDialog() {
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.mainClock.autoAdvance = false
        PlayerActionBarSettingsDialog.showDialog()
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                PlayerActionBarSettingsDialog.Render()
            }
        }
        advanceTime(300)
        compose.waitForIdle()
    }

    private fun advanceTime(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    }
}
