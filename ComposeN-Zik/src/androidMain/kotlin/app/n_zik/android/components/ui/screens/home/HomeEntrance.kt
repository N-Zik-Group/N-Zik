package app.n_zik.android.components.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay

/** Keeps the initial service grace period from replaying when returning to Home. */
@Composable
internal fun rememberHomeEntranceReady(serviceKey: Any?): State<Boolean> {
    val ready = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(serviceKey) {
        if (!ready.value) {
            delay(100)
            ready.value = true
        }
    }
    return ready
}
