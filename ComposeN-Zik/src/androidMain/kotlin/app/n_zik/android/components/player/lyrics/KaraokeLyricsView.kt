package app.n_zik.android.components.player.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.n_zik.android.components.player.lyricsThemeColor
import app.n_zik.android.enums.lyrics.LyricsBackground
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.verticalFadingEdge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.withFrameMillis
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextLayoutResult

/**
 * A single word with its start and end time in milliseconds.
 */
data class KaraokeWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val charStartIndex: Int = 0
)

/**
 * A single karaoke line: the text, its timestamp, word-level timings,
 * and agent/background info for positioning.
 */
data class KaraokeLine(
    val timeMs: Long,
    val text: String,
    val words: List<KaraokeWord>,
    val agent: String? = null,     // "v1", "v2", "v1000", etc.
    val isBackground: Boolean = false
)

/**
 * Helper to create a fast clipping path using bounding boxes instead of complex text glyph outlines.
 */
fun getFastPathForRange(layout: TextLayoutResult, startIdx: Int, endIdx: Int): Path {
    val path = Path()
    if (startIdx > endIdx || startIdx < 0 || endIdx >= layout.layoutInput.text.length) return path
    
    val startLine = layout.getLineForOffset(startIdx)
    val endLine = layout.getLineForOffset(endIdx)
    
    for (i in startLine..endLine) {
        val lineStartIdx = layout.getLineStart(i)
        val lineEndIdx = layout.getLineEnd(i, visibleEnd = true)
        val clipStartIdx = maxOf(startIdx, lineStartIdx)
        val clipEndIdx = minOf(endIdx, lineEndIdx - 1)
        
        if (clipStartIdx <= clipEndIdx) {
            val startBox = layout.getBoundingBox(clipStartIdx)
            val endBox = layout.getBoundingBox(clipEndIdx)
            path.addRect(Rect(startBox.left, startBox.top, endBox.right, endBox.bottom))
        }
    }
    return path
}

/**
 * Parse the extended LRC format produced by TTMLParser.toLRC().
 *
 * Format:
 * ```
 * [MM:SS.cc]{agent:v1}Line text
 * <word1:startSec:endSec|word2:startSec:endSec>
 * [MM:SS.cc]{bg}Background line
 * <word1:startSec:endSec|word2:startSec:endSec>
 * ```
 */
fun parseKaraokeLrc(text: String): List<KaraokeLine> {
    val lines = text.trim().lines()
    val result = mutableListOf<KaraokeLine>()

    val agentRegex = Regex("\\{agent:([^}]+)\\}")
    val bgRegex = Regex("\\{bg\\}")

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.startsWith("[")) {
            // Parse [MM:SS.cc]text
            val closeBracket = line.indexOf(']')
            if (closeBracket < 0) { i++; continue }
            val timeStr = line.substring(1, closeBracket)
            var lineText = line.substring(closeBracket + 1)

            // Parse agent tag
            val agentMatch = agentRegex.find(lineText)
            val agent = agentMatch?.groupValues?.get(1)
            if (agentMatch != null) {
                lineText = lineText.replaceFirst(agentRegex, "")
            }

            // Parse background tag
            val isBg = bgRegex.containsMatchIn(lineText)
            if (isBg) {
                lineText = lineText.replaceFirst(bgRegex, "")
            }

            val cleanText = lineText.trim()
            val timeMs = parseLrcTime(timeStr)

            // Check if next lines are word-timing lines (can be multiline)
            var words = emptyList<KaraokeWord>()
            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("<")) {
                val wordsRawBuilder = StringBuilder()
                i++ // Move to the first word timing line
                while (i < lines.size) {
                    val lineTrim = lines[i].trim()
                    wordsRawBuilder.append(lineTrim)
                    if (lineTrim.endsWith(">")) {
                        break
                    }
                    i++
                }
                words = parseWordTimings(wordsRawBuilder.toString())
                var searchIndex = 0
                words = words.map { w ->
                    val idx = cleanText.indexOf(w.text, searchIndex)
                    if (idx >= 0) {
                        searchIndex = idx + w.text.length
                        w.copy(charStartIndex = idx)
                    } else {
                        w
                    }
                }
            }

            result.add(KaraokeLine(timeMs, cleanText, words, agent, isBg))
        }
        i++
    }
    return result
}

private fun parseLrcTime(timeStr: String): Long {
    // Format: MM:SS.cc
    return try {
        val parts = timeStr.split(":")
        val minutes = parts[0].toLong()
        val secondsParts = parts[1].split(".")
        val seconds = secondsParts[0].toLong()
        val centiseconds = secondsParts[1].toLong()
        minutes * 60_000 + seconds * 1000 + centiseconds * 10
    } catch (_: Exception) {
        0L
    }
}

private fun parseWordTimings(line: String): List<KaraokeWord> {
    // Format: <word1:startSec:endSec|word2:startSec:endSec>
    val content = line.removePrefix("<").removeSuffix(">")
    if (content.isBlank()) return emptyList()

    return content.split("|").mapNotNull { part ->
        // Handle words that may contain colons (unlikely but defensive)
        // Format: text:startTime:endTime
        val lastColon = part.lastIndexOf(':')
        if (lastColon < 0) return@mapNotNull null
        val secondLastColon = part.lastIndexOf(':', lastColon - 1)
        if (secondLastColon < 0) return@mapNotNull null

        val wordText = part.substring(0, secondLastColon)
        val startSec = part.substring(secondLastColon + 1, lastColon).toDoubleOrNull() ?: return@mapNotNull null
        val endSec = part.substring(lastColon + 1).toDoubleOrNull() ?: return@mapNotNull null
        KaraokeWord(
            text = wordText,
            startMs = (startSec * 1000).toLong(),
            endMs = (endSec * 1000).toLong()
        )
    }
}

/**
 * One-shot computation of the interval-indicator data for karaoke lines:
 * the initial gap (when the first non-background line starts late) and the per-line
 * gap windows (overlapping windows removed to prevent double loaders in duets).
 */
fun computeKaraokeGapWindows(lines: List<KaraokeLine>): Pair<Pair<Long, Long>?, Map<Int, Pair<Long, Long>>> {
    val firstStart = lines.firstOrNull { !it.isBackground }?.timeMs ?: 0L
    val initialGapWindow = if (firstStart > 3000L) Pair(0L, firstStart - 650L) else null

    val gapWindows = buildMap {
        lines.forEachIndexed { index, line ->
            if (line.isBackground) return@forEachIndexed
            val startMs = line.timeMs
            val nextStartMs = if (index < lines.size - 1) lines.drop(index + 1).firstOrNull { !it.isBackground }?.timeMs ?: (startMs + 10000L) else startMs + 10000L
            val lineEndMs = if (line.words.isNotEmpty()) line.words.maxOf { it.endMs } else line.timeMs + 2000L

            val gap = nextStartMs - lineEndMs
            if (gap > 2500L) {
                put(index, Pair(lineEndMs, nextStartMs - 650L))
            }
        }
    }.toMutableMap().apply {
        val keysToRemove = mutableSetOf<Int>()
        for ((idx1, window1) in this) {
            for ((idx2, window2) in this) {
                if (idx1 < idx2) {
                    if (window1.first < window2.second && window1.second > window2.first) {
                        keysToRemove.add(idx1)
                    }
                }
            }
        }
        keysToRemove.forEach { remove(it) }
    }

    return initialGapWindow to gapWindows
}

