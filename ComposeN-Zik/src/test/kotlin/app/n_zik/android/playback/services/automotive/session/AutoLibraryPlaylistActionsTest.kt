package app.n_zik.android.playback.services.automotive.session

import java.util.concurrent.TimeUnit
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.ui.screens.home.YOUTUBE_LIBRARY_FILTER_KEY
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoLibraryPlaylistActionsTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var originalDownloads: MutableStateFlow<Map<String, Download>>
    private var online = true
    private var monthlyEnabled = true
    private var yearlyEnabled = true

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        context.preferences.edit().clear().commit()
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } answers { online }
        mockkObject(DataStoreUtils)
        every { DataStoreUtils.getBoolean(any(), DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true) } answers { monthlyEnabled }
        every { DataStoreUtils.getBoolean(any(), DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true) } answers { yearlyEnabled }
        originalDownloads = MyDownloadHelper.downloads
        MyDownloadHelper.downloads = MutableStateFlow(emptyMap())
        onDatabase { DatabaseInitializer.Instance.clearAllTables() }
        callback = AutoSessionCallback(context, Database, MyDownloadHelper)
    }

    @After fun tearDown() {
        callback.release()
        onDatabase { DatabaseInitializer.Instance.clearAllTables() }
        MyDownloadHelper.downloads = originalDownloads
        context.preferences.edit().clear().commit()
        unmockkObject(DataStoreUtils, NetworkQualityHelper)
    }

    @Test fun `category action contract accepts only exact known actions and canonicalizes old YouTube`() {
        val parents = listOf(
            AutoSessionConstants.ID_PLAYLISTS_LOCAL,
            AutoSessionConstants.ID_PLAYLISTS_YT_ALL,
            AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS,
            AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS,
            AutoSessionConstants.ID_PLAYLISTS_PINNED,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR,
        )
        parents.forEach { parent ->
            listOf("_SHUFFLE", "_PLAY_ALL").forEach { suffix ->
                assertEquals(parent, AutoMediaIdContract.playlistCategoryForAction(parent + suffix))
                assertTrue(AutoAccessPolicy.reappliesExclusions(parent + suffix))
            }
            assertTrue(AutoAccessPolicy.reappliesExclusions(parent))
            assertNull(AutoMediaIdContract.playlistCategoryForAction(parent))
            assertNull(AutoMediaIdContract.playlistCategoryForAction("$parent/other_SHUFFLE"))
        }
        listOf("PLAYLISTS_YT_SHUFFLE", "PLAYLISTS_YT_PLAY_ALL").forEach {
            assertEquals(AutoSessionConstants.ID_PLAYLISTS_YT_ALL, AutoMediaIdContract.playlistCategoryForAction(it))
        }
        assertNull(AutoMediaIdContract.playlistCategoryForAction("PLAYLISTS_YT_UNKNOWN_SHUFFLE"))
        assertNull(AutoMediaIdContract.playlistCategoryForAction("PLAYLIST_SHUFFLE/42"))
    }

    @Test fun `saved and imported podcasts join All and Podcasts independently of bookmark flag`() {
        seed(Playlist(1, "A podcast", "MPSPfirst", isYoutubePlaylist = false), "episode-a", "shared")
        seed(Playlist(2, "B imported podcast", "${MODIFIED_PREFIX}MPSPsecond"), "episode-b", "shared")
        seed(Playlist(3, "C playlist", "VLmusic", isYoutubePlaylist = true), "music")
        seed(Playlist(4, "D local", "provider-marker", isYoutubePlaylist = true), "local-song")
        context.preferences.edit().putString(YOUTUBE_LIBRARY_FILTER_KEY, "Playlists").commit()
        val before = context.preferences.all.toMap()
        val podcastIds = listOf("episode-a", "shared", "episode-b")
        assertEquals(podcastIds, action("PLAYLISTS_YT_PODCASTS_PLAY_ALL"))
        assertEquals(podcastIds.toSet(), action("PLAYLISTS_YT_PODCASTS_SHUFFLE").toSet())
        assertEquals(listOf("music"), action("PLAYLISTS_YT_PLAYLISTS_PLAY_ALL"))
        val all = podcastIds + "music"
        assertEquals(all, action("PLAYLISTS_YT_ALL_PLAY_ALL"))
        assertEquals(all, action("PLAYLISTS_YT_PLAY_ALL"))
        assertEquals(all.toSet(), action("PLAYLISTS_YT_SHUFFLE").toSet())
        assertEquals(before, context.preferences.all)
    }

    @Test fun `aggregate playback respects playlist name ordering and per-playlist song order`() {
        seed(Playlist(1, "Z podcast", "MPSPz"), "z-last", "z-first")
        seed(Playlist(2, "A podcast", "MPSPa"), "a-first", "a-last")
        context.preferences.edit()
            .putString("PlaylistSongsSortBy_2", PlaylistSongSortBy.Title.name)
            .putString("PlaylistSongsSortOrder_2", SortOrder.Descending.name)
            .commit()
        val before = context.preferences.all.toMap()
        assertEquals(listOf("a-last", "a-first", "z-first", "z-last"), action("PLAYLISTS_YT_PODCASTS_PLAY_ALL"))
        assertEquals(listOf("a-last", "a-first"), action("PLAYLIST_PLAY_ALL/2"))
        assertEquals(before, context.preferences.all)
    }

    @Test fun `empty or fully disliked podcast groups return an empty queue`() {
        assertTrue(action("PLAYLISTS_YT_PODCASTS_PLAY_ALL").isEmpty())
        seed(Playlist(1, "Podcast", "MPSPfirst"), "blocked")
        onDatabase { Database.songTable.upsert(song("blocked").copy(likedAt = -1L)) }
        assertTrue(action("PLAYLISTS_YT_PODCASTS_SHUFFLE").isEmpty())
        assertTrue(action("PLAYLISTS_YT_PLAY_ALL").isEmpty())
    }

    @Test fun `Downloaded direct and aggregate actions group only completed downloads in both directions`() {
        seed(Playlist(1, "A local"), "d-completed", "c-queued", "b-completed", "a-missing")
        seed(Playlist(2, "B local"), "z-next-playlist")
        MyDownloadHelper.downloads.value = mapOf(
            "b-completed" to download("b-completed"),
            "d-completed" to download("d-completed"),
            "c-queued" to download("c-queued", Download.STATE_QUEUED),
        )
        listOf(
            SortOrder.Ascending to listOf("b-completed", "d-completed", "a-missing", "c-queued"),
            SortOrder.Descending to listOf("a-missing", "c-queued", "b-completed", "d-completed"),
        ).forEach { (order, expected) ->
            context.preferences.edit()
                .putString("PlaylistSongsSortBy_1", PlaylistSongSortBy.Downloaded.name)
                .putString("PlaylistSongsSortOrder_1", order.name).commit()
            val before = context.preferences.all.toMap()
            assertEquals(order.name, expected, action("PLAYLIST_PLAY_ALL/1"))
            assertEquals(order.name, expected + "z-next-playlist", action("PLAYLISTS_LOCAL_PLAY_ALL"))
            assertEquals(order.name, expected.toSet(), action("PLAYLIST_SHUFFLE/1").toSet())
            assertEquals(order.name, (expected + "z-next-playlist").toSet(), action("PLAYLISTS_LOCAL_SHUFFLE").toSet())
            assertEquals(before, context.preferences.all)
        }
    }

    @Test fun `Downloaded grouping happens before direct and aggregate queue caps in both directions`() {
        val ids = (0 until 55).map { "song-%02d".format(it) }
        seed(Playlist(1, "A local"), *ids.reversed().toTypedArray())
        seed(Playlist(2, "B local"), "next-playlist")
        MyDownloadHelper.downloads.value = ids.takeLast(3).associateWith { download(it) }
        context.preferences.edit()
            .putString(maxSongsInQueueAndroidAutoKey, "50")
            .putString("PlaylistSongsSortBy_1", PlaylistSongSortBy.Downloaded.name).commit()
        listOf(
            SortOrder.Ascending to (ids.takeLast(3) + ids.dropLast(3)),
            SortOrder.Descending to ids,
        ).forEach { (order, expected) ->
            context.preferences.edit().putString("PlaylistSongsSortOrder_1", order.name).commit()
            assertEquals(order.name, expected.take(50), action("PLAYLIST_PLAY_ALL/1"))
            assertEquals(order.name, expected.take(50), action("PLAYLISTS_LOCAL_PLAY_ALL"))
        }
    }

    @Test fun `offline play all retains later downloaded episodes when first episode is unavailable`() {
        seed(Playlist(1, "Podcast", "MPSPfirst"), "a-unavailable", "b-downloaded", "c-disliked")
        onDatabase { Database.songTable.upsert(song("c-disliked").copy(likedAt = -1L)) }
        fun download(id: String) = Download(
            DownloadRequest.Builder(id, Uri.parse("https://example.test/$id")).build(),
            Download.STATE_COMPLETED, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE,
        )
        MyDownloadHelper.downloads = MutableStateFlow(mapOf(
            "b-downloaded" to download("b-downloaded"), "c-disliked" to download("c-disliked"),
        ))
        online = false
        val rows = callback.onGetChildren(
            mockk<MediaLibrarySession>(), mockk<MediaSession.ControllerInfo>(),
            AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS, 0, 200, null,
        ).get(15, TimeUnit.SECONDS).value.orEmpty()
        val actions = rows.filter { AutoMediaIdContract.isQueueAction(it.mediaId) }.map { it.mediaId }
        assertEquals(setOf("PLAYLISTS_YT_PODCASTS_PLAY_ALL", "PLAYLISTS_YT_PODCASTS_SHUFFLE"), actions.toSet())
        actions.forEach { assertEquals(listOf("b-downloaded"), action(it)) }
    }

    @Test fun `Rewind action rereads generation toggles and local actions honor hidden sections`() {
        seed(Playlist(1, "rewind-monthly:202601"), "month")
        seed(Playlist(2, "rewind-yearly:2026"), "year")
        seed(Playlist(3, "rewind-alltime"), "all-time")
        seed(Playlist(4, "pinned:Mix"), "pinned")
        seed(Playlist(5, "Plain"), "plain")
        assertEquals(setOf("month", "year", "all-time"), action("PLAYLISTS_REWIND_ALL_PLAY_ALL").toSet())
        yearlyEnabled = false
        assertEquals(listOf("month"), action("PLAYLISTS_REWIND_ALL_PLAY_ALL"))
        monthlyEnabled = false
        assertTrue(action("PLAYLISTS_REWIND_ALL_SHUFFLE").isEmpty())
        yearlyEnabled = true
        assertEquals(listOf("year"), action("PLAYLISTS_REWIND_ALL_PLAY_ALL"))
        context.preferences.edit().putBoolean(showMonthlyPlaylistsKey, false).putBoolean(showPinnedPlaylistsKey, false).commit()
        assertEquals(listOf("plain"), action("PLAYLISTS_LOCAL_PLAY_ALL"))
    }

    @Test fun `podcast aggregate keeps the configured queue cap`() {
        val ids = (0 until 55).map { "episode-%02d".format(it) }
        seed(Playlist(1, "Podcast", "MPSPfirst"), *ids.toTypedArray())
        context.preferences.edit().putString(maxSongsInQueueAndroidAutoKey, "50").commit()
        assertEquals(ids.take(50), action("PLAYLISTS_YT_PODCASTS_PLAY_ALL"))
    }

    private fun seed(playlist: Playlist, vararg ids: String) = onDatabase {
        Database.playlistTable.insertIgnore(playlist)
        ids.forEach { id ->
            Database.songTable.upsert(song(id))
            Database.songPlaylistMapTable.map(id, playlist.id)
        }
    }

    private fun song(id: String) = Song(id = id, title = id, durationText = null, thumbnailUrl = null)

    private fun download(id: String, state: Int = Download.STATE_COMPLETED) = Download(
        DownloadRequest.Builder(id, Uri.parse("https://example.test/$id")).build(),
        state, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE,
    )

    // JUnit setup and assertions are synchronous; Room must execute on the data dispatcher.
    private fun <T> onDatabase(block: suspend () -> T): T = runBlocking {
        withContext(NzikDispatchers.DATA) { block() }
    }

    private fun action(id: String): List<String> = callback.onSetMediaItems(
        mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(),
        mutableListOf(MediaItem.Builder().setMediaId(id).build()), 0, 0L,
    ).get(15, TimeUnit.SECONDS).mediaItems.map { it.mediaId }
}
