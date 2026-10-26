package app.n_zik.android.components.ui.screens.home.quickpicks

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.Countries
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.EventTable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.chartsPageComplete
import it.fast4x.innertube.requests.discoverPage
import it.fast4x.innertube.requests.HomePage
import it.fast4x.innertube.requests.relatedPage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Robolectric unit tests for the offline gating in [HomeQuickPicksState]:
 * while the network is down, load/refresh must run the local-only phase (event
 * table) and skip the charts, discover, quick-picks and home-page requests —
 * while still marking the load as done (loadedData is what the revisit skip
 * consumes) and without flashing the pull-to-refresh indicator.
 *
 * The trending related page is the one intentional exception: the local phase
 * still requests it (seeded by the local trending song, or the app's fallback
 * seed when the history is empty) and handles its offline failure — pinned by
 * `verify(exactly = 1)`. The other network functions are stubbed to fail, so
 * any accidental call would show up as a failure result in addition to
 * breaking the `verify(exactly = 0)` assertions.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HomeQuickPicksStateOfflineTest {

    private lateinit var app: Application
    private lateinit var eventTable: EventTable

    private val offlineError = RuntimeException("network disabled in this test")

    /**
     * MockK's suspend stubbing unwraps a `returns` value that is itself a
     * kotlin.Result one level — wrap the intended failure in an extra
     * Result.success so a (disallowed) call returns the intended failure
     * result (same trick as QuickPicksBrowseHandlerTest.relatedPageResult).
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> offlineFailureStub(value: Result<T>): T = Result.success(value) as T

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)

        // Real isNetworkConnected against the shadow ConnectivityManager: the
        // default shadow state holds an active network with no INTERNET
        // capability (reads as offline — the same premise NetworkGateTest
        // relies on), set explicitly so the test does not depend on defaults.
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        shadowOf(cm).setNetworkCapabilities(
            checkNotNull(cm.activeNetwork) { "the shadow must hold a default active network" },
            caps,
        )

        eventTable = mockk()
        every { eventTable.findSongsMostPlayedBetween(any(), any(), any()) } returns flowOf(emptyList())
        mockkObject(Database)
        every { Database.eventTable } returns eventTable

        // The Innertube request functions are top-level extensions — mock them
        // through their file facades, not through the Innertube object.
        mockkStatic("it.fast4x.innertube.requests.ChartsPageCompleteKt")
        coEvery { Innertube.chartsPageComplete(any()) } returns offlineFailureStub(Result.failure(offlineError))
        mockkStatic("it.fast4x.innertube.requests.DiscoverPageKt")
        coEvery { Innertube.discoverPage() } returns offlineFailureStub(Result.failure(offlineError))
        mockkStatic("it.fast4x.innertube.requests.RelatedPageKt")
        coEvery { Innertube.relatedPage(any(), any()) } returns offlineFailureStub(Result.failure(offlineError))

        mockkObject(YtMusic)
        coEvery { YtMusic.getHomePage(any()) } returns offlineFailureStub(Result.failure(offlineError))
        coEvery { YtMusic.getQuickPicks(any()) } returns offlineFailureStub(Result.failure(offlineError))

        // isYouTubeLoggedIn() reads EncryptedSharedPreferences (AndroidKeyStore —
        // unavailable on the Robolectric JVM); the offline load never needs a
        // logged-in session.
        mockkStatic("app.n_zik.android.components.settings.accounts.AccountsSettingsKt")
        every { isYouTubeLoggedIn() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** A fresh state instance: plain mutable states stand in for the persisted ones. */
    private fun newState() = HomeQuickPicksState(
        trendingList = mutableStateOf(emptyList()),
        trending = mutableStateOf(null),
        trendingInit = null,
        relatedPageResult = mutableStateOf(null),
        discoverPageResult = mutableStateOf(null),
        discoverPageInit = mutableStateOf(null),
        homePageResult = mutableStateOf(null),
        homePageInit = mutableStateOf(null),
        chartsPageResult = mutableStateOf(null),
        chartsPageInit = mutableStateOf(null),
        loadedQuickPicks = mutableStateOf(false),
        loadedData = mutableStateOf(false),
        playEventType = PlayEventsType.MostPlayed,
        selectedCountryCode = Countries.FR,
        parentalControlEnabled = false,
        localCount = 10,
        recommendations = mutableStateOf(emptyList()),
        ytmQuickPicks = mutableStateOf(emptyList()),
        refreshing = mutableStateOf(false),
        refreshKey = mutableIntStateOf(0),
    )

    /**
     * The companion-level sharedLoadJob outlives each state instance
     * (single-flight load), so a test that starts a load/refresh settles it
     * before finishing — otherwise the next test's load would only mark a
     * deferred refresh instead of running.
     */
    private fun settleSharedLoad() {
        Thread.sleep(SETTLE_MS)
    }

    private fun awaitLocalPhase(state: HomeQuickPicksState, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (state.loadedQuickPicks.value) return
            Thread.sleep(25)
        }
        fail("local phase (loadedQuickPicks) not reached within ${timeoutMs}ms")
    }

    private fun verifyNetworkPhasesSkipped() {
        // MockK's verify lambda is not suspend — runBlocking lets the suspend
        // calls be recorded inside it (AGENTS.md runBlocking exception).
        verify(exactly = 0) {
            runBlocking {
                Innertube.chartsPageComplete(any())
                Innertube.discoverPage()
            }
        }
        verify(exactly = 0) {
            runBlocking {
                YtMusic.getQuickPicks(any())
                YtMusic.getHomePage(any())
            }
        }
    }

    @Test
    fun `offline loadData skips the network phases and marks the load done without home data`() {
        settleSharedLoad()
        val state = newState()
        // runBlocking is forced by the suspend test entry point (AGENTS.md
        // runBlocking exception).
        val loaded = runBlocking { state.loadData() }

        assertTrue(loaded)
        awaitLocalPhase(state)
        assertTrue("an offline load must still count as done", state.loadedData.value)
        assertNull("the offline load must not fabricate home page data", state.homePageInit.value)
        verifyNetworkPhasesSkipped()
        // The local phase did run: the event table flow was queried, and the
        // related page was requested with the fallback seed (the event table
        // is empty, so there is no local trending song to seed from).
        verify(exactly = 1) { eventTable.findSongsMostPlayedBetween(any(), any(), any()) }
        verify(exactly = 1) {
            runBlocking { Innertube.relatedPage(any(), any()) }
        }
    }

    @Test
    fun `offline load never flashes the refreshing indicator`() {
        settleSharedLoad()
        val state = newState()
        state.load()

        var everRefreshing = false
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (state.refreshing.value) everRefreshing = true
            if (state.loadedData.value && !state.refreshing.value) break
            Thread.sleep(20)
        }
        settleSharedLoad()

        assertFalse(
            "an offline (local-only) load must not flash the pull-to-refresh indicator",
            everRefreshing,
        )
        assertTrue(state.loadedData.value)
        assertTrue(state.loadedQuickPicks.value)
        verifyNetworkPhasesSkipped()
    }

    @Test
    fun `offline refresh wipes the persisted state and reruns the local-only load`() {
        settleSharedLoad()
        val state = newState()
        // Pre-seed as if a previous online load had populated the persisted state.
        state.homePageInit.value = HomePage(
            sections = listOf(HomePage.Section("Charts", null, null, null, emptyList())),
            chips = null,
        )
        state.trendingList.value = listOf(
            Song(
                id = "seed-1",
                title = "Seeded",
                durationText = null,
                thumbnailUrl = "https://i.ytimg.com/vi/s1/hqdefault.jpg",
                likedAt = null,
                totalPlayTimeMs = 1000,
            ),
        )
        state.loadedData.value = true
        state.loadedQuickPicks.value = true

        state.refresh()

        assertEquals(1, state.refreshKey.value)
        assertNull(state.homePageInit.value)
        assertFalse(state.loadedData.value)
        assertFalse(state.loadedQuickPicks.value)
        assertTrue(state.trendingList.value.isEmpty())

        awaitLocalPhase(state)
        assertTrue("the offline reload must mark the load done", state.loadedData.value)
        verifyNetworkPhasesSkipped()
        settleSharedLoad()
    }

    companion object {
        // The load job carries a built-in 500ms settle delay; 800ms covers it
        // plus the local-phase emission.
        private const val SETTLE_MS = 800L
    }
}
