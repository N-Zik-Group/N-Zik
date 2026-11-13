package app.n_zik.android.components.player.lyrics

import app.n_zik.lrclib.models.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackDurationTest {

    private fun durationOf(raw: JsonElement) = Track(
        id = 1L,
        name = "Song",
        trackName = "Song",
        artistName = "Artist",
        albumName = "Album",
        tDuration = raw,
        instrumental = false,
        plainLyrics = null,
        syncedLyrics = null
    ).duration

    @Test
    fun `a numeric duration is truncated to whole seconds`() {
        assertEquals(233L, durationOf(JsonPrimitive(233)))
        assertEquals(233L, durationOf(JsonPrimitive(233.7)))
    }

    @Test
    fun `a duration given as an array uses its first element`() {
        assertEquals(200L, durationOf(JsonArray(listOf(JsonPrimitive(200.4), JsonPrimitive(300)))))
    }

    @Test
    fun `a null duration from LrcLib no longer throws and counts as zero`() {
        // LrcLib returns `"duration": null` for some tracks; the track list crashed on it (NumberFormatException).
        assertEquals(0L, durationOf(JsonNull))
    }

    @Test
    fun `an unreadable or empty duration counts as zero`() {
        assertEquals(0L, durationOf(JsonPrimitive("abc")))
        assertEquals(0L, durationOf(JsonArray(emptyList())))
    }
}
