package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader

/** Reuses fluid gradient resources while their size and palette are unchanged. */
@Composable
fun rememberPlayerFluidShader(size: Size, start: Color, end: Color): Shader = remember(size, start, end) {
    LinearGradientShader(Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height),
        listOf(start, end), listOf(0f, 1f))
}
