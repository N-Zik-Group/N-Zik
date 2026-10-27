package app.n_zik.android.components.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.styling.LocalAppearance

/** Reads the animated background in the modifier's composition scope. */
fun Modifier.appearanceBackground(): Modifier = composed {
    background(LocalAppearance.current.colorPalette.background0)
}

/** Keeps video sheet color reads separate from the application content scope. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceVideoSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appearance = LocalAppearance.current
    val background = appearance.colorPalette.background0
    val shape = appearance.uiRoundnessShape
    CustomModalBottomSheet(
        showSheet = showSheet,
        onDismissRequest = onDismissRequest,
        containerColor = background,
        contentColor = background,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = background,
                shape = shape,
            ) {}
        },
        shape = bottomSheetShape(shape),
        content = content,
    )
}

/** Keeps menu sheet shape reads separate from the application content scope. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceMenuSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    CustomModalBottomSheet(
        showSheet = showSheet,
        onDismissRequest = onDismissRequest,
        containerColor = Color.Transparent,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = Color.Transparent,
            ) {}
        },
        shape = bottomSheetShape(LocalAppearance.current.uiRoundnessShape),
        content = content,
    )
}

private fun bottomSheetShape(shape: Shape): Shape = (shape as? RoundedCornerShape)?.let {
    RoundedCornerShape(
        topStart = it.topStart,
        topEnd = it.topEnd,
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )
} ?: shape
