package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollapsibleTitleRowPolicyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `title and count stay opaque throughout parent collapse travel`() {
        val offset = mutableFloatStateOf(0f)
        val height = mutableIntStateOf(100)
        compose.setContent {
            Box(Modifier.size(200.dp, 160.dp).background(Color.White).testTag("scene")) {
                Box(Modifier.offset(y = 48.dp)) {
                    CollapsibleTitleRow(offset, height) {
                        Box(Modifier.size(100.dp, 24.dp).background(Color.Red))
                    }
                }
            }
        }
        for (travel in listOf(0f, -15f, -30f, -50f, -100f)) {
            compose.runOnIdle { offset.floatValue = travel }
            val title = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[50, 60]
            assertEquals(Color.Red, title)
        }
        compose.runOnIdle { height.intValue = 0 }
        assertEquals(Color.Red, compose.onNodeWithTag("scene").captureToImage().toPixelMap()[50, 60])
    }

    @Test fun `title clips only pixels crossing Android status area without fading remainder`() {
        assertEquals(0f, titleStatusClipPx(48f, 24, 40f), 0f)
        assertEquals(12f, titleStatusClipPx(12f, 24, 40f), 0f)
        assertEquals(40f, titleStatusClipPx(-30f, 24, 40f), 0f)
        assertEquals(0f, titleStatusClipPx(Float.POSITIVE_INFINITY, 24, 40f), 0f)
    }
}
