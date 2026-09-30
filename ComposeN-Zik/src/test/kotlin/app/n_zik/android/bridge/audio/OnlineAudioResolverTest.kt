package app.n_zik.android.bridge.audio

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality

private const val VIDEO = "dQw4w9WgXcQ"
private const val NOW = 1_790_000_000_000L
private val BYTES = ByteArray(1_000) { (it * 7 + 3).toByte() }

private fun stream(
    url: String,
    itag: Int = 251,
    length: Long = BYTES.size.toLong(),
    chunk: Long = 0,
    expiresAtMs: Long = NOW + 3_600_000,
) = ResolvedStream(url, mapOf("X-Test" to url), itag, length, "audio/webm; codecs=\"opus\"", chunk, expiresAtMs)

class OnlineAudioResolverTest {

    private val clock = AtomicLong(NOW)

    /** Upstream resolutions handed out in order (the last one repeats). */
    private val resolutions = ArrayDeque<ResolvedStream>()
    private val resolved = mutableListOf<Pair<String, InnerTubeXAudioQuality>>()
    private var refreshes = 0
    private var refreshGate: CompletableDeferred<Unit>? = null
    private var refreshFails = false

    private val resolver = OnlineAudioResolver(
        resolveUpstream = { id, quality ->
            resolved += id to quality
            if (resolutions.size > 1) resolutions.removeFirst() else resolutions.first()
        },
        refreshAfterRejection = {
            refreshes++
            refreshGate?.await()
            if (refreshFails) error("refresh failed")
        },
        clock = clock::get,
    )

    /** Requests made upstream: (url, position, length). */
    private val requests = mutableListOf<Triple<String, Long, Long>>()

    /** URLs answering `403` to their next request(s). */
    private val refusing = mutableMapOf<String, Int>()

    private val opener = UpstreamOpener { resolvedStream, position, length ->
        requests += Triple(resolvedStream.url, position, length)
        val refusals = refusing[resolvedStream.url] ?: 0
        if (refusals > 0) {
            refusing[resolvedStream.url] = refusals - 1
            throw IOException("open failed", UpstreamStatusException(403))
        }
        FakeConnection(BYTES, position.toInt(), length)
    }

    private suspend fun source(quality: InnerTubeXAudioQuality = InnerTubeXAudioQuality.HIGH): OnlineAudioSource =
        OnlineAudioSource(VIDEO, quality, resolver.resolve(VIDEO, quality), resolver, opener)

    // --- Resolver memory ---

