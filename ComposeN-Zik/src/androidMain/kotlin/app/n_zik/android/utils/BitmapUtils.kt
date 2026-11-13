package app.n_zik.android.utils

import android.content.Context
import android.graphics.Bitmap
import app.n_zik.android.core.coil.ImageCacheFactory

suspend fun getBitmapFromUrl(context: Context, url: String): Bitmap? {
    if (url.isBlank() || url == "null") return null

    return try {
        val bitmap: Bitmap? = ImageCacheFactory.loadBitmap(url, allowHardware = false)
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0 && !bitmap.isRecycled) {
            bitmap
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}





