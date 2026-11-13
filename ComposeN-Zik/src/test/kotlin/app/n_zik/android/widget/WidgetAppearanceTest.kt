package app.n_zik.android.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.ui.styling.BoundedCornerSize
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.R
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetAppearanceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var savedPreferences = emptyMap<String, Any?>()
    private val keys = listOf(uiRoundnessDpKey, thumbnailRoundnessDpKey, artistThumbnailRoundnessDpKey)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        savedPreferences = keys.associateWith { context.preferences.all[it] }
    }
    @After fun tearDown() {
        context.preferences.edit().apply {
            savedPreferences.forEach { (key, value) -> if (value is Float) putFloat(key, value) else remove(key) }
        }.commit()
        Dispatchers.resetMain()
    }

    @Test fun `radii match in-app shapes across settings sizes and densities`() {
        for (setting in listOf(0f, 12f, 25f, 47f, 48f)) {
            for (density in listOf(1f, 2.75f)) {
                for (size in listOf(Size(32f, 32f), Size(200f, 80f), Size(600f, 600f))) {
                    val roundness = WidgetRoundness(setting, setting, setting)
                    for (artwork in listOf(false, true)) {
                        val shape = if (artwork && setting >= 48f) CircleShape
                        else RoundedCornerShape(BoundedCornerSize(setting.dp, if (artwork) 0.25f else 0.4f))
                        val outline = shape.createOutline(size, LayoutDirection.Ltr, Density(density))
                        val expected = if (outline is Outline.Rounded) outline.roundRect.topLeftCornerRadius.x else 0f
                        assertEquals(expected,
                            roundness.radius(size.width, size.height, density, artwork), 0.001f)
                    }
                }
            }
        }
    }

    @Test fun `artist setting is independent of song thumbnail setting`() {
        val roundness = WidgetRoundness(25f, 0f, 48f)
        assertEquals(0f, roundness.radius(64f, 64f, 1f, true), 0f)
        assertEquals(32f, roundness.radius(64f, 64f, 1f, true, true), 0f)
        assertEquals(25f, roundness.radius(64f, 64f, 1f, false), 0f)
    }

    @Test fun `no-image artwork uses the exact in-app boxed resource`() {
        val actual = WidgetAppearance.artworkBitmap(context, null, 96, 96, 0f)
        val expected = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        requireNotNull(context.getDrawable(R.drawable.ic_launcher_box)).apply {
            setBounds(0, 0, 96, 96)
            draw(Canvas(expected))
        }
        val actualPixels = IntArray(96 * 96)
        val expectedPixels = IntArray(96 * 96)
        actual.getPixels(actualPixels, 0, 96, 0, 0, 96, 96)
        expected.getPixels(expectedPixels, 0, 96, 0, 0, 96, 96)
        assertArrayEquals(expectedPixels, actualPixels)
    }

    @Test fun `responsive variants preserve the exact host dimensions`() = runBlocking {
        val sizes = arrayListOf(SizeF(120f, 80f), SizeF(320f, 220f))
        val options = Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes) }
        val received = mutableListOf<SizeF>()
        WidgetAppearance.responsive(options) { size ->
            received += SizeF(size.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(),
                size.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat())
            RemoteViews(context.packageName, R.layout.widget_compact_wide)
        }
        assertEquals(sizes, received)
    }

    @Test fun `bitmap shape rendering runs on the named media dispatcher`() = runBlocking {
        val thread = AtomicReference<String>()
        mockkObject(WidgetRoundness.Companion)
        try {
            every { WidgetRoundness.read(any()) } answers {
                thread.set(Thread.currentThread().name)
                callOriginal()
            }
            WidgetAppearance.apply(context, RemoteViews(context.packageName, R.layout.widget_compact_wide),
                Bundle(), false, mapOf(R.id.widget_wide_album_art to WidgetArtwork(null)))
            assertTrue(thread.get().startsWith("nzik-media-"))
        } finally {
            unmockkObject(WidgetRoundness.Companion)
        }
    }

    // Robolectric invokes this synchronous test while the production API is suspend.
    @Test fun `turntable retains circular artwork while controls follow roundness preferences`() = runBlocking {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 240)
        }
        val white = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        for (setting in listOf(0f, 12f, 48f)) {
            context.preferences.edit().putFloat(uiRoundnessDpKey, setting)
                .putFloat(thumbnailRoundnessDpKey, setting).putFloat(artistThumbnailRoundnessDpKey, setting).commit()
            for (art in listOf(white, null)) {
                val views = RemoteViews(context.packageName, R.layout.widget_turntable)
                WidgetAppearance.apply(context, views, options, false,
                    mapOf(R.id.widget_turntable_album_art to WidgetArtwork(art)))
                val root = views.apply(context, FrameLayout(context))
                val image = (root.findViewById<ImageView>(R.id.widget_turntable_album_art).drawable as BitmapDrawable).bitmap
                assertEquals(image.width, image.height)
                assertEquals(0, Color.alpha(image.getPixel(0, 0)))
                assertTrue(Color.alpha(image.getPixel(image.width / 2, image.height / 2)) > 0)
                for (id in listOf(R.id.widget_turntable_play_bg_image, R.id.widget_turntable_like_bg_image,
                    R.id.widget_turntable_nav_bg_image)) {
                    val control = (root.findViewById<ImageView>(id).drawable as BitmapDrawable).bitmap
                    assertEquals(if (setting == 0f) 255 else 0, Color.alpha(control.getPixel(0, 0)))
                }
            }
        }
    }

    @Test fun `remote views redraw cached dimensions when roundness preferences change`() = runBlocking {
        context.preferences.edit().putFloat(uiRoundnessDpKey, 0f)
            .putFloat(thumbnailRoundnessDpKey, 0f).putFloat(artistThumbnailRoundnessDpKey, 48f).commit()
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 80)
        }
        val white = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        suspend fun rendered(): Pair<Bitmap, Bitmap> {
            val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)
            WidgetAppearance.apply(context, views, options, false,
                mapOf(R.id.widget_wide_album_art to WidgetArtwork(white)))
            val root = views.apply(context, FrameLayout(context))
            fun bitmap(id: Int) = (root.findViewById<ImageView>(id).drawable as BitmapDrawable).bitmap
            return bitmap(R.id.widget_wide_album_art) to bitmap(R.id.widget_wide_bg_image)
        }
        val (squareArtwork, squareBackground) = rendered()
        assertEquals(255, Color.alpha(squareArtwork.getPixel(0, 0)))
        assertEquals(255, Color.alpha(squareBackground.getPixel(0, 0)))
        context.preferences.edit().putFloat(thumbnailRoundnessDpKey, 48f).putFloat(uiRoundnessDpKey, 25f).commit()
        val (roundArtwork, roundBackground) = rendered()
        assertEquals(0, Color.alpha(roundArtwork.getPixel(0, 0)))
        assertEquals(0, Color.alpha(roundBackground.getPixel(0, 0)))
        assertTrue(Color.alpha(roundArtwork.getPixel(roundArtwork.width / 2, roundArtwork.height / 2)) > 0)
    }
}
