package app.n_zik.android.components.playlist

import app.n_zik.android.core.database.*
import app.n_zik.android.playback.utils.Shuffler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.models.SongPlaylistMap
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.ConfirmDialog
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.isAtLeastAndroid14
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.core.utils.Toaster

class Reposition private constructor(
    activeState: MutableState<Boolean>,
    private val menuState: MenuState,
    private val playlistId: Long
): ConfirmDialog, MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( playlistId: Long ): Reposition =
            Reposition(
                remember { mutableStateOf( false ) },
                LocalMenuState.current,
                playlistId
            )
    }

    override val confirmationTitle: String
        @Composable get() = stringResource(R.string.playlist_action_shuffle_positions)
    override val confirmationAction: String
        @Composable get() = stringResource(R.string.player_action_shuffle)

    override val messageId: Int = R.string.playlist_action_shuffle_positions
    override val iconId: Int = R.drawable.position
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.playlist_shuffle_positions_confirmation )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isActive: Boolean by activeState

    override fun onShortClick() = super.onShortClick()

    override fun onConfirm() {
        Shuffler.positions( playlistId )
        onDismiss()
        menuState.hide()
    }
}

