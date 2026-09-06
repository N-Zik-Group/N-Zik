package app.it.fast4x.rimusic.repository

import app.n_zik.android.core.database.*

import androidx.core.content.edit
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.enums.Countries
import app.it.fast4x.rimusic.enums.LocalRecommandationsNumber
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.utils.*
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.time.Duration.Companion.days

object QuickPicksRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _trendingList = MutableStateFlow<List<Song>>(emptyList())
    val trendingList: StateFlow<List<Song>> = _trendingList.asStateFlow()

    private val _trending = MutableStateFlow<Song?>(null)
    val trending: StateFlow<Song?> = _trending.asStateFlow()

    private val _relatedPage = MutableStateFlow<Innertube.RelatedPage?>(null)
    val relatedPage: StateFlow<Innertube.RelatedPage?> = _relatedPage.asStateFlow()

    private val _discoverPage = MutableStateFlow<Innertube.DiscoverPage?>(null)
    val discoverPage: StateFlow<Innertube.DiscoverPage?> = _discoverPage.asStateFlow()

    private val _homePage = MutableStateFlow<HomePage?>(null)
    val homePage: StateFlow<HomePage?> = _homePage.asStateFlow()

    private val _chartsPage = MutableStateFlow<Innertube.ChartsPage?>(null)
    val chartsPage: StateFlow<Innertube.ChartsPage?> = _chartsPage.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var lastLoadTime = 0L
    private const val CACHE_EXPIRATION = 1000 * 60 * 30 // 30 minutes

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    init {
        loadFromPreferences()
    }

    private fun loadFromPreferences() {
        val prefs = appContext().preferences
        
        _trending.value = prefs.getString(quickPicsTrendingSongKey, null)?.let {
            try { json.decodeFromString<Song>(it) } catch (e: Exception) { null }
        }
        _relatedPage.value = prefs.getString(quickPicsRelatedPageKey, null)?.let {
            try { json.decodeFromString<Innertube.RelatedPage>(it) } catch (e: Exception) { null }
        }
        _discoverPage.value = prefs.getString(quickPicsDiscoverPageKey, null)?.let {
            try { json.decodeFromString<Innertube.DiscoverPage>(it) } catch (e: Exception) { null }
        }
        _homePage.value = prefs.getString(quickPicsHomePageKey, null)?.let {
            try { json.decodeFromString<HomePage>(it) } catch (e: Exception) { null }
        }
    }

    private fun saveToPreferences() {
        val prefs = appContext().preferences
        prefs.edit {
            putString(quickPicsTrendingSongKey, _trending.value?.let { json.encodeToString(it) })
            putString(quickPicsRelatedPageKey, _relatedPage.value?.let { json.encodeToString(it) })
            putString(quickPicsDiscoverPageKey, _discoverPage.value?.let { json.encodeToString(it) })
            putString(quickPicsHomePageKey, _homePage.value?.let { json.encodeToString(it) })
            apply()
        }
    }

    fun refreshIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastLoadTime > CACHE_EXPIRATION || _trendingList.value.isEmpty()) {
            loadData()
        }
    }

    fun loadData() {
        scope.launch {
            if (_loading.value) return@launch
            _loading.value = true
            try {
                val prefs = appContext().preferences
                val playEventType = prefs.getEnum(playEventsTypeKey, PlayEventsType.MostPlayed)
                val selectedCountryCode = prefs.getEnum(selectedCountryCodeKey, Countries.ZZ)
                
                val localRecommandationsNumber = try {
                    prefs.getEnum("LocalRecommandationsNumber", LocalRecommandationsNumber.SixQ)
                } catch (e: Exception) {
                    LocalRecommandationsNumber.SixQ
                }
                val localCount = localRecommandationsNumber.value
                val from = 18250.days.inWholeMilliseconds

                val showCharts = prefs.getBoolean(showChartsKey, true)
                val showNewAlbums = prefs.getBoolean(showNewAlbumsKey, true)
                val showNewAlbumsArtists = prefs.getBoolean(showNewAlbumsArtistsKey, true)
                val showMoodsAndGenres = prefs.getBoolean(showMoodsAndGenresKey, true)

                if (showCharts) {
                    _chartsPage.value = Innertube.chartsPageComplete(countryCode = selectedCountryCode.name)?.getOrNull()
                }

                when (playEventType) {
                    PlayEventsType.MostPlayed -> {
                        Database.eventTable.findSongsMostPlayedBetween(from = from, limit = localCount)
                            .first().let { songs ->
                                val list = songs.distinctBy { it.id }.take(localCount)
                                _trendingList.value = list
                                _trending.value = list.firstOrNull()
                                refreshRelatedIfNeeded()
                            }
                    }
                    PlayEventsType.LastPlayed -> {
                        Database.eventTable.findSongsLastPlayed(limit = localCount)
                            .first().let { songs ->
                                val list = songs.distinctBy { it.id }.take(localCount)
                                _trendingList.value = list
                                _trending.value = list.firstOrNull()
                                refreshRelatedIfNeeded()
                            }
                    }
                    PlayEventsType.CasualPlayed -> {
                        Database.eventTable.findSongsMostPlayedBetween(from = 0, limit = 100)
                            .first().let { songs ->
                                val originalList = songs.distinctBy { it.id }
                                val shuffled = originalList.shuffled().take(localCount)
                                _trendingList.value = shuffled
                                _trending.value = shuffled.firstOrNull()
                                refreshRelatedIfNeeded()
                            }
                    }
                }

                if (showNewAlbums || showNewAlbumsArtists || showMoodsAndGenres) {
                    _discoverPage.value = Innertube.discoverPage()?.getOrNull()
                }

                if (isYouTubeLoggedIn()) {
                    _homePage.value = YtMusic.getHomePage(setLogin = true)?.getOrNull()
                }

                lastLoadTime = System.currentTimeMillis()
                saveToPreferences()
                Timber.tag("QuickPicksRepository").d("Success loadData")
            } catch (e: Exception) {
                Timber.tag("QuickPicksRepository").e(e, "Failed to load QuickPicks data")
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun refreshRelatedIfNeeded() {
        val currentTrending = _trending.value
        if (currentTrending != null && (_relatedPage.value == null || _relatedPage.value?.songs?.firstOrNull()?.key != currentTrending.id)) {
            _relatedPage.value = Innertube.relatedPage(videoId = currentTrending.id, setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)?.getOrNull()
        }
    }
}
