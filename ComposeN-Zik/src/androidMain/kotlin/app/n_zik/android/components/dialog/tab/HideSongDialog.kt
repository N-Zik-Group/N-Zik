package app.n_zik.android.components.dialog.tab

import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState

@UnstableApi
class HideSongDialog private constructor(
    activeState: MutableState<Boolean>,
    menuState: MenuState,
    private val binder: PlayerServiceModern.Binder?
) : DeleteSongDialog(activeState, menuState, binder) {

    companion object {
        @Composable
        operator fun invoke() = HideSongDialog(
            remember { mutableStateOf(false) },
            LocalMenuState.current,
            LocalPlayerServiceBinder.current
        )
    }

    override val confirmationTitle: String
        @Composable get() = stringResource(R.string.dialog_hide_song)
    override val confirmationAction: String
        @Composable get() = stringResource(R.string.hide)

    override val messageId: Int = R.string.hide
    override val iconId: Int = R.drawable.eye_off
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.hidesong )

    override fun onConfirm() {
        song.ifPresent {
            Database.asyncTransaction {
                menuState.hide()
                binder?.cache?.removeResource( it.id )
                binder?.downloadCache?.removeResource( it.id )
                formatTable.updateContentLengthOf( it.id )
                songTable.updateTotalPlayTime( it.id, 0 )
            }
        }

        onDismiss()
    }
}

