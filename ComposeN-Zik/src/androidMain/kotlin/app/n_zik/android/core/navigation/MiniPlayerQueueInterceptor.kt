package app.n_zik.android.core.navigation

import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavRoutes
import timber.log.Timber

/**
 * Intercepts navigation to the legacy "queue" route so the queue can be shown as an overlay
 * instead of a navigation destination.
 *
 * The legacy mini-player long-press calls [NavController.navigate] with the queue route. The
 * [NavController.OnDestinationChangedListener] registered by [attach] runs synchronously inside
 * that `navigate()` call, so calling [NavController.popBackStack] before the first frame means
 * the queue destination is never composed (the navigation StateFlows only keep the final value).
 *
 * The [NavController.currentDestination] guard makes the interception idempotent: if the listener
 * is invoked while "queue" is no longer the current destination, nothing is popped.
 *
 * @property navController the shared nav controller to listen on
 * @property onQueueRequested invoked when a queue navigation is intercepted (shows the overlay)
 */
class MiniPlayerQueueInterceptor(
    private val navController: NavController,
    private val onQueueRequested: () -> Unit,
) {
    private val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
        if (destination.route == NavRoutes.queue.name) {
            if (navController.currentDestination?.route == NavRoutes.queue.name) {
                Timber.tag(TAG).i("Queue navigation intercepted, showing overlay instead")
                onQueueRequested()
                navController.popBackStack()
            } else {
                Timber.tag(TAG).w("Queue navigation seen but no longer the current destination, ignoring")
            }
        }
    }

    /** Registers the destination listener on [navController]. */
    fun attach() {
        navController.addOnDestinationChangedListener(listener)
    }

    /** Unregisters the destination listener from [navController]. */
    fun detach() {
        navController.removeOnDestinationChangedListener(listener)
    }

    private companion object {
        const val TAG = "MiniPlayerQueueInterceptor"
    }
}
