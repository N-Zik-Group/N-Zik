package app.n_zik.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.primaryButton
import app.n_zik.android.utils.medium
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

@Composable
fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alternative: Boolean = false
) {
    BasicText(
        text = text,
        style = typography().xxs.medium,
        modifier = modifier
            .clip(uiRoundnessShape())
            .clip(uiRoundnessShape()).clickable(enabled = enabled, onClick = onClick)
            .background(if (alternative) colorPalette().background0 else colorPalette().primaryButton)
            .padding(all = 8.dp)
            .padding(horizontal = 8.dp)

    )
}






