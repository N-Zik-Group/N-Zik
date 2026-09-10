package app.n_zik.android.components.player

import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MINIPLAYER_AUTOEXPAND_DELAY_MS = 800L

/**
 * Presents the mini-player immediately, then auto-expands the player to full screen after [delayMs].
 *
 * Snaps the sheet to its collapsed bound right away so the user sees the mini-player bar
 * instead of a blank phase, then defers the full-screen expansion so the heavy full-player
 * render happens inside the existing animated transition rather than at initial appearance.
 *
 * @param sheetState the player sheet state to snap and expand
 * @param delayMs delay between the collapsed presentation and the auto-expansion
 */
fun CoroutineScope.presentMiniplayerThenExpand(
    sheetState: PlayerSheetState,
    delayMs: Long = MINIPLAYER_AUTOEXPAND_DELAY_MS,
) {
    sheetState.snapTo(sheetState.collapsedBound)
    launch {
        delay(delayMs)
        sheetState.expandSoft()
    }
}
