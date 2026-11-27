package app.n_zik.android.components.menu

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun MenuMetadataText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Crossfade(targetState = text, modifier = modifier, animationSpec = tween(180), label = "MenuMetadataText") { value ->
        Box(if (value == text) Modifier else Modifier.clearAndSetSemantics {}) {
            BasicText(
                text = value,
                style = style,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
    }
}
