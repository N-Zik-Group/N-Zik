package app.n_zik.android.components.ui.sliders

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SliderIntegrationTest {
    @Test
    fun componentReflectsExternalUpdatesAndReportsDragCompletion() = runTest {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = activityController.get()
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frameClock)
        val runner = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        val view = ComposeView(activity)
        val value = mutableFloatStateOf(12.5f)
        val range = mutableStateOf(10f..20f)
        val changes = mutableListOf<Float>()
        var completions = 0
        var frameTime = 0L
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )

        fun frames(count: Int = 180) {
            repeat(count) {
                Snapshot.sendApplyNotifications()
                runCurrent()
                frameTime += 16_000_000L
                frameClock.sendFrame(frameTime)
                runCurrent()
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 600, 160)
                (view.getChildAt(0) as ViewRootForTest).measureAndLayoutForTest()
            }
        }

        fun sliderNode(): SemanticsNode =
            (view.getChildAt(0) as ViewRootForTest).semanticsOwner
                .getAllSemanticsNodes(mergingEnabled = true)
                .single { it.config.contains(SemanticsProperties.ProgressBarRangeInfo) }

        fun progress(): ProgressBarRangeInfo =
            sliderNode().config[SemanticsProperties.ProgressBarRangeInfo]

        try {
            view.setParentCompositionContext(recomposer)
            view.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    Slider(
                        state = value.floatValue,
                        setState = {
                            changes += it
                            value.floatValue = it
                        },
                        onSlideComplete = { completions++ },
                        range = range.value,
                        modifier = Modifier.width(300.dp),
                    )
                }
            }
            activity.setContentView(view)
            frames()
            assertEquals(10f..20f, progress().range)
            assertEquals(12.5f, progress().current, 0.01f)
            assertEquals(0, progress().steps)

            value.floatValue = 17.25f
            frames()
            assertEquals(17.25f, progress().current, 0.01f)
            assertEquals(10f..20f, progress().range)

            range.value = -5f..5f
            value.floatValue = -2.75f
            frames()
            assertEquals(-5f..5f, progress().range)
            assertEquals(-2.75f, progress().current, 0.01f)
            assertEquals(0, progress().steps)
            assertTrue("External updates must not invoke the user callback", changes.isEmpty())
            assertEquals(0, completions)

            val bounds = sliderNode().boundsInRoot
            assertTrue("The slider must be laid out before gesture injection", bounds.width > 0f)
            val touchView = (view.getChildAt(0) as ViewRootForTest).view
            val downTime = SystemClock.uptimeMillis()
            var eventTime = downTime
            fun touch(action: Int, fraction: Float) {
                eventTime += 16L
                val event = MotionEvent.obtain(
                    downTime, eventTime, action,
                    bounds.left + bounds.width * fraction, bounds.center.y, 0,
                )
                try {
                    touchView.dispatchTouchEvent(event)
                } finally {
                    event.recycle()
                }
                frames(2)
            }

            touch(MotionEvent.ACTION_DOWN, 0.225f)
            touch(MotionEvent.ACTION_MOVE, 0.5f)
            touch(MotionEvent.ACTION_MOVE, 0.75f)
            touch(MotionEvent.ACTION_UP, 0.75f)
            frames()
            assertTrue("Dragging must update the parent value", changes.isNotEmpty())
            assertTrue(changes.all { it in -5f..5f })
            assertTrue("Dragging right must increase the value", value.floatValue > -2.75f)
            assertEquals(value.floatValue, progress().current, 0.01f)
            assertEquals("Releasing a drag must complete it once", 1, completions)
        } finally {
            view.disposeComposition()
            recomposer.cancel()
            runner.cancel()
            activityController.pause().stop().destroy()
        }
    }
}
