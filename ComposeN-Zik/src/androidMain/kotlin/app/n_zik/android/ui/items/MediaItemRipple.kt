package app.n_zik.android.ui.items

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
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassHighlightIndication
import app.n_zik.android.uiRoundnessShape

/** Clips only a media item's feedback while clicks and content keep the full row bounds. */
@Composable
internal fun mediaItemRipple(): Indication {
    val current = LocalIndication.current
    val configuration = LocalRippleConfiguration.current
    val shape = uiRoundnessShape()
    val glass = current as? GlassHighlightIndication
    if (glass == null && configuration == null) return current
    val color = configuration?.color?.takeIf { it.isSpecified } ?: LocalContentColor.current
    val alpha = configuration?.rippleAlpha ?: RippleDefaults.RippleAlpha
    return remember(shape, glass, color, alpha) { MediaItemIndication(shape, glass, color, alpha) }
}

private data class MediaItemIndication(
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
        return InsetMediaItemFeedback(feedback, shape)
    }
}

internal class InsetMediaItemFeedback(
    feedback: DrawModifierNode,
    private val shape: Shape,
) : DelegatingNode(), DrawModifierNode {
    private val feedbackNode = delegate(feedback)

    override fun ContentDrawScope.draw() {
        drawContent()
        val margin = 8.dp.toPx().coerceAtMost(size.width / 4f)
        val clip = Path().apply {
            val feedbackSize = Size(size.width - 2 * margin, size.height)
            addOutline(shape.createOutline(feedbackSize, layoutDirection, this@draw))
            translate(Offset(margin, 0f))
        }
        clipPath(clip) {
            val feedbackScope = object : ContentDrawScope, DrawScope by this {
                override fun drawContent() = Unit
            }
            with(feedbackNode) { feedbackScope.draw() }
        }
    }
}
