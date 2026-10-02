package app.n_zik.android.components.player

import androidx.compose.ui.unit.Dp

/**
 * Fade alpha of the mini-player while the sheet travels through the dismissed zone.
 *
 * 1f at [collapsedBound] (fully visible) down to 0f at [dismissedBound] (fully faded),
 * linear in between. Because it is driven by the sheet's animated value, it follows the
 * finger frame by frame on a dismiss drag (depop) and fades back in on an animated pop,
 * without ever decoupling from the sheet's existing translation.
 *
 * @param value The sheet's current animated value, between the dismissed and expanded bounds.
 * @param dismissedBound The sheet's lower bound, where the mini-player is fully hidden.
 * @param collapsedBound The collapsed bound, where the mini-player is fully visible.
 * @return 1f when [collapsedBound] <= [dismissedBound] (degenerate geometry, no division by
 *   zero); otherwise [value]'s linear fade across the dismissed zone, clamped to [0, 1].
 */
internal fun miniPlayerDismissAlpha(
    value: Dp,
    dismissedBound: Dp,
    collapsedBound: Dp,
): Float {
    if (collapsedBound <= dismissedBound) return 1f
    return ((value - dismissedBound) / (collapsedBound - dismissedBound)).coerceIn(0f, 1f)
}

/**
 * Whether the player sheet subtree must stay composed.
 *
 * Composed while media is present, and also while the sheet has not reached its dismissed
 * bound yet: once media goes away the animated dismiss still has to play out (slide + the
 * [miniPlayerDismissAlpha] fade) before the subtree can be removed, otherwise it is cut short.
 */
internal fun shouldComposePlayerSheet(mediaPresent: Boolean, isDismissed: Boolean): Boolean =
    mediaPresent || !isDismissed
