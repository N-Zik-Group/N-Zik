package app.n_zik.android.components.player.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R

/**
 * Animated circular wavy progress indicator shown during silent gaps between synced lyrics lines.
 *
 * @param currentPositionMs Current playback position in ms.
 * @param visible Whether the gap is currently active (position inside the gap window).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsIntervalIndicator(
    gapStartMs: Long,
    gapEndMs: Long,
    currentPositionMs: Long,
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val alpha = remember { Animatable(0f) }
    val rowHeightFraction = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            rowHeightFraction.animateTo(1f, tween(200))
            alpha.animateTo(1f, tween(200))
        } else {
            alpha.animateTo(0f, tween(200))
            rowHeightFraction.animateTo(0f, tween(200))
        }
    }

    val targetHeightDp = 72.dp

    val progress = if (gapEndMs > gapStartMs) {
        ((currentPositionMs - gapStartMs).toFloat() / (gapEndMs - gapStartMs).toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = stringResource(R.string.txt_lyricsintervalprogress)
    )

    Box(
        modifier = modifier
            .height(targetHeightDp * rowHeightFraction.value)
            .padding(top = 16.dp * rowHeightFraction.value)
            .graphicsLayer {
                this.alpha = alpha.value
                this.clip = true
            },
        contentAlignment = Alignment.Center
    ) {
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .size(36.dp)
                .alpha(alpha.value),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}
