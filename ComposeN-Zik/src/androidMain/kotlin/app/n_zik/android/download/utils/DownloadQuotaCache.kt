package app.n_zik.android.download.utils

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import java.io.File

/** Limits new committed offline audio without evicting any previously committed span. */
@UnstableApi
internal class DownloadQuotaCache(
    private val delegate: Cache,
    private val quota: Long,
    private val root: File,
) : Cache by delegate {
    override fun startFile(key: String, position: Long, length: Long): File = synchronized(delegate) {
        val remaining = remainingBytes()
        if (remaining == 0L || length > remaining) throw Cache.CacheException("The offline download limit has been reached")
        delegate.startFile(key, position, length)
    }

    override fun commitFile(file: File, length: Long) = synchronized(delegate) {
        if (length > remainingBytes()) {
            // Only the new, still-uncommitted file belongs to the rejected write.
            val candidate = file.absoluteFile
            if (candidate.canonicalFile == candidate && candidate.toPath().startsWith(root.toPath()) && candidate != root) {
                candidate.delete()
            }
            throw Cache.CacheException("The offline download limit has been reached")
        }
        delegate.commitFile(file, length)
    }

    private fun remainingBytes(): Long = (quota - delegate.cacheSpace).coerceAtLeast(0L)
}
