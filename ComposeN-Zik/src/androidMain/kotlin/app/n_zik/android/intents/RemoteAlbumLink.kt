package app.n_zik.android.intents

import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.playlistPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

internal suspend fun remoteAlbumLinkRoute(playlistBrowseId: String): String = withContext(NzikDispatchers.DATA) {
    val page = Innertube.playlistPage(browseId = playlistBrowseId)
        ?.onFailure { if (it is CancellationException) throw it }
        ?.getOrNull()
    val candidateAlbums = page?.songsPage?.items.orEmpty().mapNotNull { it.album?.endpoint?.browseId }
    val albumId = selectVerifiedAlbumEdition(playlistBrowseId, candidateAlbums) { candidate ->
        YtMusic.getAlbum(candidate, withSongs = false)
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()?.album?.playlistId
    }
    if (albumId != null) "${NavRoutes.album.name}/$albumId" else "${NavRoutes.playlist.name}/$playlistBrowseId"
}

internal suspend fun selectVerifiedAlbumEdition(
    playlistBrowseId: String,
    candidateAlbums: List<String>,
    playlistForAlbum: suspend (String) -> String?,
): String? {
    val playlistId = playlistBrowseId.removePrefix("VL")
    for (candidate in candidateAlbums.asSequence().filter { it.startsWith("MPRE") }.distinct()) {
        if (playlistForAlbum(candidate)?.removePrefix("VL") == playlistId) return candidate
    }
    return null
}
