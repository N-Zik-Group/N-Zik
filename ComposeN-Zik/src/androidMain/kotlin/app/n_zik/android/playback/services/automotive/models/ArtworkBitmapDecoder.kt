package app.n_zik.android.playback.services.automotive.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** Bounds-first decoding shared by embedded artwork and the exported JPEG provider. */
internal object ArtworkBitmapDecoder {
    const val MAX_SIDE = 512
    internal val decodeCount = AtomicLong()

    fun decode(open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = open() ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = SessionMediaItemMapper.computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
        }
        decodeCount.incrementAndGet()
        val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        // Open a fresh stream: bounds and pixel decoding have consumed their own streams.
        val orientation = runCatching {
            open()?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return orient(bitmap, orientation)
    }

    fun file(file: File): Bitmap? {
        return decode { file.inputStream() }
    }

    private fun orient(input: Bitmap, orientation: Int): Bitmap {
        var bitmap = input
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        return bitmap
    }

    /** Consumes owned input; borrowed Coil bitmaps must be copied before this call. */
    fun finish(input: Bitmap, square: Boolean = false): Bitmap {
        var bitmap = input
        if (square) {
            val side = minOf(bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            if (cropped !== bitmap) bitmap.recycle()
            bitmap = cropped
        }
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest > MAX_SIDE) {
            val scale = MAX_SIDE.toDouble() / longest
            val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }

    fun jpeg(input: Bitmap, square: Boolean = false): ByteArray {
        val bitmap = finish(input, square)
        return try {
            ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out))
                out.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}
