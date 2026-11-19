package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.uiRoundnessShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.innertube.Innertube
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.glassMoodCardBackground
import app.n_zik.android.typography
import app.n_zik.android.utils.semiBold
import app.n_zik.android.thumbnailShape
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.ui.components.themed.TextPlaceholder
import app.n_zik.android.utils.shimmerEffect

@Composable
fun MoodGridItemColored(
    mood: Innertube.Mood.Item?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSizeDp: Dp,
    shimmer: Boolean = false,
) {
    
    val moodColor = mood?.let { Color(it.stripeColor) } ?: colorPalette().background2

    Column (
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .size(thumbnailSizeDp,thumbnailSizeDp)
            .padding(5.dp)
            .clip(uiRoundnessShape()).clickable(enabled = !shimmer && mood != null, onClick = onClick)

    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .clip(thumbnailShape())
                .glassMoodCardBackground(
                    backgroundColor = colorPalette().background0,
                    cardColor = colorPalette().background4,
                    shape = thumbnailShape(),
                )

        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(Modifier.width(10.dp).fillMaxHeight().then(if (shimmer) Modifier.shimmerEffect() else Modifier.background(moodColor)))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {

                    val style = TextStyle(
                        color = colorPalette().text,
                        fontStyle = typography().xs.semiBold.fontStyle,
                        fontWeight = typography().xs.semiBold.fontWeight,
                    )
                    val textModifier = Modifier.padding(horizontal = 10.dp).align(Alignment.CenterStart)
                    if (shimmer && mood != null) {
                        MeasuredMetadataPlaceholder(mood.title, style, textModifier, maxLines = 2)
                    } else if (shimmer) {
                        TextPlaceholder(textModifier)
                    } else if (mood != null) {
                        BasicText(mood.title, style = style, modifier = textModifier, maxLines = 2)
                    }
                }
            }
        }
    }
}
