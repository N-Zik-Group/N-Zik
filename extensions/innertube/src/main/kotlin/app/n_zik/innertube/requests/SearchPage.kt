package app.n_zik.innertube.requests

import io.ktor.client.call.body
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.ContinuationResponse
import app.n_zik.innertube.models.MusicResponsiveListItemRenderer
import app.n_zik.innertube.models.MusicShelfRenderer
import app.n_zik.innertube.models.SearchResponse
import app.n_zik.innertube.utils.runCatchingNonCancellable

suspend fun <T : Innertube.Item> Innertube.searchPage(
    query: String,
    params: String? = null,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?
) = runCatchingNonCancellable {
    val response = search(query = query, params = params).body<SearchResponse>()

    response
        .contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.lastOrNull()
        ?.musicShelfRenderer
        ?.toItemsPage(fromMusicShelfRendererContent)
}

suspend fun <T : Innertube.Item> Innertube.searchPageContinuation(
    continuation: String,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?
) = runCatchingNonCancellable {
    val response = search(continuation = continuation).body<ContinuationResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toItemsPage(fromMusicShelfRendererContent)
}

private fun <T : Innertube.Item> MusicShelfRenderer?.toItemsPage(mapper: (MusicShelfRenderer.Content) -> T?) =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(mapper),
        continuation = this
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    )

private fun <T : Innertube.Item> MusicResponsiveListItemRenderer?.toItemsPage(mapper: (MusicResponsiveListItemRenderer.FlexColumn) -> T?) =
    Innertube.ItemsPage(
        items = this
            ?.flexColumns
            ?.mapNotNull(mapper),
        continuation = null
    )
