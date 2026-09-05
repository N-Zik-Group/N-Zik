package app.n_zik.android.playback.services.automotive.browse.handlers

import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.parseArtists
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.GridRenderer
import it.fast4x.innertube.requests.ArtistItemsPage
import it.fast4x.innertube.utils.from
import io.ktor.client.call.body
import kotlinx.coroutines.flow.first
import timber.log.Timber
import app.it.fast4x.rimusic.MODIFIED_PREFIX

class ArtistDetailHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean {
        if (!parentId.startsWith("${PlayerServiceModern.ARTIST}/")) return false
        val parts = parentId.split("/")
        return parts.size >= 2
    }

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val parts = parentId.split("/")
        val actualParentId = parts[0]
        val artistId = parts[1]
        
        if (artistId.startsWith(LOCAL_KEY_PREFIX) || artistId.startsWith("LOCAL_ARTIST_")) {
            return database.songArtistMapTable.allSongsBy(artistId).first().map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, actualParentId) }
        } else {
            val cleanArtistId = artistId.removePrefix(MODIFIED_PREFIX)
            val sectionItems = mutableListOf<MediaItem>()
            if (parts.size == 2) {
                val artistPage = YtMusic.getArtistPage(cleanArtistId).getOrNull()
                Timber.tag("ArtistDetailHandler").i("AA artist sections: page=${artistPage != null}")
                artistPage?.sections?.forEach { section ->
                    val type = when {
                        section.items.all { it is Innertube.SongItem } -> { sectionItems.add(browsableMediaItem("$parentId/${Uri.encode(section.title)}", section.title, null, drawableUri(context, R.drawable.musical_notes), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)); "Songs" }
                        section.items.all { it is Innertube.AlbumItem } -> { sectionItems.add(browsableMediaItem("$parentId/${Uri.encode(section.title)}", section.title, null, drawableUri(context, R.drawable.album), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)); "Albums" }
                        section.items.all { it is Innertube.VideoItem } -> { sectionItems.add(browsableMediaItem("$parentId/${Uri.encode(section.title)}", section.title, null, drawableUri(context, R.drawable.video), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)); "Videos" }
                        section.items.all { it is Innertube.PlaylistItem } -> { sectionItems.add(browsableMediaItem("$parentId/${Uri.encode(section.title)}", section.title, null, drawableUri(context, R.drawable.playlist), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)); "Playlists" }
                        section.items.all { it is Innertube.ArtistItem } -> { sectionItems.add(browsableMediaItem("$parentId/${Uri.encode(section.title)}", section.title, null, drawableUri(context, R.drawable.people), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)); "Artists" }
                        else -> "UNMATCHED"
                    }
                    Timber.tag("ArtistDetailHandler").i("AA section: title=\"${section.title}\" items=${section.items.size} firstType=${section.items.firstOrNull()?.let { it::class.simpleName }} type=$type more=${section.moreEndpoint != null}")
                }
            } else {
                val sectionTitle = Uri.decode(parts[2])
                Timber.tag("ArtistDetailHandler").i("AA browsing section: title=\"$sectionTitle\"")
                val artistPage = YtMusic.getArtistPage(cleanArtistId).getOrNull()
                val section = artistPage?.sections?.firstOrNull { it.title == sectionTitle }
                if (section != null) {
                    Timber.tag("ArtistDetailHandler").i("AA section found: items=${section.items.size} more=${section.moreEndpoint?.browseId}")
                    val moreBrowseId = section.moreEndpoint?.browseId
                    val moreParams = section.moreEndpoint?.params
                    if (moreBrowseId != null) {
                        try {
                            val response = Innertube.browse(browseId = moreBrowseId, params = moreParams).body<BrowseResponse>()
                            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()
                            var sectionContent = tabs.mapNotNull { tab -> tab.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull() }.firstOrNull() ?: response.contents?.sectionListRenderer?.contents?.firstOrNull()
                            if (sectionContent == null) {
                                val tabEndpoint = tabs.mapNotNull { it.tabRenderer?.endpoint?.browseEndpoint }.firstOrNull()
                                if (tabEndpoint != null) {
                                    val tabResponse = Innertube.browse(browseId = tabEndpoint.browseId ?: moreBrowseId, params = tabEndpoint.params).body<BrowseResponse>()
                                    val tabTabs = tabResponse.contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()
                                    sectionContent = tabTabs.mapNotNull { tab -> tab.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull() }.firstOrNull() ?: tabResponse.contents?.sectionListRenderer?.contents?.firstOrNull()
                                }
                            }
                            if (sectionContent != null) {
                                val fetched = mutableListOf<Innertube.Item>()
                                sectionContent.gridRenderer?.items?.mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)?.mapNotNull(ArtistItemsPage.Companion::fromMusicTwoRowItemRenderer)?.forEach { fetched.add(it) }
                                sectionContent.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicTwoRowItemRenderer }?.mapNotNull(ArtistItemsPage.Companion::fromMusicTwoRowItemRenderer)?.forEach { fetched.add(it) }
                                sectionContent.musicShelfRenderer?.contents?.mapNotNull { it.musicResponsiveListItemRenderer?.let { r -> Innertube.SongItem.from(r) } }?.forEach { fetched.add(it) }
                                sectionContent.musicPlaylistShelfRenderer?.contents?.mapNotNull { it.musicResponsiveListItemRenderer?.let { r -> Innertube.SongItem.from(r) } }?.forEach { fetched.add(it) }
                                Timber.tag("ArtistDetailHandler").i("AA browse section: fetched=${fetched.size} grid=${sectionContent.gridRenderer != null} carousel=${sectionContent.musicCarouselShelfRenderer != null} shelf=${sectionContent.musicShelfRenderer != null} playlistShelf=${sectionContent.musicPlaylistShelfRenderer != null}")
                                fetched.distinctBy { it.key }.forEach { item ->
                                    when (item) {
                                        is Innertube.SongItem -> { val song = item.asSong; AutoSearchState.searchedSongs = (AutoSearchState.searchedSongs + song).distinctBy { s -> s.id }; sectionItems.add(SessionMediaItemMapper.mapSongToMediaItem(song, actualParentId)) }
                                        is Innertube.AlbumItem -> sectionItems.add(SessionMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, item.key ?: "", item.info?.name ?: "", item.authors.parseArtists().joinToString(", "), item.thumbnail?.url))
                                        is Innertube.VideoItem -> { val s = item.asSong; AutoSearchState.searchedVideos = (AutoSearchState.searchedVideos + item).distinctBy { it.key }; sectionItems.add(SessionMediaItemMapper.mapSongToMediaItem(s, parentId)) }
                                        is Innertube.PlaylistItem -> sectionItems.add(browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${item.key}", item.info?.name ?: "", null, item.thumbnail?.url?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST, parentId))
                                        is Innertube.ArtistItem -> sectionItems.add(SessionMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, item.key ?: "", item.info?.name ?: "", item.thumbnail?.url, item.subscribersCountText, parentId))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("ArtistDetailHandler").w(e, "AA browse section failed")
                        }
                    }
                    if (sectionItems.isEmpty()) {
                        Timber.tag("ArtistDetailHandler").i("AA fallback to section items: ${section.items.size} items")
                        section.items.forEach { item ->
                            when (item) {
                                is Innertube.SongItem -> { val song = item.asSong; AutoSearchState.searchedSongs = (AutoSearchState.searchedSongs + song).distinctBy { s -> s.id }; sectionItems.add(SessionMediaItemMapper.mapSongToMediaItem(song, actualParentId)) }
                                is Innertube.AlbumItem -> sectionItems.add(SessionMediaItemMapper.mapAlbumToMediaItem(PlayerServiceModern.ALBUM, item.key ?: "", item.info?.name ?: "", item.authors.parseArtists().joinToString(", "), item.thumbnail?.url))
                                is Innertube.VideoItem -> { val s = item.asSong; AutoSearchState.searchedVideos = (AutoSearchState.searchedVideos + item).distinctBy { it.key }; sectionItems.add(SessionMediaItemMapper.mapSongToMediaItem(s, parentId)) }
                                is Innertube.PlaylistItem -> sectionItems.add(browsableMediaItem("${PlayerServiceModern.PLAYLIST}/${item.key}", item.info?.name ?: "", null, item.thumbnail?.url?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST, parentId))
                                is Innertube.ArtistItem -> sectionItems.add(SessionMediaItemMapper.mapArtistToMediaItem(PlayerServiceModern.ARTIST, item.key ?: "", item.info?.name ?: "", item.thumbnail?.url, item.subscribersCountText, parentId))
                            }
                        }
                    }
                } else {
                    Timber.tag("ArtistDetailHandler").w("AA section not found for title=\"$sectionTitle\"")
                }
            }
            return sectionItems.distinctBy { it.mediaId }
        }
    }
}
