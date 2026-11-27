package app.n_zik.android.components.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import app.n_zik.android.R
import app.n_zik.android.components.glass.LocalGlassOverlayPaintScope

private const val DRAG_HANDLE_TOP_PADDING = 18
private const val DRAG_HANDLE_BOTTOM_PADDING = 6
private const val DRAG_HANDLE_WIDTH = 40
private const val DRAG_HANDLE_HEIGHT = 4
private const val DRAG_HANDLE_CORNER_RADIUS = 2

internal class MenuSheetHandleActions(val overlayId: Any, val modifier: Modifier)

internal val LocalMenuSheetHandleActions = compositionLocalOf<MenuSheetHandleActions?> { null }

/** Shared appearance for visible menu handles in both native and shared hosts. */
@Composable
internal fun MenuSheetDragHandle(
    color: Color = Color.White,
    minTouchHeight: Dp = 0.dp,
    centeredVertically: Boolean = false,
) {
    val centeredPadding = (DRAG_HANDLE_TOP_PADDING + DRAG_HANDLE_BOTTOM_PADDING).dp / 2
    val topPadding = if (centeredVertically) centeredPadding else DRAG_HANDLE_TOP_PADDING.dp
    val bottomPadding = if (centeredVertically) centeredPadding else DRAG_HANDLE_BOTTOM_PADDING.dp
    // A nested window can inherit the locals of the sheet that opened it.
    val overlayId = LocalGlassOverlayPaintScope.current?.id
    val actions = LocalMenuSheetHandleActions.current?.takeIf { it.overlayId === overlayId }
    Box(Modifier.fillMaxWidth().then(actions?.modifier ?: Modifier), contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(min = minTouchHeight)
                .padding(top = topPadding, bottom = bottomPadding),
            contentAlignment = Alignment.Center,
        ) {
            MenuDragHandleBar(color)
        }
    }
}

/** Reuses the same bar inside a queue's existing draggable header. */
@Composable
internal fun MenuDragHandleBar(color: Color = Color.White) {
    Box(Modifier.size(width = DRAG_HANDLE_WIDTH.dp, height = DRAG_HANDLE_HEIGHT.dp)
        .clip(RoundedCornerShape(DRAG_HANDLE_CORNER_RADIUS.dp))
        .background(color))
}

/** Centers custom handles independently of the menu content's alignment. */
@Composable
internal fun CenteredMenuSheetDragHandle(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        content()
    }
}

/** Retains native sheet actions when the handle belongs to the moving material container. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeMenuSheetDragHandle(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun dismissSheet() {
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) onDismissRequest()
        }
    }
    Box(
        Modifier.fillMaxWidth().then(menuSheetHandleActions(sheetState, ::dismissSheet, clickable = true)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun menuSheetHandleActions(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    clickable: Boolean = false,
): Modifier {
    val scope = rememberCoroutineScope()
    val canExpand = sheetState.currentValue == SheetValue.PartiallyExpanded && sheetState.hasExpandedState
    val canCollapse = sheetState.currentValue == SheetValue.Expanded && sheetState.hasPartiallyExpandedState
    val clickLabel = stringResource(if (canExpand) R.string.settings_transition_expand else R.string.common_action_close)
    val expandSheet: () -> Unit = { scope.launch { sheetState.expand() } }
    val defaultAction = if (canExpand) expandSheet else onDismiss
    val actions = Modifier.semantics(mergeDescendants = true) {
        dismiss { onDismiss(); true }
        if (canExpand) expand { expandSheet(); true }
        if (canCollapse) collapse { scope.launch { sheetState.partialExpand() }; true }
        if (!clickable) {
            role = Role.Button
            onClick(clickLabel) { defaultAction(); true }
        }
    }
    return if (clickable) actions.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = defaultAction)
    else actions
}
