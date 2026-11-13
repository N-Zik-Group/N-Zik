package app.n_zik.android.components.ui.screens.home.onDevice

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appTextResources
import app.n_zik.android.colorPalette
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.ui.InlineCircularLoader
import app.n_zik.android.components.glass.GlassActionCellWidth
import app.n_zik.android.components.glass.GlassStandaloneActionSize
import app.n_zik.android.components.ui.CollectionButtonToolbar
import app.n_zik.android.components.musicbrainz.InfoAndCommunity
import app.n_zik.android.components.ui.screens.album.AlbumBookmark
import app.n_zik.android.components.artist.FollowButton
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.components.ui.screens.DynamicOrientationLayout
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.components.ui.screens.CollectionArtworkHeader
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.search.SearchNoResults
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.DeviceLibrarySnapshot
import app.n_zik.android.core.ondevice.DeviceLibraryResults
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.ondevice.observeDeviceLibrary
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.extensions.musicbrainz.MBMetadataHelper
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.models.Song
import app.n_zik.android.typography
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.ui.styling.overlay
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.excludeMediaItems
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.player.enqueueOffMain
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.rememberPreference
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
@Composable
internal fun OnDeviceAlbumScreen(navController: NavController, id: String, miniPlayer: @Composable () -> Unit) {
    val detail by remember(id) {
        combine(Database.albumTable.findById(id), Database.songAlbumMapTable.allSongsOf(id)) { entity, tracks ->
            entity to tracks
        }
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    val album = detail?.first
    OnDeviceGroupScreen(
        navController,
        album?.cleanTitle()?.takeIf { it.isNotBlank() } ?: stringResource(R.string.library_sort_album),
        R.drawable.album,
        album?.thumbnailUrl,
        detail?.second,
        { it.albumSongIds[id].orEmpty() },
        AlbumBookmark(id, localOnly = true),
        miniPlayer,
        info = {
            DeviceInfoAndCommunity(
                id = id,
                lastSyncAt = album?.mbLastFetch,
                lastSyncFailed = album?.let { it.mbLastFetch != null && it.genres == null } == true,
                sync = { MBMetadataHelper.Default.onAlbumViewed(id, force = true) },
                onInsightsClick = { navController.navigate("albumInsights/$id") },
                rating = album?.rating,
                ratingVotes = album?.ratingVotes,
                year = album?.year,
                countryCode = null,
                keywords = album?.keywords,
                bio = album?.wikipediaInfo,
            )
        },
    )
}

@UnstableApi
@Composable
internal fun OnDeviceArtistScreen(navController: NavController, id: String, miniPlayer: @Composable () -> Unit) {
    val detail by remember(id) {
        combine(Database.artistTable.findById(id), Database.songArtistMapTable.allSongsBy(id)) { entity, tracks ->
            entity to tracks
        }
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    val artist = detail?.first
    OnDeviceGroupScreen(
        navController,
        artist?.cleanName()?.takeIf { it.isNotBlank() } ?: stringResource(R.string.artist_title),
        R.drawable.artist,
        artist?.thumbnailUrl,
        detail?.second,
        { it.artistSongIds[id].orEmpty() },
        artist?.let { row -> FollowButton(localOnly = true) { row } },
        miniPlayer,
        info = {
            DeviceInfoAndCommunity(
                id = id,
                lastSyncAt = artist?.mbLastFetch,
                lastSyncFailed = artist?.let { it.mbLastFetch != null && it.genres == null } == true,
                sync = { MBMetadataHelper.Default.onArtistViewed(id, force = true) },
                onInsightsClick = { navController.navigate("artistInsights/$id") },
                rating = artist?.rating,
                ratingVotes = artist?.ratingVotes,
                year = artist?.beginYear?.toString(),
                countryCode = artist?.countryCode,
                keywords = artist?.keywords,
                bio = artist?.wikipediaBio,
            )
        },
    )
}

@Composable
private fun DeviceInfoAndCommunity(
    id: String,
    lastSyncAt: Long?,
    lastSyncFailed: Boolean,
    sync: suspend () -> Boolean,
    onInsightsClick: () -> Unit,
    rating: Float?,
    ratingVotes: Int?,
    year: String?,
    countryCode: String?,
    keywords: List<String>?,
    bio: String?,
) {
    val scope = rememberCoroutineScope()
    var isSyncing by remember(id) { mutableStateOf(false) }
    var pausedSeconds by remember(id) { mutableStateOf(0L) }

    InfoAndCommunity(
        rating = rating,
        ratingVotes = ratingVotes,
        year = year,
        countryCode = countryCode,
        keywords = keywords,
        links = null,
        bio = bio,
        lastSyncAt = lastSyncAt,
        lastSyncFailed = lastSyncFailed,
        pausedForSeconds = pausedSeconds,
        isSyncing = isSyncing,
        onResyncClick = {
            if (!isSyncing) {
                isSyncing = true
                scope.launch {
                    try {
                        val success = withContext(NzikDispatchers.DATA) { sync() }
                        pausedSeconds = MBMetadataHelper.Default.circuitOpenRemainingSeconds()
                        when {
                            success -> Toaster.s(R.string.metadata_musicbrainz_resync_success)
                            pausedSeconds > 0 -> Toaster.e(
                                appTextResources().getString(
                                    R.string.metadata_musicbrainz_resync_paused,
                                    (pausedSeconds / 60).coerceAtLeast(1),
                                )
                            )
                            else -> Toaster.e(R.string.metadata_musicbrainz_resync_error)
                        }
                    } finally {
                        isSyncing = false
                    }
                }
            }
        },
        onInsightsClick = onInsightsClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
private fun OnDeviceGroupScreen(
    navController: NavController,
    title: String,
    @DrawableRes placeholder: Int,
    thumbnailUrl: String?,
    tracks: List<Song>?,
    groupSongIds: (DeviceLibrarySnapshot) -> Set<String>,
    bookmark: Button?,
    miniPlayer: @Composable () -> Unit,
    info: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val palette = colorPalette()
    val type = typography()
    val binder = LocalPlayerServiceBinder.current
    val scope = rememberCoroutineScope()
    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val parentalControl by rememberPreference(parentalControlEnabledKey, false)
    val profile = getActiveProfile(context)
    val deviceLibrary by remember(context, profile) { context.observeDeviceLibrary() }
        .collectAsStateWithLifecycle(DeviceLibraryResults.current(profile))
    val eligibleIds = deviceLibrary?.let(groupSongIds).orEmpty()
    val visibleTracks = remember(tracks, eligibleIds, parentalControl) {
        tracks.orEmpty().filter { it.id in eligibleIds && (!parentalControl || !it.title.hasExplicitPrefix()) }
    }
    val duration = remember(visibleTracks) { visibleTracks.sumOf { durationTextToMillis(it.durationText.orEmpty()) } }
    val count = pluralStringResource(R.plurals.library_song_count, visibleTracks.size, visibleTracks.size)
    val artwork = (sequenceOf(thumbnailUrl) + visibleTracks.asSequence().map(Song::thumbnailUrl))
        .filterNotNull()
        .map(::cleanPrefix)
        .firstOrNull { it.startsWith("content:") || it.startsWith("file:") }

    fun play(tracksToPlay: List<Song>, selectedId: String? = null) {
        scope.launch {
            val service = binder ?: return@launch
            val mediaItems = withContext(NzikDispatchers.DATA) {
                service.player.excludeMediaItems(tracksToPlay.map(Song::asMediaItem), context)
            }
            val index = if (selectedId == null) 0 else mediaItems.indexOfFirst { it.mediaId == selectedId }
            if (index in mediaItems.indices) {
                service.stopRadio()
                service.player.forcePlayAtIndex(mediaItems, index)
            }
        }
    }

    val itemSelector = ItemSelector<Song>()
    fun actionTracks(): List<Song> {
        if (itemSelector.isEmpty()) return visibleTracks
        val selectedIds = itemSelector.mapTo(HashSet()) { it.id }
        return visibleTracks.filter { it.id in selectedIds }
    }
    val shuffle = SongShuffler(::actionTracks)
    val playNext = PlayNext {
        val songs = actionTracks()
        itemSelector.isActive = false
        scope.launch {
            val items = withContext(NzikDispatchers.DATA) { songs.map(Song::asMediaItem) }
            binder?.player?.addNextOffMain(items, context)
        }
    }
    val enqueue = Enqueue {
        val songs = actionTracks()
        itemSelector.isActive = false
        scope.launch {
            val items = withContext(NzikDispatchers.DATA) { songs.map(Song::asMediaItem) }
            binder?.player?.enqueueOffMain(items, context)
        }
    }

    val artworkPainter = ImageCacheFactory.Painter(artwork)
    val isArtist = placeholder == R.drawable.artist
    Box(Modifier.fillMaxSize().background(palette.background0)) {
        if (tracks == null || deviceLibrary == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InlineCircularLoader() }
        } else DynamicOrientationLayout(
            artworkPainter,
            shape = if (isArtist) artistThumbnailShape() else null,
            thumbnailContentPadding = rememberPageChromeContentPadding(),
        ) {
        LazyColumn(
            contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                CollectionArtworkHeader(title, artworkPainter, disableScrollingText, frameAction = false) {
                    OnDeviceFolderBadge()
                }
            }
            if (!isArtist) item(key = "album_details") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText("$count - ${formatAsTime(duration)}", style = type.xs.medium, maxLines = 1)
                }
            }
            if (visibleTracks.isNotEmpty()) item(key = "action_buttons") {
                CollectionButtonToolbar(
                    bookmark = { bookmark?.ToolBarButton() },
                    buttons = if (isArtist) listOf(shuffle, playNext, enqueue, itemSelector)
                        else listOf(shuffle, itemSelector, playNext, enqueue),
                    bookmarkWidth = if (isArtist) 100.dp else GlassStandaloneActionSize,
                    originalNativeBookmarkWidth = if (isArtist) 100.dp else 48.dp,
                    originalBookmarkGap = if (isArtist) 10.dp else 15.dp,
                    actionCellWidth = if (isArtist) GlassActionCellWidth else 44.dp,
                    modifier = if (isArtist) Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                        else Modifier.padding(top = 8.dp),
                )
            }
            item(key = "info_and_community") { info() }
            if (deviceLibrary != null && visibleTracks.isEmpty()) {
                item(key = "empty") { SearchNoResults(text = stringResource(R.string.common_empty_items)) }
            }
            if (visibleTracks.isNotEmpty()) item(key = "songsTitle") {
                BasicText(
                    text = stringResource(R.string.library_songs_title),
                    style = type.m.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(visibleTracks, key = { it.id }) { song ->
                SongItem(song, navController = navController, itemSelector = itemSelector,
                    onClick = { if (!itemSelector.isActive) play(visibleTracks, song.id) })
            }
        }
        }
        Box(Modifier.align(if (playerPosition == PlayerPosition.Top) Alignment.TopCenter else Alignment.BottomCenter).padding(vertical = 5.dp)) {
            miniPlayer()
        }
    }
}

@Composable
internal fun OnDeviceFolderBadge(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val palette = colorPalette()
    Icon(
        painterResource(R.drawable.folder),
        stringResource(R.string.library_on_device_title),
        modifier.padding(4.dp).size(size),
        tint = palette.text,
    )
}

@Composable
internal fun OnDeviceCoverBadge(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val palette = colorPalette()
    Box(
        modifier.size(size).padding(5.dp).background(palette.overlay, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.folder),
            stringResource(R.string.library_on_device_title),
            Modifier.size(16.dp),
            tint = palette.onOverlay,
        )
    }
}
