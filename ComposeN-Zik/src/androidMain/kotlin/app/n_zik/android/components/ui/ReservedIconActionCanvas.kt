package app.n_zik.android.components.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ripple
import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/** Placement transforms move the entire canvas; reserved modifier padding belongs to its action. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.reservedIconActionCanvas(
    shape: Shape,
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    role: Role? = null,
    placementModifier: Modifier = Modifier,
): Modifier = placementModifier.clip(shape)
    .combinedClickable(interactionSource = interactionSource, indication = indication,
        enabled = enabled, onClick = onClick, onLongClick = onLongClick, role = role)
    .then(this)
    .clip(shape)

/** Resolve the end radius from the actual action canvas; the owner's UI shape clips its coverage. */
internal fun nativeControlIndication(): Indication = ripple(bounded = true)
