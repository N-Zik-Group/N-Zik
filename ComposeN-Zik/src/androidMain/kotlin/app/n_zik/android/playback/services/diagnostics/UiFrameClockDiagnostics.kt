package app.n_zik.android.playback.services.diagnostics

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Issue #881 (gh-881), Phase 3.1: lifecycle events after which the probe waits for the
 * first Compose frame. Compose's window recomposer pauses its frame clock on ON_STOP and
 * resumes it on ON_START — if the clock stays paused after a screen-off/on cycle, the
 * player UI stops recomposing (frozen bar, skip buttons seeking from a stale position)
 * while touches are still delivered, which is what the 2026-10-04 field logs show.
 */
internal fun shouldProbeFirstFrame(event: Lifecycle.Event): Boolean =
    event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME

/**
 * Issue #881 (gh-881), Phase 3.1 diagnostics: logs every activity lifecycle event and, after
 * ON_START / ON_RESUME, the delay until the next Compose frame (`UI_FRAME_RESUMED`). A
 * missing `UI_FRAME_RESUMED` after `SCREEN_ON` pins a paused frame clock. One frame request
 * per event — no per-frame loop, so no battery cost.
 */
@Composable
fun UiFrameClockDiagnostics() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        var probe: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            Timber.tag(PLAYBACK_DIAG_TAG).i("UI_LIFECYCLE event=%s", event.name)
            if (shouldProbeFirstFrame(event)) {
                probe?.cancel()
                val requestedAtMs = SystemClock.elapsedRealtime()
                probe = scope.launch {
                    withFrameMillis { }
                    Timber.tag(PLAYBACK_DIAG_TAG).i(
                        "UI_FRAME_RESUMED after=%s waitMs=%d",
                        event.name,
                        SystemClock.elapsedRealtime() - requestedAtMs,
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            probe?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
