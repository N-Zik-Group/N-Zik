package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.internal.recordLayer
import app.n_zik.android.components.glass.drawContentOn

/** Captures only modifiers between [background] and [content], never player descendants. */
class GlassPlayerBackground internal constructor(internal val backdrop: LayerBackdrop) {
    internal var recording = false
    val background: Modifier get() = BackgroundElement(this, false)
    val content: Modifier get() = BackgroundElement(this, true)
}

@Composable
fun rememberGlassPlayerBackground(): GlassPlayerBackground {
    val backdrop = rememberLayerBackdrop()
    return remember(backdrop) { GlassPlayerBackground(backdrop) }
}

private data class BackgroundElement(val capture: GlassPlayerBackground, val boundary: Boolean) :
    ModifierNodeElement<BackgroundNode>() {
    override fun create() = BackgroundNode(capture, boundary)
    override fun update(node: BackgroundNode) {
        node.capture = capture
        node.boundary = boundary
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "glassPlayerBackground" }
}

private class BackgroundNode(var capture: GlassPlayerBackground, var boundary: Boolean) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    override fun ContentDrawScope.draw() {
        if (boundary) {
            if (!capture.recording) drawContent()
            return
        }
        // Refresh before drawing controls. The matching boundary stops before descendants,
        // so neither controls nor their sampled layers can enter this recording.
        recordLayer(this@BackgroundNode, capture.backdrop.graphicsLayer) {
            val previous = capture.recording
            capture.recording = true
            try {
                this@draw.drawContentOn(this)
            } finally {
                capture.recording = previous
            }
        }
        capture.backdrop.contentRecorded()
        drawContent()
    }
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!boundary && coordinates.isAttached) capture.backdrop.layerCoordinates = coordinates
    }
    override fun onDetach() {
        if (!boundary) capture.backdrop.layerCoordinates = null
    }
}
