package app.n_zik.android.components.dialog.common

import android.app.Application
import android.view.Window
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NativeDialogDimmingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native window dim follows retained dialog motion on entry and exit`() {
        compose.mainClock.autoAdvance = false
        val visible = mutableStateOf(true)
        var disposed = false
        var observedWindow: Window? = null
        var observedPresentation: DialogPresentation? = null
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = ColorPalette.fallback, typography = Typography.fallback,
                thumbnailShape = RoundedCornerShape(16.dp), uiRoundnessShape = RoundedCornerShape(16.dp),
                artistThumbnailShape = RoundedCornerShape(16.dp),
            )) {
                DialogPresence(visible.value) {
                    val presentation = requireNotNull(LocalDialogPresentation.current)
                    SideEffect { observedPresentation = presentation }
                    DisposableEffect(Unit) { onDispose { disposed = true } }
                    DialogFrame(onDismiss = {}) {
                        val window = (LocalView.current.parent as DialogWindowProvider).window
                        SideEffect { observedWindow = window }
                        BasicText("Native modal")
                    }
                }
            }
        }
        fun advance(time: Long) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeBy(time)
            compose.waitForIdle()
        }
        advance(80)
        val window = requireNotNull(observedWindow)
        val enteringDim = window.attributes.dimAmount
        assertTrue("entry must retain a partially dimmed platform window", enteringDim > 0f)
        advance(300)
        val originalNativeDim = window.attributes.dimAmount
        assertTrue("entry reaches the platform's original dim amount", originalNativeDim > enteringDim)
        compose.runOnIdle { visible.value = false }
        advance(80)
        assertFalse("exit retains the native window until its fade completes", disposed)
        val exitDim = window.attributes.dimAmount
        assertTrue("exit must progressively remove the native dim", exitDim > 0f && exitDim < originalNativeDim)
        assertEquals(originalNativeDim * dialogFadeAlpha(requireNotNull(observedPresentation).motion.value), exitDim, .001f)
        advance(300)
        assertTrue(disposed)
        assertEquals("the dismissed window ends without dimming", 0f, window.attributes.dimAmount, .001f)
    }
}
