package app.n_zik.android.components.image

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Holds a validated snapshot while Android's document picker is open. */
internal class CoverImageExport private constructor(
    private val image: PreparedImage,
    val mimeType: String,
) : Closeable by image {
    val suggestedName: String get() = image.file.name

    /** Writes the original image bytes to the selected document. */
    suspend fun writeTo(context: Context, destination: Uri) = withContext(NzikDispatchers.DATA) {
        try {
            image.file.inputStream().use { input ->
                val output = context.contentResolver.openOutputStream(destination, "wt")
                    ?: throw IOException("Could not open cover export destination")
                output.use {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        it.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: Exception) {
            discardDestination(context, destination)
            throw error
        }
    }

    companion object {
        /** Removes an unused document created by the export's SAF picker. */
        internal fun discardDestination(context: Context, destination: Uri) {
            if (destination.scheme != "content") return
            try {
                DocumentsContract.deleteDocument(context.contentResolver, destination)
            } catch (error: Exception) {
                Timber.tag("CoverImageExport").w(error, "Could not remove unfinished cover export")
            }
        }

        suspend fun prepare(context: Context, uri: Uri): CoverImageExport =
            prepare(context) { directory ->
                PickedImageEditor.copyImage(context, uri, directory, "cover")
            }

        suspend fun prepare(context: Context, bytes: ByteArray): CoverImageExport =
            prepare(context) { directory ->
                PickedImageEditor.copyImage(bytes, directory, "cover")
            }

        private suspend fun prepare(
            context: Context,
            copy: suspend (File) -> Pair<File, String>,
        ): CoverImageExport {
            val app = context.applicationContext
            val id = UUID.randomUUID().toString()
            PickedImageEditor.retainDirectory(id)
            var prepared = false
            try {
                val export = withContext(NzikDispatchers.DATA) {
                    val (file, mimeType) = copy(PickedImageEditor.directory(app, id))
                    CoverImageExport(PreparedImage(file, app), mimeType)
                }
                prepared = true
                return export
            } finally {
                if (!prepared) withContext(NonCancellable + NzikDispatchers.DATA) {
                    PickedImageEditor.discard(app, id)
                }
            }
        }
    }
}
