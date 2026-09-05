package it.fast4x.innertube.requests

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.models.v0624.podcasts.BrowsePodcastsResponse0624
import it.fast4x.innertube.models.v0624.podcasts.MusicShelfContinuation
import it.fast4x.innertube.models.v0624.podcasts.MusicShelfRendererContent


suspend fun Innertube.podcastPage(browseId: String) = runCatching {
    val response = browse(browseId = browseId).body<BrowsePodcastsResponse0624>()

    val listEpisode = arrayListOf<Innertube.Podcast.EpisodeItem>()
    val thumbnail =
        response.background?.musicThumbnailRenderer?.thumbnail?.thumbnails
            ?.map {
                Thumbnail(
                    url = it.url ?: "",
                    width = it.width?.toInt(),
                    height = it.height?.toInt()
                )
            }
    val title =
        response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicResponsiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
    val author =
        response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicResponsiveHeaderRenderer?.let {
                it.straplineTextOne?.runs?.firstOrNull()?.text ?: ""
            }
    val authorThumbnail =
        response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicResponsiveHeaderRenderer?.let {
                it.straplineThumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
                ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
                ?.url
            }
    val description =
        response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
            ?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer
            ?.description?.musicDescriptionShelfRenderer?.description?.runs?.joinToString("") {
                it.text.toString()
            }
    val data =
        response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicShelfRenderer?.contents
    parsePodcastData(data, author).let {
        listEpisode.addAll(it)
    }

    var continueParam =
        response.contents
            ?.twoColumnBrowseResultsRenderer
            ?.secondaryContents
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicShelfRenderer
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation

    while (continueParam != null) {
        val continueData = browse(
            continuation = continueParam,
            browseId = null,
            setLogin = true
        ).body<BrowsePodcastsResponse0624>()

        parseContinuationPodcastEpisodes(
            continueData.continuationContents?.musicShelfContinuation?.contents,
            author,
        ).let {
            listEpisode.addAll(it)
        }

        continueParam =
            continueData.continuationContents
                ?.musicShelfContinuation
                ?.continuations
                ?.firstOrNull()
                ?.nextContinuationData
                ?.continuation
    }

    Innertube.Podcast(
        title = title ?: "",
        author = author,
        authorThumbnail = authorThumbnail,
        thumbnail = thumbnail ?: emptyList(),
        description = description ?: "",
        listEpisode = listEpisode
    )
}

fun parsePodcastData(
    listContent: List<MusicShelfRendererContent>?,
    author: String?
): List<Innertube.Podcast.EpisodeItem> {
        val listEpisode: ArrayList<Innertube.Podcast.EpisodeItem> = arrayListOf()
        listContent?.forEach { content ->
            listEpisode.add(
                Innertube.Podcast.EpisodeItem(
                    title = content.musicMultiRowListItemRenderer?.title?.runs?.firstOrNull()?.text
                        ?: "",
                    author = author,
                    description = content.musicMultiRowListItemRenderer?.description?.runs?.joinToString(
                        separator = ""
                    ) { it.text.toString() } ?: "",
                    thumbnail = content.musicMultiRowListItemRenderer?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
                        ?.map {
                            Thumbnail(
                                url = it.url ?: "",
                                width = it.width?.toInt(),
                                height = it.height?.toInt()
                            )
                        }
                        ?: emptyList(),
                    createdDay = content.musicMultiRowListItemRenderer?.subtitle?.runs
                        ?.firstOrNull()?.text ?: "",
                    durationString = parsePodcastDuration(
                        content.musicMultiRowListItemRenderer?.playbackProgress
                            ?.musicPlaybackProgressRenderer?.durationText?.runs
                            ?.getOrNull(1)?.text ?: ""
                    ),
                    videoId = content.musicMultiRowListItemRenderer?.onTap?.watchEndpoint?.videoID ?: ""
                )
            )
        }

        return listEpisode
}

fun List<Thumbnail>.toListThumbnail(): List<Thumbnail> {
    val list = mutableListOf<Thumbnail>()
    this.forEach {
        list.add(it.toThumbnail())
    }
    return list
}

fun Thumbnail.toThumbnail(): Thumbnail {
    return Thumbnail(
        height = this.height ?: 0,
        url = this.url,
        width = this.width ?: 0
    )
}

fun parseContinuationPodcastEpisodes(
    listContent: List<MusicShelfContinuation.Content>?,
    author: String?,
): List<Innertube.Podcast.EpisodeItem> {
    if (listContent == null || author == null) {
        return emptyList()
    } else {
        val listEpisode: ArrayList<Innertube.Podcast.EpisodeItem> = arrayListOf()
        listContent.forEach { content ->
            listEpisode.add(
                Innertube.Podcast.EpisodeItem(
                    title =
                        content.musicMultiRowListItemRenderer
                            ?.title
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?: "",
                    author = author,
                    description =
                        content.musicMultiRowListItemRenderer?.description?.runs?.joinToString(
                            separator = "",
                        ) { it.text.toString() } ?: "",
                    thumbnail =
                        content.musicMultiRowListItemRenderer
                            ?.thumbnail
                            ?.musicThumbnailRenderer
                            ?.thumbnail
                            ?.thumbnails
                            ?.map {
                                Thumbnail(
                                    url = it.url ?: "",
                                    width = it.width?.toInt(),
                                    height = it.height?.toInt()
                                )
                            }
                            ?: emptyList<Thumbnail>(),
                    createdDay =
                        content.musicMultiRowListItemRenderer
                            ?.subtitle
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?: "",
                    durationString =
                        parsePodcastDuration(
                            content.musicMultiRowListItemRenderer
                                ?.playbackProgress
                                ?.musicPlaybackProgressRenderer
                                ?.durationText
                                ?.runs
                                ?.getOrNull(1)
                                ?.text
                                ?: ""
                        ),
                    videoId =
                        content.musicMultiRowListItemRenderer
                            ?.onTap
                            ?.watchEndpoint
                            ?.videoID
                            ?: "",
                ),
            )
        }
        return listEpisode
    }
}

fun parsePodcastDuration(ytDuration: String): String {
    val hours = Regex("(\\d+)\\s*hr").find(ytDuration)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val minutes = Regex("(\\d+)\\s*min").find(ytDuration)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    return if (hours > 0) {
        String.format("%d:%02d:00", hours, minutes)
    } else {
        String.format("%02d:00", minutes)
    }
}
