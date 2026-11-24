package app.n_zik.android.core.network.client

import app.n_zik.innertube.Innertube
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InnertubeProfileCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun repeatedConfigurationKeepsNetworkClientsUntilTheProxyChanges() {
        val directory = folder.newFolder("network")
        fun proxy(port: Int) = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.test", port))

        NetworkClientFactory.configure(proxy(8080), directory)
        val okhttp = NetworkClientFactory.getClient()
        val cache = checkNotNull(okhttp.cache)
        val ktorClients = mutableListOf<HttpClient>()
        try {
            val cachedKtor = NetworkClientFactory.getKtorClient(cacheless = false).also(ktorClients::add)
            val cachelessKtor = NetworkClientFactory.getKtorClient().also(ktorClients::add)
            val innertube = Innertube.client

            NetworkClientFactory.configure(proxy(8080), File(directory.path))

            assertSame(okhttp, NetworkClientFactory.getClient())
            assertSame(cachedKtor, NetworkClientFactory.getKtorClient(cacheless = false))
            assertSame(cachelessKtor, NetworkClientFactory.getKtorClient())
            assertSame(innertube, Innertube.client)

            val newProxy = proxy(8081)
            NetworkClientFactory.configure(newProxy, directory)

            val updated = NetworkClientFactory.getClient()
            assertNotSame(okhttp, updated)
            assertEquals(newProxy, updated.proxy)
            assertSame(cache, updated.cache)
            assertNotSame(cachedKtor, NetworkClientFactory.getKtorClient(cacheless = false).also(ktorClients::add))
            assertNotSame(cachelessKtor, NetworkClientFactory.getKtorClient().also(ktorClients::add))
        } finally {
            NetworkClientFactory.configure(null, null)
            ktorClients.forEach(HttpClient::close)
            cache.close()
        }
    }

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
