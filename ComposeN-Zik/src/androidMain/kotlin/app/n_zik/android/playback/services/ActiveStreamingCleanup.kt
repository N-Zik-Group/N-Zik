package app.n_zik.android.playback.services

import android.content.Context
import android.system.Os
import android.system.OsConstants
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.profiles.LegacyMediaAccess
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.isProcessStorageProfile
import app.n_zik.android.core.profiles.mediaCacheDir
import app.n_zik.android.core.profiles.profileStorageIdentity
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/** Only a bound, private streaming cache can delete keys through its live Media3 index. */
@UnstableApi
internal object ActiveStreamingCleanup {
    data class BoundCache(val owner: ProfileStorageIdentity, val root: File, val cache: SimpleCache)
    data class Span(val file: File, val identity: String)
    data class Target(
        val bound: BoundCache,
        val rootIdentity: String,
        val spans: Map<String, List<Span>>,
    ) {
        val displayedFiles get() = spans.flatMap { (key, files) ->
            listOf(key) + files.map { it.file.relativeTo(bound.root).invariantSeparatorsPath }
        }
    }

    fun hasRemovableData(context: Context, expected: ProfileStorageIdentity, bound: BoundCache?, playing: String?): Boolean =
        bound != null && valid(context, expected, bound) &&
            bound.cache.keys.any { it != playing && !StreamingCacheReaders.isReading(bound.cache, it) &&
                bound.cache.getCachedSpans(it).isNotEmpty() }

    fun capture(context: Context, expected: ProfileStorageIdentity, bound: BoundCache?, playing: String?): Target? {
        if (bound == null || !valid(context, expected, bound)) return null
        val rootIdentity = physicalIdentity(bound.root, directory = true)
        val spans = bound.cache.keys.sorted().mapNotNull { key ->
            if (key == playing) return@mapNotNull null
            StreamingCacheReaders.ifIdle(bound.cache, setOf(key)) {
                synchronized(bound.cache) { snapshot(bound, key) }
            }?.takeIf { it.isNotEmpty() }?.let { key to it }
        }.toMap()
        return Target(bound, rootIdentity, spans)
    }

    fun execute(context: Context, confirmed: Target, bound: BoundCache?, playing: String?): Boolean =
        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            if (bound != confirmed.bound || !valid(context, confirmed.bound.owner, confirmed.bound) ||
                (playing != null && playing in confirmed.spans.keys)
            ) return@withProfileStorageMutation false
            val cache = confirmed.bound.cache
            StreamingCacheReaders.ifIdle(cache, confirmed.spans.keys) {
                synchronized(cache) {
                    if (physicalIdentity(confirmed.bound.root, directory = true) != confirmed.rootIdentity ||
                        confirmed.spans.any { (key, spans) -> snapshot(confirmed.bound, key) != spans }
                    ) return@synchronized false
                    confirmed.spans.keys.forEach(cache::removeResource)
                    confirmed.spans.keys.all { cache.getCachedSpans(it).isEmpty() } &&
                        confirmed.spans.values.flatten().none { it.file.exists() }
                }
            } == true
        }

    private fun valid(context: Context, expected: ProfileStorageIdentity, bound: BoundCache): Boolean =
        expected == bound.owner && isProcessStorageProfile(context, expected.id) &&
            profileStorageIdentity(context, expected.id) == expected &&
            !LegacyMediaAccess.isProtected(context, expected.id, ProfileDataItem.MEDIA_CACHE) &&
            mediaCacheDir(context, expected.id)?.let { configured ->
                val root = checkNotNull(configured.parentFile).canonicalFile.resolve(configured.name)
                root == bound.root && root.canonicalFile == root && root.isDirectory
            } == true

    private fun snapshot(bound: BoundCache, key: String): List<Span> = bound.cache.getCachedSpans(key).map { span ->
        val file = checkNotNull(span.file)
        check(file.toPath().startsWith(bound.root.toPath()) && file != bound.root)
        check(file.parentFile?.parentFile == bound.root || file.parentFile == bound.root)
        Span(file, physicalIdentity(file, directory = false))
    }

    private fun physicalIdentity(file: File, directory: Boolean): String {
        check(file.canonicalFile == file.absoluteFile)
        if (File.separatorChar == '\\') {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            check(if (directory) attributes.isDirectory else attributes.isRegularFile)
            val key = attributes.fileKey()?.toString() ?: file.path
            return if (directory) key else "$key:${attributes.size()}:${attributes.lastModifiedTime()}"
        }
        val stat = Os.lstat(file.path)
        val parent = Os.lstat(checkNotNull(file.parentFile).path)
        check(stat.st_dev == parent.st_dev)
        check(if (directory) OsConstants.S_ISDIR(stat.st_mode) else OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1L)
        val key = "${stat.st_dev}:${stat.st_ino}"
        return if (directory) key else "$key:${stat.st_size}:${stat.st_mtime}"
    }
}
