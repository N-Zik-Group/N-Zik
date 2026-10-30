package app.n_zik.android.components.settings

import androidx.compose.foundation.layout.WindowInsets

import app.n_zik.android.LocalPlayerAwareWindowInsets

import app.n_zik.android.components.settings.general.GeneralSettingsContent
import app.n_zik.android.components.tab.Search
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.settings.general.GeneralSettings
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Wiring tests for the n_zik General settings tab (spec-settings-global-page,
 * rows GENERAL_PARITY, GENERAL_SEARCH, ENTRY_DEEPLINK, RESTART_FLAGS, RESET,
 * SHORTCUTS_DIALOG): with an empty search the tab composes its 10 cards; the
 * tab-level search filters the cards through the registry; the `pip` focus
 * scrolls the PiP card into view; toggling a setting that needs a service
 * restart (or tapping the reset) shows the tab-level restart bar, and
 * restarting clears it.
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine
 * on the JUnit 5 platform (`createComposeRule()` only works with JUnit 4),
 * with the plain [Application] so the app's heavy init is skipped. The cards'
 * entry/exit animations are finite, so the clock self-advances and the tests
 * settle with `waitForIdle()`; only the focus-scroll test freezes the clock
 * and pumps it by hand (same harness as
 * [app.n_zik.android.legacyoffmain.ui.screens.settings.BurgerMenuFocusScrollTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class GeneralSettingsWiringTest {

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

    @Before
    fun setUp() {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns context
        Dependencies.init(mainApplication)
    }

    private fun showGeneralTab(focus: String = "") {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                GeneralSettings(focus = focus)
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Drives the frozen Compose clock and the Robolectric main looper until
     * [condition] holds (or [timeoutMs] elapses). No `waitForIdle` in the loop:
     * on a frozen clock a pending entry animation keeps it from ever settling,
     * so the loop would block forever - advancing the clock IS the pump.
     * [label] is reported on timeout.
     */
    private fun pumpUntil(label: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            if (condition()) return
        }
        assertTrue("[$label] not met within ${timeoutMs}ms", condition())
    }

    private val cardTitles: List<Int> = listOf(
        R.string.languages,
        R.string.playback,
        R.string.player_controls,
        R.string.queue_management,
        R.string.app_behavior,
        R.string.audio_effects,
        R.string.gestures_events,
        R.string.picture_in_picture,
        R.string.app_shortcuts_settings,
        R.string.settings_reset,
    )

    private fun cardTitleIsShown(titleRes: Int): Boolean =
        composeRule
            .onAllNodesWithText(context.getString(titleRes), substring = false, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    /**
     * Clicks the unique row carrying the given description. In the merged
     * tree the clickable row node merges its descendant texts (title +
     * description), and the description strings are unique per row, so the
     * merged node is the row itself. The row can be below the 640px viewport:
     * `performScrollTo` first, otherwise the touch lands outside the window
     * and the click never fires.
     */
    private fun clickRowByDescription(descriptionRes: Int) {
        composeRule
            .onNodeWithText(context.getString(descriptionRes))
            .performScrollTo()
            .performClick()
    }

    @Test fun selectedContentDoesNotNestThePageShell() {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GeneralSettingsContent(
                        search = Search(initialVisible = true),
                        selectedCards = setOf(R.string.playback),
                        cardModifier = { Modifier.testTag("general.selected") },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("general.selected").assertExists()
        composeRule.onAllNodes(isEditable()).assertCountEquals(0)
        assertTrue(cardTitleIsShown(R.string.playback))
        assertTrue(!cardTitleIsShown(R.string.languages))
        assertTrue(!cardTitleIsShown(R.string.settings_reset))
    }

    @Test
    fun theGeneralTabShowsAllTenCards() {
        showGeneralTab()

        composeRule
            .onNodeWithText(context.getString(R.string.tab_general), substring = false)
            .fetchSemanticsNode()
        composeRule
            .onNodeWithText(context.getString(R.string.general_settings_description), substring = false)
            .fetchSemanticsNode()

        cardTitles.forEach { titleRes ->
            assertTrue(
                "the card '${context.getString(titleRes)}' must be composed with an empty search",
                cardTitleIsShown(titleRes)
            )
        }
    }

    @Test
    fun theTabSearchFiltersTheCardsToTheMatchingOne() {
        showGeneralTab()

        composeRule
            .onNode(isEditable())
            .performTextInput(context.getString(R.string.skip_silence))
        composeRule.waitForIdle()

        assertTrue(
            "the card whose keywords match the query must stay",
            cardTitleIsShown(R.string.audio_effects)
        )
        assertTrue(
            "a non-matching card must be filtered out",
            !cardTitleIsShown(R.string.languages)
        )
        assertTrue(
            "a non-matching card must be filtered out",
            !cardTitleIsShown(R.string.playback)
        )
    }

    @Test
    fun thePipFocusScrollsThePipCardIntoView() {
        // The burger long press deep-links to settings?tab=0&focus=pip; the tab
        // must scroll until the PiP card is in view. The frozen clock drives the
        // scroll animation by hand (see the class KDoc).
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                GeneralSettings(focus = "pip")
            }
        }

        val pipTitles = composeRule.onAllNodesWithText(
            context.getString(R.string.picture_in_picture),
            substring = false,
            useUnmergedTree = true
        )
        pumpUntil("the focused PiP card is scrolled into the viewport", 30_000) {
            val nodes = runCatching { pipTitles.fetchSemanticsNodes() }.getOrDefault(emptyList())
            nodes.any { it.boundsInRoot.height > 0f && it.boundsInRoot.top in 0f..640f }
        }

        val nodes = pipTitles.fetchSemanticsNodes()
        assertTrue(
            "the focused PiP card must be inside the 640px viewport",
            nodes.any { it.boundsInRoot.top in 0f..640f }
        )
    }

    @Test
    fun togglingASettingShowsTheRestartBarAndRestartingClearsIt() {
        showGeneralTab()

        // The auto-load entry sets restartService when toggled.
        clickRowByDescription(R.string.player_auto_load_songs_in_queue_description)
        composeRule.waitForIdle()

        assertTrue(
            "a setting that needs a service restart must show the tab-level restart bar",
            composeRule
                .onAllNodesWithText(context.getString(R.string.restart_service))
                .fetchSemanticsNodes()
                .isNotEmpty()
        )

        composeRule
            .onAllNodesWithText(context.getString(R.string.restart_service))[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(
            "restarting must clear the restart bar",
            composeRule
                .onAllNodesWithText(context.getString(R.string.restart_service))
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun theResetEntryRestoresDefaultsAndShowsTheRestartBar() {
        showGeneralTab()

        clickRowByDescription(R.string.settings_restore_default_settings)
        composeRule.waitForIdle()

        assertTrue(
            "the reset must trigger the tab-level restart bar",
            composeRule
                .onAllNodesWithText(context.getString(R.string.restart_service))
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }
    @Test fun autoLoadRemainsFunctionalWhenOnlyQueueManagementIsComposed() {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GeneralSettingsContent(
                        search = Search(initialVisible = false),
                        selectedCards = setOf(R.string.queue_management),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(!cardTitleIsShown(R.string.player_controls))
        clickRowByDescription(R.string.player_auto_load_songs_in_queue_description)
        composeRule.waitForIdle()
        assertTrue(!context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .getBoolean(app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey, true))
        assertTrue(composeRule.onAllNodesWithText(context.getString(R.string.restart_service))
            .fetchSemanticsNodes().isNotEmpty())
    }

    private fun showSelectedCard(titleRes: Int) {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GeneralSettingsContent(
                        search = Search(initialVisible = false),
                        selectedCards = setOf(titleRes),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun playbackAutomationKeepsDefaultsAndWritesOriginalPreferences() {
        showSelectedCard(R.string.playback)
        val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        assertTrue(!cardTitleIsShown(R.string.app_behavior))
        assertTrue(!cardTitleIsShown(R.string.player_controls))
        clickRowByDescription(R.string.when_device_is_connected)
        clickRowByDescription(R.string.skip_media_on_error_description)
        clickRowByDescription(R.string.info_pauses_player_when_volume_zero)
        clickRowByDescription(R.string.pause_on_headphone_disconnect_description)
        composeRule.waitForIdle()
        assertTrue(preferences.getBoolean(app.it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey, false))
        assertTrue(preferences.getBoolean(app.it.fast4x.rimusic.utils.skipMediaOnErrorKey, false))
        assertTrue(preferences.getBoolean(app.it.fast4x.rimusic.utils.isPauseOnVolumeZeroEnabledKey, false))
        assertTrue(!preferences.getBoolean(app.it.fast4x.rimusic.utils.pauseOnHeadphoneDisconnectKey, true))
        composeRule.onAllNodesWithText(context.getString(R.string.restart_service)).assertCountEquals(1)
    }

    @Test
    fun persistentQueueKeepsResumeOnStartConditionalWithoutResettingItsPreference() {
        showSelectedCard(R.string.queue_management)
        val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val resumeDescription = context.getString(R.string.resume_automatically_when_app_opens)
        composeRule.onAllNodesWithText(resumeDescription).assertCountEquals(0)
        clickRowByDescription(R.string.save_and_restore_playing_songs)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(resumeDescription).assertCountEquals(1)
        clickRowByDescription(R.string.resume_automatically_when_app_opens)
        composeRule.waitForIdle()
        assertTrue(preferences.getBoolean(app.it.fast4x.rimusic.utils.persistentQueueKey, false))
        assertTrue(preferences.getBoolean(app.it.fast4x.rimusic.utils.resumePlaybackOnStartKey, false))
        composeRule.onAllNodesWithText(context.getString(R.string.restart_service)).assertCountEquals(1)
        clickRowByDescription(R.string.save_and_restore_playing_songs)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(resumeDescription).assertCountEquals(0)
        assertTrue(!preferences.getBoolean(app.it.fast4x.rimusic.utils.persistentQueueKey, true))
        assertTrue(preferences.getBoolean(app.it.fast4x.rimusic.utils.resumePlaybackOnStartKey, false))
    }

}
