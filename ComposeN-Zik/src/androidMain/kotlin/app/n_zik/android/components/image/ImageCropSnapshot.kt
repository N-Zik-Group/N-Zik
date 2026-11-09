package app.n_zik.android.components.image

import android.graphics.Matrix
import android.graphics.RectF

/** Stores the bitmap transform in crop coordinates, independent of screen size. */
internal class ImageCropSnapshot private constructor(private val values: FloatArray) {
    fun restore(crop: RectF): Matrix = Matrix().apply {
        setValues(values)
        postScale(crop.width(), crop.height())
        postTranslate(crop.left, crop.top)
    }

    fun values(): FloatArray = values.copyOf()

    companion object {
        fun imageRect(overlay: RectF, left: Int, top: Int, right: Int, bottom: Int) = RectF(
            overlay.left - left, overlay.top - top, overlay.right - right, overlay.bottom - bottom,
        )

        fun capture(matrix: Matrix, crop: RectF): ImageCropSnapshot? {
            if (crop.isEmpty) return null
            val normalized = Matrix(matrix).apply {
                postTranslate(-crop.left, -crop.top)
                postScale(1f / crop.width(), 1f / crop.height())
            }
            return fromValues(FloatArray(9).also(normalized::getValues))
        }

        fun fromValues(values: FloatArray?): ImageCropSnapshot? =
            values?.takeIf { it.size == 9 && it.all(Float::isFinite) }?.let { ImageCropSnapshot(it.copyOf()) }
    }
}
