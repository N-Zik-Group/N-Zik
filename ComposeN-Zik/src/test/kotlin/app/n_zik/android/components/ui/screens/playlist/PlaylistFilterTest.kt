package app.n_zik.android.components.ui.screens.playlist

import app.n_zik.android.ui.screens.playlist.applyFilter
import app.n_zik.android.utils.asMediaItem
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #606 H1 — [applyFilter] is the pure, null-safe filter predicate extracted from
 * `PlaylistSongList.kt`'s old composition-time mutation block (which mutated
 * `playlistPage.songs` in place and recomputed `asMediaItem` ×3 per song on every
 * recomposition). It must never mutate its input, so re-typing a search can no longer
 * shrink an already-filtered list (cumulative-shrink bug), and matching stays
 * case-insensitive across title/artist/album.
 *
 * Robolectric + JUnit 4 (executed via junit-vintage-engine on the JUnit 5 platform):
 * `SongItem.asMediaItem` builds a `Bundle` (`bundleOf`), which only works on a Robolectric JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistFilterTest {

    private fun songItem(
        id: String,
        title: String,
        artist: String? = null,
        album: String? = null,
        explicit: Boolean = false
    ) = Innertube.SongItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)
        ),
        authors = artist?.let {
            listOf(Innertube.Info(name = it, endpoint = NavigationEndpoint.Endpoint.Browse(browseId = "artist_$id")))
        },
        album = album?.let {
            Innertube.Info(name = it, endpoint = NavigationEndpoint.Endpoint.Browse(browseId = "album_$id"))
        },
        durationText = "3:00",
        thumbnail = null,
        explicit = explicit
    )

    private val fullList = listOf(
        songItem("id_a1", "Alpha One", artist = "Bob", album = "Album X"),
        songItem("id_b1", "Beta Two", artist = "Cara", album = "Album Y"),
        songItem("id_ab2", "Alpha Beta", artist = "Dan", album = "Album Z")
    )

    @Test
    fun `null list returns null`() {
        val list: List<Innertube.SongItem>? = null

        assertNull(list.applyFilter("alpha"))
    }

    @Test
    fun `null filter returns the list unchanged`() {
        assertSame(fullList, fullList.applyFilter(null))
    }

    @Test
    fun `blank filter returns the list unchanged`() {
        assertSame(fullList, fullList.applyFilter("   "))
    }

    @Test
    fun `title match is case-insensitive`() {
        val result = fullList.applyFilter("beta")!!

        assertEquals(2, result.size)
        assertTrue(result.all { it.asMediaItem.mediaMetadata.title!!.contains("beta", true) })
    }

    @Test
    fun `artist match is case-insensitive`() {
        val result = fullList.applyFilter("cara")!!

        assertEquals(1, result.size)
        assertEquals("id_b1", result.single().key)
    }

    @Test
    fun `album match is case-insensitive`() {
        val result = fullList.applyFilter("ALBUM Y")!!

        assertEquals(1, result.size)
        assertEquals("id_b1", result.single().key)
    }

    @Test
    fun `explicit song title still matches its plain title text`() {
        val explicitList = listOf(songItem("id_exp", "Gamma", explicit = true))

        val result = explicitList.applyFilter("gamma")!!

        assertEquals(1, result.size)
        assertEquals("id_exp", result.single().key)
    }

    @Test
    fun `a filter always applies to the full list, not an already filtered one`() {
        // Filter "alpha" keeps the two alpha songs.
        val filteredAlpha = fullList.applyFilter("alpha")!!
        assertEquals(2, filteredAlpha.size)

        // Filter "beta" applied to the SAME full list must still surface "Beta Two", which the
        // old in-place mutation dropped (it filtered B over the already-A-filtered list).
        val filteredBeta = fullList.applyFilter("beta")!!
        assertEquals(2, filteredBeta.size)
        assertTrue(filteredBeta.any { it.key == "id_b1" })

        // The source list is never mutated by either call.
        assertEquals(3, fullList.size)
    }
}
