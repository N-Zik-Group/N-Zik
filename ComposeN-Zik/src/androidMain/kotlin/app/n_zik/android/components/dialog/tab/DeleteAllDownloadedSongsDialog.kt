package app.n_zik.android.components.dialog.tab

import app.n_zik.android.appTextResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.appContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import app.n_zik.android.models.Song
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.components.dialog.media.MediaDownloadDialog
import timber.log.Timber

@UnstableApi
class DeleteAllDownloadedSongsDialog(
    pendingSongs: MutableState<List<Song>?>,
    getSongs: () -> List<Song>,
    binder: PlayerServiceModern.Binder?,
    private val removableSongIds: () -> Set<String> = ::downloadRequestIds,
) : MediaDownloadDialog(pendingSongs, getSongs, binder), MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke(getSongs: () -> List<Song>) = invoke(getSongs, ::downloadRequestIds)

        @Composable
        operator fun invoke(
            getSongs: () -> List<Song>,
            removableSongIds: () -> Set<String>,
        ) =
            DeleteAllDownloadedSongsDialog(
                remember { mutableStateOf(null) },
                getSongs,
                LocalPlayerServiceBinder.current,
                removableSongIds,
            )
    }

    override val confirmationTitle: String
        @Composable get() = stringResource(R.string.downloads_delete_dialog_title)
    override val confirmationAction: String
        @Composable get() = stringResource(R.string.common_action_delete)

    override val messageId: Int = R.string.downloads_action_remove_all
    override val iconId: Int = R.drawable.download
    override val dialogTitle: String
        @Composable
        get() {
            val requestIds = removableSongIds()
            val count = confirmationSongs.count { it.id in requestIds }
            return if( count > 0 )
                stringResource( R.string.downloads_delete_all_count_confirmation, count )
            else
                stringResource( R.string.downloads_delete_all_confirmation )
        }
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        val songs = getSongs()
        val requestIds = removableSongIds()
        if (songs.any { it.id in requestIds }) {
            openForSongs(songs)
        } else {
            app.n_zik.android.core.utils.Toaster.toast(
                appTextResources().getString(R.string.downloads_nothing_to_delete),
                type = app.n_zik.android.core.utils.Toaster.Type.INFO
            )
        }
    }

    override fun onConfirm() {
        val requestIds = removableSongIds()
        val downloadedSongs = confirmationSongs.filter { it.id in requestIds }

        if (downloadedSongs.isEmpty()) {
            Timber.tag("DeleteAllDownloadedSongsDialog").d("No downloaded songs to delete")
            onDismiss()
            return
        }

        onDismiss()
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            downloadedSongs.forEach {
                MyDownloadHelper.removeDownload(appContext(), it.asMediaItem)
            }
        }
    }
}

@UnstableApi
private fun downloadRequestIds(): Set<String> = MyDownloadHelper.downloads.value.keys
