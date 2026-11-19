package app.n_zik.android.components.player.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.shimmerEffect

@Composable
internal fun lyricsReadingFontSize(size: LyricsFontSize, customSize: Float): TextUnit = when (size) {
    LyricsFontSize.Light -> typography().m.fontSize
    LyricsFontSize.Medium -> typography().l.fontSize
    LyricsFontSize.Heavy -> typography().xl.fontSize
    LyricsFontSize.Large -> typography().xlxl.fontSize
    else -> customSize.sp
}

@Composable
internal fun LyricsLoadingShimmer(
    type: LyricsType,
    fontSize: LyricsFontSize,
    customSize: Float,
    alignment: LyricsAlignment,
    modifier: Modifier = Modifier,
) {
    val textSize = lyricsReadingFontSize(fontSize, customSize)
    val horizontalAlignment = when (alignment) {
        LyricsAlignment.Left -> Alignment.Start
        LyricsAlignment.Center -> Alignment.CenterHorizontally
        LyricsAlignment.Right -> Alignment.End
    }
    val horizontalPadding = if (type == LyricsType.Unsynced) 64.dp else 32.dp
    val barHeight = with(LocalDensity.current) { (textSize.toDp() * 0.55f).coerceIn(6.dp, 14.dp) }
    val shape = uiRoundnessShape()
    Column(
        modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(0.62f, 0.84f, 0.69f, 0.43f).forEach { width ->
            Spacer(Modifier.fillMaxWidth(width).height(barHeight).clip(shape).shimmerEffect())
        }
    }
}

@Composable
internal fun LyricsTextShimmer(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val layout = remember(text, style, maxWidth, measurer) {
            measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth))
        }
        with(density) {
            Box(
                Modifier.size(layout.size.width.toDp(), layout.size.height.toDp()),
                contentAlignment = AbsoluteAlignment.TopLeft,
            ) {
                repeat(layout.lineCount) { line ->
                    val left = layout.getLineLeft(line)
                    val width = layout.getLineRight(line) - left
                    if (width > 0) {
                        val top = layout.getLineTop(line)
                        val height = layout.getLineBottom(line) - top
                        Spacer(
                            Modifier.absoluteOffset(x = left.toDp(), y = top.toDp())
                                .size(width.toDp(), height.toDp())
                                .padding(vertical = (height.toDp() - (style.fontSize.toDp() * 0.55f).coerceIn(6.dp, 14.dp)).coerceAtLeast(0.dp) / 2)
                                .clip(uiRoundnessShape())
                                .shimmerEffect()
                        )
                    }
                }
            }
        }
    }
}
