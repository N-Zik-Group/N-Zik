package app.n_zik.android.components.song

import androidx.navigation.NavController
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.MenuState
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoToArtistIdentityTest {
    @Test
    fun `artist navigation is available with a usable credit or a verified kind of ID`() {
        val nav = mockk<NavController>()
        val menu = MenuState()
        val track = Song.makePlaceholder("exactSong01").copy(artistsText = null)

        assertFalse(GoToArtist(nav, track, menu).isEnabled)
        assertFalse(GoToArtist(nav, track, menu, artistName = "Unknown Artist").isEnabled)
        assertTrue(GoToArtist(nav, track, menu, artistName = "Justice").isEnabled)
        assertTrue(GoToArtist(nav, track, menu, artistId = justice).isEnabled)
    }

    @Test
    fun `an identical title from another video does not provide the artist`() {
        val neighbor = track("neighbor001", "Justice" to justice)
        val exact = track("exactSong01", "Grand Theft Auto VI" to official)

        assertEquals(official, selectExactArtistEndpoint("exactSong01", null, listOf(neighbor, exact))?.browseId)
        assertNull(selectExactArtistEndpoint("missing0001", null, listOf(neighbor, exact)))
    }

    @Test
    fun `a collaboration uses the chosen credited artist`() {
        val track = track("exactSong01", "Grand Theft Auto VI" to official, "Justice" to justice)

        assertEquals(justice, selectExactArtistEndpoint("exactSong01", "Justice", listOf(track))?.browseId)
        assertNull(selectExactArtistEndpoint("exactSong01", null, listOf(track)))
    }

    @Test
    fun `a manual artist ID wins over automatic homonyms`() {
        val edit = Artist("modified:$official", "Artist")
        val entries = listOf(Artist(justice, "Artist"), edit)

        assertEquals(edit, chooseTrackArtist(entries, "Artist"))
        assertNull(chooseTrackArtist(entries, "Unknown"))
    }

    private fun track(videoId: String, vararg credits: Pair<String, String>) = Innertube.SongItem(
        info = Innertube.Info(name = "Shared title", endpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId)),
        authors = credits.map { (name, id) ->
            Innertube.Info(name = name, endpoint = NavigationEndpoint.Endpoint.Browse(browseId = id))
        },
        album = null,
        durationText = null,
        thumbnail = null
    )

    private companion object {
        const val official = "UCJ97ksybdciBBv7xsFSK6HA"
        const val justice = "UCABCDEFGHIJK123456789_1"
    }
}
