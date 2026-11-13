package app.n_zik.android.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetVideoArtworkTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var previous: Boolean? = null

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previous = context.preferences.all[cropVideoThumbnailsKey] as? Boolean
    }

    @After fun tearDown() {
        context.preferences.edit().apply {
            previous?.let { putBoolean(cropVideoThumbnailsKey, it) } ?: remove(cropVideoThumbnailsKey)
        }.commit()
        Dispatchers.resetMain()
    }

    // RemoteViews rendering is suspend-only; the test owns the synchronous boundary.
    @Test fun `widgets fit video frames after toggle and preserve custom and artist cropping`() = runBlocking {
        val source = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 80)
        }
        val video = "https://i.ytimg.com/vi/video/hqdefault.jpg"
        suspend fun render(url: String, crop: Boolean, artist: Boolean = false): Bitmap {
            context.preferences.edit().putBoolean(cropVideoThumbnailsKey, crop).commit()
            val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)
            WidgetAppearance.apply(context, views, options, false,
                mapOf(R.id.widget_wide_album_art to WidgetArtwork(source, artist, url)))
            val root = views.apply(context, FrameLayout(context))
            return (root.findViewById<ImageView>(R.id.widget_wide_album_art).drawable as BitmapDrawable).bitmap
        }
        val cropped = render(video, true)
        val fitted = render(video, false)
        val custom = render("file:///app_covers/custom.jpg", false)
        val artist = render(video, false, artist = true)
        assertEquals(Color.RED, cropped.getPixel(cropped.width / 2, cropped.height / 8))
        assertEquals(0, Color.alpha(fitted.getPixel(fitted.width / 2, fitted.height / 8)))
        assertEquals(Color.RED, fitted.getPixel(fitted.width / 2, fitted.height / 2))
        assertEquals(Color.RED, custom.getPixel(custom.width / 2, custom.height / 8))
        assertEquals(Color.RED, artist.getPixel(artist.width / 2, artist.height / 8))
        assertEquals(160, source.width)
        assertEquals(80, source.height)
    }
}
