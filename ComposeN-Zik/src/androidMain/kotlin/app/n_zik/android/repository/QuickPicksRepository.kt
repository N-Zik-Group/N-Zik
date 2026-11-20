package app.n_zik.android.repository

import app.n_zik.android.playback.services.isLocal

import androidx.core.content.edit
import app.n_zik.android.components.ui.screens.home.quickpicks.QuickPicksAccountIdentity
import app.n_zik.android.components.ui.screens.home.quickpicks.currentQuickPicksAccountIdentity
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.n_zik.android.enums.PlayEventsType
import app.n_zik.android.enums.LocalRecommandationsNumber
import app.n_zik.android.models.Song
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.playEventsTypeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.quickPicsRelatedPageKey
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.relatedPage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.time.Duration.Companion.days

object QuickPicksRepository {
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    
    private val _trendingList = MutableStateFlow<List<Song>>(emptyList())
    val trendingList: StateFlow<List<Song>> = _trendingList.asStateFlow()

    private val _relatedPage = MutableStateFlow<Innertube.RelatedPage?>(null)
    val relatedPage: StateFlow<Innertube.RelatedPage?> = _relatedPage.asStateFlow()

    private val _loading = MutableStateFlow(false)

    private var lastLoadTime = 0L
    private var relatedSeed: String? = null
    private var loadedAccount: QuickPicksAccountIdentity? = null
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
        
        _relatedPage.value = prefs.getString(quickPicsRelatedPageKey, null)?.let {
            try { json.decodeFromString<Innertube.RelatedPage>(it) } catch (e: Exception) { null }
        }
    }

    fun refreshIfNeeded(force: Boolean = false) {
        scope.launch {
            if (!_loading.compareAndSet(expect = false, update = true)) return@launch
            try {
                val prefs = appContext().preferences
                val account = currentQuickPicksAccountIdentity()
                val sameAccount = loadedAccount == account
                if (!force && sameAccount && System.currentTimeMillis() - lastLoadTime <= CACHE_EXPIRATION &&
                    _trendingList.value.isNotEmpty()
                ) return@launch
                val playEventType = prefs.getEnum(playEventsTypeKey, PlayEventsType.MostPlayed)
                
                val localRecommandationsNumber = try {
                    prefs.getEnum("LocalRecommandationsNumber", LocalRecommandationsNumber.SixQ)
                } catch (e: Exception) {
                    LocalRecommandationsNumber.SixQ
                }
                val localCount = localRecommandationsNumber.value
                val from = 18250.days.inWholeMilliseconds

                val songs = when (playEventType) {
                    PlayEventsType.MostPlayed ->
                        Database.eventTable.findSongsMostPlayedBetween(from = from, limit = localCount).first()
                    PlayEventsType.LastPlayed ->
                        Database.eventTable.findSongsLastPlayed(limit = localCount).first()
                    PlayEventsType.CasualPlayed ->
                        Database.eventTable.findSongsMostPlayedBetween(from = 0, limit = 100).first()
                }.distinctBy { it.id }
                val trending = (if (playEventType == PlayEventsType.CasualPlayed) songs.shuffled() else songs)
                    .take(localCount)
                val seed = trending.firstOrNull { !it.isLocal }?.id
                val related = when {
                    seed != null && (!sameAccount || seed != relatedSeed) ->
                        Innertube.relatedPage(seed, setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)
                            ?.getOrNull()
                    sameAccount || loadedAccount == null -> _relatedPage.value
                    else -> null
                }
                if (prefs !== appContext().preferences || account != currentQuickPicksAccountIdentity()) return@launch
                _trendingList.value = trending
                _relatedPage.value = related
                relatedSeed = seed.takeIf { related != null }
                loadedAccount = account
                lastLoadTime = System.currentTimeMillis()
                prefs.edit { putString(quickPicsRelatedPageKey, related?.let { json.encodeToString(it) }) }
                Timber.tag("QuickPicksRepository").d("Success loadData")
            } catch (e: Exception) {
                Timber.tag("QuickPicksRepository").e(e, "Failed to load QuickPicks data")
            } finally {
                _loading.value = false
            }
        }
    }
}
