package app.n_zik.android.components.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import app.n_zik.android.components.glass.glassGlobalIndication
import app.n_zik.android.uiRoundnessShape

/** Draws the complete download badge above its independently clipped press feedback. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    onLongClick: (() -> Unit)? = null,
    placementModifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = glassGlobalIndication(indication ?: nativeControlIndication())
    Box(
        modifier = placementModifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        // The badge reaches the glyph corners; only the feedback canvas may be clipped.
        Box(Modifier.matchParentSize().clip(uiRoundnessShape()).indication(interactionSource, feedback))
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(color),
            modifier = modifier,
        )
    }
}
