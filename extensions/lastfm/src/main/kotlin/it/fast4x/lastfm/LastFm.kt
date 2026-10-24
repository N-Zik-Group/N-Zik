package it.fast4x.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import it.fast4x.lastfm.models.LastFmApiException
import it.fast4x.lastfm.models.LastFmResponse
import it.fast4x.lastfm.models.LastFmUserInfoResponse
import it.fast4x.lastfm.models.largestImageUrl
import it.fast4x.lastfm.utils.LastFmAuthUtils
import it.fast4x.lastfm.utils.ProxyPreferences
import it.fast4x.lastfm.utils.getProxy
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import okhttp3.Response

/**
 * Assembles the raw Now Playing request parameters (unsigned, without session key).
 * The `duration` parameter is included only when it is > 0.
 */
fun nowPlayingParams(
    artist: String,
    track: String,
    album: String?,
    duration: Long,
    apiKey: String
): MutableMap<String, String> =
    mutableMapOf(
        "method" to "track.updateNowPlaying",
        "artist" to artist,
        "track" to track,
        "api_key" to apiKey,
        "format" to "json"
    ).apply {
        album?.takeIf { it.isNotBlank() }?.let { this["album"] = it }
        if (duration > 0L) this["duration"] = duration.toString()
    }

/**
 * Assembles the raw scrobble request parameters (unsigned, without session key).
 * The `duration[0]` parameter is included only when it is > 0.
 */
fun scrobbleParams(
    artist: String,
    track: String,
    timestamp: Long,
    album: String?,
    duration: Long,
    apiKey: String
): MutableMap<String, String> =
    mutableMapOf(
        "method" to "track.scrobble",
        "artist[0]" to artist,
        "track[0]" to track,
        "timestamp[0]" to timestamp.toString(),
        "api_key" to apiKey,
        "format" to "json"
    ).apply {
        album?.takeIf { it.isNotBlank() }?.let { this["album[0]"] = it }
        if (duration > 0L) this["duration[0]"] = duration.toString()
    }

object LastFm {

    /**
     * Root-level offline gate, wired by the app at startup (MainApplication).
     * When non-null and it returns false, every outbound request short-circuits
     * with a synthetic 503. Read live per request. Null until wired keeps the
     * module self-contained for standalone/tests use.
     */
    @Volatile
    var offlineGate: (() -> Boolean)? = null

    private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    private var apiKey: String = ""
    private var apiSecret: String = ""

    var sessionKey: String? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val client by lazy {
        HttpClient(OkHttp) {
            BrowserUserAgent()

            expectSuccess = true

            install(ContentNegotiation) {
                val feature = Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                    // Last.fm may return attribute values (accepted/ignored/corrected)
                    // as quoted strings; strict Int decoding would fail without this
                    isLenient = true
                }

                json(feature)
            }

            install(ContentEncoding) {
                gzip()
                deflate()
            }

            // Root-level offline gate (app-wired): fast-fail synthetic 503 while offline.
            engine {
                config {
                    addInterceptor { chain ->
                        val gate = offlineGate
                        if (gate != null && !gate()) {
                            val request = chain.request()
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

            ProxyPreferences.preference?.let {
                engine {
                    proxy = getProxy(it)
                }
            }

            defaultRequest {
                url(BASE_URL)
            }
        }
    }

    /**
     * Sets the API credentials. Safe to call repeatedly: existing
     * non-empty values are never overwritten.
     */
    @Synchronized
    fun initialize(apiKey: String, apiSecret: String) {
        if (this.apiKey.isEmpty()) this.apiKey = apiKey
        if (this.apiSecret.isEmpty()) this.apiSecret = apiSecret
    }

    private fun requireSessionKey(): String? = sessionKey?.takeIf { it.isNotEmpty() }

    /**
     * Authenticates with the mobile session flow and returns the session key.
     */
    suspend fun getMobileSession(username: String, password: String): Result<String> =
        try {
            val params = mutableMapOf(
                "method" to "auth.getMobileSession",
                "username" to username,
                "password" to password,
                "api_key" to apiKey
            )
            params["api_sig"] = LastFmAuthUtils.generateSignature(params, apiSecret)

            val response: LastFmResponse = client.post {
                params.forEach { (k, v) -> parameter(k, v) }
                parameter("format", "json")
            }.body()

            response.error?.let { code ->
                return Result.failure(LastFmApiException(code, response.message ?: "Invalid credentials"))
            }
            response.session?.key?.let { return Result.success(it) }
            Result.failure(Exception("LastFM: empty mobile session response"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Sends a Now Playing update for the current track.
     *
     * @param duration Track duration in seconds; the `duration` parameter is
     *                 omitted when it is <= 0 (unknown).
     */
    suspend fun updateNowPlaying(artist: String, track: String, album: String? = null, duration: Long = 0L): Result<Unit> =
        try {
            val sk = requireSessionKey() ?: return Result.failure(Exception("LastFM: no session key"))

            val params = nowPlayingParams(artist, track, album, duration, apiKey)
            params["sk"] = sk
            params["api_sig"] = LastFmAuthUtils.generateSignature(params, apiSecret)

            val response: LastFmResponse = client.submitForm(
                formParameters = parameters {
                    params.forEach { (k, v) -> append(k, v) }
                }
            ).body()

            response.toApiResult("Now Playing failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Sends a scrobble for a finished track.
     *
     * @param timestamp Epoch seconds when the track started playing.
     * @param duration Track duration in seconds; the `duration[0]` parameter is
     *                 omitted when it is <= 0 (unknown).
     */
    suspend fun scrobble(artist: String, track: String, timestamp: Long, album: String? = null, duration: Long = 0L): Result<Unit> =
        try {
            val sk = requireSessionKey() ?: return Result.failure(Exception("LastFM: no session key"))

            val params = scrobbleParams(artist, track, timestamp, album, duration, apiKey)
            params["sk"] = sk
            params["api_sig"] = LastFmAuthUtils.generateSignature(params, apiSecret)

            val response: LastFmResponse = client.submitForm(
                formParameters = parameters {
                    params.forEach { (k, v) -> append(k, v) }
                }
            ).body()

            response.toApiResult("Scrobble failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Fetches the user's profile info (no session key required) and
     * returns the largest available profile picture URL, or null when
     * the user has no picture.
     */
    suspend fun getUserPicture(username: String): Result<String?> =
        try {
            val params = mapOf(
                "method" to "user.getinfo",
                "username" to username,
                "api_key" to apiKey,
                "format" to "json"
            )

            val response: LastFmUserInfoResponse = client.post {
                params.forEach { (k, v) -> parameter(k, v) }
            }.body()

            Result.success(response.user?.largestImageUrl())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Loves (love = true) or unloves (love = false) a track.
     */
    suspend fun setLoveStatus(artist: String, track: String, love: Boolean): Result<Unit> =
        try {
            val sk = requireSessionKey() ?: return Result.failure(Exception("LastFM: no session key"))

            val params = mutableMapOf(
                "method" to if (love) "track.love" else "track.unlove",
                "artist" to artist,
                "track" to track,
                "api_key" to apiKey,
                "sk" to sk,
                "format" to "json"
            )
            params["api_sig"] = LastFmAuthUtils.generateSignature(params, apiSecret)

            val response: LastFmResponse = client.submitForm(
                formParameters = parameters {
                    params.forEach { (k, v) -> append(k, v) }
                }
            ).body()

            response.toApiResult("Love/unlove failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
