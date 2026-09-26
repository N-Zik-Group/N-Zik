package app.n_zik.android.core.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract for the name convergence ([NameConvergence]) against an in-memory
 * Room database: [NameConvergence.propagateRename] rewrites only the copies
 * linked to the renamed row (whole-token agreement, `modified:` prefix kept,
 * divergent and unlinked copies untouched, a prefixed new name cleaned before
 * embedding), and [NameConvergence.runSweep] re-converges the copies from the
 * links with its guards (a copy is rewritten with the COMPLETE list of linked
 * row names — cleaned+trimmed, distinct case-insensitively, link order — when
 * every copy name is backed by a linked row (whole token, or each part of a
 * duet "A & B" with the spaced separator): repairs prefix leaks, duets,
 * duplicates and completes partial lists — a name without a linked id skips the
 * copy, custom `modified:` copies untouched, unlinked copies untouched, albums
 * judged by the shared set of their songs' links) — a converged database
 * yields zero writes, and a second run over a converged database writes
 * nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NameConvergenceDbTest {

    private lateinit var db: DatabaseInitializer

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        // Redirect the lazy singleton to the in-memory database so
        // [NameConvergence.propagateRename] (which takes the [Database] object)
        // runs its real code against the DAOs under test.
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After
    fun closeDb() {
        unmockkAll()
        db.close()
    }

    // ---- fixtures ----

    private fun insertSong(id: String, artistsText: String?) {
        db.songTable.upsert(
            Song(
                id = id,
                title = "Song $id",
                artistsText = artistsText,
                durationText = null,
                thumbnailUrl = null,
            )
        )
    }

    private fun insertArtist(id: String, name: String) {
        db.artistTable.upsert(Artist(id = id, name = name))
    }

    private fun insertLink(songId: String, artistId: String) {
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId, artistId))
    }

    private fun insertAlbum(id: String, authorsText: String?) {
        db.albumTable.upsert(Album(id = id, title = "Album $id", authorsText = authorsText))
    }

    private fun linkSongToAlbum(songId: String, albumId: String, position: Int = 0) {
        db.songAlbumMapTable.upsert(listOf(SongAlbumMap(songId, albumId, position)))
    }

    private fun songArtists(id: String): String? = db.songTable.findByIdDirect(id)?.artistsText

    private fun albumAuthors(id: String): String? = db.albumTable.findByIdDirect(id)?.authorsText

    /** Runs the real sweep core against the in-memory DAOs. */
    private suspend fun sweep(): Int =
        NameConvergence.runSweep(
            songTable = db.songTable,
            artistTable = db.artistTable,
            albumTable = db.albumTable,
            songArtistMapTable = db.songArtistMapTable,
            songAlbumMapTable = db.songAlbumMapTable,
        )

    // ---- propagateRename (end to end) ----

    @Test
    fun propagateRenameRewritesOnlyLinkedAgreeingCopies() {
        insertArtist("UC1", "Aran")
        insertArtist("UC2", "B")
        insertSong("s1", "Aran, B")
        insertLink("s1", "UC1")
        insertLink("s1", "UC2")
        insertSong("s2", "Aran")
        insertLink("s2", "UC1")
        // divergent copy: token differs from the row's name -> left alone
        insertSong("s3", "modified:Aran One")
        insertLink("s3", "UC1")
        // agreeing custom copy: the modified: prefix is kept on replace
        insertSong("s4", "modified:Aran")
        insertLink("s4", "UC1")
        // unlinked copy: the row's rename does not reach it
        insertSong("s5", "Aran")
        insertAlbum("al1", "Aran")
        linkSongToAlbum("s1", "al1")
        // divergent album copy -> left alone
        insertAlbum("al2", "Aran One")
        linkSongToAlbum("s1", "al2")

        val changed = NameConvergence.propagateRename(Database, "UC1", "Aran", "Aran (UK)")

        assertEquals(4, changed) // s1, s2, s4, al1
        assertEquals("Aran (UK), B", songArtists("s1"))
        assertEquals("Aran (UK)", songArtists("s2"))
        assertEquals("modified:Aran One", songArtists("s3"))
        assertEquals("modified:Aran (UK)", songArtists("s4"))
        assertEquals("Aran", songArtists("s5"))
        assertEquals("Aran (UK)", albumAuthors("al1"))
        assertEquals("Aran One", albumAuthors("al2"))
    }

    @Test
    fun propagateRenameIsANoOpWhenNothingAgrees() {
        insertArtist("UC1", "Aran (UK)") // the row was already renamed
        insertSong("s1", "Aran (UK)")
        insertLink("s1", "UC1")

        val changed = NameConvergence.propagateRename(Database, "UC1", "Aran", "Aran (UK)")

        assertEquals(0, changed) // no token equals the old name anymore: no write
        assertEquals("Aran (UK)", songArtists("s1"))
    }

    @Test
    fun propagateRenameWithAPrefixedNewNameYieldsCleanCopies() {
        // a prefixed row name must never reach a copy: no "modified:" at a token
        // position (cleanPrefix only cuts at the head of a copy)
        insertArtist("UC1", "Aran")
        insertSong("s1", "Aran, B")
        insertLink("s1", "UC1")
        insertAlbum("al1", "Aran")
        linkSongToAlbum("s1", "al1")

        val changed =
            NameConvergence.propagateRename(Database, "UC1", "Aran", "modified:Hatsune Miku")

        assertEquals(2, changed) // s1, al1
        assertEquals("Hatsune Miku, B", songArtists("s1"))
        assertEquals("Hatsune Miku", albumAuthors("al1"))
    }

    @Test
    fun propagateRenameTrimsTrailingSpacesInTheRowNames() {
        // the live database has row names with trailing spaces ("RoughSketch  "):
        // the agreement rule (old name) and the replacement (new name) must be
        // trimmed, otherwise the rename silently reaches zero copies
        insertArtist("UC1", "Aran  ")
        insertSong("s1", "Aran, B")
        insertLink("s1", "UC1")

        val changed = NameConvergence.propagateRename(Database, "UC1", "Aran", "Aran (UK)  ")

        assertEquals(1, changed)
        assertEquals("Aran (UK), B", songArtists("s1"))
    }

    // ---- runSweep (end to end) ----

    @Test
    fun sweepOnAConvergedDatabaseWritesNothing() = runTest {
        insertArtist("UC1", "Aran")
        insertArtist("UC2", "B")
        insertSong("s1", "Aran")
        insertLink("s1", "UC1")
        insertSong("s2", "Aran, B")
        insertLink("s2", "UC1")
        insertLink("s2", "UC2")
        insertAlbum("al1", "Aran")
        linkSongToAlbum("s1", "al1")

        assertEquals(0, sweep())

        assertEquals("Aran", songArtists("s1"))
        assertEquals("Aran, B", songArtists("s2"))
        assertEquals("Aran", albumAuthors("al1"))
    }

    @Test
    fun sweepConvergesCaseDriftFromTheLinkedRow() = runTest {
        insertArtist("UC1", "Aran")
        insertSong("s1", "ARAN")
        insertLink("s1", "UC1")

        assertEquals(1, sweep())

        assertEquals("Aran", songArtists("s1"))
    }

    @Test
    fun sweepSkipsACopyWithAnUnbackedName() = runTest {
        // NoAki has no linked row (missing id): the whole copy is skipped, the
        // playback reconcile repairs the link later
        insertArtist("UC1", "Aran")
        insertSong("s1", "Aran, NoAki")
        insertLink("s1", "UC1")

        assertEquals(0, sweep())

        assertEquals("Aran, NoAki", songArtists("s1"))
    }

    @Test
    fun sweepRepairsAMidStringPrefixLeak() = runTest {
        // matrix: copy "Yunosuke, modified:Hatsune Miku" (leaked prefix at a
        // token position) linked to both rows -> cleaned full list
        insertArtist("UC1", "Yunosuke")
        insertArtist("UC2", "modified:Hatsune Miku")
        insertSong("s1", "Yunosuke, modified:Hatsune Miku")
        insertLink("s1", "UC1")
        insertLink("s1", "UC2")

        assertEquals(1, sweep())

        assertEquals("Yunosuke, Hatsune Miku", songArtists("s1"))
    }

    @Test
    fun sweepAddsMissingLinkedNamesToAPartialList() = runTest {
        // matrix: copy "Aran", links "Aran" + "B" (both valid) -> completed list
        insertArtist("UC1", "Aran")
        insertArtist("UC2", "B")
        insertSong("s1", "Aran")
        insertLink("s1", "UC1")
        insertLink("s1", "UC2")

        assertEquals(1, sweep())

        assertEquals("Aran, B", songArtists("s1"))
    }

    @Test
    fun sweepWipesADuetAndRewritesTheCommaList() = runTest {
        // matrix: copy "Miyamori Bungaku & Hatsune Miku" (one duet token, spaced
        // " & " separator), links "Miyamori Bungaku" + "modified:Hatsune Miku"
        // -> wipe + full comma list (end to end, row names from the DAOs)
        insertArtist("UC_M", "Miyamori Bungaku")
        insertArtist("UC_H", "modified:Hatsune Miku")
        insertSong("s1", "Miyamori Bungaku & Hatsune Miku")
        insertLink("s1", "UC_M")
        insertLink("s1", "UC_H")

        assertEquals(1, sweep())

        assertEquals("Miyamori Bungaku, Hatsune Miku", songArtists("s1"))
    }

    @Test
    fun sweepWritesADuplicatedRowNameOnlyOnce() = runTest {
        // matrix: two linked rows both named "BlackY" (distinct browse ids),
        // copy "BlackY, BlackY" already polluted by a previous complete-list
        // write -> repaired down to the single name (distinct ignoreCase)
        insertArtist("UC_B1", "BlackY")
        insertArtist("UC_B2", "BlackY")
        insertSong("s1", "BlackY, BlackY")
        insertLink("s1", "UC_B1")
        insertLink("s1", "UC_B2")

        assertEquals(1, sweep())

        assertEquals("BlackY", songArtists("s1"))
        // idempotent: a second sweep over the repaired copy writes nothing
        assertEquals(0, sweep())
    }

    @Test
    fun sweepIgnoresCustomCopies() = runTest {
        insertArtist("UC1", "Aran (UK)")
        insertSong("s1", "modified:Aran")
        insertLink("s1", "UC1")

        assertEquals(0, sweep())

        assertEquals("modified:Aran", songArtists("s1"))
    }

    @Test
    fun sweepIgnoresCopiesWithoutAnyLink() = runTest {
        insertArtist("UC1", "Aran")
        insertSong("s1", "Aran") // no SongArtistMap row for s1

        assertEquals(0, sweep())

        assertEquals("Aran", songArtists("s1"))
    }

    @Test
    fun sweepConvergesAnAlbumFromTheNamesSharedByAllItsSongs() = runTest {
        insertArtist("UC1", "Aran (UK)")
        for (id in listOf("s1", "s2", "s3")) {
            insertSong(id, "Aran (UK)")
            insertLink(id, "UC1")
        }
        insertAlbum("al1", "Aran")
        linkSongToAlbum("s1", "al1")
        linkSongToAlbum("s2", "al1", position = 1)
        linkSongToAlbum("s3", "al1", position = 2)

        assertEquals(1, sweep()) // only the album copy is stale

        assertEquals("Aran (UK)", albumAuthors("al1"))
    }

    @Test
    fun sweepIgnoresAnAlbumWhoseSongsDoNotShareTheSameArtistSet() = runTest {
        insertArtist("UC1", "Aran (UK)")
        insertArtist("UC2", "B")
        insertSong("s1", "Aran (UK)")
        insertLink("s1", "UC1")
        insertSong("s2", "Aran (UK)")
        insertLink("s2", "UC1")
        insertSong("s3", "Aran (UK), B") // feature: the set differs
        insertLink("s3", "UC1")
        insertLink("s3", "UC2")
        insertAlbum("al1", "Aran")
        linkSongToAlbum("s1", "al1")
        linkSongToAlbum("s2", "al1", position = 1)
        linkSongToAlbum("s3", "al1", position = 2)

        assertEquals(0, sweep())

        assertEquals("Aran", albumAuthors("al1"))
    }

    @Test
    fun sweepSilentlyIgnoresAnAlbumWithAnUnlinkedSong() = runTest {
        insertArtist("UC1", "Aran (UK)")
        insertSong("s1", "Aran (UK)")
        insertLink("s1", "UC1")
        insertSong("s2", "Aran (UK)") // linked to no artist
        insertAlbum("al1", "Aran")
        linkSongToAlbum("s1", "al1")
        linkSongToAlbum("s2", "al1", position = 1)

        assertEquals(0, sweep())

        assertEquals("Aran", albumAuthors("al1"))
    }

    @Test
    fun sweepIsIdempotent() = runTest {
        insertArtist("UC1", "Aran")
        insertSong("s1", "ARAN")
        insertLink("s1", "UC1")

        assertEquals(1, sweep())
        assertEquals("Aran", songArtists("s1"))
        assertEquals(0, sweep()) // converged: the second run writes nothing
        assertEquals("Aran", songArtists("s1"))
    }
}
