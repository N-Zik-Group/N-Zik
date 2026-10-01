package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showYtPlaylistsKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.handlers.AlbumDetailHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.AlbumsBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.ArtistDetailHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.ArtistsBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.PlaylistsBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.SongsBrowseHandler
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.kreate.android.me.knighthat.utils.getLocalSongs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
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

/**
 * Emitter-side pin of the Android Auto Rewind section and Disliked category:
 * the playlists root exposes a "Rewind" folder (gated on showMonthlyPlaylistsKey,
 * count = generated rewind-* playlists) whose sub-groups follow the phone
 * REWIND_PLAYLISTS_FILTER_KEY read-only, each listing only its own type with
 * the localized display name; the songs root exposes a "Disliked" folder whose
 * list is exactly likedAt == -1L, and the other song categories never list a
 * disliked song.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoBrowseRewindDislikedSectionsTest {

    private lateinit var context: Context
    // MyDownloadHelper is a Kotlin object with a `var downloads` property and an
    // explicit `fun getDownloads()` — under a mockkObject spy, ByteBuddy matches
    // the property getter to the explicit method (same name/params) and calls it,
    // which needs the real DownloadManager. So the object is used as-is: its
    // `downloads` flow is empty by default, and the DownloadManager is
    // pre-initialized once below (real Robolectric init, no download traffic).
    private val downloadHelper: MyDownloadHelper = MyDownloadHelper
    private val playlistsHandler = PlaylistsBrowseHandler()
    private val songsHandler = SongsBrowseHandler()
    private val albumsHandler = AlbumsBrowseHandler()
    private val artistsHandler = ArtistsBrowseHandler()
    private val albumDetailHandler = AlbumDetailHandler()
    private val artistDetailHandler = ArtistDetailHandler()

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        context = app
        // Pre-initialize the real DownloadManager (StandaloneDatabaseProvider +
        // SimpleCache under Robolectric) so the songs-root counts can call
        // getDownloadManager without first-time init mid-test.
        downloadHelper.getDownloadManager(context)
        // Start from a clean chip value: the default order must apply.
        context.preferences.edit().remove(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY).apply()
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
    }

    @After
    fun tearDown() {
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
    }

    /**
     * The shared [DatabaseInitializer.Instance] has no `allowMainThreadQueries()`,
     * so every DAO call must run off the main thread (same pattern as
     * `AutoBrowseDetailHandlersMediaIdTest`). runBlocking is forced by the
     * synchronous test API (AGENTS.md runBlocking exception).
     */
    private suspend fun <T> onDatabase(block: suspend () -> T): T = withContext(NzikDispatchers.DATA) { block() }

    /** Seeds one monthly, one yearly and one ordinary playlist; returns nothing. */
    private suspend fun seedRewindPlaylists() {
        onDatabase {
            Database.playlistTable.insertIgnore(Playlist(id = 1, name = "rewind-monthly:202609"))
            Database.playlistTable.insertIgnore(Playlist(id = 2, name = "rewind-yearly:2026"))
            Database.playlistTable.insertIgnore(Playlist(id = 3, name = "An ordinary playlist"))
        }
    }

    private suspend fun playlistsRoot(): List<androidx.media3.common.MediaItem> =
        onDatabase { playlistsHandler.getChildren(PlayerServiceModern.PLAYLIST, context, Database, downloadHelper, null) }

    private suspend fun songsRoot(): List<androidx.media3.common.MediaItem> =
        onDatabase { songsHandler.getChildren(PlayerServiceModern.SONG, context, Database, downloadHelper, null) }

    // ---------- Rewind: root folder ----------

    @Test
    fun `rewind folder appears in the playlists root between pinned and ytm with its count`() {
        runBlocking { seedRewindPlaylists() }
        val items = runBlocking { playlistsRoot() }
        val ids = items.map { it.mediaId }
        val rewind = items.first { it.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND }
        // Only the generated rewind-* playlists count (the ordinary one does not).
        assertEquals("2", rewind.mediaMetadata.subtitle)
        // Sibling folders must exist for the relative-position asserts to mean anything.
        assertTrue(ids.contains(AutoSessionConstants.ID_PLAYLISTS_PINNED))
        assertTrue(ids.contains(AutoSessionConstants.ID_PLAYLISTS_YT))
        assertTrue("Rewind must come after Pinned", ids.indexOf(AutoSessionConstants.ID_PLAYLISTS_REWIND) > ids.indexOf(AutoSessionConstants.ID_PLAYLISTS_PINNED))
        assertTrue("Rewind must come before YTM", ids.indexOf(AutoSessionConstants.ID_PLAYLISTS_REWIND) < ids.indexOf(AutoSessionConstants.ID_PLAYLISTS_YT))
    }

    @Test
    fun `rewind folder is absent when showMonthlyPlaylists is off`() {
        context.preferences.edit().putBoolean(showMonthlyPlaylistsKey, false).apply()
        runBlocking { seedRewindPlaylists() }
        val items = runBlocking { playlistsRoot() }
        assertTrue(items.none { it.mediaId == AutoSessionConstants.ID_PLAYLISTS_REWIND })
    }

    // ---------- Rewind: sub-group order follows the phone filter key ----------

    @Test
    fun `rewind sub-groups put the phone filter selection first and default to month`() {
        runBlocking { seedRewindPlaylists() }

        // Default (no chip written): canonical order with Month first.
        var ids = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_REWIND, context, Database, downloadHelper, null) }.map { it.mediaId }
        }
        assertEquals(
            listOf(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH, AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR, AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL),
            ids
        )

        // Phone chip = Year: Year first, the other two keep the canonical order.
        context.preferences.edit().putString(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, "Year").apply()
        ids = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_REWIND, context, Database, downloadHelper, null) }.map { it.mediaId }
        }
        assertEquals(
            listOf(AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR, AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH, AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL),
            ids
        )

        // Phone chip = All: All first.
        context.preferences.edit().putString(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, "All").apply()
        ids = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_REWIND, context, Database, downloadHelper, null) }.map { it.mediaId }
        }
        assertEquals(
            listOf(AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL, AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH, AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR),
            ids
        )

        // Corrupt value: read-only fallback to the default (Month first), no crash.
        context.preferences.edit().putString(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, "Bogus").apply()
        ids = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_REWIND, context, Database, downloadHelper, null) }.map { it.mediaId }
        }
        assertEquals(
            listOf(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH, AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR, AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL),
            ids
        )
    }

    // ---------- Rewind: sub-group content ----------

    @Test
    fun `rewind sub-groups list only their own playlists with localized names`() {
        runBlocking { seedRewindPlaylists() }

        val month = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH, context, Database, downloadHelper, null) }
        }
        // Shuffle first, then only the monthly playlist, localized.
        assertEquals(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE, month.first().mediaId)
        assertEquals(listOf("Rewind September 2026"), month.drop(1).map { it.mediaMetadata.title })
        assertEquals(listOf("playlist/1"), month.drop(1).map { it.mediaId })

        val year = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR, context, Database, downloadHelper, null) }
        }
        assertEquals(AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR_SHUFFLE, year.first().mediaId)
        assertEquals(listOf("Rewind 2026"), year.drop(1).map { it.mediaMetadata.title })
        assertEquals(listOf("playlist/2"), year.drop(1).map { it.mediaId })

        val all = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL, context, Database, downloadHelper, null) }
        }
        assertEquals(AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL_SHUFFLE, all.first().mediaId)
        // Monthly + yearly, but never the ordinary playlist.
        assertEquals(setOf("Rewind September 2026", "Rewind 2026"), all.drop(1).map { it.mediaMetadata.title }.toSet())
        assertEquals(setOf("playlist/1", "playlist/2"), all.drop(1).map { it.mediaId }.toSet())
    }

    @Test
    fun `empty rewind sub-group lists only its shuffle item without crashing`() {
        // No generated playlist at all: every sub-group is its bare shuffle item.
        val month = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH, context, Database, downloadHelper, null) }
        }
        assertEquals(listOf(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE), month.map { it.mediaId })
    }

    // ---------- Disliked: root folder + list ----------

    private suspend fun seedSongs() {
        // totalPlayTimeMs > 0 so the songs pass the "not hidden" filter.
        onDatabase {
            Database.songTable.upsert(Song(id = "ok-1", title = "OK 1", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
            Database.songTable.upsert(Song(id = "ok-2", title = "OK 2", durationText = null, thumbnailUrl = null, likedAt = 5L, totalPlayTimeMs = 1000))
            Database.songTable.upsert(Song(id = "dis-1", title = "Dis 1", durationText = null, thumbnailUrl = null, likedAt = -1L, totalPlayTimeMs = 1000))
            Database.songTable.upsert(Song(id = "dis-2", title = "Dis 2", durationText = null, thumbnailUrl = null, likedAt = -1L, totalPlayTimeMs = 1000))
        }
    }

    @Test
    fun `disliked folder follows the phone default order in the songs root with its count and phone icon`() {
        runBlocking { seedSongs() }
        val items = runBlocking { songsRoot() }
        val ids = items.map { it.mediaId }
        val disliked = items.first { it.mediaId == AutoSessionConstants.ID_SONGS_DISLIKED }
        assertEquals("2", disliked.mediaMetadata.subtitle)
        // Phone parity: the Disliked folder uses the phone's Disliked playlist icon.
        assertTrue(disliked.mediaMetadata.artworkUri?.toString()?.contains("heart_dislike") == true)
        // The order follows the in-app songs settings (songsDefaultOrder): all,
        // favorites, disliked, cached, downloaded, top, on_device — so by default
        // Disliked sits before Top and OnDevice (there is no fixed "after Top" spot).
        assertTrue(ids.contains(AutoSessionConstants.ID_SONGS_TOP))
        assertTrue(ids.contains(AutoSessionConstants.ID_SONGS_ONDEVICE))
        assertTrue("Disliked must come before Top (phone default order)", ids.indexOf(AutoSessionConstants.ID_SONGS_DISLIKED) < ids.indexOf(AutoSessionConstants.ID_SONGS_TOP))
        assertTrue("Disliked must come before OnDevice", ids.indexOf(AutoSessionConstants.ID_SONGS_DISLIKED) < ids.indexOf(AutoSessionConstants.ID_SONGS_ONDEVICE))
    }

    @Test
    fun `disliked folder lists exactly the disliked songs`() {
        runBlocking { seedSongs() }
        val items = runBlocking {
            onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_DISLIKED, context, Database, downloadHelper, null) }
        }
        assertEquals(AutoSessionConstants.ID_SONGS_DISLIKED_SHUFFLE, items.first().mediaId)
        assertEquals(setOf("SONGS_DISLIKED/dis-1", "SONGS_DISLIKED/dis-2"), items.drop(1).map { it.mediaId }.toSet())
    }

    @Test
    fun `all songs list excludes the disliked songs`() {
        runBlocking { seedSongs() }
        val items = runBlocking {
            onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_ALL, context, Database, downloadHelper, null) }
        }
        val ids = items.map { it.mediaId }
        assertTrue(ids.none { it.contains("dis-1") || it.contains("dis-2") })
        assertTrue(ids.any { it.contains("ok-1") })
        assertTrue(ids.any { it.contains("ok-2") })
        // The root counter mirrors the filtered list: only ok-1/ok-2 count.
        val all = runBlocking { songsRoot() }.first { it.mediaId == AutoSessionConstants.ID_SONGS_ALL }
        assertEquals("2", all.mediaMetadata.subtitle)
    }

    @Test
    fun `on-device list and root count exclude the disliked songs per phone DislikeMode`() {
        // The on-device root counter queries the DB for `local:` ids — the rows are
        // needed there; the list itself comes from getLocalSongs (a real MediaStore
        // query), which is stubbed below so no query actually runs. Local song ids
        // must carry a numeric MediaStore row id (asMediaItem parses it).
        val songOk = Song(id = "local:5", title = "Local OK", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000)
        val songDis = Song(id = "local:6", title = "Local DIS", durationText = null, thumbnailUrl = null, likedAt = -1L, totalPlayTimeMs = 1000)
        runBlocking { onDatabase { Database.songTable.upsert(songOk) } }
        runBlocking { onDatabase { Database.songTable.upsert(songDis) } }
        mockkStatic("app.kreate.android.me.knighthat.utils.OnDeviceMediaKt")
        try {
            every { any<Context>().getLocalSongs(any(), any()) } returns flowOf(mapOf(songOk to "/storage/emulated/0/Music/ok.mp3", songDis to "/storage/emulated/0/Music/dis.mp3"))

            // Default phone settings (songs DislikeMode Enabled): the disliked
            // local song is filtered out of both the list and the root counter.
            val items = runBlocking {
                onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_ONDEVICE, context, Database, downloadHelper, null) }
            }
            assertEquals(listOf(AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE, "SONGS_ONDEVICE/local:5"), items.map { it.mediaId })
            val onDevice = runBlocking { songsRoot() }.first { it.mediaId == AutoSessionConstants.ID_SONGS_ONDEVICE }
            assertEquals("1", onDevice.mediaMetadata.subtitle)

            // Phone settings: "exclude disliked" off -> both local songs come
            // back in the list and the counter.
            context.preferences.edit().putString(excludeDislikedSongsKey, DislikeMode.Disabled.name).apply()
            try {
                val items2 = runBlocking {
                    onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_ONDEVICE, context, Database, downloadHelper, null) }
                }
                assertEquals(setOf("SONGS_ONDEVICE/local:5", "SONGS_ONDEVICE/local:6"), items2.drop(1).map { it.mediaId }.toSet())
                val onDevice2 = runBlocking { songsRoot() }.first { it.mediaId == AutoSessionConstants.ID_SONGS_ONDEVICE }
                assertEquals("2", onDevice2.mediaMetadata.subtitle)
            } finally {
                context.preferences.edit().remove(excludeDislikedSongsKey).apply()
            }
        } finally {
            unmockkStatic("app.kreate.android.me.knighthat.utils.OnDeviceMediaKt")
        }
    }

    @Test
    fun `disliked songs reappear in the all songs list when phone DislikeMode is disabled`() {
        runBlocking { seedSongs() }
        // Phone settings: "exclude disliked" off -> the general categories keep them.
        context.preferences.edit().putString(excludeDislikedSongsKey, DislikeMode.Disabled.name).apply()
        try {
            val items = runBlocking {
                onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_ALL, context, Database, downloadHelper, null) }
            }
            val ids = items.map { it.mediaId }
            assertTrue(ids.any { it.contains("dis-1") })
            assertTrue(ids.any { it.contains("dis-2") })
            assertTrue(ids.any { it.contains("ok-1") })

            // The Disliked category itself is never filtered, in either mode.
            val disliked = runBlocking {
                onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_DISLIKED, context, Database, downloadHelper, null) }
            }
            assertEquals(setOf("SONGS_DISLIKED/dis-1", "SONGS_DISLIKED/dis-2"), disliked.drop(1).map { it.mediaId }.toSet())
        } finally {
            context.preferences.edit().remove(excludeDislikedSongsKey).apply()
        }
    }

    @Test
    fun `all songs list excludes songs of disliked albums and artists`() {
        runBlocking { seedDislikeContainers() }
        val ids = runBlocking {
            onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_ALL, context, Database, downloadHelper, null) }.map { it.mediaId }
        }
        // The disliked song, the song of the disliked artist and the song also
        // in a disliked album are all excluded; only s-ok remains.
        assertTrue(ids.none { it.endsWith("/s-dis") || it.endsWith("/s-art") || it.endsWith("/s-alb") })
        assertTrue(ids.any { it.endsWith("/s-ok") })

        // Disabling the albums and artists modes brings those two songs back,
        // while the songs mode still hides the disliked song.
        context.preferences.edit()
            .putString(excludeDislikedAlbumsKey, DislikeMode.Disabled.name)
            .putString(excludeDislikedArtistsKey, DislikeMode.Disabled.name)
            .apply()
        try {
            val ids2 = runBlocking {
                onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_ALL, context, Database, downloadHelper, null) }.map { it.mediaId }
            }
            assertTrue(ids2.any { it.endsWith("/s-art") })
            assertTrue(ids2.any { it.endsWith("/s-alb") })
            assertTrue(ids2.none { it.endsWith("/s-dis") })
        } finally {
            context.preferences.edit().remove(excludeDislikedAlbumsKey).remove(excludeDislikedArtistsKey).apply()
        }
    }

    // ---------- Albums / Artists: container hiding + detail triple filter ----------

    /**
     * Seeds the dislike fixtures shared by the album/artist tests:
     * s-ok (plain), s-dis (disliked song), s-art (by a disliked artist),
     * s-alb (in a disliked album) — all in the normal album LOCAL_ALBUM_OK;
     * s-ok/s-dis/s-alb by the normal artist, s-art by the disliked one.
     */
    private suspend fun seedDislikeContainers() {
        onDatabase {
            Database.songTable.upsert(Song(id = "s-ok", title = "S OK", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
            Database.songTable.upsert(Song(id = "s-dis", title = "S DIS", durationText = null, thumbnailUrl = null, likedAt = -1L, totalPlayTimeMs = 1000))
            Database.songTable.upsert(Song(id = "s-art", title = "S ART", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
            Database.songTable.upsert(Song(id = "s-alb", title = "S ALB", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
            Database.albumTable.insertIgnore(Album(id = "LOCAL_ALBUM_OK", title = "Album OK"))
            Database.albumTable.insertIgnore(Album(id = "LOCAL_ALBUM_DIS", title = "Album DIS", dislikedAt = 1L))
            Database.artistTable.insertIgnore(Artist(id = "LOCAL_ARTIST_OK", name = "Artist OK"))
            Database.artistTable.insertIgnore(Artist(id = "LOCAL_ARTIST_DIS", name = "Artist DIS", dislikedAt = 1L))
            Database.songAlbumMapTable.map("s-ok", "LOCAL_ALBUM_OK", 0)
            Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "s-ok", artistId = "LOCAL_ARTIST_OK"))
            Database.songAlbumMapTable.map("s-dis", "LOCAL_ALBUM_OK", 1)
            Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "s-dis", artistId = "LOCAL_ARTIST_OK"))
            Database.songAlbumMapTable.map("s-art", "LOCAL_ALBUM_OK", 2)
            Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "s-art", artistId = "LOCAL_ARTIST_DIS"))
            Database.songAlbumMapTable.map("s-alb", "LOCAL_ALBUM_OK", 3)
            Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "s-alb", artistId = "LOCAL_ARTIST_OK"))
            Database.songAlbumMapTable.map("s-alb", "LOCAL_ALBUM_DIS", 0)
        }
    }

    @Test
    fun `albums root hides disliked albums from library and favorites with matching counts`() {
        // allInLibrary JOINs the songs: every album needs a mapped song to be
        // a candidate at all (the disliked one must too, to prove the hiding).
        runBlocking {
            onDatabase {
                Database.songTable.upsert(Song(id = "a-1", title = "A1", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.songTable.upsert(Song(id = "a-2", title = "A2", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.songTable.upsert(Song(id = "a-3", title = "A3", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.songTable.upsert(Song(id = "a-4", title = "A4", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.albumTable.insertIgnore(Album(id = "ALB-OK", title = "OK"))
                Database.albumTable.insertIgnore(Album(id = "ALB-DIS", title = "DIS", dislikedAt = 1L))
                Database.albumTable.insertIgnore(Album(id = "ALB-FAV-OK", title = "FAV OK", bookmarkedAt = 1L))
                Database.albumTable.insertIgnore(Album(id = "ALB-FAV-DIS", title = "FAV DIS", bookmarkedAt = 1L, dislikedAt = 1L))
                Database.songAlbumMapTable.map("a-1", "ALB-OK", 0)
                Database.songAlbumMapTable.map("a-2", "ALB-DIS", 0)
                Database.songAlbumMapTable.map("a-3", "ALB-FAV-OK", 0)
                Database.songAlbumMapTable.map("a-4", "ALB-FAV-DIS", 0)
            }
        }
        val root = runBlocking { onDatabase { albumsHandler.getChildren(PlayerServiceModern.ALBUM, context, Database, downloadHelper, null) } }
        val library = root.first { it.mediaId == AutoSessionConstants.ID_ALBUMS_LIBRARY }
        val favorites = root.first { it.mediaId == AutoSessionConstants.ID_ALBUMS_FAVORITES }
        // The library holds every album with a playable song (bookmarked ones
        // included); only the disliked albums are hidden.
        assertEquals("2", library.mediaMetadata.subtitle)
        assertEquals("1", favorites.mediaMetadata.subtitle)

        val libraryList = runBlocking { onDatabase { albumsHandler.getChildren(AutoSessionConstants.ID_ALBUMS_LIBRARY, context, Database, downloadHelper, null) } }
        assertEquals(setOf("album/ALB-OK", "album/ALB-FAV-OK"), libraryList.drop(1).map { it.mediaId }.toSet())
        val favoritesList = runBlocking { onDatabase { albumsHandler.getChildren(AutoSessionConstants.ID_ALBUMS_FAVORITES, context, Database, downloadHelper, null) } }
        assertEquals(listOf("album/ALB-FAV-OK"), favoritesList.drop(1).map { it.mediaId })
    }

    @Test
    fun `artists root hides disliked artists from library and favorites with matching counts`() {
        // allInLibrary JOINs the songs: every artist needs a mapped song to be
        // a candidate at all (the disliked one must too, to prove the hiding).
        runBlocking {
            onDatabase {
                Database.songTable.upsert(Song(id = "x-1", title = "X1", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.songTable.upsert(Song(id = "x-2", title = "X2", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.songTable.upsert(Song(id = "x-3", title = "X3", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.songTable.upsert(Song(id = "x-4", title = "X4", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000))
                Database.artistTable.insertIgnore(Artist(id = "UC-OK", name = "OK"))
                Database.artistTable.insertIgnore(Artist(id = "UC-DIS", name = "DIS", dislikedAt = 1L))
                Database.artistTable.insertIgnore(Artist(id = "UC-FAV-OK", name = "FAV OK", bookmarkedAt = 1L))
                Database.artistTable.insertIgnore(Artist(id = "UC-FAV-DIS", name = "FAV DIS", bookmarkedAt = 1L, dislikedAt = 1L))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "x-1", artistId = "UC-OK"))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "x-2", artistId = "UC-DIS"))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "x-3", artistId = "UC-FAV-OK"))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "x-4", artistId = "UC-FAV-DIS"))
            }
        }
        val root = runBlocking { onDatabase { artistsHandler.getChildren(PlayerServiceModern.ARTIST, context, Database, downloadHelper, null) } }
        val library = root.first { it.mediaId == AutoSessionConstants.ID_ARTISTS_LIBRARY }
        val favorites = root.first { it.mediaId == AutoSessionConstants.ID_ARTISTS_FAVORITES }
        // The library holds every artist with a playable song (followed ones
        // included); only the disliked artists are hidden.
        assertEquals("2", library.mediaMetadata.subtitle)
        assertEquals("1", favorites.mediaMetadata.subtitle)

        val libraryList = runBlocking { onDatabase { artistsHandler.getChildren(AutoSessionConstants.ID_ARTISTS_LIBRARY, context, Database, downloadHelper, null) } }
        assertEquals(setOf("artist/UC-OK", "artist/UC-FAV-OK"), libraryList.drop(1).map { it.mediaId }.toSet())
        val favoritesList = runBlocking { onDatabase { artistsHandler.getChildren(AutoSessionConstants.ID_ARTISTS_FAVORITES, context, Database, downloadHelper, null) } }
        assertEquals(listOf("artist/UC-FAV-OK"), favoritesList.drop(1).map { it.mediaId })
    }

    @Test
    fun `album detail list shows every track (no triple filter, phone parity)`() {
        runBlocking { seedDislikeContainers() }
        // The detail list mirrors the phone's album detail, which shows every track —
        // no triple dislike filter (the phone filters only the playback queue, never
        // the detail list). All four tracks of the (non-disliked) album are listed.
        val items = runBlocking { onDatabase { albumDetailHandler.getChildren("album/LOCAL_ALBUM_OK", context, Database, downloadHelper, null) } }
        assertEquals(
            setOf("album/LOCAL_ALBUM_OK/s-ok", "album/LOCAL_ALBUM_OK/s-dis", "album/LOCAL_ALBUM_OK/s-art", "album/LOCAL_ALBUM_OK/s-alb"),
            items.map { it.mediaId }.toSet()
        )
    }

    @Test
    fun `disliked album detail list still lists its own tracks`() {
        runBlocking { seedDislikeContainers() }
        // Even though LOCAL_ALBUM_DIS is disliked, its detail still lists its own track
        // (s-alb) — the phone shows a disliked album's tracks; the triple filter only
        // hides it from the library lists and the playback queue, never its detail.
        val items = runBlocking { onDatabase { albumDetailHandler.getChildren("album/LOCAL_ALBUM_DIS", context, Database, downloadHelper, null) } }
        assertEquals(listOf("album/LOCAL_ALBUM_DIS/s-alb"), items.map { it.mediaId })
    }

    @Test
    fun `artist detail list shows every track (no triple filter, phone parity)`() {
        runBlocking { seedDislikeContainers() }
        // The detail list mirrors the phone's artist detail — every track of the
        // (non-disliked) artist is listed (s-ok, s-dis, s-alb); s-art belongs to the
        // other artist and is not part of this list.
        val items = runBlocking { onDatabase { artistDetailHandler.getChildren("artist/LOCAL_ARTIST_OK", context, Database, downloadHelper, null) } }
        assertEquals(
            setOf("artist/LOCAL_ARTIST_OK/s-ok", "artist/LOCAL_ARTIST_OK/s-dis", "artist/LOCAL_ARTIST_OK/s-alb"),
            items.map { it.mediaId }.toSet()
        )
    }

    @Test
    fun `disliked artist detail list still lists its own tracks`() {
        runBlocking { seedDislikeContainers() }
        // LOCAL_ARTIST_DIS is disliked, but its detail still lists its own track
        // (s-art) — same phone-parity reasoning as the disliked album case.
        val items = runBlocking { onDatabase { artistDetailHandler.getChildren("artist/LOCAL_ARTIST_DIS", context, Database, downloadHelper, null) } }
        assertEquals(listOf("artist/LOCAL_ARTIST_DIS/s-art"), items.map { it.mediaId })
    }

    // ---------- Playlists: YT gate + "All" folder inclusion ----------

    @Test
    fun `yt playlists folder is gated by the showYtPlaylists setting`() {
        runBlocking { seedRewindPlaylists() }
        // Default: the YT folder is present (playlists can come from imports, not only
        // live sync — the folder follows the showYtPlaylists setting alone).
        assertTrue(runBlocking { playlistsRoot() }.any { it.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT })

        context.preferences.edit().putBoolean(showYtPlaylistsKey, false).apply()
        try {
            assertFalse(runBlocking { playlistsRoot() }.any { it.mediaId == AutoSessionConstants.ID_PLAYLISTS_YT })
        } finally {
            context.preferences.edit().remove(showYtPlaylistsKey).apply()
        }
    }

    @Test
    fun `all playlists folder includes yt pinned and rewind with matching count`() {
        runBlocking {
            onDatabase {
                Database.playlistTable.insertIgnore(Playlist(id = 1, name = "YT One", browseId = "VL1", isYoutubePlaylist = true))
                Database.playlistTable.insertIgnore(Playlist(id = 2, name = "${PINNED_PREFIX}Pin"))
                Database.playlistTable.insertIgnore(Playlist(id = 3, name = "rewind-monthly:202609"))
                Database.playlistTable.insertIgnore(Playlist(id = 4, name = "An ordinary playlist"))
            }
        }
        // The root "All" (Library) counter includes every playlist — YT, pinned, rewind
        // and ordinary (the phone toggles for pinned/rewind default on).
        val all = runBlocking { playlistsRoot() }.first { it.mediaId == AutoSessionConstants.ID_PLAYLISTS_LOCAL }
        assertEquals("4", all.mediaMetadata.subtitle)

        // And the "All" folder lists every playlist — YT included (not only local).
        val ids = runBlocking {
            onDatabase { playlistsHandler.getChildren(AutoSessionConstants.ID_PLAYLISTS_LOCAL, context, Database, downloadHelper, null) }.map { it.mediaId }
        }
        assertTrue(ids.any { it.endsWith("/1") })
        assertTrue(ids.any { it.endsWith("/2") })
        assertTrue(ids.any { it.endsWith("/3") })
        assertTrue(ids.any { it.endsWith("/4") })
    }

    // ---------- Disliked: corrupt DislikeMode value ----------

    @Test
    fun `corrupt songs DislikeMode value falls back to enabled exclusion`() {
        runBlocking { seedSongs() }
        // "Bogus" is not a DislikeMode: getEnum falls back to the default (Enabled),
        // so the disliked songs stay excluded from the "All" list (no crash).
        context.preferences.edit().putString(excludeDislikedSongsKey, "Bogus").apply()
        try {
            val ids = runBlocking {
                onDatabase { songsHandler.getChildren(AutoSessionConstants.ID_SONGS_ALL, context, Database, downloadHelper, null) }.map { it.mediaId }
            }
            assertTrue(ids.none { it.contains("dis-1") || it.contains("dis-2") })
            assertTrue(ids.any { it.contains("ok-1") })
        } finally {
            context.preferences.edit().remove(excludeDislikedSongsKey).apply()
        }
    }
}
