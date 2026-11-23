package app.n_zik.innertube.requests

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.MusicResponsiveListItemRenderer
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.models.oddElements

internal fun parseArtistSong(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
    return Innertube.SongItem(
        info = Innertube.Info(
            name = renderer.flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                ?.text ?: "",
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
            } ?: emptyList(),
        album = renderer.flexColumns.getOrNull(3)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
            ?.let {
                Innertube.Info(
                    name = it.text,
                    endpoint = it.navigationEndpoint?.browseEndpoint
                )
            },
        durationText = renderer.fixedColumns?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer?.text
            ?.runs?.firstOrNull()
            ?.text,
        thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()
            ?: return null,
        explicit = renderer.badges?.find {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } != null,
    )
}
