package app.n_zik.android.core.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.migration.DbCleanup
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ManualTrackLinksTest {
    private lateinit var db: DatabaseInitializer
    private val track = Song.makePlaceholder("track")
    private val otherTrack = Song.makePlaceholder("other-track")

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java).build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After
    fun closeDatabase() {
        unmockkObject(DatabaseInitializer.Companion)
        db.close()
    }

    @Test
    fun `manual album link creates the track when it is missing from the library`() = runTest {
        val selected = Album("modified:MPRE_selected", title = "Selected")

        assertTrue(Database.linkTrackAlbumManually(track, selected))

        assertEquals(track.id, db.songTable.findByIdDirect(track.id)?.id)
        assertEquals(listOf(selected), db.songAlbumMapTable.findAlbumsOfDirect(track.id))
    }

    @Test
    fun `manual album link survives and keeps both existing editions`() = runTest {
        val original = Album("MPRE_original")
        val edition = Album("MPRE_edition")
        val selected = Album("modified:MPRE_selected", title = "Selected")
        Database.mapIgnore(original, track)
        Database.mapIgnore(edition, track)
        Database.mapIgnore(edition, otherTrack)

        assertTrue(Database.linkTrackAlbumManually(track, selected))
        assertTrue(Database.linkTrackAlbumManually(track, selected))

        assertEquals(setOf(original, edition, selected), db.songAlbumMapTable.findAlbumsOfDirect(track.id).toSet())
        assertEquals(listOf(edition), db.songAlbumMapTable.findAlbumsOfDirect(otherTrack.id))
        assertEquals(selected, Database.findEditedAlbumLink(track.id, "MPRE_selected"))
        assertNull(Database.findEditedAlbumLink(track.id, original.id))
    }

    @Test
    fun `manual artist link survives refresh with a co-artist and leaves other tracks intact`() = runTest {
        val canonical = Artist("UC_selected", name = "Selected")
        val coArtist = Artist("UC_other", name = "Co-artist")
        val selected = canonical.copy(id = "modified:${canonical.id}")
        Database.mapIgnore(canonical, track, otherTrack)
        Database.mapIgnore(coArtist, track)

        assertTrue(Database.linkTrackArtistManually(track, selected))
        Database.mapIgnore(canonical, track)
        Database.reconcileArtistLinks(track.id, listOf(canonical, coArtist))

        assertEquals(setOf(selected, coArtist), db.songArtistMapTable.findArtistsOfDirect(track.id).toSet())
        assertEquals(listOf(canonical), db.songArtistMapTable.findArtistsOfDirect(otherTrack.id))
    }

    @Test
    fun `startup cleanup keeps the selected artist when its display name differs`() = runTest {
        val selected = Artist("modified:UC_selected", name = "Selected Channel")
        val creditedTrack = track.copy(artistsText = "Different credit")

        assertTrue(Database.linkTrackArtistManually(creditedTrack, selected))
        val cleanup = DbCleanup.runClean(db.songTable, db.artistTable, db.songArtistMapTable)

        assertEquals(0, cleanup.removed)
        assertEquals(listOf(selected), db.songArtistMapTable.findArtistsOfDirect(track.id))
    }

    @Test
    fun `manual links reject device tracks and malformed remote IDs`() = runTest {
        val local = Song.makePlaceholder("local:42")
        assertFalse(Database.linkTrackAlbumManually(local, Album("modified:MPRE_remote")))
        assertFalse(Database.linkTrackArtistManually(local, Artist("modified:UC_remote")))
        assertFalse(Database.linkTrackAlbumManually(track, Album("modified:LOCAL_ALBUM_DEVICE_42")))
        assertFalse(Database.linkTrackArtistManually(track, Artist("modified:LOCAL_ARTIST_DEVICE_42")))
        assertFalse(Database.linkTrackAlbumManually(track, Album("modified:https://music.youtube.com")))
        assertFalse(Database.linkTrackArtistManually(track, Artist("modified:")))
        assertNull(db.songTable.findByIdDirect(track.id))
        assertNull(db.songTable.findByIdDirect(local.id))
    }

    @Test
    fun `selecting the same edited ID keeps the entities and track mappings`() = runTest {
        val album = Album("modified:MPRE_selected", title = "Selected")
        val artist = Artist("modified:UC_selected", name = "Selected")
        Database.mapIgnore(album, track)
        Database.mapIgnore(artist, track)

        assertTrue(Database.updateAlbumId(album.id, album.id))
        assertTrue(Database.updateArtistId(artist.id, artist.id))

        assertEquals(listOf(album), db.songAlbumMapTable.findAlbumsOfDirect(track.id))
        assertEquals(listOf(artist), db.songArtistMapTable.findArtistsOfDirect(track.id))
    }

    @Test
    fun `rebinding existing IDs merges overlapping links and keeps the target track position`() = runTest {
        val sourceAlbum = Album("MPRE_original")
        val targetAlbum = Album("MPRE_selected")
        val sourceArtist = Artist("UC_original")
        val targetArtist = Artist("UC_selected")
        Database.mapIgnore(sourceAlbum, track, position = 5)
        Database.mapIgnore(sourceAlbum, otherTrack, position = 2)
        Database.mapIgnore(targetAlbum, track, position = 1)
        Database.mapIgnore(sourceArtist, track, otherTrack)
        Database.mapIgnore(targetArtist, track)

        assertTrue(Database.updateAlbumId(sourceAlbum.id, targetAlbum.id))
        assertTrue(Database.updateArtistId(sourceArtist.id, targetArtist.id))

        assertNull(db.albumTable.findByIdDirect(sourceAlbum.id))
        assertNull(db.artistTable.findByIdDirect(sourceArtist.id))
        assertEquals(listOf(track.id, otherTrack.id), db.songAlbumMapTable.allSongsOfDirect(targetAlbum.id).map(Song::id))
        assertEquals(listOf(targetArtist), db.songArtistMapTable.findArtistsOfDirect(track.id))
        assertEquals(listOf(targetArtist), db.songArtistMapTable.findArtistsOfDirect(otherTrack.id))
    }

    @Test
    fun `two origins cannot merge into the same modified target`() = runTest {
        val album = Album("MPRE_original")
        val modifiedAlbum = Album("modified:MPRE_target")
        val artist = Artist("UC_original")
        val modifiedArtist = Artist("modified:UC_target")
        Database.mapIgnore(album, track)
        Database.mapIgnore(modifiedAlbum, otherTrack)
        Database.mapIgnore(artist, track)
        Database.mapIgnore(modifiedArtist, otherTrack)

        assertFalse(Database.updateAlbumId(album.id, modifiedAlbum.id))
        assertFalse(Database.updateArtistId(artist.id, modifiedArtist.id))
        assertEquals(listOf(album), db.songAlbumMapTable.findAlbumsOfDirect(track.id))
        assertEquals(listOf(modifiedAlbum), db.songAlbumMapTable.findAlbumsOfDirect(otherTrack.id))
        assertEquals(listOf(artist), db.songArtistMapTable.findArtistsOfDirect(track.id))
        assertEquals(listOf(modifiedArtist), db.songArtistMapTable.findArtistsOfDirect(otherTrack.id))
    }

    @Test
    fun `track edits only rebind verified associations and can link a selected target otherwise`() = runTest {
        val originalAlbum = Album("MPRE_suggested")
        val originalArtist = Artist("UC_suggested")
        val selectedAlbum = Album("modified:MPRE_selected", title = "Selected")
        val selectedArtist = Artist("modified:UC_selected", name = "Selected")
        Database.mapIgnore(originalAlbum, otherTrack)
        Database.mapIgnore(originalArtist, otherTrack)

        assertFalse(Database.updateAlbumId(originalAlbum.id, selectedAlbum.id, originalAlbum, track.id))
        assertFalse(Database.updateArtistId(originalArtist.id, selectedArtist.id, originalArtist, track.id))
        assertNull(db.albumTable.findByIdDirect(selectedAlbum.id))
        assertNull(db.artistTable.findByIdDirect(selectedArtist.id))

        assertTrue(Database.linkTrackAlbumManually(track, selectedAlbum))
        assertTrue(Database.linkTrackArtistManually(track, selectedArtist))
        assertEquals(listOf(selectedAlbum), db.songAlbumMapTable.findAlbumsOfDirect(track.id))
        assertEquals(listOf(selectedArtist), db.songArtistMapTable.findArtistsOfDirect(track.id))
        assertEquals(listOf(originalAlbum), db.songAlbumMapTable.findAlbumsOfDirect(otherTrack.id))
        assertEquals(listOf(originalArtist), db.songArtistMapTable.findArtistsOfDirect(otherTrack.id))
    }

    @Test
    fun `replacing shared links changes only the selected track and preserves stored edits`() = runTest {
        val oldAlbum = Album("MPRE_old", "Old album")
        val oldArtist = Artist("UC_old", "Old artist")
        val selectedAlbum = Album("modified:MPRE_new", "modified:Custom album")
        val selectedArtist = Artist("modified:UC_new", "modified:Custom artist")
        val compAlbum = Album("MPRE_compilation", "Compilation")
        val coArtist = Artist("UC_feature", "Featured artist")
        Database.mapIgnore(oldAlbum, track, position = 7)
        Database.mapIgnore(oldAlbum, otherTrack, position = 2)
        Database.mapIgnore(oldArtist, track, otherTrack)
        Database.mapIgnore(compAlbum, track)
        Database.mapIgnore(coArtist, track)
        Database.mapIgnore(selectedAlbum, otherTrack)
        Database.mapIgnore(selectedArtist, otherTrack)

        assertTrue(Database.linkTrackAlbumManually(track, selectedAlbum.copy(title = "Search result"), oldAlbum.id))
        assertTrue(Database.linkTrackArtistManually(track, selectedArtist.copy(name = "Search result"), oldArtist.id))

        assertEquals(setOf(selectedAlbum, compAlbum), db.songAlbumMapTable.findAlbumsOfDirect(track.id).toSet())
        assertEquals(setOf(selectedArtist, coArtist), db.songArtistMapTable.findArtistsOfDirect(track.id).toSet())
        assertEquals(setOf(oldAlbum, selectedAlbum), db.songAlbumMapTable.findAlbumsOfDirect(otherTrack.id).toSet())
        assertEquals(setOf(oldArtist, selectedArtist), db.songArtistMapTable.findArtistsOfDirect(otherTrack.id).toSet())
        assertEquals(7, db.songAlbumMapTable.findPairPosition(track.id, selectedAlbum.id))
        assertEquals(2, db.songAlbumMapTable.findPairPosition(otherTrack.id, oldAlbum.id))
    }

    @Test
    fun `resetting to the original ID leaves the shared manual entity and its other links`() = runTest {
        val album = Album("MPRE_original")
        val artist = Artist("UC_original")
        val editedAlbum = Album("modified:MPRE_changed")
        val editedArtist = Artist("modified:UC_changed")
        Database.mapIgnore(editedAlbum, track)
        Database.mapIgnore(editedAlbum, otherTrack)
        Database.mapIgnore(editedArtist, track, otherTrack)

        assertTrue(Database.linkTrackAlbumManually(track, album, editedAlbum.id))
        assertTrue(Database.linkTrackArtistManually(track, artist, editedArtist.id))

        assertEquals(listOf(album), db.songAlbumMapTable.findAlbumsOfDirect(track.id))
        assertEquals(listOf(artist), db.songArtistMapTable.findArtistsOfDirect(track.id))
        assertEquals(listOf(editedAlbum), db.songAlbumMapTable.findAlbumsOfDirect(otherTrack.id))
        assertEquals(listOf(editedArtist), db.songArtistMapTable.findArtistsOfDirect(otherTrack.id))
    }
}
