package app.n_zik.android.components.ui.screens.rewind

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.screens.rewind.slides.RewindArtwork
import app.n_zik.android.components.ui.screens.rewind.slides.RewindPlaylistArtwork
import app.n_zik.android.components.ui.screens.rewind.slides.formatRewindNumber
import app.n_zik.android.uiRoundnessShape
import java.util.Locale

/**
 * "My Rewind" home page (issue #275): the entry point to the deck.
 *
 * One row per year (listening time + plays, newest first) and, for the selected year, a 3x4
 * grid of its 12 months. Tapping a year row opens the annual deck; tapping a month cell opens
 * the monthly deck (same 16 slides, data filtered to that month). Months without events are
 * greyed out and not tappable.
 *
 * Layout follows the QuickPicks home section: the whole page is a LazyColumn (the year rows are
 * its items) and the month grid is a fixed-height LazyVerticalGrid inside one of those items.
 * The fixed height is required: a lazy layout nested in a scrollable Column receives infinite
 * height constraints and crashes.
 */
@Composable
fun RewindHomeScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {}
) {
    val palette = colorPalette()
    val viewModel: RewindHomeViewModel = viewModel(factory = RewindHomeViewModel)
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // Saveable so the selected year survives a round-trip into the deck
    var selectedYear by rememberSaveable { mutableIntStateOf(-1) }
    // Display orders, same contract as the app's sort toolbars (Sort.ToolBarButton):
    // arrow up = ascending, arrow down (rotated 180deg) = descending, tap flips.
    // Years default to chronological order (oldest first); months to calendar order (Jan to Dec).
    var yearsOrder by rememberSaveable { mutableStateOf(SortOrder.Ascending) }
    var monthsOrder by rememberSaveable { mutableStateOf(SortOrder.Ascending) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    // Default to the newest year with data; keep the previous selection when it is still
    // valid (coming back from the deck).
    LaunchedEffect(uiState.years) {
        if (selectedYear !in uiState.years.map { it.year }) {
            selectedYear = uiState.years.firstOrNull()?.year ?: -1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background0)
    ) {
        if (uiState.isLoading) {
            // Theme-aware loader while the year summaries are built
            Loader(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.years.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.musical_notes),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = palette.textDisabled
                    )
                    Text(
                        text = stringResource(R.string.rw_home_empty),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            RewindHomeContent(
                years = uiState.years,
                global = uiState.global,
                selectedYear = selectedYear,
                yearsOrder = yearsOrder,
                monthsOrder = monthsOrder,
                onYearOrderToggle = { yearsOrder = !yearsOrder },
                onMonthOrderToggle = { monthsOrder = !monthsOrder },
                onYearSelected = { selectedYear = it },
                onOpenDeck = { year, month ->
                    navController.navigate(
                        if (month == null) {
                            "${NavRoutes.rewind.name}?year=$year"
                        } else {
                            "${NavRoutes.rewind.name}?year=$year&month=$month"
                        }
                    )
                },
                onOpenGlobal = {
                    navController.navigate("${NavRoutes.rewind.name}?scope=global")
                }
            )
        }
        // The mini player is drawn as on every screen; AppNavigation wraps it in a jelly-spring
        // slide-down + fade while the home is on screen (same animation as the header hide)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            miniPlayer()
        }
    }
}

