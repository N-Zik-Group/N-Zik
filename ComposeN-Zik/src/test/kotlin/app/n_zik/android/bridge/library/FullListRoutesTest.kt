package app.n_zik.android.bridge.library

import androidx.media3.common.MediaItem
import app.n_zik.android.bridge.AuthResult
import app.n_zik.android.bridge.BridgeJson
import app.n_zik.android.bridge.BridgeServerCore
import app.n_zik.android.bridge.BridgeUiSettings
import app.n_zik.android.bridge.DeviceAuthenticator
import app.n_zik.android.bridge.UiSettingsDto
import app.n_zik.android.bridge.command.ListAction
import app.n_zik.android.bridge.command.ListCommandExecutor
import app.n_zik.android.bridge.command.ListCommandResult
import app.n_zik.android.bridge.command.ListItemResolver
import app.n_zik.android.bridge.command.ListQueueExecutor
import app.n_zik.android.bridge.command.PhoneQueue
import app.n_zik.android.bridge.command.QueueListCommandBody
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.bridge.state.BridgeServerMessage
import app.n_zik.android.bridge.state.ToastMessage
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.encodeServerMessage
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val FULL_TOKEN = "valid-token"

/** A 2000-track library (the acceptance case of the full-list routes): ids `t0`…`t1999`, titles in order. */
private class BigLibrary : LibraryProvider by LibraryProvider.EMPTY {
    val tracks = (0 until 2_000).map { libSong("t$it", "Song %04d".format(it), durationMs = 1_000L) }

    override suspend fun songs(filter: SongFilter, sort: SongSort, reverse: Boolean, period: TopPeriod?): List<TrackDto> =
        if (filter == SongFilter.LIKED) tracks.take(10) else tracks.let { if (reverse) it.reversed() else it }

    override suspend fun playlistSongs(playlistId: Long, sort: PlaylistSongSort, reverse: Boolean): List<TrackDto>? =
        if (playlistId == 7L) tracks.take(3) else null

    override suspend fun albumSongs(albumId: String): List<TrackDto>? = if (albumId == "A") tracks.take(4) else null
}

/** Records the phone queue entry a list command reached. */
private class RecordingQueue(private val available: Boolean = true) : PhoneQueue {
    val calls = mutableListOf<Pair<String, Int>>()
    var lastIndex = -1

    override suspend fun play(items: List<MediaItem>, index: Int): Boolean {
        calls += "play" to items.size
        lastIndex = index
        return available
    }

    override suspend fun shuffle(items: List<MediaItem>): Boolean = available.also { calls += "shuffle" to items.size }
    override suspend fun addNext(items: List<MediaItem>): Boolean = available.also { calls += "next" to items.size }
    override suspend fun enqueue(items: List<MediaItem>): Boolean = available.also { calls += "end" to items.size }
}

/** Every id resolves to a (mocked) media item, except the ones in [gone]. */
private class FakeItems(private val gone: Set<String> = emptySet()) : ListItemResolver {
    override suspend fun resolve(trackIds: List<String>): List<Pair<String, MediaItem>> =
        trackIds.filterNot { it in gone }.map { it to mockk<MediaItem>(relaxed = true) }
}

class FullListRoutesTest {

    private val acceptOnly = DeviceAuthenticator { token ->
        if (token == FULL_TOKEN) AuthResult.Accepted(deviceId = "test-device") else AuthResult.Revoked
    }

    private fun ApplicationTestBuilder.mount(
        library: LibraryProvider = BigLibrary(),
        lists: ListCommandExecutor = ListCommandExecutor.UNAVAILABLE,
        settings: UiSettingsDto = UiSettingsDto(),
    ) {
        application {
            BridgeServerCore(
                serverName = "Pixel test",
                authenticator = acceptOnly,
                libraryProvider = library,
                listCommandExecutor = lists,
                uiSettings = { settings },
            ).install(this)
        }
    }

    private fun executor(queue: PhoneQueue, locked: Boolean = false, gone: Set<String> = emptySet()) =
        ListQueueExecutor(BigLibrary(), queue, BridgeStateHub(), guestLocked = { locked }, items = FakeItems(gone))

    // --- library.locate ---

