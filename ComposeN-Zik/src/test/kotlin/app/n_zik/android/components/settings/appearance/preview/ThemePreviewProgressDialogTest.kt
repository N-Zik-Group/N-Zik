package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import app.n_zik.android.R
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "notnight")
class ThemePreviewProgressDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `separate progress dialog shows preparation then completed count and cancellation`() {
        val context = RuntimeEnvironment.getApplication()
        val total = mutableIntStateOf(0)
        val completed = mutableIntStateOf(0)
        var cancellations = 0
        context.preferences.edit()
            .putString(colorPaletteModeKey, ColorPaletteMode.Dark.name)
            .putString(colorPaletteNameKey, ColorPaletteName.Default.name)
            .commit()
        compose.setContent {
            val appearance = rememberThemePreviewProgressAppearance()
            assertTrue(appearance.colorPalette.isDark)
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ThemePreviewProgressDialog(completed.intValue, total.intValue) { cancellations++ }
            }
        }
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithTag("settings.section.header.icon", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.theme_store_preparing_previews)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.theme_preview_keep_open)).assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        )).assertExists()
        compose.runOnIdle {
            total.intValue = 8
            completed.intValue = 3
        }
        compose.onNodeWithText(context.getString(R.string.theme_store_preview_capture_progress, 3, 8)).assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(3f / 8, 0f..1f),
        )).assertExists()
        compose.onNodeWithText(context.getString(R.string.common_action_cancel)).performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
    }

    @Test
    fun `fresh app defaults stay dark while portable preview defaults follow the system`() {
        val context = RuntimeEnvironment.getApplication()
        context.preferences.edit().clear().commit()
        compose.setContent {
            assertTrue(rememberThemePreviewProgressAppearance().colorPalette.isDark)
        }
        compose.waitForIdle()
        assertEquals(ColorPaletteMode.System.name, themePreviewPreferences(null, false).plain[colorPaletteModeKey])
    }

    @Test
    @Config(qualifiers = "w360dp-h240dp-notnight")
    fun `cancel remains reachable by scrolling in a short window`() {
        val context = RuntimeEnvironment.getApplication()
        var cancellations = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides rememberThemePreviewProgressAppearance()) {
                ThemePreviewProgressDialog(onCancel = { cancellations++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.common_action_cancel))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
    }

    @Test
    fun `gallery uses the app system palette and shared section actions`() {
        val context = RuntimeEnvironment.getApplication()
        context.preferences.edit().clear()
            .putString(colorPaletteModeKey, ColorPaletteMode.System.name)
            .putString(colorPaletteNameKey, ColorPaletteName.Default.name)
            .commit()
        var generations = 0
        var expectedDescriptionColor = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            val appearance = rememberThemePreviewProgressAppearance()
            assertFalse(appearance.colorPalette.isDark)
            expectedDescriptionColor = appearance.colorPalette.textSecondary
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GalleryActions(emptyMap(), null, onGenerate = { generations++ }, onReplace = { _, _ -> })
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(context.getString(R.string.theme_preview_gallery_description))
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(expectedDescriptionColor, layouts.single().layoutInput.style.color)
        compose.onNodeWithTag("settings.section.header.icon", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.theme_preview_generate)).performClick()
        compose.runOnIdle { assertEquals(1, generations) }
    }
}
