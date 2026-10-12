package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Generic content dialog on the shared [DialogFrame]: a vertically
 * scrolling body (no title, no built-in buttons — the content decides).
 */
@Composable
inline fun DefaultDialog(
    noinline onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    crossinline content: @Composable ColumnScope.() -> Unit
) {
    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier,
        horizontalAlignment = horizontalAlignment
    ) {
        content()
    }
}
