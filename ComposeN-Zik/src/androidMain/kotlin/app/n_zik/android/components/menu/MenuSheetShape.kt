package app.n_zik.android.components.menu

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Shape

/** Keeps the configured upper corners while making the menu meet its bottom edge. */
internal fun straightBottomMenuShape(shape: Shape): Shape =
    if (shape is CornerBasedShape) shape.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))
    else shape
