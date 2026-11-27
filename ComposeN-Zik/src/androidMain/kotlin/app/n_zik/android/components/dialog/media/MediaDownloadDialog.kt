package app.n_zik.android.components.dialog.media

import android.content.Context
import kotlinx.coroutines.launch

import androidx.compose.runtime.MutableState
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.download.utils.completedDownloadRequestIds
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.components.tab.toolbar.ConfirmDialog
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.coroutines.NzikDispatchers
import timber.log.Timber

@UnstableApi
abstract class MediaDownloadDialog(
    private val pendingSongs: MutableState<List<Song>?>,
    protected val getSongs: () -> List<Song>,
    private val binder: PlayerServiceModern.Binder?,
): ConfirmDialog {

    override var isActive: Boolean
        get() = pendingSongs.value != null
        set(value) {
            if (value) {
                if (!isActive) openForSongs(getSongs())
            } else {
                pendingSongs.value = null
            }
        }

    protected val confirmationSongs: List<Song>
        get() = pendingSongs.value.orEmpty()

    protected fun openForSongs(songs: List<Song>) {
        pendingSongs.value = songs.toList()
    }

    open fun onBatchStart( count: Int ) {}

    override fun onConfirm() {
        val allSongs = confirmationSongs
        val downloadedIds = MyDownloadHelper.downloads.value.completedDownloadRequestIds()
        val songsToDownload = allSongs.filter { song ->
            val isDownloaded = song.id in downloadedIds
            if (isDownloaded) {
                Timber.tag("MediaDownloadDialog").d("Skipping already downloaded: ${song.id}")
            }
            !isDownloaded
        }

        if (songsToDownload.isEmpty()) {
            Timber.tag("MediaDownloadDialog").d("All songs already downloaded, nothing to do")
            onDismiss()
            return
        }

        if (binder == null) {
            Toaster.e(R.string.common_error_title)
            return
        }

        val cache = binder.cache
        onBatchStart(songsToDownload.size)
        onDismiss()
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runAdmittedDownloadBatch(appContext(), songsToDownload, cache)
        }
    }
}

/** Runs song-row download toggles with their existing cache preparation. */
@UnstableApi
internal suspend fun runDownloadBatch(
    songs: List<Song>,
    cache: Cache?,
    onAction: ( Song ) -> Unit
) {
    if (songs.isEmpty()) return
    Database.awaitWriteBatch(retries = 1) {
        songs.forEach { song ->
            prepareSongDownload(song, cache)
            onAction(song)
        }
    }
}

/** Prepares each track only when the download helper has admitted it. */
@UnstableApi
internal suspend fun runAdmittedDownloadBatch(context: Context, songs: List<Song>, cache: Cache?) {
    if (songs.isEmpty()) return
    Database.awaitWriteBatch(retries = 1) {
        songs.forEach { song ->
            MyDownloadHelper.addDownloadInBatch(context, song.asMediaItem) {
                prepareSongDownload(song, cache)
            }
        }
    }
}

@UnstableApi
private suspend fun prepareSongDownload(song: Song, cache: Cache?) {
    cache?.removeResource(song.id)
    Database.awaitWriteBatch {
        formatTable.deleteBySongId(song.id)
    }
    MyDownloadHelper.songUrlCache.invalidate(song.id)
}

