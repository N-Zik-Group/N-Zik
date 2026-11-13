package app.n_zik.android.core.ondevice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.models.Format
import app.n_zik.android.models.Song
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceTrackIndexTest {
    private lateinit var db: DatabaseInitializer

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After
    fun closeDatabase() {
        unmockkObject(DatabaseInitializer.Companion)
        db.close()
    }

    // JUnit 4 invokes these methods synchronously. The indexing transaction is suspending.
    @Test
    fun scansReuseGroupsWithoutDisplacingManualAlbumLinks() = runBlocking {
        val first = track("local:1", "Live", "Band", 2)
        val second = track("local:2", "LIVE", "BAND", 1)
        Database.songTable.insertIgnore(first.song)
        Database.albumTable.insertIgnore(Album("remote-album", title = "Live"))
        Database.songAlbumMapTable.map(first.song.id, "remote-album", 4)

        val (snapshot) = Database.indexDeviceTracks(listOf(first, second)) { true }
        val (repeated) = Database.indexDeviceTracks(listOf(second, first)) { true }

        assertEquals(snapshot, repeated)
        val localAlbumId = snapshot.albumIds.single()
        assertTrue(isDeviceAlbumId(localAlbumId))
        assertEquals(listOf(second.song.id, first.song.id), Database.songAlbumMapTable.allSongsOf(localAlbumId).first().map { it.id })
        assertEquals("remote-album", Database.albumTable.findBySongIdDirect(first.song.id)?.id)
        assertEquals(4, Database.songAlbumMapTable.findPositionOf(first.song.id).first())
        assertEquals(setOf(first.song.id, second.song.id), snapshot.artistSongIds.values.single())

        val (afterRemoval) = Database.indexDeviceTracks(listOf(first)) { true }
        assertEquals(setOf(first.song.id), afterRemoval.albumSongIds.getValue(localAlbumId))
        assertEquals(setOf(first.song.id), afterRemoval.artistSongIds.values.single())
    }

    // JUnit 4 invokes these methods synchronously. The indexing transaction is suspending.
    @Test
    fun changedTagsLeaveOldMappingsAndAnInvalidOwnerDoesNotWrite() = runBlocking {
        val first = track("local:1", "Live", "Band", 2)
        val (before) = Database.indexDeviceTracks(listOf(first)) { true }
        val (after) = Database.indexDeviceTracks(listOf(first.copy(album = "Studio"))) { true }

        val oldAlbum = before.albumIds.single()
        assertTrue(oldAlbum !in after.albumIds)
        assertEquals(listOf(first.song.id), Database.songAlbumMapTable.allSongsOf(oldAlbum).first().map { it.id })

        val blocked = track("local:2", "Other", "Another band", 1)
        val failure = runCatching { Database.indexDeviceTracks(listOf(blocked)) { false } }
        assertTrue(failure.isFailure)
        assertNull(Database.songTable.findByIdDirect(blocked.song.id))
    }

    // JUnit 4 invokes these methods synchronously. The indexing transaction is suspending.
    @Test
    fun rescanSplitsArtistCreditsAndKeepsManualLocalLinks() = runBlocking {
        val track = track("local:1", "Live", "Alpha & Beta, Gamma", 1)
        val combined = Artist(DEVICE_ARTIST_PREFIX + "combined", "Alpha & Beta, Gamma")
        val manual = Artist("LOCAL_ARTIST_manual", "Manual")
        Database.songTable.insertIgnore(track.song)
        listOf(combined, manual).forEach { artist ->
            Database.artistTable.insertIgnore(artist)
            Database.songArtistMapTable.insertIgnore(SongArtistMap(track.song.id, artist.id))
        }

        val (snapshot) = Database.indexDeviceTracks(listOf(track)) { true }
        val linked = Database.songArtistMapTable.findArtistsOfDirect(track.song.id)
        assertEquals(setOf("Alpha", "Beta", "Gamma", "Manual"), linked.map { it.name }.toSet())
        assertEquals(linked.map { it.id }.toSet(), snapshot.artistIds)
    }

    private fun track(id: String, album: String, artist: String, position: Int): IndexedDeviceTrack {
        val song = Song(id, id, artist, durationText = "01:00", thumbnailUrl = null)
        return IndexedDeviceTrack(song, Format(id, 0, "audio/flac", 0, 0, 0), album, artist, position)
    }
}
