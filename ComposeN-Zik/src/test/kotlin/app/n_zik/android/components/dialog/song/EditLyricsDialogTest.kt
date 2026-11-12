package app.n_zik.android.components.dialog.song

import java.io.ByteArrayInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import app.n_zik.android.components.player.lyrics.LyricsFileCodec
import app.n_zik.android.enums.lyrics.LyricsType

class EditLyricsDialogTest {

    @Test
    fun `saved edit row is flagged isEdited with the given song, type and text`() {
        val row = EditLyricsDialog.editedLyrics("song1", LyricsType.Synced, "[00:01.00]my line")

        assertTrue(row.isEdited)
        assertEquals("song1", row.songId)
        assertEquals(LyricsType.Synced.name, row.type)
        assertEquals("[00:01.00]my line", row.data)
    }

    @Test
    fun `read lyrics document accepts utf8 text and rejects oversized content`() {
        val text = "text: déjà vu"
        assertEquals(text, EditLyricsDialog.readLyricsFile(ByteArrayInputStream(text.encodeToByteArray())))

        val oversized = ByteArrayInputStream(ByteArray(LyricsFileCodec.MAX_FILE_BYTES + 1))
        assertThrows(IllegalArgumentException::class.java) { EditLyricsDialog.readLyricsFile(oversized) }
    }
}
