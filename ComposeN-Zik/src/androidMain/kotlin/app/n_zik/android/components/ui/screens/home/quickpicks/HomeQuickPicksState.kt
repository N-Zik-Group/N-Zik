package app.n_zik.android.components.ui.screens.home.quickpicks

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.compose.persist.persist
import app.it.fast4x.compose.persist.persistList
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.enums.*
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.core.database.Database
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.days
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn

@UnstableApi
class HomeQuickPicksState(
    val scope: CoroutineScope,
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
    private val from = 18250.days.inWholeMilliseconds
    private var dbJob: Job? = null

    @SuppressLint("SuspiciousIndentation")
    suspend fun loadData() {
        if (loadedData.value && homePageInit.value != null) return

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
            dbJob = scope.launch(Dispatchers.IO) {
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
                                        relatedPageResult.value = Innertube.relatedPage(videoId = trending.value?.id ?: "4NRXx6U8ABQ")
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
                                        relatedPageResult.value = Innertube.relatedPage(videoId = trending.value?.id ?: "4NRXx6U8ABQ")
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
                                        relatedPageResult.value = Innertube.relatedPage(videoId = trending.value?.id ?: "4NRXx6U8ABQ")
                                    }
                                    loadedQuickPicks.value = true
                                }
                    }
                }
            }

            // Phase 3: Home page sections (sequential but with early exit)
            if (!loadedData.value) {
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

        }.onFailure {
            Timber.tag("HomeQuickPicksState").e("Failed loadData ${it.stackTraceToString()}")
            loadedData.value = false
        }.onSuccess {
            loadedData.value = true
        }
    }

    fun refresh() {
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
        scope.launch(Dispatchers.IO) {
            refreshing.value = true
            loadData()
            delay(500)
            refreshing.value = false
        }
    }
}

@UnstableApi
@Composable
fun rememberHomeQuickPicksState(
    playEventType: PlayEventsType,
    selectedCountryCode: Countries,
    parentalControlEnabled: Boolean,
    localCount: Int
): HomeQuickPicksState {
    val scope = rememberCoroutineScope()
    
    val trendingList = persistList<Song>("home/quickpicks/trending_list")
    val trending = persist<Song?>("home/quickpicks/trending")
    val trendingInit = persist<Song?>(tag = "home/quickpicks/trending_init").value

    val relatedPageResult = persist<Result<Innertube.RelatedPage?>?>(tag = "home/quickpicks/relatedPageResult")
    
    val discoverPageResult = persist<Result<Innertube.DiscoverPage?>?>("home/quickpicks/discoveryAlbumsResult")
    val discoverPageInit = persist<Innertube.DiscoverPage?>("home/quickpicks/discoveryAlbumsInit")

    val homePageResult = persist<Result<HomePage?>?>("home/quickpicks/homePageResult")
    val homePageInit = rememberPreference("home/quickpicks/homePageInit", null as HomePage?)

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
            scope = scope,
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
