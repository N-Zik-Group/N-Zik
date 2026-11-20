package app.n_zik.android.intents

import android.app.Application
import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.MainActivity
import app.n_zik.android.enums.HomeScreenTabs
import app.n_zik.android.enums.NavRoutes
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LibraryIntentDeliveryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stoppingAndResumingKeepsTheNextDeliveryOfTheSameUri() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        val uri = Uri.parse("https://music.youtube.com/search?q=Daft%20Punk")
        val pending = mutableStateOf<Long?>(1L)
        val graphReady = mutableStateOf(false)
        val opened = mutableListOf<Long>()
        val interrupted = mutableListOf<Long>()
        val acknowledged = mutableListOf<Long>()

        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            val navController = rememberNavController()
            val delivery = pending.value
            if (graphReady.value) {
                NavHost(navController, startDestination = "home") {
                    composable("home") {}
                }
            }
            if (delivery != null) {
                DeliverLibraryUri(
                    lifecycle = owner.lifecycle,
                    navController = navController,
                    uri = uri,
                    delivery = delivery,
                    navigationReady = true,
                    isStillPending = { candidate, generation ->
                        candidate == uri && pending.value == generation
                    },
                    open = {
                        opened += delivery
                        if (delivery == 1L) {
                            try {
                                awaitCancellation()
                            } finally {
                                interrupted += delivery
                            }
                        }
                        // A new delivery can arrive before the old handler acknowledges completion.
                        if (delivery == 2L) pending.value = 3L
                    },
                    onDelivered = {
                        acknowledged += delivery
                        pending.value = null
                    },
                )
            }
        }

        compose.runOnIdle {
            assertEquals(emptyList<Long>(), opened)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        compose.runOnIdle {
            assertEquals(emptyList<Long>(), opened)
            graphReady.value = true
        }
        compose.runOnIdle {
            assertEquals(listOf(1L), opened)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
        }
        compose.runOnIdle {
            assertEquals(listOf(1L), interrupted)
            assertEquals(emptyList<Long>(), acknowledged)
            pending.value = 2L
        }
        compose.runOnIdle {
            assertEquals(listOf(1L), opened)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        compose.waitUntil { pending.value == null }
        compose.runOnIdle {
            assertEquals(listOf(1L, 2L, 3L), opened)
            assertEquals(listOf(3L), acknowledged)
            assertNull(pending.value)
        }
    }

    @Test
    fun homeTabShortcutReachesHomeWhenItIsMissingFromTheBackStack() {
        val shortcut = MainActivity.action_songs
        val pendingAction = mutableStateOf<String?>(null)
        var unknownActions = 0
        lateinit var navController: NavHostController

        compose.setContent {
            navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "other",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable("other") { BasicText("Other") }
                composable(NavRoutes.home.name) { BasicText("Home: ${pendingAction.value}") }
            }
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            val action = pendingAction.value
            DeliverHomeTabShortcut(
                navController = navController,
                action = action,
                tab = if (action == shortcut) HomeScreenTabs.Songs.index else -1,
                delivery = 1L,
                currentRoute = currentRoute,
                onUnknownAction = {
                    unknownActions++
                    pendingAction.value = null
                },
            )
        }

        compose.onNodeWithText("Other").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("other", navController.currentDestination?.route)
            assertNull(navController.previousBackStackEntry)
            pendingAction.value = shortcut
        }
        compose.runOnIdle {
            assertEquals(NavRoutes.home.name, navController.currentDestination?.route)
            assertEquals("other", navController.previousBackStackEntry?.destination?.route)
            assertEquals(shortcut, pendingAction.value)
            assertEquals(0, unknownActions)
        }
        compose.onNodeWithText("Home: $shortcut").assertIsDisplayed()
    }
}
