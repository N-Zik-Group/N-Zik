package app.n_zik.innertube.requests

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.InnertubeHttpException
import app.n_zik.innertube.models.BrowseResponse
import app.n_zik.innertube.models.MusicTwoRowItemRenderer
import app.n_zik.innertube.models.oddElements
import app.n_zik.innertube.models.splitBySeparator
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

private val explorePages = ExplorePageCache()

/** Reuse the parsed Explore page briefly across the callers that show its shelves. */
suspend fun Innertube.discoverPage(forceRefresh: Boolean = false): Result<Innertube.DiscoverPage> = try {
    if (offlineGate?.invoke() == false) throw InnertubeHttpException("browse", HttpStatusCode.ServiceUnavailable)
    val extraction = extractionTransport()
    val transport = extraction.innerTube
    val initialSession = transport.sessionSnapshot()
    if (initialSession.visitorData.isNullOrBlank()) transport.fetchFreshVisitorData(initialSession)
    val session = transport.sessionSnapshot()
    fun requireCurrentRequest() {
        // Browse takes its own snapshot at entry. A change followed by a restoration
        // still advances the generation, so neither can publish under the old identity.
        if (extractionTransport().innerTube !== transport || transport.sessionSnapshot() != session) {
            throw CancellationException("YouTube Explore session changed")
        }
    }

    val key = ExploreCacheKey(extraction.generation, session.generation)
    val page = explorePages.load(key, forceRefresh, ::requireCurrentRequest) {
        requireCurrentRequest()
        val response = transport.browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_explore")
        parseExplorePage(response.body())
    }
    requireCurrentRequest()
    Result.success(page)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

private fun Innertube.parseExplorePage(response: BrowseResponse): Innertube.DiscoverPage {
    val shelves = response.contents?.singleColumnBrowseResultsRenderer?.tabs
        ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
    return Innertube.DiscoverPage(
        newReleaseAlbums = shelves?.find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
        }?.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicTwoRowItemRenderer?.toNewReleaseAlbumPage() }
            .orEmpty(),
        moods = shelves?.find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_moods_and_genres"
        }?.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicNavigationButtonRenderer?.toMood() }
            .orEmpty()
    )
}

suspend fun Innertube.discoverPageNewAlbums() = discoverPage().map { page ->
    Innertube.DiscoverPageAlbums(newReleaseAlbums = page.newReleaseAlbums)
}

suspend fun Innertube.discoverPageNewAlbumsComplete() = runCatching {
    val response = browse(browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()

    Innertube.DiscoverPageAlbums(
        newReleaseAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs
            ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer?.items?.mapNotNull { it.musicTwoRowItemRenderer?.toNewReleaseAlbumPage() }
            .orEmpty()
    )
}

fun MusicTwoRowItemRenderer.toNewReleaseAlbumPage() = Innertube.AlbumItem(
    info = Innertube.Info(
        name = title?.text,
        endpoint = navigationEndpoint?.browseEndpoint
    ),
    authors = subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
        Innertube.Info(
            name = it.text,
            endpoint = it.navigationEndpoint?.browseEndpoint
        )
    },
    year = subtitle?.runs?.lastOrNull()?.text,
    thumbnail = thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.firstOrNull()
)
