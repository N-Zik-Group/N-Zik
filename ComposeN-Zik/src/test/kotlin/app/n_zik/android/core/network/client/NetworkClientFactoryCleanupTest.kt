package app.n_zik.android.core.network.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkClientFactoryCleanupTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `cleanup retains a response written by another client after confirmation`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = "payload".encodeToByteArray()
            exchange.responseHeaders.add("Cache-Control", "public, max-age=60")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val directory = temp.newFolder("network").canonicalFile
        NetworkClientFactory.configure(null, directory)
        val cache = checkNotNull(NetworkClientFactory.getClient().cache)
        val client = OkHttpClient.Builder().cache(cache).build()
        val oldUrl = "http://127.0.0.1:${server.address.port}/confirmed"
        val newUrl = "http://127.0.0.1:${server.address.port}/new"
        fun urls() = cache.urls().asSequence().toSet()
        fun key(url: String) = url.encodeUtf8().md5().hex()
        try {
            client.newCall(Request.Builder().url(oldUrl).build()).execute().use {
                assertEquals("payload", it.body.string())
            }
            val confirmed = urls()
            assertEquals(setOf(oldUrl), confirmed)
            val replacement = Request.Builder().url(oldUrl).cacheControl(CacheControl.FORCE_NETWORK).build()
            client.newCall(replacement).execute().use {
                assertFalse(NetworkClientFactory.clearBoundProfileCache(directory, setOf(key(oldUrl))) { true })
                assertEquals("payload", it.body.string())
            }
            client.newCall(Request.Builder().url(newUrl).build()).execute().use {
                assertTrue(NetworkClientFactory.clearBoundProfileCache(directory, setOf(key(oldUrl))) { urls() == confirmed })
                assertEquals("payload", it.body.string())
            }
            assertEquals(setOf(newUrl), urls())
            assertFalse(NetworkClientFactory.clearBoundProfileCache(directory, setOf(key(newUrl))) { urls() == confirmed })
            assertEquals(setOf(newUrl), urls())
        } finally {
            NetworkClientFactory.configure(null, null)
            cache.close()
            server.stop(0)
        }
    }
}
