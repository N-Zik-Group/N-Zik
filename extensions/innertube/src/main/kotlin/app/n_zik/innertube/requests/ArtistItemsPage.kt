package app.n_zik.innertube.requests

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.MusicResponsiveListItemRenderer
import app.n_zik.innertube.models.MusicTwoRowItemRenderer
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.models.getContinuation
import app.n_zik.innertube.models.oddElements
import app.n_zik.innertube.utils.from
import app.n_zik.innertube.models.MusicShelfRenderer

data class ArtistItemsPage(
    val title: String,
    val items: List<Innertube.Item>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
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
                //endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint
            )
        }

        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): Innertube.Item? {
            return when {
                renderer.isAlbum -> Innertube.AlbumItem(
                    info = Innertube.Info(
                        renderer.title?.runs?.firstOrNull()?.text,
                        renderer.navigationEndpoint?.browseEndpoint
                    ),
                    authors = null,
                    year = renderer.subtitle?.runs?.lastOrNull()?.text,
                    thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
//                    explicit = renderer.subtitleBadges?.find {
//                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
//                    } != null
                )
                // Video
                renderer.isSong -> {
                    val subtitleParts = renderer.subtitle?.splitBySeparator() ?: emptyList()
                    Innertube.VideoItem(
                        info = Innertube.Info(
                            renderer.title?.runs?.firstOrNull()?.text,
                            renderer.navigationEndpoint?.watchEndpoint
                        ),
                        authors = subtitleParts.getOrNull(0)?.map {
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
                        )?.firstOrNull()?.text,
                    )
                }
                renderer.isVideo -> {
                    val subtitleParts = renderer.subtitle?.splitBySeparator() ?: emptyList()
                    Innertube.VideoItem(
                        info = Innertube.Info(
                            renderer.title?.runs?.firstOrNull()?.text,
                            renderer.navigationEndpoint?.watchEndpoint
                        ),
                        authors = listOfNotNull(
                            subtitleParts.getOrNull(0)?.firstOrNull()?.let {
                                Innertube.Info(it.text, it.navigationEndpoint?.browseEndpoint)
                            }
                        ),
                        viewsText = subtitleParts.getOrNull(1)?.firstOrNull()?.text,
                        thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                        durationText = null // duration is not typically in TwoRowItemRenderer subtitle for videos
                    )
                }
                renderer.isArtist -> Innertube.ArtistItem(
                    info = Innertube.Info(
                        renderer.title?.runs?.firstOrNull()?.text,
                        renderer.navigationEndpoint?.browseEndpoint
                    ),
                    subscribersCountText = renderer.subtitle?.runs?.firstOrNull()?.text,
                    thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()
                )
                renderer.isPlaylist -> {
                    val subtitleParts = renderer.subtitle?.splitBySeparator() ?: emptyList()
                    val authorName = subtitleParts.getOrNull(1)?.firstOrNull()?.text
                    val viewsText = subtitleParts.getOrNull(2)?.firstOrNull()?.text
                    val isSongCount = viewsText?.contains("song", ignoreCase = true) == true
                    val channelName = if (!isSongCount) {
                        listOfNotNull(authorName, viewsText).joinToString(" • ").takeIf { it.isNotBlank() }
                    } else {
                        authorName
                    }
                    Innertube.PlaylistItem(
                        info = Innertube.Info(
                            renderer.title?.runs?.firstOrNull()?.text,
                            renderer.navigationEndpoint?.browseEndpoint
                        ),
                        songCount = if (isSongCount) viewsText?.filter { it.isDigit() }?.toIntOrNull() else null,
                        thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                        channel = channelName?.let { Innertube.Info(it, null) },
                        isEditable = false
                    )
                }
                else -> null
            }?.takeIf {
                try {
                    it.key // Test if key getter crashes
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        fun fromMusicShelfRenderer(renderer: MusicShelfRenderer, headerTitle: String? = null): ArtistItemsPage? {
            return ArtistItemsPage(
                title = renderer.title?.runs?.firstOrNull()?.text ?: headerTitle.orEmpty(),
                items = renderer.contents?.mapNotNull { content ->
                    val songItem = content.musicResponsiveListItemRenderer?.let { fromMusicResponsiveListItemRenderer(it) }
                    if (songItem != null && songItem.album == null) {
                        Innertube.VideoItem.from(content) ?: songItem
                    } else {
                        songItem ?: Innertube.VideoItem.from(content)
                    }
                }.orEmpty(),
                continuation = renderer.continuations?.getContinuation()
            )
        }
    }
}
