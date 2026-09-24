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
import androidx.compose.runtime.remember
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
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.screens.rewind.slides.RewindArtwork
import app.n_zik.android.components.ui.screens.rewind.slides.RewindPlaylistArtwork
import app.n_zik.android.components.ui.screens.rewind.slides.formatRewindNumber
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference
import java.util.Locale

/**
 * "My Rewind" home page (issue #275): the entry point to the deck.
 *
 * One row per year (listening time + plays, newest first) and, for the selected year, a 3x4
 * grid of its 12 months. Tapping a year row opens the annual deck; tapping a month cell opens
 * the monthly deck (same 16 slides, data filtered to that month). Months without events are
 * greyed out and not tappable. Unfinished periods — the in-progress month, the future months
 * and the in-progress year — are shown locked (the N-Zik launcher box under the same dark
 * scrim as the playable cells, at full opacity like them: months without stats and a
 * padlock; the year with "LOCKED" instead of its stats, no album previews, and a padlock
 * instead of the "open" chevron) and open nothing: their deck unlocks only once the period
 * is over (user decision, 2026-09-24).
 * Spammer easter eggs (same decision): hammering a locked cell or year row answers with a
 * rotation of funny messages; the tap that reaches the threshold kicks the user back from
 * the home with the final "I said no" message. Tapping an empty finished month answers with
 * a random sad one-liner (no kick).
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
    // Recap type toggles (spec GH-275): live reads so a flip made in the settings takes
    // effect in real time, whatever the composition lifecycle
    val yearlyEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true)
    val monthlyEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true)
    val globalEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, true)
    val viewModel: RewindHomeViewModel = viewModel(factory = RewindHomeViewModel)
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // The locked-period spam rotation (user decision, 2026-09-24): each tap on a locked
    // month cell or year row answers with the next funny message; the tap that reaches
    // the threshold kicks the user back from the home ("chépa, j'ai dit non, arrêter").
    // The counter resets when the home leaves the composition.
    var lockedSpam by remember { mutableStateOf(0) }
    val onLockedSpam: () -> Unit = {
        val reply = nextLockedSpamReply(lockedSpam, LOCKED_SPAM_MESSAGES, R.string.rw_home_locked_spam_kick)
        lockedSpam = if (reply.kicks) 0 else lockedSpam + 1
        // The app's in-app toaster (theme colors, main-thread, floating-nav-bar aware):
        // info toasts for the rotation, red for the final "stop".
        if (reply.kicks) Toaster.e(reply.messageRes) else Toaster.i(reply.messageRes)
        // The kick sends the user back (popBackStack() is a no-op from the root entry).
        if (reply.kicks) navController.popBackStack()
    }
    // Empty finished months: a random sad reply on each tap (user decision, 2026-09-24) —
    // no kick, nothing to protect there.
    val onEmptyMonthSpam: () -> Unit = {
        Toaster.i(randomEmptyMonthMessage(EMPTY_MONTH_SPAM_MESSAGES))
    }
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
                yearlyEnabled = yearlyEnabled,
                monthlyEnabled = monthlyEnabled,
                globalEnabled = globalEnabled,
                yearsOrder = yearsOrder,
                monthsOrder = monthsOrder,
                onYearOrderToggle = { yearsOrder = !yearsOrder },
                onMonthOrderToggle = { monthsOrder = !monthsOrder },
                onYearSelected = { selectedYear = it },
                onLockedSpam = onLockedSpam,
                onEmptyMonthSpam = onEmptyMonthSpam,
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
    // Recap type toggles (spec GH-275): a disabled type is not offered — its items are skipped
    yearlyEnabled: Boolean,
    monthlyEnabled: Boolean,
    globalEnabled: Boolean,
    yearsOrder: SortOrder,
    monthsOrder: SortOrder,
    onYearOrderToggle: () -> Unit,
    onMonthOrderToggle: () -> Unit,
    onYearSelected: (Int) -> Unit,
    // The locked-period spam rotation (funny replies, then the kick — see the screen KDoc).
    onLockedSpam: () -> Unit,
    // The empty-month random replies (see the screen KDoc).
    onEmptyMonthSpam: () -> Unit,
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
        // (null while the history has no plays at all). Skipped while the all-time recap
        // is disabled in the settings (spec GH-275)
        if (globalEnabled) {
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
                            // All time has no ending: never locked.
                            locked = false,
                            onClick = onOpenGlobal
                        )
                        Spacer(Modifier.height(22.dp))
                    }
                }
            }
        }

        // YEARS — one row per year: tap selects the year and opens the deck. Skipped while
        // the yearly recaps are disabled in the settings (spec GH-275)
        if (yearlyEnabled) {
            item(key = "years_header") {
                RewindHomeSectionHeader(
                    text = stringResource(R.string.rw_home_years),
                    order = yearsOrder,
                    onToggleOrder = onYearOrderToggle
                )
            }
            items(yearsDisplay, key = { it.year }) { year ->
                Column(modifier = Modifier.animateItem()) {
                    // The in-progress year is locked until it is over (user decision,
                    // 2026-09-24): the row shows "LOCKED" instead of its stats, no album
                    // previews, and a padlock instead of the "open" chevron; the tap opens
                    // nothing (the funny replies only come out when it is spammed). The
                    // chips below keep selecting it for the month grid.
                    val yearLocked = !isYearComplete(year.year)
                    RewindHomeYearRow(
                        label = year.year.toString(),
                        // The locked year shows "LOCKED" instead of its (spoiling) stats —
                        // same anti-spoiler decision as the album previews.
                        stats = if (yearLocked) {
                            stringResource(R.string.rw_home_locked)
                        } else {
                            stringResource(
                                R.string.rw_home_year_stats,
                                rewindHomeDuration(year.minutes),
                                formatRewindNumber(year.plays.toLong())
                            )
                        },
                        tops = year.topArtworks,
                        locked = yearLocked,
                        // Opens the year deck only — the month grid and its chips below keep
                        // their current selection (the chips are the way to switch years there).
                        onClick = { if (!yearLocked) onOpenDeck(year.year, null) },
                        onLockedTap = onLockedSpam
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            item(key = "years_spacing") { Spacer(Modifier.height(14.dp)) }
        }

        // MONTHS OF <selected year> — chips switch the grid without opening the deck.
        // Skipped while the month grid is disabled in the settings (spec GH-275)
        if (monthlyEnabled) {
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
                        // no matter the display order. All twelve months are always shown:
                        // the unfinished ones (the in-progress month and the future months)
                        // render a locked cell instead of their stats, and open nothing
                        // (user decision, 2026-09-24: a deck opens only once its period is
                        // over).
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
                                        // Locked until the month is fully over (the in-progress
                                        // and the future months): no stats, no deck navigation.
                                        locked = !isMonthComplete(yearOfKey.year, index + 1),
                                        tops = yearOfKey.monthTopArtworks.getOrNull(index)
                                            ?: TopArtworks(null, null, null, null),
                                        onClick = { onOpenDeck(yearOfKey.year, index + 1) },
                                        onLockedTap = onLockedSpam,
                                        onEmptyTap = onEmptyMonthSpam
                                    )
                                }
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
    // Locked = the period is not over yet (the in-progress year): no deck navigation, no
    // album previews, a padlock replaces the "open" chevron; the caller passes "LOCKED"
    // as [stats].
    locked: Boolean,
    onClick: () -> Unit,
    // Only called while [locked] (the spam rotation — funny replies, then the kick).
    onLockedTap: () -> Unit = {}
) {
    val palette = colorPalette()
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
            // Theme background (not raw black): while the network artworks are loading,
            // the row blends into the page instead of flashing black (same user decision
            // as the month cells, 2026-09-24).
            .background(palette.background1)
            .then(
                if (locked) Modifier.clickable(onClick = onLockedTap)
                else Modifier.clickable(onClick = onClick)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (locked) {
            // In-progress year: no album/artist previews — they would spoil the annual
            // deck (user decision, 2026-09-24). The N-Zik launcher box under the dark
            // scrim, same treatment as the locked month cells.
            RewindHomeLauncherBox(Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
        } else if (hasArtwork) {
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
                if (locked) {
                    // In-progress year: a padlock instead of the "open the deck" chevron —
                    // the annual deck unlocks once the year is over.
                    Icon(
                        painter = painterResource(R.drawable.locked),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                } else {
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
    // Locked = the month is not over yet (in progress or future): the N-Zik launcher box
    // with a padlock on top, no stats, no deck navigation (user decision, 2026-09-24).
    locked: Boolean,
    tops: TopArtworks,
    onClick: () -> Unit,
    // Only called while [locked] (the spam rotation — funny replies, then the kick).
    onLockedTap: () -> Unit = {},
    // Only called while the month is finished and empty (the random sad replies).
    onEmptyTap: () -> Unit = {}
) {
    val palette = colorPalette()
    val playable = !empty && !locked
    val showArtwork = playable &&
        (!tops.song.isNullOrBlank() ||
            !tops.artist.isNullOrBlank() ||
            !tops.album.isNullOrBlank() ||
            tops.playlist != null)
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .clip(uiRoundnessShape())
            // Theme background (not raw black): while the network artworks are loading,
            // the cell blends into the page instead of flashing black (user decision,
            // 2026-09-24).
            .background(palette.background1)
            .then(
                when {
                    playable -> Modifier.clickable(onClick = onClick)
                    locked -> Modifier.clickable(onClick = onLockedTap)
                    // Empty finished month: tap for a random sad reply.
                    else -> Modifier.clickable(onClick = onEmptyTap)
                }
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
        } else {
            // No artwork: an empty finished month or an unfinished one — the N-Zik launcher
            // box under the same dark scrim as the playable cells (scrim on top of the
            // image, full opacity, so the text stays readable — user decision 2026-09-24).
            RewindHomeLauncherBox(Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            )
            if (locked) {
                // Unfinished month: a padlock on top, no stats and no message (the funny
                // replies only come out when the cell is spammed). The deck unlocks once
                // the month is over.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.locked),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                text = month.month.uppercase(Locale.getDefault()),
                color = if (showArtwork || empty || locked) Color.White else palette.text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp
            )
            if (locked) {
                // Locked cell: the "LOCKED" label under the month name (same treatment as
                // the locked year row — user decision, 2026-09-24); the funny replies still
                // appear as toasts while the cell is spammed.
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.rw_home_locked),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (playable) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rewindHomeDuration(month.minutes),
                    color = if (showArtwork) Color.White else palette.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.rw_home_plays,
                        formatRewindNumber(month.plays.toLong())
                    ),
                    color = if (showArtwork) Color.White.copy(alpha = 0.7f) else palette.textSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                // Empty finished month: a sad one-liner instead of the dash + "0 plays"
                // (user decision, 2026-09-24)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.rw_home_month_empty_sad),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
            }
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

/**
 * The locked-period spam rotation (user decision, 2026-09-24): the funny replies to a
 * locked month cell or year row being hammered, in order.
 */
