package app.n_zik.android.core.network.client

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import it.fast4x.innertube.Innertube
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InnertubeProfileCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun innertubeUsesTheCurrentProfileNetworkCache() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = "payload".encodeToByteArray()
            exchange.responseHeaders.add("Cache-Control", "public, max-age=60")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val previousGate = Innertube.offlineGate
        val openedCaches = mutableListOf<Cache>()
        try {
            Innertube.offlineGate = { true }
            val firstUrl = "http://127.0.0.1:${server.address.port}/first"
            val secondUrl = "http://127.0.0.1:${server.address.port}/second"
            NetworkClientFactory.configure(null, folder.newFolder("first-profile"))
            val firstCache = checkNotNull(NetworkClientFactory.getClient().cache).also(openedCaches::add)
            assertEquals("payload", Innertube.client.get(firstUrl).bodyAsText())
            assertTrue(firstUrl in firstCache.urls().asSequence().toList())

            NetworkClientFactory.configure(null, folder.newFolder("second-profile"))
            val secondCache = checkNotNull(NetworkClientFactory.getClient().cache).also(openedCaches::add)
            assertEquals("payload", Innertube.client.get(secondUrl).bodyAsText())
            assertTrue(secondUrl in secondCache.urls().asSequence().toList())
            assertFalse(firstUrl in secondCache.urls().asSequence().toList())
            assertFalse(secondUrl in firstCache.urls().asSequence().toList())
        } finally {
            NetworkClientFactory.configure(null, null)
            Innertube.offlineGate = previousGate
            openedCaches.forEach(Cache::close)
            server.stop(0)
        }
    }
}
