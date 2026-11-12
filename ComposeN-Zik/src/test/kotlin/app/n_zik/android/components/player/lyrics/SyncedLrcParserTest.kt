package app.n_zik.android.components.player.lyrics

import app.n_zik.android.components.player.lyrics.utils.HtmlDecoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncedLrcParserTest {

    @Test
    fun `decodeHtmlEntities decodes named and numeric entities`() {
        val decoded = HtmlDecoder.decodeHtmlEntities("A &amp; B &lt;C&gt; &quot;D&quot; &#39;E&#39; &nbsp;F &#xE9;")

        assertEquals("A & B <C> \"D\" 'E'  F \u00E9", decoded)
    }

    @Test
    fun `decodeHtmlEntities leaves text without ampersands untouched`() {
        assertEquals("plain text", HtmlDecoder.decodeHtmlEntities("plain text"))
    }

    @Test
    fun `parseSyncedSentences parses timestamps, agent and background tags`() {
        val lrc = """
            [00:01.50]Hello
            [00:02.00]{agent:v2}Second
            [00:03.00]{bg}Background
        """.trimIndent()

        val sentences = parseSyncedSentences(lrc)

        // The initial blank line keeps lyrics hidden before the first timestamp.
        assertEquals(4, sentences.size)
        assertEquals(0L, sentences[0].timeMs)
        assertEquals("", sentences[0].text)

        assertEquals(1500L, sentences[1].timeMs)
        assertEquals("Hello", sentences[1].text)
        assertNull(sentences[1].agent)
        assertFalse(sentences[1].isBackground)

        assertEquals(2000L, sentences[2].timeMs)
        assertEquals("Second", sentences[2].text)
        assertEquals("v2", sentences[2].agent)
        assertFalse(sentences[2].isBackground)

        assertEquals(3000L, sentences[3].timeMs)
        assertEquals("Background", sentences[3].text)
        assertTrue(sentences[3].isBackground)
    }

    @Test
    fun `parseSyncedSentences keeps millisecond precision without leaking the closing tag`() {
        val sentences = parseSyncedSentences("[0:01.234]First\n[01:02]Second\n[01:75.00]Invalid")

        assertEquals(
            listOf(SyncedSentence(0L, ""), SyncedSentence(1234L, "First"), SyncedSentence(62000L, "Second")),
            sentences
        )
    }

    @Test
    fun `computeSyncedGapWindows reports window for long blank-line gap`() {
        val sentences = listOf(
            SyncedSentence(0L, "somewhat long line text"),
            SyncedSentence(3000L, ""),
            SyncedSentence(8000L, "next line")
        )

        val windows = computeSyncedGapWindows(sentences)

        assertEquals(setOf(1), windows.keys)
        assertEquals(3000L, windows[1]?.first)
        assertEquals(7350L, windows[1]?.second)
    }

    @Test
    fun `computeSyncedGapWindows ignores short gaps`() {
        val sentences = listOf(
            SyncedSentence(0L, "text"),
            SyncedSentence(1500L, ""),
            SyncedSentence(2500L, "next")
        )

        assertTrue(computeSyncedGapWindows(sentences).isEmpty())
    }

    @Test
    fun `computeSyncedGapWindows ignores non-blank sentences`() {
        val sentences = listOf(
            SyncedSentence(0L, "one"),
            SyncedSentence(1000L, "two")
        )

        assertTrue(computeSyncedGapWindows(sentences).isEmpty())
    }
}
