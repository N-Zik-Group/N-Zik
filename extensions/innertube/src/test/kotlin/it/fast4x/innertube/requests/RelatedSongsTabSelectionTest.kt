package it.fast4x.innertube.requests

import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.NextResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private typealias Tab = NextResponse.Contents.SingleColumnMusicWatchNextResultsRenderer
    .TabbedRenderer.WatchNextTabbedResultsRenderer.Tab

private typealias TabRenderer = NextResponse.Contents.SingleColumnMusicWatchNextResultsRenderer
    .TabbedRenderer.WatchNextTabbedResultsRenderer.Tab.TabRenderer

/**
 * YouTube inserted a "Lyrics" tab at index 1 of the `next()` response, which
 * shifted the related (MPTR) tab from index 2 to index 3 and broke the old
 * hard-coded `tabs.getOrNull(2)` lookup. Selection must now rely on the stable
 * `MPTR` browseId prefix, for both the new and the legacy tab orders, and must
 * ignore tabs without an MPTR browseId.
 */
class RelatedSongsTabSelectionTest {

    private fun tab(browseId: String?): Tab = Tab(
        tabRenderer = TabRenderer(
            content = null,
            endpoint = browseId?.let {
                NavigationEndpoint(
                    watchEndpoint = null,
                    watchPlaylistEndpoint = null,
                    browseEndpoint = NavigationEndpoint.Endpoint.Browse(browseId = it),
                    searchEndpoint = null,
                )
            },
            title = null,
        )
    )

    @Test
    fun `new tab order still selects the MPTR tab even when it is at index 3`() {
        val tabs = listOf(
            tab(browseId = null),        // Up next (no browse endpoint)
            tab(browseId = "aLyrics01"), // Lyrics (inserted at index 1)
            tab(browseId = null),        // Comments (browseId = null)
            tab(browseId = "MPTRt3-abc") // Related
        )
        assertEquals("MPTRt3-abc", findSimilarTabBrowseId(tabs))
    }

    @Test
    fun `legacy tab order still selects the MPTR tab at index 2`() {
        val tabs = listOf(
            tab(browseId = null),        // Up next
            tab(browseId = null),        // Comments
            tab(browseId = "MPTRt2-abc") // Related
        )
        assertEquals("MPTRt2-abc", findSimilarTabBrowseId(tabs))
    }

    @Test
    fun `returns null when no tab has an MPTR browseId`() {
        val tabs = listOf(
            tab(browseId = null),
            tab(browseId = "aLyrics01"),
            tab(browseId = "VLxyz123")
        )
        assertNull(findSimilarTabBrowseId(tabs))
    }

    @Test
    fun `returns null for an empty tab list`() {
        assertNull(findSimilarTabBrowseId(emptyList()))
    }

    @Test
    fun `browseId containing MPTR at a non-leading position is ignored`() {
        val tabs = listOf(
            tab(browseId = null),
            tab(browseId = "VLMPTR123")
        )
        assertNull(findSimilarTabBrowseId(tabs))
    }

    @Test
    fun `the first MPTR tab wins when several are present`() {
        val tabs = listOf(
            tab(browseId = "MPTRt1-first"),
            tab(browseId = null),
            tab(browseId = "MPTRt2-second")
        )
        assertEquals("MPTRt1-first", findSimilarTabBrowseId(tabs))
    }
}
