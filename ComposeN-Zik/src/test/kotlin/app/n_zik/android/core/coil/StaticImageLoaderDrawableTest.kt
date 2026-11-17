package app.n_zik.android.core.coil

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.graphics.drawable.VectorDrawable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.TimeUnit

/** Keep the shared image placeholder a frozen frame of the expressive loader. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StaticImageLoaderDrawableTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    @Test fun `placeholder keeps its intrinsic size viewport and existing gray fill`() {
        context.resources.getXml(R.drawable.loader).use { xml ->
            while (xml.next() != XmlPullParser.START_TAG) Unit
            assertEquals("vector", xml.name)
            assertEquals(24f, xml.getAttributeFloatValue(androidNamespace, "viewportWidth", 0f), 0f)
            assertEquals(24f, xml.getAttributeFloatValue(androidNamespace, "viewportHeight", 0f), 0f)
            while (xml.next() != XmlPullParser.START_TAG) Unit
            assertEquals("path", xml.name)
            assertEquals(0xff666666.toInt(), xml.getAttributeIntValue(androidNamespace, "fillColor", 0))
        }
        val drawable = requireNotNull(context.getDrawable(R.drawable.loader))
        val expectedEdge = (24 * context.resources.displayMetrics.density + .5f).toInt()
        assertEquals(expectedEdge, drawable.intrinsicWidth)
        assertEquals(expectedEdge, drawable.intrinsicHeight)
    }

    @Test fun `placeholder geometry matches the first Material loader polygon at its initial rotation`() {
        val polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
        var scale = 1f
        for (polygon in polygons) {
            val bounds = polygon.calculateBounds()
            val maxBounds = polygon.calculateMaxBounds()
            scale = min(scale, max(
                (bounds[2] - bounds[0]) / (maxBounds[2] - maxBounds[0]),
                (bounds[3] - bounds[1]) / (maxBounds[3] - maxBounds[1]),
            ))
        }
        scale *= LoadingIndicatorDefaults.IndicatorSize.value /
            min(LoadingIndicatorDefaults.ContainerWidth.value, LoadingIndicatorDefaults.ContainerHeight.value)
        val polygon = polygons.first().normalized()
        val bounds = polygon.calculateBounds(approximate = false)
        val centerX = (bounds[0] + bounds[2]) / 2f
        val centerY = (bounds[1] + bounds[3]) / 2f
        val expected = buildList {
            fun point(x: Float, y: Float) {
                // Material's initial 90-degree clockwise rotation around the container center.
                add(12f - (y - centerY) * 24f * scale)
                add(12f + (x - centerX) * 24f * scale)
            }
            point(polygon.cubics.first().anchor0X, polygon.cubics.first().anchor0Y)
            polygon.cubics.forEach {
                point(it.control0X, it.control0Y)
                point(it.control1X, it.control1Y)
                point(it.anchor1X, it.anchor1Y)
            }
        }.toFloatArray()
        val path = context.resources.getXml(R.drawable.loader).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path") {
                    return@use requireNotNull(xml.getAttributeValue(androidNamespace, "pathData"))
                }
            }
            error("Missing placeholder path")
        }
        assertEquals("The vector contains only one closed cubic contour", "MCZ",
            path.filter { it.isLetter() }.replace(Regex("C+"), "C"))
        val actual = Regex("-?\\d+(?:\\.\\d+)?").findAll(path).map { it.value.toFloat() }.toList().toFloatArray()
        assertArrayEquals(expected, actual, .00001f)
    }

    @Test fun `placeholder is a static vector and pixels stay fixed as time advances`() {
        val drawable = requireNotNull(context.getDrawable(R.drawable.loader))
        assertEquals(VectorDrawable::class.java, drawable.javaClass)
        assertFalse(drawable is Animatable)
        drawable.setBounds(0, 0, 96, 96)
        fun pixels(): IntArray {
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(bitmap))
            return IntArray(96 * 96).also { bitmap.getPixels(it, 0, 96, 0, 0, 96, 96) }
        }
        val firstFrame = pixels()
        ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS)
        assertArrayEquals(firstFrame, pixels())
    }
}
