package app.n_zik.android.playback.services.automotive.session

import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ext.FormatWithSong

import app.n_zik.android.playback.services.PlayerServiceModern

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.n_zik.android.R
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.models.bodies.ContinuationBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.requests.artistPage
import it.fast4x.innertube.requests.albumPage
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.relatedPage
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.bodies.NextBody
import timber.log.Timber
import it.fast4x.innertube.models.BrowseEndpoint
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.GridRenderer
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.SectionListRenderer
import it.fast4x.innertube.requests.ArtistItemsPage
import it.fast4x.innertube.requests.ArtistPage
import it.fast4x.innertube.requests.ArtistSection
import it.fast4x.innertube.utils.from
import io.ktor.client.call.body
import androidx.core.net.toUri
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.download.utils.MyDownloadHelper
import app.it.fast4x.rimusic.repository.QuickPicksRepository
import kotlinx.coroutines.*
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX

import app.it.fast4x.rimusic.LOCAL_KEY_PREFIX
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ALBUMS_FAVORITES
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ALBUMS_LIBRARY
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ARTISTS_FAVORITES
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ARTISTS_LIBRARY
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_LOCAL
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_MONTHLY
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_PINNED

import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_YT
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_QUICK_PICKS
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.parseArtists
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey

import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.it.fast4x.rimusic.utils.showFavoritesPlaylistKey
import app.it.fast4x.rimusic.utils.showCachedPlaylistKey
import app.it.fast4x.rimusic.utils.showDownloadedPlaylistKey
import app.it.fast4x.rimusic.utils.showOnDevicePlaylistKey
import app.kreate.android.me.knighthat.utils.getLocalSongs
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.Preference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import it.fast4x.innertube.models.NavigationEndpoint
import kotlinx.coroutines.flow.Flow
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.it.fast4x.rimusic.enums.OnDeviceSongSortBy
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import androidx.media3.session.MediaConstants
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import androidx.media3.common.Player
@UnstableApi
class AutoSessionCallback(
    val context: Context,
    val database: Database,
    val downloadHelper: MyDownloadHelper
) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(Dispatchers.Main) + Job()
    private var observationJob: Job? = null
    lateinit var binder: PlayerServiceModern.Binder
    var toggleLike: () -> Unit = {}
    var toggleDownload: () -> Unit = {}
    var toggleRepeat: () -> Unit = {}
    var toggleShuffle: () -> Unit = {}
    var startRadio: () -> Unit = {}
    var callPause: () -> Unit = {}
    var actionSearch: () -> Unit = {}
    
    private val autoBrowseTree = AutoBrowseTree(context, database, downloadHelper)

    fun observeRepository(session: MediaLibrarySession) {
        // Disabled: notifyChildrenChanged causes Android Auto to rebuild
        // the browse tree + queue on every DB change, causing queue recomposition
        // and crashes. Metrolist does not call notifyChildrenChanged at all —
        // Android Auto queries onGetChildren() on demand.
        observationJob?.cancel()
    }

    fun release() {
        observationJob?.cancel()
        scope.cancel()
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        return MediaSession.ConnectionResult.accept(
            connectionResult.availableSessionCommands.buildUpon()
                .add(AutoSessionConstants.CommandToggleDownload)
                .add(AutoSessionConstants.CommandToggleLike)
                .add(AutoSessionConstants.CommandToggleShuffle)
                .add(AutoSessionConstants.CommandToggleRepeatMode)
                .add(AutoSessionConstants.CommandStartRadio)
                .add(AutoSessionConstants.CommandSearch)
                .build(),
            connectionResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .build()
        )
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        Timber.tag("AutoSessionCallback").d("onSearch: $query")
        autoBrowseTree.clearCache()
        session.notifySearchResultChanged(browser, query, 0, params)
        return Futures.immediateFuture(LibraryResult.ofVoid(params))
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val results = listOf(            AutoMediaItemMapper.browsableMediaItem("${AutoSessionConstants.ID_SEARCH_SONGS}/$query", context.getString(R.string.songs), null, AutoMediaItemMapper.drawableUri(context, R.drawable.musical_notes), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            AutoMediaItemMapper.browsableMediaItem("${AutoSessionConstants.ID_SEARCH_ALBUMS}/$query", context.getString(R.string.albums), null, AutoMediaItemMapper.drawableUri(context, R.drawable.album), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            AutoMediaItemMapper.browsableMediaItem("${AutoSessionConstants.ID_SEARCH_ARTISTS}/$query", context.getString(R.string.artists), null, AutoMediaItemMapper.drawableUri(context, R.drawable.people), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            AutoMediaItemMapper.browsableMediaItem("${AutoSessionConstants.ID_SEARCH_VIDEOS}/$query", context.getString(R.string.videos), null, AutoMediaItemMapper.drawableUri(context, R.drawable.video), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            AutoMediaItemMapper.browsableMediaItem("${AutoSessionConstants.ID_SEARCH_PLAYLISTS}/$query", context.getString(R.string.playlists), null, AutoMediaItemMapper.drawableUri(context, R.drawable.library), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            AutoMediaItemMapper.browsableMediaItem("${AutoSessionConstants.ID_SEARCH_FEATURED}/$query", context.getString(R.string.featured), null, AutoMediaItemMapper.drawableUri(context, R.drawable.featured_playlist), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            AutoMediaItemMapper.browsableMediaItem("${AutoSessionConstants.ID_SEARCH_PODCASTS}/$query", context.getString(R.string.podcasts), null, AutoMediaItemMapper.drawableUri(context, R.drawable.podcast), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        )
        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(results), params))
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            AutoSessionConstants.ACTION_TOGGLE_LIKE -> toggleLike()
            AutoSessionConstants.ACTION_TOGGLE_DOWNLOAD -> toggleDownload()
            AutoSessionConstants.ACTION_TOGGLE_SHUFFLE -> toggleShuffle()
            AutoSessionConstants.ACTION_TOGGLE_REPEAT_MODE -> toggleRepeat()
            AutoSessionConstants.ACTION_START_RADIO -> startRadio()
            AutoSessionConstants.ACTION_SEARCH -> actionSearch()
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    @OptIn(UnstableApi::class)
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(
            MediaItem.Builder()
                .setMediaId(PlayerServiceModern.ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsPlayable(false)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build(),
            params
        )
    )

    @OptIn(UnstableApi::class)
    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
        val pageIndex = if (parentId.contains("_PAGE_")) parentId.substringAfter("_PAGE_").toIntOrNull() ?: -1 else -1
        val list = autoBrowseTree.getChildren(parentId, pageIndex, if (::binder.isInitialized) binder else null)
        LibraryResult.ofItemList(ImmutableList.copyOf(list), params)
    }

    @OptIn(UnstableApi::class)
    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.IO) {
        val songId = mediaId.split("/").lastOrNull() ?: mediaId
        database.songTable.findByIdDirect(songId)?.let { song -> 
            if (mediaId.contains("/")) {
                SessionMediaItemMapper.mapSongToMediaItem(song, mediaId.substringBeforeLast("/"), loadArtwork = true)
            } else {
                SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true)
            }
        }?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val result = onSetMediaItemsInternal(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        val maxSongs = context.preferences.getEnum(maxSongsInQueueAndroidAutoKey, MaxSongs.Unlimited).toInt()
        
        if (result.mediaItems.size <= maxSongs) return@future result
        
        val start = maxOf(0, result.startIndex - maxSongs / 2)
        val end = minOf(result.mediaItems.size, start + maxSongs)
        val adjustedStart = maxOf(0, end - maxSongs)
        
        MediaSession.MediaItemsWithStartPosition(
            result.mediaItems.subList(adjustedStart, end),
            result.startIndex - adjustedStart,
            result.startPositionMs
        )
    }

    private suspend fun onSetMediaItemsInternal(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): MediaSession.MediaItemsWithStartPosition {
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_LUCKY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_LUCKY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = (QuickPicksRepository.trendingList.value + (QuickPicksRepository.relatedPage.value?.songs?.map { it.asSong } ?: emptyList())).distinctBy { it.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONG_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ALL_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONG_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ALL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
val allSongs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).first().let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
val allSongs = database.songTable.sortFavorites(sortBy, sortOrder).first().let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
    val downloads = downloadHelper.downloads.value
    val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
    val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
    val allSongs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = false).first().fastFilter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_BY.key, OnDeviceSongSortBy.Title) } catch (e: Exception) { OnDeviceSongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val onDeviceSongs = context.getLocalSongs(sortBy, sortOrder).first().keys.toList()
            val allSongs = if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) onDeviceSongs.shuffled() else onDeviceSongs
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
val allSongs = database.formatTable.sortAllWithSongs(sortBy, sortOrder).first().fastFilter { itf -> val contentLength = itf.format.contentLength; contentLength != null && binder.cache.isCached(itf.song.id, 0L, contentLength) }.fastMap { itf -> itf.song }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_TOP_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_TOP_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt()).first().let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUM_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUM_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.albumTable.allInLibrary().first().flatMap { album -> database.songAlbumMapTable.allSongsOf(album.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.albumTable.allBookmarked().first().flatMap { album -> database.songAlbumMapTable.allSongsOf(album.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTIST_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_LIBRARY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTIST_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_LIBRARY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.artistTable.allInLibrary().first().flatMap { artist -> database.songArtistMapTable.allSongsBy(artist.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.artistTable.allFollowing().first().flatMap { artist -> database.songArtistMapTable.allSongsBy(artist.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.playlistTable.allAsPreview().first().filter { !it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX, true) && !it.playlist.name.startsWith(MONTHLY_PREFIX, true) }.flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.playlistTable.allAsPreview().first().filter { it.playlist.isYoutubePlaylist }.flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }

        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_PINNED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_PINNED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.playlistTable.allAsPreview().first().filter { it.playlist.name.startsWith(PINNED_PREFIX, true) }.flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_MONTHLY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_MONTHLY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.playlistTable.allAsPreview().first().filter { it.playlist.name.startsWith(MONTHLY_PREFIX, true) }.flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLIST_SHUFFLE) {
            val paths = mediaItems.first().mediaId.split("/")
            val playlistId = paths[1]
            val allSongs = if (playlistId.toLongOrNull() != null) {
                database.songPlaylistMapTable.allSongsOf(playlistId.toLong()).first()
            } else {
                AutoSearchState.searchedSongs
            }.shuffled()
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, 0, 0)
        }

        var queryList = emptyList<Song>()
        var startIdx = startIndex
        runCatching {
            var songId = ""
            val paths = mediaItems.first().mediaId.split("/")
            when (paths.first()) {
                AutoSessionConstants.ID_QUICK_PICKS -> { 
                    songId = paths[1]
                    val trending = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = 500).first()
                    val relatedSongs = if (trending.isNotEmpty()) {
                        Innertube.relatedPage(NextBody(videoId = trending.first().id))?.getOrNull()?.songs?.map { it.asSong } ?: emptyList()
                    } else emptyList()
                    val ytmQuickPicks = if (isYouTubeLoggedIn()) {
                        YtMusic.getQuickPicks(setLogin = true).getOrNull()?.map { it.asSong } ?: emptyList()
                    } else emptyList()
                    Timber.tag("AutoSessionCallback").d("Quick picks play list loaded -> trending: ${trending.size}, related: ${relatedSongs.size}, ytb: ${ytmQuickPicks.size}")
                    queryList = (ytmQuickPicks + trending + relatedSongs).distinctBy { it.id } 
                }
                AutoSessionConstants.ID_SEARCH_SONGS -> { songId = paths[2]; queryList = AutoSearchState.searchedSongs }
                AutoSessionConstants.ID_SEARCH_VIDEOS -> { songId = paths[2]; queryList = AutoSearchState.searchedVideos.map { it.asSong } }
                PlayerServiceModern.SEARCHED -> { songId = paths[1]; queryList = AutoSearchState.searchedSongs }
                PlayerServiceModern.SONG -> { songId = paths[1]; queryList = database.songTable.all().first() }
                AutoSessionConstants.ID_SONGS_ALL -> { songId = paths[1]; queryList = database.songTable.sortAll(SongSortBy.DateAdded, SortOrder.Descending, excludeHidden = true).first() }
                AutoSessionConstants.ID_SONGS_FAVORITES -> { songId = paths[1]; queryList = database.songTable.allFavorites().first().reversed() }
                AutoSessionConstants.ID_SONGS_DOWNLOADED -> { 
                    val downloads = downloadHelper.downloads.value
                    queryList = database.songTable.all(excludeHidden = false).first().fastFilter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }.sortedByDescending { song -> downloads[song.id]?.updateTimeMs ?: 0L }
                    songId = paths[1]
                }
                AutoSessionConstants.ID_SONGS_ONDEVICE -> { songId = paths[1]; queryList = database.songTable.allOnDevice().first() }
                AutoSessionConstants.ID_SONGS_CACHED -> {
                    queryList = database.formatTable.allWithSongs().first().fastFilter { itf -> itf.song.totalPlayTimeMs > 0 && itf.format.contentLength != null && (if (::binder.isInitialized) binder.cache.isCached(itf.song.id, 0L, itf.format.contentLength ?: 0L) else false) }.reversed().fastMap { itf -> itf.song }
                    songId = paths[1]
                }
                AutoSessionConstants.ID_SONGS_TOP -> {
                    queryList = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt()).first()
                    songId = paths[1]
                }
                PlayerServiceModern.ARTIST -> { songId = if (paths.size == 4) paths[3] else paths[2]; queryList = if (paths.size == 4) AutoSearchState.searchedSongs else database.songArtistMapTable.allSongsBy(paths[1]).first() }
                PlayerServiceModern.ALBUM -> { songId = paths[2]; queryList = database.songAlbumMapTable.allSongsOf(paths[1]).first(); if (queryList.isEmpty()) queryList = AutoSearchState.searchedSongs }
                PlayerServiceModern.PLAYLIST -> {
                    val playlistId = paths[1]; songId = paths[2]
                    queryList = when (playlistId) {
                        AutoSessionConstants.ID_FAVORITES -> database.songTable.allFavorites().map { it.reversed() }.first()
                        AutoSessionConstants.ID_CACHED -> database.formatTable.allWithSongs().map { fl -> fl.fastFilter { itf -> itf.song.totalPlayTimeMs > 0 && itf.format.contentLength != null && (if (::binder.isInitialized) binder.cache.isCached(itf.song.id, 0L, itf.format.contentLength ?: 0L) else false) }.reversed().fastMap { itf -> itf.song } }.first()
                        AutoSessionConstants.ID_TOP -> database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt()).first()
                        AutoSessionConstants.ID_ONDEVICE -> database.songTable.allOnDevice().first()
                        AutoSessionConstants.ID_DOWNLOADED -> {
                            downloadHelper.getDownloadManager(context)
                            val downloads = downloadHelper.downloads.value
                            database.songTable.all(excludeHidden = false).map { fl -> fl.fastFilter { s -> downloads[s.id]?.state == Download.STATE_COMPLETED }.sortedByDescending { s -> downloads[s.id]?.updateTimeMs ?: 0L } }.first()
                        }
                        else -> { if (playlistId.toLongOrNull() != null) database.songPlaylistMapTable.allSongsOf(playlistId.toLong()).first() else AutoSearchState.searchedSongs }
                    }
                }
            }
            startIdx = queryList.indexOfFirst { song -> song.id == songId }.coerceAtLeast(0)
        }
        return MediaSession.MediaItemsWithStartPosition(queryList.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song) }, startIdx, startPositionMs)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> = scope.future(Dispatchers.IO) {
        val parentalControlEnabled = try { context.preferences.getBoolean(parentalControlEnabledKey, false) } catch (e: Exception) { false }
        val mappedItems = mediaItems.fastMap { item ->
            val songId = item.mediaId.split("/").lastOrNull() ?: item.mediaId
            database.songTable.findById(songId).first()?.asMediaItem ?: item.buildUpon().setMediaId(songId).build()
        }.filter { !parentalControlEnabled || it.mediaMetadata.extras?.getBoolean(MediaConstants.EXTRAS_KEY_IS_EXPLICIT) != true }.toMutableList()
        mappedItems
    }

    @Deprecated("Deprecated in Java")
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val settableFuture = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        val defaultResult = MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        if (!context.preferences.getBoolean(persistentQueueKey, false)) return Futures.immediateFuture(defaultResult)
        scope.launch {
            try {
                database.queueTable.all().first().run {
                    val idx = indexOfFirst { it.position != null }.coerceAtLeast(0)
                    val startPos = getOrNull(idx)?.position ?: 0L
                    val mediaItems = map { itm -> SessionMediaItemMapper.mapSongToMediaItem(itm.mediaItem.asSong, true) }
                    settableFuture.set(MediaSession.MediaItemsWithStartPosition(mediaItems, idx, startPos))
                }
            } catch (e: Exception) {
                settableFuture.set(defaultResult)
            }
        }
        return settableFuture
    }

    private fun getCountCachedSongs(): Flow<Int> = database.formatTable.allWithSongs().map { flist ->
        if (!::binder.isInitialized) return@map 0
        flist.filter { itf ->
            val contentLength = itf.format.contentLength
            contentLength != null && binder.cache.isCached(itf.song.id, 0L, contentLength)
        }.size
    }
    private fun getCountDownloadedSongs(): Flow<Int> {
        downloadHelper.getDownloadManager(context)
        return downloadHelper.downloads.map { dm -> dm.filter { ite -> ite.value.state == Download.STATE_COMPLETED }.size }
    }
}









