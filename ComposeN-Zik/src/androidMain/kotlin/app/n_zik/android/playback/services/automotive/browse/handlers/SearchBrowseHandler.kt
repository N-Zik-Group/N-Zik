package app.n_zik.android.playback.services.automotive.browse.handlers

import it.fast4x.innertube.utils.from

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.parseArtists
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.requests.searchPageContinuation

class SearchBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId.startsWith("SEARCH_") ||
            parentId.startsWith("ID_SEARCH_")

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val parts = parentId.split("/")
        val actualParentId = parts[0]
        
        return when (actualParentId) {
            AutoSessionConstants.ID_SEARCH_SONGS -> {
                val allMapped = mutableListOf<MediaItem>()
                var cont: String? = null
                do {
                    val resultPage = if (cont == null) {
                        Innertube.searchPage<Innertube.SongItem>(query = parts[1], params = Innertube.SearchFilter.Song.value, fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) })?.getOrNull()
                    } else {
                        Innertube.searchPageContinuation<Innertube.SongItem>(continuation = cont, fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) })?.getOrNull()
                    }
                    val songs = resultPage?.items?.map { s -> s.asSong } ?: emptyList()
                    AutoSearchState.searchedSongs = (AutoSearchState.searchedSongs + songs).distinctBy { s -> s.id }
                    allMapped.addAll(songs.map { s -> SessionMediaItemMapper.mapSongToMediaItem(s, actualParentId) })
                    cont = resultPage?.continuation
                } while (cont != null && allMapped.size < 150)
                allMapped
            }
            AutoSessionConstants.ID_SEARCH_ARTISTS -> {
                val allMapped = mutableListOf<MediaItem>()
                var cont: String? = null
                do {
                    val resultPage = if (cont == null) {
                        Innertube.searchPage<Innertube.ArtistItem>(query = parts[1], params = Innertube.SearchFilter.Artist.value, fromMusicShelfRendererContent = { content -> Innertube.ArtistItem.from(content) })?.getOrNull()
                    } else {
                        Innertube.searchPageContinuation<Innertube.ArtistItem>(continuation = cont, fromMusicShelfRendererContent = { content -> Innertube.ArtistItem.from(content) })?.getOrNull()
                    }
                    val items = resultPage?.items ?: emptyList()
                    AutoSearchState.searchedArtists = (AutoSearchState.searchedArtists + items).distinctBy { it.key }
                    allMapped.addAll(items.map { ai -> SessionMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, ai.key ?: "", ai.info?.name ?: "", ai.thumbnail?.url, ai.subscribersCountText, actualParentId) })
                    cont = resultPage?.continuation
                } while (cont != null && allMapped.size < 150)
                allMapped
            }
            AutoSessionConstants.ID_SEARCH_ALBUMS -> {
                val allMapped = mutableListOf<MediaItem>()
                var cont: String? = null
                do {
                    val resultPage = if (cont == null) {
                        Innertube.searchPage<Innertube.AlbumItem>(query = parts[1], params = Innertube.SearchFilter.Album.value, fromMusicShelfRendererContent = { content -> Innertube.AlbumItem.from(content) })?.getOrNull()
                    } else {
                        Innertube.searchPageContinuation<Innertube.AlbumItem>(continuation = cont, fromMusicShelfRendererContent = { content -> Innertube.AlbumItem.from(content) })?.getOrNull()
                    }
                    val items = resultPage?.items ?: emptyList()
                    AutoSearchState.searchedAlbums = (AutoSearchState.searchedAlbums + items).distinctBy { it.key }
                    allMapped.addAll(items.map { ali -> SessionMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, ali.key ?: "", ali.info?.name ?: "", ali.authors.parseArtists().joinToString(", "), ali.thumbnail?.url, actualParentId) })
                    cont = resultPage?.continuation
                } while (cont != null && allMapped.size < 150)
                allMapped
            }
            AutoSessionConstants.ID_SEARCH_VIDEOS -> {
                val allMapped = mutableListOf<MediaItem>()
                var cont: String? = null
                do {
                    val resultPage = if (cont == null) {
                        Innertube.searchPage<Innertube.VideoItem>(query = parts[1], params = Innertube.SearchFilter.Video.value, fromMusicShelfRendererContent = { content -> Innertube.VideoItem.from(content) })?.getOrNull()
                    } else {
                        Innertube.searchPageContinuation<Innertube.VideoItem>(continuation = cont, fromMusicShelfRendererContent = { content -> Innertube.VideoItem.from(content) })?.getOrNull()
                    }
                    val items = resultPage?.items ?: emptyList()
                    val songs = items.map { it.asSong }
                    AutoSearchState.searchedVideos = (AutoSearchState.searchedVideos + items).distinctBy { it.key }
                    allMapped.addAll(songs.map { s -> SessionMediaItemMapper.mapSongToMediaItem(s, actualParentId) })
                    cont = resultPage?.continuation
                } while (cont != null && allMapped.size < 150)
                allMapped
            }
            AutoSessionConstants.ID_SEARCH_PLAYLISTS -> {
                val allMapped = mutableListOf<MediaItem>()
                var cont: String? = null
                do {
                    val resultPage = if (cont == null) {
                        Innertube.searchPage<Innertube.PlaylistItem>(query = parts[1], params = Innertube.SearchFilter.CommunityPlaylist.value, fromMusicShelfRendererContent = { content -> Innertube.PlaylistItem.from(content) })?.getOrNull()
                    } else {
                        Innertube.searchPageContinuation<Innertube.PlaylistItem>(continuation = cont, fromMusicShelfRendererContent = { content -> Innertube.PlaylistItem.from(content) })?.getOrNull()
                    }
                    val items = resultPage?.items ?: emptyList()
                    allMapped.addAll(items.map { pi -> browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${pi.key}", pi.info?.name ?: "", null, pi.thumbnail?.url?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST, actualParentId) })
                    cont = resultPage?.continuation
                } while (cont != null && allMapped.size < 150)
                allMapped
            }
            AutoSessionConstants.ID_SEARCH_FEATURED -> {
                val allMapped = mutableListOf<MediaItem>()
                var cont: String? = null
                do {
                    val resultPage = if (cont == null) {
                        Innertube.searchPage<Innertube.PlaylistItem>(query = parts[1], params = Innertube.SearchFilter.FeaturedPlaylist.value, fromMusicShelfRendererContent = { content -> Innertube.PlaylistItem.from(content) })?.getOrNull()
                    } else {
                        Innertube.searchPageContinuation<Innertube.PlaylistItem>(continuation = cont, fromMusicShelfRendererContent = { content -> Innertube.PlaylistItem.from(content) })?.getOrNull()
                    }
                    val items = resultPage?.items ?: emptyList()
                    allMapped.addAll(items.map { pi -> browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${pi.key}", pi.info?.name ?: "", null, pi.thumbnail?.url?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST, actualParentId) })
                    cont = resultPage?.continuation
                } while (cont != null && allMapped.size < 150)
                allMapped
            }
            AutoSessionConstants.ID_SEARCH_PODCASTS -> {
                val allMapped = mutableListOf<MediaItem>()
                var cont: String? = null
                do {
                    val resultPage = if (cont == null) {
                        Innertube.searchPage<Innertube.AlbumItem>(query = parts[1], params = Innertube.SearchFilter.Podcast.value, fromMusicShelfRendererContent = { content -> Innertube.AlbumItem.from(content) })?.getOrNull()
                    } else {
                        Innertube.searchPageContinuation<Innertube.AlbumItem>(continuation = cont, fromMusicShelfRendererContent = { content -> Innertube.AlbumItem.from(content) })?.getOrNull()
                    }
                    val items = resultPage?.items ?: emptyList()
                    AutoSearchState.searchedAlbums = (AutoSearchState.searchedAlbums + items).distinctBy { it.key }
                    allMapped.addAll(items.map { ali -> SessionMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, ali.key ?: "", ali.info?.name ?: "", ali.authors.parseArtists().joinToString(", "), ali.thumbnail?.url, actualParentId) })
                    cont = resultPage?.continuation
                } while (cont != null && allMapped.size < 150)
                allMapped
            }
            else -> emptyList()
        }
    }
}
