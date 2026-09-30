package app.n_zik.android.bridge.state

import app.n_zik.android.bridge.BridgeJson
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal fun testTrack(id: String) = TrackDto(
    id = id,
    title = "Title $id",
    artists = "Artist",
    durationMs = 200_000L,
    source = TrackSource.ONLINE,
    isDownloaded = false,
    isLiked = false,
    hasArtwork = true,
)

internal val playingSample = PlayerSample(
    queue = listOf(testTrack("aaaaaaaaaaa"), testTrack("bbbbbbbbbbb")),
    currentIndex = 0,
    currentTrackId = "aaaaaaaaaaa",
    isPlaying = true,
    speed = 1f,
    positionMs = 10_000L,
    sampledAtMs = 1_000L,
    repeatMode = RepeatModeDto.OFF,
    shuffle = false,
)

class BridgeStateHubTest {

    private var now = 1_000L
    private val hub = BridgeStateHub(clock = { now })

    private fun types(deltas: List<DeltaMessage>) = deltas.map { it::class.simpleName }

    @Test
    fun `a new hub starts at revision 0 with the empty snapshot`() {
        val snapshot = hub.snapshot()

        assertEquals(0L, snapshot.revision)
        assertTrue(snapshot.queue.isEmpty())
        assertEquals(-1, snapshot.currentIndex)
        assertEquals(null, snapshot.currentTrackId)
        assertEquals(false, snapshot.isPlaying)
    }

    @Test
    fun `each kind of change produces exactly one delta at revision plus one`() {
        hub.submit(playingSample)
        val start = hub.currentRevision

        val pause = hub.submit(playingSample.copy(isPlaying = false))
        assertEquals(listOf("PlaybackChangedMessage"), types(pause))
        assertEquals(start + 1, pause.single().revision)

        val next = hub.submit(playingSample.copy(isPlaying = false, currentIndex = 1, currentTrackId = "bbbbbbbbbbb"))
        assertEquals(listOf("TrackChangedMessage"), types(next))
        assertEquals(start + 2, next.single().revision)

        val added = hub.submit(
            playingSample.copy(
                isPlaying = false, currentIndex = 1, currentTrackId = "bbbbbbbbbbb",
                queue = playingSample.queue + testTrack("ccccccccccc"),
            )
        )
        assertEquals(listOf("QueueChangedMessage"), types(added))
        assertEquals(start + 3, added.single().revision)
        assertEquals(3, (added.single() as QueueChangedMessage).queue.size)

        val repeat = hub.submit(
            playingSample.copy(
                isPlaying = false, currentIndex = 1, currentTrackId = "bbbbbbbbbbb",
                queue = playingSample.queue + testTrack("ccccccccccc"), repeatMode = RepeatModeDto.ALL,
            )
        )
        assertEquals(listOf("ModesChangedMessage"), types(repeat))
        assertEquals(start + 4, repeat.single().revision)
        assertEquals(start + 4, hub.currentRevision)
    }

    @Test
    fun `speed change is a playbackChanged`() {
        hub.submit(playingSample)

        val deltas = hub.submit(playingSample.copy(speed = 1.5f))

        assertEquals(listOf("PlaybackChangedMessage"), types(deltas))
        assertEquals(1.5f, (deltas.single() as PlaybackChangedMessage).speed)
    }

    @Test
    fun `simultaneous changes come out in contract order with consecutive revisions`() {
        hub.submit(playingSample)

        val deltas = hub.submit(
            PlayerSample(
                queue = listOf(testTrack("ddddddddddd")),
                currentIndex = 0,
                currentTrackId = "ddddddddddd",
                isPlaying = true,
                speed = 2f,
                positionMs = 0L,
                sampledAtMs = now,
                repeatMode = RepeatModeDto.ONE,
                shuffle = true,
            )
        )

        assertEquals(
            listOf("QueueChangedMessage", "TrackChangedMessage", "PlaybackChangedMessage", "ModesChangedMessage"),
            types(deltas),
        )
        assertEquals(listOf(3L, 4L, 5L, 6L), deltas.map { it.revision })
    }

