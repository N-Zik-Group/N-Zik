package app.n_zik.android.playback.services.diagnostics

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Issue #881 (gh-881): pins the seek player-command → journal-name mapping (spec S3). media3
 * 1.10.1 has NINE distinct seek command ids (values 4–12, verified on the AAR via
 * `javap -constants`); the four deprecated `_WINDOW` aliases are the same ints, so they are
 * covered automatically by both [SEEK_PLAYER_COMMANDS] and [seekCommandName].
 */
class SeekCommandNameTest {

    @Test
    fun `every distinct seek command maps to its journal name`() {
        assertEquals("SEEK_TO_DEFAULT_POSITION", seekCommandName(Player.COMMAND_SEEK_TO_DEFAULT_POSITION))
        assertEquals("SEEK_TO", seekCommandName(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertEquals("SEEK_PREVIOUS_MEDIA_ITEM", seekCommandName(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertEquals("SEEK_PREVIOUS", seekCommandName(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertEquals("SEEK_NEXT_MEDIA_ITEM", seekCommandName(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertEquals("SEEK_NEXT", seekCommandName(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals("SEEK_TO_MEDIA_ITEM", seekCommandName(Player.COMMAND_SEEK_TO_MEDIA_ITEM))
        assertEquals("SEEK_BACK", seekCommandName(Player.COMMAND_SEEK_BACK))
        assertEquals("SEEK_FORWARD", seekCommandName(Player.COMMAND_SEEK_FORWARD))
    }

    @Test
    fun `the deprecated window aliases map through their shared value`() {
        // javap -constants: IN_CURRENT_WINDOW = 5, TO_PREVIOUS_WINDOW = 6,
        // TO_NEXT_WINDOW = 8, TO_WINDOW = 10 — the same ids as the non-deprecated constants.
        assertEquals("SEEK_TO", seekCommandName(5))
        assertEquals("SEEK_PREVIOUS_MEDIA_ITEM", seekCommandName(6))
        assertEquals("SEEK_NEXT_MEDIA_ITEM", seekCommandName(8))
        assertEquals("SEEK_TO_MEDIA_ITEM", seekCommandName(10))
    }

    @Test
    fun `the shared set holds exactly the nine distinct seek command ids`() {
        assertEquals(9, SEEK_PLAYER_COMMANDS.size)
        SEEK_PLAYER_COMMANDS.forEach { id ->
            assertFalse(seekCommandName(id).startsWith("UNKNOWN"), "set member $id must map to a known name")
        }
    }

    @Test
    fun `non-seek player commands are named UNKNOWN`() {
        assertEquals("UNKNOWN(0)", seekCommandName(0))
        assertFalse(Player.COMMAND_PLAY_PAUSE in SEEK_PLAYER_COMMANDS)
    }
}
