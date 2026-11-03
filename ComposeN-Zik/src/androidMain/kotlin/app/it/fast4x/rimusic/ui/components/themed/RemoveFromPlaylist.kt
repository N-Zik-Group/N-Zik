package app.it.fast4x.rimusic.ui.components.themed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog

class RemoveFromPlaylist private constructor(
    activeState: MutableState<Boolean>,
    menuState: MenuState,
    private val onConfirmAction: () -> Unit
) : DeleteDialog(activeState, menuState) {

    companion object {
        @Composable
        operator fun invoke(
            onConfirmAction: () -> Unit
        ) = RemoveFromPlaylist(
            remember { mutableStateOf(false) },
            LocalMenuState.current,
            onConfirmAction
        )
    }

    override val confirmationTitle: String
        @Composable get() = stringResource(R.string.playlist_action_remove_songs)
    override val confirmationAction: String
        @Composable get() = stringResource(R.string.common_action_remove)

    override val messageId: Int = R.string.playlist_action_remove_songs

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.playlist_remove_selected_confirmation)

    override fun onConfirm() {
        onConfirmAction()
        onDismiss()
    }
}
