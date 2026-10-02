package app.n_zik.android.components.player.lyrics

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult

/**
 * Preference key for the karaoke clip-rects debug overlay
 * (red = fill envelope, green = per-character clip, blue = text layout bounds).
 */
const val karaokeClipRectsDebugKey = "karaokeClipRectsDebug"

/**
 * Minimal layout access used to collect the per-physical-line geometry of an active karaoke word.
 *
 * Production code adapts [TextLayoutResult] to this interface; tests fake it from static
 * tables. Keeping the collection logic off [TextLayoutResult] (a final class) makes the
 * reading-order mapping directly testable on the JVM.
 */
interface ActiveFillLayout {
    /** The 0-origin line containing [offset]. */
    fun getLineForOffset(offset: Int): Int

    /** The inclusive start offset of the line at [lineIndex]. */
    fun getLineStart(lineIndex: Int): Int

    /** The exclusive end offset of the line at [lineIndex]. */
    fun getLineEnd(lineIndex: Int, visibleEnd: Boolean): Int

    /** The bounding box of the character at [offset]. */
    fun getBoundingBox(offset: Int): Rect
}

/**
 * Per-physical-line geometry of a word range within a text layout.
 *
 * All arrays hold one entry per physical line, in reading order.
 */
data class ActiveFillLineGeometry(
    val lineCharCounts: IntArray,
    val lineLefts: FloatArray,
    val lineRights: FloatArray,
    val lineTops: FloatArray,
    val lineBottoms: FloatArray
) {
    /** The number of physical lines crossed by the word range. */
    val lineCount: Int get() = lineCharCounts.size
}

/**
 * Collects the [ActiveFillLineGeometry] of the inclusive character range [startIdx, endIdx]:
 * for each physical line the range crosses, the in-range character count plus the bounds of
 * the first and last in-range character of the line (left / right / top / bottom).
 *
 * @param layout the layout to read the line structure and character boxes from
 * @param startIdx inclusive first character index of the word
 * @param endIdx inclusive last character index of the word
 * @return one entry per physical line, in reading order
 */
fun collectActiveFillGeometry(layout: ActiveFillLayout, startIdx: Int, endIdx: Int): ActiveFillLineGeometry {
    val firstLine = layout.getLineForOffset(startIdx)
    val lastLine = layout.getLineForOffset(endIdx)
    val lineCount = lastLine - firstLine + 1
    val lineCharCounts = IntArray(lineCount)
    val lineLefts = FloatArray(lineCount)
    val lineRights = FloatArray(lineCount)
    val lineTops = FloatArray(lineCount)
    val lineBottoms = FloatArray(lineCount)

    for (physLine in firstLine..lastLine) {
        val inRangeStart = maxOf(layout.getLineStart(physLine), startIdx)
        val inRangeEnd = minOf(layout.getLineEnd(physLine, visibleEnd = true) - 1, endIdx)
        val idx = physLine - firstLine
        if (inRangeStart <= inRangeEnd) {
            lineCharCounts[idx] = inRangeEnd - inRangeStart + 1
            val firstBox = layout.getBoundingBox(inRangeStart)
            val lastBox = layout.getBoundingBox(inRangeEnd)
            lineLefts[idx] = firstBox.left
            lineRights[idx] = lastBox.right
            lineTops[idx] = firstBox.top
            lineBottoms[idx] = lastBox.bottom
        }
    }

    return ActiveFillLineGeometry(lineCharCounts, lineLefts, lineRights, lineTops, lineBottoms)
}

/**
 * Clip rect of the active-word fill envelope on one physical line.
 *
 * The fields are only meaningful while [revealed] is true — unrevealed rects (lines the sweep
 * has not reached yet, or lines with zero in-range characters) carry undefined coordinates.
 *
 * @param left left edge of the line content minus padding (px)
 * @param top top edge of the line content minus headroom and padding (px)
 * @param right current sweep position within the line, plus padding (px)
 * @param bottom bottom edge of the line content plus headroom and padding (px)
 * @param revealed whether the sweep has reached this line (only revealed rects are clipped)
 */
data class FillLineRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val revealed: Boolean
)

/**
 * Computes the per-physical-line clip rects of the karaoke active-word fill sweep.
 *
 * The legacy envelope was a single rect from the first character box to the last
 * character box of the word. That is only correct while the word stays on ONE physical
 * line: when the word wraps — typically a CJK line merged into a single word because
 * `mergeSpansIntoWords` has no word boundaries without spaces — the rect spans every
 * wrapped line and `drawContent()` reveals them all at once (GH #820).
 *
 * This function maps the eased sweep progress onto the reading order: progress becomes
 * a character position, and each physical line is swept left-to-right with the same
 * in-line formula as the legacy single-line rect, so for `progress > 0f` the single-line
 * result is exactly the legacy rect (non-regression by construction) and wrapped lines
 * fill sequentially (line 1 fully, then line 2 partially, ...). At `progress == 0f` every
 * line is unrevealed, so the effective clip is empty — the legacy 16dp start sliver that
 * the old padding produced at zero progress is intentionally gone (nothing sung, nothing
 * revealed; one frame).
 *
 * All five arrays must have the same size — one entry per physical line, in reading order.
 *
 * @param lineCharCounts number of in-range characters of the word on each physical line,
 * in reading order
 * @param lineLefts leftmost x of the first in-range character of the line (px)
 * @param lineRights rightmost x of the last in-range character of the line (px)
 * @param lineTops topmost y of the first in-range character of the line (px)
 * @param lineBottoms bottommost y of the last in-range character of the line (px)
 * @param progress eased sweep progress, 0..1
 * @param topHeadroom headroom above the line content, in px (wave amplitude + rise)
 * @param bottomHeadroom headroom below the line content, in px (wave amplitude)
 * @param padding constant padding, in px (8dp in the view)
 * @return one rect per physical line in reading order; only rects with
 * [FillLineRect.revealed] == true must be clipped
 */
