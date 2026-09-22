/**
 * Project (C) 2026 "Wrapped" feature.
 * Original licensed under GPL-3.0 | See git history for contributors.
 * Ported into  Rewind feature.
 */

package app.n_zik.android.components.ui.screens.rewind.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle

/**
 * Single-line text that shrinks its font size until it fits its width,
 * used for big headline numbers on Rewind slides.
 */
@Composable
fun AutoResizingRewindText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle
) {
    var scaledTextStyle by remember(text) { mutableStateOf(style) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        style = scaledTextStyle,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth) {
                scaledTextStyle =
                    scaledTextStyle.copy(fontSize = scaledTextStyle.fontSize * 0.9)
            } else {
                readyToDraw = true
            }
        }
    )
}

