package app.n_zik.android.components.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape

/** Settings-style accent badge centered in the caller's existing leading-icon slot. */
@Composable
fun LeadingIconBadge(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = colorPalette().accent
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Keep the 32dp Settings badge without moving labels or enlarging its host.
        Box(
            modifier = Modifier
                .requiredSize(32.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .iconBadgeBackground(accent.copy(alpha = 0.1f), uiRoundnessShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
