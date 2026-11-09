package app.n_zik.android.components.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import app.n_zik.android.components.ui.screens.profiles.decodeLimitedBitmap
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import timber.log.Timber

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
        val bytes = context.contentResolver.openInputStream(source)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_IMAGE_BYTES) { "Image exceeds size limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: return null
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
