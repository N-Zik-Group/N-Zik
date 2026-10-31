package app.n_zik.android.components.menu.playlist

import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.components.playlist.loadCompletePodcastEpisodes
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.CancellationException
import timber.log.Timber

internal data class OnlinePlaylistContent(
    val title: String?,
    val author: String?,
    val thumbnailUrl: String?,
    val songs: List<Song>,
)

internal fun onlinePlaylistShareUrl(browseId: String): String =
    "https://music.youtube.com/playlist?list=$browseId"

/** Uses the browse API for podcasts, retaining their exact opaque identity. */
@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun loadOnlinePlaylistContent(
    browseId: String,
    podcastLoader: suspend (String) -> OnlinePlaylistContent? = { id ->
        try {
            val episodes = loadCompletePodcastEpisodes(id)
            OnlinePlaylistContent(
                // Search already supplies this metadata; fetch only the complete episode feed.
                title = null,
                author = null,
                thumbnailUrl = null,
                songs = episodes.map { it.asMediaItem.asSong },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PodcastContent").e(error, "Unable to load complete podcast episodes")
            null
        }
    },
    playlistLoader: suspend (String) -> OnlinePlaylistContent? = { id ->
        YtMusic.getPlaylist(id).getOrNull()?.let { page ->
            OnlinePlaylistContent(
                title = page.playlist.title,
                author = page.playlist.channel?.name,
                thumbnailUrl = page.playlist.thumbnail?.url,
                songs = page.songs.mapNotNull { it.asSong },
            )
        }
    },
): OnlinePlaylistContent? =
    if (isPodcastBrowseId(browseId)) podcastLoader(browseId) else playlistLoader(browseId)
