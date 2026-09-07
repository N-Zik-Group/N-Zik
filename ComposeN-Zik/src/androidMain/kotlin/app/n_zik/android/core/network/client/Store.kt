package app.n_zik.android.core.network.client

import android.content.Context
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import it.fast4x.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException

/**
 * Centralized store for session tokens and cookies.
 */
object Store {

    private val visitorMutex = Mutex()

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
        iosVisitorData = null

        Timber.tag("Store").d("clearSession: all session data cleared")
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
}
