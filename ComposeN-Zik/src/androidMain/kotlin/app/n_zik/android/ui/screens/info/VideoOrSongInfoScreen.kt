package app.n_zik.android.ui.screens.info

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import app.n_zik.android.LOCAL_KEY_PREFIX
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.appContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.topUiRoundnessShape
import app.n_zik.android.components.menu.MenuHeaderTitle
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.menuHeaderBackLeading
import app.n_zik.android.components.menu.info.InformationMenuBand
import app.n_zik.android.components.menu.info.InformationMenuSurface
import app.n_zik.android.components.menu.info.InformationStatsCard
import app.n_zik.android.components.menu.info.informationMenuBodyColor
import app.n_zik.android.components.menu.info.informationMenuHeaderColor
import app.n_zik.android.components.menu.navigateToMenuEntity
import app.n_zik.android.components.song.TrackMetadataSearch
import app.n_zik.android.components.song.isArtistIdForSong
import app.n_zik.android.components.song.openTrackMetadataSearch
import app.n_zik.android.components.OfflineContent
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.VideoOrSongInfo
import app.n_zik.innertube.requests.songInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.models.Info
import app.n_zik.android.utils.ARTIST_LINKED_NAMES_EXTRA
import app.n_zik.android.utils.artistLinksWithStoredFallback
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.splitArtistNames
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.trackBrowseIdOrigin
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.compose.ui.text.font.FontStyle

internal data class VideoOrSongInfoResult(
    val info: VideoOrSongInfo?,
    val finalArtists: List<Pair<String, String>>,
)

internal suspend fun loadVideoOrSongInfo(
    videoId: String,
    songArtist: String,
    source: MediaItem? = null,
): VideoOrSongInfoResult {
    val local = LOCAL_KEY_PREFIX in videoId
    val info = if (local) null else runCatching {
        Innertube.songInfo(videoId)?.getOrThrow()
    }.onFailure { error ->
        if (error is CancellationException) throw error
        Timber.tag("VideoOrSongInfoScreen").w(error, "Could not load information for %s", videoId)
    }.getOrNull()
    val stored = runCatching {
        Database.artistTable.findBySongIdDirect(videoId).map { Info(it.id, it.name) }
    }.onFailure { error ->
        if (error is CancellationException) throw error
        Timber.tag("VideoOrSongInfoScreen").w(error, "Could not load artist credits for %s", videoId)
    }.getOrDefault(emptyList())

    val editedArtist = songArtist.startsWith(MODIFIED_PREFIX, ignoreCase = true)
    val credits = if (editedArtist || source == null) stored else {
        val preferences = appContext().preferences
        source.artistLinksWithStoredFallback(stored) { preferences.trackBrowseIdOrigin(videoId, it) }
    }
    val linked = credits.mapNotNull { credit ->
        val name = credit.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        if (!isArtistIdForSong(credit.id, local)) return@mapNotNull null
        credit.id to name
    }.distinctBy { it.first }
    val sourceNames = source?.mediaMetadata?.extras?.getStringArrayList("artistNames").orEmpty()
    val sourceLinkedNames = source?.mediaMetadata?.extras?.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA).orEmpty()
    val names = when {
        !editedArtist && sourceNames.isNotEmpty() -> sourceNames
        songArtist.isNotBlank() -> cleanPrefix(songArtist).splitArtistNames()
        linked.isEmpty() -> listOfNotNull(info?.author?.takeIf(String::isNotBlank))
        else -> emptyList()
    }
    val unlinked = names.distinctBy { it.lowercase() }
        .filterNot { name ->
            (!editedArtist && linked.isNotEmpty() && sourceLinkedNames.any { it.equals(name, ignoreCase = true) }) ||
                linked.any { (_, linkedName) -> cleanPrefix(linkedName).equals(name, ignoreCase = true) }
        }
        .map { "" to it }
    return VideoOrSongInfoResult(info, linked + unlinked)
}

