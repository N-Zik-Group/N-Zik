package app.n_zik.android.components.ui.screens.rewind.slides

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.json.JSONObject

import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.ArtistPage

@Composable
internal fun RewindArtistArtwork(
    artistName: String,
    primaryUrl: String?,
    modifier: Modifier = Modifier,
    preferWikipedia: Boolean = false,
    circular: Boolean = true,
    enableSlideshow: Boolean = false
) {
    var wikipediaUrl by remember(artistName) { mutableStateOf<String?>(null) }
    var failedUrls by remember(artistName, primaryUrl) { mutableStateOf(emptySet<String>()) }
    var activeIndex by remember(artistName, primaryUrl) { mutableStateOf(0) }
    LaunchedEffect(artistName, primaryUrl, preferWikipedia, enableSlideshow) {
        wikipediaUrl = null
        failedUrls = emptySet()
        activeIndex = 0
        if (artistName.isBlank()) return@LaunchedEffect
        if (rewindShareCaptureActive.value) {
            // Export capture: skip the wiki fetch entirely — the frame settles on the
            // primary artwork only (no network wait, no late portrait).
            return@LaunchedEffect
        }
        if (enableSlideshow || preferWikipedia || primaryUrl.isNullOrBlank()) {
            wikipediaUrl = withContext(NzikDispatchers.DATA) {
                WikipediaArtistImageResolver.findImage(artistName)
            }
        }
    }
    val orderedUrls = if (preferWikipedia) {
        listOf(wikipediaUrl, primaryUrl)
    } else {
        listOf(primaryUrl, wikipediaUrl)
    }
    val candidates = orderedUrls
        .filterNotNull()
        .filter { it.isNotBlank() && it !in failedUrls }
        .distinct()
    LaunchedEffect(candidates, enableSlideshow) {
        activeIndex = activeIndex.coerceIn(0, (candidates.size - 1).coerceAtLeast(0))
        if (enableSlideshow && candidates.size > 1) {
            while (true) {
                delay(7_000)
                // Export capture: stop cycling so a frame cannot land mid-crossfade
                if (rewindShareCaptureActive.value) break
                activeIndex = (activeIndex + 1) % candidates.size
            }
        }
    }
    val selectedUrl = candidates.getOrNull(activeIndex) ?: candidates.firstOrNull()
    Crossfade(
        targetState = selectedUrl,
        animationSpec = if (rewindShareCaptureActive.value) tween(0) else tween(900),
        label = "rewindArtistArtwork"
    ) { imageUrl ->
        RewindArtworkWithFallback(
            imageUrl = imageUrl,
            title = artistName,
            modifier = modifier,
            circular = circular,
            background = rewindColors.value.purple,
            foreground = rewindColors.value.cream,
            onError = {
                if (!imageUrl.isNullOrBlank()) failedUrls = failedUrls + imageUrl
            }
        )
    }
}

@Immutable
internal data class ArtistWikiMetadata(
    val imageUrl: String?,
    val description: String?,
    val bio: String? = null,
    val wikipediaUrl: String? = null
)

/**
 * In-app artist bio for the Top Artist spotlight page, from the same source the
 * artist screen uses: the description already stored on the artist row (written
 * from the Innertube artist page), then the Innertube artist page fetched by
 * browseId. A by-name Wikipedia lookup is deliberately NOT used: for ambiguous
 * or channel-derived names it resolves to unrelated pages (e.g. a same-named
 * song's article), which is what the spotlight used to show.
 *
 * Returns null while loading or when no description is available — the card
 * renders without the "about" section in that case.
 */
@Composable
internal fun rememberArtistBio(
    browseId: String?,
    storedDescription: String? = null
): ArtistWikiMetadata? {
    var metadata by remember(browseId, storedDescription) { mutableStateOf<ArtistWikiMetadata?>(null) }
    LaunchedEffect(browseId, storedDescription) {
        metadata = null
        metadata = withContext(NzikDispatchers.DATA) {
            RewindArtistBioResolver.resolve(browseId, storedDescription)
        }
    }
    return metadata
}

/**
 * Compose-free bio resolution so the in-app-first ordering is unit-testable: a non-blank
 * stored description short-circuits any network fetch, and otherwise the Innertube artist
 * page (fetched by [browseId]) is the sole source. [fetchPage] is injectable for tests.
 */
