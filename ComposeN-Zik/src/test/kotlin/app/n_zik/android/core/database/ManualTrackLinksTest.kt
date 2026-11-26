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
}
