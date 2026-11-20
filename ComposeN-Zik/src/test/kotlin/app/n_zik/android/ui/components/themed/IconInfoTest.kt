package app.n_zik.android.ui.components.themed

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconInfoTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun loadingPlaylistMetadataKeepsBothRowsInPlace() {
        val ready = mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
            ) {
                val icon = ColorPainter(Color.White)
                Column(Modifier.width(140.dp)) {
                    IconInfo(if (ready.value) "70" else null, icon, modifier = Modifier.testTag("count"))
                    IconInfo(if (ready.value) "3 h 35 m" else null, icon, modifier = Modifier.testTag("duration"))
                }
            }
        }

        val count = compose.onNodeWithTag("count")
        val duration = compose.onNodeWithTag("duration")
        count.assertIsDisplayed()
        duration.assertIsDisplayed()
        val initialCountBounds = count.getUnclippedBoundsInRoot()
        val initialDurationBounds = duration.getUnclippedBoundsInRoot()

        compose.runOnIdle { ready.value = true }

        compose.onNodeWithText("70").assertIsDisplayed()
        compose.onNodeWithText("3 h 35 m").assertIsDisplayed()
        assertEquals(initialCountBounds, count.getUnclippedBoundsInRoot())
        assertEquals(initialDurationBounds, duration.getUnclippedBoundsInRoot())
    }
}