    @Test
    fun `position moving on its own produces no delta`() {
        hub.submit(playingSample)

        now += 10_000
        val deltas = hub.submit(playingSample.copy(positionMs = 20_000L, sampledAtMs = now))

        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `explicit seek produces a playbackChanged with the new position`() {
        hub.submit(playingSample)

        val deltas = hub.submit(playingSample.copy(positionMs = 90_000L, sampledAtMs = now), seek = true)

        val delta = deltas.single() as PlaybackChangedMessage
        assertEquals(90_000L, delta.positionMs)
        assertEquals(now, delta.serverTimeMs)
    }

    @Test
    fun `repeat-one transition on the same track is a trackChanged`() {
        hub.submit(playingSample)

        val deltas = hub.submit(playingSample.copy(positionMs = 0L, sampledAtMs = now), transition = true)

        assertEquals(listOf("TrackChangedMessage"), types(deltas))
    }

    @Test
    fun `snapshot and heartbeat extrapolate the position to serverTimeMs`() {
        hub.submit(playingSample.copy(speed = 2f))
        val revision = hub.currentRevision
        now = 1_500L

        val snapshot = hub.snapshot()
        val heartbeat = hub.heartbeat()

        assertEquals(1_500L, snapshot.serverTimeMs)
        assertEquals(11_000L, snapshot.positionMs)
        assertEquals(11_000L, heartbeat.positionMs)
        assertEquals(revision, heartbeat.revision)
        assertEquals(revision, hub.currentRevision)
    }

    @Test
    fun `paused position is not extrapolated`() {
        hub.submit(playingSample.copy(isPlaying = false))
        now = 60_000L

        assertEquals(10_000L, hub.heartbeat().positionMs)
    }

    @Test
    fun `extrapolated position never passes the end of the current track`() {
        hub.submit(playingSample)
        // 10 s into a 200 s track, 10 min later without a new sample
        now = 601_000L

        assertEquals(200_000L, hub.heartbeat().positionMs)
        assertEquals(200_000L, hub.snapshot().positionMs)
    }

    @Test
    fun `subscriber gets the snapshot first then the deltas until cancelled`() {
        hub.submit(playingSample)
        val received = mutableListOf<BridgeServerMessage>()
        val subscription = hub.subscribe { received += it }

        hub.submit(playingSample.copy(isPlaying = false))
        subscription.sendHeartbeat()
        subscription.cancel()
        hub.submit(playingSample)
        subscription.sendSnapshot()

        assertEquals(listOf("SnapshotMessage", "PlaybackChangedMessage", "HeartbeatMessage"), received.map { it::class.simpleName })
    }

    @Test
    fun `messages are encoded with their contract type and fields`() {
        hub.submit(playingSample)

        val snapshot = BridgeJson.parseToJsonElement(encodeServerMessage(hub.snapshot())).jsonObject
        assertEquals("snapshot", snapshot["type"]?.jsonPrimitive?.content)
        assertEquals(0, snapshot["currentIndex"]?.jsonPrimitive?.int)
        assertEquals("off", snapshot["repeatMode"]?.jsonPrimitive?.content)
        val track = snapshot["queue"]?.jsonArray?.first()?.jsonObject
        assertEquals(
            setOf("id", "title", "artists", "durationMs", "source", "isDownloaded", "isLiked", "hasArtwork"),
            track?.keys,
        )
        assertEquals("online", track?.get("source")?.jsonPrimitive?.content)

        val pause = hub.submit(playingSample.copy(isPlaying = false)).single()
        val delta = BridgeJson.parseToJsonElement(encodeServerMessage(pause)).jsonObject
        assertEquals("playbackChanged", delta["type"]?.jsonPrimitive?.content)
        assertEquals(3L, delta["revision"]?.jsonPrimitive?.long)
        assertEquals(false, delta["isPlaying"]?.jsonPrimitive?.boolean)
        assertEquals(setOf("type", "revision", "serverTimeMs", "isPlaying", "speed", "positionMs"), delta.keys)

        val heartbeat = BridgeJson.parseToJsonElement(encodeServerMessage(hub.heartbeat())).jsonObject
        assertEquals("heartbeat", heartbeat["type"]?.jsonPrimitive?.content)
    }
}
