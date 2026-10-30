// Backdrop catalog, Apache-2.0: https://github.com/Kyant0/AndroidLiquidGlass
// Source commit b18eb0ff12c616546a68c72e7d0097f1ab286c87; see LICENSE-backdrop.txt.
// Square adaptations: GPL-3.0, see NOTICE-square.txt.

package app.n_zik.android.components.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import app.n_zik.android.components.glass.backdrop.RuntimeShader
import app.n_zik.android.components.glass.backdrop.asComposeShader
import app.n_zik.android.components.glass.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val releaseProgressAnimationSpec =
        spring(1f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    internal fun drawHighlight(scope: DrawScope) {
        with(scope) {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                if (shader != null) {
                    drawRect(
                        Color.White.copy(0.08f * progress),
                        blendMode = BlendMode.Plus
                    )
                    shader.apply {
                        val position = position(size, positionAnimation.value)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.15f * progress))
                        setFloatUniform("radius", size.minDimension * 1.5f)
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(
                        ShaderBrush(shader.asComposeShader()),
                        blendMode = BlendMode.Plus
                    )
                } else {
                    drawRect(
                        Color.White.copy(0.08f * progress),
                        blendMode = BlendMode.Plus
                    )
                    val center = position(size, positionAnimation.value)
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to Color.White.copy(0.15f * progress),
                            0.5f to Color.White.copy(0.15f * progress),
                            1f to Color.Transparent,
                            center = Offset(center.x.fastCoerceIn(0f, size.width), center.y.fastCoerceIn(0f, size.height)),
                            radius = size.minDimension * 1.5f,
                        ),
                        blendMode = BlendMode.Plus
                    )
                }
            }

        }
    }

    val modifier: Modifier = Modifier.drawWithContent {
        drawHighlight(this)
        drawContent()
    }

    internal fun startPress(position: Offset) {
        startPosition = position
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { positionAnimation.snapTo(startPosition) }
        }
    }

    internal fun releasePress() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, releaseProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }

    internal fun movePress(position: Offset) {
        animationScope.launch { positionAnimation.snapTo(position) }
    }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                observeConsumedMovement = true,
                onDragStart = { down ->
                    startPress(down.position)
                },
                onDragEnd = { releasePress() },
                onDragCancel = { releasePress() },
            ) { change, _ ->
                movePress(change.position)
            }
        }
}
