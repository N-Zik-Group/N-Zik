package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsSelectedValueTest {
    @get:Rule
    val composeRule = createComposeRule()

    private enum class Choice { First, Second }

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Test
    fun `icon selector keeps description and changing selection on distinct lines`() {
        val selected = mutableStateOf(Choice.First)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OtherEnumValueSelectorSettingsEntry(
                    title = "Choice title",
                    icon = R.drawable.chevron_forward,
                    text = "Choice description",
                    selectedValue = selected.value,
                    onValueSelected = { selected.value = it },
                    valueText = { "Selected ${it.name}" }
                )
            }
        }
        assertThreeLines("Selected First")
        composeRule.runOnIdle { selected.value = Choice.Second }
        assertThreeLines("Selected Second")
        composeRule.onNodeWithText(currentValue("Selected First"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `plain enum selector separates existing combined description without repeating value`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                EnumValueSelectorSettingsEntry(
                    title = "Choice title",
                    text = "Choice description\nSelected First",
                    selectedValue = Choice.First,
                    onValueSelected = {},
                    valueText = { "Selected ${it.name}" }
                )
            }
        }
        assertThreeLines("Selected First")
    }

    @Test
    fun `custom dialog row shows explicitly labelled current value`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OtherSettingsEntry(
                    title = "Choice title",
                    text = "Choice description",
                    selectedValueText = "None",
                    icon = R.drawable.chevron_forward,
                    onClick = {}
                )
            }
        }
        assertThreeLines("None")
    }

    @Test
    fun `cache limit remains separate when its selected size changes`() {
        val selected = mutableStateOf("128 MB")
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                CacheSettingsEntry(
                    title = "Choice title",
                    text = "Choice description",
                    selectedValueText = selected.value,
                    icon = R.drawable.image,
                    onClick = {},
                    onTrashClick = {}
                )
            }
        }
        assertThreeLines("128 MB")
        composeRule.runOnIdle { selected.value = "2 GB" }
        assertThreeLines("2 GB")
        composeRule.onNodeWithText(currentValue("128 MB"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `information row separates detected quality from description`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OtherInfoSettingsEntry(
                    title = "Choice title",
                    text = "Choice description",
                    selectedValueText = "Auto (High)",
                    icon = R.drawable.audio_quality
                )
            }
        }
        assertThreeLines("Auto (High)")
    }

    @Test
    fun `description compatibility removes only a complete selected value suffix`() {
        assertEquals("", selectorDescription(null, "First"))
        assertEquals("", selectorDescription("First", "First"))
        assertEquals("Details", selectorDescription("Details\nFirst", "First"))
        assertEquals("First is recommended", selectorDescription("First is recommended", "First"))
        assertEquals("Details\nFirst alternative", selectorDescription("Details\nFirst alternative", "First"))
        assertEquals("Details\nMore details", selectorDescription("Details\nMore details", "First"))
    }

    private fun assertThreeLines(value: String) {
        val title = composeRule.onNodeWithText("Choice title", useUnmergedTree = true)
        val description = composeRule.onNodeWithText("Choice description", useUnmergedTree = true)
        val selection = composeRule.onNodeWithText(currentValue(value), useUnmergedTree = true)
        title.assertIsDisplayed()
        description.assertIsDisplayed()
        selection.assertIsDisplayed()
        composeRule.onAllNodesWithText(currentValue(value), useUnmergedTree = true).assertCountEquals(1)
        assertTrue(title.fetchSemanticsNode().boundsInRoot.bottom <= description.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(description.fetchSemanticsNode().boundsInRoot.bottom <= selection.fetchSemanticsNode().boundsInRoot.top)
    }

    private fun currentValue(value: String): String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.settings_current_value, value)
}
