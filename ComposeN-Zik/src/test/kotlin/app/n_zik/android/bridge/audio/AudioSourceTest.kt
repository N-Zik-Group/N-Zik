package app.n_zik.android.bridge.audio

import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.n_zik.android.bridge.AudioBodyContent
import app.n_zik.android.bridge.AudioQuality
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import io.mockk.mockk
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private val DATA = ByteArray(1_000) { (it * 13 + 5).toByte() }

/** Upstream request over [bytes] from [position]; [cut] bytes at most are served before its end. */
internal class FakeConnection(
    private val bytes: ByteArray,
    private var position: Int,
    private var remaining: Long,
    private val cut: Int = Int.MAX_VALUE,
) : UpstreamConnection {
    private var served = 0
    var closed = false
        private set

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val available = minOf(remaining, (bytes.size - position).toLong(), (cut - served).toLong()).toInt()
        if (available <= 0) return -1
        val count = minOf(length, available)
        System.arraycopy(bytes, position, buffer, offset, count)
        position += count
        remaining -= count
        served += count
        return count
    }

    override fun close() {
        closed = true
    }
}

internal suspend fun AudioStream.readAll(): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(97)
    while (true) {
        val read = read(buffer, 0, buffer.size)
        if (read < 0) return out.toByteArray()
        out.write(buffer, 0, read)
    }
}

class AudioSourceTest {

    // --- Types ---

    @Test
    fun `declared type loses its codecs parameters`() {
        assertEquals("audio/webm", AudioTypes.normalize("audio/webm; codecs=\"opus\""))
        assertEquals("audio/mp4", AudioTypes.normalize("AUDIO/MP4;codecs=\"mp4a.40.2\""))
        assertNull(AudioTypes.normalize("application/octet-stream"))
        assertNull(AudioTypes.normalize(" "))
    }

    @Test
    fun `type is sniffed from the first bytes when not declared`() {
        fun head(vararg bytes: Int) = ByteArray(12).also { bytes.forEachIndexed { i, b -> it[i] = b.toByte() } }

        assertEquals("audio/flac", AudioTypes.resolve(null, "fLaC".toByteArray()))
        assertEquals("audio/ogg", AudioTypes.resolve(null, "OggS".toByteArray()))
        assertEquals("audio/webm", AudioTypes.resolve(null, head(0x1A, 0x45, 0xDF, 0xA3)))
        assertEquals("audio/mp4", AudioTypes.resolve(null, "\u0000\u0000\u0000 ftypM4A ".toByteArray()))
        assertEquals("audio/mpeg", AudioTypes.resolve(null, "ID3".toByteArray()))
        assertEquals("audio/mpeg", AudioTypes.resolve(null, head(0xFF, 0xFB)))
        assertEquals("application/octet-stream", AudioTypes.resolve(null, head(1, 2, 3)))
        assertEquals("audio/ogg", AudioTypes.resolve("audio/ogg", "fLaC".toByteArray()))
    }

    // --- Quality ---

    @Test
    fun `low and high map to the phone's equivalent settings, auto to the phone's own setting`() {
        assertEquals(AudioQualityFormat.Low, AudioQuality.LOW.toQualityFormat(AudioQualityFormat.High))
        assertEquals(AudioQualityFormat.High, AudioQuality.HIGH.toQualityFormat(AudioQualityFormat.Low))
        assertEquals(AudioQualityFormat.Low, AudioQuality.AUTO.toQualityFormat(AudioQualityFormat.Low))
        assertEquals(AudioQualityFormat.Auto, AudioQuality.AUTO.toQualityFormat(AudioQualityFormat.Auto))
    }

    // --- Upstream refusals ---

    @Test
    fun `401, 403 and 410 are refusals, even nested in other causes`() {
        for (code in listOf(401, 403, 410)) {
            assertTrue(StreamRejection.isRejection(UpstreamStatusException(code)), "code=$code")
            assertTrue(StreamRejection.isRejection(IOException("wrapped", RuntimeException(UpstreamStatusException(code)))))
        }
    }

