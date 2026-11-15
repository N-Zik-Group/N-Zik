package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

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
        if (file in protectedPaths || file.extension != "webp" || !file.isFile ||
            (parent != imports && parent.parentFile != captures)
        ) continue
        if (file.delete() && parent.parentFile == captures) {
            val remaining = parent.listFiles()
            if (remaining?.size == 1 && remaining.single().name == "complete") remaining.single().delete()
            parent.delete()
        }
    }
}
