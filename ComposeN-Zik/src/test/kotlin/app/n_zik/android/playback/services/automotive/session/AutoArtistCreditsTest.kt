package app.n_zik.android.playback.services.automotive.session

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.database.PlaybackTrackRow
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.utils.ARTIST_LINKED_NAMES_EXTRA
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoArtistCreditsTest {
    @Test
    fun `batch updates by exact track without altering scoped selections and richer credits`() = runTest {
        val tracks = listOf("track-a", "track-b", "track-c")
        val songs = mockk<SongTable>()
        every { songs.observePlaybackTracks(tracks) } returns flowOf(listOf(
            PlaybackTrackRow(
                Song("track-a", "Correct Title", "Old Artist", durationText = null, thumbnailUrl = null),
                listOf(Artist("UC1234567890123456789012", "Correct Artist")), emptyList(),
            ),
            PlaybackTrackRow(
                Song("track-b", "Identical Title", "Old Artist", durationText = null, thumbnailUrl = null),
                listOf(Artist("UC1234567890123456789013", "Narrow Artist")), emptyList(),
            ),
            PlaybackTrackRow(
                Song("track-c", "Identical Title", "modified:New Manual Artist", durationText = null, thumbnailUrl = null),
                emptyList(), emptyList(),
            ),
        ))
        val token = "queue-token"
        val folder = MediaItem.Builder().setMediaId("folder")
            .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).build()).build()
        val playAll = MediaItem.Builder().setMediaId(AutoMediaIdContract.scopedAction(token, false))
            .setMediaMetadata(MediaMetadata.Builder().setIsPlayable(true).build()).build()
        val first = queuedItem(AutoMediaIdContract.selection(token, "track-a", 0), "Old Artist")
        val second = queuedItem(AutoMediaIdContract.selection(token, "track-b", 1), "Performer, Guest")
        val third = queuedItem(AutoMediaIdContract.selection(token, "track-c", 2), "Old Manual Artist", modified = true)
        val duplicate = queuedItem(AutoMediaIdContract.selection(token, "track-a", 3), "Old Artist")
        val items = listOf(folder, playAll, first, second, third, duplicate)

        val corrected = overlayAutoArtistCredits(songs, items, "Unknown Artist") { null }

        assertEquals(items.map { it.mediaId }, corrected.map { it.mediaId })
        assertSame(folder, corrected[0])
        assertSame(playAll, corrected[1])
        assertEquals("Correct Title", corrected[2].mediaMetadata.title.toString())
        assertEquals("Correct Artist", corrected[2].mediaMetadata.artist.toString())
        assertEquals(listOf("Correct Artist"), corrected[2].mediaMetadata.extras?.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA))
        assertEquals("Performer, Guest", corrected[3].mediaMetadata.artist.toString())
        assertEquals("New Manual Artist", corrected[4].mediaMetadata.artist.toString())
        assertTrue(corrected[4].mediaMetadata.extras?.getBoolean("isArtistModified") == true)
        assertEquals("Correct Artist", corrected[5].mediaMetadata.artist.toString())
        assertEquals(first.mediaMetadata.artworkUri, corrected[2].mediaMetadata.artworkUri)
        assertEquals(first.mediaMetadata.durationMs, corrected[2].mediaMetadata.durationMs)
        assertEquals("specific-album", corrected[2].mediaMetadata.extras?.getString("albumId"))
        verify(exactly = 1) { songs.observePlaybackTracks(tracks) }
    }

    private fun queuedItem(id: String, artist: String, modified: Boolean = false): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder()
            .setArtist(artist)
            .setIsPlayable(true)
            .setArtworkUri(Uri.parse("content://artwork/example"))
            .setDurationMs(60_000L)
            .setExtras(Bundle().apply {
                putString("albumId", "specific-album")
                putBoolean("isArtistModified", modified)
            }).build()).build()
}
