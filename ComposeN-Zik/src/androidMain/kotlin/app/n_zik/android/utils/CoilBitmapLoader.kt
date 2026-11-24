package app.n_zik.android.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import com.google.common.util.concurrent.ListenableFuture

import kotlinx.coroutines.CoroutineScope
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.guava.future
import app.n_zik.android.R
import android.content.ContentResolver
import kotlin.math.roundToInt

@UnstableApi
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val bitmapSize: Int,
) : BitmapLoader {
    private val fallback by lazy {
        ArtworkBitmapDecoder.decode { context.resources.openRawResource(R.drawable.ic_launcher_box) }
            ?.let { fitToSession(it, owned = true) }
            ?: error("Could not decode fallback artwork")
    }

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(NzikDispatchers.DATA) {
            val decoded = ArtworkBitmapDecoder.decode { data.inputStream() }
                ?: error("Could not decode image data")
            fitToSession(decoded, owned = true)
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(NzikDispatchers.DATA) {
            loadSource(uri) ?: if (uri.scheme == ContentResolver.SCHEME_CONTENT ||
                uri.scheme == ContentResolver.SCHEME_FILE
            ) fallbackBitmap() else error("Could not load image")
        }

    internal suspend fun loadSource(uri: Uri, profile: String? = null): Bitmap? =
        ImageCacheFactory.loadBitmap(
            uri.toString(),
            requestSize = bitmapSize.coerceAtLeast(1),
            cacheDecodedBitmap = false,
            profile = profile,
        )?.let { fitToSession(it, owned = false) }

    internal fun fallbackBitmap(): Bitmap = fallback

    internal fun fitToSession(bitmap: Bitmap, owned: Boolean): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        val size = bitmapSize.coerceAtLeast(1)
        if (longest <= size) return bitmap
        val scale = size.toDouble() / longest
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
        if (owned && scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}

