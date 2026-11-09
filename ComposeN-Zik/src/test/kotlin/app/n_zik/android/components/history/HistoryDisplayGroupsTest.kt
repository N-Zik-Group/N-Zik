package app.n_zik.android.components.history

import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.ext.EventWithSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HistoryDisplayGroupsTest {
    private fun entry(id: String, title: String, artist: String?) = EventWithSong(
        Event(songId = id, timestamp = 42L, playTime = 1000L),
        Song(id, title, artist, "3:00", null),
    )

    @Test fun `title groups sort visible names and retain original event identity`() {
        val edited = entry("a", "modified:e:Alpha", "Artist")
        val beta = entry("b", "Beta", "Artist")
        val blank = entry("c", "modified:", null)
        val groups = historyTitleGroups(listOf(beta, edited, blank))
        assertEquals(listOf("#", "A", "B"), groups.keys.toList())
        assertSame(edited, groups.getValue("A").single())
        assertEquals("modified:e:Alpha", edited.song.title)
    }

    @Test fun `missing or blank artists remain separate from an artist named like the fallback`() {
        val missing = entry("a", "Alpha", null)
        val blank = entry("b", "Beta", " ")
        val named = entry("c", "Gamma", "Inconnu")
        val groups = historyArtistGroups(listOf(missing, blank, named))
        assertEquals(listOf(missing, blank), groups.getValue(null))
        assertEquals(listOf(named), groups.getValue("Inconnu"))
    }

    @Test fun `artist groups merge displayed names while preserving internal colons and source values`() {
        val edited = entry("a", "Alpha", "modified:Tangerl, Guest")
        val original = entry("b", "Beta", "Tangerl")
        val colon = entry("c", "Gamma", "Artist: Live")
        val groups = historyArtistGroups(listOf(edited, original, colon))
        assertEquals(listOf("Artist: Live", "Tangerl"), groups.keys.toList())
        assertEquals(listOf(original, edited), groups.getValue("Tangerl"))
        assertSame(edited, groups.getValue("Tangerl").last())
        assertEquals("modified:Tangerl, Guest", edited.song.artistsText)
    }
}
