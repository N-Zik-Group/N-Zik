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
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.syncPushPlaylistKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.getSyncDirection
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.enums.SyncDirection
import app.n_zik.android.appContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.utils.Toaster
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

        if (isYouTubeSyncEnabled()) {
            val pushPlaylist = appContext().preferences.getBoolean(syncPushPlaylistKey, false)
            val syncDirection = getSyncDirection()
            if (pushPlaylist && syncDirection != SyncDirection.YT_TO_APP && isNetworkConnected(appContext())) {
                hideDialog()
                coroutineScope.launch(Dispatchers.IO) {
                    val playlist = runCatching {
                        YtMusic.createPlaylist(newValue).getOrNull()?.let { browseId ->
                            Playlist(
                                name = newValue,
                                browseId = browseId,
                                isYoutubePlaylist = true,
                                isEditable = true
                            )
                        }
                    }.onFailure { e ->
                        Timber.tag("NewPlaylistDialog").e(e, "Failed to create playlist on YouTube")
                        Toaster.e( R.string.error )
                    }.getOrNull() ?: Playlist(name = newValue)

                    Database.asyncTransaction {
                        val newId = playlistTable.insert( playlist )
                        onPlaylistCreated(playlist.copy(id = newId))
                    }
                    Toaster.s( R.string.added_to_favorites )
                    Timber.tag("NewPlaylistDialog").d("Playlist created: ${playlist.name}, browseId=${playlist.browseId}")
                }
            } else {
                hideDialog()
                createLocalPlaylist(newValue)
            }
        } else {
            hideDialog()
            createLocalPlaylist(newValue)
        }
    }

    private fun createLocalPlaylist(name: String) {
        val playlist = Playlist(name = name)
        Database.asyncTransaction {
            val newId = playlistTable.insert( playlist )
            onPlaylistCreated(playlist.copy(id = newId))
        }
        Toaster.s( R.string.added_to_favorites )
    }
}
