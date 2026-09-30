package app.n_zik.android.bridge.audio

import app.n_zik.android.bridge.AudioQuality
import app.n_zik.android.bridge.BridgeJson
import app.n_zik.android.bridge.BridgeServerCore
import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.IssuedDevice
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import timber.log.Timber

private const val NOW = 1_790_000_000_000L
private const val ONLINE = "dQw4w9WgXcQ"
private const val LOCAL = "local:42"
private const val FAILING = "failingTrk1"
private const val UNREACHABLE = "unreachabl1"
private const val GONE = "local:43"
private const val TRUNCATED = "truncated11"
private const val DURATION_MS = 212_000L

/** Deterministic bytes: a file whose every byte depends on its position. */
private val FILE = ByteArray(300_000) { (it * 31 + 7).toByte() }

/**
 * In-memory phone audio: [ONLINE] and [LOCAL] have bytes, [FAILING] and [UNREACHABLE] fail
 * upstream, [GONE] vanishes when its stream opens, [TRUNCATED] ends before its announced length.
 */
private class FakeAudioLibrary : AudioLibrary {
    val openedQualities = mutableListOf<AudioQuality>()

    override suspend fun track(trackId: String): AudioTrackInfo? = when (trackId) {
        ONLINE, FAILING, UNREACHABLE, TRUNCATED -> AudioTrackInfo(DURATION_MS)
        LOCAL, GONE -> AudioTrackInfo(null)
        else -> null
    }

    override suspend fun open(trackId: String, quality: AudioQuality): AudioOpenResult {
        openedQualities += quality
        return when (trackId) {
            ONLINE, LOCAL -> AudioOpenResult.Ready(object : AudioByteSource {
                override val length = FILE.size.toLong()
                override val contentType = "audio/webm"
                override suspend fun openStream(position: Long, length: Long): AudioStream =
                    ByteArrayInputStream(FILE, position.toInt(), length.toInt()).asAudioStream()
            })
            FAILING -> AudioOpenResult.UpstreamFailed
            UNREACHABLE -> AudioOpenResult.Ready(object : AudioByteSource {
                override val length = 10L
                override val contentType = "audio/mp4"
                override suspend fun openStream(position: Long, length: Long): AudioStream = throw IOException("upstream down")
            })
            GONE -> AudioOpenResult.Ready(object : AudioByteSource {
                override val length = 10L
                override val contentType = "audio/mpeg"
                override suspend fun openStream(position: Long, length: Long): AudioStream =
                    throw AudioSourceGoneException("deleted")
            })
            TRUNCATED -> AudioOpenResult.Ready(object : AudioByteSource {
                override val length = FILE.size.toLong()
                override val contentType = "audio/webm"
                override suspend fun openStream(position: Long, length: Long): AudioStream =
                    ByteArrayInputStream(FILE, 0, 1_000).asAudioStream()
            })
            else -> AudioOpenResult.NotFound
        }
    }
}

class AudioRoutesTest {

    private val clock = AtomicLong(NOW)
    private val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
    private val device: IssuedDevice = store.issue("PC-TEST")
    private val audio = FakeAudioLibrary()

    private fun newCore() = BridgeServerCore(serverName = "Pixel test", deviceStore = store, clock = clock::get, audioLibrary = audio)

    private fun ApplicationTestBuilder.mount(core: BridgeServerCore = newCore()): BridgeServerCore =
        core.also { c -> application { c.install(this) } }

    private fun errorCode(body: String): String? =
        BridgeJson.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content

