package app.n_zik.android.components.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import app.n_zik.android.R

private const val DRAG_HANDLE_TOP_PADDING = 18
private const val DRAG_HANDLE_BOTTOM_PADDING = 6
private const val DRAG_HANDLE_WIDTH = 40
private const val DRAG_HANDLE_HEIGHT = 4
private const val DRAG_HANDLE_CORNER_RADIUS = 2

/** Shared appearance for visible menu handles in both native and shared hosts. */
@Composable
internal fun MenuSheetDragHandle(color: Color = Color.White) {
    CenteredMenuSheetDragHandle {
        Box(Modifier.padding(top = DRAG_HANDLE_TOP_PADDING.dp, bottom = DRAG_HANDLE_BOTTOM_PADDING.dp)) {
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
    val partiallyExpanded = sheetState.currentValue == SheetValue.PartiallyExpanded
    val clickLabel = stringResource(if (partiallyExpanded) R.string.settings_transition_expand else R.string.common_action_close)
    fun dismissSheet() {
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) onDismissRequest()
        }
    }
    Box(
        Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {
                dismiss { dismissSheet(); true }
                if (partiallyExpanded && sheetState.hasExpandedState) {
                    expand { scope.launch { sheetState.expand() }; true }
                } else if (sheetState.currentValue == SheetValue.Expanded && sheetState.hasPartiallyExpandedState) {
                    collapse { scope.launch { sheetState.partialExpand() }; true }
                }
            }
            .clickable(role = Role.Button, onClickLabel = clickLabel) {
                if (partiallyExpanded) scope.launch { sheetState.expand() } else dismissSheet()
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
