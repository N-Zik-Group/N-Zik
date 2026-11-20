package app.n_zik.android.playback.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.utils.CoilBitmapLoader
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import app.n_zik.android.utils.coroutines.NzikDispatchers
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import kotlinx.coroutines.CancellationException
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
    private val context: Context,
    private val scope: CoroutineScope,
    bitmapSize: Int,
) : BitmapLoader {

    private val inner = CoilBitmapLoader(context, scope, bitmapSize)

    override fun supportsMimeType(mimeType: String): Boolean = inner.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = inner.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE ->
                scope.future(NzikDispatchers.DATA) {
                    tryLocal { inner.loadSource(uri) } ?: decodeLocal(uri) ?: inner.fallbackBitmap()
                }

            else -> inner.loadBitmap(uri)
        }

    private fun decodeLocal(uri: Uri): Bitmap? = tryLocal {
        val decoded = if (uri.authority == ArtworkContentProvider.AUTHORITY) {
            // This provider emits an already bounded, correctly oriented JPEG.
            // Opening it once avoids regenerating that JPEG for each decode pass.
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
            }
        } else {
            ArtworkBitmapDecoder.decode {
                if (uri.scheme == ContentResolver.SCHEME_FILE) uri.path?.let { File(it).inputStream() }
                else context.contentResolver.openInputStream(uri)
            }
        }
        decoded?.let { inner.fitToSession(it, owned = true) }
    }

    private inline fun tryLocal(load: () -> Bitmap?): Bitmap? = try {
        load()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
