package app.n_zik.innertube.requests

import io.ktor.client.call.body
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.BrowseResponse
import app.n_zik.innertube.models.MusicTwoRowItemRenderer
import app.n_zik.innertube.models.MusicResponsiveListItemRenderer
import app.n_zik.innertube.models.MusicShelfRenderer
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.utils.from
import app.n_zik.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.browseCategory(browseId: String, params: String? = null) = runCatchingNonCancellable {
    val response = browse(browseId = browseId, params = params).body<BrowseResponse>()

    val sections = response.contents?.let { contents ->
        contents.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: contents.sectionListRenderer?.contents
    }.orEmpty()

    BrowseResult(
        title = response.header?.musicImmersiveHeaderRenderer?.title?.text
            ?: response.header?.musicDetailHeaderRenderer?.title?.text,
        items = sections.mapNotNull { content ->
            val grid = content.gridRenderer
            val carousel = content.musicCarouselShelfRenderer
            val shelf = content.musicShelfRenderer
            val heading = grid?.header?.gridHeaderRenderer?.title
                ?: carousel?.header?.musicCarouselShelfBasicHeaderRenderer?.title ?: shelf?.title
            val entries = when {
                grid != null -> grid.items.orEmpty().mapNotNull { it.musicTwoRowItemRenderer?.toItem() }
                carousel != null -> carousel.contents.mapNotNull { row ->
                    row.musicTwoRowItemRenderer?.toItem() ?: row.musicResponsiveListItemRenderer?.toCategoryItem()
                    ?: row.musicMultiRowListItemRenderer?.toCategoryEpisode()
                }
                shelf != null -> shelf.contents.orEmpty().mapNotNull { row ->
                    row.musicResponsiveListItemRenderer?.toCategoryItem() ?: row.musicMultiRowListItemRenderer?.toCategoryEpisode()
                }
                else -> emptyList()
            }
            val title = heading?.text ?: return@mapNotNull null
            entries.takeIf { it.isNotEmpty() }?.let { BrowseResult.Item(title, it.distinctBy(Innertube.Item::key)) }
        },
    )
}

data class BrowseResult(
    val title: String?,
    val items: List<Item>
) {
    data class Item(
        val title: String,
        val items: List<Innertube.Item>
    )
}

fun MusicTwoRowItemRenderer.toItem(): Innertube.Item? {
    val titleRun = title?.runs?.firstOrNull()
    val browse = navigationEndpoint?.browseEndpoint ?: titleRun?.navigationEndpoint?.browseEndpoint
    val watch = navigationEndpoint?.watchEndpoint ?: titleRun?.navigationEndpoint?.watchEndpoint
    val artwork = thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()
    val authorRuns = subtitle?.runs.orEmpty().filter { it.navigationEndpoint?.browseEndpoint != null }
    val authors = authorRuns.map { Innertube.Info(it.text, it.navigationEndpoint?.browseEndpoint) }.ifEmpty { null }
    return when {
        browse?.browseId?.startsWith("MPSP") == true -> Innertube.PlaylistItem(
            info = Innertube.Info(titleRun?.text, browse), thumbnail = artwork,
            songCount = null, channel = authors?.firstOrNull() ?: subtitle?.runs?.lastOrNull()?.let { Innertube.Info(it.text, null) },
            isEditable = false,
        )
        isAlbum -> Innertube.AlbumItem.from(this)
        isPlaylist -> Innertube.PlaylistItem.from(this)
        isArtist -> Innertube.ArtistItem.from(this)
        watch?.videoId == null -> null
        watch.type.isCategoryVideo() || !isSong -> Innertube.VideoItem(
            info = Innertube.Info(titleRun?.text, watch), authors = authors, thumbnail = artwork,
            viewsText = null, durationText = null,
        )
        else -> Innertube.SongItem(
            info = Innertube.Info(titleRun?.text, watch), authors = authors, thumbnail = artwork,
            album = null, durationText = null,
            explicit = subtitleBadges.orEmpty().any { it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" },
        )
    }
}

private fun MusicResponsiveListItemRenderer.toCategoryItem(): Innertube.Item? {
    val title = flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
    val browse = navigationEndpoint?.browseEndpoint ?: title?.navigationEndpoint?.browseEndpoint
    val content = MusicShelfRenderer.Content(this, null, null)
    if (browse?.browseId?.startsWith("MPSP") == true || isPlaylist) {
        return Innertube.PlaylistItem(
            info = Innertube.Info(title?.text, browse),
            thumbnail = content.thumbnail, songCount = null,
            channel = content.runs.second.flatten().firstOrNull { it.navigationEndpoint?.browseEndpoint != null }
                ?.let { Innertube.Info(it.text, it.navigationEndpoint?.browseEndpoint) },
            isEditable = false,
        )
    }
    if (isAlbum) return Innertube.AlbumItem.from(content)
    if (isArtist) return Innertube.ArtistItem.from(content)
    val watch = title?.navigationEndpoint?.watchEndpoint ?: navigationEndpoint?.watchEndpoint
        ?: playlistItemData?.let { NavigationEndpoint.Endpoint.Watch(videoId = it.videoId) }
    if (watch?.videoId == null) return null
    return if (watch.type.isCategoryVideo() || navigationEndpoint?.watchEndpoint?.type.isCategoryVideo()) {
        Innertube.VideoItem.from(content) ?: Innertube.VideoItem(
            info = Innertube.Info(title?.text, watch), authors = null, thumbnail = content.thumbnail,
            viewsText = null, durationText = null,
        )
    } else {
        Innertube.SongItem.from(this) ?: Innertube.SongItem(
            info = Innertube.Info(title?.text, watch),
            authors = content.runs.second.flatten().filter { it.navigationEndpoint?.browseEndpoint != null }
                .map { Innertube.Info(it.text, it.navigationEndpoint?.browseEndpoint) }.ifEmpty { null },
            thumbnail = content.thumbnail, album = null,
            durationText = fixedColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.text,
            explicit = badges.orEmpty().any { it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" },
        )
    }
}

private fun String?.isCategoryVideo(): Boolean = this == "MUSIC_VIDEO_TYPE_OMV" || this == "MUSIC_VIDEO_TYPE_UGC"

private fun MusicShelfRenderer.Content.MusicMultiRowListItemRenderer.toCategoryEpisode(): Innertube.SongItem? {
    val endpoint = onTap?.watchEndpoint ?: return null
    val videoId = endpoint.videoId?.takeIf(String::isNotBlank) ?: return null
    val episodeTitle = title?.runs.orEmpty().joinToString("") { it.text }.takeIf(String::isNotBlank) ?: return null
    val authors = secondTitle?.runs.orEmpty().filter { it.text.isNotBlank() }.map {
        Innertube.Info(it.text, it.navigationEndpoint?.browseEndpoint)
    }
    val duration = playbackProgress?.musicPlaybackProgressRenderer?.durationText?.text
    val watch = NavigationEndpoint.Endpoint.Watch(
        params = endpoint.params, playlistId = endpoint.playlistId, videoId = videoId,
        index = endpoint.index, playlistSetVideoId = endpoint.playlistSetVideoId,
        watchEndpointMusicSupportedConfigs = NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs(
            NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig(
                endpoint.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    ?: "MUSIC_VIDEO_TYPE_PODCAST_EPISODE",
            ),
        ),
    )
    return Innertube.SongItem(
        info = Innertube.Info(episodeTitle, watch), authors = authors.ifEmpty { null }, album = null,
        durationText = duration?.takeIf { "min" in it || "hr" in it }?.let(::parsePodcastDuration),
        thumbnail = thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
    )
}
