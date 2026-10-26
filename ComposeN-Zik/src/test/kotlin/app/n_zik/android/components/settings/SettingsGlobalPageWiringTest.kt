package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.lastUpdateCheckKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsGlobalPage
import app.n_zik.android.components.settings.common.SettingsPageItem
import app.n_zik.android.components.settings.common.hasAutoUpdate
import app.n_zik.android.updater.models.GithubRelease
import app.n_zik.android.updater.services.Updater
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/**
 * Wiring tests for the global settings entry page (spec-settings-global-page,
 * rows ENTRY_DEFAULT, UPDATE_ITEM, UPDATE_ROW, SEARCH_GLOBAL): the empty query
 * renders the registry's two titleless group cards (card 1 = About + Update,
 * card 2 = the 8 categories); each row is a single line (no displayed
 * description); tapping an item reports the right target (route for Update,
 * tab index for categories); the Update row carries on the right the same
 * status + last-check info the About tab's removed update block showed (same
 * strings, same sources), and is greyed + non-clickable + carries the
 * explanation on the right on builds without auto-update; the search bar
 * filters to card results, with the Update item indexed too; an unmatched
 * query shows the no-results message.
 *
 * The page is composed on a FROZEN clock and pumped by hand: the Update row
 * trailing text (and the blocked explanation) is an infinite marquee, which
 * would keep the real clock from ever settling (same harness as
 * GeneralSettingsWiringTest.thePipFocusScrollsThePipCardIntoView). The debug
 * build ships with IS_AUTOUPDATE=false, so [hasAutoUpdate] is stubbed per test
 * (the blocked-state test overrides it back to false).
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine
 * on the JUnit 5 platform (`createComposeRule()` only works with JUnit 4),
 * with the plain [Application] so the app's heavy init is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SettingsGlobalPageWiringTest {

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

    private var selectedItem: SettingsPageItem? = null
    private var selectedCard: Pair<Int, String>? = null

    @Before
    fun setUp() {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns context
        Dependencies.init(mainApplication)
        mockkStatic("app.n_zik.android.components.settings.common.SettingsAutoUpdateKt")
        every { hasAutoUpdate() } returns true
        Updater.githubRelease = null
    }

    @After
    fun tearDown() {
        Updater.githubRelease = null
        unmockkAll()
    }

    /**
     * Drives the frozen Compose clock and the Robolectric main looper until
     * [condition] holds (or [timeoutMs] elapses). No `waitForIdle` in the loop:
     * on a frozen clock the infinite marquee keeps it from ever settling, so
     * advancing the clock IS the pump. [label] is reported on timeout.
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

    /** One pump step: settles click handlers and finite animations on the frozen clock. */
    private fun settle() {
        composeRule.mainClock.advanceTimeBy(500)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }

    /**
     * Composes the page on a frozen clock and pumps until [ready]. The default
     * marker is "both group cards are composed" (first + last row present).
     */
    private fun showGlobalPage(ready: () -> Boolean = { pageIsComposed() }) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SettingsGlobalPage(
                    onItemSelected = { selectedItem = it },
                    onCardSelected = { tab, focus -> selectedCard = tab to focus },
                )
            }
        }
        pumpUntil("the global page is composed", 30_000) { ready() }
        settle()
    }

    private fun pageIsComposed(): Boolean = runCatching {
        composeRule.onNodeWithTag("settings_global_update").fetchSemanticsNode()
        composeRule.onNodeWithTag("settings_global_category_7").fetchSemanticsNode()
    }.isSuccess

    @Test
    fun thePageListsAboutFirstThenUpdateThenTheBaseCategories() {
        showGlobalPage()

        // The 10 rows: About, Update, then the 8 base categories in tab order.
        composeRule.onNodeWithTag("settings_global_category_8").fetchSemanticsNode()
        composeRule.onNodeWithTag("settings_global_update").fetchSemanticsNode()
        (0..7).forEach { tab ->
            composeRule.onNodeWithTag("settings_global_category_$tab").fetchSemanticsNode()
        }

        // The top of the page is above the fold: no scroll needed for these.
        val aboutTop = composeRule
            .onNodeWithTag("settings_global_category_8")
            .fetchSemanticsNode()
            .positionInRoot.y
        val updateTop = composeRule
            .onNodeWithTag("settings_global_update")
            .fetchSemanticsNode()
            .positionInRoot.y
        val generalTop = composeRule
            .onNodeWithTag("settings_global_category_0")
            .fetchSemanticsNode()
            .positionInRoot.y

        assertTrue("About must be listed above Update", aboutTop < updateTop)
        assertTrue("Update must be listed above General", updateTop < generalTop)

        // The 8 category rows may reach below the fold: scroll to each pair's
        // lower row before comparing their tops (relative order is preserved).
        (0..6).forEach { tab ->

            val upper = composeRule
                .onNodeWithTag("settings_global_category_$tab")
                .fetchSemanticsNode()
                .positionInRoot.y
            val lower = composeRule
                .onNodeWithTag("settings_global_category_${tab + 1}")
                .fetchSemanticsNode()
                .positionInRoot.y
            assertTrue("category $tab must be listed above category ${tab + 1}", upper < lower)
        }
    }

    @Test
    fun theItemsRenderInsideTheirTitlelessGroupCards() {
        showGlobalPage()

        val appCard = composeRule.onNodeWithTag("settings_global_group_1").fetchSemanticsNode()
        val categoriesCard = composeRule.onNodeWithTag("settings_global_group_2").fetchSemanticsNode()
        assertTrue(
            "the About + Update card must be above the categories card",
            appCard.positionInRoot.y < categoriesCard.positionInRoot.y
        )

        val aboutTop = composeRule
            .onNodeWithTag("settings_global_category_8")
            .fetchSemanticsNode()
            .positionInRoot.y
        val updateTop = composeRule
            .onNodeWithTag("settings_global_update")
            .fetchSemanticsNode()
            .positionInRoot.y
        val generalTop = composeRule
            .onNodeWithTag("settings_global_category_0")
            .fetchSemanticsNode()
            .positionInRoot.y

        assertTrue(
            "About and Update must both be inside the first card",
            aboutTop >= appCard.positionInRoot.y && updateTop <= appCard.boundsInRoot.bottom
        )
        assertTrue(
            "the categories must be inside the second card",
            generalTop >= categoriesCard.positionInRoot.y && generalTop <= categoriesCard.boundsInRoot.bottom
        )
    }

    @Test
    fun theRowsAreSingleLineWithoutADisplayedDescription() {
        showGlobalPage()

        // The registry descriptions stay as search keywords only: none of them
        // is rendered on a page row.
        val generalDescription = context.getString(R.string.general_settings_description)
        val updateDescription = context.getString(R.string.settings_global_update_description)
        assertTrue(
            "no row may render the General description text",
            composeRule
                .onAllNodesWithText(generalDescription, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            "no row may render the Update description text",
            composeRule
                .onAllNodesWithText(updateDescription, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun theUpdateRowShowsTheUnknownStatusAndNeverCheckedWhenNothingIsKnown() {
        showGlobalPage()

        composeRule.onNodeWithTag("settings_global_update").assertIsEnabled()
        composeRule
            .onNodeWithTag("settings_global_update_status", useUnmergedTree = true)
            .assertTextEquals(
                "${context.getString(R.string.audio_quality_format_unknown)} \u00B7 " +
                    context.getString(R.string.never_checked)
            )
    }

    @Test
    fun theUpdateRowShowsUpToDateAndTheLastCheckDateWhenAStoredCheckIsNotNewer() {
        val lastCheck = 1_700_000_000_000L
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .edit()
            .putLong(lastUpdateCheckKey, lastCheck)
            .apply()
        Updater.githubRelease = GithubRelease(1u, tagName = "v1.0.0", name = "", body = "", builds = emptyList())

        showGlobalPage()

        // Same formatting the About tab's removed update block used.
        val expected = "${context.getString(R.string.up_to_date)} \u00B7 " +
            context.getString(
                R.string.last_check,
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(lastCheck))
            )
        composeRule
            .onNodeWithTag("settings_global_update_status", useUnmergedTree = true)
            .assertTextEquals(expected)
    }

    @Test
    fun theUpdateRowShowsUpdateAvailableWhenAStoredCheckIsNewer() {
        val lastCheck = 1_700_000_000_000L
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .edit()
            .putLong(lastUpdateCheckKey, lastCheck)
            .apply()
        Updater.githubRelease = GithubRelease(2u, tagName = "v9.0.0", name = "", body = "", builds = emptyList())

        showGlobalPage()

        val expected = "${context.getString(R.string.update_available)} \u00B7 " +
            context.getString(
                R.string.last_check,
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(lastCheck))
            )
        composeRule
            .onNodeWithTag("settings_global_update_status", useUnmergedTree = true)
            .assertTextEquals(expected)
    }

    @Test
    fun theUpdateRowIsGreyedAndBlockedWhenTheBuildHasNoAutoUpdate() {
        every { hasAutoUpdate() } returns false

        // The blocked explanation is an infinite marquee: compose on a frozen
        // clock and pump until it is composed (see the class KDoc).
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SettingsGlobalPage(
                    onItemSelected = { selectedItem = it },
                    onCardSelected = { tab, focus -> selectedCard = tab to focus },
                )
            }
        }

        val blockedMessage = composeRule.onNodeWithTag("settings_global_update_blocked_message", useUnmergedTree = true)
        pumpUntil("the blocked explanation is composed", 30_000) {
            runCatching { blockedMessage.fetchSemanticsNode().boundsInRoot.height > 0f }.getOrDefault(false)
        }

        // Matcher form: the 1.12.1 top-level assertIsNotEnabled import does not
        // resolve in this toolchain, isNotEnabled() + assert() do (same semantics).
        composeRule.onNodeWithTag("settings_global_update").assert(isNotEnabled())
        assertTrue(
            "the blocked explanation must be shown on the right of the row",
            blockedMessage.fetchSemanticsNode().boundsInRoot.height > 0f
        )
        assertTrue(
            "the status + last-check trailing must not be shown in the blocked state",
            composeRule
                .onAllNodesWithTag("settings_global_update_status", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun tappingTheUpdateItemReportsTheUpdaterRoute() {
        showGlobalPage()

        composeRule.onNodeWithTag("settings_global_update").performClick()
        settle()

        val item = selectedItem
        assertEquals(NavRoutes.updater.name, item?.route)
        assertEquals(null, item?.tab)
        assertEquals(R.string.update, item?.labelRes)
    }

    @Test
    fun tappingACategoryItemReportsItsTab() {
        showGlobalPage()

        // Exercise the route callback directly; this is not a scrolling test.
        composeRule.onNodeWithTag("settings_global_category_5").performSemanticsAction(SemanticsActions.OnClick) { it() }
        settle()

        val item = selectedItem
        assertEquals(5, item?.tab)
        assertEquals(null, item?.route)
    }

    @Test
    fun theSearchShowsTheMatchingCardAndHidesTheItemList() {
        showGlobalPage()

        composeRule
            .onNodeWithTag("settings_global_search")
            .performTextInput(context.getString(R.string.skip_silence))

        val result = composeRule.onNodeWithTag("settings_global_result_0_${R.string.audio_effects}")
        pumpUntil("the card result is composed", 30_000) {
            runCatching { result.fetchSemanticsNode() }.isSuccess
        }
        assertTrue(
            "the default item list must disappear with a non-blank query",
            composeRule.onAllNodesWithTag("settings_global_category_0").fetchSemanticsNodes().isEmpty()
        )
        // "Skip silence" matches none of the Update item's keywords, so it has
        // no item result either.
        assertTrue(
            "the Update item must not match a non-update query",
            composeRule.onAllNodesWithTag("settings_global_result_update").fetchSemanticsNodes().isEmpty()
        )

        result.performClick()
        settle()

        assertEquals("tapping a card result must report its tab and focus", 0 to "", selectedCard)
    }

    @Test
    fun theSearchIndexesTheUpdateItem() {
        showGlobalPage()

        composeRule.onNodeWithTag("settings_global_search").performTextInput("update")

        val result = composeRule.onNodeWithTag("settings_global_result_update")
        pumpUntil("the Update item result is composed", 30_000) {
            runCatching { result.fetchSemanticsNode() }.isSuccess
        }
        assertTrue(
            "the default item list must disappear with a non-blank query",
            composeRule.onAllNodesWithTag("settings_global_category_5").fetchSemanticsNodes().isEmpty()
        )

        result.performClick()
        settle()

        assertEquals(NavRoutes.updater.name, selectedItem?.route)
    }

    @Test
    fun anUnmatchedQueryShowsTheNoResultsMessage() {
        showGlobalPage()

        composeRule.onNodeWithTag("settings_global_search").performTextInput("zzz_no_match")

        val noResults = composeRule
            .onNodeWithText(context.getString(R.string.no_results_found))
        pumpUntil("the no-results message is composed", 30_000) {
            runCatching { noResults.fetchSemanticsNode() }.isSuccess
        }
        assertTrue(
            "the default item list must disappear with a non-blank query",
            composeRule.onAllNodesWithTag("settings_global_category_0").fetchSemanticsNodes().isEmpty()
        )
    }
}
