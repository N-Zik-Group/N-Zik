package app.n_zik.android.musicbrainz

import java.net.InetSocketAddress
import java.net.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyBoundClientTest {
    @Test
    fun `reset replaces the client and allows a pending response body to finish`() = runTest {
        val originalProxy = MBNetwork.proxy
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example", 8080))
        val created = mutableListOf<Proxy?>()
        val closed = mutableListOf<Int>()
        val clients = ProxyBoundClient({ configuredProxy ->
            created.add(configuredProxy)
            created.size
        }, { closed.add(it) })
        val readingBody = CompletableDeferred<Unit>()
        val finishBody = CompletableDeferred<Unit>()

        try {
            MBNetwork.proxy = proxy
            val pending = async {
                clients.use { client ->
                    readingBody.complete(Unit)
                    finishBody.await()
                    client
                }
            }
            readingBody.await()

            MBNetwork.proxy = null
            assertEquals(2, clients.use { it })
            assertEquals(2, clients.use { it })
            assertEquals(listOf(proxy, null), created)
            assertTrue(closed.isEmpty())

            finishBody.complete(Unit)
            assertEquals(1, pending.await())
            assertEquals(listOf(1), closed)
        } finally {
            finishBody.complete(Unit)
            MBNetwork.proxy = originalProxy
        }
    }
}
