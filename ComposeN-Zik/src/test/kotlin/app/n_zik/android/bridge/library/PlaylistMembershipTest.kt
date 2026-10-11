package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.bridge.BridgeJson
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackMapping
import app.n_zik.android.bridge.state.queueMembershipMoved
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `Track.inPlaylist` (contract §1.1, since 1.12.0): the batched membership read, its failure, the
 * mapping of the library rows and the WS queue, and the queue's re-sample on a membership move.
 */
class PlaylistMembershipTest {

    private fun song(id: String) = Song(id = id, title = "Title $id", artistsText = "Artist", durationText = "3:00", thumbnailUrl = null)

    @Test
    fun `membership is read in chunks under the SQLite bound parameters`() = runTest {
        val ids = (0 until 2_000).map { "id$it" }
        val chunks = mutableListOf<Int>()

        val mapped = PlaylistMembership.read(ids) { chunk ->
            chunks += chunk.size
            chunk.filter { it.endsWith("7") }
        }

        assertEquals(listOf(900, 900, 200), chunks)
        assertTrue(chunks.all { it < 999 })
        assertEquals(200, mapped.size)
        assertTrue("id17" in mapped)
        assertFalse("id18" in mapped)
    }

    @Test
    fun `no ids reads nothing`() = runTest {
        var calls = 0
        assertEquals(emptySet<String>(), PlaylistMembership.read(emptyList()) { calls++; it })
        assertEquals(0, calls)
    }

    @Test
    fun `a failed read leaves every track out of the playlists`() = runTest {
        val mapped = PlaylistMembership.read(listOf("a", "b")) { error("SQLITE_ERROR") }

        assertEquals(emptySet<String>(), mapped)
        assertFalse(LibraryMapping.track(song("a"), TrackSupport(inPlaylist = mapped)).inPlaylist)
    }

    @Test
    fun `the library rows carry the membership of their support`() {
        val support = TrackSupport(inPlaylist = setOf("in"))

        assertTrue(LibraryMapping.track(song("in"), support).inPlaylist)
        assertFalse(LibraryMapping.track(song("out"), support).inPlaylist)
        assertFalse(LibraryMapping.track(song("in"), TrackSupport()).inPlaylist)
    }

    @Test
    fun `the queue track carries the flag and the wire writes it`() {
        val track = TrackMapping.track(
            mediaId = "aaaaaaaaaaa",
            title = "Title",
            artist = "Artist",
            hasArtwork = false,
            durationText = "3:00",
            isLiked = false,
            isDownloaded = false,
            inPlaylist = true,
        )

        assertTrue(track.inPlaylist)
        val json = BridgeJson.parseToJsonElement(BridgeJson.encodeToString(TrackDto.serializer(), track)).jsonObject
        assertTrue(json.getValue("inPlaylist").jsonPrimitive.boolean)
        // A queue track built without the flag stays out of the playlists
        assertFalse(TrackMapping.track("b", "T", "A", false, null, isLiked = false, isDownloaded = false).inPlaylist)
    }

    @Test
    fun `a membership move on a queue track re-samples, an unchanged one does not`() {
        val ids = listOf("a", "b")

        assertTrue(queueMembershipMoved(ids, known = setOf("a"), fresh = setOf("a", "b")))
        assertTrue(queueMembershipMoved(ids, known = setOf("a"), fresh = emptySet()))
        assertFalse(queueMembershipMoved(ids, known = setOf("a"), fresh = setOf("a")))
        // A track outside the queue does not count
        assertFalse(queueMembershipMoved(ids, known = setOf("a"), fresh = setOf("a", "z")))
    }
}
