package app.it.fast4x.rimusic.ui.screens.history

import app.n_zik.android.core.database.*

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import app.n_zik.android.components.Sort
import app.it.fast4x.rimusic.utils.Preference
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastDistinctBy
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.it.fast4x.compose.persist.persist
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.HistoryPage
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.HistoryType
import app.n_zik.android.enums.HistorySortOrder
import app.it.fast4x.rimusic.models.Event
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.ui.components.ButtonsRow
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.components.themed.Title
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.syncImportHistoryKey
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.historyTypeKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.n_zik.android.components.tab.Search
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.it.fast4x.rimusic.enums.SortOrder
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.song.SongItemMenu
import app.it.fast4x.rimusic.utils.historySortMenuOrderKey
import androidx.compose.foundation.text.BasicText
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.n_zik.android.typography
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDate

@kotlin.OptIn(ExperimentalTextApi::class)
@OptIn(UnstableApi::class)
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HistoryList(
    navController: NavController
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val lazyListState = rememberLazyListState()

    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val search = Search(lazyListState)

    val sort = Sort<HistorySortOrder>(Preference.HISTORY_SORT_BY, Preference.HISTORY_SORT_ORDER, historySortMenuOrderKey, "hist")

    var isCoreSyncEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isCoreSyncEnabled = withContext(Dispatchers.IO) {
            appContext().encryptedPreferences.getBoolean(enableYouTubeSyncKey, false)
        }
    }

    val events by remember(sort.sortBy, sort.sortOrder, parentalControlEnabled) {
        Database.eventTable
                .allWithSong()
                .distinctUntilChanged()
                .map { list ->
                    val filtered = list.filter { !parentalControlEnabled || !it.song.title.startsWith( EXPLICIT_PREFIX, true ) }
                    val sorted = when (sort.sortOrder) {
                        SortOrder.Ascending -> filtered
                        SortOrder.Descending -> filtered.reversed()
                    }
                    when (sort.sortBy) {
                        HistorySortOrder.DATE -> {
                            val today = LocalDate.now()
                            val yesterday = today.minusDays(1)
                            sorted.groupBy { event ->
                                val eventDate = Instant.ofEpochMilli(event.event.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                val daysBetween = ChronoUnit.DAYS.between(eventDate, today)
                                when {
                                    eventDate.isEqual(today) -> context.getString(R.string.today)
                                    eventDate.isEqual(yesterday) -> context.getString(R.string.yesterday)
                                    daysBetween in 2..7 -> context.getString(R.string.x_days_ago, daysBetween.toInt())
                                    daysBetween in 8..14 -> context.getString(R.string.last_week)
                                    eventDate.year == today.year && eventDate.monthValue == today.monthValue -> context.getString(R.string.this_month)
                                    eventDate.year == today.year -> SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(event.event.timestamp)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                    eventDate.year == today.year - 1 -> context.getString(R.string.last_year)
                                    else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(event.event.timestamp)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                }
                            }
                        }
                        HistorySortOrder.ALPHABETICAL -> {
                            sorted.sortedBy { it.song.title }.groupBy { it.song.title.firstOrNull()?.uppercase() ?: "#" }
                        }
                        HistorySortOrder.ARTIST -> {
                            sorted.sortedBy { it.song.artistsText }.groupBy { it.song.artistsText?.split(",")?.first()?.trim() ?: "Unknown" }
                        }
                    }
                }
    }.collectAsState( emptyMap(), Dispatchers.IO )

    val buttonsList = mutableListOf(HistoryType.History to stringResource(R.string.history))
    val syncImportHistory by rememberPreference(syncImportHistoryKey, false)
    if (isCoreSyncEnabled && syncImportHistory) {
        buttonsList += HistoryType.YTMHistory to stringResource(R.string.yt_history)
    }

    var historyType by rememberPreference(historyTypeKey, HistoryType.History)

    var isLocalLoading by remember { mutableStateOf(true) }
    var isYTMLoading by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        if (events.isNotEmpty()) {
            isLocalLoading = false
        }
    }

    LaunchedEffect(Unit) {
        delay(1500)
        isLocalLoading = false
    }

    var historyPage by persist<Result<HistoryPage>>("home/history/pageResult")
    LaunchedEffect(historyType) {
        if (historyType == HistoryType.YTMHistory && isYouTubeSyncEnabled()) {
            isYTMLoading = true
            historyPage = YtMusic.getHistory(setLogin = true)
            isYTMLoading = false
        }
    }

    Column (
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.history),
            iconId = R.drawable.history,
            enabled = false,
            showIcon = false,
            modifier = Modifier,
            onClick = {}
        )

        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ButtonsRow(
                chips = buttonsList,
                currentValue = historyType,
                onValueUpdate = { historyType = it },
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { search.isVisible = !search.isVisible }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.search_circle),
                        contentDescription = stringResource(R.string.search),
                        tint = colorPalette().text
                    )
                }
                sort.ToolBarButton()
            }
        }

        AnimatedVisibility(
            visible = search.isVisible,
            modifier = Modifier.fillMaxWidth()
        ) {
            search.SearchBar(this@Column)
        }

        val isLoading = when (historyType) {
            HistoryType.History -> isLocalLoading && events.isEmpty()
            HistoryType.YTMHistory -> isYTMLoading || (historyPage == null && isYouTubeSyncEnabled())
        }

        if (isLoading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Loader()
            }
        } else if (
            (historyType == HistoryType.History && events.isEmpty()) ||
            (historyType == HistoryType.YTMHistory && historyPage?.getOrNull()?.sections.isNullOrEmpty())
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = stringResource(R.string.no_items),
                    style = typography().m.semiBold.copy(
                        color = colorPalette().textSecondary
                    )
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.End).asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {
                if (historyType == HistoryType.History) {
                    events.forEach { (headerStr, details) ->
                        stickyHeader {
                            Title(
                                title = headerStr,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .background(
                                        color = colorPalette().accent,
                                        shape = thumbnailShape()
                                    )
                            )
                        }

                        items(
                            items = details.fastDistinctBy { it.song.id }
                                .filter { event ->
                                    event.song.title.contains(search.inputValue, ignoreCase = true) ||
                                            (event.song.artistsText ?: "").contains(search.inputValue, ignoreCase = true)
                                },
                            key = { it.event.id }
                        ) { event ->
                            SwipeablePlaylistItem(
                                mediaItem = event.song.asMediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(event.song.asMediaItem)
                                },
                                onEnqueue = {
                                    binder?.player?.enqueue(event.song.asMediaItem)
                                }
                            ) {
                                SongItem(
                                    song = event.song,
                                    navController = navController,
                                    modifier = Modifier,

                                    onClick = {
                                        binder?.player?.forcePlay(event.song.asMediaItem)
                                    }
                                )
                            }
                        }
                    }
                }

                if (historyType == HistoryType.YTMHistory) {
                    historyPage?.getOrNull()?.sections?.forEach { section ->
                        stickyHeader {
                            Title(
                                title = section.title,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .background(
                                        color = colorPalette().accent,
                                        shape = thumbnailShape()
                                    )
                            )
                        }

                        items(
                            items = section.songs
                                .map { it.asMediaItem }
                                .filter { it.mediaId.isNotEmpty() }
                                .filter { !parentalControlEnabled || it.mediaMetadata.title?.startsWith(EXPLICIT_PREFIX, true) != true }
                                .filter { mediaItem ->
                                    (mediaItem.mediaMetadata.title ?: "").contains(search.inputValue, ignoreCase = true) ||
                                            (mediaItem.mediaMetadata.artist ?: "").contains(search.inputValue, ignoreCase = true)
                                },
                            key = { it.mediaId }
                        ) { mediaItem ->
                            SwipeablePlaylistItem(
                                mediaItem = mediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(mediaItem)
                                },
                                onEnqueue = {
                                    binder?.player?.enqueue(mediaItem)
                                }
                            ) {
                                SongItem(
                                    song = mediaItem.asSong,
                                    navController = navController,
                                    modifier = Modifier,

                                    onClick = {
                                        binder?.player?.forcePlay(mediaItem)
                                    },
                                    onLongClick = {
                                        menuState.display {
                                            SongItemMenu(
                                                navController = navController,
                                                song = mediaItem.asSong
                                            ).MenuComponent()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

