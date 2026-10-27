package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.LocalSettingsSearchMessage
import app.n_zik.android.components.tab.Search
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import java.time.Duration
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R

/** Behavioral wiring for the migrated Interface page, using semantic selectors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class UiSettingsWiringTest {
    @get:Rule val composeRule = createComposeRule()
    private val searchMessages = mutableListOf<String>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )
    private val cardIds = listOf("interface", "theme", "font", "swipe", "home-tabs", "burger", "toolbars", "sort-menus", "reset")

    @Before fun setUp() {
        searchMessages.clear()
        setActiveProfile("default", context)
        listOf("preferences", "app_settings").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        HomeTabsSettingsDialog.hideDialog()
    }

    @After fun tearDown() {
        HomeTabsSettingsDialog.hideDialog()
        // The real dismiss action terminates the process; reset only the singleton test state.
        RestartAppDialog.isActive = false
    }

    private fun show(focus: String = "") {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsSearchMessage provides { searchMessages += it }) {
                UiSettings(navController = mockk(relaxed = true), focus = focus)
            }
        }
        if (composeRule.mainClock.autoAdvance) composeRule.waitForIdle()
    }

    @Test fun selectedContentDoesNotNestThePageShell() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsSearchMessage provides { searchMessages += it }) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UiSettingsContent(
                        search = Search(initialVisible = true),
                        navController = mockk(relaxed = true),
                        selectedCards = setOf(R.string.home_tabs_settings),
                        cardModifier = { Modifier.testTag("ui.selected") },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ui.selected").assertExists()
        composeRule.onAllNodes(isEditable()).assertCountEquals(0)
        composeRule.onNodeWithTag("ui.card.home-tabs", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("ui.card.theme", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("ui.card.reset", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `unfiltered cards retain order and opening writes no preferences`() {
        show()
        val cards = composeRule.onAllNodes(SemanticsMatcher("Interface card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("ui.card.") == true
        }, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(cardIds.map { "ui.card.$it" }, cards.map { it.config[SemanticsProperties.TestTag] })
        assertTrue("Cards must retain their vertical display order", cards.zipWithNext().all { (first, second) ->
            first.positionInRoot.y < second.positionInRoot.y
        })
        assertTrue(context.getSharedPreferences("preferences", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test fun `history records only explicit submission on the Interface page`() {
        show()
        val query = context.getString(R.string.default_page)
        val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        composeRule.onNode(isEditable()).performTextInput(query)
        composeRule.waitForIdle()
        assertTrue(SearchHistoryStore.all(preferences, SearchHistoryStore.PAGE_SETTINGS_UI).isEmpty())
        composeRule.onNode(isEditable()).performImeAction()
        composeRule.waitForIdle()
        assertEquals(listOf(query), SearchHistoryStore.all(preferences, SearchHistoryStore.PAGE_SETTINGS_UI))
        assertTrue(SearchHistoryStore.all(preferences, SearchHistoryStore.PAGE_SETTINGS_GENERAL).isEmpty())
    }

    @Test fun `inner search term displays its actual control`() {
        show()
        composeRule.onNode(isEditable()).performTextInput(context.getString(R.string.default_page))
        composeRule.waitForIdle()
        composeRule.onNode(hasText(context.getString(R.string.default_page)) and !isEditable()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ui.card.interface", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("ui.card.theme", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `unknown query shows empty state and clearing restores cards`() {
        show()
        composeRule.onNode(isEditable()).performTextInput("zzzz-no-such-setting")
        composeRule.waitForIdle()
        repeat(4) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        }
        composeRule.onNodeWithTag("search_no_results_text").assertDoesNotExist()
        assertTrue(searchMessages.contains(context.getString(R.string.no_results_found)))
        composeRule.onNodeWithTag("ui.card.theme", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(isEditable()).performTextReplacement("")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ui.card.theme", useUnmergedTree = true).assertExists()
    }

    private fun settleDialogFrames() {
        repeat(30) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    @Test fun `singleton dialog remains single outside filtered cards and dismisses`() {
        composeRule.mainClock.autoAdvance = false
        show()
        settleDialogFrames()
        composeRule.onNode(isEditable()).performTextInput("zzzz-no-such-setting")
        settleDialogFrames()
        composeRule.runOnIdle { HomeTabsSettingsDialog.showDialog() }
        settleDialogFrames()
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.runOnIdle { HomeTabsSettingsDialog.showDialog() }
        settleDialogFrames()
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        settleDialogFrames()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test fun `burger deep link makes customization control visible`() {
        composeRule.mainClock.autoAdvance = false
        show("burger")
        val deadline = System.currentTimeMillis() + 30_000
        var visible = false
        while (System.currentTimeMillis() < deadline && !visible) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            visible = runCatching {
                composeRule.onNodeWithText(context.getString(R.string.burger_menu_customize)).assertIsDisplayed()
            }.isSuccess
        }
        assertTrue("Burger customization control must be visible after the measured focus scroll", visible)
    }

    @Test fun `selecting interface mode requests the existing restart dialog`() {
        composeRule.mainClock.autoAdvance = false
        show()
        settleDialogFrames()
        composeRule.onNode(isEditable()).performTextInput(context.getString(R.string.interface_in_use))
        settleDialogFrames()
        composeRule.onNode(isEditable()).performImeAction()
        settleDialogFrames()
        composeRule.onNode(isEditable()).assertIsNotFocused()
        composeRule.onNode(hasText(context.getString(R.string.interface_in_use)) and !isEditable()).performScrollTo().performClick()
        settleDialogFrames()
        composeRule.onNodeWithText("ViMusic").performClick()
        settleDialogFrames()
        assertTrue(RestartAppDialog.isActive)
        assertEquals("ViMusic", context.getSharedPreferences("preferences", Context.MODE_PRIVATE).getString("UiType", null))
    }

    @Test fun `reset action is not repeated by recomposition`() {
        show()
        composeRule.onNodeWithText(context.getString(R.string.settings_restore_default_settings)).performScrollTo().performClick()
        composeRule.waitForIdle()
        val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        composeRule.runOnIdle { preferences.edit().putBoolean(hideStatusBarKey, true).commit() }
        composeRule.onNode(isEditable()).performTextReplacement(context.getString(R.string.settings_use_font_type))
        composeRule.waitForIdle()
        assertTrue(preferences.getBoolean(hideStatusBarKey, false))
    }
    @Test fun statusBarControlBelongsToInterfaceAndKeepsItsPreference() {
        val selected = androidx.compose.runtime.mutableStateOf(setOf(R.string.settings_use_font_type))
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsSearchMessage provides { searchMessages += it }) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UiSettingsContent(
                        search = Search(initialVisible = false),
                        navController = mockk(relaxed = true),
                        selectedCards = selected.value,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.hide_status_bar)).assertDoesNotExist()
        composeRule.runOnIdle { selected.value = setOf(R.string.user_interface) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.hide_status_bar))
            .performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .getBoolean(hideStatusBarKey, false))
    }

}
