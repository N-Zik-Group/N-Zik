package it.fast4x.lrclib

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import it.fast4x.lrclib.models.Track
import it.fast4x.lrclib.utils.ProxyPreferences
import it.fast4x.lrclib.utils.getProxy
import it.fast4x.lrclib.utils.runCatchingCancellable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.Duration
import okhttp3.Protocol
import okhttp3.Response

object LrcLib {

    /**
     * Root-level offline gate, wired by the app at startup (MainApplication).
     * When non-null and it returns false, every outbound request short-circuits
     * with a synthetic 503. Read live per request. Null until wired keeps the
     * module self-contained for standalone/tests use.
     */
    @Volatile
    var offlineGate: (() -> Boolean)? = null

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
                }

                json(feature)
                //json(feature, ContentType.Text.Html)
                //json(feature, ContentType.Text.Plain)
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
                url("https://lrclib.net")
            }
        }
    }


    private suspend fun queryLyrics(artist: String, title: String, album: String? = null) =
        client.get("/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            if (album != null) parameter("album_name", album)
        }.body<List<Track>>() //.filter { it.syncedLyrics != null }

    suspend fun lyrics(
        artist: String,
        title: String,
        duration: Duration,
        album: String? = null
    ) = runCatchingCancellable {
        val tracks = queryLyrics(artist, title, album)
        tracks.first().syncedLyrics?.let(LrcLib::Lyrics)
    }

    suspend fun lyricsUnsynced(
        artist: String,
        title: String,
        duration: Duration,
        album: String? = null
    ) = runCatchingCancellable {
        val tracks = queryLyrics(artist, title, album)
        tracks.first().plainLyrics?.let(LrcLib::Lyrics)
    }

    suspend fun lyrics(artist: String, title: String) = runCatchingCancellable {
        queryLyrics(artist = artist, title = title, album = null)
    }

    @JvmInline
    value class Lyrics(val text: String) {

        val sentences: List<Pair<Long, String>>
            get() = mutableListOf(0L to "").apply {
                for (line in text.trim().lines()) {
                    try {
                        val position = line.take(10).run {
                            get(8).digitToInt() * 10L +
                                    get(7).digitToInt() * 100 +
                                    get(5).digitToInt() * 1000 +
                                    get(4).digitToInt() * 10000 +
                                    get(2).digitToInt() * 60 * 1000 +
                                    get(1).digitToInt() * 600 * 1000
                        }

                        add(position to line.substring(10))
                    } catch (_: Throwable) {
                    }
                }
            }

        val plainText: String
            get() = sentences.map { it.second }.filter { it.isNotBlank() }.joinToString("\n")

    }

}
