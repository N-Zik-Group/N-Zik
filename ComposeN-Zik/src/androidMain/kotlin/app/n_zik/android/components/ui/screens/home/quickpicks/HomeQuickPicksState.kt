package app.n_zik.android.components.ui.screens.home.quickpicks

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.compose.persist.persist
import app.it.fast4x.compose.persist.persistList
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.enums.*
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.core.database.Database
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.days
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn

@UnstableApi
class HomeQuickPicksState(
    var trendingList: MutableState<List<Song>>,
    var trending: MutableState<Song?>,
    val trendingInit: Song?,
    var relatedPageResult: MutableState<Result<Innertube.RelatedPage?>?>,
    var discoverPageResult: MutableState<Result<Innertube.DiscoverPage?>?>,
    var discoverPageInit: MutableState<Innertube.DiscoverPage?>,
    var homePageResult: MutableState<Result<HomePage?>?>,
    var homePageInit: MutableState<HomePage?>,
    var chartsPageResult: MutableState<Result<Innertube.ChartsPage?>?>,
    var chartsPageInit: MutableState<Innertube.ChartsPage?>,
    var loadedQuickPicks: MutableState<Boolean>,
    var loadedData: MutableState<Boolean>,
    val playEventType: PlayEventsType,
    val selectedCountryCode: Countries,
    val parentalControlEnabled: Boolean,
    val localCount: Int,
    var recommendations: MutableState<List<Song>>,
    var ytmQuickPicks: MutableState<List<Song>>,
    var refreshing: MutableState<Boolean>,
    var refreshKey: MutableState<Int>
) {
    companion object {
        // Outlives the composable so a Quick Picks load keeps running when the
        // user switches pages; on return the results are already there instead
        // of a cancelled load forcing a full reload.
        private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var sharedLoadJob: Job? = null

        @Volatile
        private var sharedRefreshPending = false
    }

    private val from = 18250.days.inWholeMilliseconds
    private var dbJob: Job? = null

    @SuppressLint("SuspiciousIndentation")
    suspend fun loadData(force: Boolean = false): Boolean {
        if (!force && shouldSkipQuickPicksLoad(loadedData.value, homePageInit.value != null)) {
            // Consistency repair: local data present but its "loaded" flag was lost
            // (DB observer cancelled before its first emission) — do not spin forever.
            if (trendingList.value.isNotEmpty() && !loadedQuickPicks.value) {
                loadedQuickPicks.value = true
            }
            return false
        }

        Timber.tag("HomeQuickPicksState").d("Starting loadData...")

        runCatching {
            // Phase 1: Parallel network calls for charts, discover, quick picks
            supervisorScope {
                val chartsDeferred = async(Dispatchers.IO) {
                    Innertube.chartsPageComplete(countryCode = selectedCountryCode.name)
                }
                val discoverDeferred = async(Dispatchers.IO) {
                    Innertube.discoverPage()
                }
                val quickPicksDeferred = async(Dispatchers.IO) {
                    if (isYouTubeLoggedIn() && Innertube.useLoginForBrowse) {
                        YtMusic.getQuickPicks(setLogin = true).getOrNull()
                    } else null
                }

                runCatching { chartsDeferred.await() }
                    .onSuccess { result ->
                        chartsPageResult.value = result
                        chartsPageInit.value = result?.getOrNull()
                        Timber.tag("HomeQuickPicksState").d("Charts loaded")
                    }
                    .onFailure { e ->
                        Timber.tag("HomeQuickPicksState").w(e, "Charts load failed")
                    }

                runCatching { discoverDeferred.await() }
                    .onSuccess { result ->
                        discoverPageResult.value = result
                        discoverPageInit.value = result?.getOrNull()
                        Timber.tag("HomeQuickPicksState").d("YouTube Discovery data loaded")
                    }
                    .onFailure { e ->
                        Timber.tag("HomeQuickPicksState").w(e, "Discover load failed")
                    }

                runCatching { quickPicksDeferred.await() }
                    .onSuccess { items ->
                        if (items != null && items.isNotEmpty()) {
                            ytmQuickPicks.value = items.map { it.asSong }
                            Timber.tag("HomeQuickPicksState").d("Lightweight Quick Picks loaded (${items.size} items)")
                        }
                    }
                    .onFailure { e ->
                        Timber.tag("HomeQuickPicksState").w(e, "Quick picks load failed")
                    }
            }

            // Phase 2: Database observation with related page fetch (coupled as before)
            dbJob?.cancel()
            dbJob = loadScope.launch(Dispatchers.IO) {
                when (playEventType) {
                    PlayEventsType.MostPlayed ->
                        Database.eventTable
                                .findSongsMostPlayedBetween(from = from, limit = localCount)
                                .distinctUntilChanged()
                                .collect { songs ->
                                    trendingList.value = songs.distinctBy { it.id }
                                                        .filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                                                        .take(localCount)
                                    trending.value = trendingList.value.firstOrNull()
                                    if (relatedPageResult.value == null || trending.value?.id != trendingList.value.firstOrNull()?.id) {
                                        relatedPageResult.value = Innertube.relatedPage(videoId = trending.value?.id ?: "4NRXx6U8ABQ", setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)
                                    }
                                    loadedQuickPicks.value = true
                                    Timber.tag("HomeQuickPicksState").d("Local data loaded (Trending: ${songs.size})")
                                }
                    PlayEventsType.LastPlayed -> {
                        Database.eventTable
                                .findSongsLastPlayed(limit = localCount)
                                .distinctUntilChanged()
                                .collect { songs ->
                                    trendingList.value = songs.distinctBy { it.id }
                                                        .filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                                                        .take(localCount)
                                    trending.value = trendingList.value.firstOrNull()
                                    if (relatedPageResult.value == null || trending.value?.id != trendingList.value.firstOrNull()?.id) {
                                        relatedPageResult.value = Innertube.relatedPage(videoId = trending.value?.id ?: "4NRXx6U8ABQ", setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)
                                    }
                                    loadedQuickPicks.value = true
                                    Timber.tag("HomeQuickPicksState").d("Local data loaded (Trending: ${songs.size})")
                                }
                    }
                    PlayEventsType.CasualPlayed -> {
                        Database.eventTable
                                .findSongsMostPlayedBetween(from = 0, limit = 100)
                                .distinctUntilChanged()
                                .collect { songs ->
                                    val originalList = songs.distinctBy { it.id }
                                                            .filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                                    val shuffled = originalList.shuffled().take(localCount)
                                    trendingList.value = shuffled
                                    trending.value = shuffled.firstOrNull()
                                    if (relatedPageResult.value == null || trending.value?.id != shuffled.firstOrNull()?.id) {
                                        relatedPageResult.value = Innertube.relatedPage(videoId = trending.value?.id ?: "4NRXx6U8ABQ", setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)
                                    }
                                    loadedQuickPicks.value = true
                                }
                    }
                }
            }

            // Phase 3: Home page sections (sequential but with early exit)
            // Also re-run when no YTM data is available: a stale "loaded" flag
            // left by a cancelled or failed load must not block the re-fetch.
            if (!shouldSkipQuickPicksLoad(loadedData.value, homePageInit.value != null)) {
                var cumulativeSections = homePageInit.value?.sections.orEmpty()
                var cumulativeChips = homePageInit.value?.chips.orEmpty()
                repeat(3) { attempt ->
                    val result = YtMusic.getHomePage(setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)
                    result.getOrNull()?.let { page ->
                        val newSections = mutableListOf<HomePage.Section>()
                        val existingSections = cumulativeSections.toMutableList()

                        page.sections.forEach { newSection ->
                            if (newSection.title.contains("Quick picks", ignoreCase = true)) return@forEach

                            val index = existingSections.indexOfFirst { it.title == newSection.title }
                            if (index != -1) {
                                val existing = existingSections[index]
                                val mergedItems = (existing.items + newSection.items)
                                    .filterNotNull()
                                    .distinctBy { it.key }
                                existingSections[index] = existing.copy(items = mergedItems)
                            } else {
                                newSections.add(newSection)
                            }
                        }

                        cumulativeSections = existingSections + newSections
                        cumulativeChips = (cumulativeChips + (page.chips ?: emptyList())).distinctBy { it.title }
                        homePageResult.value = Result.success(HomePage(sections = cumulativeSections, chips = cumulativeChips))
                        homePageInit.value = homePageResult.value?.getOrNull()
                    }
                    if (cumulativeSections.size > 15) return@repeat
                }

                Timber.tag("HomeQuickPicksState").d("YouTube Music sections loaded: ${homePageInit.value?.sections?.size ?: 0}")
            }

        }.onFailure { e ->
            if (e is CancellationException) {
                // Load was cancelled (navigation away): reset the flags together
                // so a stale "loaded" flag can never block a future reload.
                Timber.tag("HomeQuickPicksState").d("LoadData cancelled, flags reset")
                loadedData.value = false
                if (trendingList.value.isEmpty()) loadedQuickPicks.value = false
            } else {
                Timber.tag("HomeQuickPicksState").e("Failed loadData ${e.stackTraceToString()}")
                loadedData.value = false
            }
        }.onSuccess {
            // Only mark as loaded when YTM data is actually present; otherwise an
            // empty/failed phase 3 would block future reloads forever.
            loadedData.value = homePageInit.value != null
        }
        return true
    }

    /**
     * Single-flight entry point for loading: only one load runs at a time, and
     * the running load survives page switches (it lives in the shared companion
     * scope, not the composable scope). A call made while a load is in progress
     * marks a deferred reload instead of starting a concurrent one (concurrent
     * loads used to interleave the phase 3 merge and corrupt the YTM sections/chips).
     */
    fun load() {
        val job = sharedLoadJob
        if (job != null && job.isActive) {
            sharedRefreshPending = true
            Timber.tag("HomeQuickPicksState").d("Load in progress, refresh deferred")
            return
        }
        sharedLoadJob = loadScope.launch {
            // The pull-to-refresh indicator tracks a real data load only: startup,
            // an explicit refresh, or a deferred reload. A cached page change is a
            // no-op load and must not flash the indicator.
            if (!shouldSkipQuickPicksLoad(loadedData.value, homePageInit.value != null)) {
                refreshing.value = true
            }
            try {
                loadData()
                delay(500)
                if (sharedRefreshPending && coroutineContext[Job]?.isActive == true) {
                    sharedRefreshPending = false
                    refreshKey.value++
                    refreshing.value = true
                    Timber.tag("HomeQuickPicksState").d("Deferred refresh after load completed")
                    loadData(force = true)
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    fun refresh() {
        val job = sharedLoadJob
        if (job != null && job.isActive) {
            // A load is in progress: tearing its state down mid-flight is what
            // caused the corrupted data. Defer a full reload instead; the running
            // load keeps the indicator up until the deferred reload finishes.
            sharedRefreshPending = true
            Timber.tag("HomeQuickPicksState").d("Refresh deferred: load already in progress")
            return
        }
        if (refreshing.value) return
        refreshKey.value++
        trendingList.value = emptyList()
        ytmQuickPicks.value = emptyList()
        recommendations.value = emptyList()
        loadedQuickPicks.value = false
        loadedData.value = false
        relatedPageResult.value = null
        trending.value = null
        homePageResult.value = null
        homePageInit.value = null
        discoverPageResult.value = null
        discoverPageInit.value = null
        chartsPageResult.value = null
        chartsPageInit.value = null
        load()
    }
}

/**
 * Decides whether Quick Picks can skip a load because the YTM data is already
 * present. A stale "loaded" flag without data must never be trusted: that
 * combination is exactly what used to leave the page stuck with no spinner
 * and no YouTube categories.
 */
fun shouldSkipQuickPicksLoad(loadedData: Boolean, homePagePresent: Boolean): Boolean =
    loadedData && homePagePresent

@UnstableApi
@Composable
fun rememberHomeQuickPicksState(
    playEventType: PlayEventsType,
    selectedCountryCode: Countries,
    parentalControlEnabled: Boolean,
    localCount: Int
): HomeQuickPicksState {
    val trendingList = persistList<Song>("home/quickpicks/trending_list")
    val trending = persist<Song?>("home/quickpicks/trending")
    val trendingInit = persist<Song?>(tag = "home/quickpicks/trending_init").value

    val relatedPageResult = persist<Result<Innertube.RelatedPage?>?>(tag = "home/quickpicks/relatedPageResult")
    
    val discoverPageResult = persist<Result<Innertube.DiscoverPage?>?>("home/quickpicks/discoveryAlbumsResult")
    val discoverPageInit = persist<Innertube.DiscoverPage?>("home/quickpicks/discoveryAlbumsInit")

    val homePageResult = persist<Result<HomePage?>?>("home/quickpicks/homePageResult")
    // persist (not rememberPreference) so a load running in the background
    // writes to the same shared state the next composable instance reads.
    val homePageInit = persist<HomePage>(tag = "home/quickpicks/homePageInit")

    val ytmQuickPicks = persistList<Song>("home/quickpicks/ytmQuickPicks")

    val chartsPageResult = persist<Result<Innertube.ChartsPage?>?>("home/quickpicks/chartsPageResult")
    val chartsPageInit = persist<Innertube.ChartsPage?>("home/quickpicks/chartsPageInit")

    val loadedQuickPicks = persist("home/quickpicks/loadedQuickPicks", false)
    val loadedData = persist("home/quickpicks/loadedData", false)
    
    val recommendations = persistList<Song>("home/quickpicks/recommendations_list")
    val refreshing = remember { mutableStateOf(false) }
    val refreshKey = remember { mutableIntStateOf(0) }

    return remember(playEventType, selectedCountryCode, parentalControlEnabled, localCount) {
        HomeQuickPicksState(
            trendingList = trendingList,
            trending = trending,
            trendingInit = trendingInit,
            relatedPageResult = relatedPageResult,
            discoverPageResult = discoverPageResult,
            discoverPageInit = discoverPageInit,
            homePageResult = homePageResult,
            homePageInit = homePageInit,
            chartsPageResult = chartsPageResult,
            chartsPageInit = chartsPageInit,
            loadedQuickPicks = loadedQuickPicks,
            loadedData = loadedData,
            playEventType = playEventType,
            selectedCountryCode = selectedCountryCode,
            parentalControlEnabled = parentalControlEnabled,
            localCount = localCount,
            recommendations = recommendations,
            ytmQuickPicks = ytmQuickPicks,
            refreshing = refreshing,
            refreshKey = refreshKey
        )
    }
}
