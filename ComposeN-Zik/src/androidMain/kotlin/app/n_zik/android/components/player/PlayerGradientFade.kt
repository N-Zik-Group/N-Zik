package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.enums.AnimatedGradient
import kotlinx.coroutines.flow.collect

internal class PlayerGradientFade(initial: AnimatedGradient) {
    var target by mutableStateOf(initial)
        private set
    var outgoing by mutableStateOf<AnimatedGradient?>(null)
        private set
    val alpha = Animatable(1f)

    suspend fun show(next: AnimatedGradient, animate: Boolean) {
        if (!animate) {
            target = next
            outgoing = null
            alpha.snapTo(1f)
            return
        }
        if (outgoing != null) finish()
        if (next != target) {
            alpha.snapTo(0f)
            outgoing = target
            target = next
            finish()
        }
    }

    private suspend fun finish() {
        alpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        outgoing = null
    }
}

/** Existing renderer washes, evaluated at the displayed light/dark transition fraction. */
internal fun playerShaderWash(type: AnimatedGradient, darkness: Float): Color = when (type) {
    AnimatedGradient.GlossyGradients, AnimatedGradient.GradientFlow -> Color.Black.copy(alpha = .2f * darkness)
    AnimatedGradient.InkFlow, AnimatedGradient.OilFlow -> Color.White.copy(alpha = .4f * (1f - darkness))
    AnimatedGradient.IceReflection -> lerp(Color.White.copy(alpha = .4f), Color.Black.copy(alpha = .3f), darkness)
    AnimatedGradient.Stage -> Color.Black.copy(alpha = .3f * darkness)
    AnimatedGradient.GoldenMagma -> lerp(Color.White.copy(alpha = .3f), Color.Black.copy(alpha = .2f), darkness)
    AnimatedGradient.BlackCherryCosmos -> Color.White.copy(alpha = .35f * (1f - darkness))
    else -> Color.Transparent
}

@Composable
internal fun rememberPlayerGradientFade(target: AnimatedGradient, active: Boolean): PlayerGradientFade {
    val fade = remember { PlayerGradientFade(target) }
    val latestTarget by rememberUpdatedState(target)
    LaunchedEffect(fade, active) {
        // Finish the visible transition before taking the latest request. Keeps at most two
        // renderers mounted, even on rapid skips or when the gradient setting is retargeted.
        snapshotFlow { latestTarget }.collect { fade.show(it, animate = active) }
    }
    return fade
}

@Composable
internal fun PlayerGradientLayers(
    fade: PlayerGradientFade,
    modifier: Modifier,
    renderer: @Composable (AnimatedGradient, Boolean) -> Modifier,
) {
    val outgoing = fade.outgoing
    val target = fade.target
    val pairLayer = if (outgoing != null) Modifier.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    } else Modifier
    Box(modifier.then(pairLayer)) {
        listOfNotNull(outgoing, target).forEach { gradient ->
            key(gradient) {
                val incoming = gradient == target
                val opacity = if (outgoing != null) Modifier.graphicsLayer {
                    val progress = fade.alpha.value
                    alpha = if (incoming) progress else 1f - progress
                    // Add the weighted premultiplied layers. This also fades into the
                    // transparent fallback on APIs without RuntimeShader support.
                    blendMode = if (incoming) BlendMode.Plus else BlendMode.SrcOver
                    compositingStrategy = CompositingStrategy.Offscreen
                } else Modifier
                Box(Modifier.matchParentSize().then(opacity).then(renderer(gradient, incoming)))
            }
        }
    }
}
