package app.it.fast4x.rimusic.ui.components.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.bold
import app.n_zik.android.colorPalette
import app.n_zik.android.typography

@Composable
private fun Title( titleId: Int ) {
    val fontStyle = TabHeader.style()

    Text(
        text = stringResource( titleId ),
        style = TextStyle(
            fontSize = fontStyle.fontSize,
            fontWeight = fontStyle.fontWeight,
            color = colorPalette().text,
            textAlign = TextAlign.Start
        ),
        modifier = Modifier.padding(start = Dp.Hairline, end = 12.dp)
    )
}

@Composable
private fun RiMusicHeader( titleId: Int, additionalContent: @Composable () -> Unit ) {
    Title( titleId )
    additionalContent()
}

interface TabHeader {

    companion object {

        @Composable
        fun style(): TextStyle =
            typography().xl.bold

        @Composable
        fun height(): Dp = style().fontSize.value.dp + 14.dp
    }
}

/** Displays the application tab header. */
@Composable
fun TabHeader(
    titleId: Int,
    additionalContent: @Composable () -> Unit
) {

    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        RiMusicHeader(titleId, additionalContent)
    }
}


