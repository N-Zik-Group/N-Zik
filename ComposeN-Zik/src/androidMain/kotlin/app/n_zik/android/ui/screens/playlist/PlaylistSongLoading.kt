package app.n_zik.android.ui.screens.playlist

import androidx.compose.ui.util.fastDistinctBy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.PlaylistContinuationPage
import app.n_zik.innertube.requests.PlaylistPage

internal suspend fun loadPlaylistFirstPage(browseId: String): Result<PlaylistPage> =
    withContext(NzikDispatchers.DATA) {
        YtMusic.getPlaylist(browseId.removePrefix(MODIFIED_PREFIX))
    }

internal suspend fun loadPlaylistContinuations(
    firstPage: PlaylistPage,
    requestNextPage: suspend (String) -> Result<PlaylistContinuationPage> = { token ->
        withContext(NzikDispatchers.DATA) { YtMusic.getPlaylistContinuation(token) }
    },
    onSongsLoaded: (List<Innertube.SongItem>) -> Unit = {},
): PlaylistPage {
    val tracks = firstPage.songs.toMutableList()
    val attemptedTokens = mutableSetOf<String>()
    var continuation = firstPage.songsContinuation
    while (continuation != null && attemptedTokens.size < 50 && attemptedTokens.add(continuation)) {
        val result = requestNextPage(continuation)
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val next = result.getOrNull() ?: break
        tracks += next.songs
        onSongsLoaded(next.songs)
        continuation = next.continuation
    }
    return firstPage.copy(songs = tracks.fastDistinctBy { it.key to it.explicit }, songsContinuation = continuation)
}
