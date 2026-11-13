package app.n_zik.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NextResponse(
    val contents: Contents?
) {
    @Serializable
    data class MusicQueueRenderer(
        val content: Content?
    ) {
        @Serializable
        data class Content(
            @JsonNames("playlistPanelContinuation")
            val playlistPanelRenderer: PlaylistPanelRenderer?
        ) {
            @Serializable
            data class PlaylistPanelRenderer(
                val contents: List<Content>?,
                val continuations: List<Continuation>?,
            ) {
                @Serializable
                data class Content(
                    val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer?,
                    val automixPreviewVideoRenderer: AutomixPreviewVideoRenderer?,
                ) {

                    @Serializable
                    data class AutomixPreviewVideoRenderer(
                        val content: Content?
                    ) {
                        @Serializable
                        data class Content(
                            val automixPlaylistVideoRenderer: AutomixPlaylistVideoRenderer?
                        ) {
                            @Serializable
                            data class AutomixPlaylistVideoRenderer(
                                val navigationEndpoint: NavigationEndpoint?
                            )
                        }
                    }
                }
            }
        }
    }

    @Serializable
    data class Contents(
        val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer? = null,
        val twoColumnWatchNextResults: TwoColumnWatchNextResults? = null
    ) {
        @Serializable
        data class SingleColumnMusicWatchNextResultsRenderer(
            val tabbedRenderer: TabbedRenderer?
        ) {
            @Serializable
            data class TabbedRenderer(
                val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer?
            ) {
                @Serializable
                data class WatchNextTabbedResultsRenderer(
                    val tabs: List<Tab>?
                ) {
                    @Serializable
                    data class Tab(
                        val tabRenderer: TabRenderer?
                    ) {
                        @Serializable
                        data class TabRenderer(
                            val content: Content?,
                            val endpoint: NavigationEndpoint?,
                            val title: String?
                        ) {
                            @Serializable
                            data class Content(
                                val musicQueueRenderer: MusicQueueRenderer?
                            )
                        }
                    }
                }
            }
        }

        @Serializable
        data class TwoColumnWatchNextResults(
            val results: Results?
        ) {
            @Serializable
            data class Results(
                val results: InnerResults?
            ) {
                @Serializable
                data class InnerResults(
                    val content: List<ContentItem>?
                ) {
                    @Serializable
                    data class ContentItem(
                        val videoSecondaryInfoRenderer: VideoSecondaryInfoRenderer?,
                        val videoPrimaryInfoRenderer: VideoPrimaryInfoRenderer?
                    ) {
                        @Serializable
                        data class VideoSecondaryInfoRenderer(
                            val owner: Owner?,
                            val attributedDescription: AttributedDescription?
                        ) {
                            @Serializable
                            data class Owner(
                                val videoOwnerRenderer: VideoOwnerRenderer?
                            ) {
                                @Serializable
                                data class VideoOwnerRenderer(
                                    val title: Runs?,
                                    val navigationEndpoint: NavigationEndpoint?,
                                    val thumbnail: Thumbnails?,
                                    val subscriberCountText: Runs?
                                )
                            }
                            @Serializable
                            data class AttributedDescription(
                                val content: String?
                            )
                        }

                        @Serializable
                        data class VideoPrimaryInfoRenderer(
                            val title: Runs?,
                            val dateText: Runs?,
                            val viewCount: ViewCount?,
                            val videoActions: VideoActions?
                        ) {
                            @Serializable
                            data class ViewCount(
                                val videoViewCountRenderer: VideoViewCountRenderer?
                            ) {
                                @Serializable
                                data class VideoViewCountRenderer(
                                    val viewCount: Runs?,
                                    val shortViewCount: Runs?
                                )
                            }
                            
                            @Serializable
                            data class VideoActions(
                                val menuRenderer: MenuRenderer?
                            ) {
                                @Serializable
                                data class MenuRenderer(
                                    val topLevelButtons: List<TopLevelButton>?
                                ) {
                                    @Serializable
                                    data class TopLevelButton(
                                        val segmentedLikeDislikeButtonRenderer: SegmentedLikeDislikeButtonRenderer?,
                                        val toggleButtonRenderer: ToggleButtonRenderer?
                                    ) {
                                        @Serializable
                                        data class SegmentedLikeDislikeButtonRenderer(
                                            val likeButton: LikeButton?,
                                            val dislikeButton: DislikeButton?
                                        ) {
                                            @Serializable
                                            data class LikeButton(
                                                val toggleButtonRenderer: ToggleButtonRenderer?
                                            )
                                            @Serializable
                                            data class DislikeButton(
                                                val toggleButtonRenderer: ToggleButtonRenderer?
                                            )
                                        }
                                        @Serializable
                                        data class ToggleButtonRenderer(
                                            val defaultText: Runs?
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
