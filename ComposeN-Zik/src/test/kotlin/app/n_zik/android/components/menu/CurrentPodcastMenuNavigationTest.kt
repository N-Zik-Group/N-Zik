package app.n_zik.android.components.menu

import android.app.Application
import android.os.Bundle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.ui.components.MenuState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CurrentPodcastMenuNavigationTest {
    @Test
    fun `the current podcast closes the player and menu without pushing another destination`() {
        val podcastId = "MPSP123"
        val controller = mockk<NavController>()
        val current = mockk<NavBackStackEntry>()
        val destination = mockk<NavDestination>()
        val menu = MenuState().apply { display {} }
        var playerCloseCount = 0
        every { destination.route } returns "podcast/{id}?sourceOnly={sourceOnly}"
        every { current.arguments } returns Bundle().apply { putString("id", podcastId) }
        every { current.destination } returns destination
        every { controller.currentBackStackEntry } returns current

        navigateToMenuEntity(
            controller,
            menu,
            NavRoutes.podcast,
            podcastId,
            route = "podcast/$podcastId?sourceOnly=true",
            onNavigate = { playerCloseCount++ },
        )

        assertEquals(1, playerCloseCount)
        assertFalse(menu.isDisplayed)
    }
}
