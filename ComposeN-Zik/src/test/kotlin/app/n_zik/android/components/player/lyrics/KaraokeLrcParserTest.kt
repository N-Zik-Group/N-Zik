package app.n_zik.android.components.player.lyrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KaraokeLrcParserTest {

    @Test
    fun `parseKaraokeLrc parses lines, word timings, agent and background tags`() {
        val lrc = """
            [00:01.00]{agent:v1}Hello world
            <Hello:1.0:1.5|world:1.5:2.0>
            [00:02.50]{bg}oh oh
            <oh:2.5:2.9|oh:2.9:3.3>
        """.trimIndent()

        val lines = parseKaraokeLrc(lrc)

        assertEquals(2, lines.size)

        val first = lines[0]
        assertEquals(1000L, first.timeMs)
        assertEquals("Hello world", first.text)
        assertEquals("v1", first.agent)
        assertFalse(first.isBackground)
        assertEquals(2, first.words.size)
        assertEquals("Hello", first.words[0].text)
        assertEquals(1000L, first.words[0].startMs)
        assertEquals(1500L, first.words[0].endMs)
        assertEquals(0, first.words[0].charStartIndex)
        assertEquals("world", first.words[1].text)
        assertEquals(6, first.words[1].charStartIndex)

        val second = lines[1]
        assertEquals(2500L, second.timeMs)
        assertEquals("oh oh", second.text)
        assertNull(second.agent)
        assertTrue(second.isBackground)
        assertEquals(2900L, second.words[1].startMs)
    }

    @Test
    fun `parseKaraokeLrc returns empty list for text without timestamps`() {
        assertTrue(parseKaraokeLrc("just some plain text without any tags").isEmpty())
    }

    @Test
    fun `computeKaraokeGapWindows reports initial gap when first line starts after 3 seconds`() {
        val lines = listOf(KaraokeLine(5000L, "a", listOf(KaraokeWord("a", 5000L, 6000L))))

        val (initialGap, gapWindows) = computeKaraokeGapWindows(lines)

        assertEquals(0L, initialGap?.first)
        assertEquals(4350L, initialGap?.second)
        // The last line always gets a synthetic trailing window (nextStartMs falls back to
        // startMs + 10000L when there's no following line) — pre-existing behavior, unchanged

        // by the #606 threading migration (same formula as the original inline implementation).
        assertEquals(setOf(0), gapWindows.keys)
        assertEquals(6000L, gapWindows[0]?.first)
        assertEquals(14350L, gapWindows[0]?.second)
    }

    @Test
    fun `computeKaraokeGapWindows detects gaps longer than 2500 ms between lines`() {
        val lines = listOf(
            KaraokeLine(0L, "first", listOf(KaraokeWord("first", 0L, 2000L))),
            KaraokeLine(6000L, "second", listOf(KaraokeWord("second", 6000L, 7000L)))
        )

        val (initialGap, gapWindows) = computeKaraokeGapWindows(lines)

        assertNull(initialGap)
        // index 0: real gap to the next line (6000 - 2000 = 4000ms). index 1: synthetic trailing
        // window for the last line (same pre-existing fallback as above).
        assertEquals(setOf(0, 1), gapWindows.keys)
        assertEquals(2000L, gapWindows[0]?.first)
        assertEquals(5350L, gapWindows[0]?.second)
        assertEquals(7000L, gapWindows[1]?.first)
        assertEquals(15350L, gapWindows[1]?.second)
    }

    @Test
    fun `computeKaraokeGapWindows removes overlapping windows keeping the later one`() {
        val lines = listOf(
            KaraokeLine(0L, "a", emptyList()),
            KaraokeLine(6000L, "b", listOf(KaraokeWord("b", 6000L, 1000L))),
            KaraokeLine(20000L, "c", emptyList())
        )

        val (_, gapWindows) = computeKaraokeGapWindows(lines)

        // index 0's window (2000..5350) overlaps index 1's (1000..19350) and is dropped, keeping
        // the later one. index 2 (last line) keeps its own synthetic trailing window, which does

        // not overlap index 1's.
        assertEquals(setOf(1, 2), gapWindows.keys)
        assertEquals(1000L, gapWindows[1]?.first)
        assertEquals(19350L, gapWindows[1]?.second)
        assertEquals(22000L, gapWindows[2]?.first)
        assertEquals(29350L, gapWindows[2]?.second)
    }

    @Test
    fun `computeKaraokeGapWindows skips background lines`() {
        val lines = listOf(
            KaraokeLine(0L, "bg hum", emptyList(), isBackground = true),
            KaraokeLine(4000L, "vocal", emptyList())
        )

        val (initialGap, gapWindows) = computeKaraokeGapWindows(lines)

        assertEquals(0L, initialGap?.first)
        assertEquals(3350L, initialGap?.second)
        assertEquals(setOf(1), gapWindows.keys)
    }
}
