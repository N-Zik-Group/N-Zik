package app.n_zik.android.components.player.lyrics

import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsFileCodecTest {
    @Test
    fun `plain lyrics preserve section breaks and leading spaces`() {
        val plain = "[Verse]\n  One line\n\nAnother line\n"
        val yaml = LyricsFileCodec.encode(Lyrics("song", LyricsType.Unsynced.name, plain), "Song", "Artist")

        assertEquals(ImportedLyrics(LyricsType.Unsynced, plain), LyricsFileCodec.decode(yaml))
    }

    @Test
    fun `line timings retain millisecond precision after export`() {
        val yaml = document("""
            lines:
              - text: Second line
                start_ms: 2107
              - text: ' First line  '
                start_ms: 1237
            plain: Alternate text
        """.trimIndent())

        val imported = LyricsFileCodec.decode(yaml)
        val exported = LyricsFileCodec.encode(Lyrics("song", imported.type.name, imported.text), "Song", "Artist")

        assertEquals(ImportedLyrics(LyricsType.Synced, "[00:01.237] First line  \n[00:02.107]Second line\n"), imported)
        assertEquals(imported, LyricsFileCodec.decode(exported))
    }

    @Test
    fun `word timings preserve spaces delimiters and optional ends`() {
        val yaml = document("""
            lines:
              - text: 'Oui| :%7C {bg} 後'
                start_ms: 1237
                end_ms: 1900
                words:
                  - text: 'Oui| :%7C {bg} '
                    start_ms: 1247
                    end_ms: 1531
                  - text: '後'
                    start_ms: 1609
        """.trimIndent())

        val imported = LyricsFileCodec.decode(yaml)
        val exported = LyricsFileCodec.encode(Lyrics("song", imported.type.name, imported.text), "Song", "Artist")
        val row = parseKaraokeLrc(LyricsFileCodec.decode(exported).text).single()

        assertEquals(LyricsType.Karaoke, imported.type)
        assertEquals(1237L, row.timeMs)
        assertEquals(1900L, row.endMs)
        assertEquals("Oui| :%7C {bg} 後", row.text)
        assertFalse(row.isBackground)
        assertEquals(listOf("Oui| :%7C {bg} ", "後"), row.words.map { it.text })
        assertEquals(listOf(1247L, 1609L), row.words.map { it.startMs })
        assertEquals(1531L, row.words.first().endMs)
        assertTrue(row.words.first().hasExplicitEnd)
        assertFalse(row.words.last().hasExplicitEnd)
        assertEquals(1900L, row.words.last().endMs)
    }

    @Test
    fun `synced lyrics preserve literal formatting markers and explicit line end`() {
        val yaml = document("""
            lines:
              - text: '{agent:v1} and {bg}'
                start_ms: 1000
                end_ms: 1679
              - text: 'Literal {bg}'
                start_ms: 2000
        """.trimIndent())

        val imported = LyricsFileCodec.decode(yaml)
        val exported = LyricsFileCodec.encode(Lyrics("song", imported.type.name, imported.text), null, null)
        val rows = parseSyncedSentences(LyricsFileCodec.decode(exported).text).drop(1)

        assertEquals(listOf("{agent:v1} and {bg}", "Literal {bg}"), rows.map { it.text })
        assertEquals(listOf(1679L, null), rows.map { it.endMs })
        assertTrue(rows.all { it.agent == null && !it.isBackground })
    }

    @Test
    fun `existing BetterLyrics karaoke exports spaces between words`() {
        val karaoke = "[00:01.00]Hello world\n<Hello:1.0:1.5|world:1.5:2.0>"
        val yaml = LyricsFileCodec.encode(Lyrics("song", LyricsType.Karaoke.name, karaoke), null, null)
        val roundTrip = parseKaraokeLrc(LyricsFileCodec.decode(yaml).text).single()

        assertEquals(1000L, roundTrip.timeMs)
        assertEquals(listOf("Hello ", "world"), roundTrip.words.map { it.text })
        assertEquals(listOf(1500L, 2000L), roundTrip.words.map { it.endMs })
    }

    @Test
    fun `malformed or unsupported YAML fails before lyrics can be written`() {
        val invalid = listOf(
            document("plain: one\nplain: duplicate"),
            document("plain: !custom value"),
            document("value: &shared one\nplain: *shared"),
            document("plain: one\n---\nplain: other"),
            document("plain: one\nextra: " + "[".repeat(20) + "x" + "]".repeat(20)),
            document("lines: [{text: wrong, start_ms: -1}]"),
            document("lines: [{text: wrong, start_ms: 100, end_ms: 90}]"),
            document("plain: one").replace("'1.0'", "'2.0'"),
            document("plain: " + "è".repeat(LyricsFileCodec.MAX_FILE_BYTES / 2))
        )

        invalid.forEach { yaml ->
            assertThrows(IllegalArgumentException::class.java) { LyricsFileCodec.decode(yaml) }
        }
    }

    private fun document(body: String): String =
        "version: '1.0'\nmetadata:\n  title: Song\n  artist: Artist\n$body\n"
}
