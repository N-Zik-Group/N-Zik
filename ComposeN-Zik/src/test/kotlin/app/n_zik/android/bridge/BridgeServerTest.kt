package app.n_zik.android.bridge

import app.n_zik.android.bridge.audio.AudioLibrary
import app.n_zik.android.bridge.audio.AudioOpenResult
import app.n_zik.android.bridge.audio.AudioTrackInfo
import app.n_zik.android.bridge.command.BridgeCommand
import app.n_zik.android.bridge.command.BridgeCommandExecutor
import app.n_zik.android.bridge.command.CommandResult
import app.n_zik.android.bridge.command.PlayerAction
import app.n_zik.android.bridge.library.LibraryProvider
import app.n_zik.android.bridge.library.LibrarySong
import app.n_zik.android.bridge.library.libSong
import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import app.n_zik.android.bridge.pairing.OfferResult
import app.n_zik.android.bridge.pairing.PairingCodeManager
import app.n_zik.android.bridge.pairing.PairingQrPayload
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.bridge.state.ErrorMessage
import app.n_zik.android.bridge.state.RepeatModeDto
import app.n_zik.android.bridge.state.playingSample
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
        assertEquals("1.3", json["contractVersion"]?.jsonPrimitive?.content)
        assertEquals("Pixel test", json["serverName"]?.jsonPrimitive?.content)
        assertEquals(1_790_000_000_000L, json["serverTimeMs"]?.jsonPrimitive?.long)
        assertEquals(
            listOf(
                "pairing.qr",
                "pairing.manual",
                "playback",
                "queue",
                "library.songs",
                "library.playlists",
                "library.albums",
                "library.artists",
                "artwork",
                "audio",
                "audio.output",
                "ws.state",
            ),
            json["features"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
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

    private suspend fun DefaultClientWebSocketSession.nextJson(): JsonObject =
        withTimeout(5_000) { BridgeJson.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject }

    private fun JsonObject.type(): String? = this["type"]?.jsonPrimitive?.content

    @Test
    fun `stopping sends the serverStopped frame last then closes with 1001`() = testApplication {
        val hub = BridgeStateHub()
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, stateHub = hub, heartbeatIntervalMs = 20)
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            assertEquals("snapshot", nextJson().type())
            // State traffic right before the stop must never follow serverStopped
            hub.submit(playingSample)
            core.shutdownSessions(BridgeStopCode.TIMEOUT)
            hub.submit(playingSample.copy(isPlaying = false))

            val frames = mutableListOf<JsonObject>()
            while (true) {
                val frame = withTimeout(5_000) { incoming.receiveCatching() }.getOrNull() ?: break
                frames += BridgeJson.parseToJsonElement((frame as Frame.Text).readText()).jsonObject
            }
            val last = frames.last()
            assertEquals("serverStopped", last.type())
            assertEquals("TIMEOUT", last["code"]?.jsonPrimitive?.content)
            assertEquals(1, frames.count { it.type() == "serverStopped" })

            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals(BridgeContract.CLOSE_SERVER_STOPPED, reason?.code)
        }
    }

    // --- State synchronisation (contract §6.3, §7) ---

    @Test
    fun `first message is the snapshot at the current revision`() = testApplication {
        val hub = BridgeStateHub()
        hub.submit(playingSample)
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, stateHub = hub)
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            val snapshot = nextJson()
            assertEquals("snapshot", snapshot.type())
            assertEquals(hub.currentRevision, snapshot["revision"]?.jsonPrimitive?.long)
            assertEquals(2, snapshot["queue"]?.jsonArray?.size)
            assertEquals("aaaaaaaaaaa", snapshot["currentTrackId"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `empty player gives an empty snapshot`() = testApplication {
        mount(BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll))
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            val snapshot = nextJson()
            assertEquals(0L, snapshot["revision"]?.jsonPrimitive?.long)
            assertEquals(0, snapshot["queue"]?.jsonArray?.size)
            assertEquals(-1, snapshot["currentIndex"]?.jsonPrimitive?.int)
            assertEquals(JsonNull, snapshot["currentTrackId"])
            assertEquals("false", snapshot["isPlaying"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `ping gets an immediate pong with the client time copied`() = testApplication {
        // Read by the session coroutines: an atomic counter, not a plain var
        val now = AtomicLong(1_790_000_000_000L)
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, clock = { now.getAndIncrement() })
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            assertEquals("snapshot", nextJson().type())
            send(Frame.Text("""{"type":"ping","clientTimeMs":123456}"""))

            val pong = nextJson()
            assertEquals("pong", pong.type())
            assertEquals(123_456L, pong["clientTimeMs"]?.jsonPrimitive?.long)
            val receivedAt = pong["serverReceiveTimeMs"]?.jsonPrimitive?.long ?: 0L
            val sentAt = pong["serverSendTimeMs"]?.jsonPrimitive?.long ?: 0L
            assertTrue(receivedAt >= 1_790_000_000_000L)
            assertTrue(receivedAt <= sentAt)
        }
    }

    @Test
    fun `requestSnapshot is answered with a snapshot at the current revision`() = testApplication {
        val hub = BridgeStateHub()
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, stateHub = hub)
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            assertEquals(0L, nextJson()["revision"]?.jsonPrimitive?.long)
            hub.submit(playingSample)
            // Skip the deltas: the client acts as if it had lost them
            repeat(2) { nextJson() }

            send(Frame.Text("""{"type":"requestSnapshot"}"""))

            val snapshot = nextJson()
            assertEquals("snapshot", snapshot.type())
            assertEquals(hub.currentRevision, snapshot["revision"]?.jsonPrimitive?.long)
        }
    }

    @Test
    fun `player changes are pushed as consecutive deltas`() = testApplication {
        val hub = BridgeStateHub()
        hub.submit(playingSample)
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, stateHub = hub)
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            val base = nextJson()["revision"]?.jsonPrimitive?.long ?: -1L
            hub.submit(playingSample.copy(isPlaying = false))
            hub.submit(playingSample.copy(isPlaying = false, repeatMode = RepeatModeDto.ALL))

            val pause = nextJson()
            assertEquals("playbackChanged", pause.type())
            assertEquals(base + 1, pause["revision"]?.jsonPrimitive?.long)
            val modes = nextJson()
            assertEquals("modesChanged", modes.type())
            assertEquals(base + 2, modes["revision"]?.jsonPrimitive?.long)
            assertEquals("all", modes["repeatMode"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `heartbeat carries the current revision without incrementing it`() = testApplication {
        val hub = BridgeStateHub()
        hub.submit(playingSample)
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, stateHub = hub, heartbeatIntervalMs = 50)
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            assertEquals("snapshot", nextJson().type())
            val heartbeat = nextJson()
            assertEquals("heartbeat", heartbeat.type())
            assertEquals(hub.currentRevision, heartbeat["revision"]?.jsonPrimitive?.long)
            val next = nextJson()
            assertEquals("heartbeat", next.type())
            assertEquals(hub.currentRevision, next["revision"]?.jsonPrimitive?.long)
        }
    }

    @Test
    fun `silent client is closed with 4008 PING_TIMEOUT`() = testApplication {
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, pingTimeoutMs = 300)
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            assertEquals("snapshot", nextJson().type())

            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals(BridgeContract.CLOSE_PING_TIMEOUT, reason?.code)
            assertEquals("PING_TIMEOUT", reason?.message)
        }
        withTimeout(5_000) { while (core.sessionCount != 0) delay(10) }
    }

    @Test
    fun `unknown or invalid client messages are ignored but keep the session alive`() = testApplication {
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, pingTimeoutMs = 1_000)
        mount(core)
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            assertEquals("snapshot", nextJson().type())
            repeat(10) { i ->
                send(Frame.Text(if (i % 2 == 0) """{"type":"somethingElse"}""" else "{not json"))
                delay(200)
            }

            // Nothing was answered and the session is still open after 2 s (> 1 s timeout, 200 ms gaps)
            assertNull(withTimeoutOrNull(100) { incoming.receive() })
            assertEquals(1, core.sessionCount)
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
    fun `core built by the controller serves the hub of the server run`() = testApplication {
        val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
        val hub = BridgeStateHub()
        hub.submit(playingSample)
        mount(BridgeServerController.createCore("Pixel test", store, hub))
        val token = store.issue("PC-SALON").deviceToken
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth(token) }) {
            val snapshot = nextJson()
            assertEquals(hub.currentRevision, snapshot["revision"]?.jsonPrimitive?.long)
            assertEquals("aaaaaaaaaaa", snapshot["currentTrackId"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `core built by the controller serves the library provider of the server run`() = testApplication {
        val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
        val library = object : LibraryProvider by LibraryProvider.EMPTY {
            override suspend fun songs(): List<LibrarySong> = listOf(libSong("abc", "From the provider"))
        }
        mount(BridgeServerController.createCore("Pixel test", store, BridgeStateHub(), libraryProvider = library))
        val token = store.issue("PC-SALON").deviceToken

        val response = client.get("/api/v1/library/songs") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val page = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, page["total"]?.jsonPrimitive?.int)
        assertEquals("abc", page["items"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content)
    }

    @Test
    fun `core built by the controller serves the audio library of the server run`() = testApplication {
        val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
        val audio = object : AudioLibrary {
            override suspend fun track(trackId: String): AudioTrackInfo? = AudioTrackInfo(200_000L).takeIf { trackId == "abcdefghijk" }
            override suspend fun open(trackId: String, quality: AudioQuality): AudioOpenResult = AudioOpenResult.NotFound
        }
        mount(BridgeServerController.createCore("Pixel test", store, BridgeStateHub(), audioLibrary = audio))
        val token = store.issue("PC-SALON").deviceToken

        val response = client.post("/api/v1/audio/abcdefghijk/url") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"quality":"high"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(200_000L, body["durationMs"]?.jsonPrimitive?.long)
    }

    @Test
    fun `QR offer from the controller carries the code the service core accepts`() = testApplication {
        val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
        val core = BridgeServerController.createCore("Pixel test", store, BridgeStateHub())
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

    // runBlocking: JUnit test entry point driving the real suspend start/stop of the CIO engine
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

    // --- Commands (contract §9) ---

    private suspend fun ApplicationTestBuilder.command(route: String, body: String = "{}", token: String? = "valid-token") =
        client.post("/api/v1/$route") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** Executor answering [result] and recording what it received. */
    private class FakeExecutor(var result: CommandResult) : BridgeCommandExecutor {
        val received = mutableListOf<BridgeCommand>()
        override suspend fun execute(command: BridgeCommand): CommandResult {
            received += command
            return result
        }
    }

    @Test
    fun `applied command answers applied, changed and revision`() = testApplication {
        val executor = FakeExecutor(CommandResult.Applied(changed = true, revision = 58))
        mount(BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, commandExecutor = executor))

        val response = command("player/seek", """{"positionMs":1000,"commandId":"c-1"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        val json = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("true", json["applied"]?.jsonPrimitive?.content)
        assertEquals("true", json["changed"]?.jsonPrimitive?.content)
        assertEquals(58L, json["revision"]?.jsonPrimitive?.long)
        assertEquals(listOf(BridgeCommand(PlayerAction.Seek(1_000L), "c-1")), executor.received)
    }

    @Test
    fun `each executor outcome maps to its HTTP status and error code`() = testApplication {
        val executor = FakeExecutor(CommandResult.Rejected)
        mount(BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, commandExecutor = executor))

        val rejected = command("player/next")
        assertEquals(HttpStatusCode.UnprocessableEntity, rejected.status)
        assertEquals("PLAYER_REJECTED", errorCode(rejected.bodyAsText()))

        executor.result = CommandResult.Unavailable
        val unavailable = command("player/play")
        assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
        assertEquals("PLAYER_UNAVAILABLE", errorCode(unavailable.bodyAsText()))

        executor.result = CommandResult.QueueMismatch(revision = 12)
        val mismatch = command("queue/remove", """{"index":3,"trackId":"aaaaaaaaaaa"}""")
        assertEquals(HttpStatusCode.Conflict, mismatch.status)
        val mismatchJson = BridgeJson.parseToJsonElement(mismatch.bodyAsText()).jsonObject
        assertEquals("QUEUE_MISMATCH", mismatchJson["code"]?.jsonPrimitive?.content)
        assertEquals(12L, mismatchJson["revision"]?.jsonPrimitive?.long)

        executor.result = CommandResult.NotFound
        val notFound = command("queue/play", """{"trackIds":["zzzzzzzzzzz"],"startIndex":0}""")
        assertEquals(HttpStatusCode.NotFound, notFound.status)
        assertEquals("NOT_FOUND", errorCode(notFound.bodyAsText()))

        executor.result = CommandResult.Applied(changed = false, revision = 3)
        val unchanged = BridgeJson.parseToJsonElement(command("queue/clear").bodyAsText()).jsonObject
        assertEquals("false", unchanged["changed"]?.jsonPrimitive?.content)
        assertEquals(3L, unchanged["revision"]?.jsonPrimitive?.long)
    }

    @Test
    fun `invalid, oversized or unknown commands never reach the executor`() = testApplication {
        val executor = FakeExecutor(CommandResult.Applied(changed = false, revision = 0))
        mount(BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, commandExecutor = executor))

        val malformed = command("player/play", "{not json")
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("BAD_REQUEST", errorCode(malformed.bodyAsText()))
        assertEquals(HttpStatusCode.BadRequest, command("player/speed", """{"speed":5}""").status)
        assertEquals(HttpStatusCode.BadRequest, command("player/repeat", """{"mode":"forever"}""").status)
        val oversized = """{"commandId":null,"pad":"${"x".repeat(BridgeContract.MAX_COMMAND_BODY_BYTES)}"}"""
        assertEquals(HttpStatusCode.BadRequest, command("player/play", oversized).status)
        val unknown = command("player/stop")
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("NOT_FOUND", errorCode(unknown.bodyAsText()))

        assertTrue(executor.received.isEmpty())
    }

    @Test
    fun `oversized command body streamed without Content-Length is BAD_REQUEST`() = testApplication {
        val executor = FakeExecutor(CommandResult.Applied(changed = false, revision = 0))
        mount(BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, commandExecutor = executor))
        val bytes = """{"pad":"${"x".repeat(BridgeContract.MAX_COMMAND_BODY_BYTES)}"}""".toByteArray()

        val response = client.post("/api/v1/player/play") {
            bearerAuth("valid-token")
            setBody(object : OutgoingContent.ReadChannelContent() {
                override val contentType = ContentType.Application.Json
                override val contentLength: Long? = null
                override fun readFrom(): ByteReadChannel = ByteReadChannel(bytes)
            })
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("BAD_REQUEST", errorCode(response.bodyAsText()))
        assertTrue(executor.received.isEmpty())
    }

    @Test
    fun `commands check the Bearer token before anything else`() = testApplication {
        val executor = FakeExecutor(CommandResult.Applied(changed = true, revision = 1))
        mount(BridgeServerCore(serverName = "Pixel test", commandExecutor = executor))

        val missing = command("player/pause", token = null)
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("UNAUTHORIZED", errorCode(missing.bodyAsText()))
        // Even an invalid body or an unknown command is answered 401 first
        val revoked = command("player/stop", "{not json", token = "unknown-token")
        assertEquals(HttpStatusCode.Unauthorized, revoked.status)
        assertEquals("DEVICE_REVOKED", errorCode(revoked.bodyAsText()))
        assertTrue(executor.received.isEmpty())
    }

    @Test
    fun `server without a player answers PLAYER_UNAVAILABLE and a stopping one SERVER_STOPPING`() = testApplication {
        val core = BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll)
        mount(core)

        assertEquals("PLAYER_UNAVAILABLE", errorCode(command("queue/clear").bodyAsText()))
        core.shutdownSessions(BridgeStopCode.STOP_USER)
        val stopping = command("queue/clear")
        assertEquals(HttpStatusCode.ServiceUnavailable, stopping.status)
        assertEquals("SERVER_STOPPING", errorCode(stopping.bodyAsText()))
    }

    @Test
    fun `late error reaches the websocket without a revision`() = testApplication {
        val hub = BridgeStateHub()
        hub.submit(playingSample)
        mount(BridgeServerCore(serverName = "Pixel test", authenticator = acceptAll, stateHub = hub))
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/api/v1/ws", request = { bearerAuth("valid-token") }) {
            val revision = nextJson()["revision"]?.jsonPrimitive?.long
            hub.broadcast(ErrorMessage("PLAYER_REJECTED", "The track could not be played", "cmd-1"))

            val error = nextJson()
            assertEquals("error", error.type())
            assertEquals("PLAYER_REJECTED", error["code"]?.jsonPrimitive?.content)
            assertEquals("cmd-1", error["commandId"]?.jsonPrimitive?.content)
            assertNull(error["revision"])
            assertEquals(revision, hub.currentRevision)
        }
    }
}
