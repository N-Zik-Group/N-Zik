package app.n_zik.android.components.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import app.n_zik.android.ui.screens.player.PlayerSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MINIPLAYER_AUTOEXPAND_DELAY_MS = 800L

/**
 * Presents the mini-player immediately, then auto-expands the player to full screen after [delayMs].
 *
 * Animates the sheet to its collapsed bound right away so the user sees the mini-player bar
 * slide into place, then defers the full-screen expansion so the
 * heavy full-player render happens inside the existing animated transition rather than at
 * initial appearance.
 *
 * @param sheetState the player sheet state to collapse and expand
 * @param delayMs delay between the collapsed presentation and the auto-expansion
 * @param onPresent optional hook restoring the surrounding UI state (e.g. hidden nav bars)
 *   before the mini-player is presented; invoked synchronously, before [PlayerSheetState.collapse]
 */
fun CoroutineScope.presentMiniplayerThenExpand(
    sheetState: PlayerSheetState,
    delayMs: Long = MINIPLAYER_AUTOEXPAND_DELAY_MS,
    onPresent: (() -> Unit)? = null,
) {
    onPresent?.invoke()
    sheetState.collapse(miniPlayerAppearanceSpring())
    launch {
        delay(delayMs)
        sheetState.expandSoft()
    }
}

/** Brings back the mini-player when media arrives, including during a dismiss animation. */
fun PlayerSheetState.showMiniplayerIfDismissed() {
    if (value < collapsedBound) collapse(miniPlayerAppearanceSpring())
}

/**
 * Puts the sheet at its collapsed bound for a "keep the player minimized" presentation.
 *
 * Coming from the dismissed zone the mini-player appears: it slides in via [showMiniplayerIfDismissed].
 * From above the collapsed bound (an open player) it still snaps down.
 */
fun PlayerSheetState.presentMiniplayerCollapsed() {
    if (value < collapsedBound) showMiniplayerIfDismissed() else snapTo(collapsedBound)
}

private fun miniPlayerAppearanceSpring() =
    spring<Dp>(stiffness = Spring.StiffnessMediumLow)
