package app.n_zik.android.components.player.lyrics

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KaraokeFillSupportTest {

    private val delta = 1e-3f

    @Test
    fun `a single line mid progress produces the legacy rect`() {
        // Legacy formula: fillRight = startBox.left + (endBox.right - startBox.left) * easedProgress
        // rect = (left - pad, top - wave - rise - pad, fillRight + pad, bottom + wave + pad)
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(10),
            lineLefts = floatArrayOf(100f),
            lineRights = floatArrayOf(300f),
            lineTops = floatArrayOf(50f),
            lineBottoms = floatArrayOf(80f),
            progress = 0.4f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        assertEquals(1, rects.size)
        val rect = rects[0]
        assertTrue(rect.revealed)
        assertEquals(100f - 24f, rect.left, delta)
        assertEquals(50f - 30f - 24f, rect.top, delta)
        assertEquals(100f + (300f - 100f) * 0.4f + 24f, rect.right, delta)
        assertEquals(80f + 20f + 24f, rect.bottom, delta)
    }

    @Test
    fun `zero progress reveals no line`() {
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(4, 3),
            lineLefts = floatArrayOf(50f, 70f),
            lineRights = floatArrayOf(250f, 230f),
            lineTops = floatArrayOf(10f, 60f),
            lineBottoms = floatArrayOf(40f, 90f),
            progress = 0f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        assertTrue(rects.none { it.revealed })
    }

    @Test
    fun `full progress reveals the whole line`() {
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(10),
            lineLefts = floatArrayOf(100f),
            lineRights = floatArrayOf(300f),
            lineTops = floatArrayOf(50f),
            lineBottoms = floatArrayOf(80f),
            progress = 1f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        val rect = rects.single()
        assertTrue(rect.revealed)
        assertEquals(100f - 24f, rect.left, delta)
        assertEquals(300f + 24f, rect.right, delta)
    }

    @Test
    fun `a wrapped word fills line 1 before touching line 2`() {
        // 7 chars total, frontier at char 2.5 -> progress = 2.5 / 7
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(4, 3),
            lineLefts = floatArrayOf(50f, 70f),
            lineRights = floatArrayOf(250f, 230f),
            lineTops = floatArrayOf(10f, 60f),
            lineBottoms = floatArrayOf(40f, 90f),
            progress = 2.5f / 7f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        val (first, second) = rects
        assertTrue(first.revealed)
        // lineProgress = 2.5 / 4
        assertEquals(50f + (250f - 50f) * 0.625f + 24f, first.right, delta)
        assertEquals(10f - 30f - 24f, first.top, delta)
        assertEquals(40f + 20f + 24f, first.bottom, delta)
        // line 2 must stay hidden while the sweep is still on line 1
        assertTrue(!second.revealed)
    }

    @Test
    fun `a wrapped word fills line 2 only after line 1 is complete`() {
        // frontier at char 5.5 -> progress = 5.5 / 7
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(4, 3),
            lineLefts = floatArrayOf(50f, 70f),
            lineRights = floatArrayOf(250f, 230f),
            lineTops = floatArrayOf(10f, 60f),
            lineBottoms = floatArrayOf(40f, 90f),
            progress = 5.5f / 7f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        val (first, second) = rects
        // line 1 fully revealed up to its own right bound
        assertTrue(first.revealed)
        assertEquals(250f + 24f, first.right, delta)
        // line 2 partially revealed: lineProgress = (5.5 - 4) / 3 = 0.5
        assertTrue(second.revealed)
        assertEquals(70f + (230f - 70f) * 0.5f + 24f, second.right, delta)
        assertEquals(60f - 30f - 24f, second.top, delta)
        assertEquals(90f + 20f + 24f, second.bottom, delta)
    }

    @Test
    fun `full progress on a wrapped word reveals every line`() {
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(3, 3, 3),
            lineLefts = floatArrayOf(40f, 60f, 20f),
            lineRights = floatArrayOf(200f, 220f, 180f),
            lineTops = floatArrayOf(10f, 50f, 90f),
            lineBottoms = floatArrayOf(40f, 80f, 120f),
            progress = 1f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        val rights = rects.map { it.right }
        assertEquals(200f + 24f, rights[0], delta)
        assertEquals(220f + 24f, rights[1], delta)
        assertEquals(180f + 24f, rights[2], delta)
        assertTrue(rects.all { it.revealed })
    }

    @Test
    fun `a right aligned line uses its own bounds`() {
        // line 2 shifted to the right: the sweep within it starts at its own left bound
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(4, 3),
            lineLefts = floatArrayOf(50f, 150f),
            lineRights = floatArrayOf(250f, 250f),
            lineTops = floatArrayOf(10f, 60f),
            lineBottoms = floatArrayOf(40f, 90f),
            progress = 5.5f / 7f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        val second = rects[1]
        assertTrue(second.revealed)
        assertEquals(150f - 24f, second.left, delta)
        // lineProgress = (5.5 - 4) / 3 = 0.5 from its own left bound
        assertEquals(150f + (250f - 150f) * 0.5f + 24f, second.right, delta)
    }

    @Test
    fun `a line with zero in range characters stays hidden`() {
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(0, 5),
            lineLefts = floatArrayOf(50f, 70f),
            lineRights = floatArrayOf(250f, 230f),
            lineTops = floatArrayOf(10f, 60f),
            lineBottoms = floatArrayOf(40f, 90f),
            progress = 0.4f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        assertTrue(!rects[0].revealed)
        assertTrue(rects[1].revealed)
        // the whole progress maps onto the only non-empty line
        assertEquals(70f + (230f - 70f) * 0.4f + 24f, rects[1].right, delta)
    }

    @Test
    fun `an empty range produces no rects`() {
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(),
            lineLefts = floatArrayOf(),
            lineRights = floatArrayOf(),
            lineTops = floatArrayOf(),
            lineBottoms = floatArrayOf(),
            progress = 0.5f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        assertTrue(rects.isEmpty())
    }

    @Test
    fun `a frontier exactly on a line boundary completes the line without starting the next`() {
        // 7 chars, frontier at char 4 = exactly the end of line 1 -> progress = 4 / 7
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(4, 3),
            lineLefts = floatArrayOf(50f, 70f),
            lineRights = floatArrayOf(250f, 230f),
            lineTops = floatArrayOf(10f, 60f),
            lineBottoms = floatArrayOf(40f, 90f),
            progress = 4f / 7f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        val (first, second) = rects
        assertTrue(first.revealed)
        assertEquals(250f + 24f, first.right, delta)
        // line 2 must stay hidden at the exact boundary
        assertFalse(second.revealed)
    }

    @Test
    fun `progress outside the zero to one range is clamped`() {
        val below = computeActiveFillRects(
            lineCharCounts = intArrayOf(4, 3),
            lineLefts = floatArrayOf(50f, 70f),
            lineRights = floatArrayOf(250f, 230f),
            lineTops = floatArrayOf(10f, 60f),
            lineBottoms = floatArrayOf(40f, 90f),
            progress = -0.5f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )
        assertTrue(below.none { it.revealed })

        val above = computeActiveFillRects(
            lineCharCounts = intArrayOf(4, 3),
            lineLefts = floatArrayOf(50f, 70f),
            lineRights = floatArrayOf(250f, 230f),
            lineTops = floatArrayOf(10f, 60f),
            lineBottoms = floatArrayOf(40f, 90f),
            progress = 1.5f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )
        assertTrue(above.all { it.revealed })
        assertEquals(250f + 24f, above[0].right, delta)
        assertEquals(230f + 24f, above[1].right, delta)
    }

    @Test
    fun `a three line wrap fills the middle line after the first line is complete`() {
        // 9 chars, frontier at char 4.5 -> progress = 4.5 / 9 = 0.5 exactly
        val rects = computeActiveFillRects(
            lineCharCounts = intArrayOf(3, 3, 3),
            lineLefts = floatArrayOf(40f, 60f, 20f),
            lineRights = floatArrayOf(200f, 220f, 180f),
            lineTops = floatArrayOf(10f, 50f, 90f),
            lineBottoms = floatArrayOf(40f, 80f, 120f),
            progress = 0.5f,
            topHeadroom = 30f,
            bottomHeadroom = 20f,
            padding = 24f
        )

        val (first, second, third) = rects
        assertTrue(first.revealed)
        assertEquals(200f + 24f, first.right, delta)
        // middle line partially revealed: lineProgress = (4.5 - 3) / 3 = 0.5
        assertTrue(second.revealed)
        assertEquals(60f + (220f - 60f) * 0.5f + 24f, second.right, delta)
        assertFalse(third.revealed)
    }

    @Test
    fun `the geometry collects per-line bounds in reading order`() {
        // a CJK-like wrap: 7 characters split 4 + 3 across two physical lines
        val layout = fakeTwoLineLayout()

        val geometry = collectActiveFillGeometry(layout, startIdx = 0, endIdx = 6)

        assertEquals(2, geometry.lineCount)
        assertArrayEquals(intArrayOf(4, 3), geometry.lineCharCounts)
        // line 0: first char box (100, 50, 120, 80), last char box (160, 50, 180, 80)
        assertEquals(100f, geometry.lineLefts[0], delta)
        assertEquals(180f, geometry.lineRights[0], delta)
        assertEquals(50f, geometry.lineTops[0], delta)
        assertEquals(80f, geometry.lineBottoms[0], delta)
        // line 1: first char box (120, 100, 140, 130), last char box (160, 100, 180, 130)
        assertEquals(120f, geometry.lineLefts[1], delta)
        assertEquals(180f, geometry.lineRights[1], delta)
        assertEquals(100f, geometry.lineTops[1], delta)
        assertEquals(130f, geometry.lineBottoms[1], delta)
    }

    @Test
    fun `the geometry clamps the word range to the per-line intersection`() {
        val layout = fakeTwoLineLayout()

        // word spans chars 1..5: line 0 keeps chars 1..3, line 1 keeps chars 4..5
        val geometry = collectActiveFillGeometry(layout, startIdx = 1, endIdx = 5)

        assertArrayEquals(intArrayOf(3, 2), geometry.lineCharCounts)
        // line 0: char 1 box left (120) to char 3 box right (160 + 20 = 180)
        assertEquals(120f, geometry.lineLefts[0], delta)
        assertEquals(180f, geometry.lineRights[0], delta)
        // line 1: char 4 box left (120) to char 5 box right (140 + 20 = 160)
        assertEquals(120f, geometry.lineLefts[1], delta)
        assertEquals(160f, geometry.lineRights[1], delta)
    }

    @Test
    fun `a single character word produces one entry for its own line only`() {
        val layout = fakeTwoLineLayout()

        // word is char 4, on line 1 only — the range crosses no earlier line,
        // so line 0 is not in the geometry at all (one entry per crossed line)
        val geometry = collectActiveFillGeometry(layout, startIdx = 4, endIdx = 4)

        assertEquals(1, geometry.lineCount)
        assertArrayEquals(intArrayOf(1), geometry.lineCharCounts)
        // the single entry holds the word character's own box (char 4: x 120..140, y 100..130)
        assertEquals(120f, geometry.lineLefts[0], delta)
        assertEquals(140f, geometry.lineRights[0], delta)
        assertEquals(100f, geometry.lineTops[0], delta)
        assertEquals(130f, geometry.lineBottoms[0], delta)
    }

    @Test
    fun `line char ranges map a wrapped word onto its physical lines`() {
        // 7 chars, 4 on line 0 and 3 on line 1: word-relative offsets per line
        val layout = fakeTwoLineLayout()

        val ranges = activeWordLineCharRanges(layout, startIdx = 0, endIdx = 6)

        assertEquals(listOf(0..3, 4..6), ranges)
    }

    @Test
    fun `line char ranges clamp a partial word to the per-line intersection`() {
        val layout = fakeTwoLineLayout()

        // word spans chars 1..5: line 0 keeps layout chars 1..3, line 1 keeps 4..5
        val ranges = activeWordLineCharRanges(layout, startIdx = 1, endIdx = 5)

        assertEquals(listOf(0..2, 3..4), ranges)
    }

    @Test
    fun `line char ranges keep a single entry for a single line word`() {
        val layout = fakeTwoLineLayout()

        // word is char 4, on line 1 only: one entry, word-relative offset 0
        val ranges = activeWordLineCharRanges(layout, startIdx = 4, endIdx = 4)

        assertEquals(1, ranges.size)
        assertEquals(0..0, ranges[0])
    }

    @Test
    fun `line gate rect strips the horizontal pad but keeps the vertical headroom`() {
        val lineRect = FillLineRect(left = 12f, top = 24f, right = 112f, bottom = 84f, revealed = true)

        val gate = activeLineGateRect(lineRect, horizontalPadding = 8f)

        assertEquals(20f, gate.left, delta)
        assertEquals(24f, gate.top, delta)
        assertEquals(104f, gate.right, delta)
        assertEquals(84f, gate.bottom, delta)
    }

    @Test
    fun `line gate rect starts closed at the line frontier`() {
        // a line just revealed: fillRight == lineLeft, so the padded rect is only
        // 2*pad wide and the gate collapses to zero width — nothing is revealed
        // on the first frame (no half-open flash on a short final line)
        val lineRect = FillLineRect(left = 50f - 8f, top = 0f, right = 50f + 8f, bottom = 12f, revealed = true)

        val gate = activeLineGateRect(lineRect, horizontalPadding = 8f)

        assertTrue(gate.right - gate.left <= 0f)
    }

    @Test
    fun `no line stays active in the gap after a line finished`() {
        val lines = listOf(
            KaraokeLine(timeMs = 0L, text = "A", words = listOf(KaraokeWord("A", 0L, 1000L))),
            KaraokeLine(timeMs = 5000L, text = "B", words = listOf(KaraokeWord("B", 5000L, 6000L)))
        )

        // 2s after line 0's last word ended, before line 1 starts: nothing is lit
        val active = computeActiveLineIndices(lines, currentPositionMs = 3000L)

        assertTrue(active.isEmpty())
    }

    @Test
    fun `no line re-selected after a background line finished`() {
        val lines = listOf(
            KaraokeLine(timeMs = 0L, text = "A", words = listOf(KaraokeWord("A", 0L, 1000L))),
            KaraokeLine(timeMs = 2000L, text = "(chorus)", words = listOf(KaraokeWord("ch", 2000L, 3000L)), isBackground = true),
            KaraokeLine(timeMs = 6000L, text = "B", words = listOf(KaraokeWord("B", 6000L, 7000L)))
        )

        // the chorus ended: the gap before the next line must be empty — the
        // previous non-background line must NOT snap back to selected
        val active = computeActiveLineIndices(lines, currentPositionMs = 4000L)

        assertTrue(active.isEmpty())
    }

    @Test
    fun `line is active within its word window with the 50ms early window`() {
        val lines = listOf(
            KaraokeLine(timeMs = 1000L, text = "A", words = listOf(KaraokeWord("A", 1000L, 2000L)))
        )

        assertTrue(0 in computeActiveLineIndices(lines, currentPositionMs = 950L)) // 50ms early window
        assertTrue(0 in computeActiveLineIndices(lines, currentPositionMs = 1500L)) // mid window
        assertTrue(computeActiveLineIndices(lines, currentPositionMs = 2001L).isEmpty()) // just after the end
    }

    @Test
    fun `wordless line stays active until the next line starts`() {
        val lines = listOf(
            KaraokeLine(timeMs = 0L, text = "A", words = listOf(KaraokeWord("A", 0L, 1000L))),
            KaraokeLine(timeMs = 3000L, text = "   ", words = emptyList()),
            KaraokeLine(timeMs = 6000L, text = "B", words = listOf(KaraokeWord("B", 6000L, 7000L)))
        )

        // the blank line's window is [2950, 6000]: only it is active until the
        // next line's 50ms early window opens
        assertEquals(setOf(1), computeActiveLineIndices(lines, currentPositionMs = 4000L))
        assertEquals(setOf(1), computeActiveLineIndices(lines, currentPositionMs = 5940L))
        assertEquals(setOf(1, 2), computeActiveLineIndices(lines, currentPositionMs = 5950L))
    }

    /**
     * Two physical lines: chars 0..3 on line 0, chars 4..6 on line 1.
     * Each char is a 20px-wide box; line 0 sits at y 50..80, line 1 at y 100..130,
     * line 1 shifted 20px to the right.
     */
    private fun fakeTwoLineLayout(): ActiveFillLayout {
        fun charBox(charIndex: Int): Rect {
            val onLine0 = charIndex < 4
            val x = if (onLine0) 100 + charIndex * 20 else 120 + (charIndex - 4) * 20
            val y = if (onLine0) 50 else 100
            return Rect(x.toFloat(), y.toFloat(), (x + 20).toFloat(), (y + 30).toFloat())
        }

        return object : ActiveFillLayout {
            override fun getLineForOffset(offset: Int): Int = if (offset < 4) 0 else 1

            override fun getLineStart(lineIndex: Int): Int = if (lineIndex == 0) 0 else 4

            override fun getLineEnd(lineIndex: Int, visibleEnd: Boolean): Int = if (lineIndex == 0) 4 else 7

            override fun getBoundingBox(offset: Int): Rect = charBox(offset)
        }
    }
}
