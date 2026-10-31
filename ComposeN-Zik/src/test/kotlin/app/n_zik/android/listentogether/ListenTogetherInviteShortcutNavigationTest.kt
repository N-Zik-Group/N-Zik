package app.n_zik.android.listentogether

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.junit4.createComposeRule
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.MainActivity
import app.n_zik.android.components.ui.screens.home.initialShortcutAction
import app.n_zik.android.components.ui.screens.listentogether.consumeInvitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ListenTogetherInviteShortcutNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: NavHostController
    private var shortcutAction by mutableStateOf<String?>(null)
    private var pendingUri by mutableStateOf<Uri?>(null)

    private fun showHost(start: String) {
        compose.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = "home") {
                composable("home") { Text("Home") }
                composable("other") { Text("Other") }
                composable(NavRoutes.listenTogether.name) { Text("Listen Together") }
            }
            // The two competing MainActivity effects must be exercised together.
            val currentRoute = controller.currentBackStackEntryAsState().value?.destination?.route
            LaunchedEffect(shortcutAction, currentRoute) {
                if (shortcutAction != null) {
                    if (currentRoute?.startsWith(NavRoutes.home.name) == true) {
                        shortcutAction = null
                    } else {
                        controller.popBackStack(NavRoutes.home.name, inclusive = false)
                    }
                }
            }
            LaunchedEffect(pendingUri) {
                val uri = pendingUri ?: return@LaunchedEffect
                parseListenTogetherInvite(uri)?.let { code ->
                    pendingUri = null
                    openListenTogetherInvite(controller, code)
                }
            }
        }
        compose.waitForIdle()
        if (start != "home") {
            compose.runOnIdle { controller.navigate(start) }
            compose.waitForIdle()
        }
    }

    private fun invitation(cold: Boolean, start: String, action: String) {
        val url = "https://metrolist.cc/listen?code=ab12CD34"
        val intent = if (action == Intent.ACTION_VIEW) Intent(action, Uri.parse(url))
            else Intent(action).putExtra(Intent.EXTRA_TEXT, url)
        showHost(start)
        compose.runOnIdle {
            shortcutAction = if (cold) initialShortcutAction(inviteAwareShortcutAction(intent), false)
                else inviteAwareShortcutAction(intent)
            // A valid invitation must never enter the launcher-shortcut channel.
            assertNull(shortcutAction)
            pendingUri = listenTogetherInviteUri(intent)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(NavRoutes.listenTogether.name, controller.currentDestination?.route)
            assertEquals("AB12CD34", consumeInvitation(controller.currentBackStackEntry!!.savedStateHandle))
            assertNull(consumeInvitation(controller.currentBackStackEntry!!.savedStateHandle))
            assertNull(pendingUri)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(NavRoutes.listenTogether.name, controller.currentDestination?.route)
        }
    }

    @Test fun coldViewFromHome() = invitation(true, "home", Intent.ACTION_VIEW)
    @Test fun coldViewFromOther() = invitation(true, "other", Intent.ACTION_VIEW)
    @Test fun coldSendFromHome() = invitation(true, "home", Intent.ACTION_SEND)
    @Test fun coldSendFromOther() = invitation(true, "other", Intent.ACTION_SEND)
    @Test fun warmViewFromHome() = invitation(false, "home", Intent.ACTION_VIEW)
    @Test fun warmViewFromOther() = invitation(false, "other", Intent.ACTION_VIEW)
    @Test fun warmSendFromHome() = invitation(false, "home", Intent.ACTION_SEND)
    @Test fun warmSendFromOther() = invitation(false, "other", Intent.ACTION_SEND)

    @Test fun launcherSearchStillReturnsToHome() {
        showHost("other")
        val intent = Intent(MainActivity.action_search)
        assertEquals(MainActivity.action_search, inviteAwareShortcutAction(intent))
        assertEquals(MainActivity.action_search, initialShortcutAction(inviteAwareShortcutAction(intent), false))
        compose.runOnIdle { shortcutAction = inviteAwareShortcutAction(intent) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("home", controller.currentDestination?.route)
            assertNull(shortcutAction)
        }
    }
}
