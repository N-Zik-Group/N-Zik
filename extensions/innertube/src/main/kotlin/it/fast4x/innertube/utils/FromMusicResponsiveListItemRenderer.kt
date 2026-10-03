package it.fast4x.innertube.utils

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.Innertube.getBestQuality
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.NavigationEndpoint

fun Innertube.SongItem.Companion.from(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
    val albumId = renderer
        .flexColumns
        .getOrNull(2)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        ?.firstOrNull()
        ?.navigationEndpoint?.browseEndpoint?.browseId

    val albumRow = if (albumId == null) 3 else 2

    val explicitBadge = if (renderer
        .badges
        ?.find {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } != null) "e:" else ""

    return Innertube.SongItem(
        info = renderer
            .flexColumns
            .getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.let {
                if (it.navigationEndpoint?.endpoint is NavigationEndpoint.Endpoint.Watch) Innertube.Info(
                    name = "$explicitBadge${it.text}",
                    endpoint = it.navigationEndpoint.endpoint as NavigationEndpoint.Endpoint.Watch
                ) else null
            },
        // Keep only byline runs linked to a browse endpoint: artist names are the
        // only runs pointing to an artist page, while plain-text runs are byline
        // fragments (separators, play counts such as "162K plays", timestamps such
        // as "51:03") that must not pollute the song's authors. Same convention as
        // the two-row isSong parser in HomePage.
        authors = renderer
            .flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.filter { it.navigationEndpoint?.browseEndpoint != null }
            ?.map { Innertube.Info(name = it.text, endpoint = it.navigationEndpoint?.browseEndpoint) }
            ?.filter { !it.name.isNullOrBlank() }
            ?.takeIf(List<Any>::isNotEmpty),
        durationText = renderer
            .fixedColumns
            ?.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.getOrNull(0)
            ?.text,
        album = renderer
            .flexColumns
            .getOrNull(albumRow)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.getBestQuality(),
            //?.lastOrNull(),
        explicit = renderer
            .badges
            ?.find {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
           } != null,
    )      .takeIf { it.info?.endpoint?.videoId != null }
}