@Composable
fun KaraokeLyricsView(
    text: String,
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    onSeekTo: (Long) -> Unit,
    showlyricsthumbnail: Boolean,
    isLandscape: Boolean,
    trailingContent: (@Composable () -> Unit)?,
    isAutoScrollEnabled: Boolean = true,
    onAutoScrollEnabledChange: (Boolean) -> Unit = {},
    showBackgroundLyrics: Boolean = true,
    lyricsBackground: LyricsBackground,
    showSecondLine: Boolean,
    translateEnabled: Boolean,
    romanizationEnabled: Boolean,
    languageDestination: Language,
    translator: Translator,
    lyricsOutline: LyricsOutline,
    colorPaletteMode: ColorPaletteMode,
    fontSize: LyricsFontSize,
    customSize: Float,
    lyricsSizeAnimate: Boolean,
    lyricsColor: LyricsColor,
    lyricsCustomColor: Int,
    dominantColor: Int,
    lyricsHighlight: LyricsHighlight,
    lyricsAlignment: LyricsAlignment,
    clickLyricsText: Boolean,
    karaokeRespectAgentPosition: Boolean,
    @Suppress("UNUSED_PARAMETER") // Kept for API compatibility; centering now uses viewportHeight
    thumbnailSize: Dp,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    onInvalidLrc: (Boolean) -> Unit,
    showIntervalIndicator: Boolean = true,
    showClipRectsDebug: Boolean = false
) {
    val density = LocalDensity.current

    // Parse LRC + one-shot gap windows off the main thread (issue #606). Previous lines stay
    // displayed until the new parse completes.
    var karaokeLines by remember { mutableStateOf<List<KaraokeLine>>(emptyList()) }
    var gapWindows by remember { mutableStateOf<Map<Int, Pair<Long, Long>>>(emptyMap()) }
    var initialGapWindow by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(text) {
        val parsed = withContext(NzikDispatchers.MEDIA) { parseKaraokeLrc(text) }
        val (initialGap, windows) = withContext(NzikDispatchers.MEDIA) { computeKaraokeGapWindows(parsed) }
        karaokeLines = parsed
        gapWindows = windows
        initialGapWindow = initialGap
        if (parsed.isEmpty()) onInvalidLrc(true) else onInvalidLrc(false)
    }

    // Track current position for word-by-word animation with exact Metrolist interpolation
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var lastPlayerPos = currentPositionProvider()
        var lastUpdateTime = System.currentTimeMillis()
        while (isActive) {
            withFrameMillis {
                val now = System.currentTimeMillis()
                val playerPos = currentPositionProvider()
                if (playerPos != lastPlayerPos) {
                    lastPlayerPos = playerPos
                    lastUpdateTime = now
                }
                val elapsed = now - lastUpdateTime
                currentPositionMs = lastPlayerPos + (if (isPlayingProvider()) elapsed else 0L)
            }
        }
    }

    // --- Translation cache ---
    val translationCache = remember(karaokeLines, showSecondLine, translateEnabled, romanizationEnabled, languageDestination) {
        mutableStateMapOf<Int, String>()
    }

    LaunchedEffect(karaokeLines, showSecondLine, translateEnabled, romanizationEnabled, languageDestination) {
        if (!showSecondLine && !translateEnabled && !romanizationEnabled) return@LaunchedEffect

        val linesToTranslate = mutableListOf<Pair<Int, String>>()
        karaokeLines.forEachIndexed { index, line ->
            val trimmed = line.text.trim()
            if (trimmed.isEmpty()) {
                translationCache[index] = trimmed
            } else if (!translationCache.containsKey(index)) {
                linesToTranslate.add(index to trimmed)
            }
        }

        if (linesToTranslate.isEmpty()) return@LaunchedEffect

        if (translateEnabled) {
            withContext(NzikDispatchers.UI) {
                Toaster.i(R.string.translation_in_progress)
            }
        }

        withContext(NzikDispatchers.DATA) {
            try {
                val textToTranslate = linesToTranslate.joinToString("\n") { it.second }

                val helperTranslation = translator.translate(textToTranslate, Language.CHINESE_TRADITIONAL, Language.AUTO)
                var destLanguage = languageDestination
                if (destLanguage == Language.AUTO) {
                    destLanguage = if (helperTranslation.translatedText == textToTranslate)
                        Language.CHINESE_TRADITIONAL
                    else
                        helperTranslation.sourceLanguage
                }
                val mainTranslation = translator.translate(textToTranslate, destLanguage, Language.AUTO)

                val cleanTranslatedText = mainTranslation.translatedText.replace("\\\"", "\"").trim()
                val cleanPronunciation = mainTranslation.translatedPronunciation?.replace("\\\"", "\"")?.trim()

                val helpPronLineText = helperTranslation.sourcePronunciation?.trim() ?: ""
                val mainPronLineText = mainTranslation.sourcePronunciation?.trim() ?: textToTranslate

                val cleanTransLines = cleanTranslatedText.split("\n")
                val cleanPronLines = cleanPronunciation?.split("\n")
                val helpPronLines = helpPronLineText.split("\n")
                val mainPronLines = mainPronLineText.split("\n")

                linesToTranslate.forEachIndexed { i, (sentenceIndex, trimmed) ->
                    val cleanTransLine = cleanTransLines.getOrNull(i)?.trim() ?: ""
                    val cleanPronLine = cleanPronLines?.getOrNull(i)?.trim()
                    
                    val hPronLine = helpPronLines.getOrNull(i)?.trim() ?: ""
                    val mPronLine = mainPronLines.getOrNull(i)?.trim() ?: trimmed
                    val transPronLine = cleanPronLine ?: cleanTransLine

                    val isSameText = mainTranslation.sourceText == mainTranslation.translatedText

                    val outputText = if (!showSecondLine || isSameText) {
                        if (translateEnabled && romanizationEnabled) {
                            transPronLine
                        } else if (translateEnabled) {
                            cleanTransLine
                        } else if (romanizationEnabled) {
                            if (helperTranslation.sourceText == helperTranslation.translatedText)
                                hPronLine
                            else
                                mPronLine.ifEmpty { trimmed }
                        } else {
                            trimmed
                        }
                    } else {
                        if (translateEnabled && romanizationEnabled) {
                            val pron = if (helperTranslation.sourceText == helperTranslation.translatedText) hPronLine else mPronLine.ifEmpty { trimmed }
                            pron + "\n[$transPronLine]"
                        } else if (translateEnabled) {
                            trimmed + "\n[$cleanTransLine]"
                        } else if (romanizationEnabled) {
                            val pron = if (helperTranslation.sourceText == helperTranslation.translatedText) hPronLine else mPronLine.ifEmpty { trimmed }
                            trimmed + "\n[$pron]"
                        } else {
                            trimmed
                        }
                    }

                    val finalText = outputText.replace("\\r", "\r").replace("\\n", "\n")
                    translationCache[sentenceIndex] = finalText
                }

                if (translateEnabled) {
                    withContext(NzikDispatchers.UI) {
                        Toaster.s(R.string.translation_successful)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("KaraokeLyricsView").e("sync translation error: ${e.message}")
                if (translateEnabled) {
                    withContext(NzikDispatchers.UI) {
                        Toaster.e(R.string.translation_failed)
                    }
                }
            }
        }
    }

    // Determine active lines (multiple can be active for overlapping agents).
    // Inside a gap (finished / blank / loader window, after a background line
    // ended) the set is empty: nothing stays lit, nothing is re-selected.
    val activeLineIndices = remember(currentPositionMs, karaokeLines) {
        computeActiveLineIndices(karaokeLines, currentPositionMs)
    }

    // Primary active line for scroll targeting: the furthest active main line,
    // or — when only a chorus line is active — that chorus (the index switches
    // to it, so the chorus advances to the center like a main line and
    // retreats when the next main line takes over). Track max reached line,
    // but reset when seeking backwards.
    var maxReachedLineIndex by remember { mutableIntStateOf(0) }
    val primaryActiveIndex = remember(activeLineIndices, currentPositionMs) {
        val currentIndex = primaryLyricsIndex(karaokeLines, activeLineIndices, currentPositionMs)
        // Allow going backwards when seeking (currentIndex is significantly before max)
        if (currentIndex < maxReachedLineIndex - 1) {
            maxReachedLineIndex = currentIndex
        } else if (currentIndex > maxReachedLineIndex) {
            maxReachedLineIndex = currentIndex
        }
        maxReachedLineIndex
    }

    val lazyListState = rememberLazyListState()

    // Reset scroll position to top when lyrics content changes (mode switch / new song)
    LaunchedEffect(text) {
        lazyListState.scrollToItem(0)
    }


    val isDragged by lazyListState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged) {
        if (isDragged) {
            onAutoScrollEnabledChange(false)
        }
    }

    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.roundToPx() }
    val vpH = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
    val effectiveVpH = if (vpH > 0) vpH else screenHeightPx

    // Base multiplier
    val baseMultiplier = if (showlyricsthumbnail) 0.28f else 0.42f

    // Use true line count from the text (with \n)
    val currentText = karaokeLines.getOrNull(primaryActiveIndex)?.text ?: ""
    val translationText = translationCache[primaryActiveIndex] ?: currentText
    val trueLineCount = translationText.lines().size.coerceIn(1, 5)

    // Map fontSize enum to relative size index (0-4)
    val fontSizeIndex = when (fontSize) {
        LyricsFontSize.Light -> 0
        LyricsFontSize.Medium -> 1
        LyricsFontSize.Heavy -> 2
        LyricsFontSize.Large -> 3
        else -> 4  // Custom
    }
    // Base 40 chars at Medium, scale by font size index
    // Light: 50, Medium: 40, Heavy: 30, Large: 25, Custom: uses customSize
    val charsPerLine = if (fontSize == LyricsFontSize.Custom) {
        (40f * 16f / customSize).toInt().coerceAtLeast(10)
    } else {
        (50 - fontSizeIndex * 10).coerceAtLeast(10)
    }
    val wrappedLines = when {
        translationText.length > charsPerLine * 2 -> 3
        translationText.length > charsPerLine -> 2
        else -> 1
    }
    val lineCount = maxOf(trueLineCount, wrappedLines)
    // Multiplier based on line count (from the logs - this worked)
    val lineMultiplier = when (lineCount) {
        1 -> 0.45f
        2 -> 0.46f
        3 -> 0.48f
        4 -> 0.46f
        else -> 0.44f
    }
    var multiplier = if (showlyricsthumbnail) lineMultiplier else 0.42f
    
    val hasBackgroundBelow = karaokeLines.getOrNull(primaryActiveIndex + 1)?.isBackground == true
    if (hasBackgroundBelow) {
        multiplier -= if (showlyricsthumbnail) 0.12f else 0.06f
    }
    
    val fixedCenter = (effectiveVpH * multiplier).toInt()

    // Centers the item at [targetItem] at its target position. Extracted so
    // both the active-line change and the chorus reveal recentering can share
    // the same math. Suspend: the smooth scroll (animateScrollBy) is.
    val recenterOnItem: suspend (Int) -> Unit = { targetItem: Int ->
        val reMeasuredVpH = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
        val finalEffectiveVpH = if (reMeasuredVpH > 0) reMeasuredVpH else screenHeightPx
        val hasLoader = showIntervalIndicator && initialGapWindow != null
        var finalMultiplier = if (reMeasuredVpH > 0) multiplier else 0.45f
        if (reMeasuredVpH == 0 && hasLoader && primaryActiveIndex == 0) {
            finalMultiplier = 0.50f
        }
        val finalFixedCenter = (finalEffectiveVpH * finalMultiplier).toInt()
        Timber.tag("KaraokeLyricsView").d("CENTER: item=$targetItem vpH=$reMeasuredVpH mult=$finalMultiplier center=$finalFixedCenter lines=$lineCount loader=$hasLoader")

        // Smooth scroll Metrolist-style: use animateScrollBy for fluid transitions
        val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetItem }
        if (itemInfo != null) {
            // Item is visible, animate to the target position (using multiplier for proper positioning)
            val targetPosition = finalFixedCenter
            val currentPos = itemInfo.offset + itemInfo.size / 2
            val offset = currentPos - targetPosition
            if (kotlin.math.abs(offset) > 10) {
                lazyListState.animateScrollBy(
                    value = offset.toFloat(),
                    animationSpec = tween(durationMillis = 800),
                )
            }
        } else {
            // Item is not visible, scroll to it first with proper offset
            lazyListState.scrollToItem(targetItem, -finalFixedCenter)
        }
    }

    // Item index of every lyric line inside the LazyColumn: the header spacer,
    // the optional initial loader, and the gap-loader items interleaved right
    // after their line (the loaders are SEPARATE items, so the scroll index
    // can switch to one).
    val lineItemIndices = remember(karaokeLines, gapWindows, showIntervalIndicator, initialGapWindow) {
        lyricsLineItemIndices(
            lineCount = karaokeLines.size,
            gapWindows = gapWindows,
            showIntervalIndicator = showIntervalIndicator,
            hasInitialLoader = initialGapWindow != null
        )
    }

    // The item the scroll index should point at: an active line (a main line,
    // else the active chorus — the index switches to the chorus, so it
    // advances to the center like a main line and retreats when the next main
    // line takes over); while no line is active, the gap-loader item of the
    // active gap window (or the initial loader); otherwise null = no
    // re-centering (the list stays put).
    val centerTarget: Int? = when {
        activeLineIndices.isNotEmpty() -> lineItemIndices.getOrNull(primaryActiveIndex)
        !showIntervalIndicator -> null
        else -> {
            val activeGap = activeGapLine(gapWindows, currentPositionMs)
            val initialGap = initialGapWindow
            when {
                activeGap != null -> lineItemIndices[activeGap] + 1
                initialGap != null && currentPositionMs in initialGap.first until initialGap.second -> 1
                else -> null
            }
        }
    }

    LaunchedEffect(centerTarget, density, isAutoScrollEnabled, vpH) {
        if (!isAutoScrollEnabled) return@LaunchedEffect
        val target = centerTarget ?: return@LaunchedEffect
        if (primaryActiveIndex == 0 || vpH == 0) {
            delay(100)
        }
        recenterOnItem(target)
    }

    // Chorus lines expand their slot (200ms) when they appear (lead-in) —
    // the height change shifts the active line's scroll position, so the
    // canvas appears to jump. (Choruses never de-pop: once sung they stay
    // shown.) When the set of shown chorus lines changes, wait for the
    // height animation to settle and re-center on the current target so the
    // sung line stays put. If the target sits below the visible center its
    // height change does not move it, and the |offset| > 10 guard inside
    // recenterOnItem makes this a no-op.
    val chorusShownKeys = remember(karaokeLines, currentPositionMs) {
        chorusShownIndices(karaokeLines, currentPositionMs)
    }
    LaunchedEffect(chorusShownKeys) {
        if (!isAutoScrollEnabled) return@LaunchedEffect
        delay(300) // chorus slot height animation is 200ms; wait for it to settle
        centerTarget?.let { recenterOnItem(it) }
    }

    // Resolve the accent color
    val accentColor = when (lyricsColor) {
        LyricsColor.White -> Color.White
        LyricsColor.Cover -> Color(dominantColor)
        LyricsColor.Custom -> Color(lyricsCustomColor)
        LyricsColor.Thememode -> lyricsThemeColor(colorPalette(), showBackgroundLyrics && showlyricsthumbnail)
    }

    val inactiveColor = accentColor.copy(alpha = 0.4f)

    val textSize = when (fontSize) {
        LyricsFontSize.Light -> typography().m.fontSize
        LyricsFontSize.Medium -> typography().l.fontSize
        LyricsFontSize.Heavy -> typography().xl.fontSize
        LyricsFontSize.Large -> typography().xlxl.fontSize
        else -> customSize.sp
    }

    var modifierBG = Modifier.verticalFadingEdge()
    if (showBackgroundLyrics && showlyricsthumbnail) modifierBG = modifierBG.background(colorPalette().accent)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            userScrollEnabled = true,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifierBG
                .fillMaxSize()
                .background(
                    if (isDisplayed && !showlyricsthumbnail)
                        when (lyricsBackground) {
                            LyricsBackground.Black -> Color.Black.copy(0.6f)
                            LyricsBackground.White -> Color.White.copy(0.4f)
                            else -> Color.Transparent
                        }
                    else Color.Transparent
                )
        ) {
        item(key = "header", contentType = 0) {
            Spacer(modifier = Modifier.height(with(LocalConfiguration.current) { screenHeightDp.dp }))
        }

        val initialGap = initialGapWindow
        if (showIntervalIndicator && initialGap != null) {
            item(key = "initial_loader", contentType = 2) {
                val isVisible = currentPositionMs in initialGap.first until initialGap.second
                LyricsIntervalIndicator(
                    gapStartMs = initialGap.first,
                    gapEndMs = initialGap.second,
                    currentPositionMs = currentPositionMs,
                    visible = isVisible,
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
        }

        // Each line is its own item, followed by its gap-loader item when the
        // line owns a gap window: the loaders are SEPARATE items (not part of
        // the previous line's item), so the scroll index can switch to one —
        // while a gap runs the list centers on the loader, and the index
        // switches to the chorus line itself while it is sung.
        karaokeLines.forEachIndexed { index, line ->
        item(key = index, contentType = "lyric_line") {
            val isActiveLine = index in activeLineIndices

            // Agent-based alignment: v1=Left, v2=Right, bg/v1000=Center
            val lineAlignment = when {
                karaokeRespectAgentPosition && line.agent == "v1" -> Alignment.Start
                karaokeRespectAgentPosition && line.agent == "v2" -> Alignment.End
                karaokeRespectAgentPosition && line.agent == "v1000" -> Alignment.CenterHorizontally
                karaokeRespectAgentPosition && line.isBackground -> Alignment.CenterHorizontally
                else -> when (lyricsAlignment) {
                    LyricsAlignment.Left -> Alignment.Start
                    LyricsAlignment.Center -> Alignment.CenterHorizontally
                    LyricsAlignment.Right -> Alignment.End
                }
            }
            val lineTextAlign = when {
                karaokeRespectAgentPosition && line.agent == "v1" -> TextAlign.Left
                karaokeRespectAgentPosition && line.agent == "v2" -> TextAlign.Right
                karaokeRespectAgentPosition && line.agent == "v1000" -> TextAlign.Center
                karaokeRespectAgentPosition && line.isBackground -> TextAlign.Center
                else -> when (lyricsAlignment) {
                    LyricsAlignment.Left -> TextAlign.Left
                    LyricsAlignment.Center -> TextAlign.Center
                    LyricsAlignment.Right -> TextAlign.Right
                }
            }

            // A chorus is revealed within an extended window (isChorusLineShown):
            // it fades/expands in a few seconds BEFORE it is sung (lead-in), and
            // once sung it STAYS shown (slot kept, rendered in the past state) —
            // it never de-pops back, so the canvas is not shifted again after
            // the reveal. Regular lines are always shown.
            val isShown = if (line.isBackground) isChorusLineShown(line, currentPositionMs) else isActiveLine
            // A regular line stays in the animated karaoke branch for the sung-fade
            // tail after it deactivates, so its lift finishes in the background
            // instead of being cut off on the switch.
            val isStillAnimating = isKaraokeLineStillAnimating(line, currentPositionMs)
            // Fully in the past state: settling tail over (lines with words) or
            // the active window over (wordless lines). Chorus lines fade down to
            // the regular past-line dim level once they reach it — the same
            // "fade to the background" the other lines get once sung.
            val isPastState = isKaraokeLineInPastState(line, currentPositionMs, karaokeLines.getOrNull(index + 1)?.timeMs)
            val animateOpacity by animateFloatAsState(
                targetValue = karaokeLineOpacity(line.isBackground, isShown, isPastState),
                animationSpec = tween(if (line.isBackground) 200 else 600, easing = FastOutSlowInEasing),
                label = ""
            )
            // The pop (scale up) is kept while the leaving line is still settling,
            // so it fades in place instead of popping back down and shifting the
            // text; it only returns to the base scale once the line is fully in the
            // background (static branch, already dimmed). Chorus lines never scale
            // at all (loader behavior: height + opacity only), so their pop cannot
            // shift their text either.
            val animateScale by animateFloatAsState(
                targetValue = karaokeLineScale(line.isBackground, isActiveLine || isStillAnimating),
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = ""
            )
            val heightFraction by animateFloatAsState(
                targetValue = karaokeLineHeightFraction(line.isBackground, isShown),
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = ""
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Render without an offscreen buffer: with alpha < 1 the default
                        // strategy implicitly clips content to the item bounds, which
                        // cuts the rising karaoke characters (up to ~13dp above the
                        // layout — background lines in particular). ModulateAlpha
                        // applies the alpha per draw command, so overflow is visible
                        // and the lift effect is never cropped. Chorus lines are
                        // clipped while their slot is collapsing so the natural-size
                        // content cannot be drawn over the neighboring lines; once
                        // fully expanded the clip is off again.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = animateOpacity
                        scaleX = animateScale
                        scaleY = animateScale
                        clip = line.isBackground && heightFraction < 0.999f
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = if (clickLyricsText) LocalIndication.current else null,
                        onClick = {
                            if (clickLyricsText) onSeekTo(line.timeMs) else onDismiss()
                        }
                    )
                    .layout { measurable, constraints ->
                        // Collapse/expand the item slot (chorus lines only): measure
                        // the natural content height, then report a fraction of it,
                        // so a not-sung chorus takes no space (gap removed) and a
                        // sung one takes its place back — like the interval loader.
                        // Regular lines keep fraction 1f (no-op).
                        val placeable = measurable.measure(constraints)
                        val targetHeight = (placeable.height * heightFraction).toInt().coerceAtLeast(0)
                        layout(placeable.width, targetHeight) { placeable.place(0, 0) }
                    }
                    .padding(vertical = 4.dp, horizontal = 32.dp)
                    .background(
                        if (isActiveLine && !line.isBackground && lyricsHighlight == LyricsHighlight.White) Color.White.copy(0.5f)
                        else if (isActiveLine && !line.isBackground && lyricsHighlight == LyricsHighlight.Black) Color.Black.copy(0.5f)
                        else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    ),
                horizontalAlignment = lineAlignment
            ) {
                val gapWindow = gapWindows[index]
                val showLoader = showIntervalIndicator && gapWindow != null &&
                        currentPositionMs >= gapWindow.first && currentPositionMs <= gapWindow.second

                val lineAccent = if (line.isBackground) accentColor.copy(alpha = 0.85f) else accentColor
                val lineInactive = if (line.isBackground) accentColor.copy(alpha = 0.3f) else inactiveColor

                val displayedText = translationCache[index] ?: line.text

                if (line.words.isNotEmpty() && (isActiveLine || isStillAnimating)) {
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                    Box(contentAlignment = Alignment.Center) {
                        val isTextReplaced = displayedText != line.text && !showSecondLine

                        val backgroundText = buildAnnotatedString {
                            if (displayedText != line.text && showSecondLine) {
                                val originalLen = line.text.trim().length
                                val safeLen = originalLen.coerceAtMost(displayedText.length)
                                withStyle(SpanStyle(color = lineInactive)) {
                                    append(displayedText.substring(0, safeLen))
                                }
                                withStyle(SpanStyle(color = lineAccent.copy(alpha = 0.85f))) {
                                    append(displayedText.substring(safeLen))
                                }
                            } else if (line.words.isNotEmpty() && !isTextReplaced) {
                                // Per-word coloring: smooth fade for active long words
                                var searchIdx = 0
                                line.words.forEachIndexed { wordIdx, word ->
                                    val isActive = currentPositionMs >= word.startMs && currentPositionMs < word.endMs
                                    val isSung = currentPositionMs >= word.endMs
                                    val dur = word.endMs - word.startMs
                                    val isLongWord = dur > 500L
                                    
                                    // Append spaces between words
                                    val wordStartInText = line.text.indexOf(word.text, searchIdx)
                                    if (wordStartInText > searchIdx) {
                                        withStyle(SpanStyle(color = lineInactive)) {
                                            append(line.text.substring(searchIdx, wordStartInText))
                                        }
                                    }
                                    
                                    // Word itself — SING words (short or long alike) are transparent
                                    // in this layer: the foreground clip renders the settled word,
                                    // so both word types share the exact same final pixels (no dim
                                    // "halo" behind short words, which made them look brighter than
                                    // long ones once sung). Long ACTIVE words get a smooth fade
                                    // under the wave reveal.
                                    val wordColor = when {
                                        isSung -> androidx.compose.ui.graphics.Color.Transparent
                                        isLongWord && isActive -> {
                                            val linearProgress = ((currentPositionMs - word.startMs).toFloat() / dur.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                            val fadeAlpha = (1f - linearProgress).coerceIn(0f, 1f)
                                            lineInactive.copy(alpha = fadeAlpha * lineInactive.alpha)
                                        }
                                        else -> lineInactive
                                    }
                                    withStyle(SpanStyle(color = wordColor)) {
                                        append(word.text)
                                    }
                                    searchIdx = wordStartInText + word.text.length
                                }
                                // Remaining text
                                if (searchIdx < line.text.length) {
                                    withStyle(SpanStyle(color = lineInactive)) {
                                        append(line.text.substring(searchIdx))
                                    }
                                }
                            } else {
                                withStyle(SpanStyle(color = lineInactive)) {
                                    append(displayedText)
                                }
                            }
                        }

                        // Background (inactive) text
                        BasicText(
                            text = backgroundText,
                            style = TextStyle(
                                fontSize = textSize,
                                fontWeight = FontWeight.Bold,
                                textAlign = lineTextAlign,
                                lineHeight = textSize * KARAOKE_LINE_HEIGHT_MULTIPLIER
                            ),
                            onTextLayout = { textLayoutResult = it }
                        )

                        if (!isTextReplaced) {
                            // Unclipped Glow Layer (Restored with interpolation)
                            val glowText = buildAnnotatedString {
                                line.words.forEachIndexed { wordIndex, word ->
                                    val isWordActive = currentPositionMs >= word.startMs && currentPositionMs < word.endMs
                                    val isWordSung = currentPositionMs >= word.endMs
                                    
                                    if (isWordActive || isWordSung) {
                                        val dur = word.endMs - word.startMs
                                        val sungFactor = if (isWordSung) 1f 
                                                         else if (isWordActive) ((currentPositionMs - word.startMs).toFloat() / dur.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                                         else 0f
                                        
                                        val wordLenText = word.text.length.coerceAtLeast(1)
                                        val impactRatio = dur.toFloat() / wordLenText
                                        val fadeFactor = (sungFactor * 5f).coerceIn(0f, 1f) * ((1f - sungFactor) * 8f).coerceIn(0f, 1f)
                                        val impactFactor = (((impactRatio - 100f) / 250f).coerceIn(0f, 1f) * 0.6f + ((dur.toFloat() - 300f) / 1500f).coerceIn(0f, 1f) * 0.4f).coerceIn(0f, 1f) * fadeFactor
                                        
                                        // Make the glow completely opaque/solid to combat the transparency
                                        val glowAlpha = (1f * impactFactor).coerceIn(0f, 0.7f)
                                        val baseGlowRadius = with(density) { 8.dp.toPx() } * impactFactor
                                        
                                        if (impactFactor > 0.01f && baseGlowRadius > 0f) {
                                            withStyle(
                                                SpanStyle(
                                                    color = androidx.compose.ui.graphics.Color.Transparent,
                                                    shadow = Shadow(
                                                        color = lineAccent.copy(alpha = glowAlpha),
                                                        blurRadius = baseGlowRadius
                                                    )
                                                )
                                            ) {
                                                append(word.text)
                                            }
                                        } else {
                                            withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent)) { append(word.text) }
                                        }
                                    } else {
                                        withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent)) { append(word.text) }
                                    }
                                    if (wordIndex < line.words.lastIndex) {
                                        withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent)) { append(" ") }
                                    }
                                }
                                
                                // Preserve layout identical to displayedText by appending the translation part transparently
                                val builtLen = this.length
                                if (builtLen < displayedText.length) {
                                    withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent)) {
                                        append(displayedText.substring(builtLen))
                                    }
                                }
                            }
                            
                            BasicText(
                                text = glowText,
                                style = TextStyle(
                                    fontSize = textSize,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = lineTextAlign,
                                    lineHeight = textSize * KARAOKE_LINE_HEIGHT_MULTIPLIER
                                )
                            )
                        }

                        // Foreground (active) text with character-level clipping.
                        // Per-word settle: every word fades from the full accent
                        // to the dimmed past-line accent (the color the static
                        // branch uses) over the sung-fade window after its OWN
                        // end — short words included — so nothing pops in the
                        // background and the branch switch at the end of the
                        // fade tail is seamless (all words already sit on the
                        // static branch color).
                        val foregroundText = buildAnnotatedString {
                            if (line.words.isNotEmpty() && !isTextReplaced) {
                                var searchIdx = 0
                                line.words.forEach { word ->
                                    val wordStartInText = line.text.indexOf(word.text, searchIdx)
                                    if (wordStartInText > searchIdx) {
                                        append(line.text.substring(searchIdx, wordStartInText))
                                    }
                                    val wordColor = if (currentPositionMs >= word.endMs) {
                                        karaokeSungWordSettleColor(lineAccent, word.endMs, currentPositionMs)
                                    } else {
                                        lineAccent
                                    }
                                    withStyle(SpanStyle(color = wordColor)) {
                                        append(word.text)
                                    }
                                    searchIdx = wordStartInText + word.text.length
                                }
                                if (searchIdx < line.text.length) {
                                    append(line.text.substring(searchIdx))
                                }
                                // Translation part (never sung, never clipped here)
                                if (displayedText.length > line.text.length) {
                                    append(displayedText.substring(line.text.length))
                                }
                            } else {
                                append(displayedText)
                            }
                        }
                        BasicText(
                            text = foregroundText,
                            style = TextStyle(
                                color = lineAccent,
                                fontSize = textSize,
                                fontWeight = FontWeight.Bold,
                                textAlign = lineTextAlign,
                                lineHeight = textSize * KARAOKE_LINE_HEIGHT_MULTIPLIER
                            ),
                            modifier = Modifier.drawWithContent {
                                val layout = textLayoutResult ?: return@drawWithContent

                                // Guard: layout may be stale (computed for a different text) during
                                // recomposition after a mode switch or translation cache update.
                                // In that case, skip the animated draw to avoid getBoundingBox() crash.
                                val layoutTextLength = layout.layoutInput.text.length
                                val isLayoutStale = layoutTextLength != displayedText.length
                                if (isLayoutStale) {
                                    drawContent()
                                    return@drawWithContent
                                }

                                // [DEBUG] text layout bounds — shows where the text box ends
                                if (showClipRectsDebug) {
                                    val layoutSize = layout.size
                                    drawRect(Color.Blue.copy(alpha = 0.2f), Offset(0f, 0f), Size(layoutSize.width.toFloat(), layoutSize.height.toFloat()))
                                }

                                if (isTextReplaced) {
                                    val lineStartMs = line.timeMs
                                    val lineEndMs = line.words.maxOfOrNull { it.endMs } ?: (lineStartMs + 2000L)
                                    
                                    if (currentPositionMs >= lineEndMs) {
                                        this@drawWithContent.drawContent()
                                    } else if (currentPositionMs > lineStartMs) {
                                        val progress = ((currentPositionMs - lineStartMs).toFloat() / (lineEndMs - lineStartMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                        val totalLength = displayedText.length
                                        
                                        if (totalLength > 0) {
                                            val activeCharIdx = (progress * totalLength).toInt().coerceAtMost(totalLength - 1)
                                            val charLp = ((progress * totalLength) - activeCharIdx).coerceIn(0f, 1f)
                                            
                                            if (activeCharIdx > 0) {
                                                val path = getFastPathForRange(layout, 0, activeCharIdx - 1)
                                                drawContext.canvas.save()
                                                drawContext.canvas.clipPath(path)
                                                this@drawWithContent.drawContent()
                                                drawContext.canvas.restore()
                                            }
                                            
                                            val ease = { t: Float -> t * t * (3f - 2f * t) }
                                            val bounceFactor = when {
                                                charLp < 0.4f -> ease(charLp / 0.4f)
                                                charLp > 0.6f -> ease((1f - charLp) / 0.4f)
                                                else -> 1f
                                            }
                                            val charBounceY = -bounceFactor * with(density) { 0.8.dp.toPx() }
                                            val scaleFactor = 1f + (bounceFactor * 0.05f)
                                            
                                            val charBox = layout.getBoundingBox(activeCharIdx)
                                            
                                            drawContext.canvas.save()
                                            val pivotX = charBox.left + charBox.width / 2f
                                            val pivotY = charBox.bottom
                                            drawContext.canvas.translate(pivotX, pivotY + charBounceY)
                                            drawContext.canvas.scale(scaleFactor, scaleFactor)
                                            drawContext.canvas.translate(-pivotX, -pivotY)
                                            
                                            drawContext.canvas.clipRect(Rect(charBox.left, charBox.top, charBox.right, charBox.bottom))
                                            this@drawWithContent.drawContent()
                                            drawContext.canvas.restore()
                                        }
                                    }
                                } else {
                                    line.words.forEach { word ->
                                    val isWordActive = currentPositionMs >= word.startMs && currentPositionMs < word.endMs
                                    val isWordSung = currentPositionMs >= word.endMs
                                    
                                    val maxIdx = layoutTextLength.coerceAtLeast(1) - 1
                                    val wStartIdx = word.charStartIndex.coerceIn(0, maxIdx)
                                    val wEndIdx = (word.charStartIndex + word.text.length - 1).coerceIn(0, maxIdx)

                                    val startBox = layout.getBoundingBox(wStartIdx)
                                    val endBox = layout.getBoundingBox(wEndIdx)
                                    
                                    if (isWordSung) {
                                        val dur = word.endMs - word.startMs
                                        if (dur > 500L) {
                                            // Long word: smooth descent with fading oscillation
                                            val fadeProgress = ((currentPositionMs - word.endMs).toFloat() / KARAOKE_SUNG_FADE_MS).coerceIn(0f, 1f)
                                            val fadeFactor = 1f - (fadeProgress * fadeProgress * (3f - 2f * fadeProgress))
                                            val wordLen = word.text.length
                                            val durFactor = (dur / 800f).coerceIn(0.6f, 2f)
                                            val scaleAmount = 1f + (fadeFactor * 0.04f)
                                            // Oscillation fades gradually
                                            val waveAmplitude = fadeFactor * with(density) { (4f * durFactor).dp.toPx() }
                                            val waveFrequency = (600f / dur.toFloat().coerceAtLeast(100f)).coerceIn(2f, 8f)
                                            // Rise fades gradually
                                            val riseAmount = fadeFactor * with(density) { (2.5f * durFactor).dp.toPx() }
                                            
                                            drawContext.canvas.save()
                                            drawContext.canvas.clipRect(Rect(startBox.left - with(density) { 8.dp.toPx() }, startBox.top - waveAmplitude - riseAmount - with(density) { 8.dp.toPx() }, endBox.right + with(density) { 8.dp.toPx() }, endBox.bottom + waveAmplitude + with(density) { 8.dp.toPx() }))
                                            // [DEBUG] sung long-word envelope
                                            if (showClipRectsDebug) {
                                                val sp = with(density) { 8.dp.toPx() }
                                                drawRect(
                                                    Color.Red.copy(alpha = 0.35f),
                                                    Offset(startBox.left - sp, startBox.top - waveAmplitude - riseAmount - sp),
                                                    Size(endBox.right + sp - (startBox.left - sp), endBox.bottom + waveAmplitude + sp - (startBox.top - waveAmplitude - riseAmount - sp))
                                                )
                                            }
                                            
                                            for (charIdx in 0 until wordLen) {
                                                val globalIdx = (word.charStartIndex + charIdx).coerceIn(0, displayedText.length - 1)
                                                val charBox = layout.getBoundingBox(globalIdx)
                                                val charPos = charIdx.toFloat() / wordLen.coerceAtLeast(1)
                                                // Oscillation continues from where it ended, fading out
                                                val endPhase = charPos * waveFrequency - waveFrequency * 1.5f
                                                val wavePhase = endPhase + fadeProgress * 0.5f // slight forward motion
                                                val charWaveY = kotlin.math.sin(wavePhase).toFloat() * waveAmplitude
                                                
                                                val pivotX = charBox.left + charBox.width / 2f
                                                val pivotY = charBox.bottom
                                                drawContext.canvas.save()
                                                drawContext.canvas.translate(pivotX, pivotY + charWaveY - riseAmount)
                                                drawContext.canvas.scale(scaleAmount, scaleAmount)
                                                drawContext.canvas.translate(-pivotX, -pivotY)
                                                drawContext.canvas.clipRect(Rect(charBox.left, charBox.top, charBox.right, charBox.bottom))
                                                this@drawWithContent.drawContent()
                                                // [DEBUG] per-char clip where it actually lands (transformed space)
                                                if (showClipRectsDebug) {
                                                    drawRect(Color.Green.copy(alpha = 0.35f), Offset(charBox.left, charBox.top), Size(charBox.width, charBox.height))
                                                }
                                                drawContext.canvas.restore()
                                            }
                                            drawContext.canvas.restore()
                                        } else {
                                            // Short word: same per-character settle as the long
                                            // words, but at rest (no wave/rise motion) — identical
                                            // final rendering so short and long sung words look
                                            // the same (a whole-word path clip left glyph
                                            // overhangs uncut, which made short words look
                                            // brighter than long ones once sung).
                                            val wordLen = word.text.length
                                            drawContext.canvas.save()
                                            drawContext.canvas.clipRect(Rect(startBox.left - with(density) { 8.dp.toPx() }, startBox.top - with(density) { 8.dp.toPx() }, endBox.right + with(density) { 8.dp.toPx() }, endBox.bottom + with(density) { 8.dp.toPx() }))
                                            for (charIdx in 0 until wordLen) {
                                                val globalIdx = (word.charStartIndex + charIdx).coerceIn(0, displayedText.length - 1)
                                                val charBox = layout.getBoundingBox(globalIdx)
                                                drawContext.canvas.save()
                                                drawContext.canvas.clipRect(Rect(charBox.left, charBox.top, charBox.right, charBox.bottom))
                                                this@drawWithContent.drawContent()
                                                drawContext.canvas.restore()
                                            }
                                            drawContext.canvas.restore()
                                        }
                                    } else if (isWordActive) {
                                        val dur = word.endMs - word.startMs
                                        val linearProgress = ((currentPositionMs - word.startMs).toFloat() / dur.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                        
                                        if (dur > 500L) {
                                            // Long word: wave effect
                                            val easedProgress = linearProgress * linearProgress * (3f - 2f * linearProgress)
                                            val wordLen = word.text.length
                                            val durFactor = (dur / 800f).coerceIn(0.6f, 2f)
                                            val waveAmplitude = with(density) { (4f * durFactor).dp.toPx() }
                                            val waveFrequency = (600f / dur.toFloat().coerceAtLeast(100f)).coerceIn(2f, 8f)
                                            val scaleAmount = 1f + (easedProgress * 0.04f)
                                            
                                            // Aggressive rise curve: fast at start, then stabilizes
                                            val riseCurve = linearProgress * linearProgress // quadratic - rises fast
                                            val riseAmount = riseCurve * with(density) { (2.5f * durFactor).dp.toPx() }
                                            
                                            // Fill clip: per-physical-line sweep — a word that wraps (typically a CJK line
                                            // merged into a single word) fills line by line instead of revealing every
                                            // wrapped line at once (GH #820). Single-line words keep the legacy clipRect
                                            // path, so unwrapped words stay bit-identical; at zero progress nothing is
                                            // revealed (the legacy 16dp start sliver is intentionally gone, one frame).
                                            val pad = with(density) { 8.dp.toPx() }
                                            val firstLine = layout.getLineForOffset(wStartIdx)
                                            val lastLine = layout.getLineForOffset(wEndIdx)
                                            val spannedLines = lastLine - firstLine + 1
                                            // Per-line fill geometry is computed for every long word (single- or
                                            // multi-line): it feeds the per-line reveal gate below. For a single
                                            // physical line the rect matches the legacy fast-path clipRect exactly
                                            // (same formula, same headroom), so the gate is a no-op there.
                                            val fillLayout = object : ActiveFillLayout {
                                                override fun getLineForOffset(offset: Int) = layout.getLineForOffset(offset)
                                                override fun getLineStart(lineIndex: Int) = layout.getLineStart(lineIndex)
                                                override fun getLineEnd(lineIndex: Int, visibleEnd: Boolean) = layout.getLineEnd(lineIndex, visibleEnd)
                                                override fun getBoundingBox(offset: Int) = layout.getBoundingBox(offset)
                                            }
                                            val geometry = collectActiveFillGeometry(
                                                layout = fillLayout,
                                                startIdx = wStartIdx,
                                                endIdx = wEndIdx
                                            )
                                            val fillRects = computeActiveFillRects(
                                                lineCharCounts = geometry.lineCharCounts,
                                                lineLefts = geometry.lineLefts,
                                                lineRights = geometry.lineRights,
                                                lineTops = geometry.lineTops,
                                                lineBottoms = geometry.lineBottoms,
                                                progress = easedProgress,
                                                topHeadroom = waveAmplitude + riseAmount,
                                                bottomHeadroom = waveAmplitude,
                                                padding = pad
                                            )
                                            if (spannedLines == 1) {
                                                val fillRight = startBox.left + (endBox.right - startBox.left) * easedProgress
                                                drawContext.canvas.save()
                                                drawContext.canvas.clipRect(Rect(startBox.left - pad, startBox.top - waveAmplitude - riseAmount - pad, fillRight + pad, endBox.bottom + waveAmplitude + pad))
                                                // [DEBUG] active long-word envelope (single line)
                                                if (showClipRectsDebug) {
                                                    drawRect(
                                                        Color.Red.copy(alpha = 0.35f),
                                                        Offset(startBox.left - pad, startBox.top - waveAmplitude - riseAmount - pad),
                                                        Size((fillRight + pad) - (startBox.left - pad), (endBox.bottom + waveAmplitude + pad) - (startBox.top - waveAmplitude - riseAmount - pad))
                                                    )
                                                }
                                            } else {
                                                if (currentPositionMs - word.startMs < 50L) {
                                                    Timber.tag("KaraokeLyricsView").d("Active word spans $spannedLines physical lines (chars $wStartIdx..$wEndIdx)")
                                                }
                                                drawContext.canvas.save()
                                                val fillClipPath = Path()
                                                // Per-line rects can overlap vertically (padding + wave headroom exceeds
                                                // the line spacing); the default EvenOdd fill type would punch holes in
                                                // the overlap, clipping the tops of characters rising into it
                                                fillClipPath.fillType = PathFillType.NonZero
                                                fillRects.forEach { rect ->
                                                    if (rect.revealed) {
                                                        fillClipPath.addRect(Rect(rect.left, rect.top, rect.right, rect.bottom))
                                                    }
                                                }
                                                drawContext.canvas.clipPath(fillClipPath)
                                                // [DEBUG] active long-word envelope (per-line rects)
                                                if (showClipRectsDebug) {
                                                    fillRects.forEach { rect ->
                                                        if (rect.revealed) {
                                                            drawRect(Color.Red.copy(alpha = 0.35f), Offset(rect.left, rect.top), Size(rect.right - rect.left, rect.bottom - rect.top))
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            // Draw each physical line's characters, gated by that line's own sweep
                                            // rect inset to the true frontier (activeLineGateRect strips the 8dp
                                            // horizontal pad): with the padded edges a short final line (often one
                                            // CJK char) would open half-revealed as soon as the previous line
                                            // closes — the visible flash at the end of a wrapped word. The per-line
                                            // rects also overlap vertically (headroom exceeds the CJK line
                                            // spacing), so the union envelope alone would light up a sliver of the
                                            // next line's characters as soon as the previous line is fully filled
                                            // (GH #820 residual): each line's characters are clipped to that line's
                                            // rect, so the horizontal reveal stays strictly per-line while the
                                            // vertical headroom for wave/rise is kept.
                                            activeWordLineCharRanges(fillLayout, wStartIdx, wEndIdx).forEachIndexed { k, charRange ->
                                                val lineRect = fillRects.getOrNull(k) ?: return@forEachIndexed
                                                if (charRange.isEmpty() || !lineRect.revealed || lineRect.right <= lineRect.left) {
                                                    return@forEachIndexed
                                                }
                                                drawContext.canvas.save()
                                                drawContext.canvas.clipRect(activeLineGateRect(lineRect, pad))
                                            // Draw each character of this line with traveling wave
                                            for (charIdx in charRange) {
                                                val globalIdx = (word.charStartIndex + charIdx).coerceIn(0, displayedText.length - 1)
                                                val charBox = layout.getBoundingBox(globalIdx)
                                                val charPos = charIdx.toFloat() / wordLen.coerceAtLeast(1)
                                                
                                                val wavePhase = charPos * waveFrequency - linearProgress * waveFrequency * 1.5f
                                                val charWaveY = kotlin.math.sin(wavePhase).toFloat() * waveAmplitude
                                                
                                                val pivotX = charBox.left + charBox.width / 2f
                                                val pivotY = charBox.bottom
                                                
                                                drawContext.canvas.save()
                                                drawContext.canvas.translate(pivotX, pivotY + charWaveY - riseAmount)
                                                drawContext.canvas.scale(scaleAmount, scaleAmount)
                                                drawContext.canvas.translate(-pivotX, -pivotY)
                                                drawContext.canvas.clipRect(Rect(charBox.left, charBox.top, charBox.right, charBox.bottom))
                                                this@drawWithContent.drawContent()
                                                // [DEBUG] per-char clip where it actually lands (transformed space)
                                                if (showClipRectsDebug) {
                                                    drawRect(Color.Green.copy(alpha = 0.35f), Offset(charBox.left, charBox.top), Size(charBox.width, charBox.height))
                                                }
                                                drawContext.canvas.restore()
                                            }
                                                drawContext.canvas.restore()
                                            }
                                            drawContext.canvas.restore()
                                        } else {
                                            // Short word: original character-by-character bounce
                                            val wordLen = word.text.length
                                            val activeCharIdxInWord = (linearProgress * wordLen).toInt().coerceAtMost(wordLen - 1)
                                            val charLp = ((linearProgress * wordLen) - activeCharIdxInWord).coerceIn(0f, 1f)
                                            
                                            val activeCharGlobalIdx = (word.charStartIndex + activeCharIdxInWord).coerceIn(0, displayedText.length.coerceAtLeast(1) - 1)
                                            val charBox = layout.getBoundingBox(activeCharGlobalIdx)
                                            
                                            val path = getFastPathForRange(layout, wStartIdx, activeCharGlobalIdx - 1)
                                            drawContext.canvas.save()
                                            drawContext.canvas.clipPath(path)
                                            this@drawWithContent.drawContent()
                                            drawContext.canvas.restore()
                                            
                                            val ease = { t: Float -> t * t * (3f - 2f * t) }
                                            val bounceFactor = when {
                                                charLp < 0.4f -> ease(charLp / 0.4f)
                                                charLp > 0.6f -> ease((1f - charLp) / 0.4f)
                                                else -> 1f
                                            }
                                            val charBounceY = -bounceFactor * with(density) { 0.8.dp.toPx() }
                                            val scaleFactor = 1f + (bounceFactor * 0.05f)
                                            
                                            drawContext.canvas.save()
                                            val pivotX = charBox.left + charBox.width / 2f
                                            val pivotY = charBox.bottom
                                            drawContext.canvas.translate(pivotX, pivotY + charBounceY)
                                            drawContext.canvas.scale(scaleFactor, scaleFactor)
                                            drawContext.canvas.translate(-pivotX, -pivotY)
                                            
                                            drawContext.canvas.save()
                                            drawContext.canvas.clipRect(Rect(charBox.left, charBox.top, charBox.right, charBox.bottom))
                                            this@drawWithContent.drawContent()
                                            // [DEBUG] active-char clip where it actually lands (transformed space)
                                            if (showClipRectsDebug) {
                                                drawRect(Color.Green.copy(alpha = 0.35f), Offset(charBox.left, charBox.top), Size(charBox.width, charBox.height))
                                            }
                                            drawContext.canvas.restore()
                                            
                                            drawContext.canvas.restore()
                                        }
                                    }
                                }
                                }
                            }
                        )
                    }
                } else if (line.words.isNotEmpty() && !isActiveLine) {
                    // Inactive line with word timings — check if past or future
                    val isPastLine = currentPositionMs > (line.words.maxOfOrNull { it.endMs } ?: line.timeMs)

                    BasicText(
                        text = displayedText,
                        style = TextStyle(
                            fontSize = textSize,
                            textAlign = lineTextAlign,
                            lineHeight = textSize * KARAOKE_LINE_HEIGHT_MULTIPLIER,
                            color = if (isPastLine) lineAccent.copy(alpha = 0.5f) else lineInactive,
                            fontWeight = if (isPastLine) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                } else {
                    // Fallback: no word timings (instrumental breaks, etc.)
                    BasicText(
                        text = displayedText,
                        style = TextStyle(
                            fontSize = textSize,
                            textAlign = lineTextAlign,
                            lineHeight = textSize * KARAOKE_LINE_HEIGHT_MULTIPLIER,
                            color = if (isActiveLine) lineAccent else lineInactive,
                            fontWeight = if (isActiveLine) FontWeight.ExtraBold else FontWeight.Medium,
                            shadow = if (isActiveLine) Shadow(
                                color = lineAccent.copy(alpha = 0.2f),
                                offset = Offset.Zero,
                                blurRadius = 8f
                            ) else null
                        )
                    )
                }
            }
        }
        val gapWindow = gapWindows[index]
        if (showIntervalIndicator && gapWindow != null) {
            val (gapStart, gapEnd) = gapWindow
            item(key = "gap_$index", contentType = 2) {
                val isVisible = currentPositionMs in gapStart until gapEnd
                LyricsIntervalIndicator(
                    gapStartMs = gapStart,
                    gapEndMs = gapEnd,
                    currentPositionMs = currentPositionMs,
                    visible = isVisible,
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        }
        item(key = "footer", contentType = 2) {
            Spacer(modifier = Modifier.height(with(LocalConfiguration.current) { screenHeightDp.dp }))
        }
    }
}
}
