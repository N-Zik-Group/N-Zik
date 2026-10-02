package app.n_zik.android.components.player.lyrics

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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

/**
 * The line the scroll index should point at.
 *
 * The furthest active MAIN (non-background) line wins — several agents can be
 * active at once and the center follows the sung main line. When only a chorus
 * (background) line is active the index switches to it, so the chorus advances
 * to the center exactly like a main line and retreats when the next main line
 * takes over. When no line is active (gap, before the first line) the last
 * passed non-background line is kept — the list stays put.
 *
 * @param lines all karaoke lines in order
 * @param activeIndices the indices from [computeActiveLineIndices]
 * @param currentPositionMs playback position in ms
 * @return the line index the scroll should target (0 for an empty list)
 */
fun primaryLyricsIndex(lines: List<KaraokeLine>, activeIndices: Set<Int>, currentPositionMs: Long): Int {
    if (lines.isEmpty()) return 0
    val nonBgActive = activeIndices.filter { lines[it].isBackground.not() }
    if (nonBgActive.isNotEmpty()) return nonBgActive.max()
    val bgActive = activeIndices.filter { lines[it].isBackground }
    if (bgActive.isNotEmpty()) return bgActive.max()
    var lastNonBg = 0
    for (i in lines.indices) {
        if (lines[i].timeMs > currentPositionMs) break
        if (!lines[i].isBackground) lastNonBg = i
    }
    return lastNonBg
}

/**
 * Item index of every lyric line inside the lyrics list layout.
 *
 * The list starts with the header spacer, then the optional initial gap loader,
 * then each lyric line item, each followed by its gap-loader item when the
 * line owns a gap window and the indicator is enabled (the loaders are
 * separate items, so the scroll index can switch to one). Returns the item
 * index of each line so the re-centering can target the right item even with
 * interleaved gap loaders.
 *
 * @param lineCount number of lyric lines
 * @param gapWindows the per-line gap windows from `computeKaraokeGapWindows`
 * @param showIntervalIndicator whether gap loaders are enabled
 * @param hasInitialLoader whether the initial gap loader item is present
 * @return the list-item index of each lyric line
 */
fun lyricsLineItemIndices(
    lineCount: Int,
    gapWindows: Map<Int, Pair<Long, Long>>,
    showIntervalIndicator: Boolean,
    hasInitialLoader: Boolean
): IntArray {
    val base = 1 + (if (showIntervalIndicator && hasInitialLoader) 1 else 0)
    val indices = IntArray(lineCount)
    var next = base
    for (i in 0 until lineCount) {
        indices[i] = next
        next++ // the line item itself
        if (showIntervalIndicator && gapWindows.containsKey(i)) next++ // its gap-loader item
    }
    return indices
}

/**
 * The line whose gap-loader window contains [currentPositionMs], or null when
 * no gap window is active.
 *
 * The gap loaders are separate list items (not part of the previous line's
 * item), so the scroll index can switch to one: while a gap runs and no line
 * is active, the re-centering targets that line's loader item instead of
 * staying on the previous line.
 *
 * @param gapWindows the per-line gap windows from `computeKaraokeGapWindows`
 * @param currentPositionMs playback position in ms
 * @return the line index owning the active gap window, or null
 */
fun activeGapLine(gapWindows: Map<Int, Pair<Long, Long>>, currentPositionMs: Long): Int? =
    gapWindows.entries.firstOrNull { currentPositionMs in it.value.first until it.value.second }?.key

/**
 * Target opacity of a karaoke line in the item `graphicsLayer`.
 *
 * Chorus/background lines are HIDDEN (0f) before their reveal, shown at 0.8f
 * while being sung or settling, and — once fully in the past state — fade
 * down to the same dimmed level as regular past lines (0.35f): the same
 * "fade to the background" the regular lines get once sung. The layout slot
 * is kept either way — the opacity hides/dims the text, it does not remove
 * the line.
 *
 * Regular (non-background) lines keep the existing behavior: fully opaque
 * (1f) when active, dimmed (0.35f) when idle.
 *
 * @param isBackground whether the line is a chorus/background line
 * @param isShown whether the line is currently revealed
 * @param isPastState whether the line is fully in its past state
 * @return the target opacity
 */
