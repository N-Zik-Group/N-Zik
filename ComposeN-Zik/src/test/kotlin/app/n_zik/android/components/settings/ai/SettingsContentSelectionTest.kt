package app.n_zik.android.components.settings.ai

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.components.settings.accounts.AccountsSettingsContent
import app.n_zik.android.components.settings.common.LocalSettingsCardSlots
import app.n_zik.android.components.settings.common.SettingsCardSlots
import app.n_zik.android.components.settings.other.OtherSettingsContent
import app.n_zik.android.components.tab.Search
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SettingsContentSelectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun reenabledQuickPicksReturnsToItsOriginalCardPosition() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().putBoolean(enableQuickPicksPageKey, true).commit()
        val expected = listOf(
            R.string.settings_section_quick_picks_visibility_title,
            R.string.recommendations_title,
            R.string.rewind_settings_title,
            R.string.playlist_top,
        )
        val slots = SettingsCardSlots()
        composeRule.setContent {
            CompositionLocalProvider(LocalSettingsCardSlots provides slots) {
                AIRecommendationSettingsContent(
                    search = Search(initialVisible = true),
                    navController = mockk(relaxed = true),
                    selectedCards = expected.toSet(),
                )
            }
        }
        composeRule.runOnIdle {
            assertEquals(expected, slots.titles.toList())
            context.preferences.edit().putBoolean(enableQuickPicksPageKey, false).commit()
        }
        composeRule.runOnIdle {
            assertEquals(expected - R.string.recommendations_title, slots.titles.toList())
            context.preferences.edit().putBoolean(enableQuickPicksPageKey, true).commit()
        }
        composeRule.runOnIdle { assertEquals(expected, slots.titles.toList()) }
    }

    @Test fun mixedContentHostsComposeOnlySelectedCardsWithoutApplyingTheirResets() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putString("selection-sentinel", "keep").commit()
        val before = context.preferences.all.toMap()
        val selected = setOf(R.string.settings_reset)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
        )
        val rendered = mutableSetOf<String>()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val search = Search(initialVisible = true)
                Column {
                    AIRecommendationSettingsContent(search, mockk(relaxed = true), selected,
                        cardModifier = { title ->
                            rendered += "ai:$title"
                            Modifier.testTag("ai:$title")
                        })
                    AccountsSettingsContent(search, mockk(relaxed = true), selected,
                        cardModifier = { title ->
                            rendered += "accounts:$title"
                            Modifier.testTag("accounts:$title")
                        })
                    OtherSettingsContent(search, selected,
                        cardModifier = { title ->
                            rendered += "other:$title"
                            Modifier.testTag("other:$title")
                        })
                }
            }
        }
        composeRule.waitForIdle()
        val expected = setOf("ai:${R.string.settings_reset}", "accounts:${R.string.settings_reset}", "other:${R.string.settings_reset}")
        assertEquals(expected, rendered)
        expected.forEach { composeRule.onNodeWithTag(it).assertExists() }
        assertEquals(before, context.preferences.all)
    }
}
