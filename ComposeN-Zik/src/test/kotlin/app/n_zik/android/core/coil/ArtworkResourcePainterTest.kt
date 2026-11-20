package app.n_zik.android.core.coil

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import app.n_zik.android.appContext
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkResourcePainterTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `new artwork slots share one bundled bitmap and retain separate painters`() {
        val application = RuntimeEnvironment.getApplication()
        val track = mutableIntStateOf(0)
        lateinit var painter: Painter
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns application
        try {
            compose.setContent {
                key(track.intValue) {
                    val fallback = ImageCacheFactory.rememberArtworkResourcePainter()
                    SideEffect { painter = fallback }
                }
            }
            val first = compose.runOnIdle {
                assertEquals(Size(1200f, 1200f), painter.intrinsicSize)
                painter.also { track.intValue++ }
            }
            compose.runOnIdle {
                assertNotSame(first, painter)
                assertEquals(first, painter)
            }
        } finally {
            unmockkStatic("app.n_zik.android.GlobalVarsKt")
        }
    }
}
