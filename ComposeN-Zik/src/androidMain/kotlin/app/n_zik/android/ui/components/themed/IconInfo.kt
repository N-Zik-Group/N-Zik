package app.n_zik.android.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.typography

@Composable
fun IconInfo (
    title: String?,
    icon: Painter,
    spacer: Dp = 4.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Row (
        modifier = modifier.fillMaxWidth()
    ) {
        Image(
            painter = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier
                .size(iconSize)
        )
        Spacer(modifier = Modifier.padding(horizontal = 2.dp))
        val textStyle = TextStyle(
            color = colorPalette().text,
            fontStyle = typography().l.fontStyle
        )
        if (title == null) {
            ItemTextPlaceholder(text = null, style = textStyle, width = 0.4f)
        } else {
            BasicText(
                text = title,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

    }
}


