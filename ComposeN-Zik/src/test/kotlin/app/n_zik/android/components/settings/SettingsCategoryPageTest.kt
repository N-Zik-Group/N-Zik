package app.n_zik.android.components.settings

import androidx.compose.foundation.layout.WindowInsets

import app.n_zik.android.LocalPlayerAwareWindowInsets

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.settings.common.SettingsCategoryPage
import app.n_zik.android.components.settings.common.SettingsResetCard
import app.n_zik.android.components.settings.common.resetScopeForTarget
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassProfileDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
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

    @Test fun emptyFocusedSearchFadesLazyCardsAndTypingRestoresMatchingCards() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                SettingsCategoryPage(categoryId = "behavior", navController = mockk(relaxed = true))
            }
        }
        val targetTag = "settings.target.card.0.app_behavior"
        pumpUntil("category card composed") {
            runCatching { composeRule.onNodeWithTag(targetTag, useUnmergedTree = true).assertExists() }.isSuccess
        }
        composeRule.onNode(hasSetTextAction()).performClick()
        pumpUntil("focused category card hidden after fade") {
            runCatching { composeRule.onNodeWithTag(targetTag, useUnmergedTree = true).assertIsNotDisplayed() }.isSuccess
        }
        composeRule.onNode(hasSetTextAction()).performTextInput(context.getString(R.string.app_behavior))
        pumpUntil("matching category card restored") {
            runCatching { composeRule.onNodeWithTag(targetTag, useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
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
        pumpUntil("unmatched content has no scroll range") { categoryCannotScroll("privacy") }
        input.performSemanticsAction(SemanticsActions.SetText) {
            it(androidx.compose.ui.text.AnnotatedString(context.getString(R.string.privacy_screen_protection)))
        }
        pumpUntil("short matching card fits the viewport") {
            runCatching {
                composeRule.onNodeWithTag("settings.target.card.9.privacy_screen_protection", useUnmergedTree = true).assertIsDisplayed()
                categoryCannotScroll("privacy")
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings.category.list.privacy")
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1000f) }
        pumpUntil("short matching content cannot move") { categoryCannotScroll("privacy") }
    }

    private fun categoryCannotScroll(category: String): Boolean {
        val range = composeRule.onNodeWithTag("settings.category.list.$category")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        return range.value() == 0f && range.maxValue() == 0f
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
        listOf(0 to R.string.app_behavior, 0 to R.string.gestures_events, 1 to R.string.swipe_to_action).forEach { (tab, title) ->
            val card = "card.$tab.${context.resources.getResourceEntryName(title)}"
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

    @Test fun `Glass reset scope restores current quality defaults without changing master or other settings`() {
        val store = GlassStore(context)
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
        assertEquals(glassProfileDefaults(GlassQuality.Quality), store.read())
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
}
