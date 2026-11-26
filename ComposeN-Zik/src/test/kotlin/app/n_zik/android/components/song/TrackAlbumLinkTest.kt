package app.n_zik.android.components.song

import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.models.Album
import app.n_zik.android.models.Song
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrackAlbumLinkTest {
    private val albums = mockk<AlbumTable>()
    private val albumMaps = mockk<SongAlbumMapTable>()
    private val song = Song.makePlaceholder("track-without-a-video-id")

    @Test
    fun `search uses the album title first and otherwise uses only displayed track metadata`() {
        assertEquals("Deluxe edition", trackAlbumSearchQuery("modified:Deluxe edition", "Song", "Artist"))
        assertEquals("Song Artist", trackAlbumSearchQuery("null", "Song", "Artist"))
        assertEquals("Song by Artist", trackAlbumSearchQuery(null, "Song by Artist", "Artist"))
        assertNull(trackAlbumSearchQuery(null, " ", null))
    }

    @Test
    fun `unresolved album navigation does not fall back to the offline song search`() {
        assertNull(trackMetadataSearchRoute("Deluxe edition", TrackMetadataSearch.Album, local = true))
    }

    @BeforeEach
    fun setUp() {
        mockkObject(Database)
        every { Database.albumTable } returns albums
        every { Database.songAlbumMapTable } returns albumMaps
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Database)
    }

    @Test
    fun `explicit edition remains usable before it is present in the library`() = runTest {
        coEvery { albumMaps.findAlbumsOfDirect(song.id) } returns emptyList()
        coEvery { albums.findByIdDirect("MPREdeluxe") } returns null

        val link = resolveTrackAlbumLink(song, "MPREdeluxe", "Deluxe")

        assertEquals("MPREdeluxe", link?.route)
        assertEquals("Deluxe", link?.album?.title)
    }

    @Test
    fun `ambiguous albums are ignored without an exact track match`() = runTest {
        coEvery { albumMaps.findAlbumsOfDirect(song.id) } returns listOf(
            Album("MPREstandard"), Album("MPREdeluxe")
        )

        assertNull(resolveTrackAlbumLink(song))
    }

    @Test
    fun `the stored manual edit resolves an otherwise ambiguous set`() = runTest {
        val manual = Album("modified:MPREdeluxe", "Deluxe")
        coEvery { albumMaps.findAlbumsOfDirect(song.id) } returns listOf(Album("MPREstandard"), manual)

        assertEquals(TrackAlbumLink(manual), resolveTrackAlbumLink(song))
    }

    @Test
    fun `the edited album wins over stale metadata for that edition`() = runTest {
        val manual = Album("modified:MPREdeluxe", "My Deluxe")
        coEvery { albumMaps.findAlbumsOfDirect(song.id) } returns listOf(Album("MPREstandard"), manual)

        assertEquals(TrackAlbumLink(manual), resolveTrackAlbumLink(song, "MPREdeluxe"))
    }

    @Test
    fun `the single saved correction wins over an unrelated initial edition`() = runTest {
        val manual = Album("modified:MPREreplacement", "Corrected title")
        coEvery { albumMaps.findAlbumsOfDirect(song.id) } returns listOf(manual)

        assertEquals(TrackAlbumLink(manual), resolveTrackAlbumLink(song, "MPREstale"))
    }
}
