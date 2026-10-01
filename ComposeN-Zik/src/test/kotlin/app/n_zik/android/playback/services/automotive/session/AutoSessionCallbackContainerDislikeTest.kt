package app.n_zik.android.playback.services.automotive.session

import android.content.Context
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AA unlike (dislike) container pin: the global DISLIKE_ALBUM / DISLIKE_ARTIST
 * session commands must toggle the phone's dislike flag of the LAST BROWSED
 * album/artist detail (AA has no per-item actions), and be a no-op when the
 * displayed container is of the other type.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoSessionCallbackContainerDislikeTest {

    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        context = app
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
        callback = AutoSessionCallback(context, Database, MyDownloadHelper)
    }

    @After
    fun tearDown() {
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
    }

    /**
     * The shared [DatabaseInitializer.Instance] has no `allowMainThreadQueries()`,
     * so every DAO call must run off the main thread (same pattern as
     * `AutoSessionCallbackSelectionTest`). runBlocking is forced by the
     * synchronous test API (AGENTS.md runBlocking exception).
     */
    private suspend fun <T> onDatabase(block: suspend () -> T): T = withContext(NzikDispatchers.DATA) { block() }

    /** Drives the browse path exactly like Android Auto does (on-demand query). */
    private fun browse(parentId: String) {
        callback.onGetChildren(
            mockk<MediaLibrarySession>(),
            mockk<MediaSession.ControllerInfo>(),
            parentId,
            0,
            0,
            null
        ).get()
    }

    /** Same on-demand browse path, returning the browsed children's media ids. */
    private fun browseChildren(parentId: String): List<String> {
        val result = callback.onGetChildren(
            mockk<MediaLibrarySession>(),
            mockk<MediaSession.ControllerInfo>(),
            parentId,
            0,
            0,
            null
        ).get()
        return result.value.orEmpty().map { it.mediaId }
    }

    @Test
    fun albumDislikeTogglesFlagAndReverts() {
        runBlocking {
            onDatabase { Database.albumTable.upsert(Album(id = "LOCAL_ALBUM_1", title = "A")) }
            browse("album/LOCAL_ALBUM_1")

            withContext(NzikDispatchers.DATA) { callback.toggleContainerDislike(PlayerServiceModern.ALBUM) }
            onDatabase { assertTrue(Database.albumTable.isDisliked("LOCAL_ALBUM_1").first()) }

            // Second tap removes the dislike (toggle semantics, like the phone).
            withContext(NzikDispatchers.DATA) { callback.toggleContainerDislike(PlayerServiceModern.ALBUM) }
            onDatabase { assertFalse(Database.albumTable.isDisliked("LOCAL_ALBUM_1").first()) }
        }
    }

    @Test
    fun artistDislikeTogglesFlag() {
        runBlocking {
            onDatabase { Database.artistTable.upsert(Artist(id = "LOCAL_ARTIST_1", name = "X")) }
            browse("artist/LOCAL_ARTIST_1")

            withContext(NzikDispatchers.DATA) { callback.toggleContainerDislike(PlayerServiceModern.ARTIST) }
            onDatabase { assertTrue(Database.artistTable.isDisliked("LOCAL_ARTIST_1").first()) }
        }
    }

    @Test
    fun artistSectionDrilldownStillTargetsTheArtist() {
        runBlocking {
            onDatabase { Database.artistTable.upsert(Artist(id = "LOCAL_ARTIST_2", name = "Y")) }
            // Artist drill-down section (artist/{id}/{section}) keeps the artist container.
            browse("artist/LOCAL_ARTIST_2/Top%20songs")

            withContext(NzikDispatchers.DATA) { callback.toggleContainerDislike(PlayerServiceModern.ARTIST) }
            onDatabase { assertTrue(Database.artistTable.isDisliked("LOCAL_ARTIST_2").first()) }
        }
    }

    @Test
    fun otherTypeCommandIsNoop() {
        runBlocking {
            onDatabase {
                Database.albumTable.upsert(Album(id = "LOCAL_ALBUM_2", title = "A"))
                Database.artistTable.upsert(Artist(id = "LOCAL_ARTIST_3", name = "X"))
            }
            browse("artist/LOCAL_ARTIST_3")

            // Displayed container is an artist: the album command must not touch
            // either the album or the artist.
            withContext(NzikDispatchers.DATA) { callback.toggleContainerDislike(PlayerServiceModern.ALBUM) }
            onDatabase {
                assertFalse(Database.albumTable.isDisliked("LOCAL_ALBUM_2").first())
                assertFalse(Database.artistTable.isDisliked("LOCAL_ARTIST_3").first())
            }

            browse("album/LOCAL_ALBUM_2")
            withContext(NzikDispatchers.DATA) { callback.toggleContainerDislike(PlayerServiceModern.ARTIST) }
            onDatabase {
                assertFalse(Database.albumTable.isDisliked("LOCAL_ALBUM_2").first())
                assertFalse(Database.artistTable.isDisliked("LOCAL_ARTIST_3").first())
            }
        }
    }

    @Test
    fun dislikedAlbumsCategoryListsOnlyDislikedAlbums() {
        runBlocking {
            onDatabase {
                Database.albumTable.upsert(Album(id = "LOCAL_ALBUM_9", title = "D", dislikedAt = 123L))
                Database.albumTable.upsert(Album(id = "LOCAL_ALBUM_8", title = "N"))
            }
            val children = browseChildren(AutoSessionConstants.ID_ALBUMS_DISLIKED)
            // The folder lists the disliked albums (plus its shuffle item), never
            // the neutral ones.
            assertTrue(children.any { it == "album/LOCAL_ALBUM_9" })
            assertFalse(children.any { it == "album/LOCAL_ALBUM_8" })
        }
    }

    @Test
    fun dislikedArtistsCategoryListsOnlyDislikedArtists() {
        runBlocking {
            onDatabase {
                Database.artistTable.upsert(Artist(id = "LOCAL_ARTIST_9", name = "D", dislikedAt = 123L))
                Database.artistTable.upsert(Artist(id = "LOCAL_ARTIST_8", name = "N"))
            }
            val children = browseChildren(AutoSessionConstants.ID_ARTISTS_DISLIKED)
            assertTrue(children.any { it == "artist/LOCAL_ARTIST_9" })
            assertFalse(children.any { it == "artist/LOCAL_ARTIST_8" })
        }
    }

    @Test
    fun albumLibraryListReflectsDislikeToggleOnRebrowse() {
        runBlocking {
            onDatabase {
                // Each album needs a mapped song to be a library candidate (the
                // library JOINs the songs).
                Database.songTable.upsert(Song(id = "c-1", title = "C1", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.songTable.upsert(Song(id = "c-2", title = "C2", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.albumTable.upsert(Album(id = "LOCAL_ALBUM_KEEP", title = "Keep"))
                Database.albumTable.upsert(Album(id = "LOCAL_ALBUM_HIDE", title = "Hide"))
                Database.songAlbumMapTable.map("c-1", "LOCAL_ALBUM_KEEP", 0)
                Database.songAlbumMapTable.map("c-2", "LOCAL_ALBUM_HIDE", 0)
            }
            // Both albums are visible in the library before the toggle.
            assertTrue(browseChildren(AutoSessionConstants.ID_ALBUMS_LIBRARY).any { it == "album/LOCAL_ALBUM_HIDE" })

            // Dislike LOCAL_ALBUM_HIDE via the container command (browse its detail
            // first to set currentContainer).
            browse("album/LOCAL_ALBUM_HIDE")
            withContext(NzikDispatchers.DATA) { callback.toggleContainerDislike(PlayerServiceModern.ALBUM) }

            // The next browse re-queries (AutoBrowseTree drops its cache on every
            // non-pagination browse) and no longer lists the disliked album — even
            // though the toggle no longer clears the cache explicitly.
            val after = browseChildren(AutoSessionConstants.ID_ALBUMS_LIBRARY)
            assertFalse(after.any { it == "album/LOCAL_ALBUM_HIDE" })
            assertTrue(after.any { it == "album/LOCAL_ALBUM_KEEP" })
        }
    }
}
