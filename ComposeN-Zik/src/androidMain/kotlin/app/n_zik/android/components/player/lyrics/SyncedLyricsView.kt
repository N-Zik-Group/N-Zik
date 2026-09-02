package app.n_zik.android.components.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.enums.lyrics.LyricsBackground
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.R

import app.n_zik.android.components.player.lyrics.utils.SynchronizedLyrics
import app.it.fast4x.rimusic.utils.verticalFadingEdge
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import it.fast4x.lrclib.LrcLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.components.player.lyrics.utils.HtmlDecoder
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CancellationException

/** Minimum silence duration (ms) between two lines required to show the interval indicator. */
private const val GAP_THRESHOLD_MS = 4000L

/** Vertical spacing (dp) for header/footer in lyrics views. */
private val LYRICS_SPACING = 24.dp

private data class SyncedSentence(
    val timeMs: Long,
    val text: String,
    val agent: String? = null,
    val isBackground: Boolean = false
)

private val agentTagRegex = Regex("\\{agent:([^}]*)\\}")
private val bgTagRegex = Regex("\\{bg\\}")

private fun parseSyncedSentences(lrcText: String): List<SyncedSentence> {
    val rawSentences = LrcLib.Lyrics(lrcText).sentences
    return rawSentences.map { (time, line) ->
        val agentMatch = agentTagRegex.find(line)
        val agent = agentMatch?.groupValues?.get(1)
        val isBg = bgTagRegex.containsMatchIn(line)
        val cleanText = line.replace(agentTagRegex, "").replace(bgTagRegex, "").trim()
        SyncedSentence(time, cleanText, agent, isBg)
    }
}

