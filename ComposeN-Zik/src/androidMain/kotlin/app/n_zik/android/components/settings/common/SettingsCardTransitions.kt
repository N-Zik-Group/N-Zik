package app.n_zik.android.components.settings.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

internal const val SETTINGS_CARD_ENTER_MILLIS = 280
internal const val SETTINGS_CARD_EXIT_MILLIS = 200

/**
 * Shared enter/exit specs for blocks appearing/disappearing inside the n_zik settings cards.
 * Collapsing to zero height lets surrounding entries and sibling cards slide instead of jumping.
 */
internal val settingsEntryEnter: EnterTransition =
    expandVertically(animationSpec = tween(SETTINGS_CARD_ENTER_MILLIS)) +
        fadeIn(animationSpec = tween(SETTINGS_CARD_ENTER_MILLIS))
internal val settingsEntryExit: ExitTransition =
    shrinkVertically(animationSpec = tween(SETTINGS_CARD_EXIT_MILLIS)) +
        fadeOut(animationSpec = tween(SETTINGS_CARD_EXIT_MILLIS))

internal val settingsCardEnter: EnterTransition = settingsEntryEnter
internal val settingsCardExit: ExitTransition = settingsEntryExit

/** Card controls keep their spring envelope while their owning card enters or exits. */
internal val settingsCardOverflowEnter: EnterTransition =
    expandVertically(animationSpec = tween(SETTINGS_CARD_ENTER_MILLIS), clip = false)
internal val settingsCardOverflowExit: ExitTransition =
    shrinkVertically(animationSpec = tween(SETTINGS_CARD_EXIT_MILLIS), clip = false)
