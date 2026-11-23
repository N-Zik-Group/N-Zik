package app.n_zik.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.Float.Companion.POSITIVE_INFINITY

@Composable
internal fun BoxScope.PlayerFullscreenArtworkVeil(
    playerGradientStartY: Float,
    blurredVeilEnd: Color,
    blurredVeilBase: Color,
) {
    Column(modifier = Modifier
        .matchParentSize()
        .background(
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                1.0f to blurredVeilEnd,
                startY = playerGradientStartY,
                endY = POSITIVE_INFINITY
            )
        )
        .background(blurredVeilBase)) {}
}
