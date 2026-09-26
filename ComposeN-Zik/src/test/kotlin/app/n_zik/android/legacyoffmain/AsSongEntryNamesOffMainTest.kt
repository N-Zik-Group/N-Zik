package app.n_zik.android.legacyoffmain

import app.it.fast4x.rimusic.utils.asSong
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ArtistConjunctions
import it.fast4x.innertube.models.NavigationEndpoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * asSong display contract (v7, 2026-09-26): live YTM list displays (artist
 * page, online search, album/playlist pages) convert each SongItem /
 * VideoItem to a Song via asSong. A YTM author entry is one artist (one
 * channel): its name must stay whole in artistsText, never split on
 * "&" / "," / conjunctions.
 *
 * The previous conversion used parseArtists(), which split a single entry
 * name ("Bigflo & OLi" -> "Bigflo, OLi") — the live display showed two
 * artists for one channel even though the stored row (Database.upsert v6)
 * correctly held "Bigflo & Oli".
 */
class AsSongEntryNamesOffMainTest {

    private val conjunctionsBackup = ArtistConjunctions.conjunctions

    private fun author( name: String, browseId: String? ): Innertube.Info<NavigationEndpoint.Endpoint.Browse> =
        Innertube.Info(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

    private fun songItem( authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>> ): Innertube.SongItem =
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

    private fun videoItem( authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>> ): Innertube.VideoItem =
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
    fun singleAmpersandEntryKeepsWholeName() {
        assertEquals("Bigflo & OLi", songItem(listOf(author("Bigflo & OLi", "UC_BIGFLO"))).asSong.artistsText)
    }

    @Test
    fun multiEntrySongJoinsWholeNames() {
        val item = songItem(listOf(author("Tanchiky", "UC_A"), author("siromaru", "UC_B")))
        assertEquals("Tanchiky, siromaru", item.asSong.artistsText)
    }

    @Test
    fun delimiterArtistNameIsNotSplit() {
        // "COOL&CREATE" is one artist whose name carries "&"
        assertEquals("COOL&CREATE", songItem(listOf(author("COOL&CREATE", "UC_COOL"))).asSong.artistsText)
    }

    @Test
    fun standaloneConjunctionEntryIsSkipped() {
        val item = songItem(listOf(author("Bigflo & OLi", "UC_BIGFLO"), author("and", "UC_CONJUNCTION")))
        assertEquals("Bigflo & OLi", item.asSong.artistsText)
    }

    @Test
    fun pureSeparatorEntryIsSkipped() {
        val item = songItem(listOf(author("Bigflo & OLi", "UC_BIGFLO"), author("&", "UC_SEPARATOR")))
        assertEquals("Bigflo & OLi", item.asSong.artistsText)
    }

    @Test
    fun videoItemKeepsWholeName() {
        assertEquals("Bigflo & OLi", videoItem(listOf(author("Bigflo & OLi", "UC_BIGFLO"))).asSong.artistsText)
    }
}
