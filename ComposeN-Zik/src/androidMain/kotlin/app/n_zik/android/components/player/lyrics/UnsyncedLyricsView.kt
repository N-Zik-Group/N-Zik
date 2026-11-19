package app.n_zik.android.components.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
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
import app.n_zik.android.components.glass.LocalSurfaceBackdropColor
import app.n_zik.android.components.glass.measuredPlayerTextForeground
import app.n_zik.android.components.player.LocalPlayerBackdropSampling

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
    onDismiss: () -> Unit,
    showShimmer: Boolean = false,
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

    val scrim = if (isDisplayed && !showlyricsthumbnail) when (lyricsBackground) {
        LyricsBackground.Black -> Color.Black.copy(alpha = .4f)
        LyricsBackground.White -> Color.White.copy(alpha = .4f)
        else -> Color.Transparent
    } else Color.Transparent
    val coverInk = if (lyricsColor == LyricsColor.Cover) {
        // Even the brightest cover is dark under the on-cover scene scrim.
        val underlay = if (showlyricsthumbnail) LyricsCoverScrim.compositeOver(Color.White)
            else LocalSurfaceBackdropColor.current ?: LocalPlayerBackdropSampling.current?.foregroundSurface
        val highlight = if (!showlyricsthumbnail) when (lyricsHighlight) {
            LyricsHighlight.Black -> Color.Black.copy(alpha = .5f)
            LyricsHighlight.White -> Color.White.copy(alpha = .5f)
            else -> Color.Transparent
        } else Color.Transparent
        underlay?.let {
            val surface = highlight.compositeOver(scrim.compositeOver(it))
            measuredPlayerTextForeground(Color(dominantColor), surface).toArgb()
        } ?: dominantColor
    } else dominantColor

    Column(
        modifier = Modifier
            .verticalFadingEdge()
            .background(scrim),
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
                dominantColor = coverInk,
                lyricsHighlight = lyricsHighlight,
                clickLyricsText = clickLyricsText,
                showShimmer = showShimmer,
                onClick = onDismiss
            )
        }
    }
}

