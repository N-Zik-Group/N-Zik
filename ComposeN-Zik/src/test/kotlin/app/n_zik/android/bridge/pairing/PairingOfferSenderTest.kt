package app.n_zik.android.bridge.pairing

import app.n_zik.android.bridge.BridgeJson
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PairingOfferSenderTest {

    private val offer = PairingOffer(
        requestId = "q1w2e3r4t5y6u7i8o9p0aa",
        code = "K7M2QX",
        serverIps = listOf("192.168.1.14"),
        serverPort = 42420,
        serverName = "Pixel 8",
    )

    /** Minimal stand-in for the PC listener: answers [status] once and captures the request. */
    private fun fakeListener(status: String): Pair<ServerSocket, CompletableFuture<String>> {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val request = CompletableFuture<String>()
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val head = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    val length = head.first { it.startsWith("Content-Length:") }.substringAfter(':').trim().toInt()
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val count = input.read(body, read, length - read)
                        if (count < 0) break
                        read += count
                    }
                    request.complete(head.first() + "\n" + String(body, 0, read))
                    socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: 0\r\n\r\n".toByteArray())
                }
            }.onFailure { request.completeExceptionally(it) }
        }
        return server to request
    }

    @Test
    fun `offer is posted as JSON and 200 is accepted`() {
        val (server, request) = fakeListener("200 OK")
        server.use {
            val result = PairingOfferSender().send(listOf("127.0.0.1"), server.localPort, offer)

            assertEquals(OfferResult.Accepted("127.0.0.1"), result)
            val (line, body) = request.get(5, TimeUnit.SECONDS).split("\n", limit = 2)
            assertEquals("POST /nzik-pair/v1/offer HTTP/1.1", line)
            val json = BridgeJson.parseToJsonElement(body).jsonObject
            assertEquals(1, json["v"]?.jsonPrimitive?.int)
            assertEquals("K7M2QX", json["code"]?.jsonPrimitive?.content)
            assertEquals(42420, json["serverPort"]?.jsonPrimitive?.int)
            assertEquals("192.168.1.14", json["serverIps"]?.jsonArray?.single()?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `410 means the PC request expired`() {
        val (server, _) = fakeListener("410 Gone")
        server.use {
            assertEquals(OfferResult.Expired, PairingOfferSender().send(listOf("127.0.0.1"), server.localPort, offer))
        }
    }

    @Test
    fun `unreachable candidates are skipped until one answers`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        assertEquals(OfferResult.Unreachable, PairingOfferSender().send(listOf("127.0.0.1"), closedPort, offer))

        val (server, _) = fakeListener("200 OK")
        server.use {
            // An address that refuses the connection first, then the listener
            val result = PairingOfferSender().send(listOf("127.0.0.2", "127.0.0.1"), server.localPort, offer)
            assertEquals(OfferResult.Accepted("127.0.0.1"), result)
        }
    }
}
