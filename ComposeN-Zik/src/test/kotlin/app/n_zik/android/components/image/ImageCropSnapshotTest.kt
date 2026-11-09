package app.n_zik.android.components.image

import android.app.Application
import android.graphics.Matrix
import android.graphics.RectF
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImageCropSnapshotTest {
    @Test
    fun `rotation zoom and pan preserve selected bitmap coordinates after screen resize`() {
        val matrix = Matrix().apply {
            postScale(1.8f, 1.8f)
            postRotate(37f)
            postTranslate(160f, -45f)
        }
        val before = ImageCropSnapshot.imageRect(RectF(46f, 66f, 446f, 366f), 16, 16, 16, 16)
        val after = ImageCropSnapshot.imageRect(RectF(132f, 52f, 932f, 652f), 32, 32, 32, 32)
        assertEquals(RectF(30f, 50f, 430f, 350f), before)
        assertEquals(RectF(100f, 20f, 900f, 620f), after)
        val snapshot = requireNotNull(ImageCropSnapshot.capture(matrix, before))
        val restored = snapshot.restore(after)
        fun bitmapSelection(transform: Matrix, crop: RectF): FloatArray {
            val inverse = Matrix()
            check(transform.invert(inverse))
            return floatArrayOf(crop.left, crop.top, crop.right, crop.bottom, crop.centerX(), crop.centerY()).also(inverse::mapPoints)
        }
        assertArrayEquals(bitmapSelection(matrix, before), bitmapSelection(restored, after), .001f)
    }

    @Test
    fun `missing malformed and unlaid out crop states are ignored`() {
        assertNull(ImageCropSnapshot.fromValues(null))
        assertNull(ImageCropSnapshot.fromValues(floatArrayOf(1f)))
        assertNull(ImageCropSnapshot.fromValues(FloatArray(9) { Float.NaN }))
        assertNull(ImageCropSnapshot.capture(Matrix(), RectF()))
    }
}
