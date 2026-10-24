package app.n_zik.android.core.network.client

import app.n_zik.android.appContext
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

/**
 * Root-level offline gate.
 *
 * While the device has no validated network connection (INTERNET + VALIDATED),
 * every outbound HTTP request short-circuits with a synthetic 503 before it can
 * leave the process, so callers fail fast instead of hanging on a dead
 * connection. This is a safety net on top of the per-feature connectivity
 * checks (e.g. playback's StreamResolutionOfflineException): it guarantees no
 * request leaves the device offline, no matter which code path issued it.
 *
 * Wired at every OkHttp client creation site:
 * - app: [NetworkClientFactory], ShazamRepository, PoTokenWebView
 * - modules: each exposes an `offlineGate: (() -> Boolean)?` slot filled with
 *   [isOnline] by MainApplication (modules are plain JVM and have no Context)
 */
object NetworkGate {

    /**
     * Effective online predicate, evaluated per request: real connectivity AND
     * the per-profile "full offline mode" off — the mode override is checked
     * inside `isNetworkConnected`, so a single read of the flag is enough.
     */
    fun isOnline(): Boolean = NetworkQualityHelper.isNetworkConnected(appContext())

    /** OkHttp interceptor applied at every client creation site. */
    val interceptor: Interceptor = Interceptor { chain ->
        if (!isOnline()) {
            val request = chain.request()
            Timber.tag("NetworkGate").d("offline: short-circuited ${request.method} ${request.url.encodedPath}")
            return@Interceptor offlineResponse(request)
        }
        chain.proceed(chain.request())
    }

    // Fast-fail synthetic 503: existing retry/backoff helpers (1s/2s/4s withRetry,
    // 2000ms ItemsPage backoff, MusicBrainz circuit breaker) settle quickly
    //
    // instead of hanging on a dead connection.
    private fun offlineResponse(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(503)
            .message("No network connection")
            .build()
}
