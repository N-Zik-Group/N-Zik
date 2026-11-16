package app.n_zik.android.components.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.CustomModalBottomSheet
import app.n_zik.android.components.glass.GlassMenuSheet
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.canHostGlassMenuSheet
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.ui.styling.LocalAppearance

/** Reads the animated background in the modifier's composition scope. */
fun Modifier.appearanceBackground(): Modifier = composed {
    background(LocalAppearance.current.colorPalette.background0)
}

/** Keeps menu sheet shape reads separate from the application content scope. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceMenuSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    expandOnShow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = rememberGlassSettings()
    val host = LocalGlassMenuHost.current
    val sameWindow = host?.dialogs?.windowRoot === LocalView.current.rootView
    if (canHostGlassMenuSheet(glass, glassRenderer(glass), sameWindow && host?.backdrop != null)) {
        GlassMenuSheet(showSheet, onDismissRequest, sheetState, expandOnShow, content)
        return
    }
    CustomModalBottomSheet(
        showSheet = showSheet,
        onDismissRequest = onDismissRequest,
        containerColor = Color.Transparent,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        expandOnShow = expandOnShow,
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
