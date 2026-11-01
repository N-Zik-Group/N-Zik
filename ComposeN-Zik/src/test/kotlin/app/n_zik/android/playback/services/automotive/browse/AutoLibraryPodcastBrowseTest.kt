package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.Preference
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
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.handlers.PlaylistsBrowseHandler
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.playback.services.automotive.session.AutoSessionCallback
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Real saved-library navigation, including the snapshot selected through the public callback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoLibraryPodcastBrowseTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree
    private val handler = PlaylistsBrowseHandler()
    private val downloads = MyDownloadHelper
    private val originalDownloads = MyDownloadHelper.downloads

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<TestApplication>()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        context.preferences.edit().clear().commit()
        DataStoreUtils.prefs(context).edit().clear().commit()
        downloads.downloads = MutableStateFlow(emptyMap())
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        onDatabase { DatabaseInitializer.Instance.clearAllTables() }
        callback = AutoSessionCallback(context, Database, downloads)
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
    }

    @After
    fun tearDown() {
        callback.release()
        tree.clearCache()
        onDatabase { DatabaseInitializer.Instance.clearAllTables() }
        context.preferences.edit().clear().commit()
        DataStoreUtils.prefs(context).edit().clear().commit()
        downloads.downloads = originalDownloads
        unmockkAll()
    }

    private fun <T> onDatabase(block: suspend () -> T): T =
        // JUnit is synchronous, while real Room DAO access must run off the main thread.
        runBlocking { withContext(NzikDispatchers.DATA) { block() } }

    private fun children(parent: String): List<MediaItem> = onDatabase {
        handler.getChildren(parent, context, Database, downloads, null)
    }

    private fun browse(parent: String, page: Int = -1): List<MediaItem> =
        onDatabase { tree.getChildren(parent, page, null) }

    private fun folders(parent: String): List<MediaItem> =
        children(parent).filter { it.mediaMetadata.isBrowsable == true }

    private fun folderIds(parent: String): List<String> = folders(parent).map { it.mediaId }

    private fun seed(vararg playlists: Playlist) = onDatabase {
        playlists.forEach { Database.playlistTable.insertIgnore(it) }
    }

    private fun select(item: MediaItem): MediaSession.MediaItemsWithStartPosition = callback.onSetMediaItems(
        mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(), mutableListOf(item), 0, 1234L,
    ).get(15, TimeUnit.SECONDS)

    private fun seedMixedSources() = seed(
        Playlist(id = 1, name = "Saved podcast", browseId = "MPSPsaved", isYoutubePlaylist = false, isEditable = false),
        Playlist(id = 2, name = "Modified podcast", browseId = "${MODIFIED_PREFIX}MPSPmodified", isYoutubePlaylist = false),
        Playlist(id = 3, name = "Imported podcast", browseId = "MPSPimported", isYoutubePlaylist = false, isEditable = true),
        Playlist(id = 4, name = "Remote playlist", browseId = "VLPLremote", isYoutubePlaylist = true),
        Playlist(id = 5, name = "Modified playlist", browseId = "${MODIFIED_PREFIX}PLmodified", isYoutubePlaylist = false),
        Playlist(id = 6, name = "Local playlist"),
        Playlist(id = 7, name = "Spotify import", browseId = "SPOTIFY_IMPORT_1", isYoutubePlaylist = true),
        Playlist(id = 8, name = "Other import", browseId = "RIPLAY_IMPORT_1"),
        Playlist(id = 9, name = "Unknown source", browseId = "unknown-local", isYoutubePlaylist = true),
    )

    @Test
    fun `saved modified and imported podcasts use source identity for folders counts and icons`() {
        seedMixedSources()
        assertEquals(listOf("playlist/3", "playlist/2", "playlist/1"), folderIds(AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS))
        assertEquals(listOf("playlist/5", "playlist/4"), folderIds(AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS))
        assertEquals(setOf("playlist/1", "playlist/2", "playlist/3", "playlist/4", "playlist/5"),
            folderIds(AutoSessionConstants.ID_PLAYLISTS_YT_ALL).toSet())
        assertEquals(mapOf(
            AutoSessionConstants.ID_PLAYLISTS_YT_ALL to "5",
            AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS to "2",
            AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS to "3",
        ), children(AutoSessionConstants.ID_PLAYLISTS_YT).associate { it.mediaId to it.mediaMetadata.subtitle.toString() })
        assertEquals("5", children(PlayerServiceModern.PLAYLIST)
            .single { it.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT }.mediaMetadata.subtitle)
        folders(AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS).forEach {
            assertTrue(it.mediaMetadata.artworkUri.toString().contains("podcast"))
            assertEquals(false, it.mediaMetadata.isPlayable)
        }
        assertFalse(onDatabase { requireNotNull(Database.playlistTable.findById(1).first()).isYoutubePlaylist })
    }

    @Test
    fun `phone filter orders groups without writing missing invalid or selected preferences`() {
        seedMixedSources()
        val all = AutoSessionConstants.ID_PLAYLISTS_YT_ALL
        val playlists = AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS
        val podcasts = AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS
        listOf(
            null to listOf(all, playlists, podcasts),
            "Invalid" to listOf(all, playlists, podcasts),
            "All" to listOf(all, playlists, podcasts),
            "Playlists" to listOf(playlists, all, podcasts),
            "Podcasts" to listOf(podcasts, all, playlists),
        ).forEach { (selected, expected) ->
            context.preferences.edit().apply {
                if (selected == null) remove(YOUTUBE_LIBRARY_FILTER_KEY)
                else putString(YOUTUBE_LIBRARY_FILTER_KEY, selected)
            }.commit()
            val before = context.preferences.all.toMap()
            val groups = browse(AutoSessionConstants.ID_PLAYLISTS_YT)
            assertEquals("selection=$selected", expected, groups.map { it.mediaId })
            assertEquals("selection=$selected", setOf("5", "2", "3"), groups.map { it.mediaMetadata.subtitle.toString() }.toSet())
            groups.forEach { browse(it.mediaId) }
            assertEquals("Browsing must be read-only for selection=$selected", before, context.preferences.all)
        }
    }

    @Test
    fun `library categories default to name and respect their own saved sorting`() {
        seed(
            Playlist(id = 10, name = "Zulu", browseId = "MPSPz"),
            Playlist(id = 11, name = "Alpha", browseId = "MPSPa"),
            Playlist(id = 12, name = "${PINNED_PREFIX}Zulu"),
            Playlist(id = 13, name = "${PINNED_PREFIX}Alpha"),
            Playlist(id = 14, name = "rewind-monthly:202610"),
            Playlist(id = 15, name = "rewind-monthly:202601"),
        )
        val cases = listOf(
            Triple(AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS,
                Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER,
                listOf("playlist/11", "playlist/10")),
            Triple(AutoSessionConstants.ID_PLAYLISTS_PINNED,
                Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER,
                listOf("playlist/13", "playlist/12")),
            Triple(AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
                Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY to Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER,
                listOf("playlist/15", "playlist/14")),
        )
        cases.forEach { (parent, keys, expected) ->
            assertEquals(parent, expected, folderIds(parent))
            context.preferences.edit().putString(keys.first.key, PlaylistSortBy.Name.name)
                .putString(keys.second.key, SortOrder.Descending.name).commit()
            assertEquals(parent, expected.reversed(), folderIds(parent))
            context.preferences.edit().putString(keys.first.key, PlaylistSortBy.DateAdded.name)
                .putString(keys.second.key, SortOrder.Ascending.name).commit()
            assertEquals(parent, expected.reversed(), folderIds(parent))
            context.preferences.edit().remove(keys.first.key).remove(keys.second.key).commit()
        }
        assertEquals(listOf("Alpha", "Alpha", "Zulu", "Zulu"),
            folders(AutoSessionConstants.ID_PLAYLISTS_LOCAL).take(4).map { it.mediaMetadata.title.toString().removePrefix(PINNED_PREFIX) })
        context.preferences.edit().putString(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.DateAdded.name)
            .putString(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER.key, SortOrder.Descending.name).commit()
        assertEquals((15 downTo 10).map { "playlist/$it" }, folderIds(AutoSessionConstants.ID_PLAYLISTS_LOCAL))
        assertEquals(listOf("playlist/11", "playlist/10"), folderIds(AutoSessionConstants.ID_PLAYLISTS_YT_ALL))
    }

    @Test
    fun `rewind generation combinations gate dedicated counts and groups but not general library`() {
        seed(
            Playlist(id = 1, name = "rewind-monthly:202609"),
            Playlist(id = 2, name = "rewind-yearly:2026"),
            Playlist(id = 3, name = "rewind-alltime"),
            Playlist(id = 4, name = "Ordinary"),
            Playlist(id = 5, name = "${PINNED_PREFIX}Pinned"),
        )
        listOf(true to true, true to false, false to true, false to false).forEach { (monthly, yearly) ->
            DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, monthly)
            DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, yearly)
            val before = DataStoreUtils.prefs(context).all.toMap()
            val expected = when {
                monthly && yearly -> setOf("playlist/1", "playlist/2", "playlist/3")
                monthly -> setOf("playlist/1")
                yearly -> setOf("playlist/2")
                else -> emptySet()
            }
            assertEquals(expected, folderIds(AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL).toSet())
            assertEquals(if (monthly) listOf("playlist/1") else emptyList<String>(), folderIds(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH))
            assertEquals(if (yearly) listOf("playlist/2") else emptyList<String>(), folderIds(AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR))
            val counts = children(AutoSessionConstants.ID_PLAYLISTS_REWIND).associate { it.mediaId to it.mediaMetadata.subtitle.toString() }
            assertEquals(expected.size.toString(), counts[AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL])
            assertEquals(if (monthly) "1" else "0", counts[AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH])
            assertEquals(if (yearly) "1" else "0", counts[AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR])
            assertEquals(expected.size.toString(), children(PlayerServiceModern.PLAYLIST)
                .single { it.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND }.mediaMetadata.subtitle)
            assertEquals((1..5).map { "playlist/$it" }.toSet(), folderIds(AutoSessionConstants.ID_PLAYLISTS_LOCAL).toSet())
            assertEquals(before, DataStoreUtils.prefs(context).all)
        }
        context.preferences.edit().putBoolean(showMonthlyPlaylistsKey, false).putBoolean(showPinnedPlaylistsKey, false).commit()
        assertEquals(listOf("playlist/4"), folderIds(AutoSessionConstants.ID_PLAYLISTS_LOCAL))
        assertTrue(children(PlayerServiceModern.PLAYLIST).none {
            it.mediaId in setOf(AutoSessionConstants.ID_PLAYLISTS_REWIND, AutoSessionConstants.ID_PLAYLISTS_PINNED)
        })
    }

    @Test
    fun `empty saved podcast opens a safe local detail folder`() {
        seed(Playlist(id = 42, name = "Empty podcast", browseId = "MPSPempty", isYoutubePlaylist = false))
        val podcast = browse(AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS).single { it.mediaMetadata.isBrowsable == true }
        assertEquals("playlist/42", podcast.mediaId)
        assertEquals("0", podcast.mediaMetadata.subtitle)
        val detail = browse(podcast.mediaId)
        assertTrue(detail.all { AutoMediaIdContract.isQueueAction(it.mediaId) })
        assertTrue(select(detail.first()).mediaItems.isEmpty())
    }

    @Test
    fun `saved podcast page two selects the exact episode in the full phone sorted queue`() {
        seed(Playlist(id = 42, name = "Long saved podcast", browseId = "MPSPlong", isYoutubePlaylist = false))
        val episodes = (0 until 205).map {
            Song(id = "episode-$it", title = "Episode ${it.toString().padStart(3, '0')}",
                artistsText = "Saved show", durationText = "30:00", thumbnailUrl = null)
        }
        onDatabase { episodes.forEach { Database.songTable.upsert(it); Database.songPlaylistMapTable.map(it.id, 42) } }
        context.preferences.edit().putString("PlaylistSongsSortBy_42", PlaylistSongSortBy.Title.name)
            .putString("PlaylistSongsSortOrder_42", SortOrder.Descending.name).commit()
        val before = context.preferences.all.toMap()
        val podcast = browse(AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS).single { it.mediaMetadata.isBrowsable == true }
        val pages = browse(podcast.mediaId).filter { AutoMediaIdContract.parsePage(it.mediaId) != null }
        assertEquals(2, pages.size)
        val secondPage = browse(pages[1].mediaId)
        val rows = secondPage.filter { it.mediaMetadata.isPlayable == true && !AutoMediaIdContract.isQueueAction(it.mediaId) }
        assertEquals(5, rows.size)
        val selected = rows[2]
        val queue = select(selected)
        assertEquals(episodes.reversed().map { it.id }, queue.mediaItems.map { it.mediaId })
        assertEquals(202, queue.startIndex)
        assertEquals("episode-2", queue.mediaItems[queue.startIndex].mediaId)
        assertEquals("Episode 002", queue.mediaItems[queue.startIndex].mediaMetadata.title)
        assertEquals("Saved show", queue.mediaItems[queue.startIndex].mediaMetadata.artist)
        assertEquals(1234L, queue.startPositionMs)
        assertEquals(before, context.preferences.all)
        tree.selectionStore.clear()
        assertTrue(select(selected).mediaItems.isEmpty())
    }

    @Test
    fun `offline saved podcast folders retain aggregate actions and only available episodes`() {
        seed(Playlist(id = 42, name = "Offline saved podcast", browseId = "MPSPoffline", isYoutubePlaylist = false))
        onDatabase {
            listOf("downloaded", "unavailable").forEach {
                Database.songTable.upsert(Song.makePlaceholder(it).copy(title = it))
                Database.songPlaylistMapTable.map(it, 42)
            }
        }
        val completed = Download(
            DownloadRequest.Builder("downloaded", Uri.parse("https://example.test/downloaded")).build(),
            Download.STATE_COMPLETED, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE,
        )
        downloads.downloads = MutableStateFlow(mapOf("downloaded" to completed))
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        val groups = browse(AutoSessionConstants.ID_PLAYLISTS_YT)
        assertTrue(groups.any { it.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS })
        val podcasts = browse(AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS)
        assertEquals(2, podcasts.count { AutoMediaIdContract.isQueueAction(it.mediaId) })
        val podcast = podcasts.single { it.mediaMetadata.isBrowsable == true }
        assertEquals("playlist/42", podcast.mediaId)
        val rows = browse(podcast.mediaId).filter {
            it.mediaMetadata.isPlayable == true && !AutoMediaIdContract.isQueueAction(it.mediaId)
        }
        assertEquals(1, rows.size)
        assertEquals(listOf("downloaded"), select(rows.single()).mediaItems.map { it.mediaId })
    }

    @Test
    fun `Downloaded browse snapshots preserve both group directions through page two selection and queue cap`() {
        seed(Playlist(id = 42, name = "Downloaded ordering"))
        val songs = (0 until 205).map {
            Song(id = "song-$it", title = "Song ${it.toString().padStart(3, '0')}",
                durationText = null, thumbnailUrl = null)
        }
        onDatabase {
            songs.reversed().forEach {
                Database.songTable.upsert(it)
                Database.songPlaylistMapTable.map(it.id, 42)
            }
        }
        val completed = songs.filterIndexed { index, _ -> index % 2 == 1 }
        val missing = songs.filterIndexed { index, _ -> index % 2 == 0 }
        val downloadSnapshot = completed.associate { song ->
            song.id to Download(
                DownloadRequest.Builder(song.id, Uri.parse("https://example.test/${song.id}")).build(),
                Download.STATE_COMPLETED, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE,
            )
        }
        listOf(
            SortOrder.Ascending to (completed + missing),
            SortOrder.Descending to (missing + completed),
        ).forEach { (order, expected) ->
            tree.clearCache()
            tree.selectionStore.clear()
            downloads.downloads.value = downloadSnapshot
            context.preferences.edit()
                .putString("PlaylistSongsSortBy_42", PlaylistSongSortBy.Downloaded.name)
                .putString("PlaylistSongsSortOrder_42", order.name)
                .putString(maxSongsInQueueAndroidAutoKey, "50").commit()
            val before = context.preferences.all.toMap()
            val detail = browse("playlist/42")
            val pages = detail.filter { AutoMediaIdContract.parsePage(it.mediaId) != null }
            assertEquals(order.name, 2, pages.size)
            val rows = pages.flatMap { page ->
                browse(page.mediaId).filter {
                    it.mediaMetadata.isPlayable == true && !AutoMediaIdContract.isQueueAction(it.mediaId)
                }
            }
            assertEquals(order.name, expected.map { it.title }, rows.map { it.mediaMetadata.title.toString() })
            // Selection must retain the displayed order if download states change after browsing.
            downloads.downloads.value = emptyMap()
            val playAll = detail.single {
                AutoMediaIdContract.isQueueAction(it.mediaId) && !AutoMediaIdContract.isShuffle(it.mediaId)
            }
            assertEquals(order.name, expected.take(50).map { it.id }, select(playAll).mediaItems.map { it.mediaId })
            val queue = select(rows[202])
            assertEquals(order.name, expected.takeLast(50).map { it.id }, queue.mediaItems.map { it.mediaId })
            assertEquals(order.name, 47, queue.startIndex)
            assertEquals(order.name, expected[202].id, queue.mediaItems[queue.startIndex].mediaId)
            assertEquals(1234L, queue.startPositionMs)
            assertEquals(before, context.preferences.all)
        }
    }
}
