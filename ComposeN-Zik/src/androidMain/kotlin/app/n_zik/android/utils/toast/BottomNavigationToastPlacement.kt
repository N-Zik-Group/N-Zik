package app.n_zik.android.utils.toast

import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.ViewTreeObserver
import android.widget.Toast
import android.os.Build
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.n_zik.android.BuildConfig
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import timber.log.Timber

/** A token is owned by one rendered bar, so a departing screen cannot clear another bar. */
private class NavigationMeasurement(view: View, lifecycle: Lifecycle) {
    val view = WeakReference(view)
    val lifecycle = WeakReference(lifecycle)
    var top: Float? = null
    var standardHeight = 0
    var minimumGap = 0
}

object BottomNavigationToastPlacement {
    private val measurements = mutableSetOf<NavigationMeasurement>()

    @MainThread
    fun applyTo(toast: Toast) {
        val defaultOffset = toast.yOffset
        val offsets = measurements.mapNotNull { measurement ->
            val view = measurement.view.get() ?: return@mapNotNull null
            if (!view.isAttachedToWindow || !view.isShown || view.windowVisibility != View.VISIBLE ||
                measurement.lifecycle.get()?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true
            ) return@mapNotNull null
            val top = measurement.top ?: return@mapNotNull null
            val root = view.rootView
            val location = IntArray(2)
            root.getLocationInWindow(location)
            // WindowMetrics includes the full task window, even if adjustResize shrinks content.
            val windowBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
            } else {
                location[1] + root.height
            }
            // ToastPresenter fits system bars even when immersive mode hides them.
            val insets = ViewCompat.getRootWindowInsets(view)?.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            ) ?: return@mapNotNull null
            toastOffsetAboveNavigation(
                toast.yOffset,
                BottomNavigationGeometry(top, windowBottom, insets.bottom, measurement.standardHeight, measurement.minimumGap),
            )
        }
        val offset = offsets.maxOrNull()
        if (BuildConfig.DEBUG) Timber.tag("ToastPlacement").d(
            "Registered bars=%d active=%d defaultY=%d initialY=%s",
            measurements.size, offsets.size, defaultOffset, offset,
        )
        if (offset == null) return
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, offset)
        // A Toast window can receive different insets than the Activity (for example Android 17
        // taskbar navigation). Correct from its actual position before the first visible frame.
        @Suppress("DEPRECATION")
        val toastView = toast.view ?: return
        correctAttachedWindowBeforeDraw(toastView, defaultOffset)
    }

    private fun desiredBottomOnScreen(defaultOffset: Int): Int? = measurements.mapNotNull { measurement ->
        val view = measurement.view.get() ?: return@mapNotNull null
        if (!view.isAttachedToWindow || !view.isShown || view.windowVisibility != View.VISIBLE ||
            measurement.lifecycle.get()?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true
        ) return@mapNotNull null
        val top = measurement.top ?: return@mapNotNull null
        val screenPosition = IntArray(2)
        val windowPosition = IntArray(2)
        view.getLocationOnScreen(screenPosition)
        view.getLocationInWindow(windowPosition)
        val normalGap = (defaultOffset - measurement.standardHeight).coerceAtLeast(measurement.minimumGap)
        (top + screenPosition[1] - windowPosition[1]).roundToInt() - normalGap
    }.minOrNull()

    private fun correctAttachedWindowBeforeDraw(view: View, defaultOffset: Int) {
        val listener = object : View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
            private fun removeListeners() {
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                view.removeOnAttachStateChangeListener(this)
            }

            override fun onViewAttachedToWindow(v: View) {
                v.viewTreeObserver.addOnPreDrawListener(this)
            }

            override fun onViewDetachedFromWindow(v: View) = removeListeners()

            override fun onPreDraw(): Boolean {
                // Always remove first: a missing target or a second layout cannot suppress drawing forever.
                removeListeners()
                if (!view.isAttachedToWindow) return true
                val targetBottom = desiredBottomOnScreen(defaultOffset) ?: return true
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return true
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                val corrected = correctedToastBottomOffset(params.y, location[1] + view.height, targetBottom)
                if (BuildConfig.DEBUG) Timber.tag("ToastPlacement").d(
                    "Attached bottom=%d target=%d currentY=%d correctedY=%d",
                    location[1] + view.height, targetBottom, params.y, corrected,
                )
                if (corrected == params.y) return true
                params.y = corrected
                view.context.getSystemService(WindowManager::class.java).updateViewLayout(view, params)
                // The updated window position is applied by the next traversal; then draw normally.
                return false
            }
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) listener.onViewAttachedToWindow(view)
    }

    @Composable
    internal fun measurementModifier(isBottom: Boolean, standardContentHeight: Dp): Modifier {
        if (!isBottom) return Modifier
        val view = LocalView.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val measurement = remember(view, lifecycle) { NavigationMeasurement(view, lifecycle) }
        val standardHeight = with(LocalDensity.current) { standardContentHeight.roundToPx() }
        val minimumGap = with(LocalDensity.current) { 8.dp.roundToPx() }
        DisposableEffect(measurement) {
            measurements.add(measurement)
            onDispose { measurements.remove(measurement) }
        }
        return Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            measurement.top = bounds.top.takeIf { bounds.width > 0 && bounds.height > 0 }
            measurement.standardHeight = standardHeight
            measurement.minimumGap = minimumGap
        }
    }
}

@Composable
fun Modifier.trackBottomNavigationForToasts(isBottom: Boolean, standardContentHeight: Dp): Modifier =
    then(BottomNavigationToastPlacement.measurementModifier(isBottom, standardContentHeight))
