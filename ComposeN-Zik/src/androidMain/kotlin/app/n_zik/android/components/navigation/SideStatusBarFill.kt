package app.n_zik.android.components.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** Keeps the Android status strip opaque while content scrolls behind a side rail. */
internal fun Modifier.sideStatusBarFill(
    visible: Boolean,
    heightPx: Float,
    leftInsetPx: Float,
    rightInsetPx: Float,
    color: Color,
): Modifier = if (!visible || heightPx <= 0f) this else drawWithContent {
    drawContent()
    val left = leftInsetPx.coerceIn(0f, size.width)
    val right = rightInsetPx.coerceIn(0f, size.width - left)
    drawRect(color, Offset(left, 0f), Size(size.width - left - right, heightPx.coerceAtMost(size.height)))
}
