package app.n_zik.android.components.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.components.navigation.header.HeaderNativeActionIconSize

/** Artwork actions use the same Normal and Glass geometry as other section headers. */
@Composable
internal fun HeaderIconAction(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = HeaderNativeActionIconSize,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    GlassSectionActions(
        actions = listOf(
            GlassSectionAction(
                icon = icon,
                contentDescription = contentDescription,
                nativeIconSize = iconSize,
                nativeColor = color,
                glassColor = color,
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
            ),
        ),
        modifier = modifier,
    )
}
