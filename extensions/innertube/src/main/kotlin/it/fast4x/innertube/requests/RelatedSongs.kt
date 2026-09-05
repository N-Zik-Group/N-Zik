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

    val browseId = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        ?.getOrNull(2)
        ?.tabRenderer
        ?.endpoint
        ?.browseEndpoint
        ?.browseId
        ?: return@runCatchingNonCancellable null

    val response = browse(browseId = browseId).body<BrowseResponse>()

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
