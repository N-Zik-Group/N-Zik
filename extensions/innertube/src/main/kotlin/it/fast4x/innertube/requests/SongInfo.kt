package it.fast4x.innertube.requests

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.VideoOrSongInfo
import it.fast4x.innertube.utils.runCatchingNonCancellable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ─── ReturnYouTubeDislike API ────────────────────────────────────────────────

@Serializable
data class ReturnYouTubeDislikeResponse(
    val viewCount: Int? = null,
    val likes: Int? = null,
    val dislikes: Int? = null,
)

// ─── YouTube /next response models (ported from riplay-main YouTubeDataPage) ─

@Serializable
private data class YouTubeNextResponse(
    @SerialName("contents") val contents: Contents? = null,
) {
    @Serializable
    data class Contents(
        @SerialName("twoColumnWatchNextResults") val twoColumnWatchNextResults: TwoColumnWatchNextResults? = null,
    ) {
        @Serializable
        data class TwoColumnWatchNextResults(
            @SerialName("results") val results: Results? = null,
        ) {
            @Serializable
            data class Results(
                @SerialName("results") val results: InnerResults? = null,
            ) {
                @Serializable
                data class InnerResults(
                    @SerialName("contents") val contents: List<ContentItem?>? = null,
                ) {
                    @Serializable
                    data class ContentItem(
                        @SerialName("videoPrimaryInfoRenderer") val videoPrimaryInfoRenderer: VideoPrimaryInfoRenderer? = null,
                        @SerialName("videoSecondaryInfoRenderer") val videoSecondaryInfoRenderer: VideoSecondaryInfoRenderer? = null,
                    ) {
                        @Serializable
                        data class VideoPrimaryInfoRenderer(
                            @SerialName("title") val title: Title? = null,
                            @SerialName("viewCount") val viewCount: ViewCount? = null,
                            @SerialName("dateText") val dateText: DateText? = null,
                        ) {
                            @Serializable
                            data class Title(
                                @SerialName("runs") val runs: List<Run>? = null,
                            ) {
                                @Serializable
                                data class Run(@SerialName("text") val text: String? = null)
                            }

                            @Serializable
                            data class ViewCount(
                                @SerialName("videoViewCountRenderer") val videoViewCountRenderer: VideoViewCountRenderer? = null,
                            ) {
                                @Serializable
                                data class VideoViewCountRenderer(
                                    @SerialName("viewCount") val viewCount: ViewCountText? = null,
                                    @SerialName("shortViewCount") val shortViewCount: ViewCountText? = null,
                                ) {
                                    @Serializable
                                    data class ViewCountText(@SerialName("simpleText") val simpleText: String? = null)
                                }
                            }

                            @Serializable
                            data class DateText(@SerialName("simpleText") val simpleText: String? = null)
                        }

                        @Serializable
                        data class VideoSecondaryInfoRenderer(
                            @SerialName("owner") val owner: Owner? = null,
                            @SerialName("attributedDescription") val attributedDescription: AttributedDescription? = null,
                        ) {
                            @Serializable
                            data class AttributedDescription(@SerialName("content") val content: String? = null)

                            @Serializable
                            data class Owner(
                                @SerialName("videoOwnerRenderer") val videoOwnerRenderer: VideoOwnerRenderer? = null,
                            ) {
                                @Serializable
                                data class VideoOwnerRenderer(
                                    @SerialName("title") val title: Title? = null,
                                    @SerialName("navigationEndpoint") val navigationEndpoint: NavigationEndpoint? = null,
                                    @SerialName("thumbnail") val thumbnail: Thumbnail? = null,
                                    @SerialName("subscriberCountText") val subscriberCountText: SubscriberCountText? = null,
                                ) {
                                    @Serializable
                                    data class Title(
                                        @SerialName("runs") val runs: List<Run>? = null,
                                    ) {
                                        @Serializable
                                        data class Run(@SerialName("text") val text: String? = null)
                                    }

                                    @Serializable
                                    data class NavigationEndpoint(
                                        @SerialName("browseEndpoint") val browseEndpoint: BrowseEndpoint? = null,
                                    ) {
                                        @Serializable
                                        data class BrowseEndpoint(@SerialName("browseId") val browseId: String? = null)
                                    }

                                    @Serializable
                                    data class Thumbnail(
                                        @SerialName("thumbnails") val thumbnails: List<ThumbnailItem>? = null,
                                    ) {
                                        @Serializable
                                        data class ThumbnailItem(
                                            @SerialName("url") val url: String? = null,
                                            @SerialName("width") val width: Int? = null,
                                            @SerialName("height") val height: Int? = null,
                                        )
                                    }

                                    // subscriberCountText uses simpleText (not runs) - per riplay-main
                                    @Serializable
                                    data class SubscriberCountText(@SerialName("simpleText") val simpleText: String? = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Dedicated HTTP client for www.youtube.com ───────────────────────────────
// The main Innertube.client has defaultRequest pointing to music.youtube.com,
// which makes YouTube return singleColumnMusicWatchNextResultsRenderer (no stats/description).
// We need a standalone client targeting www.youtube.com for twoColumnWatchNextResults.

private val youtubeWebClient by lazy {
    HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }
    }
}

// ─── SongInfo request ────────────────────────────────────────────────────────

suspend fun Innertube.songInfo(videoId: String): Result<VideoOrSongInfo>? = runCatchingNonCancellable {

    // Build the request body — clientName "WEB" + xClientName 1 gives twoColumnWatchNextResults
    val bodyJson = """
        {
          "context": {
            "client": {
              "clientName": "WEB",
              "clientVersion": "2.20240321.00.00",
              "hl": "en",
              "gl": "US"
            }
          },
          "videoId": "$videoId"
        }
    """.trimIndent()

    val response = youtubeWebClient.post("https://www.youtube.com/youtubei/v1/next") {
        contentType(ContentType.Application.Json)
        header("X-YouTube-Client-Name", "1")
        header("X-YouTube-Client-Version", "2.20240321.00.00")
        header("Origin", "https://www.youtube.com")
        header("Referer", "https://www.youtube.com/watch?v=$videoId")
        setBody(bodyJson)
    }.body<YouTubeNextResponse>()

    val contentList = response.contents?.twoColumnWatchNextResults?.results?.results?.contents

    val videoSecondary = contentList?.find { it?.videoSecondaryInfoRenderer != null }?.videoSecondaryInfoRenderer
    val videoPrimary = contentList?.find { it?.videoPrimaryInfoRenderer != null }?.videoPrimaryInfoRenderer

    // Use returnyoutubedislike API for reliable view/like/dislike counts (same as riplay-main)
    val dislikeResponse = runCatching {
        youtubeWebClient.get("https://returnyoutubedislikeapi.com/Votes?videoId=$videoId")
            .body<ReturnYouTubeDislikeResponse>()
    }.getOrNull()

    VideoOrSongInfo(
        videoId = videoId,
        title = videoPrimary?.title?.runs?.firstOrNull()?.text,
        author = videoSecondary?.owner?.videoOwnerRenderer?.title?.runs?.firstOrNull()?.text,
        authorId = videoSecondary?.owner?.videoOwnerRenderer?.navigationEndpoint?.browseEndpoint?.browseId,
        authorThumbnail = videoSecondary?.owner?.videoOwnerRenderer?.thumbnail?.thumbnails
            ?.find { it.height == 48 }?.url?.replace("s48", "s960"),
        description = videoSecondary?.attributedDescription?.content,
        // subscriberCountText uses simpleText in YouTube WEB responses (per riplay-main)
        subscribers = videoSecondary?.owner?.videoOwnerRenderer?.subscriberCountText?.simpleText
            ?.split(" ")?.firstOrNull(),
        uploadDate = videoPrimary?.dateText?.simpleText,
        viewCount = dislikeResponse?.viewCount?.toString()
            ?: videoPrimary?.viewCount?.videoViewCountRenderer?.shortViewCount?.simpleText
            ?: videoPrimary?.viewCount?.videoViewCountRenderer?.viewCount?.simpleText,
        like = dislikeResponse?.likes?.toString(),
        dislike = dislikeResponse?.dislikes?.toString(),
    )
}
