package app.n_zik.android.core.network.client

import android.content.Context
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import it.fast4x.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException

/**
 * Centralized store for session tokens and cookies.
 * Ghost cookie fetch + default cookie = NZik-specific improvements.
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

    /**
     * Initialize session from EncryptedSharedPreferences (like Metrolist's DataStore pattern).
     * Should be called once at app startup.
     */
    fun initSession(context: Context) {
        val prefs = context.encryptedPreferences
        val savedCookie = prefs.getString(ytCookieKey, null)
        val savedVisitorData = prefs.getString(ytVisitorDataKey, null)
        val savedDataSyncId = prefs.getString(ytDataSyncIdKey, null)

        if (!savedCookie.isNullOrBlank()) {
            Innertube.cookie = savedCookie
            Timber.tag("Store").d("initSession: restored cookie from preferences (length=${savedCookie.length})")
        }
        if (!savedVisitorData.isNullOrBlank()) {
            Innertube.visitorData = savedVisitorData
            iosVisitorData = savedVisitorData
            Timber.tag("Store").d("initSession: restored visitor data from preferences")
        }
        if (!savedDataSyncId.isNullOrBlank()) {
            Innertube.dataSyncId = savedDataSyncId
            Timber.tag("Store").d("initSession: restored dataSyncId from preferences")
        }
    }

    /**
     * Save session to EncryptedSharedPreferences (like Metrolist's DataStore pattern).
     */
    fun saveSession(context: Context) {
        val prefs = context.encryptedPreferences
        prefs.edit().apply {
            Innertube.cookie?.let { putString(ytCookieKey, it) }
            Innertube.visitorData?.let { putString(ytVisitorDataKey, it) }
            Innertube.dataSyncId?.let { putString(ytDataSyncIdKey, it) }
        }.apply()
        Timber.tag("Store").d("saveSession: persisted session to preferences")
    }

    /**
     * Auto-fetch and persist visitor data if missing (like Metrolist).
     */
    suspend fun ensureVisitorData(context: Context): String? {
        iosVisitorData?.let { return it }

        Innertube.visitorData?.takeIf { it.isNotBlank() }?.let {
            iosVisitorData = it
            return it
        }

        val prefs = context.encryptedPreferences
        val savedVisitorData = prefs.getString(ytVisitorDataKey, null)
        if (!savedVisitorData.isNullOrBlank() && savedVisitorData != "null") {
            iosVisitorData = savedVisitorData
            Innertube.visitorData = savedVisitorData
            Timber.tag("Store").d("ensureVisitorData: restored from preferences")
            return savedVisitorData
        }

        Timber.tag("Store").d("ensureVisitorData: fetching fresh visitor data...")
        val freshData = getIosVisitorData()

        if (freshData != null) {
            prefs.edit().putString(ytVisitorDataKey, freshData).apply()
            Timber.tag("Store").d("ensureVisitorData: persisted fresh visitor data")
        }

        return freshData
    }

    /**
     * Clear all session data (like Metrolist's forgetAccount).
     */
    fun clearSession(context: Context) {
        val prefs = context.encryptedPreferences
        prefs.edit().apply {
            remove(ytCookieKey)
            remove(ytVisitorDataKey)
            remove(ytDataSyncIdKey)
        }.apply()

        Innertube.cookie = null
        Innertube.visitorData = null
        Innertube.dataSyncId = null
        cookie = null
        iosVisitorData = null
        ghostResponseHeaders = null
        ghostResponseBody = null

        Timber.tag("Store").d("clearSession: all session data cleared")
    }

    /**
     * Fetch cookies from YouTube watch page (ghost cookie fetch).
     * This improves session handling by getting Set-Cookie headers.
     */
    private suspend fun fetchIfNeeded() {
        if (ghostResponseBody != null && ghostResponseHeaders != null) {
            Timber.tag("Store").d("fetchIfNeeded: already cached, skipping")
            return
        }

        fetchMutex.withLock {
            if (ghostResponseBody != null && ghostResponseHeaders != null) {
                Timber.tag("Store").d("fetchIfNeeded: cached after lock, skipping")
                return@withLock
            }

            Timber.tag("Store").d("fetchIfNeeded: fetching cookies from YouTube...")
            runCatching {
                Innertube.client.get(YT_WATCH_URL) {
                    headers {
                        append(HttpHeaders.Connection, "Close")
                        append(HttpHeaders.Host, "www.youtube.com")
                        append(HttpHeaders.Cookie, DEFAULT_COOKIE)
                        append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3")
                        append("Sec-Fetch-Mode", "navigate")
                    }
                }
            }.fold(
                onSuccess = {
                    ghostResponseHeaders = it.headers
                    ghostResponseBody = it.bodyAsText()
                    val setCookieCount = it.headers.getAll(HttpHeaders.SetCookie)?.size ?: 0
                    Timber.tag("Store").d("fetchIfNeeded: success, received $setCookieCount Set-Cookie headers")
                },
                onFailure = {
                    Timber.tag("Store").e(it, "fetchIfNeeded: FAILED to fetch cookies from YouTube")
                }
            )
        }
    }

    /**
     * Retrieves visitor data via InnerTubeX's fetchFreshVisitorData().
     */
    suspend fun getIosVisitorData(): String? {
        iosVisitorData?.let {
            Timber.tag("Store").d("getIosVisitorData: returning cached visitor data")
            return it
        }

        return try {
            visitorMutex.withLock {
                iosVisitorData?.let {
                    Timber.tag("Store").d("getIosVisitorData: returning cached visitor data (after lock)")
                    return@withLock it
                }

                Timber.tag("Store").d("getIosVisitorData: fetching fresh visitor data from InnerTubeX...")
                val data = try {
                    val result = Innertube.extractionTransport().innerTube.fetchFreshVisitorData()
                    Timber.tag("Store").d("getIosVisitorData: success, data length=${result?.length ?: 0}")
                    result
                } catch (e: CancellationException) {
                    Timber.tag("Store").w("getIosVisitorData: fetch cancelled (session changed), retrying...")
                    val result = Innertube.extractionTransport().innerTube.fetchFreshVisitorData()
                    Timber.tag("Store").d("getIosVisitorData: retry success, data length=${result?.length ?: 0}")
                    result
                }
                iosVisitorData = data
                data
            }
        } catch (e: CancellationException) {
            Timber.tag("Store").w("getIosVisitorData: fetch cancelled after retry, returning null")
            null
        } catch (e: IOException) {
            Timber.tag("Store").w(e, "getIosVisitorData: FAILED - network error, returning null")
            null
        } catch (e: Exception) {
            Timber.tag("Store").e(e, "getIosVisitorData: FAILED - unexpected error, returning null")
            null
        }
    }

    /**
     * Retrieves the network cookie, fetching it if necessary.
     * Uses ghost cookie fetch + default cookie fallback.
     * Safe to call from any thread — never blocks the caller for network I/O.
     */
    suspend fun getCookieSuspend(): String {
        cookie?.let {
            Timber.tag("Store").d("getCookieSuspend: returning cached cookie")
            return it
        }

        Timber.tag("Store").d("getCookieSuspend: fetching fresh cookie...")
        fetchIfNeeded()

        return buildCookieFromHeaders()
    }

    /**
     * Synchronous cookie getter — returns cached cookie or DEFAULT_COOKIE immediately.
     * Never blocks for network I/O. Call [prefetchCookie] at app startup to warm the cache.
     */
    fun getCookie(): String {
        cookie?.let { return it }

        // If ghost headers are already available (prefetched), build cookie now
        if (ghostResponseHeaders != null) {
            return buildCookieFromHeaders()
        }

        Timber.tag("Store").w("getCookie: no cached cookie, returning default (call prefetchCookie at startup)")
        return DEFAULT_COOKIE
    }

    /**
     * Prefetch the ghost cookie in the background.
     * Call this once at app init (e.g. in MainApplication) so getCookie() has data ready.
     */
    fun prefetchCookie() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching { fetchIfNeeded() }
                .onSuccess { buildCookieFromHeaders() }
                .onFailure { Timber.tag("Store").w(it, "prefetchCookie: failed") }
        }
    }

    private fun buildCookieFromHeaders(): String {
        val headers = ghostResponseHeaders
        if (headers != null) {
            val setCookies = headers.getAll(HttpHeaders.SetCookie)
                .orEmpty()
                .joinToString("; ") { it.split(";").first() }
            if (setCookies.isNotBlank()) {
                val finalCookie = "$DEFAULT_COOKIE; $setCookies"
                cookie = finalCookie
                Timber.tag("Store").d("buildCookieFromHeaders: success, cookie length=${finalCookie.length}")
                return finalCookie
            }
        }

        Timber.tag("Store").w("buildCookieFromHeaders: no Set-Cookie headers, returning default")
        return DEFAULT_COOKIE
    }
}
