package app.n_zik.android.components.dialog.media

import androidx.media3.datasource.cache.Cache
import app.n_zik.android.core.database.*
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.components.tab.toolbar.ConfirmDialog
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

        // binder has to be non-null for remove from cache to work
        if( binder == null ) return

        val cache = binder.cache
        onDismiss()
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runDownloadBatch( songsToDownload, cache ) { onAction( it ) }
        }
    }
}

/**
 * Runs a download dialog's cache-eviction + per-song [onAction] batch (issue #606 H5).
 * One scope per batch, never one per song. The caller guarantees a non-null cache by
 * checking the binder before launching; a null [cache] is still a safe no-op.
 *
 * @param songs the batch to process
 * @param cache the streaming cache to evict each song from, may be null
 * @param onAction the per-song action (e.g. start a download)
 */
@UnstableApi
internal suspend fun runDownloadBatch(
    songs: List<Song>,
    cache: Cache?,
    onAction: ( Song ) -> Unit
) {
    songs.forEach { song ->
        cache?.removeResource( song.id )

        Database.asyncTransaction {
            formatTable.deleteBySongId( song.id )
        }

        onAction( song )
    }
}

