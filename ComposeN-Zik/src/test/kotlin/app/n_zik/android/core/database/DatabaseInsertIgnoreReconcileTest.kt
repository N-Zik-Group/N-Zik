package app.n_zik.android.core.database

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.utils.asSong
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Contract for artist↔song mapping reconciliation in [Database.insertIgnore]
 * (bug 2026-09-25, confirmed against a DB export: YTM author lists vary by
 * playback context — a channel playlist/radio credits the channel as an
 * "author" — so insert-only accumulation polluted SongArtistMap and a
 * channel-derived artist row ("E.T") collected hundreds of plays of unrelated
 * songs, ranking as a Rewind top artist).
 *
 * - existing song + id-bearing author list → stale mappings are deleted first,
 *   then the fresh list is mapped (delete strictly before any insert);
 * - fresh song → nothing to delete, only the fresh mappings;
 * - re-insert with no author info (local playback) → existing mappings
 *   untouched, no reconciliation.
 */
class DatabaseInsertIgnoreReconcileTest {

    private val songTable = mockk<SongTable>(relaxed = true)
    private val artistTable = mockk<ArtistTable>(relaxed = true)
    private val mapTable = mockk<SongArtistMapTable>(relaxed = true)

    private val extras = mockk<Bundle>(relaxed = true)

    private fun existingSong() = Song(id = "s1", title = "Song", durationText = null, thumbnailUrl = null)

    private fun mediaItemWithAuthors(names: List<String>, ids: List<String>): MediaItem {
        every { extras.getStringArrayList("artistNames") } returns ArrayList(names)
        every { extras.getStringArrayList("artistIds") } returns ArrayList(ids)
        // Platform-type default of a relaxed Bundle mock is "" (not null) — pin it so
        // the album branch stays out of the test
        every { extras.getString("albumId") } returns null
        val metadata = MediaMetadata.Builder()
            .setExtras(extras)
            .build()
        mockkStatic("app.it.fast4x.rimusic.utils.UtilsKt")
        every { any<MediaItem>().asSong } returns existingSong()
        return MediaItem.Builder()
            .setMediaId("s1")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun mediaItemWithNamesOnly(names: List<String>): MediaItem {
        every { extras.getStringArrayList("artistNames") } returns ArrayList(names)
        // No browse IDs: forces the names-only branch
        every { extras.getStringArrayList("artistIds") } returns null
        every { extras.getString("albumId") } returns null
        val metadata = MediaMetadata.Builder()
            .setExtras(extras)
            .build()
        mockkStatic("app.it.fast4x.rimusic.utils.UtilsKt")
        every { any<MediaItem>().asSong } returns existingSong()
        return MediaItem.Builder()
            .setMediaId("s1")
            .setMediaMetadata(metadata)
            .build()
    }

    @Before
    fun setup() {
        mockkObject(Database)
        every { Database.songTable } returns songTable
        every { Database.artistTable } returns artistTable
        every { Database.songArtistMapTable } returns mapTable
        every { songTable.findByIdDirect("s1") } returns existingSong()
        every { artistTable.findByNameDirect(any()) } returns null
        every { Database.insertIgnore(any(), false) } answers { callOriginal() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun reinsertWithAuthorListReconcilesStaleMappings() {
        val item = mediaItemWithAuthors(listOf("A", "B"), listOf("UC_A", "UC_B"))

        Database.insertIgnore(item, autoFix = false)

        verify(exactly = 1) { mapTable.deleteBySongId("s1") }
        verify(exactly = 2) { mapTable.insertIgnore(any<SongArtistMap>()) }
        verifyOrder {
            mapTable.deleteBySongId("s1")
            mapTable.insertIgnore(any<SongArtistMap>())
        }
    }

    @Test
    fun reinsertWithNamesOnlyListReconcilesStaleMappings() {
        every { artistTable.findByNameDirect("A") } returns Artist(id = "UC_A", name = "A")
        every { artistTable.findByNameDirect("B") } returns Artist(id = "UC_B", name = "B")
        val item = mediaItemWithNamesOnly(listOf("A", "B"))

        Database.insertIgnore(item, autoFix = false)

        verify(exactly = 1) { mapTable.deleteBySongId("s1") }
        verify(exactly = 2) { mapTable.insertIgnore(any<SongArtistMap>()) }
        verifyOrder {
            mapTable.deleteBySongId("s1")
            mapTable.insertIgnore(any<SongArtistMap>())
        }
    }

    @Test
    fun freshSongOnlyInsertsMappingsWithoutDelete() {
        every { songTable.findByIdDirect("s1") } returns null
        val item = mediaItemWithAuthors(listOf("A"), listOf("UC_A"))

        Database.insertIgnore(item, autoFix = false)

        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        verify(exactly = 1) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun reinsertWithoutAuthorInfoLeavesMappingsUntouched() {
        val metadata = MediaMetadata.Builder().build()
        val item = MediaItem.Builder()
            .setMediaId("s1")
            .setMediaMetadata(metadata)
            .build()
        mockkStatic("app.it.fast4x.rimusic.utils.UtilsKt")
        every { any<MediaItem>().asSong } returns existingSong()

        Database.insertIgnore(item, autoFix = false)

        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        verify(exactly = 0) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }
}
