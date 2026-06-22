package app.it.fast4x.rimusic.ui.screens.localplaylist

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.LocalPlayerAwareWindowInsets

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import app.it.fast4x.compose.persist.persistList
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.relatedSongs
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PIPED_PREFIX
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.RecommendationsNumber
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongPlaylistMap
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.SwipeableQueueItem
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Dialog
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.components.themed.IconInfo
import app.it.fast4x.rimusic.ui.components.themed.ListenOnYouTube
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.ui.components.themed.Playlist
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.it.fast4x.rimusic.ui.components.themed.ResetThumbnail
import app.it.fast4x.rimusic.ui.components.themed.Synchronize
import app.it.fast4x.rimusic.ui.components.themed.ThumbnailPicker
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.overlay
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.DeletePlaylist
import app.kreate.android.themed.rimusic.component.playlist.PositionLock
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.addToPipedPlaylist
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.autosyncKey
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.checkFileExists
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.completed
import app.it.fast4x.rimusic.utils.deleteFileIfExists
import app.it.fast4x.rimusic.utils.deletePipedPlaylist
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.formatAsTime
import app.it.fast4x.rimusic.utils.getPipedSession
import app.it.fast4x.rimusic.utils.isAtLeastAndroid14
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.isPipedEnabledKey
import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.recommendationsNumberKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.removeFromPipedPlaylist
import app.it.fast4x.rimusic.utils.saveImageToInternalStorage
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import app.it.fast4x.rimusic.utils.syncSongsInPipedPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.utils.ExternalUris
import app.n_zik.android.components.ResetCache
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.playlist.PinPlaylist
import app.n_zik.android.components.playlist.PlaylistSongsSort
import app.n_zik.android.components.playlist.RenamePlaylistDialog
import app.n_zik.android.components.playlist.Reposition
import app.n_zik.android.components.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.tab.DownloadAllSongsDialog
import app.n_zik.android.components.tab.ExportSongsToCSVDialog
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.LikeComponent
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.UUID


