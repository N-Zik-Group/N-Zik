package app.n_zik.android.musicbrainz

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import app.n_zik.android.musicbrainz.models.ExternalLink
import app.n_zik.android.musicbrainz.models.MBAlbumMetadata
import app.n_zik.android.musicbrainz.models.MBArtistDetailResponse
import app.n_zik.android.musicbrainz.models.MBArtistMetadata
import app.n_zik.android.musicbrainz.models.MBArtistRelationEntry
import app.n_zik.android.musicbrainz.models.MBArtistRelationResponse
import app.n_zik.android.musicbrainz.models.MBReleaseGroupDetailResponse
import app.n_zik.android.musicbrainz.models.MBSearchArtistResponse
import app.n_zik.android.musicbrainz.models.MBSearchReleaseGroupResponse
import app.n_zik.android.musicbrainz.models.WikiInfoResult
import app.n_zik.android.musicbrainz.utils.cleanWikipediaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import okhttp3.Protocol
import okhttp3.Response

/**
 * Thrown instead of making a request while the circuit breaker is open
 * (too many consecutive MusicBrainz failures — see [MusicBrainz.rateLimitedGet]).
 */
class MusicBrainzUnavailableException(message: String) : Exception(message)

/**
 * Persists the circuit breaker's cooldown deadline across app restarts (the module
 * itself is JVM-only and cannot read SharedPreferences) — otherwise force-quitting and
 * reopening the app during a MusicBrainz outage would silently reset the breaker and
 * let the app start hammering it again.
 */
object MBCircuitBreakerPersistence {
    var load: () -> Long = { 0L }
    var save: (Long) -> Unit = {}

    // Consecutive-failure count, persisted so a partial streak (e.g. 2 of 3) isn't
    // reset by force-quitting the app — otherwise repeatedly restarting during an
    // outage would keep the streak just below the threshold indefinitely.
    var loadFailures: () -> Int = { 0 }
    var saveFailures: (Int) -> Unit = {}
}

/**
 * MusicBrainz API client.
 *
 * MusicBrainz asks clients to respect a maximum rate of 1 request/second
 * and to identify themselves with a valid User-Agent.
 */
class MusicBrainz {

    companion object {
        private const val BASE_URL = "https://musicbrainz.org/ws/2"
        private const val WIKI_API_URL = "https://en.wikipedia.org/w/api.php"

        /**
         * Root-level offline gate, wired by the app at startup (MainApplication).
         * When non-null and it returns false, every outbound request short-circuits
         * with a synthetic 503. Read live per request. Null until wired keeps the
         * module self-contained for standalone/tests use.
         *
         * Lives on the companion (not [MBNetwork]) because MusicBrainz is a class
         * instantiated per-call-site; a companion property is what the app's
         * `MusicBrainz.offlineGate = ...` wiring resolves to.
         */
        @Volatile
        var offlineGate: (() -> Boolean)? = null

        // Set by the Android app from BuildConfig.VERSION_NAME
        var appVersion: String = "dev"

        // Circuit breaker: after this many consecutive failures (503s or otherwise),
        // MusicBrainz is almost certainly down/throttling us — stop sending it any
        // requests at all for a while instead of continuing to hammer it. Applies to
        // every caller (auto on-view sync, the manual resync button, and the backfill
        // worker) uniformly since they all funnel through this one client instance.
        private const val CIRCUIT_FAILURE_THRESHOLD = 3
        private val CIRCUIT_COOLDOWN_MS = java.util.concurrent.TimeUnit.MINUTES.toMillis(10)
    }

    // MusicBrainz requires "Application/version ( contact-url )" with a real contact
    // point (not a package id) so they can reach out instead of just blocking on abuse.
    // https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting
    private val userAgent get() = "N-Zik/$appVersion ( https://github.com/N-Zik-Group/N-Zik )"

    private val rateLimiter = Mutex()
    private var consecutiveFailures = MBCircuitBreakerPersistence.loadFailures()
        set(value) {
            field = value
            MBCircuitBreakerPersistence.saveFailures(value)
        }
    private var circuitOpenUntil = MBCircuitBreakerPersistence.load()
        set(value) {
            field = value
            MBCircuitBreakerPersistence.save(value)
        }

    /** Milliseconds left before the circuit breaker allows requests again, or 0 if closed. */
    fun circuitOpenRemainingMs(): Long = (circuitOpenUntil - System.currentTimeMillis()).coerceAtLeast(0)

