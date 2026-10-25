package app.n_zik.android.modules

import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request

/**
 * Lightweight remote-state probe for a module archive: an HTTP HEAD against
 * the pinned URL (a few bytes, never the archive itself) reports the exact
 * remote size, or null when the URL is unreachable or serves no
 * Content-Length. Used by the Marketplace "Info" entry to show the remote
 * state without downloading the module. The [Call.Factory] is injectable so
 * the probe is unit-testable on the JVM.
 */
object ModuleRemoteProbe {

    /** Remote archive size in bytes, null when the URL is unreachable. */
    suspend fun remoteSizeBytes(
        url: String,
        client: Call.Factory = NetworkClientFactory.getClient(),
    ): Long? = withContext(NzikDispatchers.DATA) {
        runCatching {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else response.header("Content-Length")?.toLongOrNull()
            }
        }.getOrNull()
    }
}
