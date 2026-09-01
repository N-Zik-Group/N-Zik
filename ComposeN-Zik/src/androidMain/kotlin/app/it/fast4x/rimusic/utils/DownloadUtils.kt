package app.it.fast4x.rimusic.utils
import app.n_zik.android.LocalDownloadHelper

import app.n_zik.android.core.database.*


import app.n_zik.android.core.network.utils.isNetworkAvailable
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import androidx.compose.ui.unit.dp
import android.content.Context

const val DOWNLOAD_INDICATOR_SIZE_NORMAL = 18
const val DOWNLOAD_INDICATOR_SIZE_SWIPE = 20
const val DOWNLOAD_INDICATOR_STROKE_WIDTH = 2

@UnstableApi
@Composable
fun InitDownloader() {
    val context = LocalContext.current
    MyDownloadHelper.getDownloadManager(context)
    MyDownloadHelper.getDownloads()
}


@UnstableApi
@Composable
fun downloadedStateMedia(mediaId: String): DownloadedStateMedia {
    val binder = LocalPlayerServiceBinder.current

    val cachedBytes = remember(mediaId) {
        binder?.cache?.getCachedBytes(mediaId, 0, -1)
    }

    val isDownloaded by remember(mediaId) {
        MyDownloadHelper.getDownload(mediaId).map { download ->
            download?.state == Download.STATE_COMPLETED
        }
    }.collectAsStateWithLifecycle(initialValue = false)
    val isCached by remember {
        Database.formatTable.findBySongId( mediaId ).map {
            it?.contentLength == cachedBytes
        }
    }.collectAsStateWithLifecycle(initialValue = false)

    return when {
        isDownloaded && isCached -> DownloadedStateMedia.CACHED_AND_DOWNLOADED
        isDownloaded && !isCached -> DownloadedStateMedia.DOWNLOADED
        !isDownloaded && isCached -> DownloadedStateMedia.CACHED
        else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
    }
}

@UnstableApi
@Composable
fun getDownloadStateMedia(
    binder: PlayerServiceModern.Binder,
    songId: String
): DownloadedStateMedia {
    if( songId.startsWith( LOCAL_KEY_PREFIX, true ) )
        return DownloadedStateMedia.DOWNLOADED

    val isDownloaded by remember(songId) {
        MyDownloadHelper.getDownload(songId)
            .map { download -> download?.state == Download.STATE_COMPLETED }
    }.collectAsStateWithLifecycle(initialValue = false)
    val isCached by remember {
        Database.formatTable
            .findBySongId( songId )
            .map {
                if( it?.contentLength == null )
                    return@map false
                binder.cache.isCached( it.songId, 0, it.contentLength )
            }
    }.collectAsStateWithLifecycle(initialValue = false)

    return when {
        isDownloaded && isCached  -> DownloadedStateMedia.CACHED_AND_DOWNLOADED
        isDownloaded && !isCached -> DownloadedStateMedia.DOWNLOADED
        !isDownloaded && isCached -> DownloadedStateMedia.CACHED
        // !isDownloaded && !isCached
        else                      -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
    }
}

@UnstableApi
fun manageDownload(
    context: Context,
    mediaItem: MediaItem,
    downloadState: Boolean = false
) {

    if (mediaItem.isLocal) return

    if (downloadState) {
        MyDownloadHelper.removeDownload(context = context, mediaItem = mediaItem)
    }
    else {
        if (context.isNetworkAvailable) {
            MyDownloadHelper.addDownload(context = context, mediaItem = mediaItem)
        }
    }

}


@UnstableApi
@Composable
fun getDownloadState(mediaId: String): Int {
    val context = LocalContext.current
    val downloader = LocalDownloadHelper.current
    if (!isNetworkAvailableComposable()) return 3

    val downloadFlow = remember(mediaId) { downloader.getDownload(mediaId) }
    return downloadFlow.collectAsStateWithLifecycle(initialValue = null as Download?).value?.state
        ?: 3
}

@UnstableApi
@Composable
fun getDownloadProgress(mediaId: String): Float {
    val downloader = LocalDownloadHelper.current
    return downloader.progresses.collectAsStateWithLifecycle().value[mediaId] ?: 0f
}

@OptIn(UnstableApi::class)
@Composable
fun isDownloadedSong(mediaId: String): Boolean {
    return when (downloadedStateMedia(mediaId)) {
        DownloadedStateMedia.CACHED -> false
        DownloadedStateMedia.CACHED_AND_DOWNLOADED, DownloadedStateMedia.DOWNLOADED -> true
        else -> false
    }
}


