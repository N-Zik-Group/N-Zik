package app.n_zik.android.easter

import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * View-time sync of the easter egg images (spec-remote-easter-eggs): downloads
 * from the N-Zik repo raw into `filesDir/easter/` with an ETag sidecar, an
 * ETag-conditional revalidation when a copy is already present, streamed 8 KB
 * writes to a temp file, then an atomic rename.
 *
 * @param filesDir app files directory (images live under `easter/`)
 * @param clientProvider source of the OkHttp client (production default: [NetworkClientFactory.getClient],
 *   proxy + offline gate + response cache included for free); injectable for tests
 */
class EasterAssetManager(
    private val filesDir: File,
    private val clientProvider: () -> OkHttpClient = { NetworkClientFactory.getClient() }
) {

    // Process-wide dedup keyed by the image path: each screen composition builds its own
    // manager (remember), so re-entering the screen during a download must not start a
    // second transfer of the same file - two writers would race on the shared temp file.
    // Keying on the absolute path keeps separate filesDirs (unit tests) isolated.
    private companion object {
        private val inflight = ConcurrentHashMap<String, CompletableDeferred<File?>>()

        const val TAG = "EasterAssetManager"
        const val EASTER_DIR = "easter"
        const val ETAG_SUFFIX = "etag"
        const val TMP_SUFFIX = "download"
        const val BUFFER_SIZE = 8 * 1024
        const val HTTP_OK = 200
        const val HTTP_NOT_MODIFIED = 304
        // The synthetic status NetworkGate returns for every request while offline: the
        // expected offline path, not an unexpected server response.
        const val HTTP_OFFLINE_GATE = 503
        const val HEADER_ETAG = "ETag"
        const val HEADER_IF_NONE_MATCH = "If-None-Match"
    }

    private val easterDir: File
        get() = File(filesDir, EASTER_DIR).apply { if (!exists()) mkdirs() }

    /** Local image file of [egg] under `filesDir/easter/`. */
    fun imageFile(egg: EasterEgg): File = File(easterDir, egg.fileName)

    private fun etagFile(egg: EasterEgg): File = File(easterDir, "${egg.fileName}.$ETAG_SUFFIX")

    /**
     * Runs the view-time sync for [egg] and returns the file to display,
     * or null when nothing is displayable (offline, failed download, no cache).
     *
     * Per-egg deduplication: the transfer runs in its own fire-and-forget
     * [NzikDispatchers.DATA] scope, so cancelling this await (leaving the
     * screen) never cancels an in-flight download (LEAVE_MID_DOWNLOAD).
     */
    suspend fun sync(egg: EasterEgg): File? {
        val key = imageFile(egg).absolutePath
        val deferred = inflight.getOrPut(key) {
            CompletableDeferred<File?>().also { pending ->
                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                    val result = runCatching { performSync(egg) }
                        .onFailure { Timber.tag(TAG).e(it, "Easter egg sync failed for %s", egg.name) }
                        .getOrNull()
                    pending.complete(result)
                    inflight.remove(key, pending)
                }
            }
        }
        return deferred.await()
    }

    private fun performSync(egg: EasterEgg): File? {
        val image = imageFile(egg)
        val existing = image.takeIf { it.exists() }
        val savedEtag = existing?.let { readEtag(egg) }

        val request = Request.Builder()
            .url(egg.rawUrl)
            .apply { if (savedEtag != null) header(HEADER_IF_NONE_MATCH, savedEtag) }
            .build()

        return try {
            clientProvider().newCall(request).execute().use { response ->
                when (response.code) {
                    HTTP_OK -> {
                        val remoteEtag = response.header(HEADER_ETAG)
                        // A 200 carrying the stored ETag (OkHttp response cache re-serve)
                        // is unchanged: no rewrite. Sidecar is only written when the
                        // header is present; a 200 without it leaves the sidecar as-is.
                        val unchanged = remoteEtag != null && remoteEtag == savedEtag
                        if (!unchanged) {
                            writeImage(egg, image, response)
                            remoteEtag?.let { writeEtag(egg, it) }
                        }
                        image
                    }

                    HTTP_NOT_MODIFIED -> existing

                    // Offline: NetworkGate short-circuits with a synthetic 503. Expected, not
                    // an unexpected server response — no warning here (the gate already logs).
                    HTTP_OFFLINE_GATE -> existing

                    else -> {
                        Timber.tag(TAG).w("Easter egg %s: unexpected status %d", egg.name, response.code)
                        existing
                    }
                }
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Easter egg %s download failed", egg.name)
            existing
        }
    }

    private fun writeImage(egg: EasterEgg, image: File, response: Response) {
        val temp = File(easterDir, "${egg.fileName}.$TMP_SUFFIX")
        try {
            response.body.byteStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
            }
            // Temp + rename: the image is either fully present or absent
            Files.move(temp.toPath(), image.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    private fun readEtag(egg: EasterEgg): String? =
        etagFile(egg).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun writeEtag(egg: EasterEgg, etag: String) {
        runCatching { etagFile(egg).writeText(etag) }
            .onFailure { Timber.tag(TAG).w(it, "Easter egg %s ETag sidecar write failed", egg.name) }
    }
}
