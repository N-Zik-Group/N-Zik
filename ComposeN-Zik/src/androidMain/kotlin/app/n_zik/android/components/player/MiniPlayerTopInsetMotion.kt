package app.n_zik.android.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import app.n_zik.android.core.navigation.navigationChromeTween

/** The caller reads this state during placement, keeping animation frames out of screen composition. */
@Composable
internal fun rememberAnimatedMiniPlayerTopInset(targetInsetPx: Float): State<Float> = animateFloatAsState(
    targetValue = targetInsetPx,
    animationSpec = navigationChromeTween(),
    label = "playerTopInset",
)
