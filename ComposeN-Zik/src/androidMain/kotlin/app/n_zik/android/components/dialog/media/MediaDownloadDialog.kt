package app.n_zik.android.components.dialog.media

import app.n_zik.android.core.database.*

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import timber.log.Timber

@UnstableApi
abstract class MediaDownloadDialog(
    activeState: MutableState<Boolean>,
    val getSongs: () -> List<Song>,
    private val binder: PlayerServiceModern.Binder?,
): ConfirmDialog {

    override var isActive: Boolean by activeState

    abstract fun onAction( media: Song )

    open fun onBatchStart( count: Int ) {}

    override fun onConfirm() {
        val allSongs = getSongs()
        val songsToDownload = allSongs.filter { song ->
            val isDownloaded = MyDownloadHelper.isSongDownloaded(song.id)
            if (isDownloaded) {
                Timber.tag("MediaDownloadDialog").d("Skipping already downloaded: ${song.id}")
            }
            !isDownloaded
        }

        val skippedCount = allSongs.size - songsToDownload.size

        if (songsToDownload.isEmpty()) {
            Timber.tag("MediaDownloadDialog").d("All songs already downloaded, nothing to do")
            onDismiss()
            return
        }

        onBatchStart(songsToDownload.size)

        // Count skipped songs toward batch progress
        if (skippedCount > 0) {
            MyDownloadHelper.skipBatchCompleted(skippedCount)
        }

        songsToDownload.forEach {
            // binder has to be non-null for remove from cache to work
            if( binder == null ) return
            binder.cache.removeResource( it.id )

            Database.asyncTransaction {
                formatTable.deleteBySongId( it.id )
            }

            onAction( it )
        }

        onDismiss()
    }
}

