package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

/** Pixel 10 Pro XL reference geometry, independent of the capturing phone's display settings. */
internal object ThemePreviewViewport {
    const val width = 1344
    const val height = 2992
    const val densityDpi = 480
    const val statusBarHeight = 159

    fun configuration(source: Configuration): Configuration = Configuration(source).apply {
        setLocale(Locale.ENGLISH)
        densityDpi = ThemePreviewViewport.densityDpi
        fontScale = 1f
        orientation = Configuration.ORIENTATION_PORTRAIT
        screenWidthDp = 448
        screenHeightDp = 997
        smallestScreenWidthDp = 448
        screenLayout = (screenLayout and
            (Configuration.SCREENLAYOUT_SIZE_MASK or Configuration.SCREENLAYOUT_LONG_MASK or
                Configuration.SCREENLAYOUT_ROUND_MASK).inv()) or
            Configuration.SCREENLAYOUT_SIZE_NORMAL or Configuration.SCREENLAYOUT_LONG_YES or
            Configuration.SCREENLAYOUT_ROUND_NO
        if (Build.VERSION.SDK_INT >= 31) fontWeightAdjustment = 0
    }

    @Suppress("DEPRECATION") // Android 9/10 need stable insets normalized before typed insets reach Compose.
    fun insets(source: WindowInsetsCompat, hideStatusBar: Boolean): WindowInsetsCompat {
        val top = Insets.of(0, if (hideStatusBar) 0 else statusBarHeight, 0, 0)
        val fixedTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.systemGestures() or WindowInsetsCompat.Type.mandatorySystemGestures() or
            WindowInsetsCompat.Type.tappableElement()
        return WindowInsetsCompat.Builder(source.consumeDisplayCutout())
            .setStableInsets(top)
            .setDisplayCutout(if (hideStatusBar) null else DisplayCutoutCompat(Rect(0, statusBarHeight, 0, 0), emptyList()))
            .setInsets(fixedTypes or WindowInsetsCompat.Type.ime(), Insets.NONE)
            .setInsetsIgnoringVisibility(fixedTypes, Insets.NONE)
            .setInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(), top)
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(), top)
            .setVisible(fixedTypes or WindowInsetsCompat.Type.ime(), false)
            .setVisible(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(), !hideStatusBar)
            .build()
    }

    fun requireCanonicalSize(actualWidth: Int, actualHeight: Int) {
        check(actualWidth == width && actualHeight == height) {
            "Canonical preview viewport unavailable: ${actualWidth}x$actualHeight; expected ${width}x$height. Retry in fullscreen."
        }
    }
}

/** Transforms insets before Compose receives them, preserving Compose's own inset listener. */
internal class ThemePreviewInsetFrame(context: Context) : FrameLayout(context) {
    var capturing = false
        private set
    private var hideStatusBar = false

    fun setCapture(active: Boolean, hideStatus: Boolean) {
        if (capturing == active && hideStatusBar == hideStatus) return
        capturing = active
        hideStatusBar = hideStatus
        ViewCompat.requestApplyInsets(this)
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets =
        super.dispatchApplyWindowInsets(if (capturing) {
            requireNotNull(ThemePreviewViewport.insets(
                WindowInsetsCompat.toWindowInsetsCompat(insets, this), hideStatusBar,
            ).toWindowInsets())
        } else insets)
}

/** Owns capture-only window changes; the gallery regains its original bounds and native navigation. */
internal class ThemePreviewCaptureWindow(private val window: Window) {
    private val original = WindowManager.LayoutParams().apply { copyFrom(window.attributes) }
    private val controller = WindowCompat.getInsetsController(window, window.decorView)
    private val navigationVisible = ViewCompat.getRootWindowInsets(window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
    private val originalBehavior = controller.systemBarsBehavior

    fun enter() {
        window.attributes = WindowManager.LayoutParams().apply {
            copyFrom(window.attributes)
            width = ThemePreviewViewport.width
            height = ThemePreviewViewport.height
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            horizontalMargin = 0f
            verticalMargin = 0f
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }

    fun restore() {
        val captureFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        window.attributes = WindowManager.LayoutParams().apply {
            copyFrom(original)
            // Keep flags owned by other effects, including the capture's independent wake-lock effect.
            flags = (window.attributes.flags and captureFlags.inv()) or (original.flags and captureFlags)
        }
        controller.systemBarsBehavior = originalBehavior
        if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
        else controller.hide(WindowInsetsCompat.Type.navigationBars())
    }
}
