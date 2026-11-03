package app.n_zik.android.components.player.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R

import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun TranslateLyricsWithRomanization(
    output: MutableState<String>,
    textToTranslate: String,
    isSync: Boolean,
    showSecondLine: Boolean,
    romanizationEnabled: Boolean,
    translateEnabled: Boolean,
    translator: Translator,
    onPlaceholderDismissed: () -> Unit,
    destinationLanguage: Language = Language.AUTO
) {
    LaunchedEffect(showSecondLine, romanizationEnabled, textToTranslate, destinationLanguage, translateEnabled){
        if (translateEnabled && textToTranslate.isNotEmpty()) {
            withContext(NzikDispatchers.UI) {
                Toaster.i(R.string.lyrics_translation_in_progress_message)
            }
        }
        var destLanguage = destinationLanguage
        val result = withContext(NzikDispatchers.DATA) {
            try {
                /** used to find the source language of the text and detect CHINESE_TRADITIONAL*/
                val helperTranslation = translator.translate(
                    textToTranslate,
                    Language.CHINESE_TRADITIONAL,
                    Language.AUTO
                )
                if(destinationLanguage == Language.AUTO){
                    destLanguage = if(helperTranslation.translatedText == textToTranslate){
                        Language.CHINESE_TRADITIONAL
                    } else {
                        helperTranslation.sourceLanguage
                    }
                }
                val mainTranslation = translator.translate(
                    textToTranslate,
                    destLanguage,
                    Language.AUTO
                )
                // Clean up escaped quotes from JSON responses
                val cleanTranslatedText = mainTranslation.translatedText.replace("\\\"", "\"")
                val cleanPronunciation = mainTranslation.translatedPronunciation?.replace("\\\"", "\"")
                
                val helpPronLine = helperTranslation.sourcePronunciation?.trim() ?: ""
                val mainPronLine = mainTranslation.sourcePronunciation?.trim() ?: textToTranslate
                val transPronLine = cleanPronunciation ?: cleanTranslatedText

                val outputText = if (textToTranslate == "") {
                    ""
                } else if (!showSecondLine || (mainTranslation.sourceText == mainTranslation.translatedText)){
                    if (translateEnabled && romanizationEnabled) {
                        transPronLine
                    } else if (translateEnabled) {
                        cleanTranslatedText
                    } else if (romanizationEnabled) {
                        if (helperTranslation.sourceText == helperTranslation.translatedText)
                            helpPronLine
                        else
                            mainPronLine.ifEmpty { textToTranslate }
                    } else {
                        textToTranslate
                    }
                } else {
                    val originalLines = textToTranslate.split("\n")
                    
                    val translationOrPronLines = if (translateEnabled && romanizationEnabled) {
                        val pron = if (helperTranslation.sourceText == helperTranslation.translatedText) helpPronLine else mainPronLine.ifEmpty { textToTranslate }
                        val pronLines = pron.split("\n")
                        val transLines = transPronLine.split("\n")
                        // If they are different lengths, fallback to transPronLine
                        transLines // The user sees [translation] with showSecondLine ON
                    } else if (translateEnabled) {
                        cleanTranslatedText.split("\n")
                    } else if (romanizationEnabled) {
                        val pron = if (helperTranslation.sourceText == helperTranslation.translatedText) helpPronLine else mainPronLine.ifEmpty { textToTranslate }
                        pron.split("\n")
                    } else {
                        emptyList()
                    }

                    // Interleave the original and translated lines
                    val interleaved = StringBuilder()
                    val maxLines = maxOf(originalLines.size, translationOrPronLines.size)
                    for (i in 0 until maxLines) {
                        val origLine = originalLines.getOrNull(i)?.trim() ?: ""
                        val transLine = translationOrPronLines.getOrNull(i)?.trim() ?: ""
                        
                        if (origLine.isNotEmpty()) {
                            interleaved.append(origLine)
                        }
                        if (transLine.isNotEmpty()) {
                            if (origLine.isNotEmpty()) interleaved.append("\n")
                            interleaved.append("[$transLine]")
                        }
                        if (i < maxLines - 1) interleaved.append("\n")
                    }
                    interleaved.toString()
                }
                outputText?.replace("\\r","\r")?.replace("\\n","\n")
            } catch (e: Exception) {
                if(isSync){
                    Timber.tag("LyricsTranslator").e("sync translation ${e.stackTraceToString()}")
                } else {
                    Timber.tag("LyricsTranslator").e("not sync translation ${e.stackTraceToString()}")
                }
            }
        }
        val translatedText = if (result.toString() == "kotlin.Unit") "" else result.toString()
        onPlaceholderDismissed()
        output.value = translatedText
        
        withContext(NzikDispatchers.UI) {
            if (textToTranslate.isNotEmpty()) {
                if (translatedText.isNotEmpty() && translateEnabled) {
                    Toaster.s(R.string.lyrics_translation_success_message)
                } else if (translateEnabled) {
                    Toaster.e(R.string.lyrics_translation_failed_message)
                }
            }
        }
    }
}
