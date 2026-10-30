package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.miniPlayerButtonOrderKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showMiniPlayerPlayPauseKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class DockToolbarSettingsDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var customOrder: String

    @Before fun setUp() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        setActiveProfile("dock-toolbar-test", context)
        context.preferences.edit().clear().commit()
        customOrder = DockToolbarPreferences.serializeOrder(buildHomeTabDefs().values.map {
            context.resources.getResourceEntryName(it.iconRes)
        }.reversed())
        context.preferences.edit()
            .putString(DockToolbarPreferences.orderKey, customOrder)
            .putString(miniPlayerButtonOrderKey, "expanded-sentinel")
            .putBoolean(showMiniPlayerPlayPauseKey, false).commit()
        DockToolbarSettingsDialog.hideDialog()
        MiniPlayerButtonsSettingsDialog.hideDialog()
    }

    @After fun tearDown() {
        DockToolbarSettingsDialog.hideDialog()
        MiniPlayerButtonsSettingsDialog.hideDialog()
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
        unmockkAll()
    }

    @Test fun `reset and apply clears only toolbar order while normal mini actions remain unchanged`() {
        showDialog()
        compose.onNodeWithText(context.getString(R.string.reset)).performClick()
        assertEquals(customOrder, context.preferences.getString(DockToolbarPreferences.orderKey, null))
        compose.onNodeWithText(context.getString(R.string.dialog_apply)).performClick()
        assertFalse(context.preferences.contains(DockToolbarPreferences.orderKey))
        assertOtherMiniPreferencesUnchanged()
    }

    @Test fun `reset then cancel keeps stored toolbar order and normal mini actions unchanged`() {
        showDialog()
        compose.onNodeWithText(context.getString(R.string.reset)).performClick()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertEquals(customOrder, context.preferences.getString(DockToolbarPreferences.orderKey, null))
        assertOtherMiniPreferencesUnchanged()
    }

    @Test fun `mini action settings expose the normal controls without separate compact selectors`() {
        showDialog(miniActions = true)
        compose.onNodeWithText(context.getString(R.string.miniplayer_button_skip_back)).assertExists()
        compose.onNodeWithText(context.getString(R.string.miniplayer_button_play_pause)).assertExists()
        compose.onNodeWithText(context.getString(R.string.miniplayer_button_skip_forward)).assertExists()
        compose.onNodeWithText(context.getString(R.string.compact_miniplayer_slot_1)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.compact_miniplayer_slot_2)).assertDoesNotExist()
    }

    private fun assertOtherMiniPreferencesUnchanged() {
        assertEquals("expanded-sentinel", context.preferences.getString(miniPlayerButtonOrderKey, null))
        assertFalse(context.preferences.getBoolean(showMiniPlayerPlayPauseKey, true))
    }

    private fun showDialog(miniActions: Boolean = false) {
        val shape = RoundedCornerShape(20.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        if (miniActions) MiniPlayerButtonsSettingsDialog.showDialog()
        else DockToolbarSettingsDialog.showDialog()
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                if (miniActions) MiniPlayerButtonsSettingsDialog.Render()
                else DockToolbarSettingsDialog.Render()
            }
        }
        compose.waitForIdle()
    }
}
