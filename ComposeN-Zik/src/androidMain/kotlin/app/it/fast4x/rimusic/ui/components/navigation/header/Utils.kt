package app.it.fast4x.rimusic.ui.components.navigation.header

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.colorPalette

@Composable
internal fun HeaderIcon(
    iconId: Int,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(uiRoundnessShape())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource( iconId ),
            contentDescription = null,
            modifier = Modifier.size( size ),
            tint = tint
        )
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
            // the palette's own text is always readable on its backgrounds, including
            // PitchBlack, whose override forces text = Color.White.
            colorPalette().text
    }
}
