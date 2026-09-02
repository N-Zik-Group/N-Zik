package app.n_zik.android.components.dialog.playlist

import app.n_zik.android.core.database.*

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.R
import it.fast4x.innertube.YtMusic
import app.n_zik.android.core.database.Database

import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.n_zik.android.components.dialog.common.InputDialogConstraints
import app.n_zik.android.components.dialog.common.TextInputDialog
import timber.log.Timber

class NewPlaylistDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val onPlaylistCreated: (Playlist) -> Unit = {}
): TextInputDialog(InputDialogConstraints.ALL), MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke(onPlaylistCreated: (Playlist) -> Unit = {}): NewPlaylistDialog {
            val coroutineScope = rememberCoroutineScope()
            return NewPlaylistDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue() )
                },
                coroutineScope,
                onPlaylistCreated
            )
        }
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.add_in_playlist
    override val messageId: Int = R.string.create_new_playlist
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.enter_the_playlist_name)
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.new_playlist )

    override var value: TextFieldValue by valueState
    override var isActive: Boolean by activeState

    override fun onShortClick() = showDialog()

    override fun hideDialog() {
        super.hideDialog()
        // TODO: Add a random name generator here
        value = value.copy( "" )
    }

    @Composable
    override fun LeadingIcon() = Icon(
        imageVector = Icons.Outlined.Edit,
        tint = colorPalette().accent,
        contentDescription = stringResource(R.string.cd_new_playlist_name)
    )

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        var playlist: Playlist? = null

        if (isYouTubeSyncEnabled()) {
            coroutineScope.launch(Dispatchers.IO) {
                YtMusic.createPlaylist(newValue)
                       .getOrNull()
                       .also {
                           playlist = Playlist(
                               name = newValue,
                               browseId = it,
                               isYoutubePlaylist = true,
                               isEditable = true
                           )
                            Timber.tag("NewPlaylistDialog").d("Innertube YtMusic createPlaylist: $it")
                       }
            }
        } else {
            playlist = Playlist(name = newValue)
        }

        playlist?.let {
            Database.asyncTransaction {
                val newId = playlistTable.insert( it )
                onPlaylistCreated(it.copy(id = newId))
            }
        }

        hideDialog()
    }
}
