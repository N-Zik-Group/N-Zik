package app.n_zik.android.playback.services.automotive.session

import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ext.FormatWithSong
import app.n_zik.android.core.rewind.RewindPlaylists

import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.diagnostics.PLAYBACK_DIAG_TAG
import app.n_zik.android.playback.services.diagnostics.SEEK_PLAYER_COMMANDS
import app.n_zik.android.playback.services.diagnostics.seekCommandName
import app.n_zik.android.playback.services.isLocal

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
import it.fast4x.innertube.requests.artistPage
import it.fast4x.innertube.requests.albumPage
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.relatedPage
import it.fast4x.innertube.YtMusic
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
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.repository.QuickPicksRepository
import kotlinx.coroutines.*
import app.it.fast4x.rimusic.PINNED_PREFIX

import app.it.fast4x.rimusic.LOCAL_KEY_PREFIX
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ALBUMS_FAVORITES
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ALBUMS_LIBRARY
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ARTISTS_FAVORITES
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_ARTISTS_LIBRARY
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_PINNED

import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLISTS_YT
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_QUICK_PICKS
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.parseArtists
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong

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
import app.n_zik.android.playback.services.automotive.DislikedExclusion
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
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private var observationJob: Job? = null
    lateinit var binder: PlayerServiceModern.Binder
    var toggleLike: () -> Unit = {}
    var toggleDownload: () -> Unit = {}
    var toggleRepeat: () -> Unit = {}
    var toggleShuffle: () -> Unit = {}
    var startRadio: () -> Unit = {}
    var callPause: () -> Unit = {}
    var actionSearch: () -> Unit = {}
    // Issue #866 (gh-866): discover command button (notification + AA overflow).
    var toggleDiscover: () -> Unit = {}
    
    private val autoBrowseTree = AutoBrowseTree(context, database, downloadHelper)

    /**
     * Container Android Auto is currently displaying (last browsed album/artist
     * detail). AA session commands are global — there are no per-item actions —
     * so DISLIKE_ALBUM / DISLIKE_ARTIST target this container.
     */
    @Volatile
    private var currentContainer: CurrentContainer? = null

    private data class CurrentContainer(val type: String, val id: String)

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
        // Fresh state on every new automotive connection: a leftover search or
        // browse cache from a previous session must not be resumed — the head
        // unit always starts from a clean browse root. currentContainer is reset
        // too, so a DISLIKE_ALBUM/ARTIST command after reconnect cannot target a
        // stale container from the previous session.
        autoBrowseTree.clearCache()
        currentContainer = null
        return MediaSession.ConnectionResult.accept(
            connectionResult.availableSessionCommands.buildUpon()
                .add(AutoSessionConstants.CommandToggleDownload)
                .add(AutoSessionConstants.CommandToggleLike)
                .add(AutoSessionConstants.CommandToggleShuffle)
                .add(AutoSessionConstants.CommandToggleRepeatMode)
                .add(AutoSessionConstants.CommandStartRadio)
                .add(AutoSessionConstants.CommandSearch)
                .add(AutoSessionConstants.CommandToggleDiscover)
                .add(AutoSessionConstants.CommandDislikeAlbum)
                .add(AutoSessionConstants.CommandDislikeArtist)
                .build(),
            connectionResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .build()
        )
    }

    override fun onPostConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ) {
        super.onPostConnect(session, controller)
        // spec-notification-click-opens-player (option B): the GLOBAL session activity is now
        // the "now playing" notification's content intent (opens the phone MainActivity with
        // a one-shot token — media3 hard-wires the notification content intent to the global
        // session activity). The Android Auto companion must NOT inherit it: send it null via
        // the per-controller override (official since media3 1.4.0) so AA falls back to the
        // standard session UI (media library) instead of the in-app screen. Runs on every
        // AA connection (car re-connects re-send it).
        // Deliberately done in onPostConnect, NOT onConnect: in media3 1.10.1 the controller
        // is only registered (ConnectedControllersManager.addController) after onConnect
        // returns, and the per-controller setSessionActivity dispatch guards on
        // isConnected(controller) — a call from onConnect would be a silent no-op, leaving
        // AA with the global activity (the in-app screen regression this feature must not
        // reintroduce).
        if (session.isAutoCompanionController(controller)) {
            session.setSessionActivity(controller, null)
        }
    }

    /**
     * Issue #881 (gh-881): observe seek commands requested by AOSP-interop session controllers
     * (lockscreen / Android Auto legacy path) — spec S3. In media3 1.10.1 the legacy `onSeekTo` /
     * `onSeekForward` / `onSeekBack` overrides are GONE: the AOSP-interop path routes every player
     * command here, so a `SEEK_SESSION` line synchronized with a seek-loop cycle pins that system
     * path as the caller.
     *
     * SCOPE: media3's OWN controllers (`MediaControllerImplBase` — the in-app media notification
     * and the media-button path) do NOT go through this callback; they apply their seeks straight
     * to the player as dedicated transactions, and those show up in the S1 `SEEK_CALL` stack
     * instead. Absence of a `SEEK_SESSION` line therefore does NOT rule out the
     * notification/media-button path.
     *
     * Purely observational: the command is always delegated to the default handling, which returns
     * `RESULT_SUCCESS` — a non-SUCCESS return would make media3 skip the command.
     */
    // onPlayerCommandRequest is @Deprecated in media3 1.10.1 — it is still the ONLY AOSP-interop
    // hook available (the legacy onSeek* overrides are gone), so the diagnostic stays on it.
    @Suppress("DEPRECATION")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int
    ): Int {
        if (playerCommand in SEEK_PLAYER_COMMANDS) {
            Timber.tag(PLAYBACK_DIAG_TAG).d(
                "SEEK_SESSION op=%s controller=%s",
                seekCommandName(playerCommand),
                controller.javaClass.simpleName,
            )
        }
        // Default handler inherited from the MediaSession.Callback parent (MediaLibrarySession.Callback
        // declares only browse commands) — pure pass-through, zero behavior change.
        return super.onPlayerCommandRequest(session, controller, playerCommand)
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
            // Issue #866 (gh-866): discover command button — routed through the service so the
            // central NZikRadio.toggleDiscover() guest guard + radio/auto-fill precheck apply.
            AutoSessionConstants.ACTION_TOGGLE_DISCOVER -> toggleDiscover()
            // Unlike (dislike) the currently displayed album/artist — the last browsed
            // container of that type; no-op otherwise (see toggleContainerDislike).
            AutoSessionConstants.ACTION_DISLIKE_ALBUM -> scope.launch(NzikDispatchers.DATA) { toggleContainerDislike(PlayerServiceModern.ALBUM) }
            AutoSessionConstants.ACTION_DISLIKE_ARTIST -> scope.launch(NzikDispatchers.DATA) { toggleContainerDislike(PlayerServiceModern.ARTIST) }
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
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(NzikDispatchers.DATA) {
        val pageIndex = if (parentId.contains("_PAGE_")) parentId.substringAfter("_PAGE_").toIntOrNull() ?: -1 else -1
        val actualParentId = if (parentId.contains("_PAGE_")) parentId.substringBefore("_PAGE_") else parentId
        noteCurrentContainer(actualParentId)
        val list = autoBrowseTree.getChildren(parentId, pageIndex, if (::binder.isInitialized) binder else null)
        LibraryResult.ofItemList(ImmutableList.copyOf(list), params)
    }

    /**
     * Remembers the container AA just opened, so the global DISLIKE_* commands can
     * target it: album details (`album/{id}`) and artist details (`artist/{id}`,
     * including their `artist/{id}/{section}` drill-downs). Last browsed container
     * wins; browsing any other node leaves it unchanged, so a stale command of the
     * other type stays a no-op.
     */
    private fun noteCurrentContainer(parentId: String) {
        val parts = parentId.split("/")
        when (parts.firstOrNull()) {
            PlayerServiceModern.ALBUM -> if (parts.size == 2) currentContainer = CurrentContainer(PlayerServiceModern.ALBUM, parts[1])
            PlayerServiceModern.ARTIST -> if (parts.size >= 2) currentContainer = CurrentContainer(PlayerServiceModern.ARTIST, parts[1])
        }
    }

    /**
     * Toggles the phone's dislike flag (unlike) of the currently displayed album or
     * artist — no-op when the last browsed container is not of the requested type.
     * The album/artist library lists hide disliked entries; AutoBrowseTree re-queries
     * on every non-pagination browse, so the next browse reflects the toggle (no
     * explicit cache clear needed here).
     */
    internal suspend fun toggleContainerDislike(type: String) {
        val container = currentContainer ?: return
        if (container.type != type) return
        Timber.tag("AutoSessionCallback").d("AA container unlike: type=$type id=${container.id}")
        when (type) {
            PlayerServiceModern.ALBUM -> database.albumTable.toggleDislike(container.id)
            PlayerServiceModern.ARTIST -> database.artistTable.toggleDislike(container.id)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(NzikDispatchers.DATA) {
        val songId = mediaId.split("/").lastOrNull() ?: mediaId
        database.songTable.findByIdDirect(songId)?.let { song -> 
            if (mediaId.contains("/")) {
                SessionMediaItemMapper.mapSongToMediaItem(song, mediaId.substringBeforeLast("/"), loadArtwork = true)
            } else {
                SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true)
            }
        }?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
    }

    // Issue #606 (gh-606 G1): mapping songs via SessionMediaItemMapper decodes cover bitmaps
    // synchronously, so the whole block runs on DATA instead of the Main-bound `scope` dispatcher.
    // onSetMediaItemsInternal touches no Main-affine state (no binder.player; binder.cache is a
    // thread-safe SimpleCache), and the returned future carries the result to Media3's own thread.
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future(NzikDispatchers.DATA) {
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
        // Triple dislike exclusion mirroring the phone shuffler (Shuffler):
        // disliked songs + songs by disliked artists + songs from disliked
        // albums, each gated by its phone DislikeMode (default Enabled).
        // Applied to every Songs/Albums/Artists queue below; the Disliked
        // category is never filtered. Empty set = no filter. Top is also
        // excluded at the SQL level (EventTable.findSongsMostPlayedBetween
        // drops likedAt == -1), so its in-memory guard is redundant (kept
        // defensively) and it never re-includes them in Disabled mode.
        val excludedIds = DislikedExclusion.excludedSongIds(context, database)
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_LUCKY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_LUCKY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val quickPickSongs = (QuickPicksRepository.trendingList.value + (QuickPicksRepository.relatedPage.value?.songs?.map { it.asSong } ?: emptyList())).distinctBy { it.id }
            // QuickPicks data is populated by the phone UI; in the Android Auto
            // service context it can be empty, so fall back to the local library
            // instead of returning an empty queue (issue #777).
            // The local library fallback never queues a song the phone shuffler
            // excludes (DislikedExclusion) — phone/AA consistency.
            val allSongs = if (quickPickSongs.isEmpty()) {
                database.songTable.sortAll(SongSortBy.DateAdded, SortOrder.Descending, excludeHidden = true).first().filter { it.id !in excludedIds }
            } else {
                quickPickSongs
            }.distinctBy { it.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONG_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ALL_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONG_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ALL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).first().filter { it.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.songTable.sortFavorites(sortBy, sortOrder).first().filter { it.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val downloads = downloadHelper.downloads.value
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = false).first().fastFilter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED && song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_BY.key, OnDeviceSongSortBy.Title) } catch (e: Exception) { OnDeviceSongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val onDeviceSongs = context.getLocalSongs(sortBy, sortOrder).first().keys.filter { it.id !in excludedIds }.toList()
            val allSongs = if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) onDeviceSongs.shuffled() else onDeviceSongs
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.formatTable.sortAllWithSongs(sortBy, sortOrder).first().fastFilter { itf -> val contentLength = itf.format.contentLength; itf.song.id !in excludedIds && contentLength != null && binder.cache.isCached(itf.song.id, 0L, contentLength) }.fastMap { itf -> itf.song }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_TOP_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_TOP_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first().filter { it.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUM_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUM_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.albumTable.allInLibrary().first().flatMap { album -> database.songAlbumMapTable.allSongsOf(album.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.albumTable.allBookmarked().first().flatMap { album -> database.songAlbumMapTable.allSongsOf(album.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTIST_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_LIBRARY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTIST_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_LIBRARY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.artistTable.allInLibrary().first().flatMap { artist -> database.songArtistMapTable.allSongsBy(artist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.artistTable.allFollowing().first().flatMap { artist -> database.songArtistMapTable.allSongsBy(artist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            // Mirror of the phone Library "All" tab queue (HomeLibrary.getSelectedSongs +
            // Shuffler.play): every playlist in display order — YT, pinned and rewind
            // included, no tab gates — flattened and deduplicated by id, then the
            // triple dislike exclusion (DislikeMode gated, like the phone shuffler).
            val sortBy = try { context.preferences.getEnum(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount) } catch (e: Exception) { PlaylistSortBy.SongCount }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.playlistTable.sortPreviews(sortBy, sortOrder).first().flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            // Triple dislike exclusion (re-negotiation 2026-10-01 « suit l'app »): the phone
            // Shuffler.play filters every queue it builds, so the YT/pinned/rewind playlist
            // shuffles mirror that (the LOCAL "All" shuffle already did).
            val allSongs = database.playlistTable.allAsPreview().first().filter { it.playlist.isYoutubePlaylist }.flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }

        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_PINNED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_PINNED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.playlistTable.allAsPreview().first().filter { it.playlist.name.startsWith(PINNED_PREFIX, true) }.flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        // Rewind sub-group shuffles: deduplicated union of the generated playlists of
        // the type (Month -> monthly, Year -> yearly, All -> both + all-time).
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = rewindSongs(RewindPlaylists.Filter.Month).filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = rewindSongs(RewindPlaylists.Filter.Year).filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = rewindSongs(RewindPlaylists.Filter.All).filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        // Disliked category shuffle: only the songs flagged as disliked (likedAt == -1L).
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DISLIKED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DISLIKED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = dislikedSongs().let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        // Unlike categories shuffles: the songs of the disliked albums / artists only.
        // Deliberately NOT filtered with excludedIds — the category IS the exclusion;
        // filtering it would always produce an empty queue.
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_DISLIKED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_DISLIKED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.albumTable.allDisliked().first().flatMap { album -> database.songAlbumMapTable.allSongsOf(album.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_DISLIKED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_DISLIKED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.artistTable.allDisliked().first().flatMap { artist -> database.songArtistMapTable.allSongsBy(artist.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        // The shuffle item carries the playlist id ("PLAYLIST_SHUFFLE/{playlistId}")
        // so the right song list can be resolved (issue #777).
        val playlistShuffleId = AutoMediaIdContract.parsePlaylistShuffle(mediaItems.firstOrNull()?.mediaId)
        if (playlistShuffleId != null) {
            val allSongs = playlistSongs(playlistShuffleId).shuffled()
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
            Timber.tag("AutoSessionCallback").w("Empty playlist shuffle queue: mediaId=%s", mediaItems.first().mediaId)
        }

        var queryList = emptyList<Song>()
        var startIdx = startIndex
        runCatching {
            var songId = ""
            val mediaId = mediaItems.first().mediaId
            val paths = mediaId.split("/")
            when (paths.first()) {
                AutoSessionConstants.ID_QUICK_PICKS -> { 
                    songId = paths[1]
                    val trending = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = 500).first()
                    val relatedSongs = if (trending.isNotEmpty()) {
                        Innertube.relatedPage(videoId = trending.first().id, setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)?.getOrNull()?.songs?.map { it.asSong } ?: emptyList()
                    } else emptyList()
                    val ytmQuickPicks = if (isYouTubeLoggedIn()) {
                        YtMusic.getQuickPicks(setLogin = true).getOrNull()?.map { it.asSong } ?: emptyList()
                    } else emptyList()
                    Timber.tag("AutoSessionCallback").d("Quick picks play list loaded -> trending: ${trending.size}, related: ${relatedSongs.size}, ytb: ${ytmQuickPicks.size}")
                    queryList = (ytmQuickPicks + trending + relatedSongs).distinctBy { it.id } 
                }
                // Triple dislike exclusion (parity): search results accumulated from
                // detail pages can include songs by disliked artists/albums.
                AutoSessionConstants.ID_SEARCH_SONGS -> { songId = paths[2]; queryList = AutoSearchState.searchedSongs.filter { it.id !in excludedIds } }
                AutoSessionConstants.ID_SEARCH_VIDEOS -> { songId = paths[2]; queryList = AutoSearchState.searchedVideos.map { it.asSong } }
                PlayerServiceModern.SEARCHED -> { songId = paths[1]; queryList = AutoSearchState.searchedSongs.filter { it.id !in excludedIds } }
                PlayerServiceModern.SONG -> { songId = paths[1]; queryList = database.songTable.all().first().filter { it.id !in excludedIds } }
                AutoSessionConstants.ID_SONGS_ALL -> { songId = paths[1]; queryList = database.songTable.sortAll(SongSortBy.DateAdded, SortOrder.Descending, excludeHidden = true).first().filter { it.id !in excludedIds } }
                AutoSessionConstants.ID_SONGS_FAVORITES -> { songId = paths[1]; queryList = database.songTable.allFavorites().first().filter { it.id !in excludedIds }.reversed() }
                AutoSessionConstants.ID_SONGS_DOWNLOADED -> { 
                    val downloads = downloadHelper.downloads.value
                    queryList = database.songTable.all(excludeHidden = false).first().fastFilter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED && song.id !in excludedIds }.sortedByDescending { song -> downloads[song.id]?.updateTimeMs ?: 0L }
                    songId = paths[1]
                }
                AutoSessionConstants.ID_SONGS_ONDEVICE -> { songId = paths[1]; queryList = database.songTable.allOnDevice().first().filter { it.id !in excludedIds } }
                AutoSessionConstants.ID_SONGS_CACHED -> {
                    queryList = database.formatTable.allWithSongs().first().fastFilter { itf -> itf.song.totalPlayTimeMs > 0 && itf.song.id !in excludedIds && itf.format.contentLength != null && (if (::binder.isInitialized) binder.cache.isCached(itf.song.id, 0L, itf.format.contentLength ?: 0L) else false) }.reversed().fastMap { itf -> itf.song }
                    songId = paths[1]
                }
                AutoSessionConstants.ID_SONGS_TOP -> {
                    queryList = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first().filter { it.id !in excludedIds }
                    songId = paths[1]
                }
                AutoSessionConstants.ID_SONGS_DISLIKED -> { songId = paths[1]; queryList = dislikedSongs() }
                PlayerServiceModern.ARTIST -> {
                    // MediaId contract: "artist/{artistId}/{songId}" or
                    // "artist/{artistId}/{section}/{songId}" (issue #777).
                    val selection = AutoMediaIdContract.parseSongSelection(mediaId, PlayerServiceModern.ARTIST)
                    if (selection == null) {
                        Timber.tag("AutoSessionCallback").w("Unresolvable artist selection: mediaId=%s", mediaId)
                    } else {
                        songId = selection.songId
                        val artistSongs = if (selection.section != null) AutoSearchState.searchedSongs else database.songArtistMapTable.allSongsBy(selection.containerId).first()
                        // Phone parity (re-negotiation 2026-10-01 « suit l'app »): the tap
                        // selection queue mirrors the unfiltered artist detail list — only
                        // the shuffle (Shuffler.play) applies the triple dislike exclusion.
                        queryList = artistSongs
                    }
                }
                PlayerServiceModern.ALBUM -> {
                    val selection = AutoMediaIdContract.parseSongSelection(mediaId, PlayerServiceModern.ALBUM)
                    if (selection == null) {
                        Timber.tag("AutoSessionCallback").w("Unresolvable album selection: mediaId=%s", mediaId)
                    } else {
                        songId = selection.songId
                        // Phone parity (re-negotiation 2026-10-01 « suit l'app »): the tap
                        // selection queue mirrors the unfiltered album detail list.
                        queryList = database.songAlbumMapTable.allSongsOf(selection.containerId).first()
                        if (queryList.isEmpty()) queryList = AutoSearchState.searchedSongs
                    }
                }
                PlayerServiceModern.PLAYLIST -> {
                    val selection = AutoMediaIdContract.parseSongSelection(mediaId, PlayerServiceModern.PLAYLIST)
                    if (selection == null) {
                        Timber.tag("AutoSessionCallback").w("Unresolvable playlist selection: mediaId=%s", mediaId)
                    } else {
                        val playlistId = selection.containerId
                        songId = selection.songId
                        queryList = playlistSongs(playlistId)
                    }
                }
            }
            startIdx = queryList.indexOfFirst { song -> song.id == songId }.coerceAtLeast(0)
        }
        return MediaSession.MediaItemsWithStartPosition(queryList.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, startIdx, startPositionMs)
    }

    /**
     * Resolves the song list of a playlist detail page, shared by the playlist
     * selection branch and the playlist "Shuffle" item so the two can never
     * drift apart (issue #777). Pseudo-playlist ids (FAVORITES, CACHED, TOP,
     * ONDEVICE, DOWNLOADED) resolve through the local library, numeric ids
     * through the song/playlist map, and online browse ids through the
     * per-playlist scoped state.
     */
    private suspend fun playlistSongs(playlistId: String): List<Song> = when (playlistId) {
        AutoSessionConstants.ID_FAVORITES -> database.songTable.allFavorites().map { it.reversed() }.first()
        AutoSessionConstants.ID_CACHED -> database.formatTable.allWithSongs().map { fl -> fl.fastFilter { itf -> itf.song.totalPlayTimeMs > 0 && itf.format.contentLength != null && (if (::binder.isInitialized) binder.cache.isCached(itf.song.id, 0L, itf.format.contentLength ?: 0L) else false) }.reversed().fastMap { itf -> itf.song } }.first()
        AutoSessionConstants.ID_TOP -> database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first()
        AutoSessionConstants.ID_ONDEVICE -> database.songTable.allOnDevice().first()
        AutoSessionConstants.ID_DOWNLOADED -> {
            downloadHelper.getDownloadManager(context)
            val downloads = downloadHelper.downloads.value
            database.songTable.all(excludeHidden = false).map { fl -> fl.fastFilter { s -> downloads[s.id]?.state == Download.STATE_COMPLETED }.sortedByDescending { s -> downloads[s.id]?.updateTimeMs ?: 0L } }.first()
        }
        // Online playlists keep their songs scoped by playlist id so queues
        // never mix previously opened playlists (issue #777).
        else -> { if (playlistId.toLongOrNull() != null) database.songPlaylistMapTable.allSongsOf(playlistId.toLong()).first() else AutoSearchState.playlistSongsById[playlistId].orEmpty() }
    }

    /**
     * Song list of a Rewind sub-group: the deduplicated union of all generated
     * playlists of that type (Month -> monthly, Year -> yearly, All -> both +
     * all-time, per [RewindPlaylists.matches]). Same shape as the
     * ID_PLAYLISTS_*_SHUFFLE resolvers, filtered by type.
     */
    private suspend fun rewindSongs(group: RewindPlaylists.Filter): List<Song> =
        database.playlistTable.allAsPreview().first()
            .filter { preview -> RewindPlaylists.matches(group, preview.playlist.name) }
            .flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }
            .distinctBy { song -> song.id }

    /**
     * Songs of the Disliked category (likedAt == -1L, sorted with the
     * dedicated HOME_SONGS_DISLIKED_SORT_* keys). Delegates to
     * [DislikedExclusion.dislikedSongs], which the browse branch uses as well,
     * so the shuffle, selection and browse lists can never drift apart.
     */
    private suspend fun dislikedSongs(): List<Song> = DislikedExclusion.dislikedSongs(context, database)

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> = scope.future(NzikDispatchers.DATA) {
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
        // Issue #606 (gh-606 G1): DATA, not Main -- the mapping below decodes cover bitmaps.
        scope.launch(NzikDispatchers.DATA) {
            try {
                database.queueTable.all().first().run {
                    val idx = indexOfFirst { it.position != null }.coerceAtLeast(0)
                    val startPos = getOrNull(idx)?.position ?: 0L
                    val mediaItems = map { itm ->
                        val song = itm.mediaItem.asSong
                        SessionMediaItemMapper.mapSongToMediaItem(song, true, song.isLocal)
                    }
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









