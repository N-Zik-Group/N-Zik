package app.n_zik.android.bridge

import androidx.media3.common.Player
import app.n_zik.android.bridge.command.CommandResult
import app.n_zik.android.bridge.command.PlayerAccess
import app.n_zik.android.bridge.command.PlayerCommandExecutor
import app.n_zik.android.bridge.command.LateFailureTracker
import app.n_zik.android.bridge.command.PlayerSettings
import app.n_zik.android.bridge.command.Sampled
import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import app.n_zik.android.bridge.state.BridgeServerMessage
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.bridge.state.playingSample
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Contract 1.2 audio output: `player/output`, `outputChanged`, and the fallback of §6.2. */
class AudioOutputControllerTest {

    /** Pauses through [player] and publishes a paused sample, like the real source after a command. */
    private class FakeAccess(val player: Player, private val hub: BridgeStateHub) : PlayerAccess {
        var applied = 0
        override suspend fun <T> read(block: (Player?) -> T): T = block(player)
        override suspend fun <T> applyAndSample(block: (Player?) -> T): Sampled<T> {
            applied++
            val result = block(player)
            val deltas = hub.submit(playingSample.copy(isPlaying = false))
            return Sampled(result, deltas, hub.currentRevision)
        }
    }

    private val hub = BridgeStateHub()
    private val player = mockk<Player>(relaxed = true)
    private val access = FakeAccess(player, hub)
    private var sessionActive = true
    private val published = mutableListOf<AudioOutput>()
    private val controller = AudioOutputController(hub, access, { sessionActive }, { published += it })

