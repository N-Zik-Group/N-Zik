package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.settings.appearance.preview.encodeThemeAppearance
import app.n_zik.android.components.settings.appearance.preview.themePreviewPreferences
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.historySortMenuOrderKey
import app.n_zik.android.utils.preferences
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class HistorySortSettingsDialogTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun clearSettings() {
        HistorySortSettingsDialog.hideDialog()
        context.preferences.edit().clear().commit()
    }

    @Test fun `applying a hidden sort retains its position in local and portable settings`() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val prefs = context.preferences
        val order = """["ARTIST","DATE","ALPHABETICAL"]"""
        val visibilityKey = "hist_sort_DATE_visible"
        prefs.edit().putString(historySortMenuOrderKey, order).putBoolean(visibilityKey, false).commit()
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                HistorySortSettingsDialog.DialogBody()
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.common_action_apply)).performClick()

        assertEquals(order, prefs.getString(historySortMenuOrderKey, null))
        assertFalse(prefs.getBoolean(visibilityKey, true))
        val document = encodeThemeAppearance(prefs.all, emptyMap<String, Any>())
        val restored = themePreviewPreferences(document, glass = false).plain
        assertEquals(order, restored[historySortMenuOrderKey])
        assertEquals(false, restored[visibilityKey])
    }
}
