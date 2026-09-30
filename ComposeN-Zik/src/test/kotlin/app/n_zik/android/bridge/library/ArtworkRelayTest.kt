package app.n_zik.android.bridge.library

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2)
private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

/** Body of unknown length (chunked) holding [bytes]. */
private class ChunkedBody(private val bytes: ByteArray) : ResponseBody() {
    override fun contentType() = "image/jpeg".toMediaType()
    override fun contentLength() = -1L
    override fun source(): BufferedSource = Buffer().write(bytes)
}

private fun local(bytes: ByteArray, declaredType: String? = null) = LocalSource(ByteArrayInputStream(bytes), declaredType)

/** A JPEG just over the relay cap. */
private fun oversizedJpeg(): ByteArray = ByteArray((MAX_ARTWORK_BYTES + 1).toInt()).also { JPEG.copyInto(it) }

/** Body declaring a huge length without holding the bytes. */
private class HugeBody : ResponseBody() {
    override fun contentType() = "image/jpeg".toMediaType()
    override fun contentLength() = MAX_ARTWORK_BYTES + 1
    override fun source(): BufferedSource = Buffer()
}

class ArtworkRelayTest {

    private val requested = mutableListOf<String>()

    /** OkHttp client whose every call is answered by [answer], without any network. */
    private fun client(answer: (Interceptor.Chain) -> Response): () -> OkHttpClient {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requested += chain.request().url.toString()
                answer(chain)
            }
            .build()
        return { client }
    }

    private fun Interceptor.Chain.respond(code: Int, body: ResponseBody, contentType: String? = null): Response =
        Response.Builder()
            .request(request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("canned")
            .apply { if (contentType != null) header("Content-Type", contentType) }
            .body(body)
            .build()

    private fun onlineRelay(answer: (Interceptor.Chain) -> Response) =
        ArtworkRelay(client(answer)) { error("no local read expected") }

    private fun localRelay(read: (String) -> LocalSource?) =
        ArtworkRelay(client { error("no network expected") }, read)

    @Test
    fun `online image is fetched at the requested size with its sniffed type`() {
        val relay = onlineRelay { it.respond(200, PNG.toResponseBody(), contentType = "image/jpeg") }

        val result = relay.relay("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj", 300)

        assertTrue(result is ArtworkResult.Image)
        result as ArtworkResult.Image
        assertArrayEquals(PNG, result.bytes)
        assertEquals("image/png", result.contentType)
        assertEquals(listOf("https://lh3.googleusercontent.com/abc=w300-h300-l90-rj"), requested)
    }

    @Test
    fun `a prefixed stored URL is cleaned before the fetch`() {
        val relay = onlineRelay { it.respond(200, JPEG.toResponseBody()) }

        val result = relay.relay("modified:https://lh3.googleusercontent.com/xyz=w120-h120", 544)

        assertTrue(result is ArtworkResult.Image)
        assertEquals("image/jpeg", (result as ArtworkResult.Image).contentType)
        assertEquals(listOf("https://lh3.googleusercontent.com/xyz=w544-h544"), requested)
    }

    @Test
    fun `upstream error statuses are UpstreamFailed`() {
        for (code in listOf(404, 500)) {
            val relay = onlineRelay { it.respond(code, "nope".toResponseBody()) }
            assertEquals(ArtworkResult.UpstreamFailed, relay.relay("https://example.com/a.jpg", 544), "status $code")
        }
    }

    @Test
    fun `chunked upstream body over the cap is UpstreamFailed`() {
        val relay = onlineRelay { it.respond(200, ChunkedBody(oversizedJpeg())) }

        assertEquals(ArtworkResult.UpstreamFailed, relay.relay("https://example.com/a.jpg", 544))
    }

    @Test
    fun `chunked upstream body under the cap is relayed`() {
        val result = onlineRelay { it.respond(200, ChunkedBody(JPEG)) }.relay("https://example.com/a.jpg", 544)

        assertArrayEquals(JPEG, (result as ArtworkResult.Image).bytes)
    }

    @Test
    fun `upstream image of an unsupported type is UpstreamFailed`() {
        val relay = onlineRelay { it.respond(200, "GIF89a".toByteArray().toResponseBody(), contentType = "image/gif") }

        assertEquals(ArtworkResult.UpstreamFailed, relay.relay("https://example.com/a.gif", 544))
    }

    @Test
    fun `URL without a supported scheme is NotFound without any request`() {
        val relay = onlineRelay { error("no request expected") }

        assertEquals(ArtworkResult.NotFound, relay.relay("ftp://example.com/a.jpg", 544))
        assertEquals(ArtworkResult.NotFound, relay.relay("modified:", 544))
        assertEquals(ArtworkResult.NotFound, relay.relay("not a url", 544))
        assertTrue(requested.isEmpty())
        assertTrue(ArtworkUrls.hasArtwork("https://lh3.googleusercontent.com/a"))
        assertTrue(ArtworkUrls.hasArtwork("content://media/external/audio/albumart/1"))
        assertEquals(false, ArtworkUrls.hasArtwork("pinned:"))
        assertEquals(false, ArtworkUrls.hasArtwork("ftp://x"))
    }

    @Test
    fun `local image over the cap or of an unsupported type is NotFound`() {
        val url = "content://media/external/audio/albumart/9"
        assertEquals(ArtworkResult.NotFound, localRelay { local(oversizedJpeg()) }.relay(url, 544))
        assertEquals(ArtworkResult.NotFound, localRelay { local("GIF89a".toByteArray(), "image/gif") }.relay(url, 544))
    }

    @Test
    fun `network failure is UpstreamFailed`() {
        val relay = onlineRelay { throw IOException("unreachable") }

        assertEquals(ArtworkResult.UpstreamFailed, relay.relay("https://example.com/a.jpg", 544))
    }

    @Test
    fun `oversized or empty upstream body is UpstreamFailed`() {
        assertEquals(
            ArtworkResult.UpstreamFailed,
            onlineRelay { it.respond(200, HugeBody()) }.relay("https://example.com/a.jpg", 544),
        )
        assertEquals(
            ArtworkResult.UpstreamFailed,
            onlineRelay { it.respond(200, ByteArray(0).toResponseBody()) }.relay("https://example.com/a.jpg", 544),
        )
    }

    @Test
    fun `missing or blank thumbnail URL is NotFound without any request`() {
        val relay = onlineRelay { error("no request expected") }

        assertEquals(ArtworkResult.NotFound, relay.relay(null, 544))
        assertEquals(ArtworkResult.NotFound, relay.relay("", 544))
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `local content URI is read through the local reader`() {
        var readUrl: String? = null
        val relay = localRelay { url ->
            readUrl = url
            local(JPEG, "image/png")
        }

        val result = relay.relay("content://media/external/audio/albumart/42", 544)

        assertTrue(result is ArtworkResult.Image)
        result as ArtworkResult.Image
        assertArrayEquals(JPEG, result.bytes)
        // The bytes win over the declared type
        assertEquals("image/jpeg", result.contentType)
        assertEquals("content://media/external/audio/albumart/42", readUrl)
    }

    @Test
    fun `local artwork without an image is NotFound`() {
        val url = "content://media/external/audio/albumart/7"
        assertEquals(ArtworkResult.NotFound, localRelay { throw FileNotFoundException("no art") }.relay(url, 544))
        assertEquals(ArtworkResult.NotFound, localRelay { null }.relay(url, 544))
        assertEquals(ArtworkResult.NotFound, localRelay { local(ByteArray(0)) }.relay(url, 544))
        assertEquals(ArtworkResult.NotFound, localRelay { throw SecurityException("denied") }.relay(url, 544))
    }
}
