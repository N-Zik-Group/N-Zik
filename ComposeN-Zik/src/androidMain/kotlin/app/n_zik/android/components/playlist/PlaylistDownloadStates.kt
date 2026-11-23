package app.n_zik.android.components.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.utils.coroutines.NzikDispatchers

@Composable
internal fun rememberPlaylistDownloadStates(
    mediaIds: List<String>,
): Pair<Map<String, Download>, Map<String, DownloadedStateMedia>> {
    val downloads by MyDownloadHelper.downloads.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    val completedIds = remember(downloads) {
        downloads.values.asSequence()
            .filter { it.state == Download.STATE_COMPLETED }
            .map { it.request.id }
            .toSet()
    }
    val states = remember(mediaIds, completedIds) {
        mediaIds.associateWith { id ->
            if (id in completedIds) DownloadedStateMedia.DOWNLOADED
            else DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
        }
    }
    return downloads to states
}
