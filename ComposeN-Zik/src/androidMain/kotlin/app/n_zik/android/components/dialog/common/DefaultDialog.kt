package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        // The frame body itself stays non-scrollable (canon); tall content
        // scrolls inside this column instead of being clipped by the frame's
        // 80 % height cap (the legacy DefaultDialog was scrollable).
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            content()
        }
    }
}
