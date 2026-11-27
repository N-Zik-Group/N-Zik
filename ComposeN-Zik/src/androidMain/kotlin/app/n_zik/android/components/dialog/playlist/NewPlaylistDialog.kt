package app.n_zik.android.components.dialog.playlist

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.appTextResources
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialogConstraints
import app.n_zik.android.components.dialog.common.TextInputDialog
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.models.Playlist
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getSyncDirection
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.innertube.YtMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class NewPlaylistDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val suggestedNamePrefix: String,
    private val localOnly: Boolean,
    private val onPlaylistCreated: ((Playlist) -> Unit)?,
): TextInputDialog(InputDialogConstraints.ALL, focusOnOpen = true), MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke(
            localOnly: Boolean = false,
            onPlaylistCreated: ((Playlist) -> Unit)? = null,
        ): NewPlaylistDialog {
            return NewPlaylistDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue() )
                },
                stringResource(R.string.playlist_action_create),
                localOnly,
                onPlaylistCreated
            )
        }
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.add_in_playlist
    override val messageId: Int = R.string.playlist_create_dialog_title
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.playlist_name_input_hint)
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.playlist_action_create )

    override var value: TextFieldValue by valueState
    override var isActive: Boolean by activeState

    override fun onShortClick() = showDialog()

    override fun showDialog() {
        if (isActive) return

        val name = "$suggestedNamePrefix ${(1000..9999).random()}"
        value = TextFieldValue(text = name, selection = TextRange(0, name.length))
        errorMessage = ""
        super.showDialog()
    }

    override fun hideDialog() {
        super.hideDialog()
        value = TextFieldValue()
    }

    @Composable
    override fun LeadingIcon() = Icon(
        imageVector = Icons.Outlined.Edit,
        tint = colorPalette().accent,
        contentDescription = stringResource(R.string.accessibility_new_playlist_name)
    )

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        if (RewindPlaylists.isReservedName(newValue)) {
            errorMessage = appTextResources().getString(R.string.playlist_name_reserved)
            return
        }

        val context = appContext()
        val owner = ProfileWorkOwner.capture(context) ?: return
        val createOnYouTube = !localOnly && isYouTubeSyncEnabled() &&
            context.preferences.getBoolean(syncPushPlaylistKey, false) &&
            getSyncDirection() != SyncDirection.YT_TO_APP && isNetworkConnected(context)
        hideDialog()
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            val playlist = try {
                withContext(NzikDispatchers.DATA) {
                    if (!ProfileWorkOwner.isBound(context, owner)) return@withContext null
                    val browseId = if (createOnYouTube) {
                        YtMusic.createPlaylist(newValue).onFailure {
                            if (it is CancellationException) throw it
                        }.getOrNull()
                    } else null
                    val draft = Playlist(
                        name = newValue,
                        browseId = browseId,
                        isYoutubePlaylist = browseId != null,
                    )
                    Database.awaitWriteBatch {
                        if (ProfileWorkOwner.isBound(context, owner)) draft.copy(id = playlistTable.insert(draft))
                        else null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("NewPlaylistDialog").e(error, "Failed to store new playlist")
                Toaster.e(R.string.common_error_title)
                return@launch
            }
            if (playlist == null || !ProfileWorkOwner.isBound(context, owner)) return@launch
            if (onPlaylistCreated != null) onPlaylistCreated.invoke(playlist) else Toaster.done()
        }
    }
}
