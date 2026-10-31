package app.n_zik.android.listentogether

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.components.ui.screens.listentogether.consumeInvitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ListenTogetherInviteNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: NavHostController
    private lateinit var screenHandle: SavedStateHandle

    private fun showHost() {
        compose.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = "home") {
                composable("home") { Text("Home") }
                composable(NavRoutes.listenTogether.name) { entry ->
                    screenHandle = remember { entry.savedStateHandle }
                    Text("Listen Together")
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun invitationIsDeliveredToRoomEntryAndBackReturnsHome() {
        showHost()
        compose.runOnIdle { openListenTogetherInvite(controller, "ABCDEFGH") }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(NavRoutes.listenTogether.name, controller.currentDestination?.route)
            assertSame(controller.currentBackStackEntry?.savedStateHandle, screenHandle)
            assertEquals("ABCDEFGH", consumeInvitation(screenHandle))
            assertNull(screenHandle.get<String>(INVITE_ROOM_CODE_KEY))
            controller.popBackStack()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("home", controller.currentDestination?.route) }
    }

    @Test
    fun repeatedTopInvitationsReachRememberedHandleWithoutAddingEntries() {
        showHost()
        compose.runOnIdle { openListenTogetherInvite(controller, "ABCDEFGH") }
        compose.waitForIdle()
        compose.runOnIdle {
            val originalEntry = controller.currentBackStackEntry
            val originalHandle = screenHandle
            val invitations = originalHandle.getStateFlow<String?>(INVITE_ROOM_CODE_KEY, null)
            assertEquals("ABCDEFGH", consumeInvitation(originalHandle))
            assertNull(invitations.value)
            openListenTogetherInvite(controller, "ABCDEFGH")
            assertSame(originalEntry, controller.currentBackStackEntry)
            assertSame(originalHandle, controller.currentBackStackEntry?.savedStateHandle)
            assertEquals("ABCDEFGH", invitations.value)
            assertEquals("ABCDEFGH", consumeInvitation(originalHandle))
            openListenTogetherInvite(controller, "12345678")
            assertEquals("12345678", invitations.value)
            assertEquals("12345678", consumeInvitation(originalHandle))
            controller.popBackStack()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("home", controller.currentDestination?.route) }
    }
}
