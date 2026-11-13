package app.n_zik.android.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.coil.ImageCacheFactory
import com.google.common.util.concurrent.ListenableFuture

import kotlinx.coroutines.CoroutineScope
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.guava.future
import app.n_zik.android.R
import android.content.ContentResolver

@UnstableApi
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val bitmapSize: Int,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(NzikDispatchers.DATA) {
            BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Could not decode image data")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(NzikDispatchers.DATA) {
            var bitmap = ImageCacheFactory.loadBitmap(uri.toString(), allowHardware = false)
            
            if (bitmap == null && (uri.scheme == ContentResolver.SCHEME_CONTENT || uri.scheme == ContentResolver.SCHEME_FILE)) {
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_box)
                if (drawable is BitmapDrawable) {
                    bitmap = drawable.bitmap
                } else if (drawable != null) {
                    bitmap = Bitmap.createBitmap(
                        if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else bitmapSize,
                        if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else bitmapSize,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap!!)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
            
            bitmap ?: error("Could not load image")
        }

}