@Composable
fun SyncedLyricsView(
    text: String,
    currentPositionProvider: () -> Long,
    onSeekTo: (Long) -> Unit,
    showlyricsthumbnail: Boolean,
    isLandscape: Boolean,
    trailingContent: (@Composable () -> Unit)?,
    showBackgroundLyrics: Boolean,
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
    lyricsAlignment: LyricsAlignment,
    lyricsSizeAnimate: Boolean,
    lyricsColor: LyricsColor,
    lyricsCustomColor: Int,
    dominantColor: Int,
    lyricsHighlight: LyricsHighlight = LyricsHighlight.None,
    isAutoScrollEnabled: Boolean = true,
    onAutoScrollEnabledChange: (Boolean) -> Unit = {},
    clickLyricsText: Boolean,
    @Suppress("UNUSED_PARAMETER") // Kept for API compatibility; centering now uses viewportHeight
    thumbnailSize: Dp,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    onInvalidLrc: (Boolean) -> Unit,
    showIntervalIndicator: Boolean = true,
    karaokeRespectAgentPosition: Boolean = true
) {
    val density = LocalDensity.current
    val syncedSentences = remember(text) {
        val decodedText = HtmlDecoder.decodeHtmlEntities(text)
        val sentences = parseSyncedSentences(decodedText)
        if (sentences.isEmpty()) onInvalidLrc(true) else onInvalidLrc(false)
        sentences
    }
    val synchronizedLyrics = remember(syncedSentences) {
        val pairs = syncedSentences.map { it.timeMs to it.text }
        SynchronizedLyrics(pairs) { currentPositionProvider() + 50L }
    }

    // Pre-compute gap windows: for each sentence index, the gap to the next sentence (if > threshold)
    // Structure: Map<lineIndex, Pair<gapStartMs, gapEndMs>>
    val gapWindows = remember(syncedSentences) {
        buildMap {
            syncedSentences.forEachIndexed { index, sentence ->
                val startMs = sentence.timeMs
                val nextStartMs = if (index < syncedSentences.size - 1) syncedSentences[index + 1].timeMs else startMs + 10000L
                val sentenceText = sentence.text.trim()
                
                if (sentenceText.isBlank()) {
                    var currentEnd = startMs
                    val prevSentence = if (index > 0) syncedSentences[index - 1] else null
                    if (prevSentence != null && prevSentence.text.isNotBlank()) {
                        val prevStartMs = prevSentence.timeMs
                        val prevText = prevSentence.text.trim()
                        
                        // Estimate end of singing: ~120ms per character + 500ms trailing
                        val estimatedDuration = (prevText.length * 120L) + 500L
                        // Max end is 2 seconds before the next line starts, so we guarantee a 2s gap if possible
                        val maxEstimatedEnd = nextStartMs - 2000L
                        
                        currentEnd = (prevStartMs + estimatedDuration)
                            .coerceAtMost(maxEstimatedEnd)
                            // Minimum 1 second after the previous line started
                            .coerceAtLeast(prevStartMs + 1000L)
                            // We can even start the gap before the blank line's official timestamp!
                            .coerceAtMost(startMs)
                    }

                    if (currentEnd < nextStartMs) {
                        val gap = nextStartMs - currentEnd
                        if (gap > 2000L) {
                            put(index, Pair(currentEnd, nextStartMs - 650L))
                        }
                    }
                }
            }
        }
    }

    // Live playback position for the interval indicator
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            currentPositionMs = currentPositionProvider()
            delay(100)
        }
    }

    // --- Translation cache ---
    // Keyed on options that affect translation output; survives scroll without re-fetching.
    val translationCache = remember(text, showSecondLine, translateEnabled, romanizationEnabled, languageDestination) {
        mutableStateMapOf<Int, String>()
    }

    // Pre-compute translations for every sentence in the background, once per key change.
    LaunchedEffect(text, showSecondLine, translateEnabled, romanizationEnabled, languageDestination) {
        if (!showSecondLine && !translateEnabled && !romanizationEnabled) return@LaunchedEffect

        val linesToTranslate = mutableListOf<Pair<Int, String>>()
        syncedSentences.forEachIndexed { index, sentence ->
            val trimmed = sentence.text.trim()
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
                // Join all lines to translate in one go
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
                Timber.tag("SyncedLyricsView").e("sync translation error: ${e.message}")
                if (translateEnabled) {
                    withContext(Dispatchers.Main) {
                        Toaster.e(R.string.translation_failed)
                    }
                }
            }
        }
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

    val currentText = syncedSentences
        .getOrNull(synchronizedLyrics.index)?.text ?: ""
    val translationText = translationCache[synchronizedLyrics.index] ?: currentText
    val trueLineCount = translationText.lines().size.coerceIn(1, 5)

    val fontSizeIndex = when (fontSize) {
        LyricsFontSize.Light -> 0
        LyricsFontSize.Medium -> 1
        LyricsFontSize.Heavy -> 2
        LyricsFontSize.Large -> 3
        else -> 4
    }
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
    val lineMultiplier = when (lineCount) {
        1 -> 0.45f
        2 -> 0.46f
        3 -> 0.48f
        4 -> 0.40f
        else -> 0.35f
    }
    val multiplier = if (showlyricsthumbnail) lineMultiplier else 0.42f
    val fixedCenter = (effectiveVpH * multiplier).toInt()

    LaunchedEffect(synchronizedLyrics, density, isAutoScrollEnabled, vpH) {
        synchronizedLyrics.update()
        
        if (isAutoScrollEnabled) {
            var reMeasuredVpH = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
            if (reMeasuredVpH == 0) {
                delay(100)
                reMeasuredVpH = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
            }
            val finalEffectiveVpH = if (reMeasuredVpH > 0) reMeasuredVpH else screenHeightPx
            val finalFixedCenter = (finalEffectiveVpH * multiplier).toInt()
            
            try {
                val targetIndex = synchronizedLyrics.index + 1
                lazyListState.scrollToItem(targetIndex, -finalFixedCenter)
            } catch (e: CancellationException) {
                if (!isActive) throw e
            }
        }

        while (isActive) {
            delay(50)
            if (!synchronizedLyrics.update()) continue
            if (isAutoScrollEnabled) {
                var reMeasuredVpH = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                val finalEffectiveVpH = if (reMeasuredVpH > 0) reMeasuredVpH else screenHeightPx
                val finalFixedCenter = (finalEffectiveVpH * multiplier).toInt()
                
                try {
                    val targetIndex = synchronizedLyrics.index + 1
                    val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                    if (itemInfo != null) {
                        val currentPos = itemInfo.offset + itemInfo.size / 2
                        val offset = currentPos - finalFixedCenter
                        if (kotlin.math.abs(offset) > 10) {
                            lazyListState.animateScrollBy(
                                value = offset.toFloat(),
                                animationSpec = tween(durationMillis = 800)
                            )
                        }
                    } else {
                        lazyListState.scrollToItem(targetIndex, -finalFixedCenter)
                    }
                } catch (e: CancellationException) {
                    if (!isActive) throw e
                }
            }
        }
    }

    // (vpH-based re-scroll removed - caused double animations)

    var modifierBG = Modifier.verticalFadingEdge()
