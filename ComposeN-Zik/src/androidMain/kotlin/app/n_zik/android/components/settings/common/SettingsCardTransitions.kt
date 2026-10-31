package app.n_zik.android.components.settings.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * Shared enter/exit specs for blocks appearing/disappearing inside the n_zik settings cards.
 * Collapsing to zero height lets surrounding entries and sibling cards slide instead of jumping.
 */
internal val settingsEntryEnter: EnterTransition =
    expandVertically(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
internal val settingsEntryExit: ExitTransition =
    shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))

internal val settingsCardEnter: EnterTransition =
    expandVertically(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
internal val settingsCardExit: ExitTransition =
    shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))

/** Card controls keep their spring envelope while their owning card enters or exits. */
internal val settingsCardOverflowEnter: EnterTransition =
    expandVertically(animationSpec = tween(400), clip = false)
internal val settingsCardOverflowExit: ExitTransition =
    shrinkVertically(animationSpec = tween(200), clip = false)
