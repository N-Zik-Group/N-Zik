package app.n_zik.android.core.network.client

import android.content.Context
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.Context as InnerContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.Blocking
import timber.log.Timber
import java.io.IOException
import java.util.Locale

/**
 * Centralized store for network tokens and cookies.
 */
object Store {

    private const val DEFAULT_COOKIE = "PREF=hl=en&tz=UTC; SOCS=CAI"
    private const val YT_WATCH_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&bpctr=9999999999&has_verified=1"

    private val fetchMutex = Mutex()
    private val visitorMutex = Mutex()

    private var ghostResponseHeaders: Headers? = null
    private var ghostResponseBody: String? = null
    private var cookie: String? = null

    private var iosVisitorData: String? = null

    @Blocking
    private suspend fun fetchIfNeeded() {
        if (ghostResponseBody != null && ghostResponseHeaders != null)
            return

        fetchMutex.withLock {
            // Double-check after acquiring lock
            if (ghostResponseBody != null && ghostResponseHeaders != null)
                return@withLock

            runCatching {
                Innertube.client.get(YT_WATCH_URL) {
                    headers {
                        append(HttpHeaders.Connection, "Close")
                        append(HttpHeaders.Host, "www.youtube.com")
                        append(HttpHeaders.Cookie, DEFAULT_COOKIE)
                        append(HttpHeaders.UserAgent, InnerContext.USER_AGENT_WEB)
                        append("Sec-Fetch-Mode", "navigate")
                    }
                }
            }.fold(
                onSuccess = {
                    // Cache for later use
                    ghostResponseHeaders = it.headers
                    ghostResponseBody = it.bodyAsText()
                },
                onFailure = {
                    Timber.tag("Store").e(it, "Failed to fetch visitorData")
                }
            )
        }
    }

    /**
     * Retrieves visitor data for iOS client.
     * Uses InnerTubeX's fetchFreshVisitorData() instead of NewPipe.
     * Returns null if the network request fails (e.g., no connectivity, DNS failure).
     */
    suspend fun getIosVisitorData(): String? {
        iosVisitorData?.let { return it }

        return try {
            visitorMutex.withLock {
                iosVisitorData?.let { return@withLock it }

                // Use InnerTubeX to fetch visitor data (replaces NewPipe)
                // Catch CancellationException separately — InnerTubeX cancels stale
                // session requests during fetchFreshVisitorData(), which is normal
                // behavior but must not propagate to ExoPlayer's LoadTask.
                val data = try {
                    Innertube.extractionTransport().innerTube.fetchFreshVisitorData()
                } catch (e: CancellationException) {
                    Timber.tag("Store").d("Visitor data fetch cancelled (session changed), retrying...")
                    // Retry once after session change
                    Innertube.extractionTransport().innerTube.fetchFreshVisitorData()
                }
                iosVisitorData = data
                data
            }
        } catch (e: CancellationException) {
            Timber.tag("Store").w("Visitor data fetch cancelled after retry")
            null
        } catch (e: IOException) {
            Timber.tag("Store").w(e, "Failed to fetch iOS visitor data (network error)")
            null
        } catch (e: Exception) {
            Timber.tag("Store").e(e, "Unexpected error fetching iOS visitor data")
            null
        }
    }

    /**
     * Retrieves the network cookie, fetching it if necessary.
     */
    @Blocking
    fun getCookie(): String {
        cookie?.let { return it }

        // runBlocking justified: getCookie() is @Blocking and called from non-suspend contexts
        runBlocking(Dispatchers.IO) { fetchIfNeeded() }

        val headers = ghostResponseHeaders
        if (headers != null) {
            headers.getAll(HttpHeaders.SetCookie)
                .orEmpty()
                .joinToString("; ") {
                    it.split(";").first()
                }
                .let {
                    val finalCookie = "$DEFAULT_COOKIE; $it"
                    cookie = finalCookie
                    return finalCookie
                }
        }

        return DEFAULT_COOKIE
    }
}