internal val LOCKED_SPAM_MESSAGES = intArrayOf(
    R.string.rw_home_locked_spam_1,
    R.string.rw_home_locked_spam_2,
    R.string.rw_home_locked_spam_3,
    R.string.rw_home_locked_spam_4,
    R.string.rw_home_locked_spam_5
)

/** The tap that reaches this count kicks the user back from the home (final message). */
internal const val LOCKED_SPAM_KICK_THRESHOLD = 6

/** One step of the spam rotation: [messageRes] to answer with, [kicks] = user sent back. */
internal data class LockedSpamReply(val messageRes: Int, val kicks: Boolean)

/**
 * Pure step of the locked-period spam rotation so it can be unit-tested without UI.
 * [spamCount] is the number of taps already answered: each tap below the threshold answers
 * with the next message of [messages]; the tap that reaches [threshold] kicks the user back
 * with [kickMessageRes] (the caller resets [spamCount] to 0 on [kicks]).
 */
internal fun nextLockedSpamReply(
    spamCount: Int,
    messages: IntArray,
    kickMessageRes: Int,
    threshold: Int = LOCKED_SPAM_KICK_THRESHOLD
): LockedSpamReply {
    val next = spamCount + 1
    return if (next >= threshold) {
        LockedSpamReply(kickMessageRes, kicks = true)
    } else {
        LockedSpamReply(messages[next - 1], kicks = false)
    }
}

/**
 * The random replies to an empty finished month being tapped (user decision, 2026-09-24):
 * each tap answers with a random sad one-liner (no rotation, no kick — nothing to protect
 * there, just the joke).
 */
internal val EMPTY_MONTH_SPAM_MESSAGES = intArrayOf(
    R.string.rw_home_month_empty_spam_1,
    R.string.rw_home_month_empty_spam_2,
    R.string.rw_home_month_empty_spam_3,
    R.string.rw_home_month_empty_spam_4,
    R.string.rw_home_month_empty_spam_5,
    R.string.rw_home_month_empty_spam_6
)

/**
 * Pure random pick of an empty-month reply so it can be unit-tested without UI:
 * [nextIndex] is invoked with the list size and its result is reduced modulo it.
 */
internal fun randomEmptyMonthMessage(
    messages: IntArray,
    nextIndex: (Int) -> Int = { (0 until it).random() }
): Int {
    return messages[nextIndex(messages.size) % messages.size]
}
