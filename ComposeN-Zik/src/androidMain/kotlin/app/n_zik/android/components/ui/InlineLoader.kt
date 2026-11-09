package app.n_zik.android.components.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette

/** App loader for cards and dialogs without the full-page navigation spacer. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InlineLoader(modifier: Modifier = Modifier) {
    LoadingIndicator(color = colorPalette().accent, modifier = modifier.size(100.dp))
}
