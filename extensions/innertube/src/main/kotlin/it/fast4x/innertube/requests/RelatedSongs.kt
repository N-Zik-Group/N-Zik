package it.fast4x.innertube.requests


import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.MusicCarouselShelfRenderer
import it.fast4x.innertube.models.NextResponse
import it.fast4x.innertube.utils.findSectionByTitle
import it.fast4x.innertube.utils.from
import it.fast4x.innertube.utils.runCatchingNonCancellable


suspend fun Innertube.relatedSongs(videoId: String) = runCatchingNonCancellable {
    val nextResponse = next(videoId = videoId).body<NextResponse>()

    val tabs = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs

    val browseId = tabs?.let(::findSimilarTabBrowseId)
        ?: return@runCatchingNonCancellable null

    // Pin hl=en so the "You might also like" shelf title is never localized,
    // which would break the exact title match below on non-English devices
    val response = browse(browseId = browseId, hl = "en").body<BrowseResponse>()

    val sectionListRenderer = response
        .contents
        ?.sectionListRenderer

    Innertube.RelatedSongs(
        songs = sectionListRenderer
            ?.findSectionByTitle("You might also like")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from)
    )
}

/**
 * Finds the browseId of the "Related" tab by its stable `MPTR` prefix.
 *
 * YouTube inserted a "Lyrics" tab at index 1 of the `next()` response, which
 * shifted the related tab (formerly at index 2) to index 3; matching on the
 * browseId prefix is robust to tab order.
 *
 * @param tabs tabs from the `next()` response
 * @return the browseId of the first tab whose browseId starts with "MPTR", or null
 */
internal fun findSimilarTabBrowseId(
    tabs: List<NextResponse.Contents.SingleColumnMusicWatchNextResultsRenderer
        .TabbedRenderer.WatchNextTabbedResultsRenderer.Tab>
): String? = tabs
    .mapNotNull { it.tabRenderer?.endpoint?.browseEndpoint?.browseId }
    .firstOrNull { it.startsWith("MPTR") }