    @OptIn(UnstableApi::class)
    private fun invalidResponse(code: Int) =
        HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), mockk<DataSpec>(relaxed = true), ByteArray(0))

    @Test
    fun `media3 401, 403 and 410 responses are refusals, bare or as a cause`() {
        for (code in listOf(401, 403, 410)) {
            assertTrue(StreamRejection.isRejection(invalidResponse(code)), "code=$code")
            assertTrue(StreamRejection.isRejection(IOException("open failed", invalidResponse(code))), "wrapped code=$code")
        }
        assertFalse(StreamRejection.isRejection(invalidResponse(404)))
        assertFalse(StreamRejection.isRejection(IOException("open failed", invalidResponse(500))))
    }

    @Test
    fun `a resolved stream never prints its URL or headers`() {
        val stream = ResolvedStream(
            url = "https://upstream.example/videoplayback?sig=SECRET_SIG",
            headers = mapOf("Cookie" to "SECRET_COOKIE"),
            itag = 251,
            contentLength = 1_000,
            mimeType = "audio/webm",
            chunkSizeBytes = 0,
            expiresAtMs = 0,
        )

        val text = stream.toString()

        assertFalse(text.contains("SECRET_SIG") || text.contains("upstream.example") || text.contains("SECRET_COOKIE"), text)
    }

    @Test
    fun `other statuses and plain failures are not refusals`() {
        assertFalse(StreamRejection.isRejection(UpstreamStatusException(404)))
        assertFalse(StreamRejection.isRejection(UpstreamStatusException(500)))
        assertFalse(StreamRejection.isRejection(IOException("network down")))
    }

    // --- Sequential stream ---

    @Test
    fun `stream is rebuilt across requests that each end early`() = runTest {
        val opened = mutableListOf<Long>()
        val stream = SequentialStream(100, 800) { position, remaining ->
            opened += position
            FakeConnection(DATA, position.toInt(), remaining, cut = 250)
        }

        assertArrayEquals(DATA.copyOfRange(100, 900), stream.readAll())
        assertEquals(listOf(100L, 350L, 600L, 850L), opened)
    }

    @Test
    fun `a request yielding nothing is a premature end`() = runTest {
        val stream = SequentialStream(0, 500) { position, remaining ->
            FakeConnection(DATA, position.toInt(), remaining, cut = if (position == 0L) 200 else 0)
        }

        assertInstanceOf(IOException::class.java, runCatching { stream.readAll() }.exceptionOrNull())
    }

    @Test
    fun `bounded input stream fails when its source ends early`() {
        val input = BoundedInputStream(ByteArrayInputStream(DATA, 0, 10), 20)

        assertThrows<IOException> { input.readBytes() }
    }

    // --- Body ---

    @Test
    fun `body copies exactly its length`() = runTest {
        val channel = ByteChannel(autoFlush = true)
        val body = AudioBodyContent(ByteArrayInputStream(DATA).asAudioStream(), HttpStatusCode.OK, ContentType.Audio.Any, 300)

        body.writeTo(channel)
        channel.flushAndClose()

        assertArrayEquals(DATA.copyOfRange(0, 300), channel.readRemaining().readByteArray())
    }

    @Test
    fun `body of a source ending early fails instead of completing`() = runTest {
        val body = AudioBodyContent(ByteArrayInputStream(DATA, 0, 10).asAudioStream(), HttpStatusCode.OK, ContentType.Audio.Any, 20)

        assertInstanceOf(IOException::class.java, runCatching { body.writeTo(ByteChannel(autoFlush = true)) }.exceptionOrNull())
    }

    // --- Remembered sources ---

    private class CountingSource(val failOpen: Boolean = false, val failRead: Boolean = false) : AudioByteSource {
        override val length = DATA.size.toLong()
        override val contentType = "audio/webm"
        override suspend fun openStream(position: Long, length: Long): AudioStream {
            if (failOpen) throw IOException("gone")
            if (failRead) {
                return object : AudioStream {
                    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int = error("provider broke")
                    override fun close() = Unit
                }
            }
            return ByteArrayInputStream(DATA, position.toInt(), length.toInt()).asAudioStream()
        }
    }

    @Test
    fun `a source whose stream read fails with any exception is forgotten`() = runTest {
        val memo = AudioSourceMemo()
        var opens = 0

        val broken = memo.getOrOpen("t", AudioQuality.HIGH) { opens++; AudioOpenResult.Ready(CountingSource(failRead = true)) }
        val stream = (broken as AudioOpenResult.Ready).source.openStream(0, 10)
        assertInstanceOf(IllegalStateException::class.java, runCatching { stream.read(ByteArray(10), 0, 10) }.exceptionOrNull())
        memo.getOrOpen("t", AudioQuality.HIGH) { opens++; AudioOpenResult.Ready(CountingSource()) }

        assertEquals(2, opens)
    }

    @Test
    fun `beyond its capacity the least recently used source is dropped`() = runTest {
        val memo = AudioSourceMemo(maxEntries = 2)
        val opened = mutableListOf<String>()
        suspend fun get(track: String) = memo.getOrOpen(track, AudioQuality.HIGH) { opened += track; AudioOpenResult.Ready(CountingSource()) }

        get("a")
        get("b")
        get("a") // touched: "b" is now the least recently used
        get("c")
        get("a")
        get("b")

        assertEquals(listOf("a", "b", "c", "b"), opened)
    }

    @Test
    fun `one source per track and quality serves the successive requests`() = runTest {
        val memo = AudioSourceMemo()
        var opens = 0
        val open = suspend { opens++; AudioOpenResult.Ready(CountingSource()) }

        val first = memo.getOrOpen("t", AudioQuality.HIGH, open) as AudioOpenResult.Ready
        val second = memo.getOrOpen("t", AudioQuality.HIGH, open) as AudioOpenResult.Ready
        memo.getOrOpen("t", AudioQuality.LOW, open)

        assertSame(first.source, second.source)
        assertEquals(2, opens)
        assertArrayEquals(DATA.copyOfRange(10, 20), second.source.openStream(10, 10).readAll())
    }

    @Test
    fun `an idle source is opened again`() = runTest {
        val clock = AtomicLong(0)
        val memo = AudioSourceMemo(clock = clock::get, idleTtlMs = 1_000)
        var opens = 0
        val open = suspend { opens++; AudioOpenResult.Ready(CountingSource()) }

        memo.getOrOpen("t", AudioQuality.HIGH, open)
        clock.set(1_001)
        memo.getOrOpen("t", AudioQuality.HIGH, open)

        assertEquals(2, opens)
    }

    @Test
    fun `a source whose stream fails is forgotten, a failed opening is not remembered`() = runTest {
        val memo = AudioSourceMemo()
        var opens = 0

        val failing = memo.getOrOpen("t", AudioQuality.HIGH) { opens++; AudioOpenResult.Ready(CountingSource(failOpen = true)) }
        assertInstanceOf(IOException::class.java, runCatching { (failing as AudioOpenResult.Ready).source.openStream(0, 1) }.exceptionOrNull())
        memo.getOrOpen("t", AudioQuality.HIGH) { opens++; AudioOpenResult.UpstreamFailed }
        memo.getOrOpen("t", AudioQuality.HIGH) { opens++; AudioOpenResult.Ready(CountingSource()) }

        assertEquals(3, opens)
    }
}
