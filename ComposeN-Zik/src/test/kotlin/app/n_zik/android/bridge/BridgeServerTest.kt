package app.n_zik.android.bridge

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.ServerSocket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        assertEquals(0, json["features"]?.jsonArray?.size)
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
