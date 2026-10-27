package app.n_zik.android.components.dialog.common

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Shared dialog with a scrolling body and optional title and fixed action footer. */
@Composable
inline fun DefaultDialog(
    noinline onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    title: String? = null,
    noinline footer: (@Composable ColumnScope.() -> Unit)? = null,
    crossinline content: @Composable ColumnScope.() -> Unit
) {
    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        title = title,
        bodyPadding = PaddingValues(0.dp),
        footer = footer
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            content()
        }
    }
}
