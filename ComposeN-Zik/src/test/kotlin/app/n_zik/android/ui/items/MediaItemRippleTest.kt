package app.n_zik.android.ui.items

import android.app.Application
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.InsetPressFeedback
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaItemRippleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun feedbackIsInsetWithoutClippingContentOrReducingTheClickTarget() {
        var clicks = 0
        compose.setContent {
            Box(
                Modifier.size(100.dp, 48.dp).testTag("media-item").clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = SolidInsetFeedback,
                    onClick = { clicks++ },
                ),
            ) {
                Box(Modifier.fillMaxSize().background(Color.Blue))
            }
        }
        val item = compose.onNodeWithTag("media-item")
        val pixels = item.captureToImage().toPixelMap()
        val centerY = pixels.height / 2
        assertEquals(Color.Blue, pixels[0, centerY])
        assertEquals(Color.Blue, pixels[pixels.width / 20, centerY])
        assertEquals(Color.Red, pixels[pixels.width / 14, centerY])
        assertEquals(Color.Red, pixels[pixels.width / 2, centerY])
        assertEquals(Color.Blue, pixels[pixels.width - 1, centerY])

        item.performTouchInput { click(Offset(1f, center.y)) }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private object SolidInsetFeedback : IndicationNodeFactory {
        override fun create(interactionSource: InteractionSource): DelegatableNode =
            InsetPressFeedback(SolidFeedbackNode(), RectangleShape)

        override fun equals(other: Any?) = other === this
        override fun hashCode() = System.identityHashCode(this)
    }

    private class SolidFeedbackNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            drawContent()
            drawRect(Color.Red)
        }
    }
}
