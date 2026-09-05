package it.fast4x.innertube.utils

import io.ktor.utils.io.CancellationException
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.SectionListRenderer
import it.fast4x.innertube.requests.ArtistItemsPage
import it.fast4x.innertube.requests.PlaylistPage
import java.security.MessageDigest


@JvmName("getPlaylistCompleted")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) break
        seenContinuations.add(continuation)
        requestCount++

        var continuationPage = YtMusic.getPlaylistContinuation(continuation).getOrNull()
        if (continuationPage == null) {
            // Retry up to2 more times with delay
            for (attempt in 1..2) {
                kotlinx.coroutines.delay(1000L * attempt)
                continuationPage = YtMusic.getPlaylistContinuation(continuation).getOrNull()
                if (continuationPage != null) break
            }
        }

        if (continuationPage != null) {
            songs += continuationPage.songs
            continuation = continuationPage.continuation
        } else {
            // Log the failure but try to continue with next continuation if possible
            continuation = null
        }
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = page.continuation,
        description = page.description,
        isEditable = page.isEditable,
    )
}

@JvmName("getArtistItemsPageCompleted")
suspend fun Result<ArtistItemsPage>.completed(): Result<ArtistItemsPage> = runCatching {
    val page = getOrThrow()
    var items = page.items
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50

    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) break
        seenContinuations.add(continuation)
        requestCount++

        var continuationPage = YtMusic.getArtistItemsContinuation(continuation).getOrNull()
        if (continuationPage == null) {
            // Retry up to2 more times with delay
            for (attempt in 1..2) {
                kotlinx.coroutines.delay(1000L * attempt)
                continuationPage = YtMusic.getArtistItemsContinuation(continuation).getOrNull()
                if (continuationPage != null) break
            }
        }

        if (continuationPage != null) {
            items += continuationPage.items
            continuation = continuationPage.continuation
        } else {
            continuation = null
        }
    }
    ArtistItemsPage(
        title = page.title,
        items = items,
        continuation = page.continuation
    )
}

internal fun SectionListRenderer.findSectionByTitle(text: String): SectionListRenderer.Content? {
    return contents?.find { content ->
        val title = content
            .musicCarouselShelfRenderer
            ?.header
            ?.musicCarouselShelfBasicHeaderRenderer
            ?.title
            ?: content
                .musicShelfRenderer
                ?.title

        title
            ?.runs
            ?.firstOrNull()
            ?.text == text
    }
}

internal fun SectionListRenderer.findSectionByStrapline(text: String): SectionListRenderer.Content? {
    return contents?.find { content ->
        content
            .musicCarouselShelfRenderer
            ?.header
            ?.musicCarouselShelfBasicHeaderRenderer
            ?.strapline
            ?.runs
            ?.firstOrNull()
            ?.text == text
    }
}

internal inline fun <R> runCatchingNonCancellable(block: () -> R): Result<R>? {
    val result = runCatching(block)
    return when (result.exceptionOrNull()) {
        is CancellationException -> null
        else -> result
    }
}

internal inline fun <T> runCatchingCancellable(block: () -> T) =
    runCatching(block).takeIf { it.exceptionOrNull() !is CancellationException }

infix operator fun <T : Innertube.Item> Innertube.ItemsPage<T>?.plus(other: Innertube.ItemsPage<T>) =
    other.copy(
        items = (this?.items?.plus(other.items ?: emptyList()) ?: other.items)
            ?.distinctBy { if (it.key.isEmpty()) System.identityHashCode(it).toString() else it.key }
    )

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.substring(0, splitIndex).trim() to part.substring(splitIndex + 1).trim()
        }
        .toMap()

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()
