package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.isVideoEnabled
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.enums.Countries
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.*
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.items.ArtistItem
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.items.VideoItem
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.*
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.HomePage
import app.it.fast4x.rimusic.models.PlaylistPreview
import timber.log.Timber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import app.it.fast4x.rimusic.ui.items.AlbumItemPlaceholder
import app.it.fast4x.rimusic.ui.components.ShimmerHost

@Composable
fun QuickPicksHeader(
    playEventType: PlayEventsType,
    onPlayEventTypeChange: (PlayEventsType) -> Unit,
    onDiceClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    isLoading: Boolean = false
) {
    val menuState = LocalMenuState.current
    
    @Composable
    fun MenuIcon(@DrawableRes icon: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                tint = colorPalette().accent,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.tips),
                style = TextStyle(
                    fontSize = typography().l.semiBold.fontSize,
                    fontWeight = typography().l.semiBold.fontWeight,
                    color = colorPalette().text,
                    textAlign = TextAlign.Start
                ),
                modifier = Modifier.weight(1f)
            )
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                    strokeWidth = 2.dp,
                    color = colorPalette().textSecondary
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.dice),
                    contentDescription = null,
                    tint = colorPalette().text,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(uiRoundnessShape())
                        .clickable(onClick = onDiceClick)
                )
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = colorPalette().text,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(uiRoundnessShape())
                        .clickable(onClick = onPlayAllClick)
                )
            }
            
            Icon(
                painter = painterResource(R.drawable.settings),
                contentDescription = null,
                tint = colorPalette().text,
                modifier = Modifier
                    .clip(uiRoundnessShape())
                    .clickable {
                        menuState.display {
                            ListMenu.Menu(title = stringResource(R.string.sorting_order)) {
                                ListMenu.Entry(
                                    icon = { MenuIcon(R.drawable.chevron_up) },
                                    text = stringResource(R.string.by_most_played_song),
                                    onClick = {
                                        onPlayEventTypeChange(PlayEventsType.MostPlayed)
                                        menuState.hide()
                                    }
                                )
                                ListMenu.Entry(
                                    icon = { MenuIcon(R.drawable.chevron_down) },
                                    text = stringResource(R.string.by_last_played_song),
                                    onClick = {
                                        onPlayEventTypeChange(PlayEventsType.LastPlayed)
                                        menuState.hide()
                                    }
                                )
                                ListMenu.Entry(
                                    icon = { MenuIcon(R.drawable.random) },
                                    text = stringResource(R.string.by_casual_played_song),
                                    onClick = {
                                        onPlayEventTypeChange(PlayEventsType.CasualPlayed)
                                        menuState.hide()
                                    }
                                )
                            }
                        }
                    }
            )
        }

        BasicText(
            text = playEventType.text,
            style = typography().xxs.secondary,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun QuickPicksGrid(
    recommendations: List<Song>,
    trendingList: List<Song>,
    playEventType: PlayEventsType,
    itemInHorizontalGridWidth: Dp,
    navController: NavController,
    endPaddingValues: PaddingValues,
    onSongClick: (Song) -> Unit
) {
    val quickPicksLazyGridState = rememberLazyGridState()
    LazyHorizontalGrid(
        state = quickPicksLazyGridState,
        rows = GridCells.Fixed(if (recommendations.isNotEmpty()) 3 else 1),
        flingBehavior = ScrollableDefaults.flingBehavior(),
        contentPadding = endPaddingValues,
        modifier = Modifier.fillMaxWidth()
            .height(
                if (recommendations.isNotEmpty())
                    Dimensions.itemsVerticalPadding * 3 * 9
                else
                    Dimensions.itemsVerticalPadding * 9
            )
    ) {
        items(recommendations.distinctBy { it.id }, key = { it.id }) { song ->
            SongItem(
                song = song,
                navController = navController,
                onClick = { onSongClick(song) },
                modifier = Modifier.width(itemInHorizontalGridWidth),
                thumbnailOverlay = {
                    if (playEventType != PlayEventsType.CasualPlayed &&
                        trendingList.any { it.id == song.id }) {
                        Image(
                            painter = painterResource(R.drawable.star_brilliant),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette().accent),
                            modifier = Modifier
                                .size(23.dp)
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        )
                    }
                }
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun YtmSectionByTitle(
    ytmSections: List<HomePage.Section>,
    titlePredicate: (String) -> Boolean,
    titleOverride: String,
    itemInHorizontalGridWidth: Dp,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    songThumbnailSizePx: Int,
    songThumbnailSizeDp: Dp,
    playlistThumbnailSizePx: Int,
    playlistThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    navController: NavController,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    displayedSectionTitles: MutableSet<String>,
    isLoading: Boolean = false
) {
    val matching = ytmSections.filter { titlePredicate(it.title) }
    if (matching.isEmpty()) {
        if (isLoading && titleOverride != null) {
            Title(
                title = titleOverride,
                enableClick = false,
                onClick = null,
                verticalPadding = 16.dp
            )
            LazyRow(contentPadding = endPaddingValues) {
                items(5, key = { it }, contentType = { "placeholder" }) {
                    ShimmerHost {
                        AlbumItemPlaceholder(
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
        return
    }
    
    val items = matching.flatMap { it.items }.filterNotNull().distinctBy { it.key }
    if (items.isEmpty()) {
        return
    }

    matching.forEach { displayedSectionTitles.add(it.title) }
    val section = matching.first().copy(
        items = items
    )
    Timber.tag("HomeQuickPicksSections").d("YTM Section found: $titleOverride (${items.size} items)")
    YtmSectionItems(
        section = section,
        titleOverride = titleOverride,
        itemInHorizontalGridWidth = itemInHorizontalGridWidth,
        albumThumbnailSizePx = albumThumbnailSizePx,
        albumThumbnailSizeDp = albumThumbnailSizeDp,
        songThumbnailSizePx = songThumbnailSizePx,
        songThumbnailSizeDp = songThumbnailSizeDp,
        playlistThumbnailSizePx = playlistThumbnailSizePx,
        playlistThumbnailSizeDp = playlistThumbnailSizeDp,
        disableScrollingText = disableScrollingText,
        endPaddingValues = endPaddingValues,
        navController = navController,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onPlaylistClick = onPlaylistClick,
        isLoading = isLoading
    )
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun NewAlbumsOfYourArtistsSection(
    discoverPageInit: Innertube.DiscoverPage?,
    artists: List<Artist>,
    newReleaseAlbumsFiltered: List<Innertube.AlbumItem>,
    showNewAlbumsArtists: Boolean,
    onAlbumClick: (String) -> Unit,
    navController: NavController,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier
) {
    val menuState = LocalMenuState.current
    if (showNewAlbumsArtists && discoverPageInit != null) {
        if (newReleaseAlbumsFiltered.isNotEmpty() && artists.isNotEmpty()) {
            Timber.tag("HomeQuickPicksSections").d("Local New Albums of your Artists found (${newReleaseAlbumsFiltered.size} items)")
            BasicText(
                text = stringResource(R.string.new_albums_of_your_artists),
                style = typography().l.semiBold,
                modifier = sectionTextModifier
            )

            LazyRow(contentPadding = endPaddingValues) {
                items(
                    items = newReleaseAlbumsFiltered.distinctBy { it.key },
                    key = { it.key },
                    contentType = { "album" }) {
                    AlbumItem(
                        album = it,
                        thumbnailSizePx = albumThumbnailSizePx,
                        thumbnailSizeDp = albumThumbnailSizeDp,
                        alternative = true,
                        modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                            onClick = { onAlbumClick(it.key) },
                            onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = it).MenuComponent() } }
                        ),
                        disableScrollingText = disableScrollingText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun NewAlbumsSection(
    discoverPageInit: Innertube.DiscoverPage?,
    showNewAlbums: Boolean,
    onAlbumClick: (String) -> Unit,
    navController: NavController,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    displayedSectionTitles: MutableSet<String>
) {
    val menuState = LocalMenuState.current
    if (showNewAlbums) {
        if (discoverPageInit != null) {
            val albums = discoverPageInit.newReleaseAlbums
            if (albums.isNotEmpty()) {
                displayedSectionTitles.add("New albums")
                Title(
                    title = stringResource(R.string.new_albums),
                    onClick = { navController.navigate(NavRoutes.newAlbums.name) },
                    verticalPadding = 16.dp,
                )

                LazyRow(contentPadding = endPaddingValues) {
                    items(
                        items = albums.distinctBy { it.key },
                        key = { it.key },
                        contentType = { "album" }) {
                        AlbumItem(
                            album = it,
                            thumbnailSizePx = albumThumbnailSizePx,
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true,
                            modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                onClick = { onAlbumClick(it.key) },
                                onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = it).MenuComponent() } }
                            ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun RelatedAlbumsSection(
    relatedInit: Innertube.RelatedPage?,
    showRelatedAlbums: Boolean,
    onAlbumClick: (String) -> Unit,
    navController: NavController,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier,
    displayedSectionTitles: MutableSet<String>
) {
    val menuState = LocalMenuState.current
    if (showRelatedAlbums) {
        val albums = relatedInit?.albums
        if (albums != null) {
            Timber.tag("HomeQuickPicksSections").d("Related Section found: Albums (${albums.size} items)")
            displayedSectionTitles.add("Related albums")
            BasicText(
                text = stringResource(R.string.related_albums),
                style = typography().l.semiBold,
                modifier = sectionTextModifier
            )

            LazyRow(contentPadding = endPaddingValues) {
                items(
                    items = albums.distinctBy { it.key },
                    key = Innertube.AlbumItem::key,
                    contentType = { "album" }
                ) { album ->
                    AlbumItem(
                        album = album,
                        thumbnailSizePx = albumThumbnailSizePx,
                        thumbnailSizeDp = albumThumbnailSizeDp,
                        alternative = true,
                        modifier = Modifier
                            .clip(uiRoundnessShape()).combinedClickable(
                                onClick = { onAlbumClick(album.key) },
                                onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = album).MenuComponent() } }
                            ),
                        disableScrollingText = disableScrollingText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun SimilarArtistsSection(
    relatedInit: Innertube.RelatedPage?,
    showSimilarArtists: Boolean,
    onArtistClick: (String) -> Unit,
    navController: NavController,
    artistThumbnailSizePx: Int,
    artistThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier,
    displayedSectionTitles: MutableSet<String>
) {
    val menuState = LocalMenuState.current
    if (showSimilarArtists) {
        val artists = relatedInit?.artists
        if (artists != null) {
            Timber.tag("HomeQuickPicksSections").d("Related Section found: Similar Artists (${artists.size} items)")
            displayedSectionTitles.add("Similar artists")
            BasicText(
                text = stringResource(R.string.similar_artists),
                style = typography().l.semiBold,
                modifier = sectionTextModifier
            )

            LazyRow(contentPadding = endPaddingValues) {
                items(
                    items = artists.distinctBy { it.key },
                    key = Innertube.ArtistItem::key,
                    contentType = { "artist" }
                ) { artist ->
                    ArtistItem(
                        artist = artist,
                        thumbnailSizePx = artistThumbnailSizePx,
                        thumbnailSizeDp = artistThumbnailSizeDp,
                        alternative = true,
                        modifier = Modifier
                            .clip(uiRoundnessShape()).combinedClickable(
                                onClick = { onArtistClick(artist.key) },
                                onLongClick = { menuState.display { OnlineArtistItemMenu(navController = navController, artist = artist).MenuComponent() } }
                            ),
                        disableScrollingText = disableScrollingText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun MonthlyPlaylistsSection(
    showMonthlyPlaylistInQuickPicks: Boolean,
    monthlyPlaylists: List<PlaylistPreview>,
    navController: NavController,
    endPaddingValues: PaddingValues,
    playlistThumbnailSizeDp: Dp,
    playlistThumbnailSizePx: Int,
    disableScrollingText: Boolean
) {
    if (showMonthlyPlaylistInQuickPicks) {
        if (monthlyPlaylists.isNotEmpty()) {
            Timber.tag("HomeQuickPicksSections").d("Local Section found: Monthly Playlists (${monthlyPlaylists.size} items)")
            BasicText(
                text = stringResource(R.string.monthly_playlists),
                style = typography().l.semiBold,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 16.dp, bottom = 8.dp)
            )

            LazyRow(contentPadding = endPaddingValues) {
                items(
                    items = monthlyPlaylists.distinctBy { it.playlist.id },
                    key = { it.playlist.id },
                    contentType = { "playlist" }
                ) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        thumbnailSizeDp = playlistThumbnailSizeDp,
                        thumbnailSizePx = playlistThumbnailSizePx,
                        alternative = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(uiRoundnessShape()).clickable(onClick = { navController.navigate(route = "${NavRoutes.localPlaylist.name}/${playlist.playlist.id}") }),
                        disableScrollingText = disableScrollingText,
                        isYoutubePlaylist = playlist.playlist.isYoutubePlaylist,
                        isEditable = playlist.playlist.isEditable
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun MyTopSection(
    showMyTopPlaylist: Boolean,
    myTopSongs: List<Song>,
    navController: NavController,
    endPaddingValues: PaddingValues,
    sectionTextModifier: Modifier,
    itemInHorizontalGridWidth: Dp
) {
    val binder = LocalPlayerServiceBinder.current
    if (showMyTopPlaylist) {
        if (myTopSongs.isNotEmpty()) {
            Timber.tag("HomeQuickPicksSections").d("Local Section found: My Top (${myTopSongs.size} items)")
            BasicText(
                text = stringResource(R.string.my_playlist_top1),
                style = typography().l.semiBold,
                modifier = sectionTextModifier
            )

            LazyRow(contentPadding = endPaddingValues) {
                items(
                    items = myTopSongs.distinctBy { it.id },
                    key = { it.id },
                    contentType = { "song" }
                ) { song ->
                    SongItem(
                        song = song,
                        navController = navController,
                        onClick = { binder?.startRadio(song, true) },
                        modifier = Modifier.width(itemInHorizontalGridWidth),
                    )
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun MoodsSection(
    homePageInit: HomePage?,
    onChipClick: (Innertube.Chip) -> Unit,
    gridsContentPadding: PaddingValues,
    displayedSectionTitles: MutableSet<String>
) {
    if (homePageInit?.chips != null) {
        val chips = homePageInit.chips!!
        if (chips.isNotEmpty()) {
            displayedSectionTitles.add("Moods")
            Title(
                title = stringResource(R.string.moods),
                verticalPadding = 16.dp,
            )

            LazyHorizontalGrid(
                rows = GridCells.Fixed(4),
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = gridsContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.itemsVerticalPadding * 4 * 8)
            ) {
                items(chips, key = { it.hashCode() }, contentType = { "chip" }) { chip ->
                    ChipItemColored(chip = chip, onClick = { onChipClick(chip) })
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun MoodsAndGenresSection(
    showMoodsAndGenres: Boolean,
    discoverPageInit: Innertube.DiscoverPage?,
    onMoodClick: (Innertube.Mood.Item) -> Unit,
    navController: NavController,
    gridsContentPadding: PaddingValues,
    displayedSectionTitles: MutableSet<String>
) {
    if (showMoodsAndGenres) {
        val moods = discoverPageInit?.moods
        if (moods != null && moods.isNotEmpty()) {
            displayedSectionTitles.add("Moods and genres")
            displayedSectionTitles.add("Moods & genres")
            Title(
                title = stringResource(R.string.moods_and_genres),
                onClick = { navController.navigate(NavRoutes.moodsPage.name) },
                verticalPadding = 16.dp,
            )

            val moodAngGenresLazyGridState = rememberLazyGridState()
            LazyHorizontalGrid(
                state = moodAngGenresLazyGridState,
                rows = GridCells.Fixed(4),
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = gridsContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.itemsVerticalPadding * 4 * 8)
            ) {
                items(
                    items = moods.sortedBy { it.title },
                    key = { it.endpoint.params ?: it.title },
                    contentType = { "mood" }
                ) {
                    MoodItemColored(
                        mood = it,
                        onClick = { it.endpoint.browseId?.let { _ -> onMoodClick(it) } }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun ChartsSection(
    showCharts: Boolean,
    chartsPageInit: Innertube.ChartsPage?,
    selectedCountryCode: Countries,
    onCountryChange: (Countries) -> Unit,
    navController: NavController,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    endPaddingValues: PaddingValues,
    playlistThumbnailSizePx: Int,
    playlistThumbnailSizeDp: Dp,
    songThumbnailSizePx: Int,
    songThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    parentalControlEnabled: Boolean,
    displayedSectionTitles: MutableSet<String>,
    itemInHorizontalGridWidth: Dp
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    if (showCharts) {
        if (chartsPageInit != null) {
            val hasPlaylists = chartsPageInit.playlists?.isNotEmpty() == true
            val hasSongs = chartsPageInit.songs?.isNotEmpty() == true
            val hasArtists = chartsPageInit.artists?.isNotEmpty() == true
            
            if (hasPlaylists || hasSongs || hasArtists) {
                displayedSectionTitles.add("Charts")
                Title(
                    title = "${stringResource(R.string.charts)} (${selectedCountryCode.countryName})",
                    onClick = {
                        menuState.display {
                            ListMenu.Menu(title = stringResource(R.string.charts)) {
                                Countries.entries.forEach { country ->
                                    ListMenu.Entry(
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        color = colorPalette().accent.copy(alpha = 0.1f),
                                                        shape = uiRoundnessShape()
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.arrow_right),
                                                    tint = colorPalette().accent,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        text = country.countryName,
                                        onClick = {
                                            onCountryChange(country)
                                            menuState.hide()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    verticalPadding = 16.dp,
                )

                chartsPageInit.playlists?.let { playlists ->
                    if (playlists.isNotEmpty()) {
                        LazyRow(contentPadding = endPaddingValues) {
                            items(
                                items = playlists.distinctBy { it.key },
                                key = Innertube.PlaylistItem::key,
                                contentType = { "playlist" }
                            ) { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    thumbnailSizePx = playlistThumbnailSizePx,
                                    thumbnailSizeDp = playlistThumbnailSizeDp,
                                    alternative = true,
                                    showSongsCount = false,
                                    modifier = Modifier
                                        .clip(uiRoundnessShape()).combinedClickable(
                                            onClick = { onPlaylistClick(playlist.key) },
                                            onLongClick = { menuState.display { OnlinePlaylistItemMenu(navController = navController, playlist = playlist).MenuComponent() } }
                                        ),
                                    disableScrollingText = disableScrollingText
                                )
                            }
                        }
                    }
                } ?: {}

                chartsPageInit.songs?.let { songs ->
                    if (songs.isNotEmpty()) {
                        BasicText(
                            text = stringResource(R.string.chart_top_songs),
                            style = typography().l.semiBold,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(top = 16.dp, bottom = 8.dp)
                        )

                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(2),
                            modifier = Modifier
                                .height(130.dp)
                                .fillMaxWidth(),
                            flingBehavior = ScrollableDefaults.flingBehavior(),
                        ) {
                            itemsIndexed(
                                items = if (parentalControlEnabled)
                                    songs.filter {
                                        !it.asSong.title.startsWith(EXPLICIT_PREFIX)
                                    }.distinctBy { it.key }
                                else songs.distinctBy { it.key },
                                key = { _, song -> song.key }
                            ) { index, song ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    BasicText(
                                        text = "${index + 1}",
                                        style = typography().l.bold.center.color(colorPalette().text),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    SongItem(
                                        song = song.asSong ?: Song.makePlaceholder(""),
                                        navController = navController,
                                        onClick = {
                                            val mediaItem = song.asMediaItem
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(mediaItem)
                                            binder?.player?.addMediaItems(songs.map { it.asMediaItem })
                                        }
                                    )
                                }
                            }
                        }
                    }
                } ?: {}

                chartsPageInit.artists?.let { artists ->
                    if (artists.isNotEmpty()) {
                        BasicText(
                            text = stringResource(R.string.chart_top_artists),
                            style = typography().l.semiBold,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp)
                        )

                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(2),
                            modifier = Modifier
                                .height(130.dp)
                                .fillMaxWidth(),
                            flingBehavior = ScrollableDefaults.flingBehavior(),
                        ) {
                            itemsIndexed(
                                items = artists.distinctBy { it.key },
                                key = { _, artist -> artist.key }
                            ) { index, artist ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    BasicText(
                                        text = "${index + 1}",
                                        style = typography().l.bold.center.color(colorPalette().text),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    ArtistItem(
                                        artist = artist,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        alternative = false,
                                        modifier = Modifier
                                            .width(200.dp)
                                            .clip(uiRoundnessShape()).combinedClickable(
                                                onClick = { onArtistClick(artist.key) },
                                                onLongClick = {
                                                    menuState.display { OnlineArtistItemMenu(navController = navController, artist = artist).MenuComponent() }
                                                }
                                            ),
                                        disableScrollingText = disableScrollingText
                                    )
                                }
                            }
                        }
                    }
                } ?: {}
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun GenericYtmSections(
    homePageInit: HomePage?,
    displayedSectionTitles: MutableSet<String>,
    itemInHorizontalGridWidth: Dp,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    songThumbnailSizePx: Int,
    songThumbnailSizeDp: Dp,
    playlistThumbnailSizePx: Int,
    playlistThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    endPaddingValues: PaddingValues,
    navController: NavController,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    isLoading: Boolean = false
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    homePageInit?.sections?.forEach { section ->
        if (section.items.isEmpty() || section.items.firstOrNull()?.key == null) return@forEach
        
        val title = section.title
        val browseId = section.endpoint?.browseId

        if (displayedSectionTitles.contains(title)) return@forEach
        if (browseId == "FEmusic_new_releases_albums") return@forEach
        if (browseId == "FEmusic_charts") return@forEach
        if (browseId == "FEmusic_moods_and_genres") return@forEach
        
        // Skip all specifically handled sections
        if (title.contains("Quick picks", ignoreCase = true)) return@forEach
        if (title.contains("Fresh finds", ignoreCase = true)) return@forEach
        if (title.contains("Old favorites", ignoreCase = true)) return@forEach
        if (title.contains("Mixed for you", ignoreCase = true)) return@forEach
        if (title.contains("Forgotten favorites", ignoreCase = true)) return@forEach
        if (title.contains("Your daily discover", ignoreCase = true)) return@forEach
        if (title.contains("New release", ignoreCase = true)) return@forEach
        if (title.contains("Fresh new music", ignoreCase = true)) return@forEach
        if (title.contains("Albums for you", ignoreCase = true)) return@forEach
        if (title.contains("Today's biggest hits", ignoreCase = true)) return@forEach
        if (title.contains("All hits", ignoreCase = true)) return@forEach
        if (title.contains("Featured playlists", ignoreCase = true)) return@forEach
        if (title.contains("Trending community playlists", ignoreCase = true)) return@forEach
        if (title.contains("From the community", ignoreCase = true)) return@forEach
        if (title.contains("Trending songs for you", ignoreCase = true)) return@forEach
        if (title.contains("Cover", ignoreCase = true)) return@forEach
        if (title.contains("remix", ignoreCase = true)) return@forEach
        if (title.contains("Music videos for you", ignoreCase = true)) return@forEach
        if (title.contains("Live performances", ignoreCase = true)) return@forEach
        if (title.contains("Charts", ignoreCase = true)) return@forEach
        if (title.contains("New albums", ignoreCase = true)) return@forEach
        if (title.contains("Related albums", ignoreCase = true)) return@forEach
        if (title.contains("Similar artists", ignoreCase = true)) return@forEach
        if (title.contains("Playlist you might like", ignoreCase = true)) return@forEach
        if (title.contains("Top music videos", ignoreCase = true)) return@forEach
        if (title.contains("Trending in Shorts", ignoreCase = true)) return@forEach
        if (title.contains("Trending in Shorts", ignoreCase = true)) return@forEach
        if (title.contains("Moods", ignoreCase = true)) return@forEach
        if (title.contains("Genre", ignoreCase = true)) return@forEach

        displayedSectionTitles.add(title)

        TitleMiniSection(section.label ?: "", modifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp, bottom = 4.dp))

        BasicText(
            text = section.title,
            style = typography().l.semiBold.color(colorPalette().text),
            modifier = Modifier.padding(horizontal = 12.dp).padding(vertical = 4.dp)
        )

        val isSongOnly = section.items.all { item -> item is Innertube.SongItem }

        if (isSongOnly) {
            val songItems = section.items.filterIsInstance<Innertube.SongItem>()
            LazyHorizontalGrid(
                rows = GridCells.Fixed(3),
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = endPaddingValues,
                modifier = Modifier.fillMaxWidth().height(Dimensions.itemsVerticalPadding * 3 * 9)
            ) {
                items(songItems, key = { it.key ?: it.hashCode() }, contentType = { "song" }) { item ->
                    SongItem(
                        song = item.asSong ?: Song.makePlaceholder(""),
                        navController = navController,
                        onClick = {
                            val mediaItem = item.asMediaItem
                            binder?.stopRadio()
                            binder?.player?.forcePlay(mediaItem)
                            binder?.player?.addMediaItems(songItems.map { s -> s.asMediaItem })
                        },
                        modifier = Modifier.width(itemInHorizontalGridWidth)
                    )
                }
                
                if (isLoading) {
                    items(3, key = { "shimmer_$it" }, contentType = { "placeholder" }) {
                        ShimmerHost {
                            AlbumItemPlaceholder(
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyRow(contentPadding = endPaddingValues) {
                items(section.items, key = { it?.hashCode() ?: 0 }, contentType = { "item" }) { item ->
                    when (item) {
                        is Innertube.SongItem -> {
                            SongItem(
                                song = item.asSong ?: Song.makePlaceholder(""),
                                navController = navController,
                                onClick = {
                                    val mediaItem = item.asMediaItem
                                    binder?.stopRadio()
                                    binder?.player?.forcePlay(mediaItem)
                                }
                            )
                        }
                        is Innertube.AlbumItem -> {
                            AlbumItem(
                                album = item,
                                alternative = true,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { navController.navigate("${NavRoutes.album.name}/${item.key}") },
                                    onLongClick = { menuState.display { OnlineAlbumItemMenu(navController = navController, album = item).MenuComponent() } }
                                )
                            )
                        }
                        is Innertube.ArtistItem -> {
                            ArtistItem(
                                artist = item,
                                thumbnailSizePx = songThumbnailSizePx,
                                thumbnailSizeDp = songThumbnailSizeDp,
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { navController.navigate("${NavRoutes.artist.name}/${item.key}") },
                                    onLongClick = { menuState.display { OnlineArtistItemMenu(navController = navController, artist = item).MenuComponent() } }
                                )
                            )
                        }
                        is Innertube.PlaylistItem -> {
                            PlaylistItem(
                                playlist = item,
                                alternative = true,
                                thumbnailSizePx = playlistThumbnailSizePx,
                                thumbnailSizeDp = playlistThumbnailSizeDp,
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { navController.navigate("${NavRoutes.playlist.name}/${item.key}") },
                                    onLongClick = { menuState.display { OnlinePlaylistItemMenu(navController = navController, playlist = item).MenuComponent() } }
                                )
                            )
                        }
                        is Innertube.VideoItem -> {
                            VideoItem(
                                video = item,
                                thumbnailHeightDp = albumThumbnailSizeDp,
                                thumbnailWidthDp = (albumThumbnailSizeDp * 16 / 9),
                                disableScrollingText = disableScrollingText,
                                alternative = true,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = {
                                        binder?.stopRadio()
                                        if (isVideoEnabled())
                                            binder?.player?.playVideo(item.asMediaItem)
                                        else
                                            binder?.player?.forcePlay(item.asMediaItem)
                                    },
                                    onLongClick = { menuState.display { VideoItemMenu(navController = navController, song = item.asSong).MenuComponent() } }
                                )
                            )
                        }
                        null -> {}
                    }
                }
                
                if (isLoading) {
                    items(3, key = { "shimmer_$it" }, contentType = { "placeholder" }) {
                        ShimmerHost {
                            AlbumItemPlaceholder(
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
