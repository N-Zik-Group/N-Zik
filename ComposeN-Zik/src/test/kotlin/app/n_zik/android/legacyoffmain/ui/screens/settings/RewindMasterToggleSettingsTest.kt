package app.n_zik.android.legacyoffmain.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.components.settings.ai.AIRecommendationSettings
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.utils.DataStoreUtils
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wiring test for the Rewind master switch through the real legacy
 * [AIRecommendationSettings] screen (spec-customizable-burger-menu manual testing): the
 * master is the total switch, so flipping it off must persist the master key AND the two
 * playlist creation gates off (and the symmetric on case re-enables all three). Also pins
 * the burger Statistics deep link's "Open" row: it navigates to the full Statistics
 * screen through the real screen.
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine on the JUnit 5
 * platform (`createComposeRule()` only works with JUnit 4), with the plain [Application] so
 * the app's heavy init is skipped (same harness as `RewindSettingsCardLayoutTest`). The
 * screen is a non-lazy verticalScroll Column, so the off-screen Statistics card composes
 * and `performScrollTo` / `performClick` reach it without a viewport pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RewindMasterToggleSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val navController = mockk<NavController>()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @After
    fun resetRewindKeys() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)
    }

    @OptIn(UnstableApi::class)
    private fun showScreen(focus: String = "") {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AIRecommendationSettings(navController = navController, focus = focus)
            }
        }
    }

    @Test
    fun theMasterSwitchOffDisablesAllThreeRewindKeys() {
        showScreen()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.rw_settings_master)).performClick()
        composeRule.waitForIdle()

        assertFalse(
            "the master off must persist the master key off",
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true)
        )
        assertFalse(
            "the master off must auto-uncheck the monthly playlist gate",
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)
        )
        assertFalse(
            "the master off must auto-uncheck the yearly playlist gate",
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)
        )
    }

    @Test
    fun theMasterSwitchOnReenablesAllThreeRewindKeys() {
        // The state the master leaves behind when flipped off: all three keys off.
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, false)
        showScreen()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.rw_settings_master)).performClick()
        composeRule.waitForIdle()

        assertTrue(
            "the master on must re-enable the master key",
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, false)
        )
        assertTrue(
            "the master on must re-check the monthly playlist gate",
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, false)
        )
        assertTrue(
            "the master on must re-check the yearly playlist gate",
            DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, false)
        )
    }

    @Test
    fun theStatisticsOpenRowNavigatesToTheStatisticsScreen() {
        every { navController.navigate(any<String>()) } just runs
        showScreen()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.statistics_open_desc))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        verify { navController.navigate(NavRoutes.statistics.name) }
    }
}
