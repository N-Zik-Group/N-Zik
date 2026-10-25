package app.n_zik.android.modules

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * JVM tests for [ModuleRemoteProbe]: the HEAD probe must report the remote
 * Content-Length and stay null on any failure, without ever transferring the
 * archive body.
 */
class ModuleRemoteProbeTest {

    private val url = "https://repo1.maven.org/maven2/io/github/maxrave-dev/ffmpeg-kit-audio/6.0.1/ffmpeg-kit-audio-6.0.1.aar"

    private fun headResponse(code: Int, contentLength: String? = null): Response {
        val builder = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body("".toResponseBody(null))
        contentLength?.let { builder.header("Content-Length", it) }
        return builder.build()
    }

    @Test
    fun `remoteSizeBytes should return the remote Content-Length on success`() = runBlocking {
        val factory = mockk<Call.Factory> {
            every { newCall(any()) } returns mockk {
                every { execute() } returns headResponse(200, "37261319")
            }
        }

        val size = ModuleRemoteProbe.remoteSizeBytes(url, client = factory)

        assertEquals(37_261_319L, size)
    }

    @Test
    fun `remoteSizeBytes should be null on an HTTP error`() = runBlocking {
        val factory = mockk<Call.Factory> {
            every { newCall(any()) } returns mockk {
                every { execute() } returns headResponse(404, "0")
            }
        }

        val size = ModuleRemoteProbe.remoteSizeBytes(url, client = factory)

        assertNull(size)
    }

    @Test
    fun `remoteSizeBytes should be null when the server sends no Content-Length`() = runBlocking {
        val factory = mockk<Call.Factory> {
            every { newCall(any()) } returns mockk {
                every { execute() } returns headResponse(200)
            }
        }

        val size = ModuleRemoteProbe.remoteSizeBytes(url, client = factory)

        assertNull(size)
    }

    @Test
    fun `remoteSizeBytes should be null when the request fails`() = runBlocking {
        val factory = mockk<Call.Factory> {
            every { newCall(any()) } returns mockk {
                every { execute() } throws java.io.IOException("network down")
            }
        }

        val size = ModuleRemoteProbe.remoteSizeBytes(url, client = factory)

        assertNull(size)
    }
}
