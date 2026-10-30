package app.n_zik.android.components.ui.screens.home.quickpicks

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.compose.persist.persist
import app.it.fast4x.compose.persist.persistList
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.enums.Countries
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.HomePage
import it.fast4x.innertube.requests.chartsPageComplete
import it.fast4x.innertube.requests.discoverPage
import it.fast4x.innertube.requests.relatedPage
import kotlinx.coroutines.CancellationException
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import kotlin.time.Duration.Companion.days
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn

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
        // Navigation must not cancel a shared network load.
        private val loadScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)

        private val loads = QuickPicksLoadCoordinator(loadScope)
        private val observations = QuickPicksObservationOwner(loadScope)
    }

    private val from = 18250.days.inWholeMilliseconds
    private val observationToken = Any()
    private var observationActivated = false

    private data class LoadIdentity(
        val playEventType: PlayEventsType,
        val country: Countries,
        val account: QuickPicksAccountIdentity,
    )

    private fun loadIdentity() = LoadIdentity(
        playEventType, selectedCountryCode,
        quickPicksAccountIdentity(
            appContext().encryptedPreferences.getString(ytCookieKey, "").orEmpty(),
            Innertube.useLoginForBrowse,
        ),
    )

    @SuppressLint("SuspiciousIndentation")
    suspend fun loadData(force: Boolean = false): Boolean {
        observeLocalData()
        if (!force && shouldSkipQuickPicksLoad(loadedData.value, homePageInit.value != null)) {
            // Repair an early-ready cache whose local loaded flag was lost.
            if (trendingList.value.isNotEmpty() && !loadedQuickPicks.value) {
                loadedQuickPicks.value = true
            }
            return false
        }

        // Offline loads retain local observation and skip network phases.
        val online = NetworkQualityHelper.isNetworkConnected(appContext())

        Timber.tag("HomeQuickPicksState").d("Starting loadData... (online=$online)")

        runCatching {
            // Phase 1: Parallel network calls for charts, discover, quick picks
            // (skipped entirely when offline - see the gate above)
            if (online) supervisorScope {
                val chartsDeferred = async(NzikDispatchers.DATA) {
                    Innertube.chartsPageComplete(countryCode = selectedCountryCode.name)
                }
                val discoverDeferred = async(NzikDispatchers.DATA) {
                    Innertube.discoverPage()
                }
                val quickPicksDeferred = async(NzikDispatchers.DATA) {
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

            // Retry incomplete home data, preserving its existing merge order.
            if (online && (force || !shouldSkipQuickPicksLoad(loadedData.value, homePageInit.value != null))) {
                var cumulativeSections = homePageInit.value?.sections.orEmpty()
                var cumulativeChips = homePageInit.value?.chips.orEmpty()
                loadQuickPicksHomeAttempts {
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
                    if (result.getOrNull() != null) countUsableQuickPicksSections(cumulativeSections) else 0
                }

                Timber.tag("HomeQuickPicksState").d("YouTube Music sections loaded: ${homePageInit.value?.sections?.size ?: 0}")
            }

        }.onFailure { e ->
            if (e is CancellationException) {
                // Keep the cache flags consistent after cancellation.
                Timber.tag("HomeQuickPicksState").d("LoadData cancelled, flags reset")
                loadedData.value = false
                if (trendingList.value.isEmpty()) loadedQuickPicks.value = false
            } else {
                Timber.tag("HomeQuickPicksState").e("Failed loadData ${e.stackTraceToString()}")
                loadedData.value = false
            }
        }.onSuccess {
            // Missing online data must remain retryable; offline local loads count as done.
            loadedData.value = homePageInit.value != null || !online
        }
        return true
    }

    @Synchronized
    internal fun observeLocalData(restart: Boolean = false) {
        if (!observationActivated) {
            observations.claim(observationToken)
            observationActivated = true
        }
        observations.start(observationToken, restart = restart) { publication ->
            // Capture this state's parameters, never a later composition's settings.
            val songsFlow = when (playEventType) {
                PlayEventsType.MostPlayed -> Database.eventTable
                    .findSongsMostPlayedBetween(from = from, limit = localCount)
                PlayEventsType.LastPlayed -> Database.eventTable.findSongsLastPlayed(limit = localCount)
                PlayEventsType.CasualPlayed -> Database.eventTable
                    .findSongsMostPlayedBetween(from = 0, limit = 100)
            }
            songsFlow.distinctUntilChanged().collect { songs ->
                val filtered = songs.distinctBy { it.id }
                    .filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                val selected = if (playEventType == PlayEventsType.CasualPlayed) {
                    filtered.shuffled().take(localCount)
                } else filtered.take(localCount)
                var fetchRelated = false
                observations.publish(publication) {
                    trendingList.value = selected
                    trending.value = selected.firstOrNull()
                    loadedQuickPicks.value = true
                    fetchRelated = relatedPageResult.value == null
                }
                if (fetchRelated) {
                    val related = Innertube.relatedPage(
                        videoId = selected.firstOrNull()?.id ?: "4NRXx6U8ABQ",
                        setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse,
                    )
                    observations.publish(publication) { relatedPageResult.value = related }
                }
            }
        }
    }

    fun load(invalidate: Boolean = false) {
        observeLocalData()
        loads.request(
            identity = loadIdentity(),
            invalidate = invalidate,
            onStart = { force -> prepareLoad(force) },
        ) { force -> runLoad(force) }
    }

    private fun prepareLoad(force: Boolean) {
        if (force) {
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
        }
        observeLocalData(restart = force)
    }

    private suspend fun runLoad(force: Boolean) {
        val online = NetworkQualityHelper.isNetworkConnected(appContext())
        if (online && (force || !shouldSkipQuickPicksLoad(loadedData.value, homePageInit.value != null))) {
            refreshing.value = true
        }
        try {
            loadData(force = force)
            delay(500)
        } finally {
            refreshing.value = false
        }
    }

    fun refresh() {
        observeLocalData()
        loads.request(
            identity = loadIdentity(),
            refresh = true,
            onStart = { force -> prepareLoad(force) },
        ) { force -> runLoad(force) }
    }
}

/** A loaded flag is trustworthy only while its home data is present. */
fun shouldSkipQuickPicksLoad(loadedData: Boolean, homePagePresent: Boolean): Boolean =
    loadedData && homePagePresent

internal fun countUsableQuickPicksSections(sections: List<HomePage.Section>): Int =
    sections.count { section -> section.items.any { !it?.key.isNullOrBlank() } }

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
    // Background loads and later page instances must share the same cache.
    val homePageInit = persist<HomePage>(tag = "home/quickpicks/homePageInit")

    val ytmQuickPicks = persistList<Song>("home/quickpicks/ytmQuickPicks")

    val chartsPageResult = persist<Result<Innertube.ChartsPage?>?>("home/quickpicks/chartsPageResult")
    val chartsPageInit = persist<Innertube.ChartsPage?>("home/quickpicks/chartsPageInit")

    val loadedQuickPicks = persist("home/quickpicks/loadedQuickPicks", false)
    val loadedData = persist("home/quickpicks/loadedData", false)
    
    val recommendations = persistList<Song>("home/quickpicks/recommendations_list")
    val refreshing = remember { mutableStateOf(false) }
    val refreshKey = remember { mutableIntStateOf(0) }

    val state = remember(playEventType, selectedCountryCode, parentalControlEnabled, localCount) {
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
    return state
}
