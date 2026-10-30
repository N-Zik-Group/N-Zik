package app.n_zik.android.components.navigation.nav

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection

/** Bottom joins the screen edge; round only the upper corners, including in landscape. */
internal fun bottomNavigationBarShape(userShape: Shape): Shape = if (userShape is RoundedCornerShape) {
    RoundedCornerShape(
        topStart = userShape.topStart,
        topEnd = userShape.topEnd,
        bottomEnd = CornerSize(0),
        bottomStart = CornerSize(0),
    )
} else userShape

/** The Top bar joins the header; only its two exposed lower corners follow UI roundness. */
internal fun topNavigationBarShape(userShape: Shape): Shape = if (userShape is RoundedCornerShape) {
    RoundedCornerShape(
        topStart = CornerSize(0),
        topEnd = CornerSize(0),
        bottomEnd = userShape.bottomEnd,
        bottomStart = userShape.bottomStart,
    )
} else userShape

/** Side panels join the screen square; only the two corners facing content are rounded. */
internal fun sideNavigationBarShape(
    userShape: Shape,
    onRight: Boolean,
    direction: LayoutDirection,
): Shape {
    if (userShape !is RoundedCornerShape) return userShape
    val roundStart = onRight == (direction == LayoutDirection.Ltr)
    return RoundedCornerShape(
        topStart = if (roundStart) userShape.topStart else CornerSize(0),
        topEnd = if (roundStart) CornerSize(0) else userShape.topEnd,
        bottomStart = if (roundStart) userShape.bottomStart else CornerSize(0),
        bottomEnd = if (roundStart) CornerSize(0) else userShape.bottomEnd,
    )
}
