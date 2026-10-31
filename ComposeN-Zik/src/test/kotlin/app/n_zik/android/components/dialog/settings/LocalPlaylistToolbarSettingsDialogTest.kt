package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
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
import app.it.fast4x.rimusic.utils.localPlaylistToolbarOrderKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class LocalPlaylistToolbarSettingsDialogTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        preferences.edit().clear().commit()
        LocalPlaylistToolbarSettingsDialog.hideDialog()
    }

    @After fun hideDialog() {
        LocalPlaylistToolbarSettingsDialog.hideDialog()
        preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test fun `new insertion action is registered once and old custom orders survive`() {
        val dialog = LocalPlaylistToolbarSettingsDialog
        val order = dialog.parseOrder("[\"export\",\"search\",\"export\",\"obsolete\"]")

        assertEquals(listOf("insertion_position", "export", "search"), order.take(3))
        assertEquals(1, order.count { it == "insertion_position" })
        assertEquals(dialog.allButtonIds.toSet(), order.toSet())
        assertEquals("insertion_position", dialog.parseOrder("").first())
        assertEquals(
            listOf("search", "insertion_position", "export"),
            dialog.parseOrder("[\"search\",\"insertion_position\",\"export\"]").take(3)
        )
    }

    @Test fun `refresh on opening is configurable once and follows existing sync order`() {
        val dialog = LocalPlaylistToolbarSettingsDialog
        assertEquals(1, dialog.allButtonIds.count { it == "refresh_on_open" })
        val saved = dialog.parseOrder("[\"export\",\"sync\",\"search\"]")
        assertEquals(listOf("insertion_position", "export", "sync", "refresh_on_open", "search"), saved.take(5))
        assertTrue(dialog.searchOptions().any { it.itemId == "pl_refresh_on_open" })
    }

    @Test fun `insertion action setting uses its own key and local playlist title`() {
        showDialog()

        composeRule.onNodeWithText(
            context.getString(R.string.local_playlist_sort) + " - " + context.getString(R.string.toolbar)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.playlist_insertion_position))
            .assertIsDisplayed().performClick()
        assertTrue("changes remain a draft until apply", preferences.getBoolean("pl_ts_insertion_position", true))
        composeRule.onNodeWithText(context.getString(R.string.dialog_apply)).performClick()

        assertFalse(preferences.getBoolean("pl_ts_insertion_position", true))
        assertTrue(preferences.getBoolean("pl_ts_search", true))
        val savedOrder = JSONArray(preferences.getString(localPlaylistToolbarOrderKey, "[]"))
        assertEquals("insertion_position", savedOrder.getString(0))
    }

    @Test fun `cancel discards insertion toggle changes`() {
        showDialog()
        composeRule.onNodeWithText(context.getString(R.string.playlist_insertion_position)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

        assertTrue(preferences.getBoolean("pl_ts_insertion_position", true))
        assertFalse(preferences.contains(localPlaylistToolbarOrderKey))
    }

    @Test fun `reset restores insertion action and default toolbar order`() {
        preferences.edit().putBoolean("pl_ts_insertion_position", false)
            .putString(localPlaylistToolbarOrderKey, "[\"search\",\"insertion_position\"]").commit()

        LocalPlaylistToolbarSettingsDialog.reset(context)

        assertTrue(preferences.getBoolean("pl_ts_insertion_position", false))
        assertEquals(
            LocalPlaylistToolbarSettingsDialog.allButtonIds,
            LocalPlaylistToolbarSettingsDialog.parseOrder(preferences.getString(localPlaylistToolbarOrderKey, "") ?: "")
        )
    }

    private fun showDialog() {
        val shape = RoundedCornerShape(20.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape,
            uiRoundnessShape = shape,
            artistThumbnailShape = shape
        )
        LocalPlaylistToolbarSettingsDialog.showDialog()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                LocalPlaylistToolbarSettingsDialog.Render()
            }
        }
        composeRule.waitForIdle()
    }
}
