package app.n_zik.android.components

import android.app.Application
import android.os.Looper
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.Countries
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreen
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.musicbrainz.insights.AlbumInsightsScreen
import app.n_zik.android.components.musicbrainz.insights.AlbumInsightsViewModel
import app.n_zik.android.components.musicbrainz.insights.ArtistInsightsScreen
import app.n_zik.android.components.musicbrainz.insights.ArtistInsightsViewModel
import app.n_zik.android.components.ui.screens.home.quickpicks.HomeQuickPicksContent
import app.n_zik.android.components.ui.screens.home.quickpicks.HomeQuickPicksState
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseEndpoint
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.requests.HomePage
import it.fast4x.innertube.requests.songInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Compose tests for the offline load state (spec-offline-load-state) — the
 * shared [OfflineContent] "Unable to load — no connection" message and the
 * [OfflineAwareLoader] seam the screens branch on, covering the I/O matrix:
 * OFFLINE_LOCAL (local Quick Picks content without shimmer),
 * OFFLINE_ONLINE_CONTENT (YTM sections replaced by the centered message),
 * ONLINE_OK (normal content), ONLINE_FETCH_FAILS (loader kept while loading)
 * and RECONNECT (offline→online triggers exactly one refresh, message gone).
 * Wave 2: OFFLINE_SONG_INFO (Video/Song info sheet banner when `info == null`),
 * MB_ROW_MISSING (missing DB row → non-loading state + centered "Unable to
 * load" message, ViewModels and screens, artists and albums) and MB_OFFLINE
 * (DB row present → local content renders, existing behavior kept).
 *
 * The offline branch is tested through the seams the spec requires:
 * [HomeQuickPicksContent] (parameterized by `isOnline` + `refresh`) and
 * [OfflineAwareLoader] (the ArtistScreen/legacy-screen branch); the real
 * network read (`isNetworkAvailableComposable`) stays at screen level.
 *
 * JUnit 4 + Robolectric with the plain [Application] (MainApplication.onCreate
 * needs AndroidKeyStore, which is unavailable on the Robolectric JVM); the
 * Room DB is the shared [DatabaseInitializer.Instance], empty in these tests.
 */
@UnstableApi
@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalTextApi::class,
)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class OfflineContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private lateinit var offlineMessage: String
    private lateinit var tipsTitle: String
    private lateinit var mixedForYouTitle: String
    private lateinit var unableToLoad: String

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        offlineMessage = app.getString(R.string.unable_to_load_no_connection)
        tipsTitle = app.getString(R.string.tips)
        mixedForYouTitle = app.getString(R.string.mixed_for_you)
        unableToLoad = app.getString(R.string.unable_to_load)
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        // runBlocking is forced by the synchronous test setup API
        // (AGENTS.md runBlocking exception); the DB is empty, no seeding needed.
        runBlocking { withContext(NzikDispatchers.DATA) { DatabaseInitializer.Instance.clearAllTables() } }
    }

    @After
    fun tearDown() {
        unmockkAll()
        runBlocking { withContext(NzikDispatchers.DATA) { DatabaseInitializer.Instance.clearAllTables() } }
    }

    /**
     * Bounded real-time wait that also idles Robolectric's paused main looper: the
     * ViewModel coroutines (Main) and the Room queries (real NzikDispatchers.DATA
     * threads) run off the test thread, so the condition must be re-checked against
     * real time, not the compose main clock.
     */
    private fun waitUntilReal(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    /**
     * Compose variant of [waitUntilReal]: besides idling the main looper it must also
     * advance the fake main clock — recompositions and the `LaunchedEffect` restarts
     * (the fetch effects, the offline→online transition) only fire on a frame, which a
     * plain looper idle never produces (same rationale as
     * `RescueScreenProcessStatusTest.awaitStatus`).
     */
    private fun composeWaitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            composeRule.waitForIdle()
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    /** Absence check — `onAllNodesWithText` never throws (unlike `onNodeWithText`). */
    private fun assertNoText(text: String, description: String) {
        assertTrue(
            description,
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun artistItem(): Innertube.ArtistItem =
        Innertube.ArtistItem(
            info = Innertube.Info(
                name = "Test Artist",
                endpoint = NavigationEndpoint.Endpoint.Browse(browseId = "artist-1"),
            ),
            subscribersCountText = null,
            thumbnail = Thumbnail(url = "https://i.ytimg.com/vi/artist-1/hqdefault.jpg", height = null, width = null),
        )

    /** A YTM home page holding one populated "Mixed for you" section. */
    private fun mixedForYouPage(): HomePage =
        HomePage(
            sections = listOf(
                HomePage.Section(
                    title = "Mixed for you",
                    label = null,
                    thumbnail = null,
                    endpoint = BrowseEndpoint(browseId = "FEmusic_mixed_for_you"),
                    items = listOf(artistItem()),
                )
            ),
            chips = emptyList(),
        )

    /**
     * [HomeQuickPicksState] with plain states (no DataStore persistence): the
     * content composable never calls load()/refresh() itself — the screen-level
     * `refresh` lambda is what the tests observe.
     */
    private fun quickPicksState(loadedData: Boolean, homePageInit: HomePage?): HomeQuickPicksState =
        HomeQuickPicksState(
            trendingList = mutableStateOf(emptyList()),
            trending = mutableStateOf(null),
            trendingInit = null,
            relatedPageResult = mutableStateOf(null),
            discoverPageResult = mutableStateOf(null),
            discoverPageInit = mutableStateOf(null),
            homePageResult = mutableStateOf(null),
            homePageInit = mutableStateOf(homePageInit),
            chartsPageResult = mutableStateOf(null),
            chartsPageInit = mutableStateOf(null),
            loadedQuickPicks = mutableStateOf(true),
            loadedData = mutableStateOf(loadedData),
            playEventType = PlayEventsType.MostPlayed,
            selectedCountryCode = Countries.ZZ,
            parentalControlEnabled = false,
            localCount = 6,
            recommendations = mutableStateOf(emptyList()),
            ytmQuickPicks = mutableStateOf(emptyList()),
            refreshing = mutableStateOf(false),
            refreshKey = mutableIntStateOf(0),
        )

    private fun quickPicksContent(
        isOnline: Boolean,
        state: HomeQuickPicksState,
        refresh: () -> Unit = {},
    ) {
        // No marquee/animation frames to pump: the texts are asserted as-is.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                HomeQuickPicksContent(
                    state = state,
                    scrollState = rememberLazyListState(),
                    isOnline = isOnline,
                    refresh = refresh,
                    navController = mockk<NavController>(relaxed = true),
                    onAlbumClick = {},
                    onArtistClick = {},
                    onPlaylistClick = {},
                    onSearchClick = {},
                    onMoodClick = {},
                    onChipClick = {},
                    onSettingsClick = {},
                    onPlayEventTypeChange = {},
                    onSelectedCountryCodeChange = {},
                    currentYouTubeLoggedIn = false,
                    playEventType = PlayEventsType.MostPlayed,
                    selectedCountryCode = Countries.ZZ,
                    parentalControlEnabled = false,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        }
        composeRule.waitForIdle()
    }

    // I/O matrix: OFFLINE_ONLINE_CONTENT — the shared message, centered.
    @Test
    fun offlineContentShowsTheCenteredMessage() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OfflineContent(modifier = Modifier.fillMaxSize())
                }
            }
        }
        composeRule.waitForIdle()

        val message = composeRule.onNodeWithText(offlineMessage)
        message.assertIsDisplayed()

        // The text is horizontally centered on the root (the Box fills the screen).
        val rootBounds = composeRule.onRoot().getBoundsInRoot()
        val bounds = message.getBoundsInRoot()
        val rootCenter = (rootBounds.left + rootBounds.right) / 2
        val messageCenter = (bounds.left + bounds.right) / 2
        assertTrue(
            "the offline message must be horizontally centered, root=$rootCenter message=$messageCenter",
            abs((messageCenter - rootCenter).value).dp <= 2.dp,
        )
    }

    // I/O matrix: OFFLINE_LOCAL + OFFLINE_ONLINE_CONTENT — local Quick Picks
    // content renders (no shimmer/spinner), the YTM sections are replaced
    //
    // by the message, even when YTM data is already in memory.
    @Test
    fun quickPicksOfflineShowsTheMessageInsteadOfTheYtmSections() {
        quickPicksContent(
            isOnline = false,
            state = quickPicksState(loadedData = true, homePageInit = mixedForYouPage()),
        )

        composeRule.onNodeWithText(offlineMessage).assertIsDisplayed()
        // The local "For You" header renders directly — it needs no network.
        composeRule.onNodeWithText(tipsTitle).assertIsDisplayed()
        // The YTM section (and its shimmer) is gone, replaced by the message.
        assertNoText(mixedForYouTitle, "the YTM section (and its shimmer) must be replaced by the message")
    }

    // I/O matrix: ONLINE_OK — online with loaded data, the normal content renders
    // and no offline message.
    @Test
    fun quickPicksOnlineWithLoadedDataShowsTheContent() {
        quickPicksContent(
            isOnline = true,
            state = quickPicksState(loadedData = true, homePageInit = mixedForYouPage()),
        )

        composeRule.onNodeWithText("Test Artist").assertIsDisplayed()
        assertNoText(offlineMessage, "online with loaded data must not show the offline message")
    }

    // I/O matrix: ONLINE_FETCH_FAILS — online while the fetch is in flight, the
    // loader/shimmer is kept (current online behavior unchanged).
    @Test
    fun quickPicksOnlineWhileLoadingKeepsTheShimmer() {
        quickPicksContent(
            isOnline = true,
            state = quickPicksState(loadedData = false, homePageInit = null),
        )

        composeRule.onNodeWithText(mixedForYouTitle).assertIsDisplayed()
        assertNoText(offlineMessage, "online while loading must keep the shimmer, not the message")
    }

    // I/O matrix: RECONNECT — starting offline, the offline→online transition
    // triggers exactly one refresh (never on the initial composition); the
    //
    // message disappears.
    @Test
    fun quickPicksReconnectTriggersRefreshOnceAndHidesTheMessage() {
        var isOnline by mutableStateOf(false)
        var refreshCount = 0

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                HomeQuickPicksContent(
                    state = quickPicksState(loadedData = true, homePageInit = mixedForYouPage()),
                    scrollState = rememberLazyListState(),
                    isOnline = isOnline,
                    refresh = { refreshCount++ },
                    navController = mockk<NavController>(relaxed = true),
                    onAlbumClick = {},
                    onArtistClick = {},
                    onPlaylistClick = {},
                    onSearchClick = {},
                    onMoodClick = {},
                    onChipClick = {},
                    onSettingsClick = {},
                    onPlayEventTypeChange = {},
                    onSelectedCountryCodeChange = {},
                    currentYouTubeLoggedIn = false,
                    playEventType = PlayEventsType.MostPlayed,
                    selectedCountryCode = Countries.ZZ,
                    parentalControlEnabled = false,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        }
        composeRule.waitForIdle()

        // Composed offline from the start: no refresh on the initial composition.
        assertEquals(0, refreshCount)
        composeRule.onNodeWithText(offlineMessage).assertIsDisplayed()

        composeRule.runOnIdle { isOnline = true }
        composeWaitUntil(10_000) { refreshCount == 1 }

        assertEquals(
            "the offline→online transition must trigger exactly one refresh",
            1,
            refreshCount,
        )
        assertNoText(offlineMessage, "the offline message must disappear after the reconnect")
    }

    // OFFLINE_ONLINE_CONTENT — the branch the ArtistScreen/legacy screens use:
    // message when offline, plain loader when online.
    @Test
    fun offlineAwareLoaderShowsTheMessageWhenOffline() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OfflineAwareLoader(isOnline = false, modifier = Modifier.fillMaxSize())
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(offlineMessage).assertIsDisplayed()
    }

    @Test
    fun offlineAwareLoaderShowsNoMessageWhenOnline() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OfflineAwareLoader(isOnline = true, modifier = Modifier.fillMaxSize())
            }
        }
        composeRule.waitForIdle()

        assertNoText(offlineMessage, "online must keep the loader, not the offline message")
    }

    // RECONNECT — with an onReconnect handler (ArtistScreen refetch), the
    // offline→online transition invokes it exactly once.
    @Test
    fun offlineAwareLoaderInvokesOnReconnectOnceWhenTheNetworkReturns() {
        var isOnline by mutableStateOf(false)
        var reconnectCount = 0

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OfflineAwareLoader(
                    isOnline = isOnline,
                    modifier = Modifier.fillMaxSize(),
                    onReconnect = { reconnectCount++ },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, reconnectCount)

        composeRule.runOnIdle { isOnline = true }
        composeWaitUntil(10_000) { reconnectCount == 1 }

        assertEquals(
            "the offline→online transition must invoke onReconnect exactly once",
            1,
            reconnectCount,
        )
        assertNoText(offlineMessage, "the offline message must disappear after the reconnect")
    }

    // ── Vague 2 ─────────────────────────────────────────────────────────────────

    // OFFLINE_SONG_INFO: the Video/Song info sheet keeps the local params (title)
    // visible and shows the offline banner instead of the silent "−" degradation.
    @Test
    fun videoOrSongInfoSheetShowsTheOfflineBannerWhenInfoIsMissing() {
        // No network in the test: the songInfo fetch returns nothing (offline).
        mockkStatic("it.fast4x.innertube.requests.SongInfoKt")
        coEvery { Innertube.songInfo(any()) } returns null

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                VideoOrSongInfoScreen(
                    videoId = "test-video-id",
                    songTitle = "Offline Song",
                    songArtist = "",
                    songThumbnailUrl = "",
                    albumId = "",
                    albumTitle = "",
                    navController = null,
                    onNavigateUp = {},
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeWaitUntil(10_000) {
            composeRule.onAllNodesWithText(offlineMessage).fetchSemanticsNodes().isNotEmpty()
        }
        // The local params stay visible alongside the banner.
        composeRule.onNodeWithText("Offline Song").assertIsDisplayed()
        composeRule.onNodeWithText(offlineMessage).assertIsDisplayed()
    }

    // MB_ROW_MISSING (ViewModels): a missing DB row must end the load (no stuck
    // Loader) with the entity null — the screen branch then renders the message.
    @Test
    fun artistInsightsMissingDbRowEndsLoadingWithANullEntity() {
        val vm = ArtistInsightsViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.loadArtist("missing-artist-id")

        waitUntilReal(10_000) { !vm.state.value.isLoading }

        assertFalse(
            "missing DB row must end the load instead of leaving the Loader stuck",
            vm.state.value.isLoading,
        )
        assertNull("missing DB row leaves the entity null", vm.state.value.artist)
    }

    @Test
    fun albumInsightsMissingDbRowEndsLoadingWithANullEntity() {
        val vm = AlbumInsightsViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.loadAlbum("missing-album-id")

        waitUntilReal(10_000) { !vm.state.value.isLoading }

        assertFalse(
            "missing DB row must end the load instead of leaving the Loader stuck",
            vm.state.value.isLoading,
        )
        assertNull("missing DB row leaves the entity null", vm.state.value.album)
    }

    // MB_ROW_MISSING (compose): the screens show the centered "Unable to load"
    // message instead of the eternal Loader / blank screen.
    @Test
    fun artistInsightsScreenShowsTheMessageWhenTheDbRowIsMissing() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ArtistInsightsScreen(
                    navController = mockk<NavController>(relaxed = true),
                    artistId = "missing-artist-id",
                )
            }
        }
        composeRule.waitForIdle()

        composeWaitUntil(10_000) {
            composeRule.onAllNodesWithText(unableToLoad).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun albumInsightsScreenShowsTheMessageWhenTheDbRowIsMissing() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AlbumInsightsScreen(
                    navController = mockk<NavController>(relaxed = true),
                    albumId = "missing-album-id",
                )
            }
        }
        composeRule.waitForIdle()

        composeWaitUntil(10_000) {
            composeRule.onAllNodesWithText(unableToLoad).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // MB_OFFLINE: DB row present, network down — the local content renders as
    // today (existing behavior unchanged, no message).
    @Test
    fun artistInsightsRendersLocalContentWhenTheDbRowExists() {
        // runBlocking is forced by the synchronous test setup API
        // (AGENTS.md runBlocking exception); the write runs on the DATA executor
        //
        // so Room stays off the main thread.
        runBlocking {
            withContext(NzikDispatchers.DATA) {
                Database.artistTable.insertIgnore(Artist(id = "artist-offline", name = "Offline Artist"))
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ArtistInsightsScreen(
                    navController = mockk<NavController>(relaxed = true),
                    artistId = "artist-offline",
                )
            }
        }
        composeRule.waitForIdle()

        composeWaitUntil(10_000) {
            composeRule.onAllNodesWithText("Offline Artist").fetchSemanticsNodes().isNotEmpty()
        }
        assertNoText(unableToLoad, "local content must render without the error message")
    }

    @Test
    fun albumInsightsRendersLocalContentWhenTheDbRowExists() {
        // The artist row uses a "local:" id so the album load skips the online
        // artist-page completion (no network in the test).
        //
        // runBlocking is forced by the synchronous test setup API
        // (AGENTS.md runBlocking exception); the writes run on the DATA executor
        //
        // so Room stays off the main thread.
        runBlocking {
            withContext(NzikDispatchers.DATA) {
                Database.artistTable.insertIgnore(Artist(id = "local:1", name = "Offline Artist"))
                Database.albumTable.insertIgnore(
                    Album(id = "album-offline", title = "Offline Album", authorsText = "Offline Artist")
                )
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AlbumInsightsScreen(
                    navController = mockk<NavController>(relaxed = true),
                    albumId = "album-offline",
                )
            }
        }
        composeRule.waitForIdle()

        composeWaitUntil(10_000) {
            composeRule.onAllNodesWithText("Offline Album").fetchSemanticsNodes().isNotEmpty()
        }
        assertNoText(unableToLoad, "local content must render without the error message")
    }
}
