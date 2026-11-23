package app.n_zik.android.components.menu.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import app.n_zik.android.R
import app.n_zik.android.components.tab.StatefulMenuIcon
import app.n_zik.android.ui.components.MenuState

@Immutable
class PlayerStatsMenuControl(val isVisible: () -> Boolean, val onToggle: () -> Unit)

internal class PlayerStatsMenuIcon(
    menuState: MenuState,
    private val control: State<PlayerStatsMenuControl?>,
) : StatefulMenuIcon(
    menuState = menuState,
    messageId = R.string.player_action_show_stats_for_nerds,
    readActive = { control.value?.isVisible?.invoke() == true },
    readIcon = {
        if (control.value?.isVisible?.invoke() == true) R.drawable.bugs_stop else R.drawable.bugs
    },
    readTitle = {
        if (control.value?.isVisible?.invoke() == true) R.string.player_action_hide_stats_for_nerds
        else R.string.player_action_show_stats_for_nerds
    },
    performAction = { control.value?.onToggle?.invoke() },
    closeMenuAfterAction = true,
) {
    val isAvailable: Boolean get() = control.value != null
}
