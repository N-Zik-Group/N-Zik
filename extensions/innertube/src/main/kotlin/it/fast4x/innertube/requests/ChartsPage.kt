package it.fast4x.innertube.requests

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.v0624.charts.BrowseChartsResponse0624
import it.fast4x.innertube.models.v0624.charts.MusicCarouselShelfRenderer
import it.fast4x.innertube.models.v0624.charts.MusicCarouselShelfRendererContent
import it.fast4x.innertube.models.NavigationEndpoint


suspend fun Innertube.chartsPage(countryCode: String = "") = runCatching {
    val response = browse(browseId = "FEmusic_charts").body<BrowseChartsResponse0624>()

    val musicDetailRenderer =
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents

    Innertube.ChartsPage(
        playlists = musicDetailRenderer
            ?.mapNotNull { it.musicCarouselShelfRenderer }
            ?.mapNotNull(Innertube.PlaylistItem::from)
    )
}

fun Innertube.PlaylistItem.Companion.from(renderer: MusicCarouselShelfRenderer): Innertube.PlaylistItem? {

    val thumbnail0 = renderer
        .contents?.firstOrNull()?.musicTwoRowItemRenderer
        ?.thumbnailRenderer
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.firstOrNull()?.toThumbnail()

    val thumbnail1 = renderer
        .contents?.firstOrNull()?.musicResponsiveListItemRenderer
        ?.thumbnail
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.firstOrNull()?.toThumbnail()

    return Innertube.PlaylistItem(
        info = Innertube.Info(
            name = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
            endpoint = NavigationEndpoint.Endpoint.Browse(
                browseId = renderer
                    .header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseID,
                browseEndpointContextSupportedConfigs = null
            )
        ),
        channel = null,
        songCount = renderer
            .contents?.size,
        thumbnail = thumbnail0 ?: thumbnail1,
        isEditable = false
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.ArtistItem.Companion.from(renderer: List<MusicCarouselShelfRendererContent>): List<Innertube.ArtistItem> {

    val thumbnail = renderer.firstOrNull()?.musicResponsiveListItemRenderer
        ?.thumbnail
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.firstOrNull()?.toThumbnail()

    return listOf(Innertube.ArtistItem(
        info = Innertube.Info(
            name = renderer.firstOrNull()?.musicResponsiveListItemRenderer
                ?.flexColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                ?.text,
            endpoint = NavigationEndpoint.Endpoint.Browse(
                browseId = renderer.firstOrNull()?.musicResponsiveListItemRenderer
                ?.navigationEndpoint?.browseEndpoint?.browseID,
                params = null,
                browseEndpointContextSupportedConfigs = null
            )
        ),
        subscribersCountText = renderer.firstOrNull()?.musicResponsiveListItemRenderer
        ?.flexColumns?.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
            ?.text,
        thumbnail = thumbnail
    ))
}
