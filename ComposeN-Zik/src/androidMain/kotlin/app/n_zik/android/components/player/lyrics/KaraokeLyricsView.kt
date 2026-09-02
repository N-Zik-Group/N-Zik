package app.n_zik.android.components.player.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import app.n_zik.android.enums.lyrics.LyricsBackground
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.verticalFadingEdge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.enums.lyrics.LyricsAlignment
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.withFrameMillis
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult

/** Vertical spacing (dp) for header/footer in lyrics views. */
private val LYRICS_SPACING = 24.dp

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
    showIntervalIndicator: Boolean = true
) {
    val density = LocalDensity.current

    val karaokeLines = remember(text) {
        val parsed = parseKaraokeLrc(text)
        if (parsed.isEmpty()) onInvalidLrc(true) else onInvalidLrc(false)
        parsed
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
    val translationCache = remember(text, showSecondLine, translateEnabled, romanizationEnabled, languageDestination) {
        mutableStateMapOf<Int, String>()
    }

    LaunchedEffect(text, showSecondLine, translateEnabled, romanizationEnabled, languageDestination) {
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
            withContext(Dispatchers.Main) {
                Toaster.i(R.string.translation_in_progress)
            }
        }

        withContext(Dispatchers.IO) {
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
                    withContext(Dispatchers.Main) {
                        Toaster.s(R.string.translation_successful)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("KaraokeLyricsView").e("sync translation error: ${e.message}")
                if (translateEnabled) {
                    withContext(Dispatchers.Main) {
                        Toaster.e(R.string.translation_failed)
                    }
                }
            }
        }
    }

    // Pre-compute gap windows for interval indicator
    val initialGapWindow = remember(karaokeLines) {
        val firstStart = karaokeLines.firstOrNull { !it.isBackground }?.timeMs ?: 0L
        if (firstStart > 3000L) Pair(0L, firstStart - 650L) else null
    }

    val gapWindows = remember(karaokeLines) {
        buildMap {
            karaokeLines.forEachIndexed { index, line ->
                if (line.isBackground) return@forEachIndexed
                val startMs = line.timeMs
                val nextStartMs = if (index < karaokeLines.size - 1) karaokeLines.drop(index + 1).firstOrNull { !it.isBackground }?.timeMs ?: (startMs + 10000L) else startMs + 10000L
                val lineEndMs = if (line.words.isNotEmpty()) line.words.maxOf { it.endMs } else line.timeMs + 2000L

                val gap = nextStartMs - lineEndMs
                if (gap > 2500L) {
                    put(index, Pair(lineEndMs, nextStartMs - 650L))
                }
            }
        }.toMutableMap().apply {
            // Remove overlapping gap windows to prevent double loaders in duets
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
    }

    // Determine active lines (multiple can be active for overlapping agents)
    val activeLineIndices = remember(currentPositionMs, karaokeLines) {
        val active = mutableSetOf<Int>()
        var lastPassedNonBgIndex = 0
        for (i in karaokeLines.indices) {
            val line = karaokeLines[i]
            if (line.timeMs > currentPositionMs + 50L) break
            if (!line.isBackground) lastPassedNonBgIndex = i

            // Determine line end time
            val lineEndMs = if (line.words.isNotEmpty()) {
                line.words.maxOf { it.endMs }
            } else {
                // Fallback: use next non-background line's start
                karaokeLines.getOrNull(i + 1)?.timeMs ?: Long.MAX_VALUE
            }

            if (currentPositionMs <= lineEndMs) {
                active.add(i)
            }
        }
        if (active.isEmpty()) {
            active.add(lastPassedNonBgIndex)
        }
        active
    }

    // Primary active line for scroll targeting - prefer non-background lines
    // Track max reached line, but reset when seeking backwards
    var maxReachedLineIndex by remember { mutableIntStateOf(0) }
    val primaryActiveIndex = remember(activeLineIndices, currentPositionMs) {
        val nonBgActive = activeLineIndices.filter { !karaokeLines[it].isBackground }
        val currentIndex = if (nonBgActive.isNotEmpty()) {
            nonBgActive.maxOrNull()!!
        } else {
            // No non-background active - find last passed non-background line
            var lastNonBg = 0
            for (i in karaokeLines.indices) {
                if (karaokeLines[i].timeMs > currentPositionMs) break
                if (!karaokeLines[i].isBackground) lastNonBg = i
            }
            lastNonBg
        }
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

    LaunchedEffect(primaryActiveIndex, density, isAutoScrollEnabled, vpH) {
        if (!isAutoScrollEnabled) return@LaunchedEffect
        if (primaryActiveIndex == 0 || vpH == 0) {
            delay(100)
        }
        val reMeasuredVpH = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
        val finalEffectiveVpH = if (reMeasuredVpH > 0) reMeasuredVpH else screenHeightPx
        val hasLoader = showIntervalIndicator && initialGapWindow != null
        var finalMultiplier = if (reMeasuredVpH > 0) multiplier else 0.45f
        if (reMeasuredVpH == 0 && hasLoader && primaryActiveIndex == 0) {
            finalMultiplier = 0.50f
        }
        val finalFixedCenter = (finalEffectiveVpH * finalMultiplier).toInt()
        Timber.tag("KaraokeLyricsView").d("CENTER: idx=${primaryActiveIndex+1} vpH=$reMeasuredVpH mult=$finalMultiplier center=$finalFixedCenter lines=$lineCount loader=$hasLoader")
        val scrollIndex = primaryActiveIndex + 1 + (if (hasLoader) 1 else 0)
        
        // Smooth scroll Metrolist-style: use animateScrollBy for fluid transitions
        val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == scrollIndex }
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
            lazyListState.scrollToItem(scrollIndex, -finalFixedCenter)
        }
    }

    // Resolve the accent color
    val accentColor = when (lyricsColor) {
        LyricsColor.White -> Color.White
        LyricsColor.Cover -> Color(dominantColor)
        LyricsColor.Custom -> Color(lyricsCustomColor)
        LyricsColor.Thememode -> if (showlyricsthumbnail) PureBlackColorPalette.text else colorPalette().text
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

        if (showIntervalIndicator && initialGapWindow != null) {
            item(key = "initial_loader", contentType = 2) {
                val isVisible = currentPositionMs in initialGapWindow.first until initialGapWindow.second
                LyricsIntervalIndicator(
                    gapStartMs = initialGapWindow.first,
                    gapEndMs = initialGapWindow.second,
                    currentPositionMs = currentPositionMs,
                    visible = isVisible,
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
        }

        itemsIndexed(karaokeLines, key = { index, _ -> index }, contentType = { _, _ -> "lyric_line" }) { index, line ->
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

            // Background vocals are smaller/dimmer
            val bgScale = if (line.isBackground) 0.85f else 1f
            val bgAlphaFactor = if (line.isBackground) 0.8f else 1f

            val animateOpacity by animateFloatAsState(
                targetValue = if (isActiveLine) 1f * bgAlphaFactor else 0.35f * bgAlphaFactor,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = ""
            )
            val animateScale by animateFloatAsState(
                targetValue = if (isActiveLine) 1.08f * bgScale
                              else 0.92f * bgScale,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = ""
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = animateOpacity
                        scaleX = animateScale
                        scaleY = animateScale
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = if (clickLyricsText) LocalIndication.current else null,
                        onClick = {
                            if (clickLyricsText) onSeekTo(line.timeMs) else onDismiss()
                        }
                    )
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

                if (line.words.isNotEmpty() && isActiveLine) {
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
                                    
                                    // Word itself - smooth fade for long active words
                                    val wordColor = if (isLongWord) {
                                        if (isActive) {
                                            val linearProgress = ((currentPositionMs - word.startMs).toFloat() / dur.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                            val fadeAlpha = (1f - linearProgress).coerceIn(0f, 1f)
                                            lineInactive.copy(alpha = fadeAlpha * lineInactive.alpha)
                                        } else if (isSung) {
                                            androidx.compose.ui.graphics.Color.Transparent
                                        } else {
                                            lineInactive
                                        }
                                    } else {
                                        lineInactive
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
                                textAlign = lineTextAlign
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
                                    textAlign = lineTextAlign
                                )
                            )
                        }

                        // Foreground (active) text with character-level clipping
                        BasicText(
                            text = displayedText,
                            style = TextStyle(
                                color = lineAccent,
                                fontSize = textSize,
                                fontWeight = FontWeight.Bold,
                                textAlign = lineTextAlign
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
                                            val fadeMs = 1200L
                                            val fadeProgress = ((currentPositionMs - word.endMs).toFloat() / fadeMs).coerceIn(0f, 1f)
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
                                                drawContext.canvas.restore()
                                            }
                                            drawContext.canvas.restore()
                                        } else {
                                            // Short word: simple clip (no wave)
                                            val path = getFastPathForRange(layout, wStartIdx, wEndIdx)
                                            drawContext.canvas.save()
                                            drawContext.canvas.clipPath(path)
                                            this@drawWithContent.drawContent()
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
                                            
                                            // Fill clip: reveals accent color left-to-right
                                            val fillRight = startBox.left + (endBox.right - startBox.left) * easedProgress
                                            drawContext.canvas.save()
                                            drawContext.canvas.clipRect(Rect(startBox.left - with(density) { 8.dp.toPx() }, startBox.top - waveAmplitude - riseAmount - with(density) { 8.dp.toPx() }, fillRight + with(density) { 8.dp.toPx() }, endBox.bottom + waveAmplitude + with(density) { 8.dp.toPx() }))
                                            
                                            // Draw each character with traveling wave
                                            for (charIdx in 0 until wordLen) {
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
                            lineHeight = textSize * 1.4f,
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
                            lineHeight = textSize * 1.4f,
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
                if (!line.isBackground && showIntervalIndicator) {
                    gapWindows[index]?.let { (gapStart, gapEnd) ->
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
        }
        item(key = "footer", contentType = 2) {
            Spacer(modifier = Modifier.height(with(LocalConfiguration.current) { screenHeightDp.dp }))
        }
    }
}
}
