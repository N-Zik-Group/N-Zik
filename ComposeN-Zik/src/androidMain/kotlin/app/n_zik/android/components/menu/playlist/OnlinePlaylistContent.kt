package app.n_zik.android.components.menu.playlist

import androidx.media3.common.util.UnstableApi
import app.n_zik.android.models.Song
import app.n_zik.android.utils.ExternalUris
import app.n_zik.android.utils.asPodcastMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.components.playlist.loadCompletePodcastPage
import app.n_zik.android.ui.screens.playlist.loadPlaylistContinuations
import app.n_zik.android.ui.screens.playlist.loadPlaylistFirstPage
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.PlaylistPage
import kotlinx.coroutines.CancellationException
import timber.log.Timber

internal data class OnlinePlaylistContent(
    val title: String?,
    val author: String?,
    val thumbnailUrl: String?,
    val songs: List<Song>?,
    val channel: Innertube.Info<NavigationEndpoint.Endpoint.Browse>? = null,
)

internal fun onlinePlaylistShareUrl(browseId: String): String? {
    val sourceId = browseId.removePrefix(MODIFIED_PREFIX)
    if (isPodcastBrowseId(sourceId)) return ExternalUris.youtubeMusicPodcast(sourceId)
    return "https://music.youtube.com/playlist?list=${sourceId.removePrefix("VL")}"
}

/** Uses the browse API for podcasts, retaining their exact opaque identity. */
@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun loadOnlinePlaylistContent(
    browseId: String,
    fetchAllowed: () -> Boolean = { true },
    podcastLoader: suspend (String) -> OnlinePlaylistContent? = { id ->
        try {
            val page = loadCompletePodcastPage(id)
            OnlinePlaylistContent(
                title = page.title,
                author = page.author,
                channel = page.owner,
                thumbnailUrl = page.thumbnail.firstOrNull()?.url,
                songs = page.listEpisode.map { it.asPodcastMediaItem(id).asSong },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PodcastContent").e(error, "Unable to load complete podcast episodes")
            null
        }
    },
    playlistLoader: suspend (String) -> OnlinePlaylistContent? = { id ->
        loadPlaylistFirstPage(id).getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }?.let { firstPage ->
            val page = if (firstPage.songsContinuation == null) firstPage else loadPlaylistContinuations(firstPage)
            page.toOnlinePlaylistMenuContent()
        }
    },
): OnlinePlaylistContent? {
    if (!fetchAllowed()) return null
    return if (isPodcastBrowseId(browseId)) podcastLoader(browseId) else playlistLoader(browseId)
}

/** Keep source metadata available when a continuation fails, without exposing a partial bulk selection. */
internal fun PlaylistPage.toOnlinePlaylistMenuContent(): OnlinePlaylistContent =
    OnlinePlaylistContent(
        title = playlist.title,
        author = playlist.channel?.name,
        thumbnailUrl = playlist.thumbnail?.url,
        songs = if (songsContinuation == null) songs.mapNotNull { it.asSong } else null,
        channel = playlist.channel,
    )
