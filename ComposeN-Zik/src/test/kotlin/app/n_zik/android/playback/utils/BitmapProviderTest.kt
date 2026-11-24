package app.n_zik.android.playback.utils

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.setActiveProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `the widget-facing bitmap falls back when its profile changes`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val profile = getActiveProfile(context)
        val uri = Uri.parse("https://example.com/widget-cover.jpg")
        val loaded = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(NzikDispatchers, ImageCacheFactory)
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        every { NzikDispatchers.DATA } returns dispatcher
        coEvery {
            ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 400, cacheDecodedBitmap = false, profile = profile)
        } returns loaded
        try {
            val provider = BitmapProvider(this) { Color.MAGENTA }
            provider.load(uri) { }
            advanceUntilIdle()
            assertSame(loaded, provider.bitmap)

            setActiveProfile("widget-bitmap-other-profile", context)
            assertEquals(uri, provider.lastUri)
            assertEquals(Color.MAGENTA, provider.bitmap.getPixel(0, 0))
        } finally {
            setActiveProfile(profile, context)
            Dispatchers.resetMain()
            unmockkObject(NzikDispatchers, ImageCacheFactory)
            unmockkStatic("app.n_zik.android.GlobalVarsKt")
        }
    }
}
