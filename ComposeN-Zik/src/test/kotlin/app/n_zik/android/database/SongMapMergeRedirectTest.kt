package app.n_zik.android.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.DatabaseInitializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the MERGE redirection path of the song mapping DAOs
 * ([app.n_zik.android.core.database.SongArtistMapTable] /
 * [app.n_zik.android.core.database.SongAlbumMapTable]) using Robolectric
 * (JUnit 4 runner) with an in-memory database.
 *
 * Regression test: when merging an old song into an existing target song, the
 * naive `UPDATE ... SET songId` violated the composite primary key
 * (songId, artistId) whenever the target was already mapped to an artist the
 * old song also referenced — observed after a database restore as
 * `SQLiteConstraintException: UNIQUE constraint failed: SongArtistMap.songId,
 * SongArtistMap.artistId` (2026-09-25 log, match « Get album version »). The
 * fix purges the conflicting target rows first
 * ([app.n_zik.android.core.database.SongArtistMapTable.clearConflictingPairs]
 * / [app.n_zik.android.core.database.SongAlbumMapTable.clearConflictingPairs])
 * so the redirect yields the union of both songs' mappings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SongMapMergeRedirectTest {

    private lateinit var db: DatabaseInitializer

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun insertSong(id: String) {
        db.songTable.upsert(Song.makePlaceholder(id))
    }

    private fun linkArtist(songId: String, artistId: String) {
        db.artistTable.insertIgnore(Artist(id = artistId))
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId = songId, artistId = artistId))
    }

    private fun linkAlbum(songId: String, albumId: String, position: Int = -1) {
        db.albumTable.insertIgnore(Album(id = albumId))
        db.songAlbumMapTable.map(songId = songId, albumId = albumId, position = position)
    }

    private fun artistsOf(songId: String): Set<String> =
        db.songArtistMapTable.pairsBySongIdDirect(songId).map { it.artistId }.toSet()

    private fun albumsOf(songId: String): Set<String> =
        db.songAlbumMapTable.findAlbumsOfDirect(songId).map { it.id }.toSet()

    /**
     * Bug reproduction: the pre-fix MERGE path (redirect without purge) must
     * still throw the constraint violation — proves the test actually
     * exercises the failing condition.
     */
    @Test(expected = SQLiteConstraintException::class)
    fun `naive redirect without purge violates the composite primary key (bug reproduction)`() {
        insertSong("song_target")
        insertSong("song_old")
        linkArtist("song_target", "artist_A1")
        linkArtist("song_old", "artist_A1")
        linkArtist("song_old", "artist_A2")

        db.songArtistMapTable.updateSongId("song_old", "song_target")
    }

    /**
     * Album-side bug reproduction: same failing condition on the
     * (songId, albumId) composite primary key.
     */
    @Test(expected = SQLiteConstraintException::class)
    fun `naive album redirect without purge violates the composite primary key (bug reproduction)`() {
        insertSong("song_target")
        insertSong("song_old")
        linkAlbum("song_target", "album_AL1")
        linkAlbum("song_old", "album_AL1")
        linkAlbum("song_old", "album_AL2")

        db.songAlbumMapTable.updateSongId("song_old", "song_target")
    }

    /**
     * Exact scenario of the 2026-09-25 log: the target already maps artist_A1,
     * the old song maps artist_A1 + artist_A2. Purge + redirect must yield the
     * union {A1, A2} on the target with no exception.
     */
    @Test
    fun `merge redirect with overlapping artists keeps the union (log scenario)`() {
        insertSong("song_target")
        insertSong("song_old")
        linkArtist("song_target", "artist_A1")
        linkArtist("song_old", "artist_A1")
        linkArtist("song_old", "artist_A2")

        val purged = db.songArtistMapTable.clearConflictingPairs("song_old", "song_target")
        db.songArtistMapTable.updateSongId("song_old", "song_target")

        assertEquals(1, purged) // the source's own artist_A1 row
        assertEquals(setOf("artist_A1", "artist_A2"), artistsOf("song_target"))
        assertTrue(db.songArtistMapTable.pairsBySongIdDirect("song_old").isEmpty())
    }

    /**
     * Disjoint keys: the purge must not touch the target's own mappings.
     */
    @Test
    fun `merge redirect with disjoint artists preserves every mapping`() {
        insertSong("song_target")
        insertSong("song_old")
        linkArtist("song_target", "artist_A1")
        linkArtist("song_old", "artist_A2")

        val purged = db.songArtistMapTable.clearConflictingPairs("song_old", "song_target")
        db.songArtistMapTable.updateSongId("song_old", "song_target")

        assertEquals(0, purged)
        assertEquals(setOf("artist_A1", "artist_A2"), artistsOf("song_target"))
    }

    /**
     * Target without any mapping: nothing to purge, the redirect alone is
     * sufficient (previous behavior preserved).
     */
    @Test
    fun `merge redirect into a target without mappings only redirects`() {
        insertSong("song_target")
        insertSong("song_old")
        linkArtist("song_old", "artist_A1")

        val purged = db.songArtistMapTable.clearConflictingPairs("song_old", "song_target")
        db.songArtistMapTable.updateSongId("song_old", "song_target")

        assertEquals(0, purged)
        assertEquals(setOf("artist_A1"), artistsOf("song_target"))
    }

    /**
     * Same fix on the album side (composite PK (songId, albumId)).
     */
    @Test
    fun `merge redirect with overlapping albums keeps the union`() {
        insertSong("song_target")
        insertSong("song_old")
        linkAlbum("song_target", "album_AL1")
        linkAlbum("song_old", "album_AL1")
        linkAlbum("song_old", "album_AL2")

        val purged = db.songAlbumMapTable.clearConflictingPairs("song_old", "song_target")
        db.songAlbumMapTable.updateSongId("song_old", "song_target")

        assertEquals(1, purged) // the source's own album_AL1 row
        assertEquals(setOf("album_AL1", "album_AL2"), albumsOf("song_target"))
        assertEquals(0, db.songAlbumMapTable.deleteBySongId("song_old"))
    }

    /**
     * The target's curated track position must win over the source's when
     * both songs map the same album (the merge keeps the target's row).
     */
    @Test
    fun `merge redirect keeps the target song's own album position`() = runBlocking {
        insertSong("song_target")
        insertSong("song_old")
        linkAlbum("song_target", "album_AL1", position = 3)
        linkAlbum("song_old", "album_AL1", position = 7)

        db.songAlbumMapTable.clearConflictingPairs("song_old", "song_target")
        db.songAlbumMapTable.updateSongId("song_old", "song_target")

        assertEquals(3, db.songAlbumMapTable.findPositionOf("song_target").first())
        // the source's conflicting row was purged, not left behind as an orphan
        assertEquals(0, db.songAlbumMapTable.deleteBySongId("song_old"))
    }
}
