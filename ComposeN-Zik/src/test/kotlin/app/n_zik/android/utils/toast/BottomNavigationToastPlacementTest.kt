package app.n_zik.android.utils.toast

import android.app.Application
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class BottomNavigationToastPlacementTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `rendered bar height changes placement and disposal restores default`() {
        val showBar = mutableStateOf(true)
        val height = mutableStateOf(84.dp)
        var barTop = 0f
        var density = 1f
        lateinit var view: View
        lateinit var lifecycle: Lifecycle
        composeRule.setContent {
            view = LocalView.current
            lifecycle = LocalLifecycleOwner.current.lifecycle
            density = LocalDensity.current.density
            Box(Modifier.fillMaxSize()) {
                if (showBar.value) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp)) {
                        Box(Modifier.fillMaxWidth().height(height.value)
                            .trackBottomNavigationForToasts(true, 54.dp)
                            .onGloballyPositioned { barTop = it.boundsInWindow().top })
                    }
                }
            }
        }
        composeRule.runOnIdle {
            // Robolectric's Compose host is attached/resumed but its AttachInfo still marks the
            // window GONE. Model the visible window explicitly; production must keep this guard.
            val attachInfo = ReflectionHelpers.getField<Any>(view, "mAttachInfo")
            ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE)
        }
        fun assertMeasuredOffset() = composeRule.runOnIdle {
            val toast = Toast(view.context)
            val default = (48 * density).roundToInt()
            toast.setGravity(Gravity.BOTTOM, 0, default)
            BottomNavigationToastPlacement.applyTo(toast)
            val windowBottom = view.context.getSystemService(WindowManager::class.java)
                .currentWindowMetrics.bounds.height()
            val bottomInset = requireNotNull(ViewCompat.getRootWindowInsets(view)).getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom
            val expected = (windowBottom - bottomInset - barTop).roundToInt() +
                (8 * density).roundToInt()
            assertEquals("attached=${view.isAttachedToWindow} shown=${view.isShown} visibility=${view.windowVisibility} lifecycle=${lifecycle.currentState} top=$barTop", expected, toast.yOffset)
            assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, toast.gravity)
        }
        assertMeasuredOffset()
        composeRule.runOnIdle {
            // Exercise the real attach/pre-draw listener with a separate WindowManager window.
            // ShadowToast does not attach custom Toast views, so attach this view explicitly.
            val windowManager = view.context.getSystemService(WindowManager::class.java)
            val toastContent = TextView(view.context).apply { text = "Placement integration" }
            val toast = Toast(view.context)
            @Suppress("DEPRECATION")
            toast.view = toastContent
            val defaultOffset = (48 * density).roundToInt()
            toast.setGravity(Gravity.BOTTOM, 0, defaultOffset)
            val params = WindowManager.LayoutParams().apply {
                width = 300
                this.height = 60
                type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                token = view.applicationWindowToken
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = toast.yOffset
            }
            windowManager.addView(toastContent, params)
            try {
                // WindowManager attaches on the next scheduled traversal, not inside addView.
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                toastContent.layout(0, 0, 300, 60)
                // Android installs a copy of the LayoutParams on the attached view.
                val attachedParams = toastContent.layoutParams as WindowManager.LayoutParams
                BottomNavigationToastPlacement.applyTo(toast)
                val toastLocation = IntArray(2)
                toastContent.getLocationOnScreen(toastLocation)
                val screenOrigin = IntArray(2)
                val windowOrigin = IntArray(2)
                view.getLocationOnScreen(screenOrigin)
                view.getLocationInWindow(windowOrigin)
                val targetBottom = (barTop + screenOrigin[1] - windowOrigin[1]).roundToInt() -
                    (8 * density).roundToInt()
                val expected = attachedParams.y + toastLocation[1] + toastContent.height - targetBottom
                toastContent.viewTreeObserver.dispatchOnPreDraw()
                assertEquals("toastAttached=${toastContent.isAttachedToWindow} hostVisible=${view.windowVisibility} listeners=${org.robolectric.Shadows.shadowOf(toastContent).onAttachStateChangeListeners.size}", expected, attachedParams.y)
                // The one-shot callback is gone, so no subsequent traversal is cancelled.
                assertFalse(toastContent.viewTreeObserver.dispatchOnPreDraw())
            } finally {
                windowManager.removeViewImmediate(toastContent)
            }
        }
        composeRule.runOnIdle { height.value = 64.dp }
        assertMeasuredOffset()
        composeRule.runOnIdle { showBar.value = false }
        composeRule.runOnIdle {
            val toast = Toast(view.context)
            toast.setGravity(Gravity.BOTTOM, 7, 64)
            BottomNavigationToastPlacement.applyTo(toast)
            assertEquals(64, toast.yOffset)
            assertEquals(7, toast.xOffset)
        }
    }
}