    @Test
    fun `pc without an active session is rejected and nothing changes`() = runTest {
        sessionActive = false

        assertEquals(CommandResult.Rejected, controller.select(AudioOutput.PC))
        assertEquals(AudioOutput.PHONE, controller.current)
        assertEquals(0L, hub.currentRevision)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `pc with a session is applied, revised and mirrored, a second time changes nothing`() = runTest {
        hub.submit(playingSample)
        val start = hub.currentRevision

        assertEquals(CommandResult.Applied(changed = true, revision = start + 1), controller.select(AudioOutput.PC))
        assertEquals(CommandResult.Applied(changed = false, revision = start + 1), controller.select(AudioOutput.PC))
        assertEquals(listOf(AudioOutput.PC), published)
        // The phone is muted, never paused, by the choice of the output
        assertEquals(0, access.applied)
    }

    @Test
    fun `fallback on pc pauses through the facade then goes back to phone, in that order`() = runTest {
        hub.submit(playingSample)
        controller.select(AudioOutput.PC)
        val received = mutableListOf<BridgeServerMessage>()
        hub.subscribe { received += it }

        controller.fallback()

        verify(exactly = 1) { player.pause() }
        assertEquals(AudioOutput.PHONE, controller.current)
        assertEquals(listOf(AudioOutput.PC, AudioOutput.PHONE), published)
        assertEquals(
            listOf("SnapshotMessage", "PlaybackChangedMessage", "OutputChangedMessage"),
            received.map { it::class.simpleName },
        )
    }

    @Test
    fun `fallback on phone does nothing`() = runTest {
        hub.submit(playingSample)
        val revision = hub.currentRevision

        controller.fallback()

        verify(exactly = 0) { player.pause() }
        assertEquals(revision, hub.currentRevision)
        assertTrue(published.isEmpty())
    }

    // --- Over the routes and the WebSocket session (contract §6.2, §9) ---

    private val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
    private val device = store.issue("PC-A")

    private fun ApplicationTestBuilder.mountWithOutputs(handoffScope: CoroutineScope? = null): Pair<BridgeServerCore, AudioOutputController> {
        lateinit var core: BridgeServerCore
        val outputs = AudioOutputController(hub, access, { core.activeDevice.value != null })
        val executor = PlayerCommandExecutor(
            player = access,
            hub = hub,
            lateFailures = LateFailureTracker(hub),
            settings = object : PlayerSettings {
                override fun jumpPreviousSeconds(): Int = 3
                override fun saveSpeed(speed: Float) = Unit
            },
            tracks = { null },
            guestLocked = { false },
            outputs = outputs,
        )
        core = BridgeServerCore(
            serverName = "Pixel test",
            deviceStore = store,
            stateHub = hub,
            commandExecutor = executor,
            onSessionEnded = outputs::fallback,
            // The service wiring of BridgeServerService.startServer, only when a scope is provided
            onSessionClaimed = if (handoffScope != null) {
                { handoffScope.launch { outputs.select(AudioOutput.PC) } }
            } else {
                {}
            },
        )
        application { core.install(this) }
        return core to outputs
    }

    private suspend fun ApplicationTestBuilder.output(value: String): HttpResponse =
        client.post("/api/v1/player/output") {
            bearerAuth(device.deviceToken)
            contentType(ContentType.Application.Json)
            setBody("""{"output":"$value"}""")
        }

    private fun ApplicationTestBuilder.wsClient(): HttpClient = createClient { install(WebSockets) }

    private suspend fun DefaultClientWebSocketSession.nextJson(): JsonObject =
        withTimeout(5_000) { BridgeJson.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject }

    private fun JsonObject.code(): String? = this["code"]?.jsonPrimitive?.content

    @Test
    fun `player output answers 422 without a session and 400 for an unknown value`() = testApplication {
        mountWithOutputs()

        val noSession = output("pc")
        assertEquals(422, noSession.status.value)
        assertEquals("PLAYER_REJECTED", BridgeJson.parseToJsonElement(noSession.bodyAsText()).jsonObject.code())

        val unknown = output("tv")
        assertEquals(400, unknown.status.value)
        assertEquals("BAD_REQUEST", BridgeJson.parseToJsonElement(unknown.bodyAsText()).jsonObject.code())
    }

    @Test
    fun `pc from the session is sent as outputChanged, and closing the session pauses then goes back to phone`() =
        testApplication {
            val (core, outputs) = mountWithOutputs()
            val session = wsClient().webSocketSession("/api/v1/ws") { bearerAuth(device.deviceToken) }
            val snapshot = session.nextJson()
            assertEquals("phone", snapshot["audioOutput"]?.jsonPrimitive?.content)

            val response = output("pc")
            assertEquals(200, response.status.value)
            val body = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("true", body["changed"]?.jsonPrimitive?.content)
            val delta = session.nextJson()
            assertEquals("outputChanged", delta["type"]?.jsonPrimitive?.content)
            assertEquals("pc", delta["audioOutput"]?.jsonPrimitive?.content)
            assertEquals(body["revision"]?.jsonPrimitive?.content, delta["revision"]?.jsonPrimitive?.content)

            session.close()
            withTimeout(5_000) { while (outputs.current != AudioOutput.PHONE || core.activeDevice.value != null) delay(10) }
            verify(exactly = 1) { player.pause() }
        }

    @Test
    fun `a kick on the pc output pauses the phone and brings the output back`() = testApplication {
        val (core, outputs) = mountWithOutputs()
        val session = wsClient().webSocketSession("/api/v1/ws") { bearerAuth(device.deviceToken) }
        session.nextJson()
        assertEquals(200, output("pc").status.value)

        assertTrue(core.kick(device.deviceId))

        withTimeout(5_000) { while (outputs.current != AudioOutput.PHONE) delay(10) }
        verify(exactly = 1) { player.pause() }
    }

    @Test
    fun `stopping the server on the pc output pauses the phone and brings the output back`() = testApplication {
        val (core, outputs) = mountWithOutputs()
        val session = wsClient().webSocketSession("/api/v1/ws") { bearerAuth(device.deviceToken) }
        session.nextJson()
        assertEquals(200, output("pc").status.value)

        core.shutdownSessions(BridgeStopCode.STOP_USER)
        // The service runs the fallback itself after the stop; idempotent with the session's own
        outputs.fallback()

        withTimeout(5_000) { while (outputs.current != AudioOutput.PHONE) delay(10) }
        verify(exactly = 1) { player.pause() }
    }

    // --- Session handoff (contract §8.5, since 1.3): the output switches to pc on activation ---

    @Test
    fun `with the handoff, a new session switches the output to pc, and closing it pauses then goes back to phone`() =
        testApplication {
            val handoffScope = CoroutineScope(Dispatchers.Unconfined)
            val (core, outputs) = mountWithOutputs(handoffScope)
            val session = wsClient().webSocketSession("/api/v1/ws") { bearerAuth(device.deviceToken) }
            val snapshot = session.nextJson()

            withTimeout(5_000) { while (outputs.current != AudioOutput.PC) delay(10) }
            // The client sees `pc` in the snapshot, or as an `outputChanged` delta right after
            if ("pc" != snapshot["audioOutput"]?.jsonPrimitive?.content) {
                val delta = session.nextJson()
                assertEquals("outputChanged", delta["type"]?.jsonPrimitive?.content)
                assertEquals("pc", delta["audioOutput"]?.jsonPrimitive?.content)
            }

            session.close()
            withTimeout(5_000) { while (outputs.current != AudioOutput.PHONE || core.activeDevice.value != null) delay(10) }
            verify(exactly = 1) { player.pause() }
            handoffScope.cancel()
        }

    @Test
    fun `with the handoff, the same device replacing its session keeps the output on pc`() =
        testApplication {
            val handoffScope = CoroutineScope(Dispatchers.Unconfined)
            val (core, outputs) = mountWithOutputs(handoffScope)
            val session = wsClient().webSocketSession("/api/v1/ws") { bearerAuth(device.deviceToken) }
            session.nextJson()
            withTimeout(5_000) { while (outputs.current != AudioOutput.PC) delay(10) }

            // A second connection of the same device replaces the first one with `4000`
            val replacement = wsClient().webSocketSession("/api/v1/ws") { bearerAuth(device.deviceToken) }
            replacement.nextJson()
            withTimeout(5_000) { while (outputs.current != AudioOutput.PC) delay(10) }

            // The replaced session ends after; the active session continues, so no fallback
            withTimeout(5_000) { while (core.sessionCount != 1) delay(10) }
            delay(100) // any late end (and any spurious fallback) of the replaced session is done by now
            assertEquals(AudioOutput.PC, outputs.current)
            verify(exactly = 0) { player.pause() }
            handoffScope.cancel()
        }
}
