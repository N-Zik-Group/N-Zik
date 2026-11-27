package app.n_zik.android.components.menu

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.blockGlassCoveredInput
import app.n_zik.android.components.ui.PressFeedbackHorizontalInset
import app.n_zik.android.components.ui.insetPressIndication

internal val LocalListMenuRowOutset = staticCompositionLocalOf { 0.dp }

/** Keeps outgoing pages visible during animation without exposing their actions. */
internal fun Modifier.menuPageInteraction(active: Boolean): Modifier =
    blockGlassCoveredInput(!active)
        .onPreviewKeyEvent { !active }
        .then(if (active) Modifier else Modifier.clearAndSetSemantics { hideFromAccessibility() })
        .focusProperties { onEnter = { if (!active) cancelFocusChange() } }
        .focusGroup()

@Composable
internal fun Modifier.menuSectionHeading(): Modifier {
    val outset = LocalListMenuRowOutset.current
    val headingModifier = semantics { heading() }
    return if (outset > 0.dp) headingModifier else headingModifier.padding(horizontal = PressFeedbackHorizontalInset)
}

@Composable
internal fun Modifier.menuEntryClick(
    shape: Shape,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    insetFeedback: Boolean = true,
    onLongClickLabel: String? = null,
): Modifier {
    if (onClick == null && onLongClick == null) return this
    return combinedClickable(
        interactionSource = null,
        indication = if (insetFeedback) insetPressIndication(shape) else LocalIndication.current,
        enabled = enabled,
        onClick = onClick ?: {},
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
    )
}

internal fun Modifier.menuToggleSemantics(checked: Boolean?): Modifier {
    if (checked == null) return this
    return semantics {
        role = Role.Switch
        toggleableState = ToggleableState(checked)
    }
}
