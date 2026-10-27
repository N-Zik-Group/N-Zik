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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OrganizedSettingsCategories
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

    @Test fun allLegacyAndThematicPagesOpenAndCloseWithoutChangingStoredHistory() {
        val pages = SearchHistoryStore.pages
        assertEquals(29, pages.size)
        pages.forEach { SearchHistoryStore.record(context.preferences, it, "Saved query for $it") }
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

        val legacyLabels = listOf(
            R.string.history, R.string.search_history_local, R.string.search_history_online,
            R.string.settings_global_title, R.string.tab_general, R.string.ui_tab,
            R.string.player_appearance, R.string.ai_recommendations, R.string.tab_data,
            R.string.tab_accounts, R.string.tab_network, R.string.tab_miscellaneous,
        )
        val expectedLabels = legacyLabels.mapIndexed { index, label ->
            if (index < 4) context.getString(label) else settingsPageLabel(label)
        } + OrganizedSettingsCategories.map { settingsPageLabel(it.labelRes) }
        assertEquals(pages.size, expectedLabels.size)

        // Scrolling every row exercises lazy composition, including About, Privacy and Services.
        expectedLabels.forEachIndexed { index, label ->
            composeRule.onNodeWithTag("toggleListDialogList").performScrollToIndex(index)
            composeRule.onAllNodesWithText(label).onLast().assertIsDisplayed()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText(context.getString(R.string.search_history_close))
            .assertIsDisplayed().performClick()
        awaitDialogClosed()
        assertFalse(open.value)
        assertEquals("Opening, scrolling and closing must preserve all history and choices", before, context.preferences.all)
    }

    @Test fun disablingThematicPrivacyPreservesItsExistingHistoryAndOtherPageChoices() {
        val page = "settings_category_privacy"
        SearchHistoryStore.record(context.preferences, page, "Saved privacy query")
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
        composeRule.onNodeWithTag("toggleListDialogList")
            .performScrollToIndex(SearchHistoryStore.pages.indexOf(page))
        composeRule.onNodeWithText(settingsPageLabel(R.string.settings_category_privacy)).performClick()
        composeRule.runOnIdle {
            assertFalse(SearchHistoryStore.isEnabled(context.preferences, page))
            assertEquals(listOf("Saved privacy query"), SearchHistoryStore.all(context.preferences, page))
            assertFalse(context.preferences.getBoolean(SearchHistoryStore.enabledKey(otherPage), true))
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText(context.getString(R.string.search_history_close))
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
            composeRule.onNodeWithTag("toggleListDialogList")
                .performScrollToIndex(SearchHistoryStore.pages.indexOf(page))
            composeRule.onNodeWithText(settingsPageLabel(R.string.settings_category_privacy)).performClick()
            composeRule.runOnIdle {
                assertFalse(profilePrefs.getBoolean(SearchHistoryStore.enabledKey(page), true))
                assertTrue(defaultPrefs.getBoolean(SearchHistoryStore.enabledKey(page), true))
            }
            composeRule.onNodeWithText(context.getString(R.string.search_history_close)).performClick()
            awaitDialogClosed()
        } finally {
            profilePrefs.edit().clear().commit()
            setActiveProfile("default", context)
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
}