    private suspend fun ApplicationTestBuilder.forge(trackId: String, body: String = """{"quality":"high"}"""): HttpResponse =
        client.post("/api/v1/audio/${trackId.replace(":", "%3A")}/url") {
            bearerAuth(device.deviceToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** Path and query of a forged URL, as the PC plays it. */
    private suspend fun ApplicationTestBuilder.forgedPath(trackId: String, quality: String = "high"): String {
        val response = forge(trackId, """{"quality":"$quality"}""")
        assertEquals(HttpStatusCode.OK, response.status)
        val url = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject["url"]?.jsonPrimitive?.content
        return Url(requireNotNull(url)).encodedPathAndQuery
    }

    // --- Forge (contract §8.1) ---

    @Test
    fun `forge returns an absolute URL to the address the request reached, with its expiry`() = testApplication {
        mount()

        val response = forge(ONLINE)

        assertEquals(HttpStatusCode.OK, response.status)
        val json = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(ONLINE, json["trackId"]?.jsonPrimitive?.content)
        assertEquals("high", json["quality"]?.jsonPrimitive?.content)
        assertEquals(NOW + DURATION_MS + 120_000, json["expiresAtMs"]?.jsonPrimitive?.long)
        assertEquals(DURATION_MS, json["durationMs"]?.jsonPrimitive?.long)
        val url = Url(requireNotNull(json["url"]?.jsonPrimitive?.content))
        assertEquals("localhost", url.host)
        assertEquals(80, url.port)
        assertEquals("/api/v1/audio/$ONLINE", url.encodedPath)
        assertTrue(url.parameters["t"].orEmpty().isNotEmpty())
    }

    @Test
    fun `forge without a known duration expires 10 min later and encodes the track id`() = testApplication {
        mount()

        val json = BridgeJson.parseToJsonElement(forge(LOCAL).bodyAsText()).jsonObject

        assertEquals(NOW + 600_000, json["expiresAtMs"]?.jsonPrimitive?.long)
        assertEquals("null", json["durationMs"].toString())
        assertTrue(json["url"]?.jsonPrimitive?.content.orEmpty().contains("/api/v1/audio/local%3A42?t="))
    }

    @Test
    fun `forge of a track outside the library is NOT_FOUND`() = testApplication {
        mount()

        val response = forge("unknownTrk1")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("NOT_FOUND", errorCode(response.bodyAsText()))
    }

    @Test
    fun `forge with an unknown quality or an invalid body is BAD_REQUEST`() = testApplication {
        mount()

        listOf("""{"quality":"ultra"}""", """{"quality":1}""", "{}", "not json", """{"quality":"${"x".repeat(5_000)}"}""")
            .forEach { body ->
                val response = forge(ONLINE, body)
                assertEquals(HttpStatusCode.BadRequest, response.status, body.take(40))
                assertEquals("BAD_REQUEST", errorCode(response.bodyAsText()))
            }
    }

    @Test
    fun `forge without Bearer is UNAUTHORIZED`() = testApplication {
        mount()

        val response = client.post("/api/v1/audio/$ONLINE/url") { setBody("""{"quality":"high"}""") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("UNAUTHORIZED", errorCode(response.bodyAsText()))
    }

    // --- Delivery (contract §8.2) ---

    @Test
    fun `GET without Range serves the whole file with its length, type and Accept-Ranges`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)

        val response = client.get(path)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(FILE.size.toString(), response.headers[HttpHeaders.ContentLength])
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        assertEquals("audio/webm", response.headers[HttpHeaders.ContentType])
        assertArrayEquals(FILE, response.readRawBytes())
        assertEquals(listOf(AudioQuality.HIGH), audio.openedQualities)
    }

    @Test
    fun `successive ranges, including a seek in the middle, give back exactly the file`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)
        val rebuilt = ByteArrayOutputStream()

        for ((start, end) in listOf(0 to 99_999, 100_000 to 199_999, 200_000 to FILE.size - 1)) {
            val response = client.get(path) { header(HttpHeaders.Range, "bytes=$start-$end") }
            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals("bytes $start-$end/${FILE.size}", response.headers[HttpHeaders.ContentRange])
            assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
            rebuilt.write(response.readRawBytes())
        }
        assertArrayEquals(FILE, rebuilt.toByteArray())

