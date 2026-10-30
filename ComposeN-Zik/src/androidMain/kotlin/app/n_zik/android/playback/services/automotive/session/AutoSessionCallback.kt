package app.n_zik.android.playback.services.automotive.session

import android.content.Context
import android.net.Uri
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.playback.services.ArtworkContentProvider
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.relatedPage
import it.fast4x.innertube.utils.from
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import androidx.media3.session.SessionCommands
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.OnDeviceSongSortBy
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.repository.QuickPicksRepository
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.preferences
import app.kreate.android.me.knighthat.utils.getLocalSongs
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.DislikedExclusion
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_QUICK_PICKS
import app.n_zik.android.playback.services.diagnostics.PLAYBACK_DIAG_TAG
import app.n_zik.android.playback.services.diagnostics.SEEK_PLAYER_COMMANDS
import app.n_zik.android.playback.services.diagnostics.seekCommandName
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers

@UnstableApi
class AutoSessionCallback(
    val context: Context,
    val database: Database,
    val downloadHelper: MyDownloadHelper
) : MediaLibrarySession.Callback {
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private var observationJob: Job? = null
    private val selectionRequestLock = Any()
    private val queueApplicationOwner = AutoQueueApplicationOwner()
    private var pendingRequest: AutoQueueApplicationOwner.Request? = null

    internal fun wrapPlayer(player: Player): Player = queueApplicationOwner.wrap(player)
    private var pendingSelection: ListenableFuture<MediaSession.MediaItemsWithStartPosition>? = null
    private val accessEpoch = java.util.concurrent.atomic.AtomicLong()
    private val browsedParents = linkedMapOf<String, Int>()
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

    /** Last browsed album or artist targeted by the dislike action. */
    @Volatile
    private var currentContainer: CurrentContainer? = null

    private data class CurrentContainer(val type: String, val id: String)

    private fun accessPolicy() = AutoAccessPolicy(context, database, downloadHelper, if (::binder.isInitialized) binder else null)

    private val connectionCommands = mutableMapOf<MediaSession.ControllerInfo, Pair<SessionCommands, Player.Commands>>()

    fun observeRepository(session: MediaLibrarySession) {
        observationJob?.cancel()
        observationJob = scope.launch {
            NetworkQualityHelper.observeConnection(context).collect {
                invalidateAccess(session)
            }
        }
    }

    /** Synchronous ownership invalidation also catches rapid profile or offline round trips. */
    fun invalidatePublication() {
        synchronized(selectionRequestLock) {
            accessEpoch.incrementAndGet()
            queueApplicationOwner.invalidate()
            pendingSelection?.cancel(true)
            pendingSelection = null
            autoBrowseTree.invalidateAccess()
            // Provider/policy invalidation makes previously browsed counts unknown too.
            synchronized(browsedParents) { browsedParents.replaceAll { _, _ -> Int.MAX_VALUE } }
        }
    }

    /** Invalidates pending publications and updates visible commands after policy changes. */
    fun invalidateAccess(session: MediaLibrarySession) {
        invalidatePublication()
        connectionCommands.forEach { (controller, commands) ->
            session.setAvailableCommands(controller, commandsForAccess(commands.first), commands.second)
        }
        notifyKnownChildren(session, accessChanged = true)
    }

    private fun commandsForAccess(commands: SessionCommands): SessionCommands {
        if (accessPolicy().online) return commands
        return commands.buildUpon().apply {
            NETWORK_COMMANDS.forEach { remove(it) }
            remove(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
            remove(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT)
        }.build()
    }

    fun refreshArtwork(session: MediaLibrarySession) {
        autoBrowseTree.refreshArtwork()
        notifyKnownChildren(session)
    }

    private fun notifyKnownChildren(session: MediaLibrarySession, accessChanged: Boolean = false) {
        val parents = synchronized(browsedParents) { browsedParents.toMutableMap() }
        val online = accessPolicy().online
        if (accessChanged || PlayerServiceModern.ROOT in parents) {
            parents[PlayerServiceModern.ROOT] = if (online) 5 else 4
        }
        // Offline direct requests may have recorded zero. Discovery is invalidated on
        // reconnect/art refresh; never advertise that stale zero as the online item count.
        parents.replaceAll { parent, count ->
            val actualParent = AutoMediaIdContract.parsePage(parent)?.parentId ?: parent
            if (actualParent == ID_QUICK_PICKS) {
                if (online) Int.MAX_VALUE else 0
            } else count
        }
        parents.forEach { (parent, count) -> session.notifyChildrenChanged(parent, count, null) }
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        connectionCommands.remove(controller)
        super.onDisconnected(session, controller)
    }

    private companion object {
        val NETWORK_COMMANDS = listOf(AutoSessionConstants.CommandToggleDownload, AutoSessionConstants.CommandStartRadio,
            AutoSessionConstants.CommandSearch, AutoSessionConstants.CommandToggleDiscover)
    }

    fun release() {
        synchronized(selectionRequestLock) {
            queueApplicationOwner.invalidate()
            pendingSelection?.cancel(true)
            pendingSelection = null
        }
        queueApplicationOwner.release()
        observationJob?.cancel()
        autoBrowseTree.selectionStore.clear()
        connectionCommands.clear()
        scope.cancel()
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        // Connecting a notification controller must not invalidate rows already displayed by AA.
        if (session.isAutoCompanionController(controller)) currentContainer = null
        val result = MediaSession.ConnectionResult.accept(
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
        connectionCommands[controller] = result.availableSessionCommands to result.availablePlayerCommands
        return MediaSession.ConnectionResult.accept(
            commandsForAccess(result.availableSessionCommands), result.availablePlayerCommands
        )
    }

    override fun onPostConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ) {
        super.onPostConnect(session, controller)
        // Media3 registers the controller only after onConnect returns.
        if (session.isAutoCompanionController(controller)) {
            session.setSessionActivity(controller, null)
        }
    }

    /** Issue #881 (gh-881): observe seek commands requested by AOSP-interop session controllers */
    // onPlayerCommandRequest is @Deprecated in media3 1.10.1 - it is still the ONLY AOSP-interop
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
        // declares only browse commands) - pure pass-through, zero behavior change.
        return super.onPlayerCommandRequest(session, controller, playerCommand)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        if (!accessPolicy().online) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
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
        if (!accessPolicy().online) return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
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
        if (!accessPolicy().allowsCommand(customCommand.customAction)) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        when (customCommand.customAction) {
            AutoSessionConstants.ACTION_TOGGLE_LIKE -> toggleLike()
            AutoSessionConstants.ACTION_TOGGLE_DOWNLOAD -> toggleDownload()
            AutoSessionConstants.ACTION_TOGGLE_SHUFFLE -> toggleShuffle()
            AutoSessionConstants.ACTION_TOGGLE_REPEAT_MODE -> toggleRepeat()
            AutoSessionConstants.ACTION_START_RADIO -> startRadio()
            AutoSessionConstants.ACTION_SEARCH -> actionSearch()
            // Issue #866 (gh-866): discover command button - routed through the service so the
            // central NZikRadio.toggleDiscover() guest guard + radio/auto-fill precheck apply.
            AutoSessionConstants.ACTION_TOGGLE_DISCOVER -> toggleDiscover()
            // Unlike (dislike) the currently displayed album/artist - the last browsed
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
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val epoch = synchronized(selectionRequestLock) {
            synchronized(browsedParents) {
                // Remember first requests before scheduling their load so access changes
                // can notify even a parent whose first result is still in flight.
                browsedParents.putIfAbsent(parentId, Int.MAX_VALUE)
                while (browsedParents.size > 32) browsedParents.remove(browsedParents.keys.first())
                accessEpoch.get()
            }
        }
        return scope.future(NzikDispatchers.DATA) {
            val pageId = AutoMediaIdContract.parsePage(parentId)
            val pageIndex = pageId?.index ?: -1
            val actualParentId = pageId?.parentId ?: parentId
            noteCurrentContainer(actualParentId)
            val list = autoBrowseTree.getChildren(parentId, pageIndex, if (::binder.isInitialized) binder else null)
            synchronized(browsedParents) {
                // Publish known counts and rows together only for the current access epoch.
                if (epoch != accessEpoch.get()) {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                } else {
                    browsedParents[parentId] = list.size
                    while (browsedParents.size > 32) browsedParents.remove(browsedParents.keys.first())
                    LibraryResult.ofItemList(ImmutableList.copyOf(list), params)
                }
            }
        }
    }

    /** Associates container dislike commands with the last browsed detail. */
    private fun noteCurrentContainer(parentId: String) {
        val parts = parentId.split("/")
        when (parts.firstOrNull()) {
            PlayerServiceModern.ALBUM -> if (parts.size == 2) currentContainer = CurrentContainer(PlayerServiceModern.ALBUM, parts[1])
            PlayerServiceModern.ARTIST -> if (parts.size >= 2) currentContainer = CurrentContainer(PlayerServiceModern.ARTIST, parts[1])
        }
    }

    /** Toggles dislike for the last browsed container of the requested type. */
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
        val requestProfile = getActiveProfile(context)
        val epoch = accessEpoch.get()
        if (AutoMediaIdContract.isScopedSelection(mediaId)) {
            val selection = autoBrowseTree.selectionStore.resolve(mediaId)
                ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            val item = selection.items[selection.startIndex].buildUpon().setMediaId(mediaId).build()
            val eligible = accessPolicy().eligible(listOf(item), selection.parentId)
            if (getActiveProfile(context) != requestProfile || epoch != accessEpoch.get() ||
                autoBrowseTree.selectionStore.resolve(mediaId)?.items !== selection.items) {
                return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
            if (eligible.isEmpty()) return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            return@future LibraryResult.ofItem(item, null)
        }
        val songId = mediaId.split('/').lastOrNull() ?: mediaId
        val item = database.songTable.findByIdDirect(songId)?.let { song ->
            if (mediaId.contains('/')) {
                CancellableAutoMediaItemMapper.mapSongToMediaItem(song, mediaId.substringBeforeLast('/'), loadArtwork = true)
            } else CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true)
        }
        val eligible = item?.let { accessPolicy().eligible(listOf(it)).isNotEmpty() } ?: false
        if (getActiveProfile(context) != requestProfile || epoch != accessEpoch.get()) {
            return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
        if (item != null && eligible) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
    }
    // Mapping and queue resolution perform disk work; Media3 receives the result on its own thread.
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = synchronized(selectionRequestLock) {
        val request = queueApplicationOwner.begin()
        pendingRequest = request
        pendingSelection?.cancel(true)
        scope.future(NzikDispatchers.DATA) {
            val requestProfile = getActiveProfile(context)
            val epoch = accessEpoch.get()
            val selectedId = mediaItems.getOrNull(if (startIndex == -1) 0 else startIndex)?.mediaId
            val scopedAction = selectedId?.let(AutoMediaIdContract::isScopedAction) == true
            val capture = selectedId?.let {
                if (scopedAction) autoBrowseTree.selectionStore.resolveAction(it)
                else autoBrowseTree.selectionStore.resolve(it)
            }
            val resolved = onSetMediaItemsInternal(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            val eligible = accessPolicy().eligible(resolved.mediaItems)
            val retainedIndex = eligible.indexOfFirst { it.index == resolved.startIndex }
            val result = if (retainedIndex < 0) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, startPositionMs)
            } else MediaSession.MediaItemsWithStartPosition(eligible.map { it.value }, retainedIndex, resolved.startPositionMs)
            val currentCapture = selectedId?.let {
                if (scopedAction) autoBrowseTree.selectionStore.resolveAction(it)
                else autoBrowseTree.selectionStore.resolve(it)
            }
            if (getActiveProfile(context) != requestProfile || epoch != accessEpoch.get() ||
                (capture != null && currentCapture?.items !== capture.items)) {
                Timber.tag("AutoSessionCallback").w("Access or capture changed before automotive queue publication")
                throw CancellationException("Automotive queue publication invalidated")
            }
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
        }.let { queueApplicationOwner.future(it, request, queueApplicationOwner.applicationLooper) }
            .also { pendingSelection = it }
    }

    private suspend fun onSetMediaItemsInternal(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): MediaSession.MediaItemsWithStartPosition {
        val requestedIndex = if (startIndex == -1) 0 else startIndex
        val selectedId = mediaItems.getOrNull(requestedIndex)?.mediaId
            ?: return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, startPositionMs)
        val scopedAction = AutoMediaIdContract.isScopedAction(selectedId)
        if (AutoMediaIdContract.isScopedSelection(selectedId) || scopedAction) {
            val selection = if (scopedAction) autoBrowseTree.selectionStore.resolveAction(selectedId)
                else autoBrowseTree.selectionStore.resolve(selectedId)
            if (selection == null) {
                Timber.tag("AutoSessionCallback").w("Expired or invalid automotive selection: %s", selectedId)
                return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, startPositionMs)
            }
            val allowed = accessPolicy().eligible(selection.items, selection.parentId)
            val currentSelection = if (scopedAction) autoBrowseTree.selectionStore.resolveAction(selectedId)
                else autoBrowseTree.selectionStore.resolve(selectedId)
            if (currentSelection == null || currentSelection.items !== selection.items || currentSelection.startIndex != selection.startIndex) {
                Timber.tag("AutoSessionCallback").w("Automotive selection invalidated during queue resolution")
                return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, startPositionMs)
            }
            val selectedIndex = if (scopedAction && allowed.isNotEmpty()) 0
                else allowed.indexOfFirst { it.index == selection.startIndex }
            if (selectedIndex < 0) {
                Timber.tag("AutoSessionCallback").w("Selected song excluded from automotive queue: %s", selectedId)
                return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, startPositionMs)
            }
            val queue = allowed.map { it.value }.let {
                if (scopedAction && AutoMediaIdContract.isShuffle(selectedId)) it.shuffled() else it
            }
            return MediaSession.MediaItemsWithStartPosition(queue, selectedIndex, if (scopedAction) 0 else startPositionMs)
        }
        val playlistShuffle = AutoMediaIdContract.parsePlaylistShuffle(selectedId)
        val playlistPlayAll = AutoMediaIdContract.parsePlaylistPlayAll(selectedId)
        val actionParent = when {
            playlistShuffle != null -> "${PlayerServiceModern.PLAYLIST}/$playlistShuffle"
            playlistPlayAll != null -> "${PlayerServiceModern.PLAYLIST}/$playlistPlayAll"
            selectedId == AutoSessionConstants.ID_LUCKY_SHUFFLE -> AutoSessionConstants.ID_QUICK_PICKS
            selectedId.endsWith("_SHUFFLE") -> selectedId.removeSuffix("_SHUFFLE")
            selectedId.endsWith("_PLAY_ALL") -> selectedId.removeSuffix("_PLAY_ALL")
            else -> null
        }
        if (actionParent != null) {
            val displayed = autoBrowseTree.selectionStore.itemsForParent(actionParent)
            if (!displayed.isNullOrEmpty()) {
                val allowed = accessPolicy().eligible(displayed, actionParent).map { it.value }
                val queue = if (AutoMediaIdContract.isShuffle(selectedId)) allowed.shuffled() else allowed
                return MediaSession.MediaItemsWithStartPosition(queue, 0, 0)
            }
        }
        if (playlistPlayAll != null) {
            return MediaSession.MediaItemsWithStartPosition(
                playlistSongs(playlistPlayAll).map { CancellableAutoMediaItemMapper.mapSongToMediaItem(it, loadArtwork = it.isLocal) }, 0, 0,
            )
        }
        val excludedIds = DislikedExclusion.excludedSongIds(context, database)
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_LUCKY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_LUCKY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val quickPickSongs = (QuickPicksRepository.trendingList.value + (QuickPicksRepository.relatedPage.value?.songs?.map { it.asSong } ?: emptyList())).distinctBy { it.id }
            val allSongs = if (quickPickSongs.isEmpty()) {
                database.songTable.sortAll(SongSortBy.DateAdded, SortOrder.Descending, excludeHidden = true).first().filter { it.id !in excludedIds }
            } else {
                quickPickSongs
            }.distinctBy { it.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONG_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ALL_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONG_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ALL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = true).first().filter { it.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_FAVORITES_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.songTable.sortFavorites(sortBy, sortOrder).first().filter { it.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val downloads = downloadHelper.downloads.value
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.songTable.sortAll(sortBy, sortOrder, excludeHidden = false).first().fastFilter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED && song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_BY.key, OnDeviceSongSortBy.Title) } catch (e: Exception) { OnDeviceSongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val onDeviceSongs = context.getLocalSongs(sortBy, sortOrder).first().keys.filter { it.id !in excludedIds }.toList()
            val allSongs = if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) onDeviceSongs.shuffled() else onDeviceSongs
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_BY.key, SongSortBy.Title) } catch (e: Exception) { SongSortBy.Title }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.formatTable.sortAllWithSongs(sortBy, sortOrder).first().fastFilter { itf -> val contentLength = itf.format.contentLength; itf.song.id !in excludedIds && contentLength != null && binder.cache.isCached(itf.song.id, 0L, contentLength) }.fastMap { itf -> itf.song }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_TOP_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_TOP_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).toInt(context.preferences.getInt(MaxTopPlaylistItemsCustomValueKey, 10))).first().filter { it.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUM_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUM_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.albumTable.allInLibrary().first().flatMap { album -> database.songAlbumMapTable.allSongsOf(album.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.albumTable.allBookmarked().first().flatMap { album -> database.songAlbumMapTable.allSongsOf(album.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTIST_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_LIBRARY_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTIST_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_LIBRARY_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.artistTable.allInLibrary().first().flatMap { artist -> database.songArtistMapTable.allSongsBy(artist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_FAVORITES_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_FAVORITES_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.artistTable.allFollowing().first().flatMap { artist -> database.songArtistMapTable.allSongsBy(artist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val sortBy = try { context.preferences.getEnum(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.SongCount) } catch (e: Exception) { PlaylistSortBy.SongCount }
            val sortOrder = try { context.preferences.getEnum(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
            val allSongs = database.playlistTable.sortPreviews(sortBy, sortOrder).first().flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.playlistTable.allAsPreview().first().filter { it.playlist.isYoutubePlaylist }.flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }

        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_PINNED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_PINNED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.playlistTable.allAsPreview().first().filter { it.playlist.name.startsWith(PINNED_PREFIX, true) }.flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }.distinctBy { song -> song.id }.filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        // Rewind sub-group shuffles: deduplicated union of the generated playlists of
        // the type (Month -> monthly, Year -> yearly, All -> both + all-time).
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = rewindSongs(RewindPlaylists.Filter.Month).filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = rewindSongs(RewindPlaylists.Filter.Year).filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = rewindSongs(RewindPlaylists.Filter.All).filter { song -> song.id !in excludedIds }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        // Disliked category shuffle: only the songs flagged as disliked (likedAt == -1L).
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DISLIKED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_SONGS_DISLIKED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = dislikedSongs().let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_DISLIKED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ALBUMS_DISLIKED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.albumTable.allDisliked().first().flatMap { album -> database.songAlbumMapTable.allSongsOf(album.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        if (mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_DISLIKED_SHUFFLE || mediaItems.firstOrNull()?.mediaId == AutoSessionConstants.ID_ARTISTS_DISLIKED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL")) {
            val allSongs = database.artistTable.allDisliked().first().flatMap { artist -> database.songArtistMapTable.allSongsBy(artist.id).first() }.distinctBy { song -> song.id }.let { if (mediaItems.firstOrNull()?.mediaId?.endsWith("_SHUFFLE") == true) it.shuffled() else it }
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
        }
        // The shuffle item carries the playlist id ("PLAYLIST_SHUFFLE/{playlistId}")
        // so the right song list can be resolved (issue #777).
        val playlistShuffleId = AutoMediaIdContract.parsePlaylistShuffle(mediaItems.firstOrNull()?.mediaId)
        if (playlistShuffleId != null) {
            val allSongs = playlistSongs(playlistShuffleId).shuffled()
            if (allSongs.isNotEmpty()) return MediaSession.MediaItemsWithStartPosition(allSongs.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, 0, 0)
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
                    val relatedSongs = if (accessPolicy().online && trending.isNotEmpty()) {
                        Innertube.relatedPage(videoId = trending.first().id, setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)?.getOrNull()?.songs?.map { it.asSong } ?: emptyList()
                    } else emptyList()
                    val ytmQuickPicks = if (accessPolicy().online && isYouTubeLoggedIn()) {
                        YtMusic.getQuickPicks(setLogin = true).getOrNull()?.map { it.asSong } ?: emptyList()
                    } else emptyList()
                    Timber.tag("AutoSessionCallback").d("Quick picks play list loaded -> trending: ${trending.size}, related: ${relatedSongs.size}, ytb: ${ytmQuickPicks.size}")
                    queryList = (ytmQuickPicks + trending + relatedSongs).distinctBy { it.id }
                }
                // Triple dislike exclusion (parity): search results accumulated from
                // detail pages can include songs by disliked artists/albums.
                AutoSessionConstants.ID_SEARCH_SONGS -> { songId = AutoMediaIdContract.parseSearchSelection(mediaId).orEmpty(); queryList = AutoSearchState.searchedSongs.filter { it.id !in excludedIds } }
                AutoSessionConstants.ID_SEARCH_VIDEOS -> { songId = AutoMediaIdContract.parseSearchSelection(mediaId).orEmpty(); queryList = AutoSearchState.searchedVideos.map { it.asSong } }
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
                    queryList = database.formatTable.allWithSongs().first().fastFilter { itf -> itf.song.id !in excludedIds && itf.format.contentLength != null && (if (::binder.isInitialized) binder.cache.isCached(itf.song.id, 0L, itf.format.contentLength ?: 0L) else false) }.reversed().fastMap { itf -> itf.song }
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
                        val artistSongs = if (selection.section != null) emptyList() else database.songArtistMapTable.allSongsBy(selection.containerId).first()
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
            val selectedIndex = queryList.indexOfFirst { song -> song.id == songId }
            if (selectedIndex < 0) {
                Timber.tag("AutoSessionCallback").w("Selected song absent from queue: %s", mediaId)
                queryList = emptyList()
                startIdx = 0
            } else startIdx = selectedIndex
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Timber.tag("AutoSessionCallback").w(error, "Unable to resolve automotive selection: %s", selectedId)
            queryList = emptyList()
            startIdx = 0
        }
        return MediaSession.MediaItemsWithStartPosition(queryList.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal) }, startIdx, startPositionMs)
    }

    /** Resolves local, generated or online playlist songs for legacy selection IDs. */
    private suspend fun playlistSongs(playlistId: String): List<Song> = when (playlistId) {
        AutoSessionConstants.ID_FAVORITES -> database.songTable.allFavorites().map { it.reversed() }.first()
        AutoSessionConstants.ID_CACHED -> database.formatTable.allWithSongs().map { fl -> fl.fastFilter { itf -> itf.format.contentLength != null && (if (::binder.isInitialized) binder.cache.isCached(itf.song.id, 0L, itf.format.contentLength ?: 0L) else false) }.reversed().fastMap { itf -> itf.song } }.first()
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

    /** Returns the deduplicated union of the requested Rewind playlists. */
    private suspend fun rewindSongs(group: RewindPlaylists.Filter): List<Song> =
        database.playlistTable.allAsPreview().first()
            .filter { preview -> RewindPlaylists.matches(group, preview.playlist.name) }
            .flatMap { preview -> database.songPlaylistMapTable.allSongsOf(preview.playlist.id).first() }
            .distinctBy { song -> song.id }

    /** Uses the same disliked-song list as the browse category. */
    private suspend fun dislikedSongs(): List<Song> = DislikedExclusion.dislikedSongs(context, database)

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> = scope.future(NzikDispatchers.DATA) {
        val requestProfile = getActiveProfile(context)
        val epoch = accessEpoch.get()
        val retained = mutableListOf<Pair<String, List<MediaItem>>>()
        val parentalControlEnabled = try { context.preferences.getBoolean(parentalControlEnabledKey, false) } catch (e: Exception) { false }
        val mappedItems = mediaItems.fastMap { item ->
            val songId = item.mediaId.split('/').lastOrNull() ?: item.mediaId
            if (AutoMediaIdContract.isScopedSelection(item.mediaId)) {
                val selection = autoBrowseTree.selectionStore.resolve(item.mediaId)
                if (selection == null) null else {
                    retained += item.mediaId to selection.items
                    val selected = selection.items[selection.startIndex]
                    selected to selection.parentId
                }
            } else {
                (database.songTable.findById(songId).first()?.let(SessionMediaItemMapper::baseMediaItem)
                    ?: item.buildUpon().setMediaId(songId).build()) to null
            }
        }.filterNotNull().filter { (item, _) ->
            !parentalControlEnabled || item.mediaMetadata.extras?.getBoolean(MediaConstants.EXTRAS_KEY_IS_EXPLICIT) != true
        }
        // Parent policies have two eligibility classes. Filter each class once, then restore
        // request indices rather than regrouping output (which would reorder duplicates).
        val accepted = BooleanArray(mappedItems.size)
        val policy = accessPolicy()
        mappedItems.withIndex().groupBy { (_, entry) ->
            entry.second?.let(AutoAccessPolicy::reappliesExclusions) == true
        }.values.forEach { group ->
            policy.eligible(group.map { it.value.first }, group.first().value.second).forEach {
                accepted[group[it.index].index] = true
            }
        }
        if (getActiveProfile(context) != requestProfile || epoch != accessEpoch.get() || retained.any { (id, items) ->
                autoBrowseTree.selectionStore.resolve(id)?.items !== items
            }) {
            Timber.tag("AutoSessionCallback").w("Profile or capture changed before automotive append publication")
            return@future mutableListOf()
        }
        mappedItems.filterIndexed { index, _ -> accepted[index] }.map { it.first }.toMutableList()
    }
    @Deprecated("Deprecated in Java")
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        onPlaybackResumption(mediaSession, controller, true)

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = synchronized(selectionRequestLock) {
        // Metadata-only recent-item requests must not supersede a pending explicit selection.
        // Play may arrive while an explicit selection still resolves and the player is empty.
        // Reuse that replacement even when saved-queue resumption is disabled.
        if (!isForPlayback) return@synchronized loadPlaybackResumption()
        if (pendingSelection?.isCancelled == false && pendingRequest?.let(queueApplicationOwner::pending) == true) {
            pendingRequest?.let(queueApplicationOwner::requestPlayback)
            pendingSelection?.let { return@synchronized it }
        }
        val request = queueApplicationOwner.begin()
        pendingRequest = request
        queueApplicationOwner.future(loadPlaybackResumption(), request, queueApplicationOwner.applicationLooper)
            .also { pendingSelection = it }
    }

    private fun loadPlaybackResumption(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val defaultResult = MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        if (!context.preferences.getBoolean(persistentQueueKey, false)) return Futures.immediateFuture(defaultResult)
        return scope.future(NzikDispatchers.DATA) {
            val requestProfile = getActiveProfile(context)
            val epoch = accessEpoch.get()
            try {
                val queued = database.queueTable.all().first()
                val idx = queued.indexOfFirst { it.position != null }.coerceAtLeast(0)
                val startPos = queued.getOrNull(idx)?.position ?: 0L
                val mediaItems = queued.map { item ->
                    val restored = item.mediaItem.asSong
                    val durable = item.mediaItem.mediaMetadata.extras
                        ?.getString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA)
                        ?.takeUnless(::isTransientArtworkSource)
                    val source = durable ?: if (isTransientArtworkSource(restored.thumbnailUrl)) {
                        database.songTable.findById(restored.id).first()?.thumbnailUrl
                    } else null
                    val song = if (source != null) restored.copy(thumbnailUrl = source) else restored
                    CancellableAutoMediaItemMapper.mapSongToMediaItem(song, true, song.isLocal)
                }
                val available = accessPolicy().eligible(mediaItems)
                if (getActiveProfile(context) != requestProfile || epoch != accessEpoch.get()) {
                    Timber.tag("AutoSessionCallback").w("Access changed before automotive resumption publication")
                    throw CancellationException("Automotive resumption publication invalidated")
                } else {
                    val retainedIndex = available.indexOfFirst { it.index == idx }
                    if (retainedIndex < 0) defaultResult
                    else MediaSession.MediaItemsWithStartPosition(available.map { it.value }, retainedIndex, startPos)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.tag("AutoSessionCallback").w(exception, "Unable to resume automotive playback")
                defaultResult
            }
        }
    }

    private fun isTransientArtworkSource(source: String?): Boolean {
        if (source.isNullOrBlank()) return false
        val uri = Uri.parse(cleanPrefix(source))
        return uri.scheme == "content" && uri.authority == ArtworkContentProvider.AUTHORITY &&
            uri.pathSegments.firstOrNull() in setOf("source", "remote")
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
