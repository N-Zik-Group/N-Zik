package app.n_zik.android.components.glass

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Uses native clickable interactions, so list scrolling still cancels the held highlight. */
internal class GlassHighlightIndication(
    private val pointerTracker: GlassHighlightPointerTracker,
    private val drawBeforeContent: Boolean = false,
) : IndicationNodeFactory {
    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        GlassHighlightNode(interactionSource, pointerTracker, drawBeforeContent)
}

internal class GlassHighlightNode(
    private val interactionSource: InteractionSource,
    private val pointerTracker: GlassHighlightPointerTracker,
    private val drawBeforeContent: Boolean = false,
) : Modifier.Node(), DrawModifierNode {
    internal var highlight: InteractiveHighlight? = null
        private set
    private var activePress: PressInteraction.Press? = null

    override fun onAttach() {
        val animation = InteractiveHighlight(coroutineScope)
        highlight = animation
        // A clickable may create its indication lazily on the first down event.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        activePress = interaction
                        animation.startPress(interaction.pressPosition)
                        pointerTracker.add(this@GlassHighlightNode)
                    }
                    is PressInteraction.Release -> if (activePress == interaction.press) {
                        activePress = null
                        pointerTracker.remove(this@GlassHighlightNode)
                        animation.releasePress()
                    }
                    is PressInteraction.Cancel -> if (activePress == interaction.press) {
                        activePress = null
                        pointerTracker.remove(this@GlassHighlightNode)
                        animation.releasePress()
                    }
                }
            }
        }
    }

    override fun onDetach() {
        highlight = null
        activePress = null
        pointerTracker.remove(this)
    }

    internal fun movePointer(position: Offset, coordinates: LayoutCoordinates) {
        if (isAttached && activePress != null && coordinates.isAttached) {
            highlight?.movePress(requireLayoutCoordinates().localPositionOf(coordinates, position))
        }
    }

    override fun ContentDrawScope.draw() {
        if (drawBeforeContent) highlight?.drawHighlight(this)
        drawContent()
        // Rows paint an opaque background in their content, unlike the button glass layer.
        if (!drawBeforeContent) highlight?.drawHighlight(this)
    }
}
