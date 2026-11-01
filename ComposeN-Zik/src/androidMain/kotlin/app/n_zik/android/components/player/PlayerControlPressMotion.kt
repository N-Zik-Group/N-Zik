package app.n_zik.android.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.it.fast4x.rimusic.utils.buttonzoomoutKey
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/** Scale the surface and its contents from the existing clickable's press lifecycle. */
@Composable
internal fun Modifier.playerControlPressMotion(source: InteractionSource, enabled: Boolean = true): Modifier {
    val shrinkEnabled by rememberPreference(buttonzoomoutKey, true)
    var pressed by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        var active: PressInteraction.Press? = null
        source.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> { active = interaction; pressed = true }
                is PressInteraction.Release -> if (active === interaction.press) {
                    // Clickables may deliver down/up together after an ancestor's touch delay.
                    delay(100)
                    pressed = false
                    active = null
                }
                is PressInteraction.Cancel -> if (active === interaction.press) {
                    pressed = false
                    active = null
                }
            }
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (enabled && shrinkEnabled && pressed) 0.8f else 1f,
        label = "playerControlPress",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
