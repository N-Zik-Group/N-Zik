package app.n_zik.innertube.requests

import io.ktor.client.call.body
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.BrowseResponse
import app.n_zik.innertube.models.NextResponse
import app.n_zik.innertube.utils.runCatchingNonCancellable

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