    @Test
    fun `one resolution per track and quality until near its expiry`() = runTest {
        resolutions += stream("u1", expiresAtMs = NOW + 60_000)

        resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH)
        resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH)
        resolver.resolve(VIDEO, InnerTubeXAudioQuality.LOW)
        assertEquals(2, resolved.size)

        clock.set(NOW + 30_000)
        resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH)
        assertEquals(3, resolved.size)
        assertEquals(InnerTubeXAudioQuality.LOW, resolved[1].second)
    }

    @Test
    fun `refusals share one refresh, and nothing is resolved again before it ends`() = runTest {
        val gate = CompletableDeferred<Unit>().also { refreshGate = it }
        val rejected = stream("u1")
        resolutions += rejected
        resolutions += stream("u2")
        resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH)

        val first = async { resolver.resolveAfterRejection(VIDEO, InnerTubeXAudioQuality.HIGH, rejected) }
        val second = async { resolver.resolveAfterRejection(VIDEO, InnerTubeXAudioQuality.HIGH, rejected) }
        repeat(5) { yield() }

        assertEquals(1, refreshes)
        assertFalse(first.isCompleted || second.isCompleted)
        assertEquals(1, resolved.size)

        gate.complete(Unit)
        assertEquals("u2", first.await().url)
        assertEquals("u2", second.await().url)
        assertEquals(2, resolved.size)
    }

    @Test
    fun `a failing refresh still lets every caller resolve again, and the next refusal refreshes anew`() = runTest {
        refreshFails = true
        val gate = CompletableDeferred<Unit>().also { refreshGate = it }
        val rejected = stream("u1")
        resolutions += rejected
        resolutions += stream("u2")
        resolutions += stream("u3")
        resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH)

        val first = async { resolver.resolveAfterRejection(VIDEO, InnerTubeXAudioQuality.HIGH, rejected) }
        val second = async { resolver.resolveAfterRejection(VIDEO, InnerTubeXAudioQuality.HIGH, rejected) }
        repeat(5) { yield() }
        gate.complete(Unit)

        assertEquals("u2", first.await().url)
        assertEquals("u2", second.await().url)
        assertEquals(1, refreshes)

        // The in-flight refresh was reset: a later refusal runs a new one
        refreshGate = null
        val single = resolver.resolveAfterRejection(VIDEO, InnerTubeXAudioQuality.HIGH, stream("u2"))
        assertEquals("u3", single.url)
        assertEquals(2, refreshes)
    }

    @Test
    fun `a stream announced as already expired is still reused for a minimum time`() = runTest {
        resolutions += stream("u1", expiresAtMs = NOW)

        resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH)
        clock.set(NOW + MIN_ENTRY_LIFETIME_MS - 1)
        resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH)
        assertEquals(1, resolved.size)

        clock.set(NOW + MIN_ENTRY_LIFETIME_MS)
        resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH)
        assertEquals(2, resolved.size)
    }

    // --- Relayed stream ---

    @Test
    fun `stream is relayed in bounded chunks and rebuilt exactly`() = runTest {
        resolutions += stream("u1", chunk = 300)

        val bytes = source().openStream(100, 800).readAll()

        assertArrayEquals(BYTES.copyOfRange(100, 900), bytes)
        assertEquals(
            listOf(Triple("u1", 100L, 300L), Triple("u1", 400L, 300L), Triple("u1", 700L, 200L)),
            requests,
        )
    }

    @Test
    fun `length and type are those of the pinned format`() = runTest {
        resolutions += stream("u1", length = 1_000)

        val source = source()

        assertEquals(1_000L, source.length)
        assertEquals("audio/webm", source.contentType)
    }

    @Test
    fun `a refusal at opening waits for the refresh then retries once with a new URL`() = runTest {
        resolutions += stream("u1")
        resolutions += stream("u2")
        val source = source()
        refusing["u1"] = 1

        val bytes = source.openStream(0, 1_000).readAll()

        assertArrayEquals(BYTES, bytes)
        assertEquals(1, refreshes)
        assertEquals(listOf("u1", "u2"), requests.map { it.first })
    }

    @Test
    fun `a refusal in the middle of the body is recovered from the current position`() = runTest {
        resolutions += stream("u1", chunk = 400)
        resolutions += stream("u2", chunk = 400)
        val source = source()
        val stream = source.openStream(0, 1_000)
        // The second chunk request of u1 is refused
        refusing["u1"] = 1

        val bytes = stream.readAll()

        assertArrayEquals(BYTES, bytes)
        assertEquals(1, refreshes)
        assertEquals(listOf(Triple("u1", 0L, 400L), Triple("u1", 400L, 400L), Triple("u2", 400L, 400L), Triple("u2", 800L, 200L)), requests)
    }

    @Test
    fun `a second refusal fails the stream after one refresh only`() = runTest {
        resolutions += stream("u1")
        resolutions += stream("u2")
        val source = source()
        refusing["u1"] = 1
        refusing["u2"] = 1

        val error = assertInstanceOf(IOException::class.java, runCatching { source.openStream(0, 1_000) }.exceptionOrNull())

        assertTrue(StreamRejection.isRejection(error))
        assertEquals(1, refreshes)
    }

    @Test
    fun `another format after the refresh fails the stream cleanly`() = runTest {
        resolutions += stream("u1", itag = 251)
        resolutions += stream("u2", itag = 140)
        val source = source()
        refusing["u1"] = 1

        assertInstanceOf(UpstreamFormatChangedException::class.java, runCatching { source.openStream(0, 1_000) }.exceptionOrNull())
        assertEquals(listOf("u1"), requests.map { it.first })
    }

    @Test
    fun `a new resolution to another format is never relayed under the pinned length`() = runTest {
        resolutions += stream("u1", length = 1_000, expiresAtMs = NOW + 60_000)
        resolutions += stream("u2", length = 900)
        val source = source()
        clock.set(NOW + 59_000)

        assertInstanceOf(UpstreamFormatChangedException::class.java, runCatching { source.openStream(0, 10) }.exceptionOrNull())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `other upstream failures propagate without any refresh`() = runTest {
        resolutions += stream("u1")
        val failing = OnlineAudioSource(
            VIDEO,
            InnerTubeXAudioQuality.HIGH,
            resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH),
            resolver,
        ) { _, _, _ -> throw UpstreamStatusException(500) }

        assertInstanceOf(UpstreamStatusException::class.java, runCatching { failing.openStream(0, 10) }.exceptionOrNull())
        assertEquals(0, refreshes)
    }

    @Test
    fun `an upstream request ending with no byte is a premature end`() = runTest {
        resolutions += stream("u1")
        val empty = OnlineAudioSource(
            VIDEO,
            InnerTubeXAudioQuality.HIGH,
            resolver.resolve(VIDEO, InnerTubeXAudioQuality.HIGH),
            resolver,
        ) { _, position, length -> FakeConnection(BYTES, position.toInt(), length, cut = 0) }

        assertInstanceOf(IOException::class.java, runCatching { empty.openStream(0, 10).readAll() }.exceptionOrNull())
    }
}
