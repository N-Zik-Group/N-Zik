package app.n_zik.android.utils.playlist

import android.content.Context
import android.net.Uri
import app.n_zik.android.components.image.ownedCoverMutationMutex
import app.n_zik.android.components.image.saveOwnedImage
import app.n_zik.android.components.image.restoreOwnedCover
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Replaces the shared playlist thumbnail after the cover dialog confirms its draft. */
internal suspend fun savePlaylistThumbnail(
    context: Context,
    uri: Uri,
    thumbnailName: String,
    isCurrentTarget: () -> Boolean = { true },
    save: (Context, Uri, String, String) -> Uri? = ::saveOwnedImage,
): Uri? {
    if (!isCurrentTarget()) return null
    if (!thumbnailName.matches(Regex("playlist_[0-9]+"))) return null
    return ownedCoverMutationMutex.withLock {
        var replacedFile: File? = null
        var previousBytes: ByteArray? = null
        var committed = false
        try {
            val saved = withContext(NzikDispatchers.DATA) {
                if (!isCurrentTarget()) return@withContext null
                val directory = File(context.filesDir, "thumbnail").canonicalFile
                val target = File(directory, thumbnailName).canonicalFile
                require(target.parentFile == directory)
                previousBytes = target.takeIf(File::exists)?.readBytes()
                replacedFile = target
                val saved = save(context, uri, "thumbnail", thumbnailName) ?: return@withContext null
                for (quality in ImageCacheFactory.NetworkQuality.entries) {
                    ImageCacheFactory.clearCacheForKey(saved.toString(), quality)
                    ImageCacheFactory.clearCacheForKey(target.absolutePath, quality)
                }
                saved
            } ?: return@withLock null
            currentCoroutineContext().ensureActive()
            if (!isCurrentTarget()) return@withLock null
            committed = true
            saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PlaylistThumbnail").e(error, "Could not apply playlist thumbnail")
            null
        } finally {
            val target = replacedFile
            if (!committed && target != null) withContext(NonCancellable + NzikDispatchers.DATA) {
                try {
                    restoreOwnedCover(target, previousBytes)
                    for (quality in ImageCacheFactory.NetworkQuality.entries) {
                        ImageCacheFactory.clearCacheForKey(Uri.fromFile(target).toString(), quality)
                        ImageCacheFactory.clearCacheForKey(target.absolutePath, quality)
                    }
                } catch (error: Exception) {
                    Timber.tag("PlaylistThumbnail").e(error, "Could not restore playlist thumbnail")
                }
            }
        }
    }
}