if (showBackgroundLyrics && showlyricsthumbnail) modifierBG =
        modifierBG.background(colorPalette().accent)

    val accentColor = when (lyricsColor) {
        LyricsColor.White -> Color.White
        LyricsColor.Cover -> Color(dominantColor)
        LyricsColor.Custom -> Color(lyricsCustomColor)
        LyricsColor.Thememode -> if (showlyricsthumbnail) PureBlackColorPalette.text else colorPalette().text
    }

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
                Spacer(modifier = Modifier.height(thumbnailSize))
            }

            itemsIndexed(
                items = syncedSentences,
                key = { index, _ -> index },
                contentType = { _, _ -> "lyric_line" }
            ) { index, sentence ->
                val trimmedSentence = sentence.text.trim()

                // Read from cache — no network call here
                val displayText = if (showSecondLine || translateEnabled || romanizationEnabled) {
                    translationCache[index] ?: trimmedSentence
                } else {
                    trimmedSentence
                }

                val hasGapIndicator = showIntervalIndicator && gapWindows.containsKey(index)

                // Agent-based alignment: v1=Left, v2=Right, bg/v1000=Center
                val effectiveAlignment = when {
                    karaokeRespectAgentPosition && sentence.agent == "v1" -> LyricsAlignment.Left
                    karaokeRespectAgentPosition && sentence.agent == "v2" -> LyricsAlignment.Right
                    karaokeRespectAgentPosition && sentence.agent == "v1000" -> LyricsAlignment.Center
                    karaokeRespectAgentPosition && sentence.isBackground -> LyricsAlignment.Center
                    else -> lyricsAlignment
                }
                val lineAlignment = when (effectiveAlignment) {
                    LyricsAlignment.Left -> Alignment.CenterStart
                    LyricsAlignment.Center -> Alignment.Center
                    LyricsAlignment.Right -> Alignment.CenterEnd
                }

                if (!hasGapIndicator) {
                    Box(
                        contentAlignment = lineAlignment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LyricsTextPainter(
                            text = displayText,
                            isSync = true,
                            isCurrentIndex = index == synchronizedLyrics.index,
                            showlyricsthumbnail = showlyricsthumbnail,
                            lyricsOutline = lyricsOutline,
                            colorPaletteMode = colorPaletteMode,
                            fontSize = fontSize,
                            customSize = customSize,
                            lyricsAlignment = effectiveAlignment,
                            lyricsSizeAnimate = lyricsSizeAnimate,
                            lyricsColor = lyricsColor,
                            lyricsCustomColor = lyricsCustomColor,
                            dominantColor = dominantColor,
                            lyricsHighlight = lyricsHighlight,
                            clickLyricsText = clickLyricsText,
                            onClick = {
                                if (clickLyricsText) onSeekTo(sentence.timeMs) else onDismiss()
                            }
                        )
                    }
                }

                // Interval indicator after this line (if there is a gap and the feature is enabled)
                if (showIntervalIndicator) {
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

            item(key = "footer", contentType = 2) {
                Spacer(modifier = Modifier.height(thumbnailSize))
            }
        }
    }
}
