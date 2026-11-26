package app.n_zik.android.components.menu

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

internal fun Modifier.menuEntryClick(
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): Modifier {
    if (onClick == null && onLongClick == null) return this
    return combinedClickable(enabled = enabled, onClick = onClick ?: {}, onLongClick = onLongClick)
}

internal fun Modifier.menuToggleSemantics(checked: Boolean): Modifier = semantics {
    role = Role.Switch
    toggleableState = ToggleableState(checked)
}
