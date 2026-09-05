package it.fast4x.innertube.requests

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.Innertube.getBestQuality
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.MusicCarouselShelfRenderer
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.oddElements
import it.fast4x.innertube.utils.from
import it.fast4x.innertube.utils.runCatchingCancellable
import it.fast4x.innertube.utils.runCatchingNonCancellable


data class PlaylistPage(
    val playlist: Innertube.PlaylistItem,
    val description: String?,
    var songs: List<Innertube.SongItem>,
    val songsContinuation: String?,
    val continuation: String?,
    val isEditable: Boolean? = false,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem {
            return Innertube.SongItem(
                info = Innertube.Info(
                    name = renderer.flexColumns.firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer?.text
                        ?.runs?.firstOrNull()?.text,
                    endpoint = NavigationEndpoint.Endpoint.Watch(
                        videoId = renderer.playlistItemData?.videoId
                    )
                ),
                authors = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.oddElements()
                    ?.map {
                    Innertube.Info(
                        name = it.text,
                        endpoint = it.navigationEndpoint?.browseEndpoint
                    )
                },
                album = renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.let {
                        Innertube.Info(
                            name = it.text,
                            endpoint = it.navigationEndpoint?.browseEndpoint
                        )
                    },
                durationText = renderer.fixedColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                setVideoId = renderer.playlistItemData?.playlistSetVideoId,
//                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,

            )
        }
    }
}

suspend fun Innertube.playlistPage(browseId: String, params: String? = null) = runCatchingCancellable {
    val response = browse(browseId = browseId, params = params, setLogin = true).body<BrowseResponse>()


    if (response.contents?.twoColumnBrowseResultsRenderer == null) {
        /* OLD */
        val header = response
            .header
            ?.musicDetailHeaderRenderer

        val contents = response
            .contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents

//        val musicShelfRenderer = contents
//            ?.firstOrNull()
//            ?.musicShelfRenderer

        val musicCarouselShelfRenderer = contents
            ?.getOrNull(1)
            ?.musicCarouselShelfRenderer

        Innertube.PlaylistOrAlbumPage(
            title = header
                ?.title
                ?.text,
            description = header
                ?.description
                ?.text,
            thumbnail = header
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.getBestQuality(),
                //?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) },
            authors = header
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(1)
                ?.map(Innertube::Info),
            year = header
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(2)
                ?.firstOrNull()
                ?.text,
            url = response
                .microformat
                ?.microformatDataRenderer
                ?.urlCanonical,
            songsPage = Innertube.ItemsPage(
                items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                    ?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                        it.musicResponsiveListItemRenderer?.let { it1 -> Innertube.SongItem.from(it1) }
                    },
                continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations
                    ?.firstOrNull()?.nextContinuationData?.continuation
            ),
            otherVersions = musicCarouselShelfRenderer
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.AlbumItem::from),
            otherInfo = header
                ?.secondSubtitle
                ?.text
        )
    } else {
        /* NEW */
        val header = response
            .contents
            .twoColumnBrowseResultsRenderer
            .tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicResponsiveHeaderRenderer

        val contents = response
            .contents
            .twoColumnBrowseResultsRenderer
            .secondaryContents
            ?.sectionListRenderer
            ?.contents

//        val musicShelfRenderer = contents
//            ?.firstOrNull()
//            ?.musicShelfRenderer

        val musicCarouselShelfRenderer = contents
            ?.getOrNull(1)
            ?.musicCarouselShelfRenderer

        Innertube.PlaylistOrAlbumPage(
            title = header
                ?.title
                ?.text,
            description = null,
//            description = response.contents.twoColumnBrowseResultsRenderer.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
//                ?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.description?.musicDescriptionShelfRenderer?.description?.runs?.joinToString("") { it.text.toString() },
            thumbnail = header
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.getBestQuality(),
            authors = header
                ?.straplineTextOne
                ?.splitBySeparator()
                ?.getOrNull(0)
                ?.map(Innertube::Info),
            year = header
                ?.subtitle
                ?.runs
                ?.getOrNull(2)
                ?.text,
            url = response
                .microformat
                ?.microformatDataRenderer
                ?.urlCanonical,
            songsPage = Innertube.ItemsPage(
                items = response.contents.twoColumnBrowseResultsRenderer.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                    it.musicResponsiveListItemRenderer?.let { it1 -> Innertube.SongItem.from(it1) }
                },
                continuation = response.contents.twoColumnBrowseResultsRenderer.secondaryContents?.sectionListRenderer
                    ?.continuations?.firstOrNull()?.nextContinuationData?.continuation

//                continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations
//                    ?.firstOrNull()?.nextContinuationData?.continuation
            ),
            otherVersions = musicCarouselShelfRenderer
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.AlbumItem::from),
            otherInfo = response.contents.twoColumnBrowseResultsRenderer.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicResponsiveHeaderRenderer?.secondSubtitle?.runs?.joinToString("") { it.text.toString() }
        )
    }

}

suspend fun Innertube.playlistPageContinuation(continuation: String) = runCatchingNonCancellable {
    val response = browse(continuation = continuation).body<BrowseResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toSongsPage()
}

private fun MusicShelfRenderer?.toSongsPage() = Innertube.ItemsPage(
    items = this
        ?.contents
        ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
        ?.mapNotNull(Innertube.SongItem::from),
    continuation = this
        ?.continuations
        ?.firstOrNull()
        ?.nextContinuationData
        ?.continuation
)
private fun BrowseResponse.ContinuationContents.MusicPlaylistShelfContinuation?.toSongsPage() = Innertube.ItemsPage(
    items = this
        ?.contents
        ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
        ?.mapNotNull(Innertube.SongItem::from),
    continuation = this
        ?.continuations
        ?.firstOrNull()
        ?.nextContinuationData
        ?.continuation
)


