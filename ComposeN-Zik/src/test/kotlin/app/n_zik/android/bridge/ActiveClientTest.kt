package app.n_zik.android.bridge

import app.n_zik.android.bridge.audio.AudioByteSource
import app.n_zik.android.bridge.audio.AudioLibrary
import app.n_zik.android.bridge.audio.AudioOpenResult
import app.n_zik.android.bridge.audio.AudioStream
import app.n_zik.android.bridge.audio.AudioTrackInfo
import app.n_zik.android.bridge.audio.asAudioStream
import app.n_zik.android.bridge.command.BridgeCommand
import app.n_zik.android.bridge.command.BridgeCommandExecutor
import app.n_zik.android.bridge.command.CommandResult
import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.IssuedDevice
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.bridge.state.playingSample
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSocketUpgrade
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val TRACK = "abcdefghijk"
private const val CRLF = "\r\n"
private val AUDIO_BYTES = ByteArray(1_000) { it.toByte() }

/** Contract §6.1, §6.2, §6.4 and §8.4: one remote client at a time, session replacement and kick. */
class ActiveClientTest {

    private val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
    private val deviceA: IssuedDevice = store.issue("PC-A")
    private val deviceB: IssuedDevice = store.issue("PC-B")
    private val hub = BridgeStateHub()

    private class RecordingExecutor : BridgeCommandExecutor {
        val received = mutableListOf<BridgeCommand>()
        override suspend fun execute(command: BridgeCommand): CommandResult {
            received += command
            return CommandResult.Applied(changed = false, revision = 0)
        }
    }

    private val executor = RecordingExecutor()

    private val audio = object : AudioLibrary {
        override suspend fun track(trackId: String): AudioTrackInfo? = AudioTrackInfo(200_000L).takeIf { trackId == TRACK }
        override suspend fun open(trackId: String, quality: AudioQuality): AudioOpenResult =
            AudioOpenResult.Ready(object : AudioByteSource {
                override val length = AUDIO_BYTES.size.toLong()
                override val contentType = "audio/webm"
                override suspend fun openStream(position: Long, length: Long): AudioStream =
                    ByteArrayInputStream(AUDIO_BYTES, position.toInt(), length.toInt()).asAudioStream()
            })
    }

    private fun newCore(pingTimeoutMs: Long = BridgeContract.PING_TIMEOUT_MS) = BridgeServerCore(
        serverName = "Pixel test",
        deviceStore = store,
        stateHub = hub,
        pingTimeoutMs = pingTimeoutMs,
        commandExecutor = executor,
        audioLibrary = audio,
    )

    private fun ApplicationTestBuilder.mount(core: BridgeServerCore = newCore()): BridgeServerCore =
        core.also { c -> application { c.install(this) } }

    private fun ApplicationTestBuilder.wsClient(): HttpClient = createClient { install(WebSockets) }

    private suspend fun HttpClient.openSession(token: String): DefaultClientWebSocketSession =
        webSocketSession("/api/v1/ws") { bearerAuth(token) }

    private suspend fun DefaultClientWebSocketSession.nextJson(): JsonObject =
        withTimeout(5_000) { BridgeJson.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject }

    private fun JsonObject.type(): String? = this["type"]?.jsonPrimitive?.content

    private class RawResponse(val status: Int, val body: String)

    /**
     * A WebSocket upgrade sent over a raw socket to a real server, so that a refusal's status
     * and JSON body can be read (Ktor clients hide both). The socket stays open: on a `101`
     * the session lives until the caller closes it.
     */
    private fun rawUpgrade(port: Int, token: String?): Pair<Socket, RawResponse> {
        val socket = Socket("127.0.0.1", port).apply { soTimeout = 5_000 }
        val lines = listOfNotNull(
            "GET /api/v1/ws HTTP/1.1",
            "Host: 127.0.0.1:$port",
            "Connection: Upgrade",
            "Upgrade: websocket",
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==",
            "Sec-WebSocket-Version: 13",
            token?.let { "Authorization: Bearer $it" },
        )
        socket.getOutputStream().apply {
            write(lines.joinToString(CRLF, postfix = CRLF + CRLF).toByteArray(Charsets.US_ASCII))
            flush()
        }
        val input = socket.getInputStream()
        val head = readHead(input).split(CRLF)
        val status = head.first().split(' ')[1].toInt()
        val length = head.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toInt()
        val body = if (status != 101 && length != null) String(input.readNBytes(length), Charsets.UTF_8) else ""
        return socket to RawResponse(status, body)
    }

