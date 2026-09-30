package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.core.coil.thumbnail
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import timber.log.Timber

private const val TAG = "BridgeArtwork"

/** Largest artwork relayed; anything bigger is refused. */
internal const val MAX_ARTWORK_BYTES = 8L * 1_024 * 1_024

/** Stream of a local image and the type its provider declares, if any. */
internal class LocalSource(val stream: InputStream, val declaredType: String?)

/** Which stored thumbnail URLs the bridge can serve (contract §1.1 `hasArtwork`, §10). */
internal object ArtworkUrls {
    private val ONLINE_SCHEMES = listOf("http://", "https://")
    private val LOCAL_SCHEMES = listOf("content://", "file://")

    /** The cleaned URL when it is non-blank with a supported scheme, else `null`. */
    fun relayable(thumbnailUrl: String?): String? =
        thumbnailUrl?.let(::cleanPrefix)?.trim()?.takeIf { url -> isOnline(url) || isLocal(url) }

    fun hasArtwork(thumbnailUrl: String?): Boolean = relayable(thumbnailUrl) != null

    fun isOnline(url: String): Boolean = ONLINE_SCHEMES.any { url.startsWith(it, ignoreCase = true) }

    fun isLocal(url: String): Boolean = LOCAL_SCHEMES.any { url.startsWith(it, ignoreCase = true) }
}

/** Image type detection: only the three types of contract §10 are ever served. */
internal object ImageTypes {
    private val ALLOWED = setOf("image/jpeg", "image/png", "image/webp")

    fun sniff(bytes: ByteArray): String? = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "image/png"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }

    /** The sniffed type, else the declared one when allowed (parameters dropped), else `null` (unusable). */
    fun contentTypeOf(bytes: ByteArray, declared: String?): String? =
        sniff(bytes) ?: declared?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it in ALLOWED }
}

/**
 * Turns a stored thumbnail URL into the artwork answer of contract §10. A local file
 * (`content://…`, `file://…`) is read through [openLocal] — `null`, a
 * [FileNotFoundException], an oversized or unusable image all mean `404`. An `http(s)`
 * image is relayed through [httpClient] at the requested size: any upstream failure,
 * oversized or unusable image is `502`. Any other URL is `404`. Every read is bounded by
 * [MAX_ARTWORK_BYTES]. Blocking: call it off the main thread.
 */
internal class ArtworkRelay(
    private val httpClient: () -> OkHttpClient,
    private val openLocal: (String) -> LocalSource?,
) {
    fun relay(thumbnailUrl: String?, size: Int): ArtworkResult {
        val url = ArtworkUrls.relayable(thumbnailUrl) ?: return ArtworkResult.NotFound
        return if (ArtworkUrls.isLocal(url)) local(url) else online(url, size)
    }

    private fun local(url: String): ArtworkResult {
        val read = try {
            openLocal(url)?.let { source -> source.stream.use { readBounded(it) } to source.declaredType }
        } catch (e: FileNotFoundException) {
            // The album has no embedded or cached art
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not read a local artwork")
            null
        } ?: return ArtworkResult.NotFound
        val bytes = read.first ?: run {
            Timber.tag(TAG).w("Local artwork larger than $MAX_ARTWORK_BYTES bytes, refused")
            return ArtworkResult.NotFound
        }
        if (bytes.isEmpty()) return ArtworkResult.NotFound
        val type = ImageTypes.contentTypeOf(bytes, read.second) ?: run {
            Timber.tag(TAG).w("Local artwork of an unsupported type, refused")
            return ArtworkResult.NotFound
        }
        return ArtworkResult.Image(bytes, type)
    }

    /** Online artwork relayed at the requested size: the PC never reaches the Internet itself. */
    private fun online(url: String, size: Int): ArtworkResult =
        try {
            val request = Request.Builder().url(url.thumbnail(size) ?: url).get().build()
            httpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Artwork upstream answered ${response.code}")
                    return ArtworkResult.UpstreamFailed
                }
                val body = response.body
                val bytes = if (body.contentLength() > MAX_ARTWORK_BYTES) null else body.byteStream().use { readBounded(it) }
                if (bytes == null) {
                    Timber.tag(TAG).w("Artwork upstream body larger than $MAX_ARTWORK_BYTES bytes, refused")
                    return ArtworkResult.UpstreamFailed
                }
                if (bytes.isEmpty()) return ArtworkResult.UpstreamFailed
                val type = ImageTypes.contentTypeOf(bytes, response.header("Content-Type")) ?: run {
                    Timber.tag(TAG).w("Artwork upstream sent an unsupported image type")
                    return ArtworkResult.UpstreamFailed
                }
                ArtworkResult.Image(bytes, type)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not fetch an online artwork")
            ArtworkResult.UpstreamFailed
        }

    /** Reads at most [MAX_ARTWORK_BYTES] + 1 bytes; `null` when the stream holds more than the cap. */
    private fun readBounded(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1_024)
        val limit = MAX_ARTWORK_BYTES + 1
        while (out.size() < limit) {
            val wanted = minOf(buffer.size.toLong(), limit - out.size()).toInt()
            val read = input.read(buffer, 0, wanted)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return if (out.size() > MAX_ARTWORK_BYTES) null else out.toByteArray()
    }
}
