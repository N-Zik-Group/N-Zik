package app.n_zik.android.components.player

import androidx.compose.animation.core.tween
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MINIPLAYER_AUTOEXPAND_DELAY_MS = 800L

/** Duration of the animated pop that brings the mini-player back from dismissed to collapsed. */
const val MINIPLAYER_POP_ANIMATION_MS = 250L

/** Fade-in duration of the sheet subtree (mini-player) when it appears with new media. */
const val MINIPLAYER_APPEAR_FADE_MS = 300L

/**
 * Frames awaited before the appearance fade starts, so the heavy first composition of the
 * mini-player (composed once the sheet leaves the dismissed bound) does not swallow the fade.
 */
const val MINIPLAYER_APPEAR_FADE_SKIPPED_FRAMES = 2

/**
 * Presents the mini-player immediately, then auto-expands the player to full screen after [delayMs].
 *
 * Animates the sheet to its collapsed bound right away so the user sees the mini-player bar
 * (fading in with it) instead of a blank phase, then defers the full-screen expansion so the
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
    sheetState.collapse(tween(MINIPLAYER_POP_ANIMATION_MS.toInt()))
    launch {
        delay(delayMs)
        sheetState.expandSoft()
    }
}

/**
 * Brings the mini-player back when media is available but the sheet is in the dismissed zone.
 *
 * A UI preference change recreates the activity: the player service is not bound yet, so media
 * reads as absent and the sheet is dismissed, and nothing presents it again once media returns.
 * Call this when media is present; a sheet that is showing or expanded is left untouched.
 * The pop is animated (a tween on the sheet value), so the mini-player fades + slides into
 * place instead of snapping there.
 *
 * The condition is the sheet value, not [isDismissed]: a dismiss animation still in flight
 * has not reached the dismissed bound, and a stop-then-quick-resume must still restore the
 * mini-player instead of letting that animation settle to a hidden sheet.
 */
fun PlayerSheetState.showMiniplayerIfDismissed() {
    if (value < collapsedBound) collapse(tween(MINIPLAYER_POP_ANIMATION_MS.toInt()))
}

/**
 * Puts the sheet at its collapsed bound for a "keep the player minimized" presentation.
 *
 * Coming from the dismissed zone the mini-player appears: it pops with the animated collapse
 * (see [showMiniplayerIfDismissed]) so the dismissed-zone alpha fades it in instead of making it
 * appear in one frame. From above the collapsed bound (an open player) it still snaps down.
 */
fun PlayerSheetState.presentMiniplayerCollapsed() {
    if (value < collapsedBound) showMiniplayerIfDismissed() else snapTo(collapsedBound)
}
