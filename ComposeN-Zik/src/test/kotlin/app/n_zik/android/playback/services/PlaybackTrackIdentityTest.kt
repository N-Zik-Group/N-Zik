package app.n_zik.android.playback.services

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.database.PlaybackTrackRow
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.utils.ARTIST_LINKED_NAMES_EXTRA
import app.n_zik.android.utils.withPlaybackAlbum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackTrackIdentityTest {
    @Test
    fun `an ID-only snapshot adopts the discovered cover without replacing the media source`() {
        val sparse = item.buildUpon().setMediaMetadata(MediaMetadata.EMPTY).build()
        val source = "https://example.test/discovered-cover"
        val filled = row.copy(song = row.song.copy(thumbnailUrl = source, durationText = "3:48"))

        val updated = sparse.withCurrentTrackIdentity(filled, "Unknown Artist") { null }

        assertEquals(Uri.parse(source), updated.mediaMetadata.artworkUri)
        assertEquals("Corrected Title", updated.mediaMetadata.title.toString())
        assertEquals("3:48", updated.mediaMetadata.extras?.getString("durationText"))
        assertEquals(sparse.localConfiguration, updated.localConfiguration)
        assertSame(updated, updated.withCurrentTrackIdentity(filled, "Unknown Artist") { null })
    }

    @Test
    fun `stale snapshots keep their chosen edition and audio source while adopting committed identity`() {
        val snapshot = item.withPlaybackAlbum("MPRE-deluxe", "Old Deluxe")
        val corrected = snapshot.withCurrentTrackIdentity(row, "Unknown Artist") { null }

        assertEquals("Corrected Title", corrected.mediaMetadata.title.toString())
        assertEquals("Verified Artist", corrected.mediaMetadata.artist.toString())
        assertEquals(listOf("UC1234567890123456789012"), corrected.mediaMetadata.extras?.getStringArrayList("artistIds"))
        assertEquals("MPRE-deluxe", corrected.mediaMetadata.extras?.getString("albumId"))
        assertEquals("Deluxe", corrected.mediaMetadata.albumTitle.toString())
        assertEquals(snapshot.localConfiguration, corrected.localConfiguration)
        assertEquals(snapshot.mediaMetadata.artworkUri, corrected.mediaMetadata.artworkUri)
        assertSame(corrected, corrected.withCurrentTrackIdentity(row, "Unknown Artist") { null })
    }

    @Test
    fun `a corrected byline with no linked ID leaves no stale or invented artist destination`() {
        val unlinked = row.copy(artists = emptyList())
        val corrected = item.withCurrentTrackIdentity(unlinked, "Unknown Artist") { null }

        assertEquals("Verified Artist", corrected.mediaMetadata.artist.toString())
        assertEquals(emptyList<String>(), corrected.mediaMetadata.extras?.getStringArrayList("artistIds"))
        assertEquals(emptyList<String>(), corrected.mediaMetadata.extras?.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA))
    }

    @Test
    fun `a derived album association follows a committed replacement`() {
        val snapshot = item.withPlaybackAlbum("MPRE-previous", "Previous", derived = true)
        val current = row.copy(albums = listOf(Album("MPRE-current", "Current")))
        val corrected = snapshot.withCurrentTrackIdentity(current, "Unknown Artist") { null }

        assertEquals("MPRE-current", corrected.mediaMetadata.extras?.getString("albumId"))
        assertEquals("Current", corrected.mediaMetadata.albumTitle.toString())
    }

    private val item = MediaItem.Builder().setMediaId("track").setUri("https://example.test/track")
        .setCustomCacheKey("track").setMediaMetadata(
            MediaMetadata.Builder().setTitle("Stale Title").setArtist("Stale Artist")
                .setArtworkUri(Uri.parse("https://example.test/cover")).setExtras(Bundle().apply {
                    putStringArrayList("artistIds", arrayListOf("UC1234567890123456789013"))
                    putStringArrayList("artistNames", arrayListOf("Stale Artist"))
                }).build()
        ).build()

    private val row = PlaybackTrackRow(
        Song("track", "Corrected Title", "Verified Artist", durationText = null, thumbnailUrl = null),
        listOf(Artist("UC1234567890123456789012", "Verified Artist")),
        listOf(Album("MPRE-deluxe", "Deluxe"), Album("modified:MPRE-standard", "Other Edition")),
    )
}
