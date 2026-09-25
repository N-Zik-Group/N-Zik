package app.n_zik.android.components.ui.screens.rewind

import app.n_zik.android.components.ui.screens.rewind.slides.RewindArtistBioResolver
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.requests.ArtistPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract for the Top Artist spotlight bio ([RewindArtistBioResolver]): the in-app
 * description is the sole source — a non-blank stored description short-circuits any
 * network fetch, and otherwise only the Innertube artist page (fetched by browseId) is
 * consulted. A by-name Wikipedia lookup must never feed the bio: it is what made the
 * "E.T" spotlight render the Wikipedia article of the same-named song (bug report,
 * 2026-09-25, confirmed against a DB export: channel-derived artist rows).
 */
class RewindArtistBioResolverTest {

    private fun artistPage(description: String?, thumbnailUrl: String? = null) =
        ArtistPage(
            artist = Innertube.ArtistItem(
                info = null,
                subscribersCountText = null,
                thumbnail = thumbnailUrl?.let { Thumbnail(url = it, height = null, width = null) }
            ),
            sections = emptyList(),
            description = description,
            subscribers = null,
            listeners = null,
            shuffleEndpoint = null,
            radioEndpoint = null
        )

    /** Fetch stub that fails loudly if invoked — proves no network ran. */
    private fun noNetwork(): suspend (String) -> Result<ArtistPage> =
        { error("network fetch must not run") }

    // ── Stored (in-app) description wins ─────────────────────────────────────────────

    @Test
    fun storedDescriptionShortCircuitsNetworkFetch() = runBlocking {
        val bio = RewindArtistBioResolver.resolve(
            browseId = "UC123",
            storedDescription = "  In-app bio  ",
            fetchPage = noNetwork()
        )
        assertEquals("In-app bio", bio?.bio)
        assertEquals("In-app bio", bio?.description)
    }

    // ── Fallback to the Innertube artist page by browseId ────────────────────────────

    @Test
    fun blankStoredDescriptionFallsBackToArtistPage() = runBlocking {
        val bio = RewindArtistBioResolver.resolve(
            browseId = "UC123",
            storedDescription = "   ",
            fetchPage = { Result.success(artistPage("YTM bio", "https://thumb.example/a")) }
        )
        assertEquals("YTM bio", bio?.bio)
        assertEquals("YTM bio", bio?.description)
        assertEquals("https://thumb.example/a", bio?.imageUrl)
    }

    @Test
    fun blankBrowseIdIsTrimmedBeforeFetch() = runBlocking {
        var fetched: String? = null
        val bio = RewindArtistBioResolver.resolve(
            browseId = "  UC123  ",
            storedDescription = null,
            fetchPage = { id ->
                fetched = id
                Result.success(artistPage("YTM bio"))
            }
        )
        assertEquals("UC123", fetched)
        assertEquals("YTM bio", bio?.bio)
    }

    // ── Nothing available → no bio, card renders without the about section ──────────

    @Test
    fun missingStoredAndBrowseIdYieldsNullWithoutFetch() = runBlocking {
        assertNull(RewindArtistBioResolver.resolve(null, "   ", noNetwork()))
        assertNull(RewindArtistBioResolver.resolve("  ", null, noNetwork()))
        assertNull(RewindArtistBioResolver.resolve(null, null, noNetwork()))
    }

    @Test
    fun failedFetchYieldsNull() = runBlocking {
        val bio = RewindArtistBioResolver.resolve(
            browseId = "UC123",
            storedDescription = null,
            fetchPage = { Result.failure(IllegalStateException("network down")) }
        )
        assertNull(bio)
    }

    @Test
    fun artistPageWithoutDescriptionYieldsNull() = runBlocking {
        val bio = RewindArtistBioResolver.resolve(
            browseId = "UC123",
            storedDescription = null,
            fetchPage = { Result.success(artistPage("   ")) }
        )
        assertNull(bio)
    }
}
