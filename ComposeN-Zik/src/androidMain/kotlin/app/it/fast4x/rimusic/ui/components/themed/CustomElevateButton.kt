package app.it.fast4x.rimusic.ui.components.themed

import app.n_zik.android.components.glass.glassGlobalIndication

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.doubleShadowDrop
import app.n_zik.android.components.player.playerTransportFeedback
import app.n_zik.android.components.player.playerTransportIndication
import app.n_zik.android.uiRoundnessShape
import androidx.compose.material3.ripple

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomElevatedButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = uiRoundnessShape(),
    glassTransport: Boolean = false,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interSource = remember { MutableInteractionSource() }
    val isPressed by interSource.collectIsPressedAsState()

    // Animate shadow offset and blur radius
    // To create a hide shadow animation on press
    val shadowOffset by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 4.dp, label = ""
    )
    val shadowBlur by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 8.dp, label = ""
    )

    Box(
        modifier = Modifier
            .then(if (glassTransport) Modifier.playerTransportFeedback(interSource, shape, enabled = enabled) else Modifier)
            .then(modifier)
            // Apply shadow effect
            .doubleShadowDrop(shape, shadowOffset, shadowBlur)
            //.background(Color(0xFF010203), shape)
            .background(backgroundColor, shape)
            .clip(shape)
            .let {
                if (onLongClick != null) {
                    it.combinedClickable(
                        enabled = enabled,
                        interactionSource = interSource,
                        indication = if (glassTransport) playerTransportIndication(ripple()) else glassGlobalIndication(ripple()),
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    it.clickable(
                        enabled = enabled,
                        interactionSource = interSource,
                        indication = if (glassTransport) playerTransportIndication(ripple()) else glassGlobalIndication(ripple()),
                        onClick = onClick
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}