    @Test
    fun `locate finds a track beyond any loaded page`() = testApplication {
        mount()
        val response = client.get("/api/v1/library/locate?kind=songs&trackId=t1500") { bearerAuth(FULL_TOKEN) }
        assertEquals(HttpStatusCode.OK, response.status)
        val json = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1_500, json.getValue("index").jsonPrimitive.int)
        assertEquals(2_000, json.getValue("total").jsonPrimitive.int)
    }

    @Test
    fun `locate follows the list parameters and answers -1 when absent`() = testApplication {
        mount()
        val reversed = client.get("/api/v1/library/locate?kind=songs&reverse=true&trackId=t1999") { bearerAuth(FULL_TOKEN) }
        assertEquals(0, BridgeJson.parseToJsonElement(reversed.bodyAsText()).jsonObject.getValue("index").jsonPrimitive.int)
        val searched = client.get("/api/v1/library/locate?kind=songs&text=Song%2000&trackId=t1500") { bearerAuth(FULL_TOKEN) }
        assertEquals(-1, BridgeJson.parseToJsonElement(searched.bodyAsText()).jsonObject.getValue("index").jsonPrimitive.int)
    }

    @Test
    fun `locate rejects an unknown list, kind or filter`() = testApplication {
        mount()
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/library/locate?kind=playlist&id=99&trackId=t1") { bearerAuth(FULL_TOKEN) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/library/locate?kind=nope&trackId=t1") { bearerAuth(FULL_TOKEN) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/library/locate?kind=songs&filter=bad&trackId=t1") { bearerAuth(FULL_TOKEN) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/library/locate?kind=songs") { bearerAuth(FULL_TOKEN) }.status)
    }

    // --- album / artist duration (1.10.0) ---

    @Test
    fun `album songs carry the whole list duration`() = testApplication {
        mount()
        val response = client.get("/api/v1/library/albums/A/songs?limit=1") { bearerAuth(FULL_TOKEN) }
        assertEquals(4_000L, BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("totalDurationMs").jsonPrimitive.long)
    }

    // --- ui.settings ---

    @Test
    fun `ui settings serves the injected settings`() = testApplication {
        mount(settings = UiSettingsDto(colorPaletteName = "PureBlack", topN = null))
        val response = client.get("/api/v1/ui/settings") { bearerAuth(FULL_TOKEN) }
        assertEquals(HttpStatusCode.OK, response.status)
        val json = BridgeJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("PureBlack", json.getValue("colorPaletteName").jsonPrimitive.content)
        assertEquals("Fade", json.getValue("transitionEffect").jsonPrimitive.content)
    }

    @Test
    fun `ui settings needs the bearer`() = testApplication {
        mount()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/ui/settings").status)
    }

    @Test
    fun `ui settings reader keeps the phone defaults and resolves Top N`() {
        val empty = object : BridgeUiSettings.Source {
            override fun string(key: String): String? = null
            override fun boolean(key: String, default: Boolean) = default
            override fun float(key: String, default: Float) = default
            override fun int(key: String, default: Int) = default
        }
        assertEquals(UiSettingsDto(), BridgeUiSettings.read(empty))
        val custom = object : BridgeUiSettings.Source by empty {
            override fun string(key: String): String? = if (key == "MaxTopPlaylistItems") "Custom" else null
            override fun int(key: String, default: Int) = 25
        }
        assertEquals(25, BridgeUiSettings.read(custom).topN)
        assertNull(BridgeUiSettings.topN("Unlimited", 3))
        assertEquals(50, BridgeUiSettings.topN("50", 3))
        assertEquals(10, BridgeUiSettings.topN("garbage", 3))
        // The phone's now-playing indicator (its `MusicAnimationType` name), `Bubbles` by default
        assertEquals("Bubbles", BridgeUiSettings.read(empty).nowPlayingIndicator)
        val bars = object : BridgeUiSettings.Source by empty {
            override fun string(key: String): String? = if (key == "nowPlayingIndicator") "Bars" else null
        }
        assertEquals("Bars", BridgeUiSettings.read(bars).nowPlayingIndicator)
    }

    // --- queue.fullList ---

    @Test
    fun `queue list without a phone answers 503`() = testApplication {
        mount()
        val response = client.post("/api/v1/queue/list") {
            bearerAuth(FULL_TOKEN)
            setBody("""{"list":{"kind":"songs"},"action":"shuffle"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun `queue list route maps the executor outcome`() = testApplication {
        val queue = RecordingQueue()
        mount(lists = executor(queue))
        val ok = client.post("/api/v1/queue/list") {
            bearerAuth(FULL_TOKEN)
            setBody("""{"list":{"kind":"songs"},"action":"play","startIndex":1500,"startTrackId":"t1500"}""")
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        // The answer never waits for the queue: changed is false
        assertEquals("false", BridgeJson.parseToJsonElement(ok.bodyAsText()).jsonObject.getValue("changed").jsonPrimitive.content)
        assertEquals(listOf("play" to 2_000), queue.calls)
        assertEquals(1_500, queue.lastIndex)
        val unknown = client.post("/api/v1/queue/list") {
            bearerAuth(FULL_TOKEN)
            setBody("""{"list":{"kind":"playlist","id":"99"},"action":"end"}""")
        }
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        val bad = client.post("/api/v1/queue/list") {
            bearerAuth(FULL_TOKEN)
            setBody("""{"list":{"kind":"songs"},"action":"sideways"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }

    @Test
    fun `the whole list reaches the phone entries, not a page`() = runTest {
        val queue = RecordingQueue()
        val exec = executor(queue)
        for (action in listOf("shuffle", "next", "end")) {
            val result = exec.execute("""{"list":{"kind":"songs"},"action":"$action"}""")
            assertTrue(result is ListCommandResult.Applied)
        }
        assertEquals(listOf("shuffle" to 2_000, "next" to 2_000, "end" to 2_000), queue.calls)
    }

    @Test
    fun `list commands follow the list reference`() = runTest {
        val queue = RecordingQueue()
        val exec = executor(queue)
        exec.execute("""{"list":{"kind":"songs","filter":"liked"},"action":"shuffle"}""")
        exec.execute("""{"list":{"kind":"playlist","id":"7"},"action":"end"}""")
        exec.execute("""{"list":{"kind":"album","id":"A"},"action":"next"}""")
        exec.execute("""{"list":{"kind":"songs","text":"Song 19"},"action":"shuffle"}""")
        assertEquals(listOf("shuffle" to 10, "end" to 3, "next" to 4, "shuffle" to 100), queue.calls)
    }

    @Test
    fun `a stale start index is corrected by the start track, an absent start track is 404`() = runTest {
        val queue = RecordingQueue()
        val exec = executor(queue)
        exec.execute("""{"list":{"kind":"songs"},"action":"play","startIndex":3,"startTrackId":"t10"}""")
        assertEquals(10, queue.lastIndex)
        assertEquals(ListCommandResult.NotFound, exec.execute("""{"list":{"kind":"songs"},"action":"play","startIndex":3,"startTrackId":"zz"}"""))
        assertEquals(ListCommandResult.NotFound, exec.execute("""{"list":{"kind":"songs"},"action":"play","startIndex":5000}"""))
    }

    @Test
    fun `tracks gone from the database are skipped, the start follows its track`() = runTest {
        val queue = RecordingQueue()
        executor(queue, gone = setOf("t0", "t1")).execute("""{"list":{"kind":"songs"},"action":"play","startTrackId":"t5"}""")
        assertEquals(listOf("play" to 1_998), queue.calls)
        assertEquals(3, queue.lastIndex)
    }

    @Test
    fun `guest lock and missing player are refused`() = runTest {
        assertEquals(ListCommandResult.Rejected, executor(RecordingQueue(), locked = true).execute("""{"list":{"kind":"songs"},"action":"shuffle"}"""))
        assertEquals(ListCommandResult.Unavailable, executor(RecordingQueue(available = false)).execute("""{"list":{"kind":"songs"},"action":"shuffle"}"""))
    }

    @Test
    fun `parse validates the body`() {
        assertEquals(ListAction.NEXT, ListQueueExecutor.parse("""{"list":{"kind":"songs"},"action":"next"}""")?.action)
        assertNull(ListQueueExecutor.parse("""{"list":{"kind":"songs"},"action":"next","startIndex":-1}"""))
        assertNull(ListQueueExecutor.parse("""{"action":"next"}"""))
        assertNull(ListQueueExecutor.parse("not json"))
        val longId = "x".repeat(65)
        assertNull(ListQueueExecutor.parse("""{"list":{"kind":"songs"},"action":"next","commandId":"$longId"}"""))
        assertEquals(
            2,
            ListQueueExecutor.startIndexOf(QueueListCommandBody(ListRef(ListKind.SONGS), ListAction.PLAY, startIndex = 0, startTrackId = "c"), listOf("a", "b", "c")),
        )
    }

    // --- ui.toasts ---

    @Test
    fun `toast message wire form`() {
        val message: BridgeServerMessage = ToastMessage("songs_shuffled", listOf("12"), "success", "12 songs shuffled")
        val json = BridgeJson.parseToJsonElement(encodeServerMessage(message)).jsonObject
        assertEquals("toast", json.getValue("type").jsonPrimitive.content)
        assertEquals("songs_shuffled", json.getValue("key").jsonPrimitive.content)
        assertEquals("success", json.getValue("toastType").jsonPrimitive.content)
        assertEquals("12", json.getValue("args").jsonArray.single().jsonPrimitive.content)
        // Not revised: no revision field
        assertNull(json["revision"])
    }

    @Test
    fun `toasts reach the subscribers of the hub`() {
        val hub = BridgeStateHub()
        val received = mutableListOf<BridgeServerMessage>()
        hub.subscribe { received += it }
        hub.broadcast(ToastMessage("no_song_to_shuffle", emptyList(), "info", "No song to shuffle"))
        assertTrue(received.any { it is ToastMessage && it.key == "no_song_to_shuffle" })
    }
}

class OrderResolvedTest {
    @Test
    fun `batched resolution keeps the list order and skips unknown ids`() {
        val found = mapOf("b" to 2, "a" to 1)
        assertEquals(listOf("a" to 1, "b" to 2, "a" to 1), app.n_zik.android.bridge.command.orderResolved(listOf("a", "x", "b", "a")) { found[it] })
    }
}
