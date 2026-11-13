package app.n_zik.android.components.player.lyrics

import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.lrclib.models.Track
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsTrackSelectorTest {

    private fun track(plain: String?, synced: String?) = Track(
        id = 1L,
        name = "Song",
        trackName = "Song",
        artistName = "Artist",
        albumName = "Album",
        tDuration = JsonPrimitive(200),
        instrumental = false,
        plainLyrics = plain,
        syncedLyrics = synced
    )

    @Test
    fun `in Synced mode a track with synced lyrics is saved as a Synced row flagged isEdited`() {
        val row = checkNotNull(pickedLyrics("song1", track(plain = "plain", synced = "[00:01.00]synced"), LyricsType.Synced))

        assertEquals("song1", row.songId)
        assertEquals(LyricsType.Synced.name, row.type)
        assertEquals("[00:01.00]synced", row.data)
        // The flag keeps an automatic fetch from replacing the user's deliberate choice.
        assertTrue(row.isEdited)
    }

    @Test
    fun `in Synced mode a plain-only track cannot be picked`() {
        val plainOnly = track(plain = "plain text", synced = null)

        // Plain text in a Synced row would break the synced view, and an Unsynced row would never be shown.
        assertFalse(canPick(plainOnly, LyricsType.Synced))
        assertNull(pickedLyrics("song1", plainOnly, LyricsType.Synced))
    }

    @Test
    fun `in Unsynced mode a track with plain text is saved as an Unsynced row flagged isEdited`() {
        val row = checkNotNull(pickedLyrics("song1", track(plain = "plain text", synced = "[00:01.00]synced"), LyricsType.Unsynced))

        assertEquals(LyricsType.Unsynced.name, row.type)
        assertEquals("plain text", row.data)
        assertTrue(row.isEdited)
    }

    @Test
    fun `in Unsynced mode a synced-only track is saved as plain text without its timestamps`() {
        val row = checkNotNull(
            pickedLyrics("song1", track(plain = null, synced = "[00:01.00]Hello\n[00:02.00]World"), LyricsType.Unsynced)
        )

        assertEquals(LyricsType.Unsynced.name, row.type)
        assertEquals("Hello\nWorld", row.data)
        assertTrue(row.isEdited)
    }

    @Test
    fun `a track without any text cannot be picked in any mode`() {
        val instrumental = track(plain = "", synced = "  ")

        // An empty row would overwrite the stored lyrics of that type.
        listOf(LyricsType.Synced, LyricsType.Unsynced).forEach { mode ->
            assertFalse(canPick(instrumental, mode), "mode $mode")
            assertNull(pickedLyrics("song1", instrumental, mode), "mode $mode")
        }
        assertNull(pickedLyrics("song1", track(plain = null, synced = null), LyricsType.Unsynced))
    }
}
