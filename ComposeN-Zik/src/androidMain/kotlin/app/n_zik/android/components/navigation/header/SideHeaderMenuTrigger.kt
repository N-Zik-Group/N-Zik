package app.n_zik.android.components.navigation.header

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.n_zik.android.components.glass.GlassMenuTriggerState

/** Root-owned identity lets a closing profile menu follow a replacement side header. */
internal val LocalSideHeaderMenuTrigger = staticCompositionLocalOf<GlassMenuTriggerState?> { null }

@Composable
internal fun rememberHeaderMenuTrigger(vertical: Boolean): GlassMenuTriggerState {
    val shared = LocalSideHeaderMenuTrigger.current
    val local = remember { GlassMenuTriggerState() }
    return if (vertical) shared ?: local else local
}
