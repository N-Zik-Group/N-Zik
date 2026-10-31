// Adapted from Square's DampedDragAnimation / Kyant0 Backdrop catalog (Apache-2.0).
// Reference commit b18eb0ff12c616546a68c72e7d0097f1ab286c87; see ../glass/LICENSE-backdrop.txt.
package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock

/** Presentation only: these springs never dispatch a seek or change the native track. */
@Stable
class PlayerSeekGlassMotion(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val timeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val visibilityThreshold = .0005f
    private val initialScale = 14f / 36f
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, .001f)
    private val scaleXAnimationSpec = spring(.6f, 250f, .001f)
    private val scaleYAnimationSpec = spring(.7f, 250f, .001f)
    private val valueAnimation = Animatable(initialValue.coerceIn(0f, 1f), visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, .001f)
    private val scaleXAnimation = Animatable(initialScale, .001f)
    private val scaleYAnimation = Animatable(initialScale, .001f)
    private val velocityTracker = VelocityTracker()
    private var releaseJob: Job? = null

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value
    val opticalScaleX: Float get() = scaleX / (1f - (velocity / 10f * .75f).coerceIn(-.2f, .2f))
    val opticalScaleY: Float get() = scaleY * (1f - (velocity / 10f * .25f).coerceIn(-.2f, .2f))

    fun press() {
        // A new native gesture supersedes a pending release from its predecessor.
        releaseJob?.cancel()
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYAnimationSpec) }
        }
    }

    fun release() {
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            // Same next-frame barrier as Square's awaitFrame, using Compose's lifecycle clock.
            withFrameNanos { }
            if (value != targetValue) {
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < .025f }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        animationScope.launch {
            launch {
                valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() }
            }
        }
    }

    suspend fun reset(value: Float) {
        releaseJob?.cancel()
        velocityTracker.resetTracking()
        valueAnimation.snapTo(if (value.isFinite()) value.coerceIn(0f, 1f) else 0f)
        velocityAnimation.snapTo(0f)
        pressProgressAnimation.snapTo(0f)
        scaleXAnimation.snapTo(initialScale)
        scaleYAnimation.snapTo(initialScale)
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(timeMillis(), Offset(value, 0f))
        // Values already use Square's normalized 0..1 range, so the range divisor is one.
        val targetVelocity = velocityTracker.calculateVelocity().x
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}
