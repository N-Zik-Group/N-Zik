package app.n_zik.android.components.navigation.header

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassActionIconSize
import app.n_zik.android.components.glass.GlassStandaloneActionSize
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.R
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.glass.LiquidButton
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.glassMenuTrigger
import androidx.compose.runtime.SideEffect
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.rememberGlassSettings

@Composable
internal fun HeaderIcon(
    iconId: Int,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    menuTrigger: GlassMenuTriggerState? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val glass = rememberGlassSettings()
    val icon: @Composable () -> Unit = {
        Icon(
            imageVector = ImageVector.vectorResource(iconId),
            contentDescription = stringResource(when (iconId) {
                R.drawable.search -> R.string.search
                R.drawable.chevron_back -> R.string.glass_back
                else -> R.string.menu
            }),
            modifier = Modifier.size(if (glass.usesGlassMaterial(GlassArea.Header)) GlassActionIconSize else size),
            tint = tint,
        )
    }
    if (glass.usesGlassMaterial(GlassArea.Header)) {
        LiquidButton(
            onClick = onClick,
            backdrop = LocalGlassBackdrop.current,
            settings = glass.forArea(GlassArea.Header),
            filmColor = surfaceColor(colorPalette().background1),
            modifier = modifier.size(GlassStandaloneActionSize),
            contentHeight = GlassStandaloneActionSize,
            contentPadding = 0.dp,
            menuTrigger = menuTrigger,
        ) { icon() }
    } else {
        val shape = uiRoundnessShape()
        SideEffect {
            menuTrigger?.content = icon
            menuTrigger?.shape = shape
        }
        Box(
            modifier = modifier.size(48.dp).glassMenuTrigger(menuTrigger).clip(shape)
                .glassAreaContour(glass, GlassArea.Header, shape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { icon() }
    }
}

internal class Preference {

    internal companion object {

        @Composable
        fun parentalControl(): Boolean =
            rememberPreference( parentalControlEnabledKey, false ).value
    }
}

internal class AppBar {

    internal companion object {

        @Composable
        fun contentColor(): Color =
            // Keyed on the effective tone, not the mode (spec-achromatic-ramp-luminance-cap):
            // the palette's own text is always readable on its backgrounds,

            // including PitchBlack, whose override forces text = Color.White.
            colorPalette().text
    }
}