    // MusicBrainz enforces ~1 request/sec per source IP; the delay must gate every
    // individual HTTP call, not a logical operation that may issue several (e.g. a
    // search followed by a detail lookup) — otherwise those fire back-to-back and
    // already exceed the limit before the next operation's own delay even starts.
    //
    // Also enforces the circuit breaker: force-resync (manual button mashing) and the
    // background worker share this same gate, so neither can bypass a MusicBrainz
    // outage by retrying faster than the rest of the app.
    private suspend fun rateLimitedGet(url: String) =
        rateLimiter.withLock {
            val now = System.currentTimeMillis()
            if (now < circuitOpenUntil) {
                throw MusicBrainzUnavailableException(
                    "MusicBrainz circuit open for another ${(circuitOpenUntil - now) / 1000}s after $consecutiveFailures consecutive failures"
                )
            }
            delay(1050) // 1 second + margin
            try {
                val response = client.get(url) { header("User-Agent", userAgent) }
                consecutiveFailures = 0
                response
            } catch (e: Exception) {
                consecutiveFailures++
                if (consecutiveFailures >= CIRCUIT_FAILURE_THRESHOLD) {
                    circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS
                }
                throw e
            }
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                        isLenient = true
                    }
                )
            }

            install(ContentEncoding) {
                gzip()
                deflate()
            }

            MBNetwork.proxy?.let { proxy ->
                engine {
                    this.proxy = proxy
                }
            }

            // Root-level offline gate (app-wired): fast-fail synthetic 503 while offline.
            // Reads the companion's [offlineGate] (accessible unqualified from instance scope).
            engine {
                config {
                    addInterceptor { chain ->
                        val gate = offlineGate
                        if (gate != null && !gate()) {
                            val request = chain.request()
                            MBLogger.d("MusicBrainz", "offline: short-circuited ${request.method} ${request.url.encodedPath}")
                            return@addInterceptor Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(503)
                                .message("No network connection")
                                .build()
                        }
                        chain.proceed(chain.request())
                    }
                }
            }

            defaultRequest {
                url(BASE_URL)
            }
        }
    }

    /**
     * Fetches artist metadata (genres, tags, rating, links) from MusicBrainz.
     */
    suspend fun fetchArtistMetadata(artistName: String): MBArtistMetadata {
        // 1. Search the artist to get the MBID
        val encodedName = URLEncoder.encode(artistName, "UTF-8")
        val searchResponse = rateLimitedGet("$BASE_URL/artist?query=$encodedName&fmt=json")
        val searchResult = searchResponse.body<MBSearchArtistResponse>()
        val mbid = searchResult.artists.maxByOrNull { it.score }?.id
            ?: return MBArtistMetadata(emptyList(), null, null, null, emptyList(), null, null, null, null, null, emptyList(), null)

        // 2. Fetch details with genres
        val detailResponse = rateLimitedGet("$BASE_URL/artist/$mbid?inc=genres+tags+ratings+url-rels&fmt=json")
        val detailResult = detailResponse.body<MBArtistDetailResponse>()

        val genres = detailResult.genres
            .sortedByDescending { it.count }
            .map { it.name.lowercase() }

        val beginYear = detailResult.lifeSpan?.begin?.take(4)?.toIntOrNull()

        val topTags = detailResult.tags
            ?.sortedByDescending { it.count }
            ?.take(5)
            ?.map { it.name.lowercase() }
            ?: emptyList()

        val ratingValue = detailResult.rating?.value
        val ratingVotes = detailResult.rating?.votesCount

        val links = detailResult.relations
            ?.filter { it.url != null && (it.type == "social network" || it.type == "official homepage") }
            ?.map { relation ->
                val url = relation.url!!.resource
                ExternalLink(
                    type = relation.type ?: "unknown",
                    url = url,
                    platform = extractPlatformFromUrl(url, relation.type)
                )
            } ?: emptyList()

        val wikiUrl = detailResult.relations
            ?.firstOrNull { it.url?.resource?.contains("wikipedia.org") == true }
            ?.url?.resource

        return MBArtistMetadata(
            genres = genres,
            artistType = detailResult.type,
            countryCode = detailResult.country,
            beginYear = beginYear,
            topTags = topTags,
            ratingValue = ratingValue,
            ratingVotes = ratingVotes,
            wikipediaUrl = wikiUrl,
            disambiguation = detailResult.disambiguation,
            wikipediaBio = null,
            links = links,
            mbId = mbid
        )
    }

    /**
     * Fetches release-group metadata (genres, tags, rating, links) for an album.
     */
    suspend fun fetchAlbumMetadata(albumTitle: String, artistName: String): MBAlbumMetadata {
        // 1. Search the Release Group
        val query = URLEncoder.encode("releasegroup:\"$albumTitle\" AND artist:\"$artistName\"", "UTF-8")
        val searchResponse = rateLimitedGet("$BASE_URL/release-group?query=$query&fmt=json")
        val searchResult = searchResponse.body<MBSearchReleaseGroupResponse>()
        val mbid = searchResult.releaseGroups.maxByOrNull { it.score }?.id
            ?: return MBAlbumMetadata(emptyList(), null, null, emptyList(), null, null, null, mbId = null)

        // 2. Fetch details
        val detailResponse = rateLimitedGet("$BASE_URL/release-group/$mbid?inc=genres+tags+ratings+url-rels&fmt=json")
        val detailResult = detailResponse.body<MBReleaseGroupDetailResponse>()

        // 3. Extract and format the data
        val genres = detailResult.genres
            .sortedByDescending { it.count }
            .map { it.name.lowercase() }

        // If the type is Album but also Live, prefer "Live"
        val albumType = when {
            detailResult.secondaryTypes.contains("Live") -> "Live"
            detailResult.secondaryTypes.contains("Compilation") -> "Compilation"
            detailResult.secondaryTypes.contains("Remix") -> "Remix"
            else -> detailResult.primaryType // "Album", "Single", "EP"
        }

        // Extract the year from the YYYY date
        val originalYear = detailResult.firstReleaseDate?.take(4)?.toIntOrNull()

        val topTags = detailResult.tags
            ?.sortedByDescending { it.count }
            ?.take(5)
            ?.map { it.name.lowercase() }
            ?: emptyList()

        val ratingValue = detailResult.rating?.value
        val ratingVotes = detailResult.rating?.votesCount

        val wikiUrl = detailResult.relations
            ?.firstOrNull { it.url?.resource?.contains("wikipedia.org") == true }
            ?.url?.resource

        val links = detailResult.relations
            ?.filter { it.url != null && (it.type == "social network" || it.type == "official homepage") }
            ?.map { relation ->
                val url = relation.url!!.resource
                ExternalLink(
                    type = relation.type ?: "unknown",
                    url = url,
                    platform = extractPlatformFromUrl(url, relation.type)
                )
            } ?: emptyList()

        return MBAlbumMetadata(
            genres = genres,
            albumType = albumType,
            originalYear = originalYear,
            topTags = topTags,
            ratingValue = ratingValue,
            ratingVotes = ratingVotes,
            wikipediaUrl = wikiUrl,
            links = links,
            mbId = mbid
        )
    }

    /**
     * Fetches a short Wikipedia extract (intro) for the given search term.
     */
    suspend fun fetchWikipediaExtractByArtist(artistTerm: String): WikiInfoResult? {
        return try {
            val cleanName = artistTerm
                .replace(Regex("(?i)VEVO$|- Topic$|Official"), "")
                .trim()
                .encodeURLParameter()

            val searchUrl = "$WIKI_API_URL?" +
                    "action=query&titles=$cleanName&prop=extracts&exintro&explaintext&format=json&redirects=true"

            val searchResponse = client.get(searchUrl)
            val searchJson = Json.parseToJsonElement(searchResponse.bodyAsText()).jsonObject

            val pages = searchJson["query"]?.jsonObject?.get("pages")?.jsonObject

            // Wikipedia JSON is a map keyed by page id (e.g. "12345");
            // when the page does not exist the key is "-1"
            val pageEntry = pages?.entries?.firstOrNull()

            val isMissing = pageEntry?.value?.jsonObject?.containsKey("missing")

            if (isMissing == true) {
                return null
            }

            val pageData = pageEntry?.value?.jsonObject

            val bio = pageData?.get("extract")?.jsonPrimitive?.contentOrNull

            // Final title after any Wikipedia redirect
            val title = pageData?.get("title")?.jsonPrimitive?.contentOrNull

            if (bio != null && title != null) {
                // Build the URL: "Nirvana (band)" -> "Nirvana_(band)"
                val formattedTitle = title.replace(" ", "_")
                val wikiUrl = "https://en.wikipedia.org/wiki/${formattedTitle.encodeURLParameter()}"

                WikiInfoResult(
                    info = bio.cleanWikipediaText(),
                    url = wikiUrl
                )
            } else {
                null
            }

        } catch (e: Exception) {
            MBLogger.e("MusicBrainz", "Error fetching Wikipedia bio for $artistTerm", e)
            null
        }
    }

    /**
     * Fetches artist relations (members, collaborations, ...) from MusicBrainz.
     */
    suspend fun fetchArtistRelations(artistMbId: String): List<MBArtistRelationEntry> {
        val response = rateLimitedGet("$BASE_URL/artist/$artistMbId?inc=artist-rels&fmt=json")
        return response.body<MBArtistRelationResponse>().relations
    }

    /**
     * Guesses the platform of an external link from its URL.
     */
    private fun extractPlatformFromUrl(url: String, type: String?): String {
        return when {
            type == "official homepage" -> "home"
            "instagram.com" in url -> "instagram"
            "facebook.com" in url -> "facebook"
            "twitter.com" in url || "x.com" in url -> "twitter"
            "youtube.com" in url || "youtu.be" in url -> "youtube"
            "open.spotify.com" in url -> "spotify"
            "music.apple.com" in url -> "applemusic"
            "deezer.com" in url -> "deezer"
            "tidal.com" in url -> "tidal"
            "soundcloud.com" in url -> "soundcloud"
            "discogs.com" in url -> "discogs"
            "rateyourmusic.com" in url -> "rateyourmusic"
            "last.fm" in url -> "lastfm"
            else -> "" // Generic fallback
        }
    }
}
