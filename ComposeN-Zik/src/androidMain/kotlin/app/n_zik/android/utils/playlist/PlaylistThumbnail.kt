package app.n_zik.android.utils.playlist

import android.content.Context
import android.net.Uri
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.image.saveOwnedImage
import app.n_zik.android.components.image.restoreOwnedCover
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Edits a temporary copy and replaces the playlist thumbnail only after editor confirmation. */
internal suspend fun savePlaylistThumbnail(
    context: Context,
    uri: Uri,
    thumbnailName: String,
    isCurrentTarget: () -> Boolean = { true },
    save: (Context, Uri, String, String) -> Uri? = ::saveOwnedImage,
): Uri? {
    if (!isCurrentTarget()) return null
    if (!thumbnailName.matches(Regex("playlist_[0-9]+"))) return null
    val profile = getActiveProfile(context)
    val edited = PickedImageEditor.edit(context, uri) ?: return null
    return edited.use {
        thumbnailCommitMutex.withLock {
            var replacedFile: File? = null
            var previousBytes: ByteArray? = null
            var committed = false
            try {
                val saved = withContext(NzikDispatchers.DATA) {
                    if (!isCurrentTarget() || getActiveProfile(context) != profile) return@withContext null
                    val directory = File(context.filesDir, "thumbnail").canonicalFile
                    val target = File(directory, thumbnailName).canonicalFile
                    require(target.parentFile == directory)
                    previousBytes = target.takeIf(File::exists)?.readBytes()
                    replacedFile = target
                    save(context, edited.uri, "thumbnail", thumbnailName)
                } ?: return@withLock null
                currentCoroutineContext().ensureActive()
                if (!isCurrentTarget() || getActiveProfile(context) != profile) return@withLock null
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
}

private val thumbnailCommitMutex = Mutex()
