package app.n_zik.android.components.menu.playlist

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.menu.player.playerMenuMediaItem
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.models.Song
import app.n_zik.android.utils.ExternalUris
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asPodcastMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.canPushToYTM
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.shouldPushPlaylistChanges
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.android.utils.withPodcastSource
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.database.isPodcastBrowseId
import app.n_zik.android.components.playlist.PodcastContinuationFailure
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
    val availableSongs: List<Song>? = songs,
    val sourceItems: Map<String, MediaItem> = emptyMap(),
    val sourceSongs: Map<String, Innertube.SongItem> = emptyMap(),
) {
    @androidx.annotation.OptIn(UnstableApi::class)
    fun getSourceItem(songId: String): MediaItem? = sourceItems[songId] ?: sourceSongs[songId]?.asMediaItem
}

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
            val items = page.listEpisode.map { it.asPodcastMediaItem(id) }
            OnlinePlaylistContent(
                title = page.title,
                author = page.author,
                channel = page.owner,
                thumbnailUrl = page.thumbnail.firstOrNull()?.url,
                songs = items.map { it.asSong },
                sourceItems = items.associateBy(MediaItem::mediaId),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: PodcastContinuationFailure) {
            Timber.tag("PodcastContent").e(error, "Unable to load complete podcast episodes")
            val items = error.availableEpisodes.map { it.asPodcastMediaItem(id) }
            OnlinePlaylistContent(
                title = null,
                author = null,
                thumbnailUrl = null,
                songs = null,
                availableSongs = items.map { it.asSong },
                sourceItems = items.associateBy(MediaItem::mediaId),
            )
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

/** Keep visible songs available for Add without treating an interrupted page as a complete collection. */
internal fun PlaylistPage.toOnlinePlaylistMenuContent(): OnlinePlaylistContent {
    val available = songs.map { it.asSong }
    return OnlinePlaylistContent(
        title = playlist.title,
        author = playlist.channel?.name,
        thumbnailUrl = playlist.thumbnail?.url,
        songs = available.takeIf { songsContinuation == null },
        channel = playlist.channel,
        availableSongs = available,
        sourceSongs = songs.associateBy(Innertube.SongItem::key),
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun onlinePlaylistMenuMediaItem(song: Song, content: OnlinePlaylistContent?, browseId: String): MediaItem =
    content?.getSourceItem(song.id)?.let { playerMenuMediaItem(it, song) }
        ?: song.asMediaItem.withPodcastSource(browseId)

internal fun canPushPlaylistBookmark(context: Context): Boolean =
    shouldPushPlaylistChanges(
        isYouTubeSyncEnabled(), canPushToYTM(), context.preferences.getBoolean(syncPushPlaylistKey, false),
    ) && isNetworkConnected(context)