internal object RewindArtistBioResolver {
    suspend fun resolve(
        browseId: String?,
        storedDescription: String?,
        fetchPage: suspend (String) -> Result<ArtistPage> = { YtMusic.getArtistPage(it) }
    ): ArtistWikiMetadata? {
        val stored = storedDescription?.trim()?.takeIf(String::isNotBlank)
        if (stored != null) {
            return ArtistWikiMetadata(imageUrl = null, description = stored, bio = stored)
        }
        val id = browseId?.trim()?.takeIf(String::isNotBlank) ?: return null
        return fetchPage(id).getOrNull()?.let { page ->
            val description = page.description?.trim()?.takeIf(String::isNotBlank) ?: return@let null
            ArtistWikiMetadata(
                imageUrl = page.artist.thumbnail?.url,
                description = description,
                bio = description
            )
        }
    }
}

private object WikipediaArtistImageResolver {
    suspend fun findImage(artistName: String): String? =
        WikipediaArtistMetadataResolver.find(artistName)?.imageUrl
}

private object WikipediaArtistMetadataResolver {
    private const val MissCacheTtlMs = 5L * 60L * 1000L
    private const val CacheCapacity = 64
    // Bounded cache: process-lifetime retention of every resolved artist would grow without
    // bound (spec GH-275, patch "Wikipedia cache unbounded").
    private val cache = LruCache<String, ArtistWikiMetadata>(CacheCapacity)
    private val misses = ConcurrentHashMap<String, Long>()
    // In-flight coalescing: concurrent searches for the same artist share one network request.
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ArtistWikiMetadata?>>()

    suspend fun find(artistName: String): ArtistWikiMetadata? {
        val key = artistName.trim().lowercase()
        if (key.isBlank()) return null
        cache.get(key)?.let { return it }
        val now = System.currentTimeMillis()
        misses[key]?.let { missedAt ->
            if (now - missedAt < MissCacheTtlMs) return null
            misses.remove(key, missedAt)
        }
        val slot = CompletableDeferred<ArtistWikiMetadata?>()
        val existing = inFlight.putIfAbsent(key, slot)
        val resolved: ArtistWikiMetadata? = if (existing == null) {
            // We own the in-flight slot [slot]: resolve, publish to the waiters, release the
            // slot
            val result = runCatching {
                listOf(
                    "$artistName musician",
                    "$artistName singer",
                    "$artistName rapper",
                    artistName
                ).firstNotNullOfOrNull { query ->
                    runCatching { requestMetadata(artistName, query) }.getOrNull()
                }
            }.getOrNull()
            try {
                slot.complete(result)
                result
            } finally {
                // Remove only when still the slot owner (a newer in-flight search replaced it)
                inFlight.remove(key, slot)
            }
        } else {
            // A concurrent search already owns the slot: await its result instead of firing
            // a duplicate network request
            existing.await()
        }
        if (resolved != null) {
            cache.put(key, resolved)
            misses.remove(key)
        } else {
            misses[key] = now
        }
        return resolved
    }

    private fun requestMetadata(artistName: String, query: String): ArtistWikiMetadata? {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val endpoint =
            "https://en.wikipedia.org/w/api.php?action=query&generator=search" +
                "&gsrsearch=$encoded&gsrnamespace=0&gsrlimit=5" +
                "&prop=pageimages|description|extracts|info&pithumbsize=1000" +
                "&exintro=1&explaintext=1&exchars=480&inprop=url" +
                "&format=json&formatversion=2&redirects=1"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 7000
            connection.setRequestProperty("User-Agent", "NZik-Rewind/1.0")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val pages = JSONObject(body)
                .optJSONObject("query")
                ?.optJSONArray("pages")
                ?: return null
            val normalized = artistName.trim().lowercase()
            val results = buildList {
                for (i in 0 until pages.length()) {
                    val page = pages.optJSONObject(i) ?: continue
                    val title = page.optString("title").trim().lowercase()
                    val source = page.optJSONObject("thumbnail")?.optString("source").orEmpty()
                    val description = page.optString("description")
                        .takeIf { it.isNotBlank() && !it.contains("may refer to", ignoreCase = true) }
                    val bio = page.optString("extract")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .takeIf {
                            it.isNotBlank() &&
                                !it.contains("may refer to", ignoreCase = true) &&
                                !it.contains("disambiguation", ignoreCase = true)
                        }
                    val pageUrl = page.optString("fullurl").takeIf { it.isNotBlank() }
                    if (source.isBlank() && description.isNullOrBlank() && bio.isNullOrBlank()) continue
                    val score = when {
                        title == normalized -> 0
                        title.startsWith("$normalized (") -> 1
                        title.contains(normalized) -> 2
                        else -> 3
                    }
                    add(score to ArtistWikiMetadata(source.ifBlank { null }, description, bio, pageUrl))
                }
            }
            results.minByOrNull { it.first }?.second
        } finally {
            connection.disconnect()
        }
    }
}
