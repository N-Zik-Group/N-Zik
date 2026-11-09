package app.n_zik.android.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
class MusicWidgetCompactLayoutTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    // The Android RemoteViews entry point is suspend; Robolectric invokes synchronous tests.
    @Test fun `short hosts keep full controls artwork and elapsed duration visible`() = runBlocking {
        for (fontScale in listOf(1f, 1.3f)) {
            val sizedContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                this.fontScale = fontScale
            })
            for ((width, height) in listOf(200 to 40, 200 to 100, 260 to 80, 340 to 100, 400 to 140)) {
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                }
                val views = NZikWidgetManager.createRemoteViewsForSize(sizedContext, options, "#1f1e33", "Camellia",
                    null, false, true, 169_000, 25_000, DefaultDarkColorPalette, null)
                assertEquals(R.layout.widget_compact_wide, views.layoutId)
                val root = views.apply(sizedContext, FrameLayout(sizedContext)) as ViewGroup
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                for (id in listOf(R.id.widget_wide_album_art, R.id.widget_wide_like_button,
                    R.id.widget_wide_play_pause_bg, R.id.widget_wide_play_pause,
                    R.id.widget_wide_prev_icon, R.id.widget_wide_next_icon, R.id.widget_wide_song_title,
                    R.id.widget_wide_chronometer, R.id.widget_wide_total_duration) +
                    if (height >= 64 * fontScale) listOf(R.id.widget_wide_artist_name) else emptyList()) {
                    val child = root.findViewById<View>(id)
                    assertEquals(View.VISIBLE, child.visibility)
                    assertTrue("View $id has no drawable area", child.width > 0 && child.height > 0)
                    val bounds = Rect(0, 0, child.width, child.height)
                    root.offsetDescendantRectToMyCoords(child, bounds)
                    assertTrue("View $id outside ${width}x$height: $bounds",
                        bounds.left >= 0 && bounds.top >= 0 && bounds.right <= width && bounds.bottom <= height)
                    var ancestor = child.parent
                    var descendant = child
                    while (ancestor is ViewGroup && ancestor !== root) {
                        assertTrue("View $id clipped by its parent at ${width}x$height font $fontScale",
                            descendant.left >= 0 && descendant.top >= 0 &&
                                descendant.right <= ancestor.width && descendant.bottom <= ancestor.height)
                        descendant = ancestor
                        ancestor = ancestor.parent
                    }
                }
                assertEquals(" / 2:49", root.findViewById<TextView>(R.id.widget_wide_total_duration).text.toString())
                for (id in listOf(R.id.widget_wide_chronometer, R.id.widget_wide_total_duration)) {
                    val text = root.findViewById<TextView>(id)
                    assertEquals("Time must remain on one line", 1, text.lineCount)
                    assertTrue("Time text is truncated", text.paint.measureText(text.text.toString()) <=
                        text.width - text.compoundPaddingLeft - text.compoundPaddingRight)
                }
                val artist = root.findViewById<View>(R.id.widget_wide_artist_name)
                assertEquals(if (height >= 64 * fontScale) View.VISIBLE else View.GONE, artist.visibility)
        }
        }
    }
}
