package app.n_zik.android.bridge

import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import app.n_zik.android.bridge.pairing.OfferResult
import app.n_zik.android.bridge.pairing.PairingCodeManager
import app.n_zik.android.bridge.pairing.PairingQrPayload
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val REQUEST_ID = "q1w2e3r4t5y6u7i8o9p0aa"

class BridgeServerTest {

    private val acceptAll = DeviceAuthenticator { AuthResult.Accepted(deviceId = "test-device") }

    private fun ApplicationTestBuilder.mount(core: BridgeServerCore) {
        application { core.install(this) }
    }

    private fun errorCode(body: String): String? =
        BridgeJson.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content

    @Test
    fun `meta returns the contract version, server name and server time`() = testApplication {
        mount(BridgeServerCore(serverName = "Pixel test", clock = { 1_790_000_000_000L }))

        val response = client.get("/api/v1/meta")

        assertEquals(HttpStatusCode.OK, response.status)
        val json = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("1.0", json["contractVersion"]?.jsonPrimitive?.content)
        assertEquals("Pixel test", json["serverName"]?.jsonPrimitive?.content)
        assertEquals(1_790_000_000_000L, json["serverTimeMs"]?.jsonPrimitive?.long)
        assertEquals(listOf("pairing.qr", "pairing.manual"), json["features"]?.jsonArray?.map { it.jsonPrimitive.content })
    }

    @Test
    fun `unknown route returns a JSON NOT_FOUND error`() = testApplication {
        mount(BridgeServerCore(serverName = "Pixel test"))

        val response = client.get("/api/v1/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("NOT_FOUND", errorCode(response.bodyAsText()))
    }

    @Test
    fun `missing or non-Bearer authorization is UNAUTHORIZED`() {
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll)

        assertEquals("UNAUTHORIZED", core.authorizationFailure(null)?.code)
        assertEquals("UNAUTHORIZED", core.authorizationFailure("Basic dXNlcjpwYXNz")?.code)
        assertEquals("UNAUTHORIZED", core.authorizationFailure("Bearer ")?.code)
    }

    @Test
    fun `unknown token is DEVICE_REVOKED and a known token is accepted`() {
        val rejecting = BridgeServerCore(serverName = "Pixel test")
        val accepting = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll)

