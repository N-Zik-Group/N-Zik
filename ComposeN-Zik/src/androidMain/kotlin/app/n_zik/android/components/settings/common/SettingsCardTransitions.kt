package app.n_zik.android.components.settings.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassOverflowFade
import app.n_zik.android.components.glass.isGlassMotionAllowed
import app.n_zik.android.components.glass.mode
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.menu.menuPageInteraction

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

internal fun GlassSettings.settingsControlsMayOverflow(): Boolean =
    usesGlassMaterial(GlassArea.ActionButtons) || (enabled && mode(GlassArea.Switches) != GlassAreaMode.Disabled)

@Composable
internal fun SettingsEntryVisibility(visible: Boolean, content: @Composable () -> Unit) {
    if (!isGlassMotionAllowed()) {
        if (visible) Column { content() }
        return
    }
    val overflow = rememberGlassSettings().settingsControlsMayOverflow()
    AnimatedVisibility(
        visible = visible,
        enter = if (overflow) settingsCardOverflowEnter else settingsEntryEnter,
        exit = if (overflow) settingsCardOverflowExit else settingsEntryExit,
    ) {
        val interaction = Modifier.menuPageInteraction(visible)
        val fade = if (overflow) glassOverflowFade(SETTINGS_CARD_ENTER_MILLIS, SETTINGS_CARD_EXIT_MILLIS) else Modifier
        Column(interaction.then(fade)) {
            content()
        }
    }
}
