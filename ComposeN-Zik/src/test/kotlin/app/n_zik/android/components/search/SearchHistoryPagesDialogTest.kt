package app.n_zik.android.components.search

import android.app.Application
import android.os.Looper
import java.time.Duration
import org.robolectric.Shadows
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OrganizedSettingsCategories
import app.n_zik.android.components.settings.common.normalizedSettingsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SearchHistoryPagesDialogTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
    }

    @Test fun onlyActiveSearchPagesHaveTogglesAndBrowsingPreservesStoredHistory() {
        SearchHistoryStore.pages.forEach { SearchHistoryStore.record(context.preferences, it, "Saved query for $it") }
        val before = context.preferences.all.toMap()
        val open = mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                if (open.value) SearchHistoryPagesDialog(onDismiss = { open.value = false })
            }
        }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)

        val standaloneLabels = listOf(
            R.string.library_history_title, R.string.search_history_local, R.string.search_history_online,
            R.string.settings_global_title, R.string.glass_effect_title,
        )
        val expectedLabels = standaloneLabels.map { context.getString(it) } +
            OrganizedSettingsCategories.filterNot { it.id == "about" }.map { settingsPageLabel(it.labelRes) }
        expectedLabels.forEachIndexed { index, label ->
            composeRule.onNodeWithTag("toggleListDialogList").performScrollToIndex(index)
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText(context.getString(R.string.common_action_close))
            .assertIsDisplayed().performClick()
        awaitDialogClosed()
        assertFalse(open.value)
        assertEquals("Opening, scrolling and closing must preserve all history and choices", before, context.preferences.all)
    }

    @Test fun disablingDataHistoryOnlyAffectsTheVisibleCategory() {
        val page = "settings_category_data"
        val legacy = SearchHistoryStore.PAGE_SETTINGS_DATA
        SearchHistoryStore.record(context.preferences, page, "Saved category query")
        SearchHistoryStore.record(context.preferences, legacy, "Saved legacy query")
        val otherPage = "settings_category_services"
        context.preferences.edit().putBoolean(SearchHistoryStore.enabledKey(otherPage), false).commit()
        val open = mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                if (open.value) SearchHistoryPagesDialog(onDismiss = { open.value = false })
            }
        }
        composeRule.mainClock.advanceTimeBy(400)
        scrollToCategory("data")
        composeRule.onNodeWithText(settingsPageLabel(R.string.settings_data_title)).performClick()
        composeRule.runOnIdle {
            assertFalse(SearchHistoryStore.isEnabled(context.preferences, page))
            assertEquals(listOf("Saved category query"), SearchHistoryStore.all(context.preferences, page))
            assertTrue(SearchHistoryStore.isEnabled(context.preferences, legacy))
            assertEquals(listOf("Saved legacy query"), SearchHistoryStore.all(context.preferences, legacy))
            assertFalse(context.preferences.getBoolean(SearchHistoryStore.enabledKey(otherPage), true))
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText(context.getString(R.string.common_action_close))
            .assertIsDisplayed().performClick()
        awaitDialogClosed()
    }

    @Test fun `history choices update only the active profile`() {
        val page = "settings_category_privacy"
        val defaultPrefs = context.preferences
        defaultPrefs.edit().putBoolean(SearchHistoryStore.enabledKey(page), true).commit()
        setActiveProfile("history-pages-profile", context)
        val profilePrefs = context.preferences
        profilePrefs.edit().clear().commit()
        try {
            val open = mutableStateOf(true)
            composeRule.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    if (open.value) SearchHistoryPagesDialog(onDismiss = { open.value = false })
                }
            }
            scrollToCategory("privacy")
            composeRule.onNodeWithText(settingsPageLabel(R.string.settings_category_privacy)).performClick()
            composeRule.runOnIdle {
                assertFalse(profilePrefs.getBoolean(SearchHistoryStore.enabledKey(page), true))
                assertTrue(defaultPrefs.getBoolean(SearchHistoryStore.enabledKey(page), true))
            }
            composeRule.onNodeWithText(context.getString(R.string.common_action_close)).performClick()
            awaitDialogClosed()
        } finally {
            profilePrefs.edit().clear().commit()
            setActiveProfile("default", context)
        }
    }

    @Test fun inheritedChoiceCanBeOverriddenWhileGloballyPaused() {
        val page = SearchHistoryStore.categoryPage("network")
        val legacyKey = SearchHistoryStore.enabledKey(SearchHistoryStore.PAGE_SETTINGS_NETWORK)
        context.preferences.edit().putBoolean(legacyKey, false).putBoolean(pauseSearchHistoryKey, true).commit()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SearchHistoryPagesDialog(onDismiss = {})
            }
        }
        scrollToCategory("network")
        composeRule.onNodeWithText(settingsPageLabel(R.string.settings_network_title)).performClick()
        composeRule.runOnIdle {
            val prefs = context.preferences
            assertTrue(prefs.getBoolean(SearchHistoryStore.enabledKey(page), false))
            assertFalse(prefs.getBoolean(legacyKey, true))
            assertFalse(SearchHistoryStore.isEnabled(prefs, page))
            assertTrue(prefs.getBoolean(pauseSearchHistoryKey, false))
        }
    }

    @Test
    @Config(qualifiers = "el-w360dp-h640dp")
    fun `localized category title collisions show separate descriptions in history choices`() {
        val playback = OrganizedSettingsCategories.single { it.id == "playback" }
        val player = OrganizedSettingsCategories.single { it.id == "player" }
        val sameTitle = normalizedSettingsText(context.getString(playback.labelRes)) ==
            normalizedSettingsText(context.getString(player.labelRes))
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SearchHistoryPagesDialog(onDismiss = {})
            }
        }
        listOf(playback, player).forEach { category ->
            scrollToCategory(category.id)
            val description = composeRule.onNodeWithText(context.getString(category.descriptionRes), useUnmergedTree = true)
            if (sameTitle) {
                description.assertIsDisplayed()
            } else {
                composeRule.onNodeWithText(settingsPageLabel(category.labelRes)).assertIsDisplayed()
                description.assertDoesNotExist()
            }
        }
    }

    private fun awaitDialogClosed() {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeByFrame()
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            composeRule.waitForIdle()
            if (composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()) return
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

    private fun settingsPageLabel(labelRes: Int) =
        context.getString(R.string.search_history_settings_page, context.getString(labelRes))

    private fun scrollToCategory(id: String) {
        val categories = OrganizedSettingsCategories.filterNot { it.id == "about" }
        composeRule.onNodeWithTag("toggleListDialogList")
            .performScrollToIndex(5 + categories.indexOfFirst { it.id == id })
    }
}
