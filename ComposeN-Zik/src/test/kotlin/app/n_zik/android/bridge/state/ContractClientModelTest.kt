package app.n_zik.android.bridge.state

import app.n_zik.android.bridge.BridgeJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reference client of contract §7.3–§7.5, written from the contract text only: it checks
 * that what the server emits is consumable by a conforming v1 client.
 */
internal class ContractClientModel {
    /** Last applied revision, `null` before the first snapshot. */
    var last: Long? = null
        private set

    /** Types of the messages actually applied, in order. */
    val applied = mutableListOf<String>()

    /** Client → server messages sent (only `requestSnapshot` here). */
    val sent = mutableListOf<String>()

    private var waitingForSnapshot = false

    fun receive(text: String) {
        val message = BridgeJson.parseToJsonElement(text).jsonObject
        val type = (message["type"] as? JsonPrimitive)?.contentOrNull
        val revision = (message["revision"] as? JsonPrimitive)?.longOrNull
        when (type) {
            "snapshot" -> {
                // §7.1 / §7.4: always applied, even at a lower revision (server restart)
                applied += type
                last = requireNotNull(revision)
                waitingForSnapshot = false
            }
            in DELTA_TYPES -> account(requireNotNull(revision)) { applied += requireNotNull(type) }
            "heartbeat" -> onHeartbeat(requireNotNull(revision))
            "pong", "error", "serverStopped" -> Unit
            // §7.5: unknown type, counted for revisions only when it carries an integer revision
            else -> if (revision != null) account(revision) { }
        }
    }

    private inline fun account(revision: Long, apply: () -> Unit) {
        val current = last ?: return
        if (waitingForSnapshot) return
        when {
            revision <= current -> Unit // CAS: stale message rejected
            revision == current + 1 -> {
                apply()
                last = revision
            }
            else -> requestSnapshot()
        }
    }

    private fun onHeartbeat(revision: Long) {
        val current = last ?: return
        when {
            revision == current -> applied += "heartbeat"
            revision > current -> requestSnapshot()
        }
    }

    private fun requestSnapshot() {
        waitingForSnapshot = true
        sent += """{"type":"requestSnapshot"}"""
    }

    private companion object {
        val DELTA_TYPES = setOf("playbackChanged", "trackChanged", "queueChanged", "modesChanged")
    }
}

class ContractClientModelTest {

    private var now = 1_000L
    private val client = ContractClientModel()

    private fun BridgeStateHub.submitEncoded(sample: PlayerSample): List<String> = submit(sample).map { encodeServerMessage(it) }

    private fun delta(type: String, revision: Long) =
        """{"type":"$type","revision":$revision,"serverTimeMs":1,"isPlaying":true,"speed":1.0,"positionMs":0}"""

    private fun snapshotAt(revision: Long): String {
        val hub = BridgeStateHub(clock = { now })
        return encodeServerMessage(hub.snapshot().copy(revision = revision))
    }

    @Test
    fun `revision and CAS matrix of contract 7_4`() {
        client.receive(snapshotAt(5))
        assertEquals(5L, client.last)

        client.receive(delta("playbackChanged", 6))
        assertEquals(6L, client.last)

        // revision <= last: rejected
        client.receive(delta("playbackChanged", 6))
        client.receive(delta("playbackChanged", 4))
        assertEquals(6L, client.last)
        assertTrue(client.sent.isEmpty())

        // revision > last + 1: not applied, snapshot requested, following deltas ignored
        client.receive(delta("playbackChanged", 8))
        client.receive(delta("playbackChanged", 7))
        assertEquals(6L, client.last)
        assertEquals(listOf("""{"type":"requestSnapshot"}"""), client.sent)

        client.receive(snapshotAt(9))
        client.receive(delta("playbackChanged", 10))
        assertEquals(10L, client.last)
        assertEquals(listOf("snapshot", "playbackChanged", "snapshot", "playbackChanged"), client.applied)
    }

