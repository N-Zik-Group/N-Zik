package app.n_zik.android.components.settings.appearance.preview

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 33], application = Application::class)
class ThemePreviewViewportTest {
    @Test
    fun configurationReplacesHostGeometryAndAccessibilityScaleWithoutMutatingIt() {
        val original = Configuration(RuntimeEnvironment.getApplication().resources.configuration).apply {
            setLocale(Locale.FRENCH)
            densityDpi = 640
            fontScale = 1.7f
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 1200
            screenHeightDp = 700
            smallestScreenWidthDp = 700
            screenLayout = Configuration.SCREENLAYOUT_SIZE_XLARGE
        }
        val canonical = ThemePreviewViewport.configuration(original)

        assertEquals("en", canonical.locales[0].language)
        assertEquals(480, canonical.densityDpi)
        assertEquals(1f, canonical.fontScale)
        assertEquals(Configuration.ORIENTATION_PORTRAIT, canonical.orientation)
        assertEquals(448, canonical.screenWidthDp)
        assertEquals(997, canonical.screenHeightDp)
        assertEquals(448, canonical.smallestScreenWidthDp)
        assertEquals(Configuration.SCREENLAYOUT_SIZE_NORMAL,
            canonical.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK)
        assertEquals("fr", original.locales[0].language)
        assertEquals(640, original.densityDpi)
        assertEquals(1.7f, original.fontScale)
        assertEquals(1200, original.screenWidthDp)
    }

    @Test
    fun captureReplacesThreeButtonAndKeyboardInsetsWithHiddenGestureNavigation() {
        val source = hostInsets()
        val canonical = ThemePreviewViewport.insets(source, false)

        assertEquals(Insets.of(0, 159, 0, 0), canonical.getInsets(WindowInsetsCompat.Type.systemBars()))
        assertEquals(Insets.of(0, 159, 0, 0), canonical.getInsets(WindowInsetsCompat.Type.displayCutout()))
        assertEquals(Insets.NONE, canonical.getInsets(WindowInsetsCompat.Type.navigationBars()))
        assertEquals(Insets.NONE, canonical.getInsets(WindowInsetsCompat.Type.ime()))
        assertEquals(Insets.NONE, canonical.getInsets(WindowInsetsCompat.Type.systemGestures()))
        assertEquals(Insets.NONE, canonical.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()))
        assertFalse(canonical.isVisible(WindowInsetsCompat.Type.navigationBars()))
        assertEquals(144, source.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
    }

    @Test
    fun hiddenStatusBarAlsoRemovesCanonicalCutoutSpace() {
        val canonical = ThemePreviewViewport.insets(hostInsets(), true)
        assertEquals(Insets.NONE, canonical.getInsets(WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout()))
        assertEquals(Insets.NONE, canonical.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.displayCutout()))
    }

    @Test
    @Config(sdk = [26, 28])
    fun legacyNativeInsetRoundTripKeepsCanonicalSafeTopAndNoNavigationSpace() {
        for (hidden in listOf(false, true)) {
            val canonical = ThemePreviewViewport.insets(hostInsets(), hidden)
            val received = WindowInsetsCompat.toWindowInsetsCompat(requireNotNull(canonical.toWindowInsets()))
            assertEquals(if (hidden) 0 else 159, received.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            assertEquals(0, received.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            assertEquals(0, received.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom)
            assertEquals(0, received.getInsets(WindowInsetsCompat.Type.displayCutout()).left)
            assertEquals(0, received.getInsets(WindowInsetsCompat.Type.displayCutout()).right)
        }
    }

    @Test
    fun wrapperTransformsChildInsetsAndRestoresNativeInsetsForGallery() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapper = ThemePreviewInsetFrame(activity)
        val child = View(activity)
        wrapper.addView(child)
        activity.setContentView(wrapper)
        var received: WindowInsetsCompat? = null
        ViewCompat.setOnApplyWindowInsetsListener(child) { _, insets ->
            received = insets
            insets
        }
        val native = requireNotNull(hostInsets().toWindowInsets())
        wrapper.dispatchApplyWindowInsets(native)
        val galleryInsets = checkNotNull(received)
        wrapper.setCapture(true, false)
        wrapper.dispatchApplyWindowInsets(native)
        assertEquals(159, received?.getInsets(WindowInsetsCompat.Type.statusBars())?.top)
        assertEquals(0, received?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom)
        wrapper.setCapture(false, false)
        wrapper.dispatchApplyWindowInsets(native)
        assertEquals(galleryInsets.getInsets(WindowInsetsCompat.Type.navigationBars()),
            received?.getInsets(WindowInsetsCompat.Type.navigationBars()))
        assertEquals(native, received?.toWindowInsets())
    }

    @Test
    fun windowRestoresGalleryBoundsAndPreservesIndependentFlagChanges() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window
        val original = WindowManager.LayoutParams().apply { copyFrom(window.attributes) }
        val capture = ThemePreviewCaptureWindow(window)
        capture.enter()
        assertEquals(1344, window.attributes.width)
        assertEquals(2992, window.attributes.height)
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        capture.restore()
        assertEquals(original.width, window.attributes.width)
        assertEquals(original.height, window.attributes.height)
        assertEquals(original.gravity, window.attributes.gravity)
        assertEquals(original.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            window.attributes.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }

    @Test
    fun constrainedViewportFailsBeforeAnyDeviceSizedImageCanBeSaved() {
        ThemePreviewViewport.requireCanonicalSize(1344, 2992)
        val error = runCatching { ThemePreviewViewport.requireCanonicalSize(1080, 2400) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("Retry in fullscreen"))
        assertTrue(runCatching { ThemePreviewViewport.requireCanonicalSize(2992, 1344) }.isFailure)
    }

    private fun hostInsets(): WindowInsetsCompat = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 90, 0, 0))
        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 144))
        .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 144))
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 900))
        .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(30, 0, 30, 96))
        .setVisible(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars() or
            WindowInsetsCompat.Type.ime(), true)
        .build()
}