        assertEquals("DEVICE_REVOKED", rejecting.authorizationFailure("Bearer unknown-token")?.code)
        assertNull(accepting.authorizationFailure("Bearer valid-token"))
    }

    @Test
    fun `websocket handshake with a rejected token opens no session`() = testApplication {
        val core = BridgeServerCore(serverName = "Pixel test")
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        val failure = runCatching {
            wsClient.webSocket("/api/v1/ws", request = { bearerAuth("unknown-token") }) { }
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(0, core.sessionCount)
    }

    @Test
    fun `stopping sends the serverStopped frame then closes with 1001`() = testApplication {
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll)
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            withTimeout(5_000) { while (core.sessionCount == 0) delay(10) }
            core.shutdownSessions(BridgeStopCode.TIMEOUT)

            val frame = incoming.receive() as Frame.Text
            val json = BridgeJson.parseToJsonElement(frame.readText()).jsonObject
            assertEquals("serverStopped", json["type"]?.jsonPrimitive?.content)
            assertEquals("TIMEOUT", json["code"]?.jsonPrimitive?.content)

            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals(BridgeContract.CLOSE_SERVER_STOPPED, reason?.code)
        }
    }

    @Test
    fun `requests received while stopping get SERVER_STOPPING`() = testApplication {
        val core = BridgeServerCore(serverName = "Pixel test")
        mount(core)
        core.shutdownSessions(BridgeStopCode.STOP_USER)

        val response = client.get("/api/v1/meta")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("SERVER_STOPPING", errorCode(response.bodyAsText()))
    }

    // --- Pairing (contract §4.5) and device authentication (contract §2, §4.7) ---

    private class PairingFixture {
        val codes = PairingCodeManager()
        val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
        val core = BridgeServerCore(serverName = "Pixel test", deviceStore = store, pairingCodes = codes)

        fun openCode(): String {
            codes.open()
            return requireNotNull(codes.code.value) { "No active code" }.code
        }
    }

    private suspend fun ApplicationTestBuilder.validate(code: String, requestId: String?, deviceName: String = "PC-SALON") =
        client.post("/api/v1/pairing/validate") {
            contentType(ContentType.Application.Json)
            val id = requestId?.let { "\"$it\"" } ?: "null"
            setBody("""{"code":"$code","requestId":$id,"deviceName":"$deviceName"}""")
        }

    @Test
    fun `QR validation with the bound requestId issues a token`() = testApplication {
        val fixture = PairingFixture()
        mount(fixture.core)
        val code = fixture.openCode()
        fixture.codes.bindRequestId(REQUEST_ID)

        val response = validate(code, REQUEST_ID)

        assertEquals(HttpStatusCode.OK, response.status)
        val json = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = json["deviceToken"]?.jsonPrimitive?.content.orEmpty()
        assertEquals(43, token.length)
        assertEquals(11, json["deviceId"]?.jsonPrimitive?.content?.length)
        assertEquals("Pixel test", json["serverName"]?.jsonPrimitive?.content)
        // Port the request reached: 80 on Ktor's test host
        assertEquals(80, json["serverPort"]?.jsonPrimitive?.int)
        assertEquals(listOf("PC-SALON"), fixture.store.devices.value.map { it.deviceName })
        assertNull(fixture.core.authorizationFailure("Bearer $token"))
    }

    @Test
    fun `manual validation with a null requestId is accepted`() = testApplication {
        val fixture = PairingFixture()
        mount(fixture.core)
        val code = fixture.openCode()

        val response = validate("${code.take(3)}-${code.drop(3)}".lowercase(), null)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, fixture.store.devices.value.size)
    }

    @Test
    fun `wrong code, unbound requestId and replay are PAIRING_REJECTED`() = testApplication {
        val fixture = PairingFixture()
        mount(fixture.core)
        val code = fixture.openCode()
        fixture.codes.bindRequestId(REQUEST_ID)
        val wrong = if (code == "222222") "333333" else "222222"

        assertEquals("PAIRING_REJECTED", errorCode(validate(wrong, null).bodyAsText()))
        assertEquals("PAIRING_REJECTED", errorCode(validate(code, "zzzzzzzzzzzzzzzzzzzzzz").bodyAsText()))
        assertEquals(HttpStatusCode.OK, validate(code, REQUEST_ID).status)
        val replay = validate(code, REQUEST_ID)
        assertEquals(HttpStatusCode.Forbidden, replay.status)
        assertEquals("PAIRING_REJECTED", errorCode(replay.bodyAsText()))
    }

    @Test
    fun `validation without an open pairing mode is rejected`() = testApplication {
        mount(BridgeServerCore(serverName = "Pixel test"))

        val response = validate("ABCDEF", null)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("PAIRING_REJECTED", errorCode(response.bodyAsText()))
    }

    @Test
    fun `sixth attempt in a minute is RATE_LIMITED without evaluating the code`() = testApplication {
        val fixture = PairingFixture()
        mount(fixture.core)
        val code = fixture.openCode()

        repeat(5) { assertEquals(HttpStatusCode.Forbidden, validate("222222", null).status) }
        val limited = validate(code, null)

        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        val json = BridgeJson.parseToJsonElement(limited.bodyAsText()).jsonObject
        assertEquals("RATE_LIMITED", json["code"]?.jsonPrimitive?.content)
        assertTrue((json["retryAfterMs"]?.jsonPrimitive?.long ?: 0L) > 0L)
        assertTrue(fixture.store.devices.value.isEmpty())
    }

    @Test
    fun `invalid body is BAD_REQUEST`() = testApplication {
        val fixture = PairingFixture()
        mount(fixture.core)
        val code = fixture.openCode()

        assertEquals("BAD_REQUEST", errorCode(validate(code, null, deviceName = "  ").bodyAsText()))
        val malformed = client.post("/api/v1/pairing/validate") { setBody("{not json") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        val oversized = validate(code, null, deviceName = "x".repeat(5_000))
        assertEquals(HttpStatusCode.BadRequest, oversized.status)
    }

    @Test
    fun `QR offer from the controller carries the code the service core accepts`() = testApplication {
        val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
        val core = BridgeServerController.createCore("Pixel test", store)
        mount(core)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            val offerBody = CompletableFuture<String>()
            thread(isDaemon = true) {
                runCatching {
                    listener.accept().use { socket ->
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
                        offerBody.complete(String(body, 0, read))
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    }
                }.onFailure { offerBody.completeExceptionally(it) }
            }
            try {
                BridgeServerController.attachCore(core)
                BridgeServerController.publish(BridgeState.Running("127.0.0.1", 42420, viaHotspot = false))
                BridgeServerController.openPairing()
                val payload = PairingQrPayload(REQUEST_ID, "PC-SALON", listener.localPort, listOf("127.0.0.1"))

                assertTrue(BridgeServerController.sendOffer(payload) is OfferResult.Accepted)

                val offer = BridgeJson.parseToJsonElement(offerBody.get(5, TimeUnit.SECONDS)).jsonObject
                assertEquals(42420, offer["serverPort"]?.jsonPrimitive?.int)
                assertEquals("Pixel test", offer["serverName"]?.jsonPrimitive?.content)
                val code = offer["code"]?.jsonPrimitive?.content.orEmpty()
                assertEquals(BridgeServerController.pairingCode.value?.code, code)
                coroutineScope {
                    val pairedEvent = async(start = CoroutineStart.UNDISPATCHED) { BridgeServerController.pairedEvents.first() }
                    assertEquals(HttpStatusCode.OK, validate(code, REQUEST_ID).status)
                    assertEquals(listOf("PC-SALON"), store.devices.value.map { it.deviceName })
                    // A successful pairing closes pairing mode and announces the device (success toast)
                    assertNull(BridgeServerController.pairingCode.value)
                    assertEquals("PC-SALON", withTimeout(5_000) { pairedEvent.await() })
                }
            } finally {
                BridgeServerController.closePairing()
                BridgeServerController.attachCore(null)
                BridgeServerController.publish(BridgeState.Stopped)
            }
            assertNull(BridgeServerController.pairingCode.value)
        }
    }

    @Test
    fun `paired token opens the websocket and revocation closes it with 4003`() = testApplication {
        val fixture = PairingFixture()
        mount(fixture.core)
        val other = fixture.store.issue("PC-2")
        val code = fixture.openCode()
        val json = BridgeJson.parseToJsonElement(validate(code, null).bodyAsText()).jsonObject
        val token = json["deviceToken"]?.jsonPrimitive?.content.orEmpty()
        val deviceId = json["deviceId"]?.jsonPrimitive?.content.orEmpty()
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth(token) }) {
            withTimeout(5_000) { while (fixture.core.sessionCount == 0) delay(10) }

            assertTrue(fixture.core.revokeDevice(deviceId))

            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals(BridgeContract.CLOSE_DEVICE_REVOKED, reason?.code)
        }
        assertEquals("DEVICE_REVOKED", fixture.core.authorizationFailure("Bearer $token")?.code)
        assertNull(fixture.core.authorizationFailure("Bearer ${other.deviceToken}"))
    }

    @Test
    fun `server falls back to the next port when the first one is taken`() = runBlocking {
        ServerSocket(0).use { occupied ->
            val taken = occupied.localPort
            val server = BridgeServer(BridgeServerCore(serverName = "Pixel test"), portCandidates = listOf(taken, 0))

            val bound = server.start("127.0.0.1")
            try {
                assertNotEquals(taken, bound)
                assertTrue(bound > 0)
            } finally {
                server.stop(BridgeStopCode.STOP_USER)
            }
        }
    }
}
