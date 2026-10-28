package app.n_zik.android.components.search

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Registers only active sessions, after the destination's existing navigation callbacks. */
@Composable
internal fun SearchBackHandler(active: Boolean, onDismiss: () -> Unit) {
    if (!active) return
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentDismiss = rememberUpdatedState(onDismiss)
    val lifecycle = lifecycleOwner.lifecycle
    val callback = remember(dispatcher, lifecycleOwner) {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                isEnabled = false
                currentDismiss.value()
            }
        }
    }
    // A dismiss and reopen can share a frame without disposing the active composition.
    SideEffect {
        val enabled = lifecycle.currentState == Lifecycle.State.RESUMED
        if (callback.isEnabled != enabled) callback.isEnabled = enabled
    }
    DisposableEffect(dispatcher, lifecycleOwner, callback) {
        // STARTED also includes destinations animating out. Disable synchronously on pause.
        val observer = LifecycleEventObserver { _, _ ->
            callback.isEnabled = lifecycle.currentState == Lifecycle.State.RESUMED
        }
        dispatcher.addCallback(lifecycleOwner, callback)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            callback.remove()
        }
    }
}
