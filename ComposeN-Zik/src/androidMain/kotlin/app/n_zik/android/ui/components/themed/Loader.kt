package app.n_zik.android.ui.components.themed

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.FullPageLoader

@Composable
fun Loader(
    size: Dp = 100.dp,
    modifier: Modifier = Modifier.fillMaxWidth()
) = FullPageLoader(size = size, modifier = modifier)