    @Test
    fun `server deltas are consecutive and all applied`() {
        val hub = BridgeStateHub(clock = { now })
        client.receive(encodeServerMessage(hub.snapshot()))

        val messages = hub.submitEncoded(playingSample) +
            hub.submitEncoded(playingSample.copy(isPlaying = false)) +
            hub.submitEncoded(playingSample.copy(isPlaying = false, repeatMode = RepeatModeDto.ONE))
        messages.forEach(client::receive)
        client.receive(encodeServerMessage(hub.heartbeat()))

        assertEquals(hub.currentRevision, client.last)
        assertTrue(client.sent.isEmpty())
        assertEquals("heartbeat", client.applied.last())
    }

    @Test
    fun `snapshot at a low revision after a server restart is applied`() {
        val oldServer = BridgeStateHub(clock = { now })
        client.receive(encodeServerMessage(oldServer.snapshot()))
        repeat(3) { i -> oldServer.submitEncoded(playingSample.copy(isPlaying = i % 2 == 0)).forEach(client::receive) }
        assertTrue((client.last ?: 0L) > 0L)

        val restarted = BridgeStateHub(clock = { now })
        client.receive(encodeServerMessage(restarted.snapshot()))
        assertEquals(0L, client.last)

        restarted.submitEncoded(playingSample).forEach(client::receive)
        assertEquals(restarted.currentRevision, client.last)
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun `lost delta is revealed by the next heartbeat and a snapshot resynchronises`() {
        val hub = BridgeStateHub(clock = { now })
        client.receive(encodeServerMessage(hub.snapshot()))
        hub.submitEncoded(playingSample).forEach(client::receive)
        val synced = client.last

        // This delta never reaches the client
        hub.submitEncoded(playingSample.copy(isPlaying = false))
        assertEquals(synced, client.last)

        client.receive(encodeServerMessage(hub.heartbeat()))
        assertEquals(listOf("""{"type":"requestSnapshot"}"""), client.sent)

        client.receive(encodeServerMessage(hub.snapshot()))
        assertEquals(hub.currentRevision, client.last)
        client.receive(encodeServerMessage(hub.heartbeat()))
        assertEquals(1, client.sent.size)
        assertEquals("heartbeat", client.applied.last())
    }

    @Test
    fun `v1 client counts an unknown revised message from a v1_5 server`() {
        val hub = BridgeStateHub(clock = { now })
        client.receive(encodeServerMessage(hub.snapshot()))
        hub.submitEncoded(playingSample).forEach(client::receive)
        val before = client.last ?: error("no snapshot applied")

        // A future delta type: +1 revision, unknown to v1
        client.receive("""{"type":"lyricsChanged","revision":${before + 1},"serverTimeMs":1,"lines":[]}""")
        assertEquals(before + 1, client.last)
        assertTrue("lyricsChanged" !in client.applied)

        client.receive(delta("playbackChanged", before + 2))
        assertEquals(before + 2, client.last)
        assertTrue(client.sent.isEmpty())

        // Unknown message without a revision: ignored
        client.receive("""{"type":"somethingNew","value":3}""")
        assertEquals(before + 2, client.last)

        // Unknown revised message beyond last + 1: snapshot requested
        client.receive("""{"type":"lyricsChanged","revision":${before + 5}}""")
        assertEquals(before + 2, client.last)
        assertEquals(1, client.sent.size)
    }

    @Test
    fun `reference client parses every server message type`() {
        val hub = BridgeStateHub(clock = { now })
        val all = listOf(
            encodeServerMessage(hub.snapshot()),
            encodeServerMessage(PongMessage(1L, 2L, 3L)),
        ) + hub.submitEncoded(
            playingSample.copy(repeatMode = RepeatModeDto.ALL, speed = 1.25f)
        ) + encodeServerMessage(hub.heartbeat())
        all.forEach(client::receive)

        val types = all.map { (BridgeJson.parseToJsonElement(it) as JsonObject)["type"]?.let { t -> (t as JsonPrimitive).content } }
        assertEquals(
            listOf("snapshot", "pong", "queueChanged", "trackChanged", "playbackChanged", "modesChanged", "heartbeat"),
            types,
        )
        assertEquals(hub.currentRevision, client.last)
    }
}
