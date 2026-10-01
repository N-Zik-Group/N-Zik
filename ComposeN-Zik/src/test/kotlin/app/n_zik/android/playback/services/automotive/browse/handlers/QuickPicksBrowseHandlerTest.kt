package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.playEventsTypeKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.TestApplication
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.relatedPage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the AA QuickPicks page as a 1:1 mirror of the in-app home QuickPicks
 * row (HomeQuickPicks / HomeQuickPicksState): local trending per the
 * play-events setting + related page, the first local song pinned right after
 * the Lucky Shuffle entry, deterministic assembly, and the app's default
 * related seed ("4NRXx6U8ABQ") when there is no local history — so the row
 * fills even with an empty DB, exactly like in the app.
 *
 * Harness follows [AutoBrowseDetailHandlersMediaIdTest] (Robolectric +
 * TestApplication, mockk MainApplication) and [VideoArtistFallbackWriteTest]
 * (mockkObject(Database) with stubbed tables, `Innertube.relatedPage`
 * static-mocked so no network happens). runBlocking is forced by the
 * synchronous test API (AGENTS.md runBlocking exception).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class QuickPicksBrowseHandlerTest {

    private lateinit var context: Context
    private val downloadHelper: MyDownloadHelper = mockk(relaxed = true)
    private val eventTable = mockk<EventTable>(relaxed = true)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        context = app

        mockkObject(Database)
        every { Database.eventTable } returns eventTable
        // Defaults: no local history until a test stubs it
        every { eventTable.findSongsMostPlayedBetween(any(), any(), any()) } returns flowOf(emptyList())
        every { eventTable.findSongsLastPlayed(any()) } returns flowOf(emptyList())

        // No network: the related page is static-mocked, and YouTube is
        // "logged out" so the ytm section is skipped and useLoginForBrowse
        // is never read (both call sites short-circuit on isYouTubeLoggedIn).
        mockkStatic("it.fast4x.innertube.requests.RelatedPageKt")
        mockkStatic("app.it.fast4x.rimusic.ui.screens.settings.AccountsSettingsKt")
        every { isYouTubeLoggedIn() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * `relatedPage` returns a nullable `Result<RelatedPage?>`, and MockK
     * 1.14.11's suspend-mocking support unwraps a `returns` value that is
     * itself a `kotlin.Result` one level for such functions — so the intended
     * `Result.success(page)` is wrapped in one extra `Result.success(...)`
     * layer (same trick as VideoArtistFallbackWriteTest.relatedPageResult).
     */
    @Suppress("UNCHECKED_CAST")
    private fun relatedPageResult(value: Result<Innertube.RelatedPage?>): Result<Innertube.RelatedPage?>? =
        Result.success(value) as Result<Innertube.RelatedPage?>?

    private fun stubRelated(videoIds: List<String>) {
        val songs = videoIds.map { id ->
            Innertube.SongItem(
                info = Innertube.Info(
                    name = "Related $id",
                    endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)
                ),
                authors = emptyList(),
                album = null,
                durationText = null,
                thumbnail = null
            )
        }
        coEvery { Innertube.relatedPage(any(), any()) } returns relatedPageResult(
            Result.success(Innertube.RelatedPage(songs = songs))
        )
    }

    private fun song(id: String) = Song.makePlaceholder(id).copy(title = id)

    private fun fetchIds(): List<String> = runBlocking {
        QuickPicksBrowseHandler()
            .getChildren(AutoSessionConstants.ID_QUICK_PICKS, context, Database, downloadHelper, null)
            .map { it.mediaId }
    }

    @Test
    fun `mostPlayed pins the first local song and mixes the related songs`() {
        every { eventTable.findSongsMostPlayedBetween(any(), any(), any()) } returns flowOf(listOf(song("qa"), song("qb")))
        stubRelated(listOf("rel1", "rel2"))

        val ids = fetchIds()

        assertEquals(AutoSessionConstants.ID_LUCKY_SHUFFLE, ids.first())
        // App behavior: listOfNotNull(first) + pool.shuffled(random) — the
        // first local song is pinned, the rest of the pool is intermixed.
        assertEquals("QUICK_PICKS/qa", ids[1])
        assertEquals(setOf("QUICK_PICKS/qb", "QUICK_PICKS/rel1", "QUICK_PICKS/rel2"), ids.drop(2).toSet())
        // Lucky Shuffle + pinned first local + 3 pool songs.
        assertEquals(5, ids.size)
    }

    @Test
    fun `assembly is deterministic for the same data`() {
        every { eventTable.findSongsMostPlayedBetween(any(), any(), any()) } returns
            flowOf(listOf(song("qa"), song("qb"), song("qc")))
        stubRelated(listOf("rel1", "rel2", "rel3"))

        val first = fetchIds()
        val second = fetchIds()

        // Same trending ids + same related keys => same seed => same order
        assertEquals(first, second)
        assertEquals(AutoSessionConstants.ID_LUCKY_SHUFFLE, first.first())
        assertEquals(7, first.size)
    }

    @Test
    fun `empty history uses the app default related seed`() {
        stubRelated(listOf("rel1"))

        val ids = fetchIds()

        // No local songs: the related songs still fill the row, exactly like
        // the app does with its fallback seed video.
        assertEquals(setOf(AutoSessionConstants.ID_LUCKY_SHUFFLE, "QUICK_PICKS/rel1"), ids.toSet())
        coVerify(exactly = 1) { Innertube.relatedPage(videoId = "4NRXx6U8ABQ", setLogin = false) }
    }

    @Test
    fun `lastPlayed uses the last-played query and pins its first song`() {
        context.preferences.edit { putString(playEventsTypeKey, PlayEventsType.LastPlayed.name) }
        every { eventTable.findSongsLastPlayed(any()) } returns flowOf(listOf(song("recent"), song("older")))
        stubRelated(emptyList())

        val ids = fetchIds()

        assertEquals(AutoSessionConstants.ID_LUCKY_SHUFFLE, ids.first())
        assertEquals("QUICK_PICKS/recent", ids[1])
        assertEquals(
            setOf(AutoSessionConstants.ID_LUCKY_SHUFFLE, "QUICK_PICKS/recent", "QUICK_PICKS/older"),
            ids.toSet()
        )
    }

    @Test
    fun `parental control filters explicit songs from the row`() {
        context.preferences.edit { putBoolean(parentalControlEnabledKey, true) }
        every { eventTable.findSongsMostPlayedBetween(any(), any(), any()) } returns
            flowOf(listOf(song("clean"), song("ex").copy(title = "$EXPLICIT_PREFIX explicit")))
        stubRelated(emptyList())

        val ids = fetchIds()

        // The explicit song is dropped; the clean one stays.
        assertTrue(ids.any { it == "QUICK_PICKS/clean" })
        assertFalse(ids.any { it == "QUICK_PICKS/ex" })
    }

    @Test
    fun `casualPlayed uses the from-0 limit-100 query`() {
        context.preferences.edit { putString(playEventsTypeKey, PlayEventsType.CasualPlayed.name) }
        // Only the CasualPlayed query (from=0, limit=100) returns songs; the
        // all-time MostPlayed window stays empty via the default stub.
        every { eventTable.findSongsMostPlayedBetween(0L, any(), 100) } returns flowOf(listOf(song("cas1"), song("cas2")))
        stubRelated(emptyList())

        val ids = fetchIds()

        // The casual songs appear — proving the from=0/limit=100 query was used, not
        // the all-time window (which returns nothing).
        assertTrue(ids.any { it == "QUICK_PICKS/cas1" })
        assertTrue(ids.any { it == "QUICK_PICKS/cas2" })
        coVerify(exactly = 1) { eventTable.findSongsMostPlayedBetween(0L, any(), 100) }
    }
}
