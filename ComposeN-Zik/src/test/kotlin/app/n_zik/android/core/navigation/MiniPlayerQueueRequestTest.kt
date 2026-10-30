package app.n_zik.android.core.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.test.junit4.createComposeRule
import app.n_zik.android.utils.appNavBarPresentForRoute
import app.n_zik.android.components.glass.QueueEmbeddedMini
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MiniPlayerQueueRequestTest {
    @get:Rule val compose = createComposeRule()

    @Test fun embeddedMiniBlocksRecursiveQueueRequestsAndRestoresOuterCallback() {
        var openCount = 0
        var fallbackNavigations = 0
        val outerRequest: () -> Unit = { openCount++ }
        var before: (() -> Unit)? = null
        var embedded: (() -> Unit)? = null
        var after: (() -> Unit)? = null
        compose.setContent {
            CompositionLocalProvider(LocalMiniPlayerQueueRequest provides outerRequest) {
                before = LocalMiniPlayerQueueRequest.current
                QueueEmbeddedMini { embedded = LocalMiniPlayerQueueRequest.current }
                after = LocalMiniPlayerQueueRequest.current
            }
        }
        compose.runOnIdle {
            before?.invoke() ?: run { fallbackNavigations++ }
            assertEquals(1, openCount)
            // Both the queue button and long-press use this callback before legacy navigation.
            repeat(2) { embedded?.invoke() ?: run { fallbackNavigations++ } }
            assertEquals(1, openCount)
            assertEquals(0, fallbackNavigations)
            assertTrue(after === outerRequest)
            after?.invoke()
            assertEquals(2, openCount)
        }
    }

    @Test fun openingAndClosingQueueKeepsHomeDestinationAndNavigationInset() {
        lateinit var navigation: NavHostController
        lateinit var openQueue: () -> Unit
        var queueShown by mutableStateOf(false)
        var observedRoute: String? = null
        var observedEntryId: String? = null
        var reservesNavigationInset = false
        compose.setContent {
            navigation = rememberNavController()
            val entry by navigation.currentBackStackEntryAsState()
            observedRoute = entry?.destination?.route
            observedEntryId = entry?.id
            reservesNavigationInset = appNavBarPresentForRoute(observedRoute, activeHomeTabCount = 4)
            CompositionLocalProvider(LocalMiniPlayerQueueRequest provides { queueShown = true }) {
                openQueue = requireNotNull(LocalMiniPlayerQueueRequest.current)
                NavHost(navigation, startDestination = "home") {
                    composable("home") { Text(if (queueShown) "Queue overlay" else "Home") }
                    composable("queue") { Text("Legacy queue destination") }
                }
            }
        }
        var homeEntryId: String? = null
        compose.runOnIdle {
            assertEquals("home", observedRoute)
            assertTrue(reservesNavigationInset)
            homeEntryId = observedEntryId
            openQueue()
        }
        compose.runOnIdle {
            assertTrue(queueShown)
            assertEquals("home", observedRoute)
            assertEquals(homeEntryId, observedEntryId)
            assertEquals("home", navigation.currentDestination?.route)
            assertTrue(reservesNavigationInset)
            queueShown = false
        }
        compose.runOnIdle {
            assertEquals("home", observedRoute)
            assertEquals(homeEntryId, observedEntryId)
            assertTrue(reservesNavigationInset)
        }
    }
}