/**
 * The page body: a LazyColumn (like QuickPicks) holding the hero, the year rows and the
 * month grid item.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RewindHomeContent(
    years: List<RewindHomeYear>,
    global: RewindHomeGlobal?,
    selectedYear: Int,
    yearsOrder: SortOrder,
    monthsOrder: SortOrder,
    onYearOrderToggle: () -> Unit,
    onMonthOrderToggle: () -> Unit,
    onYearSelected: (Int) -> Unit,
    onOpenDeck: (year: Int, month: Int?) -> Unit,
    onOpenGlobal: () -> Unit
) {
    val palette = colorPalette()
    val yearsDisplay = if (yearsOrder == SortOrder.Descending) years else years.reversed()
    val selected = years.firstOrNull { it.year == selectedYear }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        // Brand hero
        item(key = "hero") {
            Text(
                text = stringResource(R.string.rw_home_title),
                color = palette.text,
                fontSize = 24.sp,
                lineHeight = 25.sp,
                letterSpacing = (-0.8).sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.rw_home_tagline),
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
            Spacer(Modifier.height(26.dp))
        }

        // ALL TIME — the lifetime totals, above the years: tap opens the all-time deck
        // (null while the history has no plays at all)
        global?.let { allTime ->
            item(key = "all_time") {
                Column(modifier = Modifier.animateItem()) {
                    RewindHomeYearRow(
                        label = stringResource(R.string.rw_home_all_time),
                        stats = stringResource(
                            R.string.rw_home_year_stats,
                            rewindHomeDuration(allTime.minutes),
                            formatRewindNumber(allTime.plays.toLong())
                        ),
                        tops = allTime.topArtworks,
                        onClick = onOpenGlobal
                    )
                    Spacer(Modifier.height(22.dp))
                }
            }
        }

        // YEARS — one row per year: tap selects the year and opens the deck
        item(key = "years_header") {
            RewindHomeSectionHeader(
                text = stringResource(R.string.rw_home_years),
                order = yearsOrder,
                onToggleOrder = onYearOrderToggle
            )
        }
        items(yearsDisplay, key = { it.year }) { year ->
            Column(modifier = Modifier.animateItem()) {
                RewindHomeYearRow(
                    label = year.year.toString(),
                    stats = stringResource(
                        R.string.rw_home_year_stats,
                        rewindHomeDuration(year.minutes),
                        formatRewindNumber(year.plays.toLong())
                    ),
                    tops = year.topArtworks,
                    // Opens the year deck only — the month grid and its chips below keep
                    // their current selection (the chips are the way to switch years there).
                    onClick = { onOpenDeck(year.year, null) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item(key = "years_spacing") { Spacer(Modifier.height(14.dp)) }

        // MONTHS OF <selected year> — chips switch the grid without opening the deck
        item(key = "months_header") {
            RewindHomeSectionHeader(
                text = stringResource(R.string.rw_home_months_of, selectedYear.toString()),
                order = monthsOrder,
                onToggleOrder = onMonthOrderToggle
            )
        }
        item(key = "year_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Chips keep the stable data order (newest first) — the year sort above only
                // reorders the year rows, never the chips.
                years.forEach { year ->
                    FilterChip(
                        label = { Text(year.year.toString()) },
                        selected = year.year == selectedYear,
                        shape = uiRoundnessShape(),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colorPalette().background1,
                            labelColor = colorPalette().text,
                            selectedContainerColor = colorPalette().accent,
                            selectedLabelColor = colorPalette().onAccent,
                        ),
                        onClick = { onYearSelected(year.year) }
                    )
                }
            }
        }

        selected?.let { year ->
            // Crossfade the whole grid when the chip switches the year: the cells keep the
            // same month keys, so a plain recomposition would swap the content instantly.
            // A sort toggle only reorders the items, which animateItem() handles.
            item(key = "months_grid") {
                Crossfade(
                    targetState = year.year,
                    modifier = Modifier.fillMaxWidth()
                ) { yearKey ->
                    val yearOfKey = years.first { it.year == yearKey }
                    // Calendar month numbers in display order; the number drives navigation
                    // no matter the display order.
                    val monthEntries = yearOfKey.months
                        .mapIndexed { index, month -> index to month }
                        .let { if (monthsOrder == SortOrder.Descending) it.reversed() else it }

                    // Fixed-height lazy grid (QuickPicks pattern): 12 months over 4 rows of
                    // 3. The height is derived from the actual cell width so the grid fits
                    // exactly, with no nested scroll.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val spacing = 10.dp
                        val cellWidth = (maxWidth - 2 * spacing) / 3
                        val gridHeight = 4 * (cellWidth / 0.92f) + 3 * spacing + 6.dp
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            flingBehavior = ScrollableDefaults.flingBehavior(),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            contentPadding = PaddingValues(top = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gridHeight)
                        ) {
                            items(monthEntries, key = { it.first + 1 }) { entry ->
                                val (index, month) = entry
                                RewindHomeMonthCell(
                                    modifier = Modifier.animateItem(),
                                    month = month,
                                    empty = month.plays == 0,
                                    tops = yearOfKey.monthTopArtworks.getOrNull(index)
                                        ?: TopArtworks(null, null, null, null),
                                    onClick = { onOpenDeck(yearOfKey.year, index + 1) }
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(Dimensions.bottomSpacer)) }
    }
}

@Composable
private fun RewindHomeSectionHeader(
    text: String,
    order: SortOrder,
    onToggleOrder: () -> Unit
) {
    val palette = colorPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp
        )
        RewindHomeSortArrow(order = order, onToggle = onToggleOrder)
    }
}

/**
 * Sort direction toggle, reusing the app's shared TabToolBar.Icon component
 * (same visual contract as Sort.ToolBarButton): arrow_up at toolbar size,
 * rotated 180deg with 400ms linear animation, clip(uiRoundnessShape()) + ripple.
 */
