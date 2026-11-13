package app.n_zik.innertube.requests

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.Innertube.getBestQuality
import app.n_zik.innertube.models.MusicTwoRowItemRenderer
import app.n_zik.innertube.models.oddElements
import app.n_zik.innertube.models.splitBySeparator
import app.n_zik.innertube.utils.InnertubeLogger


object NewReleaseAlbumPage {
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): Innertube.AlbumItem {
        InnertubeLogger.d("NewReleaseAlbumPage", "fromMusicTwoRowItemRenderer: ${renderer.navigationEndpoint?.browseEndpoint}")
        return Innertube.AlbumItem(
            info = Innertube.Info(
                name = renderer.title?.runs?.firstOrNull()?.text ?: "",
                endpoint = renderer.navigationEndpoint?.browseEndpoint
            ),
//            playlistId = renderer.thumbnailOverlay
//                ?.musicItemThumbnailOverlayRenderer?.content
//                ?.musicPlayButtonRenderer?.playNavigationEndpoint
//                ?.watchPlaylistEndpoint?.playlistId ?: return null,
            authors = renderer.subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()
                ?.map {
                    Innertube.Info(
                        name = it.text,
                        endpoint = it.navigationEndpoint?.browseEndpoint
                    )
                } ?: emptyList(),
            year = renderer.subtitle?.runs?.lastOrNull()?.text,
            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
//            explicit = renderer.subtitleBadges?.find {
//                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
//            } != null
        )
    }
}
