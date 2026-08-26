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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
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

import app.n_zik.android.appContext
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.RecommendationsNumber
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongPlaylistMap
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.isUnmatched
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

import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.autosyncKey
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.checkFileExists
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.completed
import app.it.fast4x.rimusic.utils.deleteFileIfExists

import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.formatAsTime

import app.it.fast4x.rimusic.utils.isAtLeastAndroid14
import app.it.fast4x.rimusic.utils.isLandscape

import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.recommendationsNumberKey
import app.it.fast4x.rimusic.ui.components.themed.TextFieldDialog
import androidx.core.net.toUri
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.parseArtists
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.localPlaylistToolbarOrderKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.Preference
import app.n_zik.android.download.utils.MyDownloadHelper
import androidx.media3.exoplayer.offline.Download

import app.it.fast4x.rimusic.utils.saveImageToInternalStorage
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showFloatingIconKey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import app.it.fast4x.rimusic.utils.ExternalUris
import app.n_zik.android.components.ResetCache
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.playlist.PinPlaylist
import app.n_zik.android.components.playlist.PlaylistSongsSort
import app.n_zik.android.components.dialog.playlist.RenamePlaylistDialog
import app.n_zik.android.components.playlist.Reposition
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.LikeComponent
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.playback.utils.Shuffler
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.UUID
import app.it.fast4x.rimusic.LOCAL_KEY_PREFIX
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.components.themed.DefaultDialog
import app.it.fast4x.rimusic.ui.components.themed.InProgressDialog
import app.it.fast4x.rimusic.ui.components.themed.InputTextField
import app.n_zik.android.components.dialog.search.MatchResultsDialog
import app.n_zik.android.components.tab.ImportPlaylistsMenu
import app.n_zik.android.components.tab.ImportSongsFromCSV
import app.n_zik.android.components.tab.ImportSongsFromServices
import app.n_zik.android.core.database.ImportSong
import app.n_zik.android.components.dialog.settings.LocalPlaylistToolbarSettingsDialog
import app.n_zik.android.utils.getAlbumVersionFromVideo
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray


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

    val disableScrollingText by rememberPreference( disableScrollingTextKey, false )
    var isRecommendationEnabled by remember { mutableStateOf(false) }
    var getAlbumVersion by remember { mutableStateOf(false) }
    var showGetAlbumVersionDialogue by remember { mutableStateOf(false) }
    var showGetAlbumVersionDialogueExt by remember { mutableStateOf(false) }
    var cancelMatchExt by remember { mutableStateOf(false) }
    var matchRunning by remember { mutableStateOf(false) }
    var matchRunningExt by remember { mutableStateOf(false) }
    var matchJob by remember { mutableStateOf<Job?>(null) }
    var matchJobExt by remember { mutableStateOf<Job?>(null) }
    var showConfirmMatchAllDialog by remember { mutableStateOf(false) }
    var totalSongsToMatch by remember { mutableStateOf(0) }
    var songsMatched by remember { mutableStateOf(0) }
    var retryMatchMode by remember { mutableStateOf(false) }
    var retryMatchSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var showMatchResultsDialog by remember { mutableStateOf(false) }
    var matchResultsMatched by remember { mutableStateOf(0) }
    var matchResultsFailed by remember { mutableStateOf(0) }
    var matchResultsMerged by remember { mutableStateOf(0) }
    var matchResultsFailedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Non-vital

    val thumbnailUrl = remember { mutableStateOf("") }

    val playlist by remember {
        Database.playlistTable
                .findById( playlistId )
    }.collectAsState( null, Dispatchers.IO )

    val sort = PlaylistSongsSort(playlistId)

    val items by remember( sort.sortBy, sort.sortOrder ) {
        if (sort.sortBy == PlaylistSongSortBy.Downloaded) {
            val downloaded: List<String> = MyDownloadHelper.downloads
                                                           .value
                                                           .values
                                                           .filter { it.state == Download.STATE_COMPLETED }
                                                           .map { it.request.id }
            Database.songPlaylistMapTable
                    .sortSongs( playlistId, PlaylistSongSortBy.Title, SortOrder.Ascending )
                    .map { list ->
                        if (sort.sortOrder == SortOrder.Ascending) {
                            list.sortedByDescending { it.id in downloaded }
                        } else {
                            list.sortedBy { it.id in downloaded }
                        }
                    }
                    .flowOn( Dispatchers.IO )
                    .distinctUntilChanged()
        } else {
            Database.songPlaylistMapTable
                    .sortSongs( playlistId, sort.sortBy, sort.sortOrder )
                    .flowOn( Dispatchers.IO )
                    .distinctUntilChanged()
        }
    }.collectAsState( emptyList(), Dispatchers.IO )
    var itemsOnDisplay by persistList<Song>("localPlaylist/$playlistId/songs/on_display")

    val importedBrowseIds = remember {
        listOf("SPOTIFY_IMPORT", "RIPLAY_IMPORT") +
        listOf("SPOTIFY_IMPORT_HOMESONGS", "RIPLAY_IMPORT_HOMESONGS")
    }
    LaunchedEffect( playlist?.browseId, items ) {
        if (items.isEmpty()) return@LaunchedEffect
        val browseId = playlist?.browseId.orEmpty()
        val isSpotifyRiplay = importedBrowseIds.any { browseId == it || browseId.startsWith("${it}_") }
        val hasImportedSongs = items.any { it.totalPlayTimeMs == 1L }
        val prefs = appContext().preferences
        val currentSort = prefs.getString("PlaylistSongsSortBy_$playlistId", null)
        // Only force Custom if no saved preference exists AND playlist has imports
        if ((isSpotifyRiplay || hasImportedSongs) && currentSort == null) {
            sort.sortBy = PlaylistSongSortBy.Custom
        }
    }

    val itemSelector = ItemSelector<Song>()

    fun getSongs() = itemSelector.ifEmpty { itemsOnDisplay }
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val search = Search(lazyListState)

    if (showGetAlbumVersionDialogue){
        InProgressDialog(
            total = totalSongsToMatch,
            done = songsMatched,
            text = stringResource(R.string.matching_songs),
            onDismiss = {
                showGetAlbumVersionDialogue = false
                cancelMatchExt = true
                matchJob?.cancel()
            }
        )
    }

    if (showGetAlbumVersionDialogueExt){
        InProgressDialog(
            total = totalSongsToMatch,
            done = songsMatched,
            text = stringResource(R.string.matching_songs),
            onDismiss = {
                showGetAlbumVersionDialogueExt = false
                cancelMatchExt = true
                matchJobExt?.cancel()
            }
        )
    }
    // Note: cancel only closes the dialog, matchRunningExt stays true so the LaunchedEffect
    // continues to completion and shows the results dialog.

    if (showConfirmMatchAllDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.match_all_confirmation, items.count { it.id.length != 11 || (it.durationText == "00:00" && it.totalPlayTimeMs == 1L) }),
            onDismiss = {
                showConfirmMatchAllDialog = false
            },onConfirm = {
                retryMatchMode = false
                retryMatchSongs = emptyList()
                getAlbumVersion = true
                showGetAlbumVersionDialogue = true
                showConfirmMatchAllDialog = false
                matchRunning = true
                cancelMatchExt = false
            }
        )
    }

    if (showMatchResultsDialog) {
        MatchResultsDialog(
            matched = matchResultsMatched,
            failed = matchResultsFailed,
            merged = matchResultsMerged,
            failedSongs = matchResultsFailedSongs,
            onRetry = if (matchResultsFailed > 0) {{
                showMatchResultsDialog = false
                retryMatchMode = true
                retryMatchSongs = matchResultsFailedSongs
                getAlbumVersion = true
                showGetAlbumVersionDialogue = true
                matchRunning = true
                cancelMatchExt = false
            }} else null,
            onDismiss = { showMatchResultsDialog = false }
        )
    }

    val unmatchedExt = remember(items) { items.any { song -> song.id == (cleanPrefix(song.title ?: "")+(song.artistsText ?: "")).filter{it.isLetterOrDigit()} } }
    if (unmatchedExt && !matchRunningExt){
        matchRunningExt = true
        showGetAlbumVersionDialogueExt = true
        cancelMatchExt = false
    }
    LaunchedEffect(matchRunningExt) {
        if (!matchRunningExt) return@LaunchedEffect
        val mergedCounter = AtomicInteger(0)
        val matchedItemsRef = items.filter{song -> song.id == (cleanPrefix(song.title ?: "")+(song.artistsText ?: "")).filter{it.isLetterOrDigit()}}
        val job = launch(Dispatchers.IO) {
            try {
                totalSongsToMatch = matchedItemsRef.size
                songsMatched = 0

                val jobs = mutableListOf<Job>()
                matchedItemsRef.forEachIndexed { index, song ->
                    ensureActive()
                    jobs.add(launch(Dispatchers.IO) {
                        var wasCancelled = false
                        try {
                            if (cancelMatchExt) return@launch
                            getAlbumVersionFromVideo(
                                song = song,
                                playlistId = playlistId,
                                position = index,
                                playlist = playlist,
                                mergedCounter = mergedCounter
                            )
                        } catch (e: CancellationException) {
                            wasCancelled = true
                            throw e
                        } catch (e: Exception) {
                            Timber.tag("LocalPlaylistSongs").e(e, "Failed to match song to album version")
                        } finally {
                            if (!wasCancelled) songsMatched++
                        }
                    })
                    kotlinx.coroutines.delay(800) // Space out requests to avoid YouTube rate limiting (403 Error)
                }
                jobs.forEach { it.join() }
            } catch (e: CancellationException) {
                // Expected on cancel - cleanup happens in finally
            } finally {
                withContext(NonCancellable) {
                    kotlinx.coroutines.delay(500)

                    // Count failed: ImportSong entries where originalId is still in DB
                    // AND is NOT a valid YouTube ID (Riplay imports may have YouTube IDs as originalId)
                    var failedCount = 0
                    val allEntries = Database.importSongTable.getAllEntries()
                    val failedEntries = mutableListOf<ImportSong>()
                    for (entry in allEntries) {
                        if (entry.playlistId != playlistId) continue
                        val count = Database.songTable.countById(entry.originalId)
                        val isYouTubeId = entry.originalId.length == 11 && !entry.originalId.startsWith(LOCAL_KEY_PREFIX)
                        Timber.tag("MatchPlaylist").d("CLEANUP BDD: originalId='${entry.originalId}' count=$count isYouTubeId=$isYouTubeId")
                        if (count > 0) {
                            if (isYouTubeId) {
                                val song = Database.songTable.findById(entry.originalId).first()
                                val dur = song?.durationText ?: "?"
                                if (song != null && song.durationText == "00:00") {
                                    Timber.tag("MatchPlaylist").d("CLEANUP BDD: FAIL (Riplay) originalId='${entry.originalId}' - duration='00:00'")
                                    failedCount++
                                    failedEntries.add(entry)
                                } else {
                                    Timber.tag("MatchPlaylist").d("CLEANUP BDD: MATCHED originalId='${entry.originalId}' (duration='$dur')")
                                    Database.importSongTable.deleteByOriginalId(entry.originalId)
                                }
                            } else {
                                Timber.tag("MatchPlaylist").d("CLEANUP BDD: FAIL originalId='${entry.originalId}' (placeholder)")
                                failedCount++
                                failedEntries.add(entry)
                            }
                        } else {
                            Timber.tag("MatchPlaylist").d("CLEANUP BDD: MATCHED originalId='${entry.originalId}' (deleted)")
                            Database.importSongTable.deleteByOriginalId(entry.originalId)
                        }
                    }

                    val matchedCount = maxOf(0, totalSongsToMatch - failedCount)

                    if (matchedItemsRef.isNotEmpty()) {
                        matchResultsMatched = matchedCount
                        matchResultsFailed = failedCount
                        matchResultsMerged = mergedCounter.get()
                        // For failedSongs list, look up current Song objects in items
                        val failedOriginalIds = failedEntries.map { it.originalId }.toSet()
                        val failedSongsList = items.filter { it.id in failedOriginalIds }
                        matchResultsFailedSongs = failedSongsList
                        showMatchResultsDialog = true
                    }
                }
                showGetAlbumVersionDialogueExt = false
                getAlbumVersion = false
                matchRunningExt = false
                cancelMatchExt = false
                matchJobExt = null
            }
        }
        matchJobExt = job
        job.join()
    }

    LaunchedEffect(matchRunning) {
        if (!matchRunning) return@LaunchedEffect
        val mergedCounter = AtomicInteger(0)
        val unmatched = if (retryMatchMode && retryMatchSongs.isNotEmpty()) {
            retryMatchSongs
        } else {
            getSongs().filter { (it.id.length != 11 || (it.durationText == "00:00" && it.totalPlayTimeMs == 1L)) && !it.id.startsWith(LOCAL_KEY_PREFIX) }
        }
        val job = launch(Dispatchers.IO) {
            try {
                totalSongsToMatch = unmatched.size
                songsMatched = 0

                val jobs = mutableListOf<Job>()
                unmatched.forEachIndexed { index, song ->
                    ensureActive()
                    jobs.add(launch(Dispatchers.IO) {
                        var wasCancelled = false
                        try {
                            if (cancelMatchExt) return@launch
                            getAlbumVersionFromVideo(
                                song = song,
                                playlistId = playlistId,
                                position = index,
                                playlist = playlist,
                                mergedCounter = mergedCounter
                            )
                        } catch (e: CancellationException) {
                            wasCancelled = true
                            throw e
                        } catch (e: Exception) {
                            Timber.tag("LocalPlaylistSongs").e(e, "Failed to match song to album version")
                        } finally {
                            if (!wasCancelled) songsMatched++
                        }
                    })
                    kotlinx.coroutines.delay(800)
                }
                jobs.forEach { it.join() }
            } catch (e: CancellationException) {
                // Expected on cancel - cleanup happens in finally
            } finally {
                withContext(NonCancellable) {
                    kotlinx.coroutines.delay(500)

                    // Count failed: ImportSong entries where originalId is still in DB
                    // AND is NOT a valid YouTube ID (Riplay imports may have YouTube IDs as originalId)
                    var failedCount = 0
                    val allEntries = Database.importSongTable.getAllEntries()
                    val failedEntries = mutableListOf<ImportSong>()
                    for (entry in allEntries) {
                        if (entry.playlistId != playlistId) continue
                        val count = Database.songTable.countById(entry.originalId)
                        val isYouTubeId = entry.originalId.length == 11 && !entry.originalId.startsWith(LOCAL_KEY_PREFIX)
                        Timber.tag("MatchPlaylist").d("CLEANUP BDD: originalId='${entry.originalId}' count=$count isYouTubeId=$isYouTubeId")
                        if (count > 0) {
                            if (isYouTubeId) {
                                val song = Database.songTable.findById(entry.originalId).first()
                                val dur = song?.durationText ?: "?"
                                if (song != null && song.durationText == "00:00") {
                                    Timber.tag("MatchPlaylist").d("CLEANUP BDD: FAIL (Riplay) originalId='${entry.originalId}' - duration='00:00'")
                                    failedCount++
                                    failedEntries.add(entry)
                                } else {
                                    Timber.tag("MatchPlaylist").d("CLEANUP BDD: MATCHED originalId='${entry.originalId}' (duration='$dur')")
                                    Database.importSongTable.deleteByOriginalId(entry.originalId)
                                }
                            } else {
                                Timber.tag("MatchPlaylist").d("CLEANUP BDD: FAIL originalId='${entry.originalId}' (placeholder)")
                                failedCount++
                                failedEntries.add(entry)
                            }
                        } else {
                            Timber.tag("MatchPlaylist").d("CLEANUP BDD: MATCHED originalId='${entry.originalId}' (deleted)")
                            Database.importSongTable.deleteByOriginalId(entry.originalId)
                        }
                    }

                    val matchedCount = maxOf(0, totalSongsToMatch - failedCount)

                    if (unmatched.isNotEmpty()) {
                        matchResultsMatched = matchedCount
                        matchResultsFailed = failedCount
                        matchResultsMerged = mergedCounter.get()
                        val failedOriginalIds = failedEntries.map { it.originalId }.toSet()
                        val failedSongsList = items.filter { it.id in failedOriginalIds }
                        matchResultsFailedSongs = failedSongsList
                        showMatchResultsDialog = true
                    }
                }
                showGetAlbumVersionDialogue = false
                getAlbumVersion = false
                retryMatchMode = false
                retryMatchSongs = emptyList()
                matchRunning = false
                cancelMatchExt = false
                matchJob = null
            }
        }
        matchJob = job
        job.join()
    }
    val shuffle = SongShuffler ( ::getSongs )
    val renameDialog = RenamePlaylistDialog { playlist }
    val exportDialog = ExportSongsToCSVDialog(
        playlistBrowseId = playlist?.browseId.orEmpty(),
        playlistName = playlist?.name ?: "",
        songs = ::getSongs
    )
    val importNzikDialog = ImportSongsFromCSV(targetPlaylistId = playlistId, onImportComplete = {
        appContext().preferences.edit().putString("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Custom.name).apply()
    })
    val importSpotifyDialog = ImportSongsFromServices.init(
        afterTransaction = { finalPosition, song, _, _ ->
            // Already handled by ImportSongsFromServices internally
        },
        playlistIdForMatch = playlistId,
        playlistName = playlist?.name ?: "",
        source = "SPOTIFY_IMPORT",
        onImportComplete = {
            appContext().preferences.edit().putString("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Custom.name).apply()
        }
    )
    val importRiplayDialog = ImportSongsFromServices.init(
        afterTransaction = { finalPosition, song, _, _ ->
        },
        playlistIdForMatch = playlistId,
        playlistName = playlist?.name ?: "",
        source = "RIPLAY_IMPORT",
        onImportComplete = {
            appContext().preferences.edit().putString("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Custom.name).apply()
        }
    )

    var showYouTubeLinkDialog by remember { mutableStateOf(false) }
    if (showYouTubeLinkDialog) {
        DefaultDialog(
            onDismiss = { showYouTubeLinkDialog = false },
            modifier = Modifier.fillMaxWidth(if (isLandscape) 0.3f else 0.8f)
        ) {
            InputTextField(
                onDismiss = { showYouTubeLinkDialog = false },
                title = stringResource(R.string.import_via_youtube_link),
                value = "",
                placeholder = "https://youtube.com/playlist?list=...",
                setValue = { url ->
                    showYouTubeLinkDialog = false
                    coroutineScope.launch(Dispatchers.IO) {
                        val urlPlaylistId = listOf(
                            "https://www.youtube.com/playlist?",
                            "https://youtube.com/playlist?",
                            "https://music.youtube.com/playlist?",
                            "https://m.youtube.com/playlist?"
                        ).find { url.startsWith(it) }?.let { url.toUri().getQueryParameter("list") }

                        if (urlPlaylistId != null) {
                            val browseId = if (urlPlaylistId.startsWith("VL")) urlPlaylistId else "VL$urlPlaylistId"
                            Innertube.playlistPage(BrowseBody(browseId = browseId))?.getOrNull()?.let { playlistPage ->
                                val songs = playlistPage.songsPage?.items?.mapNotNull { it.asSong.copy(totalPlayTimeMs = 1L) }
                                if (songs != null) {
                                    Database.asyncTransaction {
                                        songTable.upsert(songs)
                                        songs.forEach { song ->
                                            songPlaylistMapTable.map(song.id, playlistId)
                                        }
                                    }
                                    appContext().preferences.edit().putString("PlaylistSongsSortBy_$playlistId", PlaylistSongSortBy.Custom.name).apply()
                                    Toaster.done()
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    val importMenu = remember { ImportPlaylistsMenu(
        onImportNzik = { importNzikDialog.onShortClick() },
        onImportSpotify = { importSpotifyDialog.onShortClick() },
        onImportRiplay = { importRiplayDialog.onShortClick() },
        onImportYoutubeLink = { showYouTubeLinkDialog = true }
    ) }
    val matchAlbumButton = remember {
        object : MenuIcon, Descriptive {
            override val iconId: Int = R.drawable.alert
            override val messageId: Int = R.string.match_album_audio_version
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { showConfirmMatchAllDialog = true }
            override fun onLongClick() {}
        }
    }
    val deleteDialog = DeletePlaylist {
        Database.asyncTransaction {
            playlist?.let( playlistTable::delete )
        }


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


            getMediaItems()
        },
        { throwable, preview ->
            Timber.tag("LocalPlaylistSongs").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        {
            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    )

    fun sync() {
        playlist?.let {
            CoroutineScope(Dispatchers.IO).launch {
                val remotePlaylist = Innertube.playlistPage(
                    BrowseBody(browseId = it.browseId?.removePrefix(MODIFIED_PREFIX) ?: "")
                )?.completed()?.getOrNull()
                remotePlaylist?.let { rp ->
                    val mediaItems = rp.songsPage
                        ?.items
                        ?.map(Innertube.SongItem::asMediaItem)

                    if (mediaItems != null && mediaItems.isNotEmpty()) {
                        Database.asyncTransaction {
                            songPlaylistMapTable.clear( playlistId )
                            mapIgnore( it, *mediaItems.toTypedArray() )
                        }
                    }
                }
            }
        }
    }
    val syncComponent = Synchronize { sync() }
    val listenOnYT = ListenOnYouTube {
        val browseId = playlist?.browseId?.removePrefix(MODIFIED_PREFIX)?.removePrefix( "VL" )

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
                            artistsText = songItem.authors.parseArtists().joinToString(", "),
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
    importMenu.Render()

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
                            shape = thumbnailShape()
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

                val toolbarOrderPref by rememberPreference(localPlaylistToolbarOrderKey, "")

                val hasUnmatchedSongs = remember(items) { items.any { (it.id.length != 11 || (it.durationText == "00:00" && it.totalPlayTimeMs == 1L)) && !it.id.startsWith(LOCAL_KEY_PREFIX) } }

                val defaultOrder = LocalPlaylistToolbarSettingsDialog.allButtonIds
                val order = try {
                    if (toolbarOrderPref.isBlank()) defaultOrder else {
                        val arr = JSONArray(toolbarOrderPref)
                        (0 until arr.length()).map { arr.getString(it) }.distinct()
                    }
                } catch (_: Exception) { defaultOrder }

                val toolbarButtons = buildList {
                    order.forEach { id ->
                        when (id) {
                            "pin" -> if (playlistNotMonthlyType) add( pin )
                            "position_lock" -> if ( sort.sortBy == PlaylistSongSortBy.Custom ) add( positionLock )
                            "match" -> if ( hasUnmatchedSongs ) add( matchAlbumButton )
                            "renumber" -> if ( sort.sortBy == PlaylistSongSortBy.Custom ) add( renumberDialog )
                            "download_all" -> add( downloadAllDialog )
                            "delete_downloads" -> add( deleteDownloadsDialog )
                            "item_selector" -> add( itemSelector )
                            "play_next" -> add( playNext )
                            "enqueue" -> add( enqueue )
                            "add_to_favorite" -> add( addToFavorite )
                            "add_to_playlist" -> add( addToPlaylist )
                            "sync" -> if ( !playlist?.browseId.isNullOrBlank() ) add( syncComponent )
                            "listen_on_yt" -> if ( !playlist?.browseId.isNullOrBlank() ) add( listenOnYT )
                            "import_menu" -> add( importMenu )
                            "rename" -> add( renameDialog )
                            "delete" -> add( deleteDialog )
                            "export" -> add( exportDialog )
                            "thumbnail_picker" -> add( thumbnailPicker )
                            "reset_thumbnail" -> add( resetThumbnail )
                            "reset_cache" -> add( resetCache )
                        }
                    }
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
                            if ( !positionLock.isLocked() && sort.sortBy == PlaylistSongSortBy.Custom && sort.sortOrder == SortOrder.Ascending ) {
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
                                if ((song.id.length != 11 || (song.durationText == "00:00" && song.totalPlayTimeMs == 1L)) && !song.id.startsWith(LOCAL_KEY_PREFIX)) {
                                    Icon(
                                        painter = painterResource(R.drawable.alert),
                                        contentDescription = stringResource(R.string.unmatched_song),
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.padding(start = 8.dp).size(18.dp)
                                    )
                                }
                                if( !positionLock.isLocked() && sort.sortBy == PlaylistSongSortBy.Custom && sort.sortOrder == SortOrder.Ascending )
                                    // Create a fake box to store drag anchor and checkbox
                                    Box( Modifier.width( 24.dp ) )
                            },
                            thumbnailOverlay = {
                                if (sort.sortBy == PlaylistSongSortBy.PlayTime || sort.sortBy == PlaylistSongSortBy.RelativePlayTime || sort.sortBy == PlaylistSongSortBy.PlayCount) {
                                    val text = if (sort.sortBy == PlaylistSongSortBy.PlayCount) {
                                        song.playCount.toString()
                                    } else {
                                        song.formattedTotalPlayTime
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(thumbnailShape())
                                            .background(colorPalette().overlay)
                                    ) {
                                        BasicText(
                                            text = text,
                                            style = typography().xxs.semiBold.center.color(
                                                colorPalette().onOverlay
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .align(Alignment.BottomCenter)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                if (itemSelector.isActive) {
                                    search.hideIfEmpty()
                                    return@SongItem
                                }
                                if (song.isUnmatched) {
                                    Toaster.w(R.string.playback_blocked_match_first)
                                } else {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(
                                        itemsOnDisplay.map( Song::asMediaItem ),
                                        index
                                    )

                                    search.hideIfEmpty()
                                }
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
        if ( showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                visible = !reorderingState.isAnyItemDragging,
                onClick = {
                    getSongs().let { songs ->
                        val playableSongs = songs.filter { !it.isUnmatched }
                        if (playableSongs.isNotEmpty()) {
                            binder?.let { Shuffler.play(it, playableSongs) }
                        } else {
                            Toaster.w(R.string.playback_blocked_match_first)
                        }
                    }
                }
            )
    }
}










