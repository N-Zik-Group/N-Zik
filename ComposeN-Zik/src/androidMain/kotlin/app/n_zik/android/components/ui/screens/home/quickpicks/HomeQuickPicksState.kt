package app.n_zik.android.components.ui.screens.home.quickpicks

import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.themePreviewQuickPicksPage
import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.compose.persist.persist
import app.n_zik.android.compose.persist.persistList
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.enums.Countries
import app.n_zik.android.enums.PlayEventsType
import app.n_zik.android.models.Song
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.encryptedPreferencesUpdateTrigger
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.useLoginForBrowseKey
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.HomePage
import app.n_zik.innertube.requests.chartsPageComplete
import app.n_zik.innertube.requests.discoverPage
import app.n_zik.innertube.requests.relatedPage
import app.n_zik.android.playback.services.isLocal
import kotlinx.coroutines.CancellationException
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import kotlin.time.Duration.Companion.days
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn

@UnstableApi
internal class HomeQuickPicksState(
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
    var refreshKey: MutableState<Int>,
    private val account: QuickPicksAccountIdentity = currentQuickPicksAccountIdentity(),
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

    private fun loadIdentity() = LoadIdentity(playEventType, selectedCountryCode, account)

    internal fun isCurrentAccount(): Boolean = account == currentQuickPicksAccountIdentity()

    @SuppressLint("SuspiciousIndentation")
    suspend fun loadData(force: Boolean = false): Boolean {
        if (!isCurrentAccount()) return false
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
                        YtMusic.getQuickPicks(setLogin = true).getOrThrow()
                    } else emptyList()
                }

                runCatching { chartsDeferred.await() }
                    .onSuccess { result ->
                        chartsPageResult.value = result ?: Result.success(null)
                        if (result?.isSuccess == true) chartsPageInit.value = result.getOrNull()
                        result?.exceptionOrNull()?.let { error ->
                            if (error is CancellationException) throw error
                        }
                        Timber.tag("HomeQuickPicksState").d("Charts loaded")
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        chartsPageResult.value = Result.failure(e)
                        Timber.tag("HomeQuickPicksState").w(e, "Charts load failed")
                    }

                runCatching { discoverDeferred.await() }
                    .onSuccess { result ->
                        discoverPageResult.value = result ?: Result.success(null)
                        if (result?.isSuccess == true) discoverPageInit.value = result.getOrNull()
                        result?.exceptionOrNull()?.let { error ->
                            if (error is CancellationException) throw error
                        }
                        Timber.tag("HomeQuickPicksState").d("YouTube Discovery data loaded")
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        discoverPageResult.value = Result.failure(e)
                        Timber.tag("HomeQuickPicksState").w(e, "Discover load failed")
                    }

                runCatching { quickPicksDeferred.await() }
                    .onSuccess { items ->
                        if (isCurrentAccount()) {
                            ytmQuickPicks.value = items.map { it.asSong }
                            Timber.tag("HomeQuickPicksState").d("Lightweight Quick Picks loaded (${items.size} items)")
                        }
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Timber.tag("HomeQuickPicksState").w(e, "Quick picks load failed")
                    }
            }

            // Retry incomplete home data, preserving its existing merge order.
            if (online && (force || !shouldSkipQuickPicksLoad(loadedData.value, homePageInit.value != null))) {
                val cachedHome = homePageInit.value
                val replacingCachedHome = force && cachedHome != null
                var cumulativeSections = if (replacingCachedHome) emptyList() else homePageInit.value?.sections.orEmpty()
                var cumulativeChips = if (replacingCachedHome) emptyList() else homePageInit.value?.chips.orEmpty()
                var refreshedHome: HomePage? = null
                var latestResult: Result<HomePage>? = null
                loadQuickPicksHomeAttempts {
                    if (!isCurrentAccount()) throw CancellationException("YouTube account changed")
                    val result = YtMusic.getHomePage(setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)
                    if (!isCurrentAccount()) throw CancellationException("YouTube account changed")
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    latestResult = result
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
                        val merged = HomePage(sections = cumulativeSections, chips = cumulativeChips)
                        refreshedHome = merged
                        if (!replacingCachedHome) {
                            homePageResult.value = Result.success(merged)
                            homePageInit.value = merged
                        }
                    }
                    if (result.getOrNull() != null) countUsableQuickPicksSections(cumulativeSections) else 0
                }

                if (replacingCachedHome && cachedHome != null) {
                    refreshedHome?.takeIf { shouldReplaceCachedQuickPicksHome(cachedHome, it) }?.let { page ->
                        homePageResult.value = Result.success(page)
                        homePageInit.value = page
                    }
                }
                if (homePageInit.value == null) homePageResult.value = latestResult
                Timber.tag("HomeQuickPicksState").d("YouTube Music sections loaded: ${homePageInit.value?.sections?.size ?: 0}")
            }

        }.onFailure { e ->
            if (e is CancellationException) {
                // Keep the cache flags consistent after cancellation.
                Timber.tag("HomeQuickPicksState").d("LoadData cancelled, flags reset")
                loadedData.value = false
                if (isCurrentAccount() && trendingList.value.isEmpty()) loadedQuickPicks.value = false
            } else {
                Timber.tag("HomeQuickPicksState").e(e, "Failed to load Quick Picks")
                if (homePageInit.value == null) homePageResult.value = Result.failure(e)
                if (chartsPageResult.value == null) chartsPageResult.value = Result.failure(e)
                if (discoverPageResult.value == null) discoverPageResult.value = Result.failure(e)
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
            var relatedRequested = false
            val songsFlow = when (playEventType) {
                PlayEventsType.MostPlayed -> Database.eventTable
                    .findSongsMostPlayedBetween(from = from, limit = localCount)
                PlayEventsType.LastPlayed -> Database.eventTable.findSongsLastPlayed(limit = localCount)
                PlayEventsType.CasualPlayed -> Database.eventTable
                    .findSongsMostPlayedBetween(from = 0, limit = 100)
            }
            songsFlow.distinctUntilChanged().catch { error ->
                Timber.tag("HomeQuickPicksState").w(error, "Could not read local history")
                observations.publish(publication) {
                    loadedQuickPicks.value = true
                    if (relatedPageResult.value == null) relatedPageResult.value = Result.failure(error)
                }
            }.collect { songs ->
                val filtered = songs.distinctBy { it.id }
                    .filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                val selected = if (playEventType == PlayEventsType.CasualPlayed) {
                    filtered.shuffled().take(localCount)
                } else filtered.take(localCount)
                observations.publish(publication) {
                    trendingList.value = selected
                    trending.value = selected.firstOrNull()
                    loadedQuickPicks.value = true
                }
                if (isCurrentAccount() && !relatedRequested && (restart || relatedPageResult.value == null)) {
                    relatedRequested = true
                    val related = try {
                        Innertube.relatedPage(
                            videoId = selected.firstOrNull { !it.isLocal }?.id ?: "4NRXx6U8ABQ",
                            setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.tag("HomeQuickPicksState").w(error, "Could not load related music")
                        Result.failure(error)
                    }
                    observations.publish(publication) {
                        if (isCurrentAccount() && (related?.isSuccess == true || relatedPageResult.value?.getOrNull() == null)) {
                            relatedPageResult.value = related ?: Result.success(null)
                        }
                    }
                }
            }
        }
    }

    fun load(invalidate: Boolean = false) {
        observeLocalData()
        loads.request(
            identity = loadIdentity(),
            invalidate = invalidate,
            onStart = { force -> prepareLoad(force, clearCache = invalidate) },
        ) { force -> runLoad(force) }
    }

    private fun prepareLoad(force: Boolean, clearCache: Boolean = false) {
        if (force) refreshKey.value++
        if (force && clearCache) {
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
        } else if (force) {
            if (homePageInit.value == null) homePageResult.value = null
            if (discoverPageInit.value == null) discoverPageResult.value = null
            if (chartsPageInit.value == null) chartsPageResult.value = null
            if (relatedPageResult.value?.getOrNull() == null) relatedPageResult.value = null
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

internal fun shouldReplaceCachedQuickPicksHome(cached: HomePage, refreshed: HomePage): Boolean {
    val refreshedCount = countUsableQuickPicksSections(refreshed.sections)
    return refreshedCount > QUICK_PICKS_HOME_SECTION_TARGET ||
        refreshedCount >= countUsableQuickPicksSections(cached.sections)
}

internal fun currentQuickPicksAccountIdentity(): QuickPicksAccountIdentity {
    val session = Innertube.youtubeSessionSnapshot()
    return quickPicksAccountIdentity(
        session.cookie.orEmpty(), Innertube.useLoginForBrowse, session.dataSyncId, session.authUser,
    )
}

internal class QuickPicksAccountCache {
    private var owner: QuickPicksAccountIdentity? = null
    private var data: QuickPicksAccountData = QuickPicksAccountData()

    fun forAccount(account: QuickPicksAccountIdentity): QuickPicksAccountData {
        if (owner != account) {
            owner = account
            data = QuickPicksAccountData()
        }
        return data
    }
}

internal class QuickPicksAccountData {
    val relatedPageResult = mutableStateOf<Result<Innertube.RelatedPage?>?>(null)
    val homePageResult = mutableStateOf<Result<HomePage?>?>(null)
    val homePage = mutableStateOf<HomePage?>(null)
    val quickPicks = mutableStateOf<List<Song>>(emptyList())
    val recommendations = mutableStateOf<List<Song>>(emptyList())
    val loaded = mutableStateOf(false)
}

@UnstableApi
@Composable
internal fun rememberHomeQuickPicksState(
    playEventType: PlayEventsType,
    selectedCountryCode: Countries,
    parentalControlEnabled: Boolean,
    localCount: Int
): HomeQuickPicksState {
    val preview = LocalThemePreview.current
    val trendingList = if (preview == null) persistList<Song>("home/quickpicks/trending_list")
        else remember { mutableStateOf(ThemePreviewDemo.songs.take(8)) }
    val trending = persist<Song?>("home/quickpicks/trending")
    val trendingInit = persist<Song?>(tag = "home/quickpicks/trending_init").value

    val useAuthenticatedBrowse = rememberPreference(useLoginForBrowseKey, true).value
    val account = remember(useAuthenticatedBrowse, encryptedPreferencesUpdateTrigger) {
        currentQuickPicksAccountIdentity()
    }
    val initialAccountCache = remember { QuickPicksAccountCache() }
    val accountCache = persist("home/quickpicks/account-cache", initialAccountCache).value
    val accountData = remember(accountCache, account) { accountCache.forAccount(account) }
    val homePageInit = if (preview == null) accountData.homePage
        else remember { mutableStateOf<HomePage?>(themePreviewQuickPicksPage()) }

    val discoverPageResult = persist<Result<Innertube.DiscoverPage?>?>("home/quickpicks/discoveryAlbumsResult")
    val discoverPageInit = persist<Innertube.DiscoverPage?>("home/quickpicks/discoveryAlbumsInit")

    val chartsPageResult = persist<Result<Innertube.ChartsPage?>?>("home/quickpicks/chartsPageResult")
    val chartsPageInit = persist<Innertube.ChartsPage?>("home/quickpicks/chartsPageInit")

    val loadedQuickPicks = persist("home/quickpicks/loadedQuickPicks", preview != null)
    val loadedData = if (preview == null) accountData.loaded else remember { mutableStateOf(true) }

    val recommendations = if (preview == null) accountData.recommendations
        else remember { mutableStateOf(ThemePreviewDemo.songs.take(8)) }
    val refreshing = remember { mutableStateOf(false) }
    val refreshKey = remember { mutableIntStateOf(0) }

    val state = remember(accountData, playEventType, selectedCountryCode, parentalControlEnabled, localCount) {
        HomeQuickPicksState(
            trendingList = trendingList,
            trending = trending,
            trendingInit = trendingInit,
            relatedPageResult = accountData.relatedPageResult,
            discoverPageResult = discoverPageResult,
            discoverPageInit = discoverPageInit,
            homePageResult = accountData.homePageResult,
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
            ytmQuickPicks = accountData.quickPicks,
            refreshing = refreshing,
            refreshKey = refreshKey,
            account = account,
        )
    }
    return state
}
