package app.n_zik.android.playback.services.automotive.browse

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.handlers.AlbumDetailHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.ArtistDetailHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.PlaylistDetailHandler
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test-only application: MainApplication.onCreate() migrates credentials via
 * AndroidKeyStore (MasterKey), which is not available on the Robolectric JVM.
 * MainApplication is final, so Dependencies.application is a mock wired to
 * this app's context (same pattern as `MediaItemUtilsTest`).
 */
class TestApplication : Application()

/**
 * Issue #777 — emitting-side pin of the mediaId contract: the browse detail
 * handlers must emit detail-page songs with the FULL container context
 * ("{prefix}/{containerId}/{songId}") and the playlist shuffle item with the
 * playlist id ("PLAYLIST_SHUFFLE/{playlistId}").
 *
 * `AutoMediaIdContractTest` only pins the PARSER against hand-written literals;
 * without this class, a handler regressing to the pre-fix bare prefix (e.g.
 * `mapSongToMediaItem(song, parts[0])`) would leave every other test green while
 * reproducing "Unable to perform the selection" on the head unit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoBrowseDetailHandlersMediaIdTest {

    private lateinit var context: Context
    private val downloadHelper: MyDownloadHelper = mockk(relaxed = true)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        context = app
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
    }

    @After
    fun tearDown() {
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
    }

    /**
     * The shared [DatabaseInitializer.Instance] has no `allowMainThreadQueries()`,
     * so every DAO call must run off the main thread (same pattern as
     * `MediaItemUtilsTest`). runBlocking is forced by the synchronous test API
     * (AGENTS.md runBlocking exception).
     */
    private suspend fun <T> onDatabase(block: suspend () -> T): T = withContext(NzikDispatchers.DATA) { block() }

    @Test
    fun `local album detail emits container-prefixed song mediaIds`() {
        runBlocking {
            onDatabase {
                // The album row itself is required by the SongAlbumMap foreign key.
                Database.albumTable.insertIgnore(Album(id = "LOCAL_ALBUM_1"))
                Database.songTable.upsert(Song.makePlaceholder("alb-a"))
                Database.songTable.upsert(Song.makePlaceholder("alb-b"))
                Database.songAlbumMapTable.map("alb-a", "LOCAL_ALBUM_1", 0)
                Database.songAlbumMapTable.map("alb-b", "LOCAL_ALBUM_1", 1)
            }
        }
        val items = runBlocking {
            onDatabase { AlbumDetailHandler().getChildren("album/LOCAL_ALBUM_1", context, Database, downloadHelper, null) }
        }
        assertEquals(listOf("album/LOCAL_ALBUM_1/alb-a", "album/LOCAL_ALBUM_1/alb-b"), items.map { it.mediaId })
    }

    @Test
    fun `local artist detail emits container-prefixed song mediaIds`() {
        runBlocking {
            onDatabase {
                Database.songTable.upsert(Song.makePlaceholder("art-a"))
                Database.artistTable.insertIgnore(Artist(id = "local:5"))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "art-a", artistId = "local:5"))
            }
        }
        val items = runBlocking {
            onDatabase { ArtistDetailHandler().getChildren("artist/local:5", context, Database, downloadHelper, null) }
        }
        assertEquals(listOf("artist/local:5/art-a"), items.map { it.mediaId })
    }

    @Test
    fun `local playlist detail emits the shuffle item with the playlist id`() {
        runBlocking {
            onDatabase {
                Database.playlistTable.insertIgnore(Playlist(id = 777, name = "PL 777"))
                Database.songTable.upsert(Song.makePlaceholder("pl-a"))
                Database.songTable.upsert(Song.makePlaceholder("pl-b"))
                Database.songPlaylistMapTable.map("pl-a", 777)
                Database.songPlaylistMapTable.map("pl-b", 777)
            }
        }
        val items = runBlocking {
            onDatabase { PlaylistDetailHandler().getChildren("playlist/777", context, Database, downloadHelper, null) }
        }
        // The shuffle item carries the playlist id so the resolver can find the right list.
        assertEquals(AutoMediaIdContract.playlistShuffle("777"), items.first().mediaId)
        // The songs carry the full playlist context.
        assertEquals(setOf("playlist/777/pl-a", "playlist/777/pl-b"), items.drop(1).map { it.mediaId }.toSet())
    }
}
