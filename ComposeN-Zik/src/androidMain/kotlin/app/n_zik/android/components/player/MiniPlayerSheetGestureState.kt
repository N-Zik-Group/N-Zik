package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

internal data class MiniPlayerSheetGestureState(val isOpening: Boolean, val isAtRest: Boolean)

/** Observe gesture boundaries, not every animation frame (negative progress is dismissal). */
@Composable
internal fun rememberMiniPlayerSheetGestureState(progress: () -> Float): State<MiniPlayerSheetGestureState> =
    remember(progress) {
        derivedStateOf {
            val value = progress()
            MiniPlayerSheetGestureState(isOpening = value > 0f, isAtRest = value == 0f)
        }
    }
