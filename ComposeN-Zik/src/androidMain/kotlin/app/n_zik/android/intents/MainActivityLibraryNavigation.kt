package app.n_zik.android.intents

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import androidx.navigation.NavController
import app.n_zik.android.enums.NavRoutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

@Composable
internal fun DeliverHomeTabShortcut(
    navController: NavController,
    action: String?,
    tab: Int,
    delivery: Long,
    currentRoute: String?,
    onUnknownAction: () -> Unit,
) {
    LaunchedEffect(action, delivery, currentRoute) {
        if (action == null) return@LaunchedEffect
        if (tab == -1) {
            onUnknownAction()
        } else if (currentRoute != null && !currentRoute.startsWith(NavRoutes.home.name)) {
            // Home consumes the shortcut once it is back on the screen.
            if (!navController.popBackStack(NavRoutes.home.name, inclusive = false)) {
                navController.navigate(NavRoutes.home.name) { launchSingleTop = true }
            }
        }
    }
}

@Composable
internal fun DeliverRewindTargets(
    lifecycle: Lifecycle,
    navController: NavController,
    deck: Pair<Int, Int>?,
    playlist: Long?,
    delivery: Long,
    navigationReady: Boolean,
    onDeckDelivered: () -> Unit,
    onPlaylistDelivered: () -> Unit,
) {
    DeliverRouteWhenReady(lifecycle, navController, deck?.let { rewindDeckRoute(it.first, it.second) },
        delivery, navigationReady, onDeckDelivered)
    DeliverRouteWhenReady(lifecycle, navController, playlist?.let { "${NavRoutes.localPlaylist.name}/$it" },
        delivery, navigationReady, onPlaylistDelivered)
}

@Composable
internal fun DeliverSettingsShortcut(
    lifecycle: Lifecycle,
    navController: NavController,
    route: NavRoutes,
    pending: Boolean,
    delivery: Long,
    navigationReady: Boolean,
    onDelivered: () -> Unit,
) {
    DeliverRouteWhenReady(lifecycle, navController, route.name.takeIf { pending }, delivery, navigationReady, onDelivered)
}

@Composable
private fun DeliverRouteWhenReady(
    lifecycle: Lifecycle,
    navController: NavController,
    route: String?,
    delivery: Long,
    navigationReady: Boolean,
    onDelivered: () -> Unit,
) {
    LaunchedEffect(route, delivery, navigationReady) {
        if (!navigationReady || route == null) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
        navController.currentBackStackEntryFlow.first()
        lifecycle.withStarted {
            navController.navigate(route)
            onDelivered()
        }
    }
}

@Composable
internal fun DrainDeferredLibraryIntents(
    lifecycle: Lifecycle,
    navController: NavController,
    navigationReady: Boolean,
    queue: DeferredLibraryIntents,
    awaitRestoredNavigation: suspend () -> Unit,
    consumersIdle: () -> Boolean,
    dispatch: (Intent) -> Unit,
) {
    LaunchedEffect(navigationReady, navController) {
        if (!navigationReady) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            navController.currentBackStackEntryFlow.first()
            awaitRestoredNavigation()
            while (true) {
                queue.drain(consumersIdle, dispatch)
                snapshotFlow { queue.hasQueued }.first { it }
            }
        }
    }
}

@Composable
internal fun DeliverLibraryUri(
    lifecycle: Lifecycle,
    navController: NavController,
    uri: Uri?,
    delivery: Long,
    navigationReady: Boolean,
    isStillPending: (Uri, Long) -> Boolean,
    open: suspend (Uri) -> Unit,
    onDelivered: () -> Unit,
) {
    LaunchedEffect(uri, delivery, navigationReady) {
        if (!navigationReady || uri == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!isStillPending(uri, delivery)) return@repeatOnLifecycle
            navController.currentBackStackEntryFlow.first()
            if (!isStillPending(uri, delivery)) return@repeatOnLifecycle
            try {
                open(uri)
            } catch (cancelled: CancellationException) {
                // A restarted activity must still be able to deliver the pending URI.
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("MainActivity").e(error, "Could not handle a library link")
            }
            if (isStillPending(uri, delivery)) onDelivered()
        }
    }
}