    private fun readHead(input: InputStream): String {
        val head = StringBuilder()
        while (!head.endsWith(CRLF + CRLF)) {
            val byte = input.read()
            check(byte >= 0) { "Connection closed in the response head" }
            head.append(byte.toChar())
        }
        return head.removeSuffix(CRLF + CRLF).toString()
    }

    private suspend fun ApplicationTestBuilder.librarySongs(token: String?): HttpResponse =
        client.get("/api/v1/library/songs") { token?.let { bearerAuth(it) } }

    private suspend fun ApplicationTestBuilder.forge(token: String): HttpResponse =
        client.post("/api/v1/audio/$TRACK/url") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"quality":"high"}""")
        }

    private suspend fun ApplicationTestBuilder.forgedPath(token: String): String {
        val response = forge(token)
        assertEquals(HttpStatusCode.OK, response.status)
        val url = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject["url"]?.jsonPrimitive?.content
        return Url(requireNotNull(url)).encodedPathAndQuery
    }

    private fun json(response: String): JsonObject = BridgeJson.parseToJsonElement(response).jsonObject

    /** Asserts the `409 CONFLICT_ACTIVE_CLIENT` of contract §3 naming [active] as the holder. */
    private suspend fun assertConflict(response: HttpResponse, active: IssuedDevice, activeName: String) {
        assertConflict(response.status.value, response.bodyAsText(), active, activeName)
    }

    private fun assertConflict(status: Int, text: String, active: IssuedDevice, activeName: String) {
        assertEquals(409, status)
        val body = json(text)
        assertEquals("CONFLICT_ACTIVE_CLIENT", body["code"]?.jsonPrimitive?.content)
        assertNotNull(body["message"])
        val device = body["activeDevice"]?.jsonObject
        assertEquals(active.deviceId, device?.get("deviceId")?.jsonPrimitive?.content)
        assertEquals(activeName, device?.get("deviceName")?.jsonPrimitive?.content)
    }

    private suspend fun awaitNoActive(core: BridgeServerCore) {
        withTimeout(5_000) { while (core.activeDevice.value != null || core.sessionCount != 0) delay(10) }
    }

    @Test
    fun `first session becomes active and starts with its snapshot`() = testApplication {
        val core = mount()

        val session = wsClient().openSession(deviceA.deviceToken)

        assertEquals("snapshot", session.nextJson().type())
        assertEquals(ActiveDevice(deviceA.deviceId, "PC-A"), core.activeDevice.value)
        session.close()
    }

    @Test
    fun `another device is refused the websocket with 409 naming the active one`() = testApplication {
        val core = mount()
        val ws = wsClient()
        val sessionA = ws.openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        assertNotNull(runCatching { ws.openSession(deviceB.deviceToken) }.exceptionOrNull())

        assertEquals(1, core.sessionCount)
        assertEquals(deviceA.deviceId, core.activeDevice.value?.deviceId)
        sessionA.close()
    }

    @Test
    fun `another device gets the same 409 on every Bearer route and nothing is processed`() = testApplication {
        mount()
        val sessionA = wsClient().openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())
        val tokenB = deviceB.deviceToken

        assertConflict(librarySongs(tokenB), deviceA, "PC-A")
        assertConflict(client.get("/api/v1/library/playlists") { bearerAuth(tokenB) }, deviceA, "PC-A")
        assertConflict(client.get("/api/v1/artwork/$TRACK") { bearerAuth(tokenB) }, deviceA, "PC-A")
        assertConflict(forge(tokenB), deviceA, "PC-A")
        // Even an invalid body or an unknown command is answered 409 before anything else
        val command = client.post("/api/v1/player/play") {
            bearerAuth(tokenB)
            contentType(ContentType.Application.Json)
            setBody("{not json")
        }
        assertConflict(command, deviceA, "PC-A")
        assertConflict(client.post("/api/v1/player/stop") { bearerAuth(tokenB) }, deviceA, "PC-A")
        assertTrue(executor.received.isEmpty())
        sessionA.close()
    }

    @Test
    fun `the active device's own Bearer requests are processed normally`() = testApplication {
        mount()
        val sessionA = wsClient().openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        assertEquals(HttpStatusCode.OK, librarySongs(deviceA.deviceToken).status)
        val command = client.post("/api/v1/player/play") {
            bearerAuth(deviceA.deviceToken)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, command.status)
        assertEquals(HttpStatusCode.OK, forge(deviceA.deviceToken).status)
        assertEquals(1, executor.received.size)
        sessionA.close()
    }

    @Test
    fun `without a session every paired device reaches the Bearer routes`() = testApplication {
        mount()

        assertEquals(HttpStatusCode.OK, librarySongs(deviceA.deviceToken).status)
        assertEquals(HttpStatusCode.OK, librarySongs(deviceB.deviceToken).status)
        assertEquals(HttpStatusCode.OK, forge(deviceB.deviceToken).status)
    }

    @Test
    fun `authentication is answered 401 before the active-client rule`() = testApplication {
        mount()
        val sessionA = wsClient().openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        val missing = librarySongs(null)
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("UNAUTHORIZED", json(missing.bodyAsText())["code"]?.jsonPrimitive?.content)
        val revoked = librarySongs("unknown-token")
        assertEquals(HttpStatusCode.Unauthorized, revoked.status)
        assertEquals("DEVICE_REVOKED", json(revoked.bodyAsText())["code"]?.jsonPrimitive?.content)
        sessionA.close()
    }

    // runBlocking: JUnit test entry point driving the real suspend start/stop of the CIO engine
    @Test
    fun `websocket upgrade is refused before the upgrade, 401 first, then 409 with the active device`() = runBlocking {
        val core = newCore()
        val server = BridgeServer(core, portCandidates = listOf(0))
        val port = server.start("127.0.0.1")
        val sockets = mutableListOf<Socket>()
        try {
            val (socketA, accepted) = rawUpgrade(port, deviceA.deviceToken)
            sockets += socketA
            assertEquals(101, accepted.status)
            assertEquals(ActiveDevice(deviceA.deviceId, "PC-A"), core.activeDevice.value)

            val (socketB, conflict) = rawUpgrade(port, deviceB.deviceToken)
            sockets += socketB
            assertConflict(conflict.status, conflict.body, deviceA, "PC-A")

            val (socketNoToken, missing) = rawUpgrade(port, null)
            sockets += socketNoToken
            assertEquals(401, missing.status)
            assertEquals("UNAUTHORIZED", json(missing.body)["code"]?.jsonPrimitive?.content)
            val (socketUnknown, revoked) = rawUpgrade(port, "unknown-token")
            sockets += socketUnknown
            assertEquals(401, revoked.status)
            assertEquals("DEVICE_REVOKED", json(revoked.body)["code"]?.jsonPrimitive?.content)

            // Refused upgrades leave the active session untouched
            assertEquals(deviceA.deviceId, core.activeDevice.value?.deviceId)
            assertEquals(1, core.sessionCount)
        } finally {
            sockets.forEach { runCatching { it.close() } }
            server.stop(BridgeStopCode.STOP_USER)
        }
    }

    @Test
    fun `public routes and audio delivery are outside the 409 rule`() = testApplication {
        mount()
        // Forged by B while no session is active, played after A took the session
        val pathB = forgedPath(deviceB.deviceToken)
        val sessionA = wsClient().openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        assertEquals(HttpStatusCode.OK, client.get("/api/v1/meta").status)
        val validate = client.post("/api/v1/pairing/validate") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"ABCDEF","requestId":null,"deviceName":"PC-C"}""")
        }
        assertEquals("PAIRING_REJECTED", json(validate.bodyAsText())["code"]?.jsonPrimitive?.content)
        assertEquals(HttpStatusCode.OK, client.get(pathB).status)
        sessionA.close()
    }

    // runBlocking: JUnit test entry point driving the real suspend start/stop of the CIO engine
    @Test
    fun `simultaneous upgrades of different devices let exactly one win, the others get 409 naming it`() = runBlocking {
        val core = newCore()
        val server = BridgeServer(core, portCandidates = listOf(0))
        val port = server.start("127.0.0.1")
        val devices = listOf(deviceA, deviceB) + (1..4).map { store.issue("PC-$it") }
        val sockets = mutableListOf<Socket>()
        try {
            val startBarrier = CompletableDeferred<Unit>()
            val attempts = devices.map { device ->
                async(Dispatchers.IO) {
                    startBarrier.await()
                    device to rawUpgrade(port, device.deviceToken)
                }
            }
            startBarrier.complete(Unit)
            val outcomes = attempts.awaitAll()
            outcomes.forEach { (_, outcome) -> sockets += outcome.first }

            val winners = outcomes.filter { (_, outcome) -> outcome.second.status == 101 }
            assertEquals(1, winners.size)
            val winner = winners.single().first
            val winnerName = store.devices.value.first { it.deviceId == winner.deviceId }.deviceName
            outcomes.filter { (device, _) -> device !== winner }.forEach { (_, outcome) ->
                assertConflict(outcome.second.status, outcome.second.body, winner, winnerName)
            }
            assertEquals(ActiveDevice(winner.deviceId, winnerName), core.activeDevice.value)
        } finally {
            sockets.forEach { runCatching { it.close() } }
            server.stop(BridgeStopCode.STOP_USER)
        }
    }

    @Test
    fun `an upgrade that never happens frees the session it claimed`() = testApplication {
        val core = newCore()
        val failedUpgrades = AtomicInteger()
        application {
            core.install(this)
            // Ktor answers 101 to any request reaching the WebSocket route: the failure of an
            // upgrade after the claim is simulated by turning the first upgrade into a 500
            install(createApplicationPlugin("FailFirstUpgrade") {
                onCallRespond { _, body ->
                    if (body is WebSocketUpgrade && failedUpgrades.getAndIncrement() == 0) {
                        transformBody { TextContent("{}", ContentType.Application.Json, HttpStatusCode.InternalServerError) }
                    }
                }
            })
        }
        val ws = wsClient()

        assertNotNull(runCatching { ws.openSession(deviceA.deviceToken) }.exceptionOrNull())
        assertEquals(1, failedUpgrades.get())
        withTimeout(5_000) { while (core.activeDevice.value != null) delay(10) }

        val sessionB = ws.openSession(deviceB.deviceToken)
        assertEquals("snapshot", sessionB.nextJson().type())
        assertEquals(deviceB.deviceId, core.activeDevice.value?.deviceId)
        sessionB.close()
    }

    @Test
    fun `same device reconnecting replaces its session with 4000 and the new one gets the snapshot`() = testApplication {
        val core = mount()
        hub.submit(playingSample)
        val ws = wsClient()
        val first = ws.openSession(deviceA.deviceToken)
        assertEquals("snapshot", first.nextJson().type())

        val second = ws.openSession(deviceA.deviceToken)

        val reason = withTimeout(5_000) { first.closeReason.await() }
        assertEquals(BridgeContract.CLOSE_SESSION_REPLACED, reason?.code)
        assertEquals("SESSION_REPLACED", reason?.message)
        val snapshot = second.nextJson()
        assertEquals("snapshot", snapshot.type())
        assertEquals(hub.currentRevision, snapshot["revision"]?.jsonPrimitive?.long)
        assertEquals(ActiveDevice(deviceA.deviceId, "PC-A"), core.activeDevice.value)
        withTimeout(5_000) { while (core.sessionCount != 1) delay(10) }
        // The state goes on, without interruption, on the new session
        hub.submit(playingSample.copy(isPlaying = false))
        val delta = second.nextJson()
        assertEquals("playbackChanged", delta.type())
        assertEquals(hub.currentRevision, delta["revision"]?.jsonPrimitive?.long)
        // The replaced session ended for good: other devices are still refused
        assertConflict(librarySongs(deviceB.deviceToken), deviceA, "PC-A")
        second.close()
    }

    @Test
    fun `closing the active session frees it for another device`() = testApplication {
        val core = mount()
        val ws = wsClient()
        val sessionA = ws.openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        sessionA.close()
        awaitNoActive(core)

        val sessionB = ws.openSession(deviceB.deviceToken)
        assertEquals("snapshot", sessionB.nextJson().type())
        assertEquals(deviceB.deviceId, core.activeDevice.value?.deviceId)
        sessionB.close()
    }

    @Test
    fun `a 4008 ping timeout frees the session`() = testApplication {
        val core = mount(newCore(pingTimeoutMs = 300))
        val ws = wsClient()
        val sessionA = ws.openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        assertEquals(BridgeContract.CLOSE_PING_TIMEOUT, withTimeout(5_000) { sessionA.closeReason.await() }?.code)
        awaitNoActive(core)

        assertEquals(HttpStatusCode.OK, librarySongs(deviceB.deviceToken).status)
        val sessionB = ws.openSession(deviceB.deviceToken)
        assertEquals("snapshot", sessionB.nextJson().type())
        sessionB.close()
    }

    @Test
    fun `a 4003 revocation frees the session`() = testApplication {
        val core = mount()
        val ws = wsClient()
        val sessionA = ws.openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        assertTrue(core.revokeDevice(deviceA.deviceId))

        assertEquals(BridgeContract.CLOSE_DEVICE_REVOKED, withTimeout(5_000) { sessionA.closeReason.await() }?.code)
        assertNull(core.activeDevice.value)
        val sessionB = ws.openSession(deviceB.deviceToken)
        assertEquals("snapshot", sessionB.nextJson().type())
        sessionB.close()
    }

    @Test
    fun `kick closes with 4001, frees the session and keeps the pairing`() = testApplication {
        val core = mount()
        val ws = wsClient()
        val sessionA = ws.openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        assertTrue(core.kick(deviceA.deviceId))

        val reason = withTimeout(5_000) { sessionA.closeReason.await() }
        assertEquals(BridgeContract.CLOSE_KICKED, reason?.code)
        assertEquals("KICKED", reason?.message)
        assertNull(core.activeDevice.value)
        assertTrue(store.isPaired(deviceA.deviceId))
        assertNull(core.authorizationFailure("Bearer ${deviceA.deviceToken}"))
        awaitNoActive(core)
        val sessionB = ws.openSession(deviceB.deviceToken)
        assertEquals("snapshot", sessionB.nextJson().type())
        sessionB.close()
    }

    @Test
    fun `kick invalidates the URLs forged before it, not those forged after a reconnection`() = testApplication {
        val core = mount()
        val ws = wsClient()
        val sessionA = ws.openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())
        val before = forgedPath(deviceA.deviceToken)
        assertEquals(HttpStatusCode.OK, client.get(before).status)

        assertTrue(core.kick(deviceA.deviceId))

        val refused = client.get(before)
        assertEquals(HttpStatusCode.Unauthorized, refused.status)
        assertEquals("DEVICE_REVOKED", json(refused.bodyAsText())["code"]?.jsonPrimitive?.content)
        // The kicked device coming back by itself is accepted (contract §6.4)
        awaitNoActive(core)
        val again = ws.openSession(deviceA.deviceToken)
        assertEquals("snapshot", again.nextJson().type())
        val after = forgedPath(deviceA.deviceToken)
        assertEquals(HttpStatusCode.OK, client.get(after).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(before).status)
        again.close()
    }

    @Test
    fun `kick of a device without the session does nothing`() = testApplication {
        val core = mount()
        assertFalse(core.kick(deviceA.deviceId))

        val sessionA = wsClient().openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        assertFalse(core.kick(deviceB.deviceId))
        assertEquals(deviceA.deviceId, core.activeDevice.value?.deviceId)
        sessionA.close()
    }

    @Test
    fun `stopping the server frees the session`() = testApplication {
        val core = mount()
        val sessionA = wsClient().openSession(deviceA.deviceToken)
        assertEquals("snapshot", sessionA.nextJson().type())

        core.shutdownSessions(BridgeStopCode.STOP_USER)

        assertNull(core.activeDevice.value)
        assertEquals(BridgeContract.CLOSE_SERVER_STOPPED, withTimeout(5_000) { sessionA.closeReason.await() }?.code)
    }

    @Test
    fun `controller exposes the active device and kicks it`() = testApplication {
        val core = BridgeServerController.createCore("Pixel test", store, hub)
        mount(core)
        try {
            assertFalse(BridgeServerController.kick())
            BridgeServerController.attachCore(core)
            assertNull(BridgeServerController.activeDevice.value)
            val sessionA = wsClient().openSession(deviceA.deviceToken)
            assertEquals("snapshot", sessionA.nextJson().type())

            assertEquals(ActiveDevice(deviceA.deviceId, "PC-A"), BridgeServerController.activeDevice.value)
            assertTrue(BridgeServerController.kick())

            assertEquals(BridgeContract.CLOSE_KICKED, withTimeout(5_000) { sessionA.closeReason.await() }?.code)
            assertNull(BridgeServerController.activeDevice.value)
            assertFalse(BridgeServerController.kick())
            assertTrue(store.isPaired(deviceA.deviceId))
        } finally {
            BridgeServerController.attachCore(null)
        }
        assertNull(BridgeServerController.activeDevice.value)
    }
}
