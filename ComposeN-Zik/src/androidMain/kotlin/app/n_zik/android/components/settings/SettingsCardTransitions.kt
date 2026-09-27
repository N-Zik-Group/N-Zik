package app.n_zik.android.components.settings

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * Shared enter/exit specs for the entries and sub-blocks that appear or disappear
 * inside the n_zik settings cards (Rewind notifications entries, Last.fm scrobbling
 * blocks). Mirrors the [SettingsSectionCard] helper's own card animation so a card
 * and its entries move with the same motion: expand/collapse + fade. The collapse
 * to zero height is what lets the surrounding entries and sibling cards slide
 * naturally instead of jumping.
 *
 * Compose Animation 1.12 dropped the legacy `Transition` builders: `collapseVertically`
 * is `shrinkVertically`, and both are top-level functions (there is no `transitionBuilder`).
 */
internal val settingsEntryEnter: EnterTransition =
    expandVertically(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
internal val settingsEntryExit: ExitTransition =
    shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
