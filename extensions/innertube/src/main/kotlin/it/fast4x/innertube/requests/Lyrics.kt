package it.fast4x.innertube.requests

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.NextResponse
import it.fast4x.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.lyrics(videoId: String): Result<String?>? = runCatchingNonCancellable {
    val nextResponse = next(videoId = videoId).body<NextResponse>()

    val browseId = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        ?.getOrNull(1)
        ?.tabRenderer
        ?.endpoint
        ?.browseEndpoint
        ?.browseId
        ?: return@runCatchingNonCancellable null

    val response = browse(browseId = browseId).body<BrowseResponse>()

    response.contents
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicDescriptionShelfRenderer
        ?.description
        ?.text
}
