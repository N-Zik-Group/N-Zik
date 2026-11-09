package app.n_zik.android.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TurntableWidgetGeometryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val ids = listOf(R.id.widget_turntable_album_art, R.id.widget_turntable_prev_button,
        R.id.widget_turntable_next_button, R.id.widget_turntable_play_container,
        R.id.widget_turntable_like_button, R.id.widget_turntable_chronometer,
        R.id.widget_turntable_total_duration)

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    // RemoteViews production creation suspends; Robolectric invokes a synchronous test method.
    @Test fun `rectangular hosts only translate the existing square composition`() = runBlocking {
        val square = render(200, 200)
        val original = LayoutInflater.from(context).inflate(R.layout.widget_turntable,
            FrameLayout(context), false) as ViewGroup
        for (id in listOf(R.id.widget_turntable_chronometer, R.id.widget_turntable_total_duration)) {
            original.findViewById<TextView>(id).text = square.findViewById<TextView>(id).text
        }
        measure(original, 200, 200)
        for (id in ids) assertEquals("Square layout changed for $id", bounds(original, id), bounds(square, id))

        for ((width, height) in listOf(200 to 200, 200 to 300, 300 to 200)) {
            val root = render(width, height)
            for (id in ids) {
                val child = root.findViewById<View>(id)
                assertEquals(View.VISIBLE, child.visibility)
                assertTrue("View $id has no area", child.width > 0 && child.height > 0)
                val expected = bounds(square, id).apply { offset((width - 200) / 2, (height - 200) / 2) }
                val actual = bounds(root, id)
                assertEquals("View $id changed relative to record in ${width}x$height", expected, actual)
                assertTrue(actual.left >= 0 && actual.top >= 0 && actual.right <= width && actual.bottom <= height)
            }
            val art = root.findViewById<ImageView>(R.id.widget_turntable_album_art)
            assertEquals(192, art.width)
            assertEquals(art.width, art.height)
            val bitmap = (art.drawable as BitmapDrawable).bitmap
            assertEquals(bitmap.width, bitmap.height)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(255, Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
            assertEquals("2:49", root.findViewById<TextView>(R.id.widget_turntable_total_duration).text.toString())
        }
    }

    // Preserve the legacy minimum-size composition; its pre-existing crowding is a separate concern.
    // The suspend RemoteViews factory requires a coroutine from this synchronous Robolectric test.
    @Test fun `minimum width tall host does not change legacy control geometry`() = runBlocking {
        val square = render(110, 110)
        val tall = render(110, 210)
        for (id in ids) {
            val expected = bounds(square, id).apply { offset(0, 50) }
            assertEquals("Minimum-size geometry changed for $id", expected, bounds(tall, id))
        }
    }

    private suspend fun render(width: Int, height: Int): ViewGroup {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        }
        val artwork = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val views = NZikWidgetManager.createTurntableRemoteViewsForSize(context, options, artwork,
            false, true, 169_000, 25_000, DefaultDarkColorPalette, null)
        return (views.apply(context, FrameLayout(context)) as ViewGroup).also { measure(it, width, height) }
    }

    private fun measure(root: ViewGroup, width: Int, height: Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }

    private fun bounds(root: ViewGroup, id: Int): Rect {
        val child = root.findViewById<View>(id)
        return Rect(0, 0, child.width, child.height).also { root.offsetDescendantRectToMyCoords(child, it) }
    }
}