fun karaokeLineOpacity(isBackground: Boolean, isShown: Boolean, isPastState: Boolean): Float = when {
    isBackground -> when {
        !isShown -> 0f
        isPastState -> 0.35f
        else -> 0.8f
    }
    isShown -> 1f
    else -> 0.35f
}

/**
 * Whether [line] is fully in its past state.
 *
 * Lines with words are in the past state once their sung settling tail
 * ([KARAOKE_SUNG_FADE_MS] after the last word) is over. Lines without words
 * (no per-word settling exists) are in the past state from the next line's
 * start onwards — the end of the window [computeActiveLineIndices] keeps
 * them active.
 *
 * @param line the karaoke line
 * @param currentPositionMs playback position in ms
 * @param nextLineStartMs start time of the following line, or null when there
 * is none
 * @return true while the line is fully in its past state
 */
fun isKaraokeLineInPastState(line: KaraokeLine, currentPositionMs: Long, nextLineStartMs: Long?): Boolean {
    if (line.words.isNotEmpty()) {
        return currentPositionMs >= line.words.maxOf { it.endMs } + KARAOKE_SUNG_FADE_MS
    }
    return nextLineStartMs != null && currentPositionMs >= nextLineStartMs
}

/**
 * Target scale of a karaoke line in the item `graphicsLayer`.
 *
 * Only the sung (active) regular line gets the "pop" (scale up to 1.08); idle
 * regular lines stay at 1.0. Chorus lines NEVER scale — the pop/de-pop would
 * visibly shift their text (the scale transform is anchored at the content
 * center), so the chorus follows the interval loader behavior instead: height
 * collapse/expand plus opacity only, at a constant 0.85 scale.
 *
 * @param isBackground whether the line is a chorus/background line
 * @param isActive whether the line is currently in its sung window
 * @return the target scale
 */
fun karaokeLineScale(isBackground: Boolean, isActive: Boolean): Float = when {
    isBackground -> 0.85f
    isActive -> 1.08f
    else -> 1f
}

/**
 * How long (ms) a sung word keeps decaying after its end (the wave/rise fade of
 * long words in the karaoke branch). A line must stay in the animated karaoke
 * branch for this long after its last word ended, otherwise the in-progress
 * lift animation is cut off the moment the active line changes.
 */
const val KARAOKE_SUNG_FADE_MS = 1200L

/**
 * How long (ms) a chorus line starts appearing BEFORE its first word is sung.
 * The chorus does not pop in exactly at its line start; it fades/expands in a
 * few seconds before, so the reveal is gradual and anticipated.
 */
const val CHORUS_LEAD_IN_MS = 2000L

/**
 * Whether [line] should be shown (expanded slot, revealed) at [currentPositionMs].
 *
 * Regular lines are always shown. Chorus lines appear starting
 * [CHORUS_LEAD_IN_MS] before their first word — they reveal a few seconds
 * before being sung, instead of popping in exactly at the line start. Once
 * sung, a chorus STAYS shown: its slot is kept and it is rendered in the past
 * state (like a finished regular line) — it never de-pops back, so the canvas
 * is not shifted again after the reveal.
 *
 * @param line the karaoke line
 * @param currentPositionMs playback position in ms
 * @return true while the line should be revealed
 */
fun isChorusLineShown(line: KaraokeLine, currentPositionMs: Long): Boolean {
    if (!line.isBackground) return true
    val lineStartMs = line.words.firstOrNull()?.startMs ?: line.timeMs
    return currentPositionMs >= lineStartMs - CHORUS_LEAD_IN_MS
}

/**
 * Indices of the chorus (background) lines currently shown (expanded slot)
 * at [currentPositionMs].
 *
 * The view uses this list as a change detector: when an entry appears or
 * disappears, a chorus slot is popping in or de-popping out, and its 200ms
 * height animation shifts the active line's scroll position — the view
 * re-centers on the active line once the height animation has settled so
 * the sung line stays put (the canvas shift is absorbed by the scroll).
 *
 * @param lines all karaoke lines in order
 * @param currentPositionMs playback position in ms
 * @return the indices of the currently shown chorus lines (empty when none)
 */
fun chorusShownIndices(lines: List<KaraokeLine>, currentPositionMs: Long): List<Int> =
    lines.indices.filter { i -> lines[i].isBackground && isChorusLineShown(lines[i], currentPositionMs) }

