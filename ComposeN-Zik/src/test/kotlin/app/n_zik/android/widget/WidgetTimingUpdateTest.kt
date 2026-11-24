package app.n_zik.android.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.n_zik.android.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33], application = Application::class)
class WidgetTimingUpdateTest {
    private val packageName get() = ApplicationProvider.getApplicationContext<Application>().packageName
    private val manager = mockk<AppWidgetManager>(relaxed = true)

    @Test
    fun `single layout advances progress with a partial update`() = runBlocking {
        val timing = mutableListOf<WidgetTimingUpdate>()
        manager.publishPlaybackWidget(1, Bundle(), timing) {
            RemoteViews(packageName, R.layout.widget_music_player)
        }
        timing.single().publish(manager, packageName, playback(speed = 1f))

        verify(exactly = 1) { manager.updateAppWidget(1, any()) }
        verify(exactly = 1) { manager.partiallyUpdateAppWidget(1, any()) }
    }

    @Test
    fun `chronometer only layout uses host timing until playback speed changes`() = runBlocking {
        val timing = mutableListOf<WidgetTimingUpdate>()
        manager.publishPlaybackWidget(1, Bundle(), timing) {
            RemoteViews(packageName, R.layout.widget_turntable)
        }
        timing.single().publish(manager, packageName, playback(speed = 1f))
        verify(exactly = 0) { manager.partiallyUpdateAppWidget(1, any()) }

        timing.single().publish(manager, packageName, playback(speed = 1.5f))
        verify(exactly = 1) { manager.partiallyUpdateAppWidget(1, any()) }
    }

    @Test
    fun `multiple layouts keep their static views across timing updates`() = runBlocking {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 360)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 210)
        }
        val timing = mutableListOf<WidgetTimingUpdate>()
        var staticRenders = 0
        manager.publishPlaybackWidget(1, options, timing) {
            staticRenders++
            RemoteViews(packageName, R.layout.widget_music_player)
        }
        repeat(2) { timing.single().publish(manager, packageName, playback(speed = 0.5f)) }

        assertEquals(2, staticRenders)
        verify(exactly = 3) { manager.updateAppWidget(1, any()) }
        verify(exactly = 0) { manager.partiallyUpdateAppWidget(1, any()) }
    }

    @Test
    fun `widgets display separate liked disliked and neutral heart icons`() {
        assertEquals(R.drawable.ic_widget_heart_nav, widgetHeartIcon(true))
        assertEquals(R.drawable.heart_dislike, widgetHeartIcon(false))
        assertEquals(R.drawable.ic_widget_heart_outline_nav, widgetHeartIcon(null))
    }

    private fun playback(speed: Float) =
        WidgetPlaybackSnapshot(60_000, 10_000, SystemClock.elapsedRealtime(), true, speed)
}
