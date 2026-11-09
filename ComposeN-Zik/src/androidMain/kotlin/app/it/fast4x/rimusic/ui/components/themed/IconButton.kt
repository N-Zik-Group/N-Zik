package app.it.fast4x.rimusic.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.components.glass.glassGlobalIndication
import app.n_zik.android.components.ui.DownloadIconButton
import app.n_zik.android.components.ui.nativeControlIndication
import app.n_zik.android.components.ui.reservedIconActionCanvas
import app.n_zik.android.uiRoundnessShape

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
    onLongClick: (() -> Unit)? = null,
    placementModifier: Modifier = Modifier,
) {
    if (GlassToolbarIcon(painterResource(icon), color, iconSize ?: 18.dp, enabled,
            modifier = placementModifier.then(modifier), modifierOnContainer = true,
            onClick = onClick, onLongClick = onLongClick)) return
    IconButton(
        icon = icon,
        color = color,
        onClick = onClick,
        enabled = enabled,
        indication = indication,
        onLongClick = onLongClick,
        placementModifier = placementModifier,
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
    onLongClick: (() -> Unit)? = null,
    placementModifier: Modifier = Modifier,
) {
    if (icon == R.drawable.download || icon == R.drawable.downloaded || icon == R.drawable.download_progress) {
        DownloadIconButton(
            onClick = onClick,
            icon = icon,
            color = color,
            modifier = modifier,
            enabled = enabled,
            indication = indication,
            onLongClick = onLongClick,
            placementModifier = placementModifier,
        )
        return
    }
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.reservedIconActionCanvas(
                shape = uiRoundnessShape(),
                indication = glassGlobalIndication(indication ?: nativeControlIndication()),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                placementModifier = placementModifier,
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
    onLongClick: (() -> Unit)? = null,
    placementModifier: Modifier = Modifier,
) {
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.reservedIconActionCanvas(
                shape = uiRoundnessShape(),
                indication = glassGlobalIndication(indication ?: nativeControlIndication()),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                placementModifier = placementModifier,
            )
    )
}
