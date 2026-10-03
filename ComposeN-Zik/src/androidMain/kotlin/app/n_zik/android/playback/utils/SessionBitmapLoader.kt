package app.n_zik.android.playback.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.CoilBitmapLoader
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future

/**
 * Session BitmapLoader for the MediaLibrarySession: repairs the session metadata
 * artwork (Android Auto home big player, notification) for local content:// and
 * file:// sources that [CoilBitmapLoader] cannot resolve on a shared-cache miss
 * (it falls back to a placeholder). Those streams are opened in-process, where
 * the app holds the MediaStore permissions; every other scheme delegates to the
 * legacy loader unchanged.
 */
@UnstableApi
class SessionBitmapLoader(
    context: Context,
    private val scope: CoroutineScope,
    private val bitmapSize: Int,
) : BitmapLoader {

    private val inner = CoilBitmapLoader(context, scope, bitmapSize)

    override fun supportsMimeType(mimeType: String): Boolean = inner.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = inner.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE ->
                scope.future(NzikDispatchers.DATA) {
                    decodeLocal(uri) ?: inner.loadBitmap(uri).get()
                }

            else -> inner.loadBitmap(uri)
        }

    /**
     * Reads a local artwork source the service process can open: shared image
     * cache first (the same source of truth as the legacy loader), then a
     * direct downsampled decode. Returns null when the source is missing or
     * undecodable so the legacy behavior stays in charge.
     */
    private suspend fun decodeLocal(uri: Uri): Bitmap? = runCatching {
        val cached = ImageCacheFactory.loadBitmap(uri.toString(), allowHardware = false)
        if (cached != null) return@runCatching cached
        val data: ByteArray? = when (uri.scheme) {
            ContentResolver.SCHEME_FILE ->
                uri.path?.let { path -> File(path).takeIf(File::exists)?.readBytes() }
            else -> appContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        if (data == null) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = SessionMediaItemMapper.computeInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    bitmapSize
                )
            }
        )
    }.getOrNull()
}