fun computeActiveFillRects(
    lineCharCounts: IntArray,
    lineLefts: FloatArray,
    lineRights: FloatArray,
    lineTops: FloatArray,
    lineBottoms: FloatArray,
    progress: Float,
    topHeadroom: Float,
    bottomHeadroom: Float,
    padding: Float
): List<FillLineRect> {
    val totalChars = lineCharCounts.sum()
    if (totalChars == 0) return emptyList()

    val progressChars = progress.coerceIn(0f, 1f) * totalChars

    var charOffset = 0
    return lineCharCounts.indices.map { i ->
        val count = lineCharCounts[i]
        val start = charOffset
        charOffset += count
        val end = charOffset

        if (count == 0 || progressChars <= start) {
            FillLineRect(lineLefts[i], lineTops[i], lineLefts[i], lineBottoms[i], revealed = false)
        } else if (progressChars >= end) {
            FillLineRect(
                left = lineLefts[i] - padding,
                top = lineTops[i] - topHeadroom - padding,
                right = lineRights[i] + padding,
                bottom = lineBottoms[i] + bottomHeadroom + padding,
                revealed = true
            )
        } else {
            val lineProgress = ((progressChars - start) / count).coerceIn(0f, 1f)
            val fillRight = lineLefts[i] + (lineRights[i] - lineLefts[i]) * lineProgress
            FillLineRect(
                left = lineLefts[i] - padding,
                top = lineTops[i] - topHeadroom - padding,
                right = fillRight + padding,
                bottom = lineBottoms[i] + bottomHeadroom + padding,
                revealed = true
            )
        }
    }
}

/**
 * Per-physical-line character ranges of an active word, in reading order.
 *
 * Returns one [IntRange] per physical line crossed by the inclusive range [startIdx]..[endIdx];
 * offsets are relative to [startIdx], so `startIdx + charIdx` is the layout offset of the
 * character (matching `word.charStartIndex + charIdx` in the view). A line holding no
 * in-range character carries an empty range.
 *
 * Used to gate the per-line reveal of a wrapped active word (GH #820 residual): the per-line
 * fill rects overlap vertically (headroom exceeds the CJK line spacing), so the union
 * envelope alone lights up a sliver of the next line's characters as soon as the previous
 * line is fully filled. Each line's characters must be drawn within that line's own rect.
 */
fun activeWordLineCharRanges(layout: ActiveFillLayout, startIdx: Int, endIdx: Int): List<IntRange> {
    val firstLine = layout.getLineForOffset(startIdx)
    val lastLine = layout.getLineForOffset(endIdx)
    return (firstLine..lastLine).map { physLine ->
        val from = maxOf(layout.getLineStart(physLine), startIdx) - startIdx
        val to = minOf(layout.getLineEnd(physLine, visibleEnd = true) - 1, endIdx) - startIdx
        from..to
    }
}

/**
 * Per-line reveal gate for an active line's characters: the line's fill rect with its
 * horizontal padding stripped.
 *
 * The fill rect's horizontal padding exists so the union envelope covers chars in motion
 * near the sweep frontier. If the gate reused those padded edges, a short final physical
 * line (often a single CJK char on a wrapped line) would open already half-revealed the
 * moment the previous line closes — the visible flash at the end of a wrapped active word.
 * Insetting the gate back to the true frontier keeps the reveal strictly progressive.
 * Vertical edges keep the full headroom so the wave/rise motion is never clipped.
 *
 * @param lineRect the fill rect of the line (must be [FillLineRect.revealed])
 * @param horizontalPadding the pad that [computeActiveFillRects] added on left/right (px)
 * @return the clip rect for the line's characters
 */
fun activeLineGateRect(lineRect: FillLineRect, horizontalPadding: Float): Rect =
    Rect(
        left = lineRect.left + horizontalPadding,
        top = lineRect.top,
        right = lineRect.right - horizontalPadding,
        bottom = lineRect.bottom
    )

/**
 * Indices of the karaoke lines whose time window contains [currentPositionMs].
 *
 * A line is active while the position lies within `[timeMs - 50ms, lineEndMs]`, where
 * lineEndMs is the latest word endMs, or — for a line without words — the following
 * line's start time. When the position falls in a gap (a finished line, a blank line,
 * a gap/loader window, or after a background line ended), NO line is active: nothing
 * stays selected. In particular the previous line must not be force-selected and kept
 * fully lit until the next line starts — that fallback showed up as a flash at the
 * closing of a wrapped line and as the selection snapping back to the last line after
 * a chorus line ended.
 *
 * @param lines all karaoke lines in order
 * @param currentPositionMs playback position in ms
 * @return the active line indices (empty inside gaps)
 */
fun computeActiveLineIndices(lines: List<KaraokeLine>, currentPositionMs: Long): Set<Int> {
    val active = mutableSetOf<Int>()
    for (i in lines.indices) {
        val line = lines[i]
        if (line.timeMs > currentPositionMs + 50L) break
        val lineEndMs = if (line.words.isNotEmpty()) {
            line.words.maxOf { it.endMs }
        } else {
            lines.getOrNull(i + 1)?.timeMs ?: Long.MAX_VALUE
        }
        if (currentPositionMs <= lineEndMs) {
            active.add(i)
        }
    }
    return active
}
