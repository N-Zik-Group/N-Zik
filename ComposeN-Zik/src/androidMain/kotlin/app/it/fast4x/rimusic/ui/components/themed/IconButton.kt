package app.it.fast4x.rimusic.ui.components.themed

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import app.n_zik.android.components.glass.GlassToolbarIcon

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    iconSize: Dp? = 20.dp,
    onLongClick: (() -> Unit)? = null
) {
    if (GlassToolbarIcon(painterResource(icon), color, iconSize ?: 18.dp, enabled,
            onClick = onClick, onLongClick = onLongClick)) return
    IconButton(
        icon = icon,
        color = color,
        onClick = onClick,
        enabled = enabled,
        indication = indication,
        onLongClick = onLongClick,
        modifier = modifier
            .padding(all = 2.dp)
            .size(iconSize ?: 18.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    onLongClick: (() -> Unit)? = null
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier
            .clip(uiRoundnessShape())
            .combinedClickable(
                indication = indication ?: ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    onLongClick: (() -> Unit)? = null
) {
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier
            .clip(uiRoundnessShape())
            .combinedClickable(
                indication = indication ?: ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}
