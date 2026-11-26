package app.n_zik.android.components.menu.song

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.ondevice.DEVICE_ARTIST_PREFIX
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Info
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackMenuArtistsTest {
    @Test
    fun `device track and album artists keep their exact labels and links`() {
        val trackArtist = Artist("${DEVICE_ARTIST_PREFIX}track", "Alice, Bob")
        val albumArtist = Artist("${DEVICE_ARTIST_PREFIX}album", "Various Artists")
        val importedArtist = Artist("UC1234567890", "Provider artist")

        assertEquals(
            listOf(Info(trackArtist.id, "Alice, Bob"), Info(albumArtist.id, "Various Artists")),
            deviceTrackMenuArtists(listOf(trackArtist, albumArtist, importedArtist, trackArtist)),
        )
    }

    @Test
    fun `edited link replaces its provider credit while unlinked artists remain selectable`() {
        val edited = Info("${MODIFIED_PREFIX}UC_artist", "${MODIFIED_PREFIX}New artist name")
        val original = Info("UC_artist", "Original artist name")

        val credits = mergeTrackMenuArtistCredits(
            resolvedLinks = listOf(edited),
            sourceLinks = listOf(original),
            sourceNames = listOf("original artist name", "Guest musician"),
        )

        assertEquals(listOf(edited, Info("", "Guest musician")), credits)
    }
}