/**
 * Target height fraction (0..1) of a karaoke line's item slot.
 *
 * Chorus lines collapse their slot to zero height while not sung and expand it
 * back to full height while sung — the same show/hide behavior as the interval
 * loader (gap removed when hidden, place taken back when shown). Regular lines
 * always keep their full slot.
 *
 * @param isBackground whether the line is a chorus/background line
 * @param isActive whether the line is currently in its sung window
 * @return the target height fraction
 */
fun karaokeLineHeightFraction(isBackground: Boolean, isActive: Boolean): Float =
    if (isBackground) (if (isActive) 1f else 0f) else 1f

/**
 * Whether [line] must keep rendering in the animated karaoke branch even though
 * it is no longer the active line, because its sung fade (wave/rise + per-word
 * color settle) is still running. The fade runs [KARAOKE_SUNG_FADE_MS] after
 * the line's last word ended; staying in the animated branch until then lets
 * the settling finish in the background instead of being cut off on the
 * switch — without it, the last word would pop from full accent to the dimmed
 * past-line color the moment the branch flips. Chorus lines qualify too: once
 * sung they stay revealed in the past state, so their last word must finish
 * settling before the branch flips to the static text as well.
 *
 * @param line the karaoke line
 * @param currentPositionMs playback position in ms
 * @return true while the line is inside its sung-fade tail (deactivated, but the
 * fade has not completed yet)
 */
fun isKaraokeLineStillAnimating(line: KaraokeLine, currentPositionMs: Long): Boolean {
    if (line.words.isEmpty()) return false
    val lineEndMs = line.words.maxOf { it.endMs }
    return currentPositionMs in (lineEndMs + 1L)..(lineEndMs + KARAOKE_SUNG_FADE_MS)
}

/**
 * How long (ms) a sung word's color takes to settle from the full accent to
 * the dimmed past-line accent after the word ended.
 *
 * Much shorter than the motion decay ([KARAOKE_SUNG_FADE_MS]): the color must
 * settle quickly so the line stays visually uniform — a slow per-word color
 * fade shows up as a brightness gradient across the words (some words
 * brighter than others), both during playback and in the background.
 */
const val KARAOKE_SUNG_COLOR_SETTLE_MS = 300L

/**
 * Multiplier of the text size used as the `lineHeight` of every karaoke line,
 * in every branch (animated and static alike).
 *
 * The animated branch and the static branch must lay a line out with exactly
 * the same height: a line enters the animated branch when it is sung and
 * leaves it after the sung-fade tail. If the two branches used different line
 * heights, the LazyColumn item would resize on each branch switch and the
 * whole list would visibly jump (micro-jump) — on both the activation and
 * the leave. One shared multiplier keeps the layout bit-identical across
 * the switches, so the leave animation runs while the settle effects finish
 * without any layout shift.
 */
const val KARAOKE_LINE_HEIGHT_MULTIPLIER = 1.4f

/**
 * Color of a sung karaoke word while it settles after its end.
 *
 * Every word (short and long alike) fades per-word from the full accent color
 * to the dimmed past-line accent the static branch uses (0.5 alpha) over
 * [KARAOKE_SUNG_COLOR_SETTLE_MS] after the word ended, with the same smoothstep
 * easing as the long-word motion decay. Settling per-word — instead of when
 * the line deactivates — keeps the background free of pops: a word sung early
 * in the line is already dimmed while the line is still active, and once the
 * fade tail is over every word sits exactly on the static branch color, so
 * the branch switch is seamless.
 *
 * @param accent the line accent color at full intensity
 * @param wordEndMs end time of the word, in ms
 * @param currentPositionMs playback position in ms
 * @return the word color at [currentPositionMs]: [accent] just after the word
 * ended, `accent.copy(alpha = 0.5f)` once the settle is done
 */
fun karaokeSungWordSettleColor(accent: Color, wordEndMs: Long, currentPositionMs: Long): Color {
    val fadeProgress = ((currentPositionMs - wordEndMs).toFloat() / KARAOKE_SUNG_COLOR_SETTLE_MS).coerceIn(0f, 1f)
    val settleFactor = 1f - (fadeProgress * fadeProgress * (3f - 2f * fadeProgress))
    return accent.copy(alpha = 0.5f + 0.5f * settleFactor)
}
