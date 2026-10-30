package app.it.fast4x.rimusic.ui.screens.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.info.InformationMenuSurface
import app.n_zik.android.components.menu.info.informationMenuBodyColor
import app.n_zik.android.components.menu.info.informationMenuHeaderColor
import app.n_zik.android.components.OfflineContent
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.VideoOrSongInfo
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.requests.songInfo
import it.fast4x.innertube.utils.from
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.utils.splitArtistNames
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.compose.ui.text.font.FontStyle

/** Issue #606 M12+M13 — result bundle for [loadVideoOrSongInfo], mirroring the two Compose state variables (`info`, `finalArtists`) that `LaunchedEffect(videoId)` used to assign as it went, interleaved with every network/DB call below. */
internal data class VideoOrSongInfoResult(
    val info: VideoOrSongInfo?,
    val finalArtists: List<Pair<String, String>>,
)

/**
 * Issue #606 M12 (`Innertube.songInfo`, `Innertube.searchPage`) + M13
 * (`Database.artistTable.findBySongId`/`findByName` inside a loop) -- `VideoOrSongInfoScreen`'s
 * `LaunchedEffect(videoId)` used to run this entire sequential network+DB block inline, on whatever
 * dispatcher that effect resumes on (Main). Extracted here verbatim (only the two former Compose
 * `var`s become local `var`s so the function has a single entry/exit point) so the composable can
 * dispatch it in one `withContext(NzikDispatchers.DATA)` call and so it is unit-testable without
 * instantiating the composable. `internal` (not `private`) purely so
 * `VideoOrSongInfoScreenLoadOffMainTest` can call it directly -- it adds no new public legacy API.
 */
