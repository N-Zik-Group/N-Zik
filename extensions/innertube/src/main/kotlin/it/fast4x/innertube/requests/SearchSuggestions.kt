package it.fast4x.innertube.requests

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.Innertube.getBestQuality
import it.fast4x.innertube.models.GetSearchSuggestionsResponse
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.SearchSuggestionsResponse
import it.fast4x.innertube.models.oddElements
import it.fast4x.innertube.models.splitBySeparator
import it.fast4x.innertube.utils.runCatchingNonCancellable


suspend fun Innertube.searchSuggestions(input: String) = runCatchingNonCancellable {
    val response = getSearchSuggestions(input = input).body<SearchSuggestionsResponse>()

    response
        .contents
        ?.firstOrNull()
        ?.searchSuggestionsSectionRenderer
        ?.contents
        ?.mapNotNull { content ->
            content
                .searchSuggestionRenderer
                ?.navigationEndpoint
                ?.searchEndpoint
                ?.query
        }
}

suspend fun Innertube.searchSuggestionsWithItems(input: String) = runCatchingNonCancellable {
    val response = getSearchSuggestions(input = input).body<GetSearchSuggestionsResponse>()

    val queries = response.contents?.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull { content ->
        content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text.toString() }
    }.orEmpty()

    val recommendedItems =
        response.contents?.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull {
            it.musicResponsiveListItemRenderer?.let { renderer ->
                SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
            }
        }.orEmpty()

    Innertube.SearchSuggestions(
        queries = queries,
        recommendedSong = recommendedItems.filterIsInstance<Innertube.SongItem>().firstOrNull(),
        recommendedPlaylist = recommendedItems.filterIsInstance<Innertube.PlaylistItem>().firstOrNull(),
        recommendedAlbum = recommendedItems.filterIsInstance<Innertube.AlbumItem>().firstOrNull(),
        recommendedArtist = recommendedItems.filterIsInstance<Innertube.ArtistItem>().firstOrNull(),
        recommendedVideo = recommendedItems.filterIsInstance<Innertube.VideoItem>().firstOrNull(),
    )
}

object SearchSuggestionPage {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): Innertube.Item? {
        return when {
            renderer.isSong -> {
                Innertube.SongItem(
                    info = Innertube.Info(
                        name = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text,
                        endpoint = renderer.navigationEndpoint?.endpoint as NavigationEndpoint.Endpoint.Watch
                    ),
                    authors = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()
                        ?.getOrNull(1)?.oddElements()?.map {
                            Innertube.Info(
                                name = it.text,
                                endpoint = it.navigationEndpoint?.endpoint as NavigationEndpoint.Endpoint.Browse
                            )
                        } ?: return null,
                    album = renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                        ?.let {
                            Innertube.Info(
                                name = it.text,
                                endpoint = it.navigationEndpoint?.endpoint as NavigationEndpoint.Endpoint.Browse
                            )
                        },
                    durationText = null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                        ?: return null,
                    explicit = renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null
                )
            }
            renderer.isArtist -> {
                Innertube.ArtistItem(
                    info = Innertube.Info(
                        name = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: return null,
                        endpoint = renderer.navigationEndpoint?.endpoint as NavigationEndpoint.Endpoint.Browse
                    ),
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                        ?: return null,
                    subscribersCountText = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                )
            }
            renderer.isAlbum -> {
                val secondaryLine = renderer.flexColumns.getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator() ?: return null
                Innertube.AlbumItem(
                    info = Innertube.Info(
                        name = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: return null,
                        endpoint = renderer.navigationEndpoint?.endpoint as NavigationEndpoint.Endpoint.Browse
                    ),
                    authors = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()
                        ?.getOrNull(1)?.oddElements()?.map {
                            Innertube.Info(
                                name = it.text,
                                endpoint = it.navigationEndpoint?.endpoint as NavigationEndpoint.Endpoint.Browse
                            )
                        } ?: return null,
                    year = secondaryLine.lastOrNull()?.firstOrNull()?.text,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                        ?: return null
                )
            }
            else -> null
        }
    }
}
