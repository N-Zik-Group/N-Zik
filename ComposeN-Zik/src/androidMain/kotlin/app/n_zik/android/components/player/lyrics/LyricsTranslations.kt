package app.n_zik.android.components.player.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.R
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers

@Composable
internal fun <T> rememberLyricsTranslations(
    lines: List<T>,
    showSecondLine: Boolean,
    translateEnabled: Boolean,
    romanizationEnabled: Boolean,
    languageDestination: Language,
    translator: Translator,
    logTag: String,
    textOf: (T) -> String,
): Map<Int, String> {
    val translationCache = remember(lines, showSecondLine, translateEnabled, romanizationEnabled, languageDestination) {
        mutableStateMapOf<Int, String>()
    }

    LaunchedEffect(lines, showSecondLine, translateEnabled, romanizationEnabled, languageDestination) {
        if (!showSecondLine && !translateEnabled && !romanizationEnabled) return@LaunchedEffect

        val linesToTranslate = mutableListOf<Pair<Int, String>>()
        lines.forEachIndexed { index, line ->
            val trimmed = textOf(line).trim()
            if (trimmed.isEmpty()) {
                translationCache[index] = trimmed
            } else if (!translationCache.containsKey(index)) {
                linesToTranslate.add(index to trimmed)
            }
        }

        if (linesToTranslate.isEmpty()) return@LaunchedEffect

        if (translateEnabled) {
            withContext(NzikDispatchers.UI) {
                Toaster.i(R.string.lyrics_translation_in_progress_message)
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
                        Toaster.s(R.string.lyrics_translation_success_message)
                    }
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e("sync translation error: ${e.message}")
                if (translateEnabled) {
                    withContext(NzikDispatchers.UI) {
                        Toaster.e(R.string.lyrics_translation_failed_message)
                    }
                }
            }
        }
    }
    return translationCache
}
