package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import app.n_zik.android.components.navigation.header.headerScrollFade

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopNavigationStatusFillTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `status and header retain identical gray film throughout fade and reverse`() {
        val offset = mutableFloatStateOf(0f)
        val film = Color(0xFF29272A).copy(alpha = .7f)
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("matched")
                .topNavigationStatusFill(false, 24f, 64f, film, headerColor = film) { offset.floatValue }
                .background(Color.White)) {
                Box(Modifier.headerScrollFade(24f, 64f, film, clipStatusAtRest = true) { offset.floatValue }
                    .size(100.dp, 88.dp))
            }
        }
        for (position in listOf(0f, -32f, -52f, -64f, -52f, 0f)) {
            compose.runOnIdle { offset.floatValue = position }
            val pixels = compose.onNodeWithTag("matched").captureToImage().toPixelMap()
            val status = pixels[50, 12]
            val remainingRow = (64f + position).coerceAtLeast(0f)
            val headerY = if (remainingRow > 0f) (24f + remainingRow / 2f).toInt() else 40
            val header = pixels[50, headerY]
            assertEquals(header.red, status.red, .01f)
            assertEquals(header.green, status.green, .01f)
            assertEquals(header.blue, status.blue, .01f)
        }
    }

    @Test fun `without top tabs status background follows header then clears for standalone top mini`() {
        val offset = mutableFloatStateOf(0f)
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("status")
                .topNavigationStatusFill(false, 24f, 64f, Color.Blue, headerColor = Color.Red) { offset.floatValue }
                .background(Color.White))
        }
        fun sample(y: Int) = compose.onNodeWithTag("status").captureToImage().toPixelMap()[50, y]
        assertEquals(Color.Red, sample(12))
        assertEquals(Color.White, sample(40))
        compose.runOnIdle { offset.floatValue = -32f }
        assertEquals(.5f, sample(12).green, .02f)
        assertEquals(1f, sample(12).red, .02f)
        compose.runOnIdle { offset.floatValue = -52f }
        assertEquals(.8125f, sample(12).green, .02f)
        assertEquals(1f, sample(12).red, .02f)
        compose.runOnIdle { offset.floatValue = -64f }
        assertEquals(Color.White, sample(12))
        compose.runOnIdle { offset.floatValue = 0f }
        assertEquals(Color.Red, sample(12))
    }

    @Test fun `navigation film fills only status area as header leaves and requires actual top tabs`() {
        val offset = mutableFloatStateOf(0f)
        val visible = mutableStateOf(true)
        val color = mutableStateOf(Color.Red)
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("scene")
                .topNavigationStatusFill(visible.value, 24f, 64f, color.value) { offset.floatValue }
                .background(Color.White))
        }
        fun sample(y: Int) = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[50, y]
        assertEquals(Color.White, sample(12))
        compose.runOnIdle { offset.floatValue = -32f }
        assertEquals(.5f, sample(12).green, .02f)
        assertEquals(1f, sample(12).red, .02f)
        assertEquals(Color.White, sample(40))
        compose.runOnIdle { offset.floatValue = -64f }
        assertEquals(Color.Red, sample(12))
        compose.runOnIdle { color.value = Color.Red.copy(alpha = .4f) }
        assertEquals(.6f, sample(12).green, .02f)
        compose.runOnIdle { visible.value = false }
        assertEquals(Color.White, sample(12))
        assertEquals(Color.White, sample(40))
    }

    @Test fun `landscape status film leaves system side insets clear`() {
        val offset = mutableFloatStateOf(-64f)
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("landscapeStatus")
                .topNavigationStatusFill(true, 24f, 64f, Color.Red,
                    horizontalStartInsetPx = 12f, horizontalEndInsetPx = 18f) { offset.floatValue }
                .background(Color.White))
        }
        val pixels = compose.onNodeWithTag("landscapeStatus").captureToImage().toPixelMap()
        assertEquals(Color.White, pixels[5, 12])
        assertEquals(Color.Red, pixels[50, 12])
        assertEquals(Color.White, pixels[90, 12])
    }
}