internal suspend fun loadVideoOrSongInfo(videoId: String, songArtist: String): VideoOrSongInfoResult {
    var info: VideoOrSongInfo? = null
    var finalArtists: List<Pair<String, String>> = emptyList()

    try {
        val result = Innertube.songInfo(videoId)
        if (result != null && result.isSuccess) {
            info = result.getOrNull()
        }
    } catch (e: Exception) {
        Timber.tag("VideoOrSongInfoScreen").e(e, "exception")
    }

    // Fetch artists from database
    try {
        val dbArtists = Database.artistTable.findBySongId(videoId).first()
        if (dbArtists.isNotEmpty()) {
            finalArtists = dbArtists.map { it.id to (it.name ?: "") }
        } else if (songArtist.isNotBlank()) {
            // Parse songArtist - handle "," and "&" separators, then deduplicate
            val parsed = songArtist
                .splitArtistNames()
                .distinctBy { it.lowercase() }
            val artistsWithIds = mutableListOf<Pair<String, String>>()
            for (name in parsed) {
                // Try database first
                var artistId = try {
                    Database.artistTable.findByName(name).first()?.id
                } catch (e: Exception) { null }

                // If not in database, search online
                if (artistId == null) {
                    try {
                        val searchResult = Innertube.searchPage<Innertube.ArtistItem>(
                            query = name, params = Innertube.SearchFilter.Artist.value,
                            fromMusicShelfRendererContent = { content -> Innertube.ArtistItem.from(content) }
                        )?.getOrNull()
                        val foundArtist = searchResult?.items?.firstOrNull()
                        if (foundArtist != null) {
                            artistId = foundArtist.key
                            // Save to database
                            Database.artistTable.insertIgnore(
                                Artist(id = artistId, name = foundArtist.info?.name ?: name)
                            )
                            Database.songArtistMapTable.insertIgnore(
                                SongArtistMap(songId = videoId, artistId = artistId)
                            )
                        }
                    } catch (e: Exception) { /* Silently fail */ }
                }

                artistsWithIds.add((artistId ?: "") to name)
            }
            finalArtists = artistsWithIds
        } else {
            val apiAuthor = info?.author?.takeIf { it.isNotBlank() }
            val apiAuthorId = info?.authorId
            if (apiAuthor != null && apiAuthorId != null) {
                finalArtists = listOf(apiAuthorId to apiAuthor)
            }
        }
    } catch (e: Exception) {
        // Fallback
        val apiAuthor = info?.author?.takeIf { it.isNotBlank() }
        val apiAuthorId = info?.authorId
        if (apiAuthor != null && apiAuthorId != null) {
            finalArtists = listOf(apiAuthorId to apiAuthor)
        }
    }

    // Final deduplication by artist name (case-insensitive)
    // Keep the entry with a non-empty ID if possible
    finalArtists = finalArtists
        .groupBy { it.second.lowercase() }
        .map { (_, group) ->
            group.firstOrNull { it.first.isNotBlank() } ?: group.first()
        }

    return VideoOrSongInfoResult(info, finalArtists)
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
    onNavigateUp: () -> Unit,
    onClose: () -> Unit,
    onPlay: (() -> Unit)? = null
) {
    if (videoId.isBlank()) return

    var info by remember { mutableStateOf<VideoOrSongInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var finalArtists by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(videoId) {
        isLoading = true
        val result = withContext(NzikDispatchers.DATA) {
            loadVideoOrSongInfo(videoId, songArtist)
        }
        info = result.info
        finalArtists = result.finalArtists
        isLoading = false
    }

    val displayTitle = info?.title?.takeIf { it.isNotBlank() } ?: songTitle
    val displayThumbnail = songThumbnailUrl.takeIf { it.isNotBlank() }

    InformationMenuSurface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        if (isLoading) {
            Column(Modifier.fillMaxSize().background(informationMenuBodyColor())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Loader()
                }
            }
        } else {
            // ── Top Section (Separated Header) ───────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(informationMenuHeaderColor())
            ) {
                // Arrow down to close
                Box(
                    modifier = Modifier
                        .padding(top = 18.dp, bottom = 6.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                        .clickable { onClose() }
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
                        // Titre + Flèche
                        InfoRow(
                            label = stringResource(R.string.title),
                            value = displayTitle,
                            showArrow = onPlay != null,
                            onClick = {
                                if (onPlay != null) {
                                    onPlay()
                                }
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Artistes - un par ligne si plusieurs, sinon une seule ligne
                        if (finalArtists.size > 1) {
                            // Multiple artists - show each separately
                            finalArtists.forEach { (artistId, artistName) ->
                                InfoRow(
                                    label = stringResource(R.string.artists),
                                    value = artistName,
                                    showArrow = artistId.isNotBlank(),
                                    onClick = {
                                        if (artistId.isNotBlank()) {
                                            navController?.navigate("${NavRoutes.artist.name}/$artistId")
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        } else if (finalArtists.size == 1) {
                            // Single artist
                            InfoRow(
                                label = stringResource(R.string.artists),
                                value = finalArtists[0].second,
                                showArrow = finalArtists[0].first.isNotBlank(),
                                onClick = {
                                    if (finalArtists[0].first.isNotBlank()) {
                                        navController?.navigate("${NavRoutes.artist.name}/${finalArtists[0].first}")
                                    }
                                }
                            )
                        }

                        // Album + Flèche
                        if (albumId.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            InfoRow(
                                label = stringResource(R.string.sort_album),
                                value = albumTitle.takeIf { it.isNotBlank() } ?: stringResource(R.string.sort_album),
                                showArrow = true,
                                onClick = {
                                    navController?.navigate("${NavRoutes.album.name}/$albumId")
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(Modifier.height(1.dp))
            }

            Column(Modifier.weight(1f).background(informationMenuBodyColor())) {
                // Offline banner (spec-offline-load-state wave 2): the local params
                // (title/artist/thumbnail) stay visible, the online info (stats,
                //
                // description) is replaced by a centered message instead of the
                // silent "−" degradation.
                if (info == null) {
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = uiRoundnessShape(),
                            colors = CardDefaults.cardColors(
                                containerColor = colorPalette().accent.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(label = stringResource(R.string.subscribers), value = info?.subscribers ?: "-")
                                StatItem(label = stringResource(R.string.views), value = info?.viewCount ?: "-")
                                StatItem(label = stringResource(R.string.likes), value = info?.like ?: "-")
                                StatItem(label = stringResource(R.string.dislikes), value = info?.dislike ?: "-")
                            }
                        }
                    }

                    // ── Description ───────────────────────────────────────────────────
                    val desc = info?.description
                    item {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(
                                text = stringResource(R.string.description),
                                style = typography().m,
                                color = colorPalette().accent,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = if (!desc.isNullOrBlank()) desc else stringResource(R.string.no_description_available),
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
            .clip(RoundedCornerShape(8.dp))
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
                text = value,
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
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = value,
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
