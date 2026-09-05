package it.fast4x.innertube.requests

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.ContinuationResponse
import it.fast4x.innertube.models.GridRenderer
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.MusicTwoRowItemRenderer
import it.fast4x.innertube.utils.runCatchingNonCancellable

suspend fun <T : Innertube.Item> Innertube.itemsPage(
    browseId: String,
    params: String? = null,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null },
) = runCatchingNonCancellable {
    val response = browse(browseId = browseId, params = params).body<BrowseResponse>()

    val sectionListRendererContent = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()

    itemsPageFromMusicShelRendererOrGridRenderer(
        musicShelfRenderer = sectionListRendererContent
            ?.musicShelfRenderer,
        gridRenderer = sectionListRendererContent
            ?.gridRenderer,
        fromMusicResponsiveListItemRenderer = fromMusicResponsiveListItemRenderer,
        fromMusicTwoRowItemRenderer = fromMusicTwoRowItemRenderer,
    )
}

suspend fun <T : Innertube.Item> Innertube.itemsPageContinuation(
    continuation: String,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null },
) = runCatchingNonCancellable {
    val response = browse(continuation = continuation).body<ContinuationResponse>()

    itemsPageFromMusicShelRendererOrGridRenderer(
        musicShelfRenderer = response
            .continuationContents
            ?.musicShelfContinuation,
        gridRenderer = null,
        fromMusicResponsiveListItemRenderer = fromMusicResponsiveListItemRenderer,
        fromMusicTwoRowItemRenderer = fromMusicTwoRowItemRenderer,
    )
}

private fun <T : Innertube.Item> itemsPageFromMusicShelRendererOrGridRenderer(
    musicShelfRenderer: MusicShelfRenderer?,
    gridRenderer: GridRenderer?,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T?,
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T?,
): Innertube.ItemsPage<T>? {
    return if (musicShelfRenderer != null) {
        Innertube.ItemsPage(
            continuation = musicShelfRenderer
                .continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation,
            items = musicShelfRenderer
                .contents
                ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                ?.mapNotNull(fromMusicResponsiveListItemRenderer)
        )
    } else if (gridRenderer != null) {
        Innertube.ItemsPage(
            continuation = null,
            items = gridRenderer
                .items
                ?.mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                ?.mapNotNull(fromMusicTwoRowItemRenderer)
        )
    } else {
        null
    }
}
