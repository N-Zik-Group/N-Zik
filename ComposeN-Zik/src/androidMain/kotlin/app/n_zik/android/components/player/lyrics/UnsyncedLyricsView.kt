package app.n_zik.android.components.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.enums.lyrics.LyricsBackground
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline

import app.n_zik.android.utils.verticalFadingEdge
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import app.n_zik.android.components.player.lyrics.utils.HtmlDecoder

/** Vertical spacing (dp) for header/footer in lyrics views. */
private val LYRICS_SPACING = 24.dp

@Composable
fun UnsyncedLyricsView(
    text: String,
    showlyricsthumbnail: Boolean,
    isDisplayed: Boolean,
    showSecondLine: Boolean,
    translateEnabled: Boolean,
    romanizationEnabled: Boolean,
    languageDestination: Language,
    translator: Translator,
    lyricsBackground: LyricsBackground,
    lyricsOutline: LyricsOutline,
    colorPaletteMode: ColorPaletteMode,
    fontSize: LyricsFontSize,
    customSize: Float,
    lyricsAlignment: LyricsAlignment,
    lyricsColor: LyricsColor,
    lyricsCustomColor: Int,
    dominantColor: Int,
    lyricsHighlight: LyricsHighlight,
    @Suppress("UNUSED_PARAMETER") // Kept for API compatibility; centering now uses viewportHeight
    thumbnailSize: Dp,
    clickLyricsText: Boolean,
    onDismiss: () -> Unit
) {
    val decodedText = remember(text) { HtmlDecoder.decodeHtmlEntities(text) }
    var translatedText by remember { mutableStateOf("") }
    
    if (showSecondLine || translateEnabled || romanizationEnabled) {
        val mutState = remember(translateEnabled, romanizationEnabled, showSecondLine) { mutableStateOf("") }
        TranslateLyricsWithRomanization(
            output = mutState,
            textToTranslate = decodedText,
            isSync = false,
            showSecondLine = showSecondLine,
            romanizationEnabled = romanizationEnabled,
            translateEnabled = translateEnabled,
            translator = translator,
            onPlaceholderDismissed = {},
            destinationLanguage = languageDestination
        )
        translatedText = mutState.value.ifEmpty { decodedText }
    } else {
        translatedText = decodedText
    }

    Column(
        modifier = Modifier
            .verticalFadingEdge()
            .background(
                if (isDisplayed && !showlyricsthumbnail) if (lyricsBackground == LyricsBackground.Black) Color.Black.copy(
                    0.4f
                ) else if (lyricsBackground == LyricsBackground.White) Color.White.copy(
                    0.4f
                ) else Color.Transparent else Color.Transparent
            ),
    ) {
        Box(
            modifier = Modifier
                .verticalFadingEdge()
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(vertical = LYRICS_SPACING, horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            LyricsTextPainter(
                text = translatedText,
                isSync = false,
                isCurrentIndex = true, // Treat all text as "current" index for unsynced colors
                showlyricsthumbnail = showlyricsthumbnail,
                lyricsOutline = lyricsOutline,
                colorPaletteMode = colorPaletteMode,
                fontSize = fontSize,
                customSize = customSize,
                lyricsAlignment = lyricsAlignment,
                lyricsSizeAnimate = false, // Not used for unsynced
                lyricsColor = lyricsColor,
                lyricsCustomColor = lyricsCustomColor,
                dominantColor = dominantColor,
                lyricsHighlight = lyricsHighlight,
                clickLyricsText = clickLyricsText,
                onClick = onDismiss
            )
        }
    }
}

