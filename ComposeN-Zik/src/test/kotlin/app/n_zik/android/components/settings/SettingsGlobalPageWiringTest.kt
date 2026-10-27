package app.n_zik.android.components.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.components.settings.common.LocalSettingsSearchMessage
import app.n_zik.android.components.search.SearchHistoryStore
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.components.settings.common.OrganizedSettingsCategories
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.components.settings.common.parentCard
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

/** The thematic hub keeps update gating, search callbacks and navigation state. */
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

    private val messages = mutableListOf<String>()
    private var selectedItem: SettingsPageItem? = null
    private var selectedCard: Pair<Int, String>? = null

    @Before
    fun setUp() {
        setActiveProfile("default", context)
        messages.clear()
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
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsSearchMessage provides { messages += it }) {
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
        composeRule.onNodeWithTag("settings_global_category_system").fetchSemanticsNode()
    }.isSuccess

    @Test fun savedSearchesAppearOnlyWhileFocusedAndFilterWhileTyping() {
        SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_GLOBAL, "Audio controls")
        SearchHistoryStore.record(context.preferences, SearchHistoryStore.PAGE_SETTINGS_GLOBAL, "Network controls")
        showGlobalPage()
        composeRule.onNodeWithText("Audio controls").assertDoesNotExist()
        composeRule.onNodeWithText("Network controls").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_global_search").performClick()
        settle()
        composeRule.onNodeWithText("Audio controls").assertExists()
        composeRule.onNodeWithText("Network controls").assertExists()
        composeRule.onNodeWithTag("settings_global_search").performTextInput("Audio")
        settle()
        composeRule.onNodeWithText("Audio controls").assertExists()
        composeRule.onNodeWithText("Network controls").assertDoesNotExist()
        composeRule.onNodeWithText("Audio controls").performClick()
        settle()
        composeRule.onAllNodesWithText("Audio controls").assertCountEquals(1)
        composeRule.onNodeWithTag("settings_global_search").assertTextEquals("Audio controls")
    }

    @Test fun globalPageReservesTheSharedBottomNavigationSpace() {
        var expected: Dp = 0.dp
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsSearchMessage provides { messages += it }) {
                expected = Dimensions.bottomSpacer
                SettingsGlobalPage(onItemSelected = {}, onCardSelected = { _, _ -> })
            }
        }
        pumpUntil("global page", 30_000) { pageIsComposed() }
        scrollToGlobalNode("settings_global_bottom_space")
        composeRule.onNodeWithTag("settings_global_bottom_space").assertHeightIsEqualTo(expected)
    }

    @Test
    fun thePageListsAboutThenUpdateAndEveryThematicDestination() {
        showGlobalPage()
        OrganizedSettingsCategories.forEach { category ->
            composeRule.onNodeWithTag("settings_global_category_${category.id}").assertExists()
        }
        val about = composeRule.onNodeWithTag("settings_global_category_about").fetchSemanticsNode().positionInRoot.y
        val update = composeRule.onNodeWithTag("settings_global_update").fetchSemanticsNode().positionInRoot.y
        val playback = composeRule.onNodeWithTag("settings_global_category_playback").fetchSemanticsNode().positionInRoot.y
        assertTrue(about < update)
        assertTrue(update < playback)
    }

    @Test
    fun theItemsRenderInsideTheirThematicTitlelessGroups() {
        showGlobalPage()
        (1..7).forEach { group -> composeRule.onNodeWithTag("settings_global_group_$group").assertExists() }
        OrganizedSettingsCategories.forEach { category ->
            composeRule.onNodeWithTag("settings_global_category_${category.id}")
                .assert(hasAnyAncestor(hasTestTag("settings_global_group_${category.group}")))
        }
        composeRule.onNodeWithTag("settings_global_update")
            .assert(hasAnyAncestor(hasTestTag("settings_global_group_1")))
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
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsSearchMessage provides { messages += it }) {
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
    fun tappingACategoryItemReportsItsStableCategory() {
        showGlobalPage()

        // Exercise the route callback directly; this is not a scrolling test.
        composeRule.onNodeWithTag("settings_global_category_youtube").performSemanticsAction(SemanticsActions.OnClick) { it() }
        settle()

        val item = selectedItem
        assertEquals("youtube", item?.categoryId)
        assertEquals(null, item?.tab)
        assertEquals(null, item?.route)
    }

    @Test
    fun theSearchShowsTheExactSettingAndHidesTheItemList() {
        showGlobalPage()

        composeRule
            .onNodeWithTag("settings_global_search")
            .performTextInput(context.getString(R.string.skip_silence))

        val result = composeRule.onNodeWithTag("settings_global_entry_general.audio_effects.skip_silence")
        pumpUntil("the exact setting result is composed", 30_000) {
            runCatching { result.fetchSemanticsNode() }.isSuccess
        }
        assertTrue(
            "the default item list must disappear with a non-blank query",
            composeRule.onAllNodesWithTag("settings_global_category_playback").fetchSemanticsNodes().isEmpty()
        )
        // "Skip silence" matches none of the Update item's keywords, so it has
        // no item result either.
        assertTrue(
            "the Update item must not match a non-update query",
            composeRule.onAllNodesWithTag("settings_global_result_update").fetchSemanticsNodes().isEmpty()
        )

        result.performClick()
        settle()

        assertEquals("legacy callback fallback must retain the source tab", 0 to "", selectedCard)
    }

    @Test
    fun theSearchIndexesTheUpdateItem() {
        showGlobalPage()

        composeRule.onNodeWithTag("settings_global_search").performTextInput("update")
        settle()
        scrollToGlobalNode("settings_global_result_update")

        val result = composeRule.onNodeWithTag("settings_global_result_update")
        pumpUntil("the Update item result is composed", 30_000) {
            runCatching { result.fetchSemanticsNode() }.isSuccess
        }
        assertTrue(
            "the default item list must disappear with a non-blank query",
            composeRule.onAllNodesWithTag("settings_global_category_youtube").fetchSemanticsNodes().isEmpty()
        )

        result.performClick()
        settle()

        assertEquals(NavRoutes.updater.name, selectedItem?.route)
    }

    @Test
    fun returningFromADestinationRestoresTheGlobalQuery() {
        lateinit var navigation: NavHostController
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsSearchMessage provides { messages += it }) {
                navigation = rememberNavController()
                NavHost(navigation, startDestination = "global") {
                    composable("global") {
                        SettingsGlobalPage(
                            onItemSelected = { navigation.navigate("destination") },
                            onCardSelected = { _, _ -> navigation.navigate("destination") },
                        )
                    }
                    composable("destination") { Text("Destination") }
                }
            }
        }
        pumpUntil("global navigation entry", 30_000) { pageIsComposed() }
        val query = context.getString(R.string.audio_effects)
        composeRule.onNodeWithTag("settings_global_search").performTextInput(query)
        settle()
        scrollToGlobalNode("settings_global_result_0_${R.string.audio_effects}")
        val result = composeRule.onNodeWithTag("settings_global_result_0_${R.string.audio_effects}")
        pumpUntil("audio result", 30_000) { runCatching { result.fetchSemanticsNode() }.isSuccess }
        result.performSemanticsAction(SemanticsActions.OnClick) { it() }
        pumpUntil("destination", 30_000) {
            runCatching { composeRule.onNodeWithText("Destination").fetchSemanticsNode() }.isSuccess
        }
        composeRule.runOnIdle { navigation.popBackStack() }
        pumpUntil("restored result", 30_000) { runCatching { result.fetchSemanticsNode() }.isSuccess }
        scrollToGlobalNode("settings_global_search")
        composeRule.onNodeWithTag("settings_global_search").assertTextEquals(query)
        composeRule.onNodeWithTag("settings_global_category_playback").assertDoesNotExist()
    }

    private fun scrollToGlobalNode(tag: String) {
        val key = when {
            tag == "settings_global_search" -> "search_controls"
            tag == "settings_global_bottom_space" -> "bottom_space"
            tag == "settings_global_result_update" -> "item_${R.string.update}"
            tag.startsWith("settings_global_entry_") -> "entry_${tag.removePrefix("settings_global_entry_")}"
            tag.startsWith("settings_global_result_") -> "card_${tag.removePrefix("settings_global_result_")}"
            else -> error("Unknown global item tag: $tag")
        }
        composeRule.onNodeWithTag("settings_global_list").performScrollToKey(key)
        settle()
    }

    @Test
    fun broadSearchComposesOnlyVisibleResultsAndCanReachTheLastEntry() {
        val results = searchSettingsEntries(context, "e")
        assertTrue("query must exercise a large result list", results.size > 30)
        val lastTag = "settings_global_entry_${results.last().id}"
        showGlobalPage()
        composeRule.onNodeWithTag("settings_global_search").performTextInput("e")
        settle()
        composeRule.onNodeWithTag("settings_global_entry_${results.first().id}").assertExists()
        composeRule.onNodeWithTag(lastTag).assertDoesNotExist()
        scrollToGlobalNode(lastTag)
        composeRule.onNodeWithTag(lastTag).assertExists().performClick()
        settle()
        assertEquals(results.last().tab to results.last().parentCard().focusId, selectedCard)
    }

    @Test
    fun anUnmatchedQueryShowsTheNoResultsMessage() {
        showGlobalPage()

        composeRule.onNodeWithTag("settings_global_search").performTextInput("zzz_no_match")

        pumpUntil("the no-results toast is emitted", 30_000) {
            messages.contains(context.getString(R.string.no_results_found))
        }
        composeRule.onNodeWithText(context.getString(R.string.no_results_found)).assertDoesNotExist()
        assertTrue(
            "the default item list must disappear with a non-blank query",
            composeRule.onAllNodesWithTag("settings_global_category_playback").fetchSemanticsNodes().isEmpty()
        )
    }
}
