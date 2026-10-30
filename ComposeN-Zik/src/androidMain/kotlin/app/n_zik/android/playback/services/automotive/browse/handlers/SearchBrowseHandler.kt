package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.requests.searchPageContinuation
import it.fast4x.innertube.utils.from

import app.it.fast4x.rimusic.utils.asSong
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.isLocal

import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.R
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.playback.services.automotive.browse.AutoSearchWindowStore
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract

class SearchBrowseHandler(
    internal val windows: AutoSearchWindowStore = AutoSearchWindowStore(),
    private val online: (Context) -> Boolean = NetworkQualityHelper::isNetworkConnected,
    private val profile: (Context) -> String = ::getActiveProfile,
) : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId.startsWith("SEARCH_") ||
        parentId.startsWith("ID_SEARCH_") || AutoMediaIdContract.parseSearchContinuation(parentId) != null

    fun canonicalParent(parentId: String, context: Context): String? =
        AutoMediaIdContract.parseSearchContinuation(parentId)?.let { windows.parentFor(it, profile(context)) } ?: parentId

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?,
    ): List<MediaItem> {
        if (!online(context)) { windows.clear(); return emptyList() }
        val requestProfile = profile(context)
        val token = AutoMediaIdContract.parseSearchContinuation(parentId)
        val parent = if (token != null) windows.parentFor(token, requestProfile) ?: return emptyList() else parentId
        val separator = parent.indexOf('/')
        if (separator < 0) return emptyList()
        val category = parent.substring(0, separator)
        val query = parent.substring(separator + 1)
        if (query.isBlank()) return emptyList()
        val window = windows.load(parent, requestProfile,
            currentOwner = { profile(context).takeIf { online(context) } }, token = token,
        ) { continuation -> loadBatch(category, query, continuation) } ?: return emptyList()
        return window.items + listOfNotNull(window.nextToken?.let {
            browsableMediaItem(
                AutoMediaIdContract.searchContinuation(it), "${context.getString(R.string.next)} (${window.pageNumber + 1})", null,
                drawableUri(context, R.drawable.library),
                MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, parent,
            )
        })
    }

    private suspend fun loadBatch(category: String, query: String, continuation: String?): AutoSearchWindowStore.Batch = when (category) {
        AutoSessionConstants.ID_SEARCH_SONGS -> {
            val page = if (continuation == null) {
                Innertube.searchPage<Innertube.SongItem>(query, Innertube.SearchFilter.Song.value) { Innertube.SongItem.from(it) }
            } else {
                Innertube.searchPageContinuation<Innertube.SongItem>(continuation) { Innertube.SongItem.from(it) }
            }
            val result = page?.getOrThrow() ?: error("Search batch unavailable")
            val items = result.items.orEmpty()
            AutoSearchWindowStore.Batch(items.map { it.asSong }.map { CancellableAutoMediaItemMapper.mapSongToMediaItem(it, category, loadArtwork = it.isLocal) }, result.continuation)
        }
        AutoSessionConstants.ID_SEARCH_VIDEOS -> {
            val page = if (continuation == null) {
                Innertube.searchPage<Innertube.VideoItem>(query, Innertube.SearchFilter.Video.value) { Innertube.VideoItem.from(it) }
            } else {
                Innertube.searchPageContinuation<Innertube.VideoItem>(continuation) { Innertube.VideoItem.from(it) }
            }
            val result = page?.getOrThrow() ?: error("Search batch unavailable")
            val items = result.items.orEmpty()
            AutoSearchWindowStore.Batch(items.map { it.asSong }.map { CancellableAutoMediaItemMapper.mapSongToMediaItem(it, category, loadArtwork = it.isLocal) }, result.continuation)
        }
        AutoSessionConstants.ID_SEARCH_ARTISTS -> {
            val page = if (continuation == null) {
                Innertube.searchPage<Innertube.ArtistItem>(query, Innertube.SearchFilter.Artist.value) { Innertube.ArtistItem.from(it) }
            } else {
                Innertube.searchPageContinuation<Innertube.ArtistItem>(continuation) { Innertube.ArtistItem.from(it) }
            }
            val result = page?.getOrThrow() ?: error("Search batch unavailable")
            val items = result.items.orEmpty()
            AutoSearchWindowStore.Batch(items.map { CancellableAutoMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, it.key ?: "", it.info?.name ?: "", it.thumbnail?.url, it.subscribersCountText, category) }, result.continuation)
        }
        AutoSessionConstants.ID_SEARCH_ALBUMS -> {
            val page = if (continuation == null) {
                Innertube.searchPage<Innertube.AlbumItem>(query, Innertube.SearchFilter.Album.value) { Innertube.AlbumItem.from(it) }
            } else {
                Innertube.searchPageContinuation<Innertube.AlbumItem>(continuation) { Innertube.AlbumItem.from(it) }
            }
            val result = page?.getOrThrow() ?: error("Search batch unavailable")
            val items = result.items.orEmpty()
            AutoSearchWindowStore.Batch(items.map { CancellableAutoMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, it.key ?: "", it.info?.name ?: "", it.authors.artistEntryNames().joinToString(", "), it.thumbnail?.url, category) }, result.continuation)
        }
        AutoSessionConstants.ID_SEARCH_PLAYLISTS -> {
            val page = if (continuation == null) {
                Innertube.searchPage<Innertube.PlaylistItem>(query, Innertube.SearchFilter.CommunityPlaylist.value) { Innertube.PlaylistItem.from(it) }
            } else {
                Innertube.searchPageContinuation<Innertube.PlaylistItem>(continuation) { Innertube.PlaylistItem.from(it) }
            }
            val result = page?.getOrThrow() ?: error("Search batch unavailable")
            val items = result.items.orEmpty()
            AutoSearchWindowStore.Batch(items.map { browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${it.key}", it.info?.name ?: "", null, it.thumbnail?.url?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST, category) }, result.continuation)
        }
        AutoSessionConstants.ID_SEARCH_FEATURED -> {
            val page = if (continuation == null) {
                Innertube.searchPage<Innertube.PlaylistItem>(query, Innertube.SearchFilter.FeaturedPlaylist.value) { Innertube.PlaylistItem.from(it) }
            } else {
                Innertube.searchPageContinuation<Innertube.PlaylistItem>(continuation) { Innertube.PlaylistItem.from(it) }
            }
            val result = page?.getOrThrow() ?: error("Search batch unavailable")
            val items = result.items.orEmpty()
            AutoSearchWindowStore.Batch(items.map { browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${it.key}", it.info?.name ?: "", null, it.thumbnail?.url?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST, category) }, result.continuation)
        }
        AutoSessionConstants.ID_SEARCH_PODCASTS -> {
            val page = if (continuation == null) {
                Innertube.searchPage<Innertube.AlbumItem>(query, Innertube.SearchFilter.Podcast.value) { Innertube.AlbumItem.from(it) }
            } else {
                Innertube.searchPageContinuation<Innertube.AlbumItem>(continuation) { Innertube.AlbumItem.from(it) }
            }
            val result = page?.getOrThrow() ?: error("Search batch unavailable")
            val items = result.items.orEmpty()
            AutoSearchWindowStore.Batch(items.map { CancellableAutoMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, it.key ?: "", it.info?.name ?: "", it.authors.artistEntryNames().joinToString(", "), it.thumbnail?.url, category) }, result.continuation)
        }
        else -> AutoSearchWindowStore.Batch(emptyList(), null)
    }
}
