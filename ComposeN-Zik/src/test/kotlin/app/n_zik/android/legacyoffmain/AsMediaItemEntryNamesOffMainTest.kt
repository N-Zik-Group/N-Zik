package app.n_zik.android.legacyoffmain

import app.it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ArtistConjunctions
import it.fast4x.innertube.models.NavigationEndpoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * asMediaItem display contract (v8, 2026-09-26): the player / queue shows
 * `mediaMetadata.artist`, built by `SongItem.asMediaItem` / `VideoItem.asMediaItem`
 * from the YTM author entries. One YTM author entry = one artist (one channel):
 * its name must stay whole ("Bigflo & OLi"), never split on "&" / "," /
 * conjunctions — the previous `parseArtists()` split fed "Bigflo, OLi" to the
 * player even though the stored row correctly held "Bigflo & Oli".
 *
 * The extras "artistNames" list must also stay whole-name-per-entry so the
 * name -> id zip consumed by `insertIgnore` (mapping) and
 * `artistIdsWithFallback` (player artist buttons) stays aligned: one entry,
 * one name, one browseId.
 *
 * Robolectric + JUnit 4: `asMediaItem` builds a `Bundle` (`bundleOf`), which
 * only works on a Robolectric JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AsMediaItemEntryNamesOffMainTest {

    private val conjunctionsBackup = ArtistConjunctions.conjunctions

    private fun author(name: String, browseId: String?): Innertube.Info<NavigationEndpoint.Endpoint.Browse> =
        Innertube.Info(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

    private fun songItem(authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>): Innertube.SongItem =
        Innertube.SongItem(
            info = Innertube.Info(
                name = "Dommage",
                endpoint = NavigationEndpoint.Endpoint.Watch(videoId = "ORxckE7oN6g")
            ),
            authors = authors,
            album = null,
            durationText = null,
            thumbnail = null
        )

    private fun videoItem(authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>): Innertube.VideoItem =
        Innertube.VideoItem(
            info = Innertube.Info(
                name = "Dommage",
                endpoint = NavigationEndpoint.Endpoint.Watch(videoId = "ORxckE7oN6g")
            ),
            authors = authors,
            viewsText = null,
            durationText = null,
            thumbnail = null
        )

    @Before
    fun setup() {
        // Deterministic regardless of the locale wiring done at app startup
        ArtistConjunctions.conjunctions = listOf("and")
    }

    @After
    fun tearDown() {
        ArtistConjunctions.conjunctions = conjunctionsBackup
    }

    @Test
    fun songItemAsMediaItemKeepsWholeArtist() {
        val media = songItem(listOf(author("Bigflo & OLi", "UC_BIGFLO"))).asMediaItem
        assertEquals("Bigflo & OLi", media.mediaMetadata.artist)
    }

    @Test
    fun songItemAsMediaItemExtrasStayAligned() {
        val media = songItem(listOf(author("Bigflo & OLi", "UC_BIGFLO"))).asMediaItem
        val extras = media.mediaMetadata.extras!!
        assertEquals(listOf("Bigflo & OLi"), extras.getStringArrayList("artistNames"))
        assertEquals(listOf("UC_BIGFLO"), extras.getStringArrayList("artistIds"))
    }

    @Test
    fun songItemAsMediaItemMultiEntryJoinsWholeNames() {
        val media = songItem(listOf(author("Tanchiky", "UC_A"), author("siromaru", "UC_B"))).asMediaItem
        assertEquals("Tanchiky, siromaru", media.mediaMetadata.artist)
        val extras = media.mediaMetadata.extras!!
        assertEquals(listOf("Tanchiky", "siromaru"), extras.getStringArrayList("artistNames"))
        assertEquals(listOf("UC_A", "UC_B"), extras.getStringArrayList("artistIds"))
    }

    @Test
    fun videoItemAsMediaItemKeepsWholeArtist() {
        val media = videoItem(listOf(author("Bigflo & OLi", "UC_BIGFLO"))).asMediaItem
        assertEquals("Bigflo & OLi", media.mediaMetadata.artist)
    }

    @Test
    fun videoItemAsMediaItemSkipsJunkEntryNames() {
        val media = videoItem(listOf(author("Bigflo & OLi", "UC_BIGFLO"), author("&", "UC_SEPARATOR"))).asMediaItem
        assertEquals("Bigflo & OLi", media.mediaMetadata.artist)
        val extras = media.mediaMetadata.extras!!
        // Junk entry names are dropped from artistNames; artistIds keeps every
        // browseId (unfiltered) — the name->id zip (insertIgnore) truncates to
        // the shorter list, so no junk row is ever linked.
        assertEquals(listOf("Bigflo & OLi"), extras.getStringArrayList("artistNames"))
        assertEquals(listOf("UC_BIGFLO", "UC_SEPARATOR"), extras.getStringArrayList("artistIds"))
    }
}
