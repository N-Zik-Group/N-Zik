package app.n_zik.android.playback.services.automotive.models

import java.io.File
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks replaced pixels even when the target file retains its size and timestamp. */
object ArtworkRevisions {
    private val lock = Any()
    private val revision = MutableStateFlow(0L)
    private var globalRevision = 0L
    private val sources = mutableMapOf<String, Long>()

    internal val changes: StateFlow<Long> = revision.asStateFlow()

    fun invalidate() = synchronized(lock) {
        globalRevision = revision.value + 1
        sources.clear()
        revision.value = globalRevision
    }

    internal fun invalidateFiles(files: Collection<File>) =
        invalidateSources(files.map { it.absolutePath })

    internal fun invalidateFile(file: File) = invalidateFiles(listOf(file))

    internal fun invalidateSources(urls: Collection<String>, profile: String? = null) = synchronized(lock) {
        if (urls.isNotEmpty()) {
            val next = revision.value + 1
            urls.forEach { sources[sourceKey(it, profile)] = next }
            revision.value = next
        }
    }

    internal fun notifyRemoteRefresh() = synchronized(lock) { revision.value += 1 }

    internal fun current(): Long = revision.value

    internal fun global(): Long = synchronized(lock) { globalRevision }

    internal fun current(source: String, profile: String? = null, includeGlobal: Boolean = true): Long = synchronized(lock) {
        sources[sourceKey(source, profile)] ?: if (includeGlobal) globalRevision else 0L
    }

    private fun sourceKey(source: String, profile: String?): String = when {
        source.startsWith("file:", ignoreCase = true) -> Uri.parse(source).path?.let(::fileKey) ?: source
        source.startsWith("content:", ignoreCase = true) -> "$profile|$source"
        else -> fileKey(source)
    }
    private fun fileKey(path: String): String {
        val localPath = if (File.separatorChar == '\\' && path.startsWith('/') && path.getOrNull(2) == ':') {
            path.substring(1)
        } else path
        return File(localPath).normalize().path
    }
}
