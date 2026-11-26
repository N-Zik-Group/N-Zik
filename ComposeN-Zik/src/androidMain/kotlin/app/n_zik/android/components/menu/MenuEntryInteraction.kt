package app.n_zik.android.components.menu

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import app.n_zik.android.components.ui.PressFeedbackHorizontalInset
import app.n_zik.android.components.ui.insetPressIndication

internal val LocalListMenuRowOutset = staticCompositionLocalOf { 0.dp }

/** Let the menu entry paint into the existing sheet gutter while preserving its content position. */
internal fun Modifier.listMenuRowOutset(outset: Dp): Modifier =
    if (outset == 0.dp) this else layout { measurable, constraints ->
        val side = outset.roundToPx()
        val child = measurable.measure(constraints.offset(horizontal = side * 2))
        layout(constraints.constrainWidth(child.width - side * 2), child.height) {
            child.placeRelative(-side, 0)
        }
    }

@Composable
internal fun Modifier.menuSectionHorizontalAlignment(): Modifier {
    val outset = LocalListMenuRowOutset.current
    return if (outset > 0.dp) offset(x = -outset) else padding(horizontal = PressFeedbackHorizontalInset)
}

@Composable
internal fun Modifier.menuEntryClick(
    shape: Shape,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    insetFeedback: Boolean = true,
): Modifier {
    if (onClick == null && onLongClick == null) return this
    return combinedClickable(
        interactionSource = null,
        indication = if (insetFeedback) insetPressIndication(shape) else LocalIndication.current,
        enabled = enabled,
        onClick = onClick ?: {},
        onLongClick = onLongClick,
    )
}

internal fun Modifier.menuToggleSemantics(checked: Boolean): Modifier = semantics {
    role = Role.Switch
    toggleableState = ToggleableState(checked)
}
