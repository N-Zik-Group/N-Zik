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
import app.it.fast4x.rimusic.models.SongPlaylistMap
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.isAtLeastAndroid14
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.kreate.android.me.knighthat.utils.Toaster

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
        @Composable get() = stringResource(R.string.renumber_songs_positions)
    override val confirmationAction: String
        @Composable get() = stringResource(R.string.shuffle)

    override val messageId: Int = R.string.renumber_songs_positions
    override val iconId: Int = R.drawable.position
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.do_you_really_want_to_renumbering_positions_in_this_playlist )
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

