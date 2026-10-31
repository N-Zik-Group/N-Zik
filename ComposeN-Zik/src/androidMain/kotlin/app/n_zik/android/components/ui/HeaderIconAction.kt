package app.n_zik.android.components.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.components.glass.NativeSectionIconAction

/** Header glyphs keep their size inside the shared native action canvas or Glass toolbar. */
@Composable
internal fun HeaderIconAction(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    if (GlassToolbarIcon(painterResource(icon), color, iconSize, enabled = true,
            modifier = modifier, modifierOnContainer = true, onClick = onClick,
            onLongClick = onLongClick, contentDescription = contentDescription)) return
    NativeSectionIconAction(icon, contentDescription, modifier = modifier, iconSize = iconSize,
        color = color, onClick = onClick, onLongClick = onLongClick)
}
