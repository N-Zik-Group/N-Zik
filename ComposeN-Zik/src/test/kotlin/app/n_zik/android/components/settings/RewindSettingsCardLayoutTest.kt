package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.utils.DataStoreUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the rewind card overlap (user report, spec-customizable-burger-menu
 * manual testing): when the three cards of [RewindSettingsCard] leaked out as direct
 * children of the host's measurement Box, the Box stacked all three on top of each other
 * and the lower cards bled through the upper one. The cards are now wrapped in their own
 * Column, so the first card's entries must be clickable at their own position: a click on
 * the yearly recap entry must toggle ITS preference, not a covered row of another card.
 *
 * Harness: the real card under Robolectric, appearance provided by the shared
 * [LocalAppearance] (same template as [AccountsSettingsEmbeddingWiringTest]); all
 * preferences read their defaults (feature on, every card visible).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RewindSettingsCardLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @After
    fun resetPreferences() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)
    }

    @Test
    fun `clicking the yearly recap entry toggles its own preference`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                RewindSettingsCard()
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.rw_settings_yearly))
            .performClick()
        composeRule.waitForIdle()

        assertFalse(
            "the yearly recap entry must toggle its own preference; if another card covered " +
                "it, the click lands on the wrong row instead",
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true)
        )
    }

    @Test
    fun `the open row is absent without the callback and fires it when provided`() {
        // The rule's setContent is single-shot (Compose 1.12), so the callback is an
        // observed state the test flips after the first assertions.
        val callback = mutableStateOf<(() -> Unit)?>(null)
        var opens = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                RewindSettingsCard(onOpenRewind = callback.value)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.module_open)).assertDoesNotExist()

        callback.value = { opens++ }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.rewind_open_desc)).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.module_open))
            .performClick()
        composeRule.waitForIdle()

        assertEquals("a click on the open row must fire the navigation callback", 1, opens)
    }

    @Test
    fun `the whole rewind section is hidden while the master is off`() {
        // The state the master toggle leaves behind when flipped off: master off AND the
        // two playlist creation gates auto-unchecked.
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, false)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                RewindSettingsCard()
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.rw_settings_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.rw_settings_notifications)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.rw_settings_playlists_title)).assertDoesNotExist()
    }
}
