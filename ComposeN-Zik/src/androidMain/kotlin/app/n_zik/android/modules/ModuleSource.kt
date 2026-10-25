package app.n_zik.android.modules

import app.n_zik.android.core.network.client.NetworkClientFactory
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import timber.log.Timber

/**
 * Downloads module archives. Implemented with OkHttp via [NetworkClientFactory] so
 * proxy settings are respected (same pattern as `UpdateDownloadManager`).
 */
interface ModuleSource {
    /**
     * Downloads [def]'s archive into [destination], reporting 0f..1f through
     * [onProgress]. Throws [IOException] on failure; the caller's coroutine
     * cancellation unblocks the transfer (see [cancelActiveDownload]).
     */
    suspend fun download(def: ModuleDefinition, destination: File, onProgress: (Float) -> Unit)

    /** Cancels the in-flight download, if any (no-op when idle). */
    fun cancelActiveDownload()
}

/** OkHttp-based [ModuleSource]; the production implementation. */
object OkHttpModuleSource : ModuleSource {

    private const val TAG = "ModuleSource"

    @Volatile
    private var activeCall: okhttp3.Call? = null

    override suspend fun download(def: ModuleDefinition, destination: File, onProgress: (Float) -> Unit) {
        withContext(NzikDispatchers.DATA) {
            destination.parentFile?.mkdirs()
            val request = Request.Builder().url(def.downloadUrl).build()
            val call = NetworkClientFactory.getClient().newCall(request)
            activeCall = call
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} ${response.message}")
                    }
                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()
                    var totalBytes = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                ensureActive()
                                output.write(buffer, 0, read)
                                totalBytes += read
                                if (contentLength > 0) {
                                    onProgress((totalBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
            } finally {
                if (activeCall === call) activeCall = null
            }
        }
    }

    override fun cancelActiveDownload() {
        runCatching { activeCall?.cancel() }
            .onFailure { Timber.tag(TAG).w(it, "Failed to cancel module download") }
        activeCall = null
    }
}