@KotlinCsvExperimental
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation", "UnrememberedMutableState")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun LocalPlaylistSongs(
    navController: NavController,
    playlistId: Long,
    onDelete: () -> Unit,
) {
    // Essentials
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    // Settings
    val parentalControlEnabled by rememberPreference( parentalControlEnabledKey, false )
    val isPipedEnabled by rememberPreference( isPipedEnabledKey, false )
    val disableScrollingText by rememberPreference( disableScrollingTextKey, false )
    var isRecommendationEnabled by remember { mutableStateOf(false) }
    var getAlbumVersion by remember { mutableStateOf(false) }
    var showGetAlbumVersionDialogue by remember { mutableStateOf(false) }
    var showGetAlbumVersionDialogueExt by remember { mutableStateOf(false) }
    var showConfirmMatchAllDialog by remember { mutableStateOf(false) }
    var totalSongsToMatch by remember { mutableIntStateOf(0) }
    var songsMatched by remember { mutableIntStateOf(0) }

    // Non-vital
    val pipedSession = getPipedSession()
    val thumbnailUrl = remember { mutableStateOf("") }

    val playlist by remember {
        Database.playlistTable
                .findById( playlistId )
    }.collectAsState( null, Dispatchers.IO )

    val sort = PlaylistSongsSort()
    val items by remember( sort.sortBy, sort.sortOrder ) {
        Database.songPlaylistMapTable
                .sortSongs( playlistId, sort.sortBy, sort.sortOrder )
                .flowOn( Dispatchers.IO )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )
    var itemsOnDisplay by persistList<Song>("localPlaylist/$playlistId/songs/on_display")

    val itemSelector = ItemSelector<Song>()

    fun getSongs() = itemSelector.ifEmpty { itemsOnDisplay }
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val search = Search(lazyListState)

    if (showGetAlbumVersionDialogue){
        app.it.fast4x.rimusic.ui.components.themed.InProgressDialog(
            total = totalSongsToMatch,
            done = songsMatched,
            text = "Matching songs..."
        )
    }

    if (showGetAlbumVersionDialogueExt){
        app.it.fast4x.rimusic.ui.components.themed.InProgressDialog(
            total = totalSongsToMatch,
            done = songsMatched,
            text = "Matching songs...",
            onDismiss = {showGetAlbumVersionDialogueExt = false}
        )
    }

    if (showConfirmMatchAllDialog) {
        app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog(
            text = "Do you really want to match all songs to their Album/Audio version?",
            onDismiss = { showConfirmMatchAllDialog = false },
            onConfirm = {
                getAlbumVersion = true
                showGetAlbumVersionDialogue = true
                showConfirmMatchAllDialog = false
            }
        )
    }

    val unmatchedExt = remember(items) { items.any { song -> song.id == (app.it.fast4x.rimusic.cleanPrefix(song.title ?: "")+(song.artistsText ?: "")).filter{it.isLetterOrDigit()} } }
    if (unmatchedExt){
        showGetAlbumVersionDialogueExt = true
        LaunchedEffect(unmatchedExt) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val matchedItems = items.filter{song -> song.id == (app.it.fast4x.rimusic.cleanPrefix(song.title ?: "")+(song.artistsText ?: "")).filter{it.isLetterOrDigit()}}
                totalSongsToMatch = matchedItems.size
                songsMatched = 0

                val jobs = mutableListOf<kotlinx.coroutines.Job>()
                matchedItems.forEachIndexed { index, song ->
                    jobs.add(coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        app.n_zik.android.utils.getAlbumVersionFromVideo(
                            song = song,
                            playlistId = playlistId,
                            position = index,
                            playlist = playlist
                        )
                    })
                }
                while(jobs.isNotEmpty()){
                    val oldSize = jobs.size
                    jobs.removeIf{it.isCompleted}
                    songsMatched += oldSize - jobs.size
                    kotlinx.coroutines.delay(10)
                }
                showGetAlbumVersionDialogueExt = false
                getAlbumVersion = false
            }
        }
    }

    LaunchedEffect(getAlbumVersion) {
        if (!getAlbumVersion) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val matchedItems = items.filter { (it.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com") == false) && !(it.id.startsWith(app.it.fast4x.rimusic.LOCAL_KEY_PREFIX)) }
            totalSongsToMatch = matchedItems.size
            songsMatched = 0

            val jobs = mutableListOf<kotlinx.coroutines.Job>()
            matchedItems.forEachIndexed { index, song ->
                jobs.add(coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    app.n_zik.android.utils.getAlbumVersionFromVideo(
                        song = song,
                        playlistId = playlistId,
                        position = index,
                        playlist = playlist
                    )
                })
            }
            while(jobs.isNotEmpty()){
                val oldSize = jobs.size
                jobs.removeIf{it.isCompleted}
                songsMatched += oldSize - jobs.size
                kotlinx.coroutines.delay(10)
            }
            showGetAlbumVersionDialogue = false
            getAlbumVersion = false
        }
    }
    val shuffle = SongShuffler ( ::getSongs )
    val renameDialog = RenamePlaylistDialog { playlist }
    val exportDialog = ExportSongsToCSVDialog(
        playlistBrowseId = playlist?.browseId.orEmpty(),
        playlistName = playlist?.name ?: "",
        songs = ::getSongs
    )
    val deleteDialog = DeletePlaylist {
        Database.asyncTransaction {
            playlist?.let( playlistTable::delete )
        }

        if (
            playlist?.name?.startsWith(PIPED_PREFIX) == true
            && isPipedEnabled
            && pipedSession.token.isNotEmpty()
        )
            deletePipedPlaylist(
                context = context,
                coroutineScope = coroutineScope,
                pipedSession = pipedSession.toApiSession(),
                id = UUID.fromString(playlist?.browseId)
            )

        onDismiss()

        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED)
            navController.popBackStack()
    }
    val renumberDialog = Reposition(playlistId)
    val downloadAllDialog = DownloadAllSongsDialog ( ::getSongs )
    val deleteDownloadsDialog = DeleteAllDownloadedSongsDialog ( ::getSongs )
    val editThumbnailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) {
            val thumbnailName = "playlist_${playlist?.id}"
            val permaUri = saveImageToInternalStorage(context, uri, "thumbnail", thumbnailName)
            thumbnailUrl.value = permaUri.toString()
        } else {
            Toaster.w( R.string.thumbnail_not_selected )
        }
    }
    val pin = PinPlaylist( playlist )
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }
    LaunchedEffect( itemSelector.isActive ) {
        // Setting this field to true means disable it
        if( itemSelector.isActive )
            positionLock.isFirstIcon = true
    }
    // Either position lock or item selector can be turned on at a time
    LaunchedEffect( positionLock.isFirstIcon ) {
        if( !positionLock.isFirstIcon ) {
            // Open to move position
            itemSelector.isActive = false
            // Disable smart recommendation, it breaks the index
            isRecommendationEnabled = false
        }
    }

    val playNext = PlayNext {
        binder?.player?.addNext( getMediaItems(), appContext() )

        // Turn of selector clears the selected list
        itemSelector.isActive = false
    }
    val enqueue = Enqueue {
        binder?.player?.enqueue( getMediaItems(), context )

        // Turn of selector clears the selected list
        itemSelector.isActive = false
    }
    val addToFavorite = LikeComponent( ::getSongs )

    val addToPlaylist = PlaylistsMenu.init(
        navController,
        {
            if( it.playlist.name.startsWith(PIPED_PREFIX)
                && isPipedEnabled
                && pipedSession.token.isNotEmpty()
            )
                addToPipedPlaylist(
                    context = context,
                    coroutineScope = coroutineScope,
                    pipedSession = pipedSession.toApiSession(),
                    id = UUID.fromString(it.playlist.browseId),
                    videos = getSongs().map( Song::id )
                )

            getMediaItems()
        },
        { throwable, preview ->
            Timber.e( "Failed to add songs to playlist ${preview.playlist.name} on LocalPlaylistSongs" )
            throwable.printStackTrace()
        },
        {
            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    )

    fun sync() {
        playlist?.let {
            if ( !it.name.startsWith(PIPED_PREFIX, true) ) {
                Database.asyncTransaction {
                    runBlocking(Dispatchers.IO) {
                        withContext(Dispatchers.IO) {
                            Innertube.playlistPage(
                                BrowseBody(
                                    browseId = it.browseId
                                        ?: ""
                                )
                            )
                                ?.completed()
                        }
                    }?.getOrNull()?.let { remotePlaylist ->
                        songPlaylistMapTable.clear( playlistId )

                        remotePlaylist.songsPage
                                      ?.items
                                      ?.map(Innertube.SongItem::asMediaItem)
                                      ?.let { mediaItems ->
                                          mapIgnore( it, *mediaItems.toTypedArray() )
                                      }
                    }
                }
            } else {
                syncSongsInPipedPlaylist(
                    context = context,
                    coroutineScope = coroutineScope,
                    pipedSession = pipedSession.toApiSession(),
                    idPipedPlaylist = UUID.fromString(
                        it.browseId
                    ),
                    playlistId = it.id

                )
            }
        }
    }
    val syncComponent = Synchronize { sync() }
    val listenOnYT = ListenOnYouTube {
        val browseId = playlist?.browseId?.removePrefix( "VL" )

        binder?.player?.pause()
        uriHandler.openUri( ExternalUris.youtubePlaylist(browseId ?: "") )
    }
    val resetCache = ResetCache( ::getSongs )

    fun openEditThumbnailPicker() {
        editThumbnailLauncher.launch("image/*")
    }
    val thumbnailPicker = ThumbnailPicker { openEditThumbnailPicker() }

    fun resetThumbnail() {
        if(thumbnailUrl.value == "") {
            Toaster.w( R.string.no_thumbnail_present )
            return
        }
        val thumbnailName = "thumbnail/playlist_${playlist?.id}"
        val retVal = deleteFileIfExists(context, thumbnailName)
        if(retVal == true){
            Toaster.s( R.string.removed_thumbnail )
            thumbnailUrl.value = ""
        } else
            Toaster.e( R.string.failed_to_remove_thumbnail )
    }
    val resetThumbnail = ResetThumbnail { resetThumbnail() }

    val locator = Locator( lazyListState, ::getSongs )

    //<editor-fold defaultstate="collapsed" desc="Smart recommendation">
    val recommendationsNumber by rememberPreference( recommendationsNumberKey, RecommendationsNumber.Adaptive )
    var relatedSongs by rememberSaveable {
        // SongEntity before Int in case random position is equal
        mutableStateOf( emptyMap<Song, Int>() )
    }
    var isRecommendationsLoading by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect( isRecommendationEnabled ) {
        if( !isRecommendationEnabled ) {
            relatedSongs = emptyMap()
            isRecommendationsLoading = false
            return@LaunchedEffect
        }

        /*
            This process will be run before [items]
               most of the time.
            When it does, an exception will
               be thrown because [items] is not ready yet.
            To make sure that it is ready to use, a
               delay is set to suspend the thread.
        */
        while( items.isEmpty() )
            delay( 100L )

        isRecommendationsLoading = true

        val targetRecommendations = recommendationsNumber.calculateAdaptiveRecommendations(items.size)
        val allRelatedSongs = mutableListOf<Song>()
        val existingSongIds = items.map { it.id }.toSet()
        
        // For large playlists, make more requests to get enough recommendations
        val numberOfRequests = when {
            items.size <= 100 -> 1
            items.size <= 500 -> 3
            items.size <= 1000 -> 5
            items.size <= 2000 -> 8
            else -> 10
        }
        
        // Select random songs from the playlist to use as seeds
        val seedSongs = items.shuffled().take(numberOfRequests)
        
        for (seedSong in seedSongs) {
            try {
                val requestBody = NextBody(videoId = seedSong.id)
                val relatedSongsResult = Innertube.relatedSongs(requestBody)?.getOrNull()
                
                relatedSongsResult?.songs?.forEach { songItem ->
                    // Filter out songs that are already in the playlist
                    if (!existingSongIds.contains(songItem.info?.endpoint?.videoId)) {
                        val prefix = if (songItem.explicit) EXPLICIT_PREFIX else ""
                        val song = Song(
                            id = "$prefix${songItem.info!!.endpoint!!.videoId!!}",
                            title = songItem.info!!.name!!,
                            artistsText = songItem.authors?.joinToString { author -> author.name ?: "" },
                            durationText = songItem.durationText,
                            thumbnailUrl = songItem.thumbnail?.url
                        )
                        
                        // Avoid duplicates
                        if (!allRelatedSongs.any { it.id == song.id }) {
                            allRelatedSongs.add(song)
                        }
                    }
                }
                
                // Small delay between requests
                if (numberOfRequests > 1) delay(200L)
                
            } catch (e: Exception) {
                // Continue with other requests even if one fails
                continue
            }
        }
        
        // Take the target number of recommendations and assign stable positions
        // Note: We don't force the exact target number because:
        // 1. YouTube doesn't always return 20 songs per request
        // 2. Some songs are filtered out (already in playlist)
        // 3. Some requests may fail
        // 4. Better to have fewer quality recommendations than many poor ones
        val finalRecommendations = allRelatedSongs.take(targetRecommendations)
        val recommendationsWithPositions = finalRecommendations.associate { song ->
            song to (0..items.size).random()
        }
        
        relatedSongs = recommendationsWithPositions
        isRecommendationsLoading = false
        
        // Enable position lock
        positionLock.isFirstIcon = true
    }
    //</editor-fold>
    LaunchedEffect( items, relatedSongs, search.inputValue, parentalControlEnabled ) {
        val baseList = items.toMutableList()
        
        if (isRecommendationEnabled && relatedSongs.isNotEmpty()) {
            // Use the memorized positions to maintain stability
            relatedSongs.forEach { (song, position) ->
                if (!baseList.any { it.id == song.id }) {
                    // Use the memorized position, but ensure it's within bounds
                    val safePosition = position.coerceIn(0, baseList.size)
                    baseList.add( safePosition, song )
                }
            }
        }
        
        baseList
             .distinctBy( Song::id )
             .filter { !parentalControlEnabled || !it.title.startsWith( EXPLICIT_PREFIX ) }
             .filter { song ->
                 // Without cleaning, user can search explicit songs with "e:"
                 // I kinda want this to be a feature, but it seems unnecessary
                 val containsName = song.cleanTitle().contains(search.inputValue, true)
                 val containsArtist = song.cleanArtistsText().contains(search.inputValue, true)

                 containsName || containsArtist
             }
            .let { itemsOnDisplay = it }
    }
    LaunchedEffect( playlist?.name ) {
//        renameDialog.playlistName = playlistPreview?.playlist?.name?.let { name ->
//            if( name.startsWith( MONTHLY_PREFIX, true ) )
//                getTitleMonthlyPlaylist(context, name.substringAfter(MONTHLY_PREFIX))
//            else
//                name.substringAfter( PINNED_PREFIX )
//                    .substringAfter( PIPED_PREFIX )
//        } ?: "Unknown"

        val thumbnailName = "thumbnail/playlist_${playlistId}"
        val presentThumbnailUrl: String? = checkFileExists(context, thumbnailName)
        if (presentThumbnailUrl != null) {
            thumbnailUrl.value = presentThumbnailUrl
        }
    }

    var autosync by rememberPreference(autosyncKey, false)

    

    val hapticFeedback = LocalHapticFeedback.current
    val reorderingState = rememberReorderableLazyListState(
        lazyListState = lazyListState
    ) { from, to ->
        val mutableItems = itemsOnDisplay.toMutableList()
        val fromIndex = mutableItems.indexOfFirst { it.id == from.key }
        val toIndex = mutableItems.indexOfFirst { it.id == to.key }
        
        if (fromIndex != -1 && toIndex != -1) {
            val movedSong = mutableItems.removeAt( fromIndex )
            mutableItems.add( toIndex, movedSong )
            itemsOnDisplay = mutableItems

            CoroutineScope( Dispatchers.Default ).launch {
                Database.asyncTransaction {
                    mutableItems.forEachIndexed { index, song ->
                        Database.songPlaylistMapTable.updatePosition( playlistId, song.id, index )
                    }
                }
            }
        }
    }

    renameDialog.Render()
    exportDialog.Render()
    deleteDialog.Render()
    (renumberDialog as Dialog).Render()
    downloadAllDialog.Render()
    deleteDownloadsDialog.Render()

    val playlistThumbnailSizeDp = Dimensions.thumbnails.playlist
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val rippleIndication = ripple(bounded = false)

    val playlistNotMonthlyType =
        playlist?.name?.startsWith(MONTHLY_PREFIX, 0, true) == false


    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
                .fillMaxWidth()
    ) {
        //LookaheadScope {
        LazyColumn(
            state = lazyListState,
            //contentPadding = LocalPlayerAwareWindowInsets.current
            //    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            //    .asPaddingValues(),
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {

                    HeaderWithIcon(
                        title = cleanPrefix( playlist?.name ?: "" ),
                        iconId = R.drawable.playlist,
                        enabled = true,
                        showIcon = false,
                        modifier = Modifier
                            .padding(bottom = 8.dp),
                        onClick = {}
                    )

                }

                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        //.background(colorPalette().background4)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(
                            color = colorPalette().background1,
                            shape = app.n_zik.android.thumbnailShape()
                        )
                ) {

                    playlist?.let {
                        Playlist(
                            playlist = it,
                            songCount = items.size,
                            thumbnailSizeDp = playlistThumbnailSizeDp,
                            thumbnailSizePx = playlistThumbnailSizePx,
                            alternative = true,
                            showName = false,
                            modifier = Modifier
                                .padding(top = 14.dp),
                            disableScrollingText = disableScrollingText,
                            thumbnailUrl = if (thumbnailUrl.value == "") null else thumbnailUrl.value
                        )
                    }


                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            //.fillMaxHeight()
                            .padding(end = 10.dp)
                            .fillMaxWidth(if (isLandscape) 0.90f else 0.80f)
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val totalSongs = if (isRecommendationEnabled && !isRecommendationsLoading && relatedSongs.isNotEmpty()) {
                            items.size + relatedSongs.size
                        } else {
                            items.size
                        }
                        IconInfo(
                            title = totalSongs.toString(),
                            icon = painterResource(R.drawable.musical_notes)
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        val recommendedSongsDuration = if (isRecommendationEnabled && !isRecommendationsLoading) {
                            relatedSongs.keys.sumOf { durationTextToMillis(it.durationText ?: "0:0") }
                        } else {
                            0L
                        }
                        val totalDuration = items.sumOf { durationTextToMillis(it.durationText ?: "0:0") } + recommendedSongsDuration
                        IconInfo(
                            title = formatAsTime( totalDuration ),
                            icon = painterResource(R.drawable.time)
                        )
                        if (isRecommendationEnabled) {
                            Spacer(modifier = Modifier.height(5.dp))
                            if (isRecommendationsLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.smart_shuffle),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                }
                            } else {
                                IconInfo(
                                    title = relatedSongs.size.toString(),
                                    icon = painterResource(R.drawable.smart_shuffle)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(30.dp))
                    }

                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp), // Standard IconButton size
                            contentAlignment = Alignment.Center
                        ) {
                            val unmatchedSongsCount = items.filter { (it.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com") == false) && !(it.id.startsWith(app.it.fast4x.rimusic.LOCAL_KEY_PREFIX)) }.size
                            if (unmatchedSongsCount > 0) {
                                HeaderIconButton(
                                    icon = app.n_zik.android.R.drawable.alert,
                                    enabled = true,
                                    color = colorPalette().text,
                                    modifier = Modifier.clip(uiRoundnessShape()),
                                    onClick = {
                                        showConfirmMatchAllDialog = true
                                    },
                                    onLongClick = {
                                        Toaster.i("Match songs to album versions")
                                    }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            if (isRecommendationsLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = colorPalette().text,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                HeaderIconButton(
                                    icon = R.drawable.smart_shuffle,
                                    enabled = true,
                                    color = if (isRecommendationEnabled) colorPalette().text else colorPalette().textDisabled,
                                    modifier = Modifier.clip(uiRoundnessShape()),
                                            onClick = {
                                                isRecommendationEnabled = !isRecommendationEnabled
                                            },
                                            onLongClick = {
                                                Toaster.i( R.string.info_smart_recommendation )
                                            }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        shuffle.ToolBarButton()
                        Spacer(modifier = Modifier.height(10.dp))
                        search.ToolBarButton()
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val toolbarButtons = remember { mutableStateListOf<Button>() }

                LaunchedEffect(
                    playlistNotMonthlyType,
                    sort.sortBy,
                    sort.sortOrder,
                    playlist?.browseId
                ) {
                    toolbarButtons.clear()
                    if (playlistNotMonthlyType)
                        toolbarButtons.add( pin )
                    if ( sort.sortBy == PlaylistSongSortBy.Custom ) {
                        toolbarButtons.add( positionLock )
                        toolbarButtons.add( renumberDialog )
                    }

                    toolbarButtons.add( downloadAllDialog )
                    toolbarButtons.add( deleteDownloadsDialog )
                    toolbarButtons.add( itemSelector )
                    toolbarButtons.add( playNext )
                    toolbarButtons.add( enqueue )
                    toolbarButtons.add( addToFavorite )
                    toolbarButtons.add( addToPlaylist )
                    if( !playlist?.browseId.isNullOrBlank() ) {
                        toolbarButtons.add( syncComponent )
                        toolbarButtons.add( listenOnYT )
                    }
                    toolbarButtons.add( renameDialog )
                    toolbarButtons.add( deleteDialog )
                    toolbarButtons.add( exportDialog )
                    toolbarButtons.add( thumbnailPicker )
                    toolbarButtons.add( resetThumbnail )
                    toolbarButtons.add( resetCache )
                }

                TabToolBar.Buttons( toolbarButtons )

                if ( autosync && playlist?.browseId.isNullOrBlank() ) {
                    sync()
                }

                Spacer(modifier = Modifier.height(10.dp))

                /*        */
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .fillMaxWidth()
                ) {

                    sort.ToolBarButton()

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) { locator.ToolBarButton() }

                }

                Column { search.SearchBar( this ) }
            }

            itemsIndexed(
                items = itemsOnDisplay,
                key = { _, song -> song.id },
                contentType = { _, song -> song },
            ) { index, song ->

                    ReorderableItem(
                        reorderingState,
                        key = song.id
                    ) { isDraggingItem ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(2f)
                        ) {
                            val isLocal by remember { derivedStateOf { song.asMediaItem.isLocal } }

                            // Drag anchor
                            if ( !positionLock.isLocked() && sort.sortBy == PlaylistSongSortBy.Custom && sort.sortOrder == app.it.fast4x.rimusic.enums.SortOrder.Ascending ) {
                                Box(
                                    modifier = Modifier.padding( end = 16.dp ) // Accommodate horizontal padding of SongItem
                                                       .size( 24.dp )
                                                       .zIndex( 2f )
                                                       .align( Alignment.CenterEnd ),
                                    contentAlignment = Alignment.Center
                                ) {

                                    IconButton(
                                        icon = R.drawable.reorder,
                                        color = colorPalette().textDisabled,
                                        indication = rippleIndication,
                                        onClick = {},
                                        modifier = Modifier
                                            .draggableHandle(
                                                onDragStarted = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )
                                    )
                                }
                            }

                            SwipeableQueueItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(song.asMediaItem)
                        },
                        onRemoveFromQueue = {
                            Database.asyncTransaction {
                                songPlaylistMapTable.deleteBySongId( song.id, playlistId )
                            }


                            if (playlist?.name?.startsWith(PIPED_PREFIX) == true && isPipedEnabled && pipedSession.token.isNotEmpty()) {
                                Timber.d("MediaItemMenu LocalPlaylistSongs onSwipeToLeft browseId ${playlist?.browseId}")
                                removeFromPipedPlaylist(
                                    context = context,
                                    coroutineScope = coroutineScope,
                                    pipedSession = pipedSession.toApiSession(),
                                    id = UUID.fromString(playlist?.browseId),
                                    index
                                )
                            }

                            Toaster.s(
                                "${context.resources.getString( R.string.deleted )} \"${song.asMediaItem.mediaMetadata.title}\" - \"${song.asMediaItem.mediaMetadata.artist}\""
                            )
                        },
                        onDownload = {
                            binder?.cache?.removeResource(song.asMediaItem.mediaId)
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( song.id )
                            }

                            if (!isLocal) {
                                manageDownload(
                                    context = context,
                                    mediaItem = song.asMediaItem,
                                    downloadState = song.isLocal
                                )
                            }
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(
                                song.asMediaItem,
                                context
                            )
                        },
                    ) {
                        SongItem(
                            song = song,
                            itemSelector = itemSelector,
                            navController = navController,
                            isRecommended = song in relatedSongs,
                            modifier = Modifier,

                            trailingContent = {
                                if( !positionLock.isLocked() && sort.sortBy == PlaylistSongSortBy.Custom && sort.sortOrder == app.it.fast4x.rimusic.enums.SortOrder.Ascending )
                                    // Create a fake box to store drag anchor and checkbox
                                    Box( Modifier.width( 24.dp ) )
                            },
                            thumbnailOverlay = {
                                if (sort.sortBy == PlaylistSongSortBy.PlayTime) {
                                    BasicText(
                                        text = song.formattedTotalPlayTime,
                                        style = typography().xxs.semiBold.center.color(
                                            colorPalette().onOverlay
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                                           .background(
                                                               brush = Brush.verticalGradient(
                                                                   colors = listOf(
                                                                       Color.Transparent,
                                                                       colorPalette().overlay
                                                                   )
                                                               ),
                                                               shape = thumbnailShape()
                                                           )
                                                           .padding( horizontal = 8.dp, vertical = 4.dp )
                                                           .align( Alignment.BottomCenter )
                                    )
                                }
                            },
                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    itemsOnDisplay.map( Song::asMediaItem ),
                                    index
                                )

                                /*
                                    Due to the small size of checkboxes,
                                    we shouldn't disable [itemSelector]
                                 */

                                search.hideIfEmpty()
                            }
                        )
                    }
                }
            }
        }

            item(
                key = "footer",
                contentType = 0,
            ) {
                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)

        val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
        if ( UiType.ViMusic.isCurrent() && showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                visible = !reorderingState.isAnyItemDragging,
                onClick = {
                    getMediaItems().let { songs ->
                        if (songs.isNotEmpty()) {
                            binder?.stopRadio()
                            binder?.player
                                  ?.forcePlayFromBeginning( songs.shuffled() )
                        }
                    }
                }
            )
    }
}








