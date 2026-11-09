package app.n_zik.android.components.image

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Owns the edited copy until the caller saves or discards its draft. */
internal class PreparedImage internal constructor(
    val file: File,
    private val context: Context,
) : Closeable {
    val uri: Uri get() = Uri.fromFile(file)
    private val closed = AtomicBoolean(false)

    init {
        file.parentFile?.name?.let(PickedImageEditor::retainDirectory)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                PickedImageEditor.discard(context, file.parentFile?.name)
            }
        }
    }
}

/** Edits an isolated copy; callers retain ownership of persistence and target identity. */
internal object PickedImageEditor {
    internal const val SESSION_EXTRA = "image_edit_session"
    private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
    private const val TAG = "PickedImageEditor"
    private val sessions = ConcurrentHashMap<String, EditSession>()
    private val ownedDirectories = ConcurrentHashMap.newKeySet<String>()

    internal class EditSession(
        val id: String,
        val context: Context,
        val source: File,
        val mimeType: String,
    ) {
        init {
            retainDirectory(id)
        }

        val result = CompletableDeferred<PreparedImage?>()
        val output = File(source.parentFile, "cropped")
        // uCrop owns a non-cancellable AsyncTask. Its callback is the write-completion barrier.
        var exportFinished: CompletableDeferred<Unit>? = null
        var outputReceived = false
    }

    /** Returns a staged result, or null when editing was cancelled or unavailable. */
    suspend fun edit(context: Context, source: Uri): PreparedImage? = withContext(NzikDispatchers.UI) {
        val activity = context.activity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) return@withContext null
        val app = context.applicationContext
        val id = UUID.randomUUID().toString()
        retainDirectory(id)
        var delivered = false
        var session: EditSession? = null
        try {
            session = withContext(NzikDispatchers.DATA) {
                cleanupOrphans(app)
                val copied = copyImage(app, source, directory(app, id), "input")
                EditSession(id, app, copied.first, copied.second)
            }
            sessions[id] = session
            activity.startActivity(editIntent(activity, id))
            val prepared = session.result.await()
            delivered = prepared != null
            prepared
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Could not prepare the image editor")
            Toaster.e(R.string.image_editor_failed)
            null
        } finally {
            sessions.remove(id)
            if (!delivered) {
                session?.result?.cancel()
                withContext(NonCancellable + NzikDispatchers.DATA) {
                    val activeSession = session
                    if (activeSession != null) cancelAndDiscard(activeSession) else discard(app, id)
                }
            }
        }
    }

    internal fun session(id: String?): EditSession? = id?.let(sessions::get)

    internal fun editIntent(context: Context, id: String): Intent =
        Intent(context, PickedImageEditorActivity::class.java).putExtra(SESSION_EXTRA, id)

    internal suspend fun prepareResult(session: EditSession, resultUri: Uri): PreparedImage = withContext(NzikDispatchers.DATA) {
        require(resultUri == Uri.fromFile(session.output)) { "Unexpected crop output" }
        // Detect the actual MIME: uCrop may copy an unchanged input instead of recompressing it.
        val file = copyImage(session.context, resultUri, requireNotNull(session.source.parentFile), "prepared").first
        PreparedImage(file, session.context)
    }

    internal suspend fun cancelAndDiscard(session: EditSession) = withContext(NonCancellable + NzikDispatchers.DATA) {
        session.exportFinished?.await()
        discard(session.context, session.id)
    }

    internal fun discard(context: Context, id: String?) {
        if (id == null || !runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) return
        val folder = directory(context, id)
        if (folder.exists() && !folder.deleteRecursively()) Timber.tag(TAG).w("Could not remove an image edit directory")
        ownedDirectories.remove(id)
    }

    private fun directory(context: Context, id: String): File = File(File(context.cacheDir, "image_edit"), id)

    internal fun retainDirectory(id: String) {
        ownedDirectories.add(id)
    }

    /** Sweeps abandoned process state without touching live editors or unapplied image drafts. */
    internal suspend fun cleanupOrphans(context: Context) = withContext(NzikDispatchers.DATA) {
        File(context.cacheDir, "image_edit").listFiles()?.filter { it.name !in ownedDirectories }?.forEach {
            discard(context, it.name)
        }
    }

    internal suspend fun copyImage(context: Context, source: Uri, directory: File, name: String): Pair<File, String> {
        require(source.scheme == "content" || source.scheme == "file") { "Unsupported image URI" }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create image edit directory" }
        val pending = File(directory, "$name.pending")
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot open image")
            input.use { stream ->
                pending.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var count = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val size = stream.read(buffer)
                        if (size < 0) break
                        count += size
                        if (count > MAX_IMAGE_BYTES) throw IOException("Image exceeds the editor size limit")
                        output.write(buffer, 0, size)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(pending.absolutePath, bounds)
            val mimeType = bounds.outMimeType?.takeIf { it.startsWith("image/") }
                ?: throw IOException("Unsupported image data")
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image dimensions")
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 128) sample *= 2
            val checkBitmap = BitmapFactory.decodeFile(pending.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("Cannot decode image data")
            checkBitmap.recycle()
            val extension = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/heif", "image/heic" -> "heic"
                else -> "img"
            }
            val file = File(directory, "$name.$extension")
            if (!pending.renameTo(file)) throw IOException("Cannot prepare image file")
            return file to mimeType
        } finally {
            if (pending.exists() && !pending.delete()) Timber.tag(TAG).w("Could not remove an incomplete image copy")
        }
    }

    private fun Context.activity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val base = current.baseContext
            if (base === current) return null
            current = base
        }
        return null
    }
}
