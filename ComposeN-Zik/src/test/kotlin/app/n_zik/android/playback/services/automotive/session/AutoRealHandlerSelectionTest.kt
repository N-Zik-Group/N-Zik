package app.n_zik.android.playback.services.automotive.session

import app.n_zik.android.core.network.utils.NetworkQualityHelper
import io.mockk.mockkObject
import io.mockk.every
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Format
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.browse.handlers.ArtistDetailHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.BrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.QuickPicksBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.SongsBrowseHandler
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.ArtistPage
import it.fast4x.innertube.requests.ArtistSection
import it.fast4x.innertube.requests.relatedPage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Runs real browse mapping and eligibility through tree capture and the public playback callback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoRealHandlerSelectionTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree

    @Before
    fun setUp() {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns app
        Dependencies.init(application)
        context = app
        context.preferences.edit().clear().commit()
        clearDatabase()
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
    }

    @After
    fun tearDown() {
        unmockkAll()
        tree.clearCache()
        context.preferences.edit().clear().commit()
        clearDatabase()
    }

    private fun clearDatabase() {
        // Synchronous JUnit lifecycle must dispatch Room work away from its main thread.
        runBlocking { withContext(NzikDispatchers.DATA) { DatabaseInitializer.Instance.clearAllTables() } }
    }

    private fun browse(handler: BrowseHandler, parent: String, binder: PlayerServiceModern.Binder? = null): List<MediaItem> {
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply { isAccessible = true }.set(tree, listOf(handler))
        // Synchronous JUnit methods bridge the suspend browse API and Room work here.
        return runBlocking { withContext(NzikDispatchers.DATA) { tree.getChildren(parent, -1, binder) } }
    }

    private fun assertSelection(rows: List<MediaItem>, expected: List<String>, index: Int = 1) {
        val songs = rows.filter { AutoMediaIdContract.isScopedSelection(it.mediaId) }
        assertEquals(expected, songs.map {
            requireNotNull(tree.selectionStore.resolve(it.mediaId)).let { selection -> selection.items[selection.startIndex].mediaId }
        })
        val result = callback.onSetMediaItems(
            mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(), mutableListOf(songs[index]), 0, 789L
        ).get(15, TimeUnit.SECONDS)
        assertEquals(expected, result.mediaItems.map { it.mediaId })
        assertEquals(index, result.startIndex)
        assertEquals(expected[index], result.mediaItems[result.startIndex].mediaId)
    }

    private fun video(id: String) = Innertube.VideoItem(
        info = Innertube.Info(name = "Video $id", endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)),
        authors = emptyList(), viewsText = null, durationText = "3:00", thumbnail = null
    )

    @Test
    fun `empty history quick picks load default seed and selected related song plays its displayed queue`() {
        val events = mockk<EventTable>()
        mockkObject(Database)
        every { Database.eventTable } returns events
        every { events.findSongsMostPlayedBetween(any(), any(), any()) } returns flowOf(emptyList())
        mockkStatic("app.n_zik.android.components.settings.accounts.AccountsSettingsKt")
        every { isYouTubeLoggedIn() } returns false
        mockkStatic("it.fast4x.innertube.requests.RelatedPageKt")
        val ids = listOf("related-c", "related-a", "related-b")
        val related = Innertube.RelatedPage(songs = ids.map { id ->
            Innertube.SongItem(
                info = Innertube.Info(name = id, endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)),
                authors = emptyList(), album = null, durationText = null, thumbnail = null
            )
        })
        // Nullable Result suspend mocking needs the same extra Result layer as QuickPicksBrowseHandlerTest.
        @Suppress("UNCHECKED_CAST")
        val response = Result.success(Result.success(related)) as Result<Innertube.RelatedPage?>?
        coEvery { Innertube.relatedPage(any(), any()) } returns response
        val rows = browse(QuickPicksBrowseHandler(), AutoSessionConstants.ID_QUICK_PICKS)
        val displayed = rows.filter { AutoMediaIdContract.isScopedSelection(it.mediaId) }.map {
            requireNotNull(tree.selectionStore.resolve(it.mediaId)).let { selection -> selection.items[selection.startIndex].mediaId }
        }
        assertEquals(ids.toSet(), displayed.toSet())
        assertEquals(3, displayed.size)
        coVerify(exactly = 1) { Innertube.relatedPage(videoId = "4NRXx6U8ABQ", setLogin = false) }
        assertSelection(rows, displayed)
    }

    @Test
    fun `artist Videos section maps videos without borrowing songs from previous search`() {
        mockkObject(YtMusic)
        AutoSearchState.searchedSongs = listOf(Song.makePlaceholder("previous-search-song"))
        AutoSearchState.searchedVideos = listOf(video("previous-search-video"))
        val ids = listOf("video-c", "video-a", "video-b")
        val page = ArtistPage(
            artist = mockk(relaxed = true), sections = listOf(ArtistSection("Videos", ids.map(::video), null)),
            description = null, subscribers = null, listeners = null, shuffleEndpoint = null, radioEndpoint = null
        )
        coEvery { YtMusic.getArtistPage("UC_artist", any()) } returns Result.success(page)
        val rows = browse(ArtistDetailHandler(), "artist/UC_artist/Videos")
        assertSelection(rows, ids)
        coVerify(exactly = 1) { YtMusic.getArtistPage("UC_artist", any()) }
    }

    @Test
    fun `cached never played songs follow configured title sort and reject missing cache or length`() {
        context.preferences.edit()
            .putString(Preference.HOME_SONGS_OFFLINE_SORT_BY.key, SongSortBy.Title.name)
            .putString(Preference.HOME_SONGS_OFFLINE_SORT_ORDER.key, SortOrder.Descending.name)
            .remove(maxSongsInQueueAndroidAutoKey).commit()
        val songs = listOf(
            Song.makePlaceholder("cache-a").copy(title = "Alpha"),
            Song.makePlaceholder("cache-z").copy(title = "Zulu"),
            Song.makePlaceholder("cache-m").copy(title = "Middle"),
            Song.makePlaceholder("not-cached").copy(title = "No cache"),
            Song.makePlaceholder("no-length").copy(title = "No length")
        )
        assertTrue(songs.all { it.totalPlayTimeMs == 0L })
        // Synchronous JUnit fixture creation must run Room writes away from its main thread.
        runBlocking { withContext(NzikDispatchers.DATA) {
            songs.forEach { song ->
                Database.songTable.upsert(song)
                Database.formatTable.upsert(Format(songId = song.id, contentLength = if (song.id == "no-length") null else 123L))
            }
        } }
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.cache.isCached(any(), 0L, any()) } answers { firstArg<String>() != "not-cached" }
        callback.binder = binder
        val rows = browse(SongsBrowseHandler(), AutoSessionConstants.ID_SONGS_CACHED, binder)
        assertSelection(rows, listOf("cache-z", "cache-m", "cache-a"))
    }
}
