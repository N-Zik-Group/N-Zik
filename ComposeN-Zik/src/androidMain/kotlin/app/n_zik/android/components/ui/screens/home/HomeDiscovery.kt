package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.uiRoundnessShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.innertube.Innertube
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.thumbnailShape

@Composable
fun MoodGridItemColored(
    mood: Innertube.Mood.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSizeDp: Dp
) {
    
    val moodColor by remember { derivedStateOf { Color(mood.stripeColor) } }

    Column (
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .size(thumbnailSizeDp,thumbnailSizeDp)
            .padding(5.dp)
            .clip(uiRoundnessShape()).clickable { onClick() }

    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .clip(thumbnailShape())

        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(color = moodColor)
                    .padding(start = 10.dp)
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .background(color = colorPalette().background4)
                        .fillMaxSize()
                ) {

                    BasicText(
                        text = mood.title,
                        style = TextStyle(
                            color = colorPalette().text,
                            fontStyle = typography().xs.semiBold.fontStyle,
                            fontWeight = typography().xs.semiBold.fontWeight
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp)
                            .align(Alignment.CenterStart),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
