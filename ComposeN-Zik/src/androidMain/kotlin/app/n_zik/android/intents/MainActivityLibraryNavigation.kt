package app.n_zik.android.intents

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import androidx.navigation.NavController
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    val context = LocalContext.current
    DeliverRouteWhenReady(lifecycle, navController, deck?.let { rewindDeckRoute(it.first, it.second) },
        delivery, navigationReady, onDeckDelivered)
    DeliverRouteWhenReady(lifecycle, navController, playlist?.let { "${NavRoutes.localPlaylist.name}/$it" },
        delivery, navigationReady, onPlaylistDelivered) {
        val id = playlist ?: return@DeliverRouteWhenReady false
        val profile = getActiveProfile(context)
        val row = withContext(NzikDispatchers.DATA) { Database.playlistTable.findById(id).first() }
        row != null && getActiveProfile(context) == profile &&
            RewindPlaylists.isReservedName(row.name) &&
            RewindPlaylists.visibility(context).visibleInLibrary(row.name)
    }
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
    canDeliver: suspend () -> Boolean = { true },
) {
    LaunchedEffect(route, delivery, navigationReady) {
        if (!navigationReady || route == null) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
        navController.currentBackStackEntryFlow.first()
        val allowed = canDeliver()
        lifecycle.withStarted {
            if (allowed) navController.navigate(route)
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
