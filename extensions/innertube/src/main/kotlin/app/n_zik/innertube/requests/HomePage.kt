package app.n_zik.innertube.requests

import app.n_zik.innertube.requests.LibraryPage
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.BrowseEndpoint
import app.n_zik.innertube.models.MusicCarouselShelfRenderer
import app.n_zik.innertube.models.MusicTwoRowItemRenderer
import app.n_zik.innertube.models.oddElements
import kotlinx.serialization.Serializable


@Serializable
data class HomePage(
    val sections: List<Section>,
    val chips: List<Innertube.Chip>?,
) {
    @Serializable
    data class Section(
        val title: String,
        val label: String?,
        val thumbnail: String?,
        val endpoint: BrowseEndpoint?,
        val items: List<Innertube.Item?>,
    ) {
        companion object {
            fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {
                return Section(
                    title = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                        ?: "",
                    label = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.strapline?.runs?.firstOrNull()?.text,
                    thumbnail = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),

                    endpoint = BrowseEndpoint(
                        browseId = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId
                            ?: "",
                    ),
                    items = renderer.contents
                        .mapNotNull {
                            fromMusicTwoRowItemRenderer(
                                it.musicTwoRowItemRenderer
                            ) ?: it.musicResponsiveListItemRenderer?.let { listItem ->
                                LibraryPage.fromMusicResponsiveListItemRenderer(listItem)
                            }
                        }
                )
            }

            private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer?): Innertube.Item? {
                return when {
                    renderer?.isSong == true -> {
                        val songSubtitleRuns = renderer.subtitle?.runs?.map { "${it.text}(${it.navigationEndpoint?.browseEndpoint != null})" }
                        Innertube.SongItem(
                            info = Innertube.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.watchEndpoint
                            ),
                            authors = renderer.subtitle?.runs
                                ?.filter { it.navigationEndpoint?.browseEndpoint != null }
                                ?.map {
                                Innertube.Info(
                                    name = it.text,
                                    endpoint = it.navigationEndpoint?.browseEndpoint
                                )
                            },
                            album = null,
                            durationText = null,
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                            explicit = renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null
                        )
                    }

                    renderer?.isAlbum == true -> {
                        Innertube.AlbumItem(
                            info = Innertube.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.browseEndpoint
                            ),
//                            playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
//                                ?.musicPlayButtonRenderer?.playNavigationEndpoint
//                                ?.watchPlaylistEndpoint?.playlistId ?: return null,
//                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            authors = renderer.subtitle?.runs?.oddElements()?.drop(1)?.map {
                                Innertube.Info(
                                    name = it.text,
                                    endpoint = it.navigationEndpoint?.browseEndpoint
                                )
                            },
                            year = renderer.subtitle?.runs?.lastOrNull()?.text,
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
//                            explicit = renderer.subtitleBadges?.find {
//                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
//                            } != null
                        )
                    }

                    renderer?.isPlaylist == true -> {
                        Innertube.PlaylistItem(
                            info = Innertube.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.browseEndpoint
                            ),
                            songCount = null,
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                            channel = null,
                            isEditable = false
                        )
                    }

                    renderer?.isArtist == true -> {
                        Innertube.ArtistItem(
                            info = Innertube.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.browseEndpoint
                            ),
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                            subscribersCountText = null
                        )
                    }

                    renderer?.isVideo == true -> {
                        val subtitleParts = renderer.subtitle?.splitBySeparator() ?: emptyList()
                        Innertube.VideoItem(
                            info = Innertube.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.watchEndpoint
                            ),
                            authors = subtitleParts.getOrNull(0)
                                ?.filter { it.navigationEndpoint?.browseEndpoint != null }
                                ?.map {
                                Innertube.Info(
                                    name = it.text,
                                    endpoint = it.navigationEndpoint?.browseEndpoint
                                )
                            },
                            durationText = subtitleParts.getOrNull(
                                if (subtitleParts.size >= 3) subtitleParts.lastIndex else -1
                            )?.firstOrNull()?.text,
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                            viewsText = subtitleParts.getOrNull(
                                if (subtitleParts.size >= 3) subtitleParts.lastIndex - 1
                                else if (subtitleParts.size == 2) 1
                                else -1
                            )?.firstOrNull()?.text
                        )

                    }

                    else -> {
                        null
                    }
                }
            }

        }
    }
}

