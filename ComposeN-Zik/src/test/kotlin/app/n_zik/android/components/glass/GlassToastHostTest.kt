package app.n_zik.android.components.glass

import android.app.Application
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.utils.toast.BottomNavigationToastPlacement
import app.n_zik.android.utils.toast.GlassToastDispatcher
import app.n_zik.android.utils.toast.trackBottomNavigationForToasts
import app.n_zik.android.utils.toast.trackToastObstruction
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassToastHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `toast pixels fade in and out while queued messages retain their full lifetime`() {
        val enabled = mutableStateOf(true)
        val presence = mutableListOf<Boolean>()
        lateinit var view: View
        var density = 1f
        compose.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                Box(Modifier.size(360.dp, 640.dp).background(Color.Black).testTag("toastScene")) {
                    GlassToastHost(null, 24.dp,
                        settings = GlassSettings(enabled = enabled.value,
                            areaModes = mapOf(GlassArea.Menus to GlassAreaMode.ContourOnly)),
                        onPresenceChanged = { presence.add(it) })
                }
            }
        }
        compose.runOnIdle {
            val info = ReflectionHelpers.getField<Any>(view, "mAttachInfo")
            ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            assertTrue(GlassToastDispatcher.tryShow("First", Toast.LENGTH_SHORT, null, 0xffff0000.toInt(), -1))
            assertTrue(GlassToastDispatcher.tryShow("Second", Toast.LENGTH_SHORT, null, 0xffff0000.toInt(), -1))
        }
        fun redAtToastMargin(): Float {
            val bounds = compose.onNodeWithTag("glassToast").fetchSemanticsNode().boundsInRoot
            val scene = compose.onNodeWithTag("toastScene")
            val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
            val pixels = scene.captureToImage().toPixelMap()
            // Inside the film, away from the text and the rounded contour.
            return pixels[(bounds.left - sceneBounds.left + 10 * density).roundToInt(),
                (bounds.center.y - sceneBounds.top).roundToInt()].red
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        // The first animation frame establishes its start time after the host mounts.
        compose.mainClock.advanceTimeBy(64)
        val entering = redAtToastMargin()
        assertTrue("Entrance must draw an intermediate opacity: $entering", entering > .01f && entering < .95f)
        compose.mainClock.advanceTimeBy(236)
        assertEquals(1f, redAtToastMargin(), .02f)
        compose.mainClock.advanceTimeBy(1900)
        // Delay resumes on a frame, and the exit animation then needs its own first frame.
        // Follow the drawn onset rather than assuming both happen at the timeout timestamp.
        var departure = redAtToastMargin()
        var exitFrames = 0
        while (departure >= .99f && exitFrames < 20) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            departure = redAtToastMargin()
            exitFrames++
        }
        assertTrue("Exit must begin while the toast is still drawn: $departure", departure > .01f && departure < .99f)
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val exiting = redAtToastMargin()
        assertTrue("Exit must draw an intermediate opacity: $exiting", exiting > .01f && exiting < .95f)
        compose.onNodeWithText("First").assertExists()
        compose.onNodeWithText("Second").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(true), presence) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("First").assertDoesNotExist()
        compose.onNodeWithText("Second").assertExists()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1f, redAtToastMargin(), .02f)
        compose.mainClock.advanceTimeBy(2200)
        compose.onNodeWithTag("glassToast").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(true, false), presence) }
        compose.runOnIdle { enabled.value = false }
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.runOnIdle {
            assertFalse("Disabled Glass preserves native fallback", GlassToastDispatcher.tryShow(
                "native", Toast.LENGTH_SHORT, null, 1, 2))
        }
        compose.onNodeWithTag("glassToast").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun `Glass and native use actual mini top then navigation top across toggles`() {
        val enabled = mutableStateOf(true)
        val hostPresent = mutableStateOf(true)
        val presence = mutableListOf<Boolean>()
        val modalHost = GlassMenuHostState()
        var pageClicks = 0
        val sourcePresent = mutableStateOf(true)
        val miniPresent = mutableStateOf(true)
        val navPresent = mutableStateOf(true)
        val miniBottom = mutableStateOf(128.dp)
        lateinit var view: View
        var density = 1f
        var miniTop = 0f
        var navTop = 0f
        var rootTop = 0f
        compose.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            val source = rememberCanvasBackdrop { drawRect(Color.Cyan) }
            val forbidden = rememberCanvasBackdrop { error("Toast must sample the explicit source only") }
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
                LocalGlassBackdrop provides forbidden,
                LocalGlassMenuHost provides modalHost,
            ) {
                Box(Modifier.size(360.dp, 640.dp).onGloballyPositioned { rootTop = it.boundsInWindow().top }) {
                    Box(Modifier.size(32.dp).testTag("pageAction").clickable { pageClicks++ })
                    if (navPresent.value) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                        Box(Modifier.fillMaxWidth().height(64.dp).trackBottomNavigationForToasts(true, 54.dp)
                            .onGloballyPositioned { navTop = it.boundsInWindow().top })
                    }
                    if (miniPresent.value) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = miniBottom.value)) {
                        Box(Modifier.fillMaxWidth().height(68.dp).trackToastObstruction()
                            .onGloballyPositioned { miniTop = it.boundsInWindow().top })
                    }
                    if (hostPresent.value) GlassToastHost(if (sourcePresent.value) source else null, 24.dp,
                        settings = GlassSettings(enabled = enabled.value), onPresenceChanged = { presence.add(it) })
                }
            }
        }
        compose.runOnIdle {
            val info = ReflectionHelpers.getField<Any>(view, "mAttachInfo")
            ReflectionHelpers.setField(info, "mWindowVisibility", View.GONE)
            assertFalse(GlassToastDispatcher.tryShow("background", Toast.LENGTH_SHORT, null, 1, 2))
            ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            assertTrue(GlassToastDispatcher.tryShow("Visible toast", Toast.LENGTH_LONG, null, 0xff198754.toInt(), -1))
        }
        fun settle() {
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.mainClock.advanceTimeBy(96); compose.waitForIdle()
        }
        fun assertGlassBottom(top: () -> Float) {
            settle()
            compose.onNodeWithText("Visible toast").assertExists()
            val bounds = compose.onNodeWithTag("glassToast").fetchSemanticsNode().boundsInRoot
            assertTrue("Toast text has nonempty layout", bounds.width > 0 && bounds.height > 0)
            assertEquals(top() - rootTop - 8 * density, bounds.bottom, 1.1f)
        }
        fun assertNativeBottom(top: Float) = compose.runOnIdle {
            val native = Toast(view.context)
            native.setGravity(Gravity.BOTTOM, 0, 20)
            BottomNavigationToastPlacement.applyTo(native)
            val windowBottom = view.context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
            val bottomInset = requireNotNull(ViewCompat.getRootWindowInsets(view)).getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom
            assertEquals((windowBottom - bottomInset - top).roundToInt() + (8 * density).roundToInt(), native.yOffset)
        }
        settle()
        assertGlassBottom { miniTop }
        compose.runOnIdle {
            assertEquals(true, presence.last())
            assertFalse("Toast presence must never claim modal input", modalHost.hasOverlays)
        }
        compose.onNodeWithTag("pageAction").performClick()
        compose.runOnIdle { assertEquals(1, pageClicks) }
        assertNativeBottom(miniTop)
        // Change the actual mini geometry independently of the old synthetic player insets.
        compose.runOnIdle { miniBottom.value = 170.dp }
        settle()
        assertGlassBottom { miniTop }
        assertNativeBottom(miniTop)
        compose.runOnIdle { miniPresent.value = false }
        settle()
        assertGlassBottom { navTop }
        assertNativeBottom(navTop)
        compose.runOnIdle { enabled.value = false }
        settle()
        compose.runOnIdle { assertEquals(false, presence.last()) }
        compose.runOnIdle { assertFalse(GlassToastDispatcher.tryShow("native", Toast.LENGTH_SHORT, null, 1, 2)) }
        assertNativeBottom(navTop)
        compose.runOnIdle { navPresent.value = false }
        settle()
        compose.runOnIdle {
            val native = Toast(view.context)
            native.setGravity(Gravity.BOTTOM, 0, 80)
            BottomNavigationToastPlacement.applyTo(native)
            assertEquals("No chrome retains the platform default", 80, native.yOffset)
            enabled.value = true
            sourcePresent.value = false
        }
        settle()
        compose.runOnIdle { assertFalse(GlassToastDispatcher.tryShow("native", Toast.LENGTH_SHORT, null, 1, 2)) }
        compose.runOnIdle { sourcePresent.value = true }
        settle()
        compose.runOnIdle { assertTrue(GlassToastDispatcher.tryShow("Expires", Toast.LENGTH_SHORT, null, 1, 2)) }
        settle()
        compose.onNodeWithText("Expires").assertExists()
        compose.runOnIdle { assertEquals(true, presence.last()) }
        compose.mainClock.advanceTimeBy(2400)
        compose.onNodeWithTag("glassToast").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(false, presence.last())
            assertTrue(GlassToastDispatcher.tryShow("Disposed", Toast.LENGTH_LONG, null, 1, 2))
        }
        settle()
        compose.runOnIdle {
            assertEquals(true, presence.last())
            hostPresent.value = false
        }
        settle()
        compose.runOnIdle {
            assertEquals(false, presence.last())
            assertFalse(modalHost.hasOverlays)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun `all menu modes route correctly with live settings changes`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val enabled = mutableStateOf(true)
        lateinit var view: View
        lateinit var lifecycle: androidx.lifecycle.Lifecycle
        compose.setContent {
            view = LocalView.current
            lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            val shape = RoundedCornerShape(12.dp)
            val source = rememberCanvasBackdrop { drawRect(Color.Blue) }
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                GlassToastHost(source, 24.dp, settings = GlassSettings(enabled = enabled.value,
                    areaModes = mapOf(GlassArea.Menus to mode.value)))
            }
        }
        compose.runOnIdle {
            val info = ReflectionHelpers.getField<Any>(view, "mAttachInfo")
            ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE)
            assertTrue(view.isAttachedToWindow)
            assertTrue(view.isShown)
            assertTrue(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        }
        compose.mainClock.autoAdvance = false
        GlassAreaMode.entries.forEach { selected ->
            compose.runOnIdle { mode.value = selected }
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle {
                assertEquals(selected != GlassAreaMode.Disabled,
                    GlassToastDispatcher.tryShow("mode $selected", Toast.LENGTH_SHORT, null, 1, 2))
            }
            repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.mainClock.advanceTimeBy(64)
            if (selected != GlassAreaMode.Disabled) compose.onNodeWithTag("glassToast").assertExists()
        }
        compose.runOnIdle { enabled.value = false }
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertFalse(GlassToastDispatcher.tryShow("native", Toast.LENGTH_SHORT, null, 1, 2)) }
        compose.onNodeWithTag("glassToast").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun `caller owned drawable bounds and filter survive drawing and errors`() {
        var fail = false
        val drawable = object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: android.graphics.Canvas) { if (fail) error("draw failed") }
            override fun setAlpha(alpha: Int) = Unit
            private var filter: android.graphics.ColorFilter? = null
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { filter = colorFilter }
            override fun getColorFilter(): android.graphics.ColorFilter? = filter
            @Deprecated("Deprecated in Android")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
        val originalFilter = android.graphics.PorterDuffColorFilter(123, android.graphics.PorterDuff.Mode.SRC_IN)
        drawable.setBounds(3, 4, 13, 14)
        drawable.colorFilter = originalFilter
        val canvas = android.graphics.Canvas(android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888))
        drawToastIcon(drawable, canvas, 24, 24, -1)
        assertEquals(android.graphics.Rect(3, 4, 13, 14), drawable.bounds)
        assertSame(originalFilter, drawable.colorFilter)
        fail = true
        assertTrue(runCatching { drawToastIcon(drawable, canvas, 24, 24, -1) }.isFailure)
        assertEquals(android.graphics.Rect(3, 4, 13, 14), drawable.bounds)
        assertSame(originalFilter, drawable.colorFilter)
    }
}
