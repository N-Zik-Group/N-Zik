package app.n_zik.android.playback.utils

import android.app.Application
import android.graphics.Color
import app.n_zik.android.appContext
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "xxxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapProviderTest {
    @Test fun `the solid artwork fallback keeps one pixel even at high density`() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns RuntimeEnvironment.getApplication()
        try {
            val provider = BitmapProvider(TestScope()) { Color.MAGENTA }
            val fallback = provider.bitmap
            assertEquals(1, fallback.width)
            assertEquals(1, fallback.height)
            assertEquals(Color.MAGENTA, fallback.getPixel(0, 0))
        } finally {
            unmockkStatic("app.n_zik.android.GlobalVarsKt")
        }
    }
}
