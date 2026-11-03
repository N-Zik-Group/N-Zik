package app.n_zik.android.components.ui.toggles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.exactUiRoundnessShape
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.mode
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.isGlassMotionAllowed
import app.n_zik.android.components.glass.GlassSwitch
import app.n_zik.android.components.glass.rememberGlassSettings

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    checkedThumbColor: Color = colorPalette().onAccent,
    checkedTrackColor: Color = colorPalette().accent,
    uncheckedThumbColor: Color = colorPalette().textSecondary,
    uncheckedTrackColor: Color = colorPalette().textSecondary.copy(alpha = 0.3f),
    enabled: Boolean = true
) {
    val settings = rememberGlassSettings()
    if (settings.enabled && settings.mode(GlassArea.Switches) != GlassAreaMode.Disabled) {
        GlassSwitch(checked, onCheckedChange, modifier, enabled, settings.forArea(GlassArea.Switches))
        return
    }
    val gesture = rememberSwitchGestureState(checked, enabled)
    val interactions = remember { MutableInteractionSource() }
    val position = remember { Animatable(if (checked) 1f else 0f) }
    val motion = isGlassMotionAllowed()
    val target = if (gesture.dragging) gesture.position else if (checked) 1f else 0f
    LaunchedEffect(target, gesture.dragging, motion) {
        if (gesture.dragging || !motion) position.snapTo(target) else position.animateTo(target, tween(150))
    }
    val input = Modifier.switchInput(checked, onCheckedChange, enabled, interactions, gesture,
        position.value, 20.dp)
    val shape = exactUiRoundnessShape()
    val indication = LocalIndication.current
    val feedback = if (onCheckedChange != null) Modifier.indication(interactions, indication) else Modifier
    val trackColor by animateColorAsState(if (checked) checkedTrackColor else uncheckedTrackColor)
    val thumbColor by animateColorAsState(if (checked) checkedThumbColor else uncheckedThumbColor)

    Box(
        modifier = modifier.then(input)
            .size(width = 52.dp, height = 32.dp)
            .background(trackColor, shape = shape)
            .glassAreaContour(settings, GlassArea.Switches, shape)
            .clip(shape)
            .then(feedback),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (8f + 16f * position.value).dp)
                .size((16f + 8f * position.value).dp)
                .background(thumbColor, shape = shape)
        )
    }
}
