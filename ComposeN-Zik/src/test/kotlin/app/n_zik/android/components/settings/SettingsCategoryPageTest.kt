package app.n_zik.android.components.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import app.n_zik.android.LocalPlayerAwareWindowInsets

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.isConnectionMeteredEnabledKey
import app.n_zik.android.utils.isSwipeToActionEnabledKey
import app.n_zik.android.utils.isShowingThumbnailInLockscreenKey
import app.n_zik.android.utils.loudnessBaseGainKey
import app.n_zik.android.utils.pauseOnHeadphoneDisconnectKey
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.volumeNormalizationKey
import app.n_zik.android.R
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.settings.common.settingsCardTargetId
import app.n_zik.android.components.settings.common.SettingsCategoryPage
import app.n_zik.android.components.settings.common.RewindMasterSettingsTarget
import app.n_zik.android.components.settings.common.RewindNotificationSectionTarget
import app.n_zik.android.components.settings.common.SettingsTargetHighlighted
import app.n_zik.android.components.settings.common.LocalSettingsSearchMessage
import app.n_zik.android.components.settings.common.SettingsGlobalPage
import app.n_zik.android.components.settings.common.SettingsScrollToTop
import app.n_zik.android.components.settings.common.SettingsCards
import app.n_zik.android.components.settings.common.cardsForCategory
import app.n_zik.android.components.settings.common.categoryId
import app.n_zik.android.components.settings.common.destinationRoute
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.components.settings.common.parentCard
import app.n_zik.android.components.settings.common.SettingsResetCard
import app.n_zik.android.components.settings.common.DOWNLOAD_RESET_SCOPE
import app.n_zik.android.components.settings.common.LISTEN_TOGETHER_RESET_SCOPE
import app.n_zik.android.components.settings.common.SettingsResetChooserTarget
import app.n_zik.android.components.settings.common.SettingsResetDownloadsTarget
import app.n_zik.android.components.settings.common.resetScopeForTarget
import app.n_zik.android.components.settings.common.AllSettingsEntryTargets
import app.n_zik.android.components.settings.common.locationDescription
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.settings.general.GeneralAudioEffectsCard
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.text.NumberFormat
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SettingsCategoryPageTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().putString("organization-test-sentinel", "unchanged").commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    private fun pumpUntil(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            if (condition()) return
        }
        assertTrue(label, condition())
    }

    @Test fun interfaceShortcutsUseTheNavigatorThatOwnsEachDestination() {
        val nav = mockk<NavController>(relaxed = true)
        val settingsRoutes = mutableListOf<String>()
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = "interface", navController = nav, onSearchResultNavigate = { settingsRoutes.add(it) })
            }
        }
        composeRule.onNodeWithTag("settings.category.list.interface").performScrollToKey("interface-shortcuts")
        composeRule.onNodeWithTag("settings_global_category_settingsThemeStore").performClick()
        verify(exactly = 1) { nav.navigate(NavRoutes.themeStore.name) }
        composeRule.runOnIdle { assertTrue(settingsRoutes.isEmpty()) }
        composeRule.onNodeWithTag("settings_global_category_settingsGlass").performClick()
        composeRule.runOnIdle { assertEquals(listOf("settingsGlass"), settingsRoutes) }
    }

    @Test fun aboutHasNoSearchInput() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = "about", navController = mockk(relaxed = true))
            }
        }
        pumpUntil("About category composed") {
            runCatching { composeRule.onNodeWithTag("settings.category.about").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun emptyFocusedSearchFadesCardsAndTypingShowsOnlyCompactNavigationRows() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = "behavior", navController = mockk(relaxed = true))
            }
        }
        val targetTag = "settings.target.card.0.app_behavior"
        val groupTitle = context.getString(R.string.settings_group_app_navigation_behavior)
        pumpUntil("category card composed") {
            runCatching { composeRule.onNodeWithTag(targetTag, useUnmergedTree = true).assertExists() }.isSuccess
        }
        composeRule.onNodeWithText(groupTitle).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.settings_global_search_hint), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performClick()
        pumpUntil("focused category card hidden after fade") {
            runCatching { composeRule.onNodeWithTag(targetTag, useUnmergedTree = true).assertIsNotDisplayed() }.isSuccess
        }
        composeRule.onNode(hasSetTextAction()).performTextInput(groupTitle)
        settleSearch()
        val resultTag = "settings.category.card.0.${R.string.settings_app_behavior_title}"
        composeRule.onNodeWithTag("settings.category.list.behavior").performScrollToKey("result:0:${R.string.settings_app_behavior_title}")
        pumpUntil("matching compact navigation row displayed") {
            runCatching { composeRule.onNodeWithTag(resultTag).assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag(targetTag, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("settings.category.list.behavior").performScrollToKey("header")
        composeRule.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.SetText) {
            it(androidx.compose.ui.text.AnnotatedString(""))
        }
        pumpUntil("clearing search restores the original category card") {
            runCatching { composeRule.onNodeWithTag(targetTag, useUnmergedTree = true).assertExists() }.isSuccess
        }
    }

    @Test fun sectionSearchShowsExactOptionLocationAndNavigatesToItsStableRow() {
        composeRule.mainClock.autoAdvance = false
        val navigation = mockk<NavController>(relaxed = true)
        val target = AllSettingsEntryTargets.single { it.id == "general.audio_effects.skip_silence" }
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = "audio", navController = navigation)
            }
        }
        pumpUntil("section search composed") {
            runCatching { composeRule.onNode(hasSetTextAction()).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNode(hasSetTextAction()).performTextInput(context.getString(R.string.settings_audio_skip_silence_description))
        settleSearch()
        val tag = "settings.category.entry.${target.id}"
        pumpUntil("description finds exact audio option") {
            runCatching { composeRule.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag(tag).assertTextContains(target.locationDescription(context))
        composeRule.onNodeWithTag("settings.category.entry.general.app_behavior.close_app_with_back_button").assertDoesNotExist()
        composeRule.onNodeWithTag(tag).performClick()
        verify {
            navigation.navigate("settingsCategory/audio?target=${Uri.encode(target.id)}&parent=${Uri.encode("card.0.audio_effects")}")
        }
        assertEquals("unchanged", context.preferences.getString("organization-test-sentinel", null))
    }

    @Test fun focusedBlankAndUnmatchedAndShortCategoryResultsCannotScroll() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = "privacy", navController = mockk(relaxed = true))
            }
        }
        pumpUntil("category search composed") {
            runCatching { composeRule.onNode(hasSetTextAction()).fetchSemanticsNode() }.isSuccess
        }
        val pageBounds = composeRule.onNodeWithTag("settings.category.privacy").fetchSemanticsNode().boundsInRoot
        val listBounds = composeRule.onNodeWithTag("settings.category.list.privacy").fetchSemanticsNode().boundsInRoot
        assertEquals("the list must draw behind bottom overlays without a fixed empty band", pageBounds.bottom, listBounds.bottom, 1f)
        val input = composeRule.onNode(hasSetTextAction())
        input.performClick()
        pumpUntil("blank focused content releases its scroll range") { categoryCannotScroll("privacy") }
        input.performTextInput("zzz_no_match")
        settleSearch()
        pumpUntil("unmatched content has no scroll range") { categoryCannotScroll("privacy") }
        input.performSemanticsAction(SemanticsActions.SetText) {
            it(androidx.compose.ui.text.AnnotatedString(context.getString(R.string.privacy_screen_protection)))
        }
        pumpUntil("short matching card fits the viewport") {
            runCatching {
                composeRule.onNodeWithTag("settings.category.card.9.${R.string.privacy_screen_protection}").assertIsDisplayed()
                categoryCannotScroll("privacy")
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings.category.list.privacy")
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1000f) }
        pumpUntil("short matching content cannot move") { categoryCannotScroll("privacy") }
    }

    @Test fun `audio short query renders exact global result geometry without full cards`() {
        val global = mutableStateOf(true)
        val target = searchSettingsEntries(context, "th").first { it.parentCard().categoryId() == "audio" }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                if (global.value) SettingsGlobalPage(onItemSelected = {}, onCardSelected = { _, _ -> })
                else SettingsCategoryPage(categoryId = "audio", navController = mockk(relaxed = true))
            }
        }
        pumpUntil("global search ready") { runCatching { composeRule.onNode(hasSetTextAction()).assertExists() }.isSuccess }
        composeRule.onNode(hasSetTextAction()).performTextInput("th")
        settleSearch()
        composeRule.onNodeWithTag("settings_global_list").performScrollToKey("entry_${target.id}")
        val globalBounds = composeRule.onNodeWithTag("settings_result_entry_${target.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        global.value = false
        pumpUntil("audio search ready") { runCatching { composeRule.onNodeWithTag("settings.category.audio").assertExists() }.isSuccess }
        composeRule.onNode(hasSetTextAction()).performTextInput("th")
        settleSearch()
        composeRule.onNodeWithTag("settings.category.list.audio").performScrollToKey("entry:${target.id}")
        val categoryBounds = composeRule.onNodeWithTag("settings.category.result.entry.${target.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(globalBounds.width, categoryBounds.width, 1f)
        assertEquals(globalBounds.height, categoryBounds.height, 1f)
        cardsForCategory("audio").forEach { card ->
            val name = context.resources.getResourceEntryName(card.titleRes)
            composeRule.onNodeWithTag("settings.target.card.${card.tab}.$name", useUnmergedTree = true).assertDoesNotExist()
        }
        composeRule.onNodeWithTag("settings.reset.card", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("settings.category.entry.${target.id}").assertTextContains(target.locationDescription(context))
    }

    @Test fun `theme title search is a compact link and navigates to the original card`() {
        val navigation = mockk<NavController>(relaxed = true)
        val theme = SettingsCards.single { it.tab == 1 && it.titleRes == R.string.common_color_source_theme }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = theme.categoryId(), navController = navigation)
            }
        }
        pumpUntil("theme search ready") { runCatching { composeRule.onNode(hasSetTextAction()).assertExists() }.isSuccess }
        composeRule.onNode(hasSetTextAction()).performTextInput(context.getString(R.string.common_color_source_theme))
        settleSearch()
        composeRule.onNodeWithTag("settings.category.list.${theme.categoryId()}").performScrollToKey("result:1:${R.string.common_color_source_theme}")
        composeRule.onNodeWithTag("settings.target.card.1.theme", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("settings.category.card.1.${R.string.common_color_source_theme}").performClick()
        verify { navigation.navigate("settingsCategory/${theme.categoryId()}?target=${Uri.encode("card.1.theme")}") }
    }

    @Test fun `category search floating arrow appears on upward scroll and returns to search controls`() {
        verifySearchScrollArrow(global = false)
    }

    @Test fun `global search floating arrow appears on upward scroll and returns to search controls`() {
        verifySearchScrollArrow(global = true)
    }

    private fun verifySearchScrollArrow(global: Boolean) {
        // This preference controls the separate action menu, not the return-to-top arrow.
        context.preferences.edit().putBoolean("showFloatingIcon", false).commit()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                if (global) SettingsGlobalPage(onItemSelected = {}, onCardSelected = { _, _ -> })
                else SettingsCategoryPage(categoryId = "audio", navController = mockk(relaxed = true))
            }
        }
        pumpUntil("search ready") { runCatching { composeRule.onNode(hasSetTextAction()).assertExists() }.isSuccess }
        composeRule.onNode(hasSetTextAction()).performTextInput("a")
        settleSearch()
        val listTag = if (global) "settings_global_list" else "settings.category.list.audio"
        val arrowTag = if (global) "settings.global.scrollToTop" else "settings.category.scrollToTop"
        val arrow = hasAnyAncestor(hasTestTag(arrowTag)) and hasClickAction()
        composeRule.onAllNodes(arrow).assertCountEquals(0)
        val results = searchSettingsEntries(context, "a").filter { global || it.parentCard().categoryId() == "audio" }
        assertTrue("enough results to leave the search header behind", results.size > 5)
        val distantResult = results[results.lastIndex - 2]
        val key = if (global) "entry_${distantResult.id}" else "entry:${distantResult.id}"
        composeRule.onNodeWithTag(listTag).performScrollToKey(key)
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag(listTag).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -150f) }
        pumpUntil("existing app arrow appears when scrolling up far from top") {
            runCatching { composeRule.onNode(arrow).assertIsDisplayed() }.isSuccess
        }
        composeRule.onNode(arrow).performClick()
        pumpUntil("arrow returns to search controls") {
            val range = composeRule.onNodeWithTag(listTag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            range.value() == 0f
        }
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        pumpUntil("arrow disappears at the top") { composeRule.onAllNodes(arrow).fetchSemanticsNodes().isEmpty() }
    }

    private fun settleSearch() {
        composeRule.mainClock.advanceTimeBy(500)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }

    @Test fun `blank global groups support scroll arrow while still inside one tall item`() {
        verifyBlankScrollArrow(global = true)
    }

    @Test fun `store grid presents a labeled return arrow inside one tall item`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalAppearance provides appearance,
            ) {
                val grid = rememberLazyGridState()
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().testTag("grid"), state = grid) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.height(2400.dp))
                        }
                    }
                    SettingsScrollToTop(grid, modifier = Modifier.testTag("arrow"))
                }
            }
        }
        val list = composeRule.onNodeWithTag("grid")
        pumpUntil("grid ready") { runCatching { list.assertIsDisplayed() }.isSuccess }
        val arrow = hasAnyAncestor(hasTestTag("arrow")) and hasClickAction() and
            hasContentDescription(context.getString(R.string.common_action_scroll_to_top))
        composeRule.onAllNodes(arrow).assertCountEquals(0)
        val viewportHeight = list.fetchSemanticsNode().size.height.toFloat()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, viewportHeight * 1.5f) }
        settleSearch()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -viewportHeight / 4) }
        val action = composeRule.onNode(arrow)
        pumpUntil("labeled return arrow appears after scrolling up") {
            runCatching { action.assertIsDisplayed() }.isSuccess
        }
        action.performClick()
        pumpUntil("return arrow scrolls the grid to the top") {
            list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() == 0f
        }
    }

    @Test fun `blank audio page supports scroll arrow while still inside one tall card`() {
        verifyBlankScrollArrow(global = false)
    }

    private fun verifyBlankScrollArrow(global: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                if (global) SettingsGlobalPage(onItemSelected = {}, onCardSelected = { _, _ -> })
                else SettingsCategoryPage(categoryId = "audio", navController = mockk(relaxed = true))
            }
        }
        val listTag = if (global) "settings_global_list" else "settings.category.list.audio"
        val arrowTag = if (global) "settings.global.scrollToTop" else "settings.category.scrollToTop"
        pumpUntil("blank page ready") { runCatching { composeRule.onNodeWithTag(listTag).assertExists() }.isSuccess }
        composeRule.onNodeWithTag(listTag).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1600f) }
        settleSearch()
        composeRule.onNodeWithTag(listTag).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -60f) }
        val arrow = hasAnyAncestor(hasTestTag(arrowTag)) and hasClickAction()
        pumpUntil("tall item exposes arrow on upward movement") { runCatching { composeRule.onNode(arrow).assertIsDisplayed() }.isSuccess }
        composeRule.onNode(arrow).performClick()
        pumpUntil("blank page returns to its header") {
            composeRule.onNodeWithTag(listTag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() == 0f
        }
    }

    @Test fun `pending restart footer and mini player remain below the category arrow`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(bottom = 140.dp), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = "behavior", navController = mockk(relaxed = true))
            }
        }
        val toggle = "settings.target.general.app_behavior.close_background_player"
        pumpUntil("restart option ready") { runCatching { composeRule.onNodeWithTag(toggle).assertExists() }.isSuccess }
        composeRule.onNodeWithTag(toggle).performScrollTo().performClick()
        pumpUntil("restart is pending") { runCatching { composeRule.onNodeWithText(context.getString(R.string.settings_player_action_restart_service)).assertIsDisplayed() }.isSuccess }
        val list = composeRule.onNodeWithTag("settings.category.list.behavior")
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1600f) }
        settleSearch()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -60f) }
        val arrow = hasAnyAncestor(hasTestTag("settings.category.scrollToTop")) and hasClickAction()
        pumpUntil("arrow clears pending footer") {
            runCatching {
                composeRule.onNode(arrow).assertIsDisplayed()
                val arrowBounds = composeRule.onNode(arrow).fetchSemanticsNode().boundsInRoot
                val footerBounds = composeRule.onNodeWithTag("settings.category.footer", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                arrowBounds.bottom <= footerBounds.top
            }.getOrDefault(false)
        }
    }

    @Test fun `keyword only visualizer card remains a compact destination on both hosts`() {
        val global = mutableStateOf(true)
        val card = SettingsCards.single { R.string.visualizer_microphone_permission_message in it.keywordRes }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                if (global.value) SettingsGlobalPage(onItemSelected = {}, onCardSelected = { _, _ -> })
                else SettingsCategoryPage(categoryId = "player", navController = mockk(relaxed = true))
            }
        }
        pumpUntil("global keyword search ready") { runCatching { composeRule.onNode(hasSetTextAction()).assertExists() }.isSuccess }
        composeRule.onNode(hasSetTextAction()).performTextInput("mic permission")
        settleSearch()
        composeRule.onNodeWithTag("settings_global_result_${card.tab}_${card.titleRes}").assertIsDisplayed()
        global.value = false
        pumpUntil("page keyword search ready") { runCatching { composeRule.onNodeWithTag("settings.category.player").assertExists() }.isSuccess }
        composeRule.onNode(hasSetTextAction()).performTextInput("mic permission")
        settleSearch()
        composeRule.onNodeWithTag("settings.category.card.${card.tab}.${card.titleRes}").assertIsDisplayed()
        composeRule.onNodeWithTag("settings.target.${settingsCardTargetId(context, card.tab, card.titleRes)}", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun categoryCannotScroll(category: String): Boolean {
        val range = composeRule.onNodeWithTag("settings.category.list.$category")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        return range.value() == 0f && range.maxValue() == 0f
    }

    @Test fun disabledSwipeActionHighlightsTheMasterAndExplainsHowToEnableIt() {
        context.preferences.edit().putBoolean(isSwipeToActionEnabledKey, false).commit()
        val before = context.preferences.all.toMap()
        val target = "ui.swipe_to_action.queue_and_local_playlists_right_swipe"
        val master = "ui.swipe_to_action.swipe_to_action"
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalAppearance provides appearance,
                LocalSettingsSearchMessage provides { message -> messages.add(message) },
            ) {
                SettingsCategoryPage(categoryId = "behavior", navController = mockk(relaxed = true), target = target)
            }
        }
        pumpUntil("the disabled option must highlight its switch") {
            runCatching {
                composeRule.onNodeWithTag("settings.target.$master", useUnmergedTree = true)
                    .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, true))
            }.isSuccess
        }
        val prerequisite = "${context.getString(R.string.settings_swipe_actions_title)} › ${context.getString(R.string.common_state_on)}"
        assertEquals(listOf(context.getString(R.string.settings_target_prerequisite, prerequisite)), messages)
        assertEquals(before, context.preferences.all)
    }

    @Test fun artworkStatsSearchExplainsThePlayerTypePrerequisite() {
        val before = context.preferences.all.toMap()
        val target = "appearance.cover_and_thumbnail.statsfornerdsplayer"
        val playerType = "appearance.player_presets_and_layout.playertype"
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalAppearance provides appearance,
                LocalSettingsSearchMessage provides { message -> messages.add(message) },
            ) {
                SettingsCategoryPage(categoryId = "player", navController = mockk(relaxed = true), target = target)
            }
        }
        pumpUntil("the unavailable Artwork option must highlight Player type") {
            runCatching {
                composeRule.onNodeWithTag("settings.target.$playerType", useUnmergedTree = true)
                    .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, true))
            }.isSuccess
        }
        val prerequisite = "${context.getString(R.string.player_type)} › ${context.getString(R.string.player_controls_style_modern)}"
        assertEquals(listOf(context.getString(R.string.settings_target_prerequisite, prerequisite)), messages)
        assertEquals(before, context.preferences.all)
    }

    @Test fun rewindMonthlySearchExplainsTheDisabledMasterSwitch() {
        val globalPreferences = DataStoreUtils.prefs(context)
        globalPreferences.edit().putBoolean(DataStoreUtils.KEY_REWIND_ENABLED, false).commit()
        assertSearchPrerequisite(
            category = "rewind",
            target = "embedded.rewind.rw_settings_monthly",
            prerequisiteTarget = RewindMasterSettingsTarget,
            labelRes = R.string.rewind_settings_enabled,
            valueRes = R.string.common_state_on,
        )
        assertEquals(false, globalPreferences.getBoolean(DataStoreUtils.KEY_REWIND_ENABLED, true))
    }

    @Test fun rewindNotificationsSearchHighlightsTheSummaryPeriodsWhenAllSchedulesAreDisabled() {
        val globalPreferences = DataStoreUtils.prefs(context)
        globalPreferences.edit()
            .putBoolean(DataStoreUtils.KEY_REWIND_ENABLED, true)
            .putBoolean(DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, false)
            .putBoolean(DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, false)
            .putBoolean(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, false)
            .putBoolean(DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, false)
            .commit()
        val before = globalPreferences.all.toMap()
        val target = searchSettingsEntries(context, context.getString(R.string.rewind_settings_notifications))
            .first { it.id == RewindNotificationSectionTarget }
        assertSearchFeedback(
            category = "rewind",
            target = target.id,
            highlightedTarget = "embedded.rewind.section.summaries",
            expectedMessage = context.getString(R.string.settings_target_unavailable),
        )
        assertEquals(before, globalPreferences.all)
    }

    @Test fun rewindNotificationsExplainDeniedPermissionAndKeepTheSwitchesUsable() {
        Shadows.shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val globalPreferences = DataStoreUtils.prefs(context)
        globalPreferences.edit()
            .putBoolean(DataStoreUtils.KEY_REWIND_ENABLED, true)
            .putBoolean(DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true)
            .putBoolean(DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true)
            .putBoolean(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)
            .putBoolean(DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)
            .putBoolean(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED, true)
            .commit()
        val hint = context.getString(R.string.rewind_settings_notification_permission_hint)
        val notificationLabels = listOf(
            R.string.rewind_settings_monthly_notification,
            R.string.rewind_settings_yearly_notification,
            R.string.rewind_settings_playlists_monthly_notification,
            R.string.rewind_settings_playlists_yearly_notification,
        )
        assertEquals(notificationLabels.toSet(), searchSettingsEntries(context, hint).map { it.labelRes }.toSet())
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) { RewindSettingsCard() }
            }
        }
        notificationLabels.forEach { label ->
            composeRule.onNodeWithText(context.getString(label))
                .assertIsEnabled().assertTextContains(hint, substring = true)
        }
        val before = globalPreferences.all.toMap()
        composeRule.onNodeWithText(context.getString(R.string.rewind_settings_playlists_monthly_notification))
            .performScrollTo().performClick()
        assertEquals(before + (DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED to false), globalPreferences.all)
    }

    @Test fun lyricsOnArtworkSearchPointsToBackgroundWhenArtworkVisibilityIsHidden() {
        context.preferences.edit().putBoolean(showthumbnailKey, false).commit()
        assertSearchPrerequisite(
            category = "player",
            target = "appearance.txt_lyrics.show_lyrics_thumbnail",
            prerequisiteTarget = "appearance.colors_and_background.background_colors",
            labelRes = R.string.settings_player_background_colors_label,
            valueRes = R.string.player_background_artwork_blurred_label,
        )
        assertEquals(false, context.preferences.getBoolean(showthumbnailKey, true))
    }

    private fun assertSearchPrerequisite(
        category: String,
        target: String,
        prerequisiteTarget: String,
        labelRes: Int,
        valueRes: Int,
    ) {
        val requirement = "${context.getString(labelRes)} › ${context.getString(valueRes)}"
        assertSearchFeedback(
            category = category,
            target = target,
            highlightedTarget = prerequisiteTarget,
            expectedMessage = context.getString(R.string.settings_target_prerequisite, requirement),
        )
    }

    private fun assertSearchFeedback(
        category: String,
        target: String,
        highlightedTarget: String,
        expectedMessage: String,
    ) {
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalAppearance provides appearance,
                LocalSettingsSearchMessage provides { messages.add(it) },
            ) {
                SettingsCategoryPage(categoryId = category, navController = mockk(relaxed = true), target = target)
            }
        }
        pumpUntil("the required setting must be highlighted") {
            runCatching {
                composeRule.onNodeWithTag("settings.target.$highlightedTarget", useUnmergedTree = true)
                    .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, true))
            }.isSuccess
        }
        assertEquals(listOf(expectedMessage), messages)
    }

    @Test fun mixedSourceCategoryHasOneScrollContainerAndTargetsTheActualSetting() {
        val before = context.preferences.all.toMap()
        val target = "ui.swipe_to_action.queue_and_local_playlists_right_swipe"
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(
                    categoryId = "behavior",
                    navController = mockk(relaxed = true),
                    target = target,
                    parentTarget = "card.1.swipe_to_action",
                )
            }
        }
        pumpUntil("the far-down setting must be brought into view") {
            runCatching {
                composeRule.onNodeWithTag("settings.target.$target", useUnmergedTree = true).assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(1)
        listOf(0 to R.string.settings_app_behavior_title, 0 to R.string.settings_gestures_title, 1 to R.string.settings_swipe_actions_title).forEach { (tab, title) ->
            val card = settingsCardTargetId(context, tab, title)
            composeRule.onNodeWithTag("settings.category.list.behavior").performScrollToKey("$tab:$title")
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            composeRule.onNodeWithTag("settings.target.$card", useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithTag("settings.target.card.0.playback", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("settings.target.card.1.theme", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("search navigation must not write settings", before, context.preferences.all)
    }

    @Test fun `reset picker exposes scopes without composing an action until explicitly selected`() {
        val before = context.preferences.all.toMap()
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsResetCard((0..7).toList(), "") { scope ->
                    Text("Reset action", Modifier.testTag("reset.action.$scope"))
                }
            }
        }
        composeRule.onNodeWithTag("settings.reset.dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.reset.open").performClick()
        (0..7).forEach { scope ->
            composeRule.onNodeWithTag("settings.reset.scope.$scope").assertExists()
            composeRule.onNodeWithTag("reset.action.$scope").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("settings.reset.scope.7").performScrollTo().performClick()
        composeRule.onNodeWithTag("reset.action.7").assertIsDisplayed()
        composeRule.onNodeWithTag("reset.action.0").assertDoesNotExist()
        assertEquals(before, context.preferences.all)
    }

    @Test fun `system reset picker includes download and device-wide Listen Together scopes`() {
        showCategoryReset("system", SettingsResetChooserTarget)
        composeRule.onNodeWithTag("settings.reset.all.open").assertExists()
        composeRule.onNodeWithTag("settings.reset.scope.$DOWNLOAD_RESET_SCOPE").assertExists()
        composeRule.onNodeWithTag("settings.reset.scope.$LISTEN_TOGETHER_RESET_SCOPE").assertExists()
        composeRule.mainClock.autoAdvance = true
        try {
            composeRule.onNodeWithTag("settings.reset.scope.$DOWNLOAD_RESET_SCOPE").performScrollTo().performClick()
        } finally {
            composeRule.mainClock.autoAdvance = false
        }
        pumpUntil("the System download reset action is visible") {
            runCatching { composeRule.onNodeWithTag("settings.reset.downloads.action").assertIsDisplayed() }.isSuccess
        }
    }

    @Test fun `local System search opens its download reset scope`() {
        composeRule.mainClock.autoAdvance = false
        var destination: String? = null
        val query = context.getString(R.string.settings_reset_downloads)
        val globalResult = searchSettingsEntries(context, query).single { it.id == SettingsResetDownloadsTarget }
        assertEquals("settingsCategory/downloads", Uri.parse(globalResult.destinationRoute(context)).path)
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(
                    categoryId = "system",
                    navController = mockk(relaxed = true),
                    onSearchResultNavigate = { destination = it },
                )
            }
        }
        pumpUntil("System search composed") {
            runCatching { composeRule.onNode(hasSetTextAction()).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNode(hasSetTextAction())
            .performClick()
            .performTextInput(query)
        settleSearch()
        val row = composeRule.onNodeWithTag("settings.category.entry.$SettingsResetDownloadsTarget")
        pumpUntil("the System download reset search result is visible") {
            runCatching { row.assertIsDisplayed() }.isSuccess
        }
        val location = listOf(R.string.settings_title, R.string.settings_category_system, R.string.settings_reset)
            .joinToString(" › ") { context.getString(it) }
        row.assertTextContains(location)
        row.performClick()
        composeRule.runOnIdle {
            val route = Uri.parse(checkNotNull(destination))
            assertEquals("settingsCategory/system", route.path)
            assertEquals(SettingsResetDownloadsTarget, route.getQueryParameter("target"))
            assertEquals("card.6.settings_reset", route.getQueryParameter("parent"))
        }
    }

    @Test fun `confirming an unchanged gain keeps its hundredths`() {
        val gain = 5.24f
        context.preferences.edit().putBoolean(volumeNormalizationKey, true).putFloat(loudnessBaseGainKey, gain).commit()
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GeneralAudioEffectsCard(visible = true, onRestartServiceSet = {})
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.settings_loudness_base_gain)).performScrollTo().performClick()
        val expected = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).format(5.24)
        composeRule.onNode(hasSetTextAction()).assertTextEquals(expected)
        composeRule.onNodeWithText(context.getString(R.string.common_action_save)).performClick()
        composeRule.runOnIdle { assertEquals(gain, context.preferences.getFloat(loudnessBaseGainKey, 0f), 0f) }
    }

    @Test fun `downloads reset chooser cannot widen to other settings`() {
        showCategoryReset("downloads", SettingsResetDownloadsTarget)
        composeRule.onNodeWithTag("settings.reset.downloads.action").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_reset_scope_back)).performClick()
        pumpUntil("the download-only chooser is visible") {
            runCatching { composeRule.onNodeWithTag("settings.reset.scope.$DOWNLOAD_RESET_SCOPE").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("settings.reset.all.open").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.reset.scope.$LISTEN_TOGETHER_RESET_SCOPE").assertDoesNotExist()
    }

    private fun showCategoryReset(category: String, target: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = category, navController = mockk(relaxed = true), target = target)
            }
        }
        pumpUntil("the reset dialog is visible") {
            runCatching { composeRule.onNodeWithTag("settings.reset.dialog").assertIsDisplayed() }.isSuccess
        }
    }

    @Test fun `reset search targets open only their original scope without changing preferences`() {
        val before = context.preferences.all.toMap()
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsResetCard((0..7).toList(), "appearance.settings_reset.settings_reset") { scope ->
                    Text("Reset action", Modifier.testTag("reset.action.$scope"))
                }
            }
        }
        composeRule.onNodeWithTag("reset.action.2").assertIsDisplayed()
        composeRule.onNodeWithTag("settings.reset.scope.2").assertDoesNotExist()
        assertEquals(before, context.preferences.all)
        listOf("general", "ui", "appearance", "ai", "data", "accounts", "network", "other")
            .forEachIndexed { tab, name ->
                assertEquals(tab, resetScopeForTarget("$name.settings_reset.settings_reset"))
                assertEquals(tab, resetScopeForTarget("card.$tab.settings_reset"))
            }
        assertEquals(null, resetScopeForTarget("card.8.settings_reset"))
        assertEquals(null, resetScopeForTarget("general.playback.audio_quality"))
    }

    @Test fun `Glass reset scope restores cold defaults without changing master or other settings`() {
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        val store = GlassStore(context)
        val fresh = store.read()
        store.setProfile(GlassQuality.Quality)
        store.setFloat(glassFilmKey, .263f)
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        val before = context.preferences.all.toMap()
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsResetCard((0..7).toList(), "") { scope ->
                    Text("Other reset $scope")
                }
            }
        }
        composeRule.onNodeWithTag("settings.reset.open").performClick()
        composeRule.onNodeWithTag("settings.reset.scope.-1").performScrollTo().performClick()
        assertEquals(.263f, store.read().film)
        composeRule.onNodeWithTag("settings.reset.glass.action").performClick()
        assertEquals(fresh, store.read())
        assertEquals(before, context.preferences.all)
    }

    @Test fun `system reset search opens the existing action without applying its defaults`() {
        val before = context.preferences.all.toMap()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(
                    categoryId = "system",
                    navController = mockk(relaxed = true),
                    target = "ui.settings_reset.settings_reset",
                    parentTarget = "card.1.settings_reset",
                )
            }
        }
        pumpUntil("the original reset action must be visible inside its selected scope") {
            runCatching {
                composeRule.onNodeWithTag("settings.target.ui.settings_reset.settings_reset", useUnmergedTree = true)
                    .assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithTag("settings.reset.card", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("settings.target.card.0.settings_reset", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("opening a reset search result must never apply defaults", before, context.preferences.all)
    }

    @Test fun `General reset keeps one restart action after leaving its scope`() {
        assertRestartAfterReset("general", 0, pauseOnHeadphoneDisconnectKey, defaultValue = true)
    }

    @Test fun `Appearance reset keeps one restart action after leaving its scope`() {
        assertRestartAfterReset("appearance", 2, isShowingThumbnailInLockscreenKey, defaultValue = true)
    }

    @Test fun `Network reset keeps one restart action after leaving its scope`() {
        assertRestartAfterReset("network", 6, isConnectionMeteredEnabledKey, defaultValue = false)
    }

    private fun assertRestartAfterReset(scope: String, tab: Int, preference: String, defaultValue: Boolean) {
        context.preferences.edit().putBoolean(preference, !defaultValue).commit()
        composeRule.mainClock.autoAdvance = false
        val target = "$scope.settings_reset.settings_reset"
        val restartLabel = context.getString(R.string.settings_player_action_restart_service)
        val footerRestart = hasAnyAncestor(hasTestTag("settings.category.footer")) and hasText(restartLabel)
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(
                    categoryId = "system",
                    navController = mockk(relaxed = true),
                    target = target,
                    parentTarget = "card.$tab.settings_reset",
                )
            }
        }
        val action = composeRule.onNodeWithTag("settings.target.$target", useUnmergedTree = true)
        pumpUntil("reset action is visible") { runCatching { action.assertIsDisplayed() }.isSuccess }
        composeRule.onNodeWithText(restartLabel).assertDoesNotExist()
        action.performClick()
        assertEquals(defaultValue, context.preferences.getBoolean(preference, !defaultValue))
        composeRule.onNodeWithText(context.getString(R.string.settings_reset_scope_back)).performClick()
        pumpUntil("scope chooser is visible") {
            runCatching { composeRule.onNodeWithTag("settings.reset.all.open").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithText(context.getString(R.string.common_action_cancel)).performClick()
        pumpUntil("one restart action remains in the page footer") {
            runCatching {
                composeRule.onNodeWithTag("settings.reset.dialog").assertDoesNotExist()
                composeRule.onNode(footerRestart, useUnmergedTree = true).assertIsDisplayed()
                composeRule.onAllNodes(hasText(restartLabel), useUnmergedTree = true).assertCountEquals(1)
            }.isSuccess
        }
    }
}
