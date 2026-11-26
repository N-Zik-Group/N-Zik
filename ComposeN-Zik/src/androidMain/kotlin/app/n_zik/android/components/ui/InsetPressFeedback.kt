package app.n_zik.android.components.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.createRippleModifierNode
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassHighlightIndication
import app.n_zik.android.uiRoundnessShape

internal val PressFeedbackHorizontalInset = 8.dp

/** Clips only the feedback while clicks and content keep the full entry bounds. */
@Composable
internal fun insetPressIndication(shape: Shape = uiRoundnessShape()): Indication {
    val current = LocalIndication.current
    val configuration = LocalRippleConfiguration.current
    val glass = current as? GlassHighlightIndication
    if (glass == null && configuration == null) return current
    val color = configuration?.color?.takeIf { it.isSpecified } ?: LocalContentColor.current
    val alpha = configuration?.rippleAlpha ?: RippleDefaults.RippleAlpha
    return remember(shape, glass, color, alpha) { InsetPressIndication(shape, glass, color, alpha) }
}

private data class InsetPressIndication(
    val shape: Shape,
    val glass: GlassHighlightIndication?,
    val color: Color,
    val alpha: RippleAlpha,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        val feedback = glass?.create(interactionSource) ?: createRippleModifierNode(
            interactionSource = interactionSource,
            bounded = true,
            radius = Dp.Unspecified,
            color = { color },
            rippleAlpha = { alpha },
        )
        if (feedback !is DrawModifierNode) return feedback
        return InsetPressFeedback(feedback, shape)
    }
}

internal class InsetPressFeedback(
    feedback: DrawModifierNode,
    shape: Shape,
) : DelegatingNode(), DrawModifierNode {
    private val feedbackNode = delegate(feedback)
    private val feedbackShape = HorizontallyInsetShape(shape)

    override fun ContentDrawScope.draw() {
        drawContent()
        val clip = Path().apply {
            addOutline(feedbackShape.createOutline(size, layoutDirection, this@draw))
        }
        clipPath(clip) {
            val feedbackScope = object : ContentDrawScope, DrawScope by this {
                override fun drawContent() = Unit
            }
            with(feedbackNode) { feedbackScope.draw() }
        }
    }
}

/** Offsets a surface outline without changing that surface's layout. */
internal data class HorizontallyInsetShape(private val shape: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val margin = with(density) { PressFeedbackHorizontalInset.toPx() }.coerceAtMost(size.width / 4f)
        val insetSize = Size(size.width - 2 * margin, size.height)
        return when (val outline = shape.createOutline(insetSize, layoutDirection, density)) {
            is Outline.Rectangle -> Outline.Rectangle(outline.rect.translate(margin, 0f))
            is Outline.Rounded -> with(outline.roundRect) {
                Outline.Rounded(
                    RoundRect(
                        left + margin, top, right + margin, bottom,
                        topLeftCornerRadius, topRightCornerRadius, bottomRightCornerRadius, bottomLeftCornerRadius,
                    ),
                )
            }
            is Outline.Generic -> Outline.Generic(Path().apply {
                addPath(outline.path, Offset(margin, 0f))
            })
        }
    }
}
