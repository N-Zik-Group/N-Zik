package app.n_zik.android.components.menu

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.PressFeedbackHorizontalInset
import app.n_zik.android.components.ui.insetPressIndication

internal val LocalListMenuRowOutset = staticCompositionLocalOf { 0.dp }

@Composable
internal fun Modifier.menuSectionHorizontalAlignment(): Modifier {
    val outset = LocalListMenuRowOutset.current
    return if (outset > 0.dp) this else padding(horizontal = PressFeedbackHorizontalInset)
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
