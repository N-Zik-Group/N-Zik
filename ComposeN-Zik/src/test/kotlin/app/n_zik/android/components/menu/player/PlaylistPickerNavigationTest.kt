package app.n_zik.android.components.menu.player

import android.app.Application
import android.os.Bundle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.ui.components.themed.PlaylistPickerOpenButton
import app.n_zik.android.core.navigation.LocalPlaylistPickerNavigation
import app.n_zik.android.core.navigation.navigateToPickedPlaylist
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaylistPickerNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `opening the displayed playlist does not add another destination`() {
        val controller = playlistController(42)
        navigateToPickedPlaylist(controller, 42)
        verify(exactly = 0) { controller.navigate("localPlaylist/42") }
    }

    @Test fun `opening a different playlist or leaving the device filter navigates normally`() {
        val controller = playlistController(42, onDevice = true)
        navigateToPickedPlaylist(controller, 42)
        navigateToPickedPlaylist(controller, 17)
        verify(exactly = 1) { controller.navigate("localPlaylist/42") }
        verify(exactly = 1) { controller.navigate("localPlaylist/17") }
    }

    private fun playlistController(id: Long, onDevice: Boolean = false): NavController {
        val destination = mockk<NavDestination>()
        every { destination.route } returns "localPlaylist/{id}?onDevice={onDevice}"
        val entry = mockk<NavBackStackEntry>()
        every { entry.destination } returns destination
        every { entry.arguments } returns Bundle().apply {
            putLong("id", id)
            putBoolean("onDevice", onDevice)
        }
        return mockk<NavController>(relaxUnitFun = true).also {
            every { it.currentBackStackEntry } returns entry
        }
    }

    @Test fun `track row picker opens the selected playlist through the latest host before dismissal`() {
        val events = mutableListOf<String>()
        val destination = mutableStateOf<((Long, Boolean) -> Unit)?>(null)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val open = hasClickAction() and hasContentDescription(app.getString(R.string.playlist_action_open))
        compose.setContent {
            val appearance = Appearance(ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape)
            CompositionLocalProvider(LocalAppearance provides appearance, LocalPlaylistPickerNavigation provides destination.value) {
                PlaylistPickerOpenButton(42L, onDismiss = { events += "dismissed" }, onDevice = true)
            }
        }
        compose.onNode(open).assertDoesNotExist()
        compose.runOnIdle { destination.value = { id, _ -> events += "previous $id" } }
        compose.onNode(open).assertExists()
        compose.runOnIdle { destination.value = { id, onDevice -> events += "latest $id $onDevice" } }
        compose.onNode(open).performClick()
        compose.runOnIdle { assertEquals(listOf("latest 42 true", "dismissed"), events) }
    }

    @Test fun `player picker keeps its explicit navigation ahead of the row host`() {
        val events = mutableListOf<String>()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val open = hasClickAction() and hasContentDescription(app.getString(R.string.playlist_action_open))
        compose.setContent {
            val appearance = Appearance(ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape)
            CompositionLocalProvider(LocalAppearance provides appearance, LocalPlaylistPickerNavigation provides { id, _ -> events += "host $id" }) {
                PlaylistPickerOpenButton(17L, onDismiss = { events += "dismissed" }) { id -> events += "player $id" }
            }
        }
        compose.onNode(open).performClick()
        compose.runOnIdle { assertEquals(listOf("player 17", "dismissed"), events) }
    }
}
