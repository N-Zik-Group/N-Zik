package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.json.JSONObject
import timber.log.Timber

internal const val SubmissionPreviewMaxEdge = 1920

internal fun submissionPreviewVariantSlots(slots: List<String>, glass: Boolean): List<String> {
    val suffix = if (glass) "-glass" else "-normal"
    return slots.filter { it.endsWith(suffix) || (!it.endsWith("-normal") && !it.endsWith("-glass")) }
}

/** Keeps explicitly removed extras out of inherited revisions and resumed drafts. */
@Serializable
internal data class SubmissionPreviewSelection(val removedExtras: Set<String> = emptySet()) {
    fun includes(slot: String): Boolean = slot in automaticPreviews || slot !in removedExtras

    fun withoutExtra(slot: String): SubmissionPreviewSelection {
        require(slot !in automaticPreviews) { "Automatic preview slots cannot be removed" }
        return copy(removedExtras = removedExtras + slot)
    }

    fun published(previews: List<StorePreview>, reuseAutomatic: Boolean): Map<String, StorePreview> =
        previews.associateBy { it.path.substringAfterLast('/').substringBeforeLast('.') }
            .filterKeys { includes(it) && (reuseAutomatic || it !in automaticPreviews) }

    fun gallerySlots(published: Set<String>, replacements: Set<String>): List<String> {
        val ready = (published + replacements).filter(::includes).toSet()
        return requiredPreviews + playbackPreviews.filter { it in ready } + (ready - automaticPreviews.toSet())
    }
}

/** Identifies GIF by its header because document providers can report a generic MIME type. */
internal fun InputStream.readSubmissionGifOrNull(): ByteArray? {
    val source = buffered()
    val signature = ByteArray(6)
    source.mark(signature.size)
    try {
        DataInputStream(source).readFully(signature)
    } catch (_: EOFException) {
        return null
    }
    if (signature.decodeToString() !in listOf("GIF87a", "GIF89a")) return null
    source.reset()
    val bytes = source.readThemeBytes(SubmissionPreviewLimit)
    require(bytes.size >= 14 && bytes.last() == 0x3b.toByte())
    fun dimension(offset: Int) =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    require(dimension(6) in 1..SubmissionPreviewMaxEdge && dimension(8) in 1..SubmissionPreviewMaxEdge)
    return bytes
}

/** Decode a small sample to reject containers that have no image before keeping their original bytes. */
internal fun requireDecodableSubmissionGif(bytes: ByteArray) {
    val options = BitmapFactory.Options().apply { inSampleSize = 16 }
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
        "Preview GIF has no decodable image"
    }
    bitmap.recycle()
}

internal fun submissionPreviewUris(previews: JSONObject?): Set<String> =
    previews?.keys()?.asSequence()?.mapNotNull { previews.opt(it) as? String }?.toSet().orEmpty()

internal fun draftPreviewUris(draft: String?): Set<String> =
    draft?.let { runCatching { submissionPreviewUris(JSONObject(it).optJSONObject("previews")) }.getOrNull() }.orEmpty()

internal class ThemeSubmissionCleanup : ViewModel() {
    var onExit: (() -> Unit)? = null

    override fun onCleared() {
        onExit?.invoke()
    }
}

private val submissionPreviewCleanup = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)

internal fun removeUnusedSubmissionPreviews(
    context: Context,
    drafts: SharedPreferences,
    candidates: Set<String>,
    retained: Set<String> = emptySet(),
) {
    if (candidates.isEmpty()) return
    submissionPreviewCleanup.launch {
        try {
            val protected = (retained + drafts.all.values.flatMap { draftPreviewUris(it as? String) })
                .mapNotNull(::submissionPreviewFile)
            pruneSubmissionPreviewFiles(context.filesDir, candidates.mapNotNull(::submissionPreviewFile), protected)
        } catch (error: java.io.IOException) {
            Timber.tag("ThemeStore").w(error, "Could not clean up unused submission previews")
        }
    }
}

private fun submissionPreviewFile(value: String): File? =
    Uri.parse(value).takeIf { it.scheme == "file" && it.authority.isNullOrEmpty() && it.isAbsolute }
        ?.path?.takeIf { it.startsWith('/') }?.let(::File)

internal fun pruneSubmissionPreviewFiles(filesDir: File, candidates: Iterable<File>, protected: Iterable<File>) {
    val root = filesDir.canonicalFile
    val imports = File(root, "theme-submission-imports")
    val captures = File(root, "theme-submission-previews")
    val protectedPaths = protected.map(File::getCanonicalFile).toSet()
    for (candidate in candidates) {
        val file = candidate.canonicalFile
        val parent = file.parentFile ?: continue
        val managedImport = parent == imports && file.extension in setOf("webp", "gif")
        val managedCapture = parent.parentFile == captures && file.extension == "webp"
        if (file in protectedPaths || !file.isFile || (!managedImport && !managedCapture)) continue
        if (file.delete() && parent.parentFile == captures) {
            val remaining = parent.listFiles()
            if (remaining?.size == 1 && remaining.single().name == "complete") remaining.single().delete()
            parent.delete()
        }
    }
}
