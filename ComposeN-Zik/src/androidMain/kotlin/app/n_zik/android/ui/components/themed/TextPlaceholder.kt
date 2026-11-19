package app.n_zik.android.ui.components.themed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.shimmerEffect
import kotlin.random.Random

@Composable
fun TextPlaceholder(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(remember { 0.25f + Random.nextFloat() * 0.5f })
            .height(16.dp)
            .clip(uiRoundnessShape())
            .shimmerEffect()
    )
}

@Composable
internal fun TextPlaceholders(count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(count) { TextPlaceholder() }
    }
}

@Composable
internal fun MeasuredMetadataPlaceholder(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val layout = remember(text, style, width, maxLines, overflow, measurer) {
            measurer.measure(text, style = style, maxLines = maxLines, overflow = overflow, constraints = Constraints(maxWidth = width))
        }
        Column {
            repeat(layout.lineCount) { line ->
                val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
                val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
                with(density) {
                    Spacer(Modifier.width(lineWidth.coerceAtLeast(0f).toDp())
                        .height(lineHeight.toDp()).padding(vertical = 2.dp).clip(uiRoundnessShape()).shimmerEffect())
                }
            }
        }
    }
}

/** A skeleton line with the same text metrics as its loaded card. */
@Composable
internal fun ItemTextPlaceholder(
    text: String?,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    width: Float = 0.6f,
    maxLines: Int = 1,
) {
    if (text == null) {
        androidx.compose.foundation.text.BasicText(
            text = "",
            style = style,
            maxLines = 1,
            modifier = modifier.fillMaxWidth(width).clip(uiRoundnessShape()).shimmerEffect(),
        )
    } else {
        MeasuredMetadataPlaceholder(text, style, modifier, maxLines = maxLines)
    }
}
