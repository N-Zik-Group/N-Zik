package app.n_zik.android.components.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.components.ui.screens.profiles.decodeLimitedBitmap
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber

/** Serializes in-process cover edits, including rollback, with database backup imports. */
internal val ownedCoverMutationMutex = Mutex()

internal const val ORIGINAL_ARTWORK_QUALITY_KEY = "originalCustomArtworkQuality"
internal const val OWNED_COVER_EXTENSION_PATTERN = "jpg|png|webp|gif|heic|img"

internal fun keepsOriginalArtwork(context: Context): Boolean =
    profileDataPrefs(context, getActiveProfile(context)).getBoolean(ORIGINAL_ARTWORK_QUALITY_KEY, false)

/** Verifies image content with a small decode, independent of its stored dimensions. */
internal fun validateOwnedArtwork(file: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid artwork dimensions" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 128) sample *= 2
    val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
        inSampleSize = sample
    })) { "Unreadable artwork" }
    bitmap.recycle()
}

/** Decodes before replacing an app-owned image, preserving the previous file on failure. */
internal fun saveOwnedImage(
    context: Context,
    source: Uri,
    directory: String,
    name: String,
): Uri? {
    var bitmap: Bitmap? = null
    return try {
        val root = context.filesDir.canonicalFile
        val target = File(File(root, directory), name).canonicalFile
        require(target.path.startsWith(root.path + File.separator))
        if (keepsOriginalArtwork(context)) {
            writeOwnedImageAtomically(target) { output ->
                check(copyArtwork(context, source, output)) { "Cannot open artwork" }
                output.flush()
                validateOwnedArtwork(File(target.path + ".new"))
            }
            ArtworkRevisions.invalidate()
            return Uri.fromFile(target)
        }
        val buffer = ByteArrayOutputStream()
        if (!copyArtwork(context, source, buffer)) return null
        val bytes = buffer.toByteArray()
        val decoded = decodeLimitedBitmap(bytes, 1000) ?: return null
        bitmap = decoded
        writeOwnedImageAtomically(target) { output ->
            check(decoded.compress(Bitmap.CompressFormat.JPEG, 85, output))
        }
        ArtworkRevisions.invalidate()
        Uri.fromFile(target)
    } catch (error: Exception) {
        Timber.tag("OwnedImageStorage").e(error, "Could not save edited image")
        null
    } finally {
        bitmap?.recycle()
    }
}

private fun copyArtwork(context: Context, source: Uri, output: OutputStream): Boolean {
    val input = context.contentResolver.openInputStream(source) ?: return false
    input.use {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_IMAGE_BYTES) { "Image exceeds size limit" }
            output.write(buffer, 0, count)
        }
    }
    return true
}

/** The caller supplies an app-owned destination; no picked/gallery path is ever deleted. */
internal fun writeOwnedImageAtomically(target: File, write: (OutputStream) -> Unit) {
    val directory = requireNotNull(target.parentFile)
    if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create image directory")
    val atomic = AtomicFile(target)
    val output = atomic.startWrite()
    try {
        write(output)
        atomic.finishWrite(output)
        // AtomicFile logs a failed rename without throwing; do not publish an uncommitted URI.
        if (File(target.path + ".new").exists() || !target.isFile) {
            throw IOException("Could not publish the completed image")
        }
    } catch (error: Throwable) {
        atomic.failWrite(output)
        throw error
    }
}

/** Rolls back a failed cover commit and expires artwork read during the aborted replacement. */
internal fun restoreOwnedCover(target: File, previousBytes: ByteArray?) {
    if (previousBytes == null) {
        check(!target.exists() || target.delete()) { "Cannot discard uncommitted cover" }
    } else {
        writeOwnedImageAtomically(target) { it.write(previousBytes) }
    }
    ArtworkRevisions.invalidate()
}

private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
