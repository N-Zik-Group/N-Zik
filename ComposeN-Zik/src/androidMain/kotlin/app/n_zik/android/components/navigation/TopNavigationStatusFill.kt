package app.n_zik.android.components.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.components.navigation.header.headerScrollOpacity

/** Extend only a real top navigation surface into the status inset as the app header leaves. */
internal fun Modifier.topNavigationStatusFill(
    visible: Boolean,
    statusBarTopPx: Float,
    headerHeightPx: Float,
    color: Color,
    headerColor: Color? = null,
    horizontalStartInsetPx: Float = 0f,
    horizontalEndInsetPx: Float = 0f,
    headerOffsetPx: () -> Float,
): Modifier = if ((!visible && headerColor == null) || statusBarTopPx <= 0f || headerHeightPx <= 0f) this else drawWithContent {
    drawContent()
    val offset = headerOffsetPx()
    val progress = if (headerColor == null) (-offset / headerHeightPx).coerceIn(0f, 1f)
        else 1f - headerScrollOpacity(statusBarTopPx, headerHeightPx, offset)
    // One surface follows the header, then hands over to actual Top tabs if present.
    // With only a top mini-player, it clears completely once the header is hidden.
    // Transparent black changes RGB as well as alpha during Color.lerp. Keep the
    // surface's RGB at the transparent endpoint so this matches header layer alpha.
    val startColor = headerColor ?: color.copy(alpha = 0f)
    val endColor = if (visible) color else startColor.copy(alpha = 0f)
    val left = horizontalStartInsetPx.coerceIn(0f, size.width)
    val right = horizontalEndInsetPx.coerceIn(0f, size.width - left)
    drawRect(lerp(startColor, endColor, progress),
        topLeft = Offset(left, 0f),
        size = Size(size.width - left - right, statusBarTopPx.coerceAtMost(size.height)))
}
