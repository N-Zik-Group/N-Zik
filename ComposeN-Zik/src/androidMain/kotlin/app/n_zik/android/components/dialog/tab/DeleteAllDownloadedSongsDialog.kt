package app.n_zik.android.components.dialog.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.asMediaItem
import app.n_zik.android.components.dialog.media.MediaDownloadDialog
import timber.log.Timber

@UnstableApi
class DeleteAllDownloadedSongsDialog(
    activeState: MutableState<Boolean>,
    getSongs: () -> List<Song>,
    binder: PlayerServiceModern.Binder?
) : MediaDownloadDialog(activeState, getSongs, binder), MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( getSongs: () -> List<Song> ) =
            DeleteAllDownloadedSongsDialog(
                remember { mutableStateOf(false) },
                getSongs,
                LocalPlayerServiceBinder.current
            )
    }

    override val messageId: Int = R.string.info_remove_all_downloaded_songs
    override val iconId: Int = R.drawable.download
    override val dialogTitle: String
        @Composable
        get() {
            val count = getSongs().count { song -> MyDownloadHelper.downloads.value.containsKey(song.id) }
            return if( count > 0 )
                stringResource( R.string.do_you_really_want_to_delete_download_count, count )
            else
                stringResource( R.string.do_you_really_want_to_delete_download )
        }
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        val count = getSongs().count { song -> MyDownloadHelper.downloads.value.containsKey(song.id) }
        if (count > 0) {
            super.onShortClick()
        } else {
            app.kreate.android.me.knighthat.utils.Toaster.toast(
                appContext().getString(R.string.nothing_to_delete),
                type = app.kreate.android.me.knighthat.utils.Toaster.Type.INFO
            )
        }
    }

    override fun onConfirm() {
        // Reverse filter: process both completed and ongoing downloads for deletion
        val downloadedSongs = getSongs().filter { song ->
            MyDownloadHelper.downloads.value.containsKey(song.id)
        }

        if (downloadedSongs.isEmpty()) {
            Timber.tag("DeleteAllDownloadedSongsDialog").d("No downloaded songs to delete")
            onDismiss()
            return
        }

        downloadedSongs.forEach {
            MyDownloadHelper.removeDownload( appContext(), it.asMediaItem )
        }

        onDismiss()
    }

    override fun onAction( media: Song ) =
        MyDownloadHelper.removeDownload( appContext(), media.asMediaItem )
}