@Composable
private fun RewindHomeSortArrow(
    order: SortOrder,
    onToggle: () -> Unit
) {
    TabToolBar.Icon(
        iconId = R.drawable.arrow_up,
        tint = colorPalette().text,
        size = 32.dp,
        enabled = true,
        modifier = Modifier.graphicsLayer { rotationZ = order.rotationZ },
        onShortClick = onToggle,
        onLongClick = {}
    )
}

/**
 * One tappable home row (a year, or the all-time row): a strip collage of the period's top
 * song/album/artist/playlist behind the label + totals, with the same dark-scrim treatment
 * as the month cells.
 */
@Composable
private fun RewindHomeYearRow(
    label: String,
    stats: String,
    tops: TopArtworks,
    onClick: () -> Unit
) {
    // Row height; all four quadrants share it, as in the month collage.
    val rowHeight = 96.dp
    val hasArtwork = !tops.song.isNullOrBlank() ||
        !tops.album.isNullOrBlank() ||
        !tops.artist.isNullOrBlank() ||
        tops.playlist != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(uiRoundnessShape())
            .background(Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        if (hasArtwork) {
            // The year's top song, album, artist and playlist as a strip behind the text;
            // the dark scrim keeps the white text readable over any covers.
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1: song
                Box(modifier = Modifier.weight(1f)) {
                    if (tops.song.isNullOrBlank()) {
                        RewindHomeLauncherBox(Modifier.fillMaxSize())
                    } else {
                        RewindArtwork(
                            imageUrl = tops.song,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 2: album
                Box(modifier = Modifier.weight(1f)) {
                    if (tops.album.isNullOrBlank()) {
                        RewindHomeLauncherBox(Modifier.fillMaxSize())
                    } else {
                        RewindArtwork(
                            imageUrl = tops.album,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 3: artist
                Box(modifier = Modifier.weight(1f)) {
                    if (tops.artist.isNullOrBlank()) {
                        RewindHomeLauncherBox(Modifier.fillMaxSize())
                    } else {
                        RewindArtwork(
                            imageUrl = tops.artist,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 4: playlist — the same quadrant as the others. The cover mosaic is sized to
                // the quadrant's height so its corner-aligned covers fill the whole cell, as in
                // the month collage.
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val playlist = tops.playlist
                    if (playlist != null) {
                        RewindPlaylistArtwork(
                            playlistId = playlist.playlist.id,
                            title = playlist.playlist.cleanName(),
                            name = playlist.playlist.name,
                            browseId = playlist.playlist.browseId,
                            isYoutubePlaylist = playlist.playlist.isYoutubePlaylist,
                            sizeDp = maxHeight,
                            shape = RoundedCornerShape(0.dp),
                            emptyFallback = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        RewindHomeLauncherBox(Modifier.fillMaxSize())
                    }
                }
            }
            // Dark scrim on top of the images so the text is readable — same 0.7 as the month cell.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
        } else {
            // No plays this year: the N-Zik launcher box behind the label, under a dark
            // scrim — same treatment as an empty month cell.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
            RewindHomeLauncherBox(Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stats,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.chevron_forward),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}



@Composable
private fun RewindHomeLauncherBox(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_box),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun RewindHomeMonthCell(
    modifier: Modifier = Modifier,
    month: MonthlyStat,
    empty: Boolean,
    tops: TopArtworks,
    onClick: () -> Unit
) {
    val palette = colorPalette()
    val playable = !empty
    val showArtwork = playable &&
        (!tops.song.isNullOrBlank() ||
            !tops.artist.isNullOrBlank() ||
            !tops.album.isNullOrBlank() ||
            tops.playlist != null)
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .clip(uiRoundnessShape())
            .background(Color.Black)
            .graphicsLayer { alpha = if (playable) 1f else 0.35f }
            .then(
                if (playable) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (showArtwork) {
            // The month's top song, album, artist and playlist as a 2x2 collage behind the
            // text; the dark scrim keeps the white text readable over any covers.
            RewindHomeMonthCollage(tops, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
        } else if (empty) {
            // No plays this month: the N-Zik launcher box behind the label, under a dark
            // scrim — same treatment as playable cells with artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
            RewindHomeLauncherBox(Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                text = month.month.uppercase(Locale.getDefault()),
                color = if (showArtwork || empty) Color.White else palette.text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (playable) rewindHomeDuration(month.minutes) else "—",
                color = if (showArtwork || empty) Color.White else palette.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.rw_home_plays,
                    formatRewindNumber(month.plays.toLong())
                ),
                color = if (showArtwork || empty) Color.White.copy(alpha = 0.7f) else palette.textSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 2x2 collage of a month's top items: song and album on top, artist (landscape crop of the
 * portrait artwork) and playlist (the app's cover pipeline) below. Missing categories stay
 * on the base background.
 */
@Composable
private fun RewindHomeMonthCollage(
    tops: TopArtworks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            RewindHomeThumbnailCell(tops.song, Modifier.weight(1f))
            RewindHomeThumbnailCell(tops.album, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            RewindHomeThumbnailCell(tops.artist, Modifier.weight(1f))
            // The playlist cover is a mosaic of corner-aligned square covers: sizing it to
            // the quadrant's height makes the top/bottom covers overlap across the (narrower)
            // width, so it fills the whole quadrant instead of a small centered square. The
            // cell is already rounded: no corner radius on the mosaic itself.
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val playlist = tops.playlist
                if (playlist != null) {
                    RewindPlaylistArtwork(
                        playlistId = playlist.playlist.id,
                        title = playlist.playlist.cleanName(),
                        name = playlist.playlist.name,
                        browseId = playlist.playlist.browseId,
                        isYoutubePlaylist = playlist.playlist.isYoutubePlaylist,
                        sizeDp = maxHeight,
                        shape = RoundedCornerShape(0.dp),
                        emptyFallback = true,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // No top playlist for this month: the N-Zik launcher box.
                    RewindHomeLauncherBox(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun RewindHomeThumbnailCell(
    thumbnail: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (thumbnail.isNullOrBlank()) {
            // No artwork: the N-Zik launcher box (ImageCacheFactory's fallback).
            RewindHomeLauncherBox(Modifier.fillMaxSize())
        } else {
            RewindArtwork(
                imageUrl = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Compact listening time for the home rows and cells: "38 h 12" over an hour, "45 min" under
 * one (reuses the deck's compact minutes string).
 */
@Composable
private fun rewindHomeDuration(minutes: Long): String {
    val hours = (minutes / 60L).toInt()
    val rest = (minutes % 60L).toInt()
    return if (hours > 0) {
        stringResource(R.string.rw_home_duration, hours, rest)
    } else {
        stringResource(R.string.rw_minutes_compact, formatRewindNumber(minutes))
    }
}