        val seek = client.get(path) { header(HttpHeaders.Range, "bytes=150000-150009") }
        assertArrayEquals(FILE.copyOfRange(150_000, 150_010), seek.readRawBytes())
    }

    @Test
    fun `open and suffix ranges are served as 206`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)

        val open = client.get(path) { header(HttpHeaders.Range, "bytes=299990-") }
        assertEquals(HttpStatusCode.PartialContent, open.status)
        assertEquals("bytes 299990-299999/300000", open.headers[HttpHeaders.ContentRange])
        assertArrayEquals(FILE.copyOfRange(299_990, 300_000), open.readRawBytes())

        val suffix = client.get(path) { header(HttpHeaders.Range, "bytes=-5") }
        assertEquals(HttpStatusCode.PartialContent, suffix.status)
        assertEquals("bytes 299995-299999/300000", suffix.headers[HttpHeaders.ContentRange])
        assertArrayEquals(FILE.copyOfRange(299_995, 300_000), suffix.readRawBytes())
    }

    @Test
    fun `range outside the file is RANGE_NOT_SATISFIABLE`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)

        val response = client.get(path) { header(HttpHeaders.Range, "bytes=300000-") }

        assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, response.status)
        assertEquals("RANGE_NOT_SATISFIABLE", errorCode(response.bodyAsText()))
        assertEquals("bytes */300000", response.headers[HttpHeaders.ContentRange])
    }

    @Test
    fun `HEAD gives the same headers as GET without a body`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)

        val full = client.head(path)
        assertEquals(HttpStatusCode.OK, full.status)
        assertEquals(FILE.size.toString(), full.headers[HttpHeaders.ContentLength])
        assertEquals("bytes", full.headers[HttpHeaders.AcceptRanges])
        assertEquals("audio/webm", full.headers[HttpHeaders.ContentType])
        assertEquals(0, full.readRawBytes().size)

        val partial = client.head(path) { header(HttpHeaders.Range, "bytes=10-19") }
        assertEquals(HttpStatusCode.PartialContent, partial.status)
        assertEquals("10", partial.headers[HttpHeaders.ContentLength])
        assertEquals("bytes 10-19/300000", partial.headers[HttpHeaders.ContentRange])
    }

    @Test
    fun `local track is served as is`() = testApplication {
        mount()

        val response = client.get(forgedPath(LOCAL))

        assertEquals(HttpStatusCode.OK, response.status)
        assertArrayEquals(FILE, response.readRawBytes())
    }

    @Test
    fun `upstream failure, before or at opening, is AUDIO_UPSTREAM_FAILED`() = testApplication {
        mount()

        for (track in listOf(FAILING, UNREACHABLE)) {
            val response = client.get(forgedPath(track)) { header(HttpHeaders.Range, "bytes=0-4") }
            assertEquals(HttpStatusCode.BadGateway, response.status, track)
            assertEquals("AUDIO_UPSTREAM_FAILED", errorCode(response.bodyAsText()))
            // Range headers only once the source is open
            assertNull(response.headers[HttpHeaders.ContentRange], track)
            assertNull(response.headers[HttpHeaders.AcceptRanges], track)
        }
    }

    @Test
    fun `tampered token, other track or no token is AUDIO_URL_INVALID and nothing is opened`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)
        val token = Url("http://h$path").parameters["t"].orEmpty()

        val tampered = client.get(path.dropLast(1) + (if (path.last() == 'A') 'B' else 'A'))
        val otherTrack = client.get("/api/v1/audio/$FAILING?t=$token")
        val missing = client.get("/api/v1/audio/$ONLINE")

        for (response in listOf(tampered, otherTrack, missing)) {
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("AUDIO_URL_INVALID", errorCode(response.bodyAsText()))
        }
        assertTrue(audio.openedQualities.isEmpty())
    }

    @Test
    fun `audio route ignores any Authorization header`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)

        val response = client.get(path) { bearerAuth("not-a-device-token") }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `URL replayed after revocation is DEVICE_REVOKED, even once expired`() = testApplication {
        val core = mount()
        val path = forgedPath(ONLINE)
        assertEquals(HttpStatusCode.OK, client.head(path).status)

        core.revokeDevice(device.deviceId)

        val response = client.get(path)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("DEVICE_REVOKED", errorCode(response.bodyAsText()))
        clock.set(NOW + 3_600_000)
        assertEquals("DEVICE_REVOKED", errorCode(client.get(path).bodyAsText()))
    }

    @Test
    fun `URL replayed after its expiry is AUDIO_URL_EXPIRED`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)

        clock.set(NOW + DURATION_MS + 120_000)
        assertEquals(HttpStatusCode.OK, client.head(path).status)
        clock.set(NOW + DURATION_MS + 120_001)
        val response = client.get(path)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("AUDIO_URL_EXPIRED", errorCode(response.bodyAsText()))
        val head = client.head(path)
        assertEquals(HttpStatusCode.Forbidden, head.status)
        assertNull(head.headers[HttpHeaders.ContentRange])
    }

    @Test
    fun `URL forged before a server restart is AUDIO_URL_INVALID`() {
        // Two successive server runs: each core draws its own signing key
        var path = ""
        testApplication {
            mount(newCore())
            path = forgedPath(ONLINE)
        }
        testApplication {
            mount(newCore())
            val response = client.get(path)
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("AUDIO_URL_INVALID", errorCode(response.bodyAsText()))
        }
    }

    @Test
    fun `qualities low and auto reach the phone's audio as forged`() = testApplication {
        mount()

        assertEquals(HttpStatusCode.OK, client.head(forgedPath(ONLINE, "low")).status)
        assertEquals(HttpStatusCode.OK, client.head(forgedPath(ONLINE, "auto")).status)

        assertEquals(listOf(AudioQuality.LOW, AudioQuality.AUTO), audio.openedQualities)
    }

    @Test
    fun `local file gone when its stream opens is NOT_FOUND`() = testApplication {
        mount()

        val response = client.get(forgedPath(GONE))

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("NOT_FOUND", errorCode(response.bodyAsText()))
        assertNull(response.headers[HttpHeaders.AcceptRanges])
    }

    @Test
    fun `HEAD outside the file is 416 without a body`() = testApplication {
        mount()
        val path = forgedPath(ONLINE)

        val response = client.head(path) { header(HttpHeaders.Range, "bytes=400000-") }

        assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, response.status)
        assertEquals("bytes */300000", response.headers[HttpHeaders.ContentRange])
        assertEquals(0, response.readRawBytes().size)
    }

    @Test
    fun `a source ending before its length never completes the body`() = testApplication {
        mount()
        val path = forgedPath(TRUNCATED)

        val outcome = runCatching { client.get(path).readRawBytes() }

        // Either the transfer fails, or fewer bytes than announced arrive: never a complete file
        assertTrue(outcome.isFailure || outcome.getOrThrow().size < FILE.size)
    }

    @Test
    fun `failing audio requests never log their token`() = testApplication {
        val logged = mutableListOf<String>()
        val captureTree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logged += message
                generateSequence(t) { it.cause }.forEach { logged += it.toString() }
            }
        }
        Timber.plant(captureTree)
        try {
            mount()
            for (track in listOf(UNREACHABLE, GONE, TRUNCATED)) {
                val path = forgedPath(track)
                val token = Url("http://h$path").parameters["t"].orEmpty()
                runCatching { client.get(path).readRawBytes() }

                assertTrue(logged.isNotEmpty(), track)
                assertTrue(logged.none { it.contains(token) }, track)
            }
        } finally {
            Timber.uproot(captureTree)
        }
    }
}
