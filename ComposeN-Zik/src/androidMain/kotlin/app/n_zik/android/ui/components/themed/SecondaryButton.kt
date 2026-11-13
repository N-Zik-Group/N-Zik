package app.n_zik.android.ui.components.themed

import app.n_zik.android.uiRoundnessShape

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.primaryButton
import app.n_zik.android.colorPalette

@Composable
fun SecondaryCircleButton(
    onClick: () -> Unit,
    @DrawableRes iconId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(uiRoundnessShape())
            .clickable(enabled = enabled, onClick = onClick)
            .background(colorPalette().primaryButton)
            .size(36.dp)
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp)
        )
    }
}

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    @DrawableRes iconId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            //.clip(uiRoundnessShape())
            .clickable(enabled = enabled, onClick = onClick)
            //.background(colorPalette.primaryButton)
            .size(36.dp)
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp)
        )
    }
}






