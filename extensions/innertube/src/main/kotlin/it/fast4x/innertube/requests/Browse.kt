package it.fast4x.innertube.requests

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.MusicTwoRowItemRenderer
import it.fast4x.innertube.utils.from
import it.fast4x.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.browseCategory(browseId: String, params: String? = null) = runCatchingNonCancellable {
    val response = browse(browseId = browseId, params = params).body<BrowseResponse>()

    BrowseResult(
        title = response.header?.musicImmersiveHeaderRenderer?.title?.text ?: response.header
            ?.musicDetailHeaderRenderer?.title?.text,
        items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { content ->
                when {
                    content.gridRenderer != null -> BrowseResult.Item(
                        title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs
                            ?.firstOrNull()?.text ?: return@mapNotNull null,
                        items = content.gridRenderer.items?.mapNotNull { it.musicTwoRowItemRenderer?.toItem() }
                            .orEmpty()
                    )

                    content.musicCarouselShelfRenderer != null -> BrowseResult.Item(
                        title = content.musicCarouselShelfRenderer.header?.musicCarouselShelfBasicHeaderRenderer
                            ?.title?.runs?.firstOrNull()?.text ?: return@mapNotNull null,
                        items = content.musicCarouselShelfRenderer.contents?.mapNotNull { it.musicTwoRowItemRenderer?.toItem() }
                            .orEmpty()
                    )

                    else -> null
                }
            }.orEmpty()
    )
}

data class BrowseResult(
    val title: String?,
    val items: List<Item>
) {
    data class Item(
        val title: String,
        val items: List<Innertube.Item>
    )
}

fun MusicTwoRowItemRenderer.toItem() = when {
    isAlbum -> Innertube.AlbumItem.from(this)
    isPlaylist -> Innertube.PlaylistItem.from(this)
    isArtist -> Innertube.ArtistItem.from(this)
    else -> null
}