@Composable
fun VideoOrSongInfoScreen(
    videoId: String,
    songTitle: String = "",
    songArtist: String = "",
    songThumbnailUrl: String = "",
    albumId: String = "",
    albumTitle: String = "",
    navController: NavController? = null,
    source: MediaItem? = null,
    onNavigateUp: () -> Unit,
    onClose: () -> Unit,
    onEntityNavigation: () -> Unit = {},
    onPlay: (() -> Unit)? = null
) {
    if (videoId.isBlank()) return

    val menuState = LocalMenuState.current
    val guestLocked = rememberListenTogetherGuestLock()
    val playAction = onPlay.takeUnless { guestLocked }
    var info by remember(videoId, songArtist, source) { mutableStateOf<VideoOrSongInfo?>(null) }
    var isLoading by remember(videoId, songArtist, source) { mutableStateOf(true) }
    var finalArtists by remember(videoId, songArtist, source) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(videoId, songArtist, source) {
        isLoading = true
        val result = withContext(NzikDispatchers.DATA) {
            loadVideoOrSongInfo(videoId, songArtist, source)
        }
        info = result.info
        finalArtists = result.finalArtists
        isLoading = false
    }

    val shimmer = isLoading || forceLoadingShimmerPreview()
    val artists = if (isLoading) {
        cleanPrefix(songArtist).splitArtistNames().distinctBy { it.lowercase() }.map { "" to it }
    } else finalArtists
    val displayTitle = if (songTitle.startsWith(MODIFIED_PREFIX, ignoreCase = true)) cleanPrefix(songTitle)
        else info?.title?.takeIf(String::isNotBlank) ?: songTitle
    val displayThumbnail = songThumbnailUrl.takeIf { it.isNotBlank() }

    InformationMenuSurface(
        modifier = Modifier
            .fillMaxSize()
            .clip(topUiRoundnessShape())
    ) {
        // ── Top Section (Separated Header) ───────────────────────────────────
        InformationMenuBand(
            film = informationMenuHeaderColor(),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MenuSheetDragHandle(color = colorPalette().text)
            MenuHeaderTitle(
                title = null,
                leading = menuHeaderBackLeading(onNavigateUp),
                trailing = null,
            )

            // ── Main Info: Cover + Title + Artist + Album ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pochette
                if (displayThumbnail != null) {
                    ImageCacheFactory.AsyncImage(
                        shape = uiRoundnessShape(),
                        thumbnailUrl = displayThumbnail,
                        contentDescription = displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp) // Slightly smaller for the menu header
                            .clip(uiRoundnessShape())
                    )
                    Spacer(Modifier.width(16.dp))
                }

                // Text Infos
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    InfoRow(
                        label = stringResource(R.string.metadata_info_title_label),
                        value = displayTitle,
                        showArrow = playAction != null,
                        onClick = playAction,
                    )

                    Spacer(Modifier.height(8.dp))

                    artists.forEachIndexed { index, (artistId, artistName) ->
                        if (index > 0) Spacer(Modifier.height(8.dp))
                        InfoRow(
                            label = stringResource(R.string.library_artists_title),
                            value = artistName,
                            showArrow = navController != null && !isLoading,
                            onClick = {
                                if (navController != null) {
                                    if (artistId.isNotBlank()) {
                                        if (navigateToMenuEntity(navController, menuState, NavRoutes.artist, artistId)) {
                                            onEntityNavigation()
                                        }
                                    } else if (openTrackMetadataSearch(navController, artistName, TrackMetadataSearch.Artist, menuState)) {
                                        onClose()
                                        onEntityNavigation()
                                    }
                                }
                            },
                        )
                    }

                    // Album + Flèche
                    if (albumId.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        InfoRow(
                            label = stringResource(R.string.library_sort_album),
                            value = albumTitle.takeIf { it.isNotBlank() } ?: stringResource(R.string.library_sort_album),
                            showArrow = navController != null,
                            onClick = {
                                if (navController != null) {
                                    if (navigateToMenuEntity(navController, menuState, NavRoutes.album, albumId)) {
                                        onEntityNavigation()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.height(1.dp))
        }

        InformationMenuBand(film = informationMenuBodyColor(), modifier = Modifier.weight(1f)) {
            // Only remote tracks require an online metadata lookup.
            if (!isLoading && info == null && LOCAL_KEY_PREFIX !in videoId) {
                OfflineContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }

            // ── Body ───────────────────────────────────────────────────────────
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 32.dp
                )
            ) {
                // ── Stats card ────────────────────────────────────────────────────
                item {
                    InformationStatsCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(label = stringResource(R.string.metadata_video_subscribers_label), value = info?.subscribers, shimmer = shimmer)
                            StatItem(label = stringResource(R.string.metadata_video_views_label), value = info?.viewCount, shimmer = shimmer)
                            StatItem(label = stringResource(R.string.metadata_video_likes_label), value = info?.like, shimmer = shimmer)
                            StatItem(label = stringResource(R.string.metadata_video_dislikes_label), value = info?.dislike, shimmer = shimmer)
                        }
                    }
                }

                // ── Description ───────────────────────────────────────────────────
                val desc = info?.description
                item {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = stringResource(R.string.metadata_description_title),
                            style = typography().m,
                            color = colorPalette().accent,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (isLoading) {
                            for (width in listOf(1f, 0.93f, 0.76f)) {
                                ItemTextPlaceholder(null, typography().xs, Modifier.padding(bottom = 3.dp), width)
                            }
                        } else if (shimmer && !desc.isNullOrBlank()) {
                            MeasuredMetadataPlaceholder(desc, typography().xs)
                        } else Text(
                            text = if (!desc.isNullOrBlank()) desc else stringResource(R.string.common_description_unavailable),
                            style = typography().xs,
                            color = colorPalette().text,
                            fontStyle = if (desc.isNullOrBlank()) FontStyle.Italic else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    showArrow: Boolean,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(uiRoundnessShape())
            .clickable(enabled = showArrow && onClick != null) { onClick?.invoke() }
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = typography().xs,
            color = colorPalette().accent
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = cleanPrefix(value),
                style = typography().xs,
                color = colorPalette().text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showArrow) {
                Icon(
                    painter = painterResource(R.drawable.chevron_forward),
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String?,
    shimmer: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        if (shimmer && value != null) MeasuredMetadataPlaceholder(value, typography().xxs, maxLines = 1)
        else if (shimmer) ItemTextPlaceholder(null, typography().xxs, Modifier.width(28.dp), width = 1f)
        else Text(
            text = value ?: "-",
            style = typography().xxs,
            color = colorPalette().text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = typography().xxs,
            color = colorPalette().text,
        )
    }
}
