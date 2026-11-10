package app.n_zik.android.download.utils

import android.content.Context
import android.system.Os
import android.system.OsConstants
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import app.n_zik.android.core.profiles.LegacyMediaAccess
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.downloadsDir
import app.n_zik.android.core.profiles.isProcessStorageProfile
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import app.n_zik.android.download.services.ProfileDownloadCommands
import app.n_zik.android.playback.services.StreamingCacheReaders
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Uses only the manager already bound to the confirmed active profile. */
@UnstableApi
internal object ActiveDownloadCleanup {
    data class Span(val file: File, val fingerprint: String)

    data class Entry(
        val request: DownloadRequest,
        val startTime: Long,
        val spans: List<Span>,
    )

    data class Target(
        val commands: ProfileDownloadCommands,
        val root: File,
        val rootIdentity: Pair<Long, Long>?,
        val entries: List<Entry>,
        val protectedMediaId: String?,
    ) {
        val eligibleEntries: List<Entry>
            get() = ActiveDownloadCleanup.excludePlayingEntries(entries, protectedMediaId)
    }

    enum class Outcome { COMPLETED, SKIPPED, FAILED }

    suspend fun hasRemovableEntries(context: Context, currentMediaId: String?): Boolean {
        val manager = withContext(NzikDispatchers.UI) { MyDownloadHelper.getDownloadManager(context) }
        return withContext(NzikDispatchers.DATA) {
            val requests = manager.downloadIndex.getDownloads().use { rows ->
                buildList { while (rows.moveToNext()) add(rows.download.request) }
            }
            val protectedKeys = requests.playingKeys(currentMediaId)
            requests.any { it.cacheKey !in protectedKeys }
        }
    }

    suspend fun capture(context: Context, profile: ProfileStorageIdentity, currentMediaId: String?): Target? {
        if (!isProcessStorageProfile(context, profile.id) ||
            profile != profileStorageIdentity(context, profile.id) ||
            LegacyMediaAccess.isProtected(context, profile.id, ProfileDataItem.DOWNLOADS)
        ) return null
        val commands = ProfileDownloadCommands.capture(context)?.takeIf {
            it.consumer == profile && it.owner == profile
        } ?: return null
        val root = downloadsDir(context, profile.id)?.let {
            checkNotNull(it.parentFile).canonicalFile.resolve(it.name)
        } ?: return null
        val manager = withContext(NzikDispatchers.UI) { MyDownloadHelper.getDownloadManager(context) }
        val entries = readEntries(context, manager, root)
        val rootIdentity = if (root.exists()) {
            val stat = Os.lstat(root.path)
            val base = Os.lstat(checkNotNull(root.parentFile).path)
            check(OsConstants.S_ISDIR(stat.st_mode) && stat.st_dev == base.st_dev)
            stat.st_dev to stat.st_ino
        } else null
        return Target(commands, root, rootIdentity, entries, currentMediaId)
    }

    suspend fun execute(context: Context, target: Target, currentMediaId: () -> String?): Outcome =
        withContext(NzikDispatchers.UI) {
            if (target.commands != ProfileDownloadCommands.capture(context)) return@withContext Outcome.SKIPPED
            val confirmed = target.eligibleEntries
            if (confirmed.isEmpty()) return@withContext Outcome.COMPLETED

            val manager = MyDownloadHelper.getDownloadManager(context)
            if (!manager.isInitialized) return@withContext Outcome.SKIPPED
            val cache = MyDownloadHelper.getDownloadCache(context)
            val reserved = confirmed.groupBy { it.cacheKey }
                .mapNotNull { (key, entries) ->
                    val ids = entries.map { it.request.id }.toSet()
                    StreamingCacheReaders.reserveIdle(cache, ids + key)?.let { entries to it }
                }
            if (reserved.isEmpty()) return@withContext Outcome.SKIPPED
            val idle = CompletableDeferred<Unit>()
            val remaining = mutableSetOf<String>()
            val finished = CompletableDeferred<Boolean>()
            var changed = false
            var submitted = false
            var abandoned = false
            val temporarilyPause = !manager.isIdle && !manager.downloadsPaused
            fun release() {
                reserved.forEach { (_, reservation) -> reservation.close() }
                if (temporarilyPause && manager.downloadsPaused &&
                    target.commands == ProfileDownloadCommands.capture(context) &&
                    !target.commands.isPaused(context)
                ) manager.resumeDownloads()
            }
            val listener = object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) {
                    changed = true
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    changed = true
                    remaining.remove(download.request.id)
                    if (submitted && remaining.isEmpty()) finish(downloadManager, true)
                }

                override fun onIdle(downloadManager: DownloadManager) {
                    if (submitted) finish(downloadManager, remaining.isEmpty()) else idle.complete(Unit)
                }

                private fun finish(downloadManager: DownloadManager, success: Boolean) {
                    downloadManager.removeListener(this)
                    if (abandoned) release()
                    finished.complete(success)
                }
            }
            manager.addListener(listener)
            try {
                if (temporarilyPause) manager.pauseDownloads()
                if (!manager.isIdle && withTimeoutOrNull(5_000L) { idle.await(); true } != true) {
                    return@withContext Outcome.SKIPPED
                }
                changed = false
                val current = capture(context, target.commands.consumer, target.protectedMediaId)
                // This comparison and enqueue share the manager's application looper turn.
                // Its background queue is idle, and the listener catches any completed mutation
                // that occurred while the physical snapshot was read.
                if (current == null || current.root != target.root || current.rootIdentity != target.rootIdentity ||
                    changed || !manager.isIdle || target.commands != ProfileDownloadCommands.capture(context)
                ) return@withContext Outcome.SKIPPED

                // A transfer may have flushed new data while stopping. Preserve every changed
                // entry and any other request sharing its physical cache key.
                val unchanged = unchangedEntries(target.entries, current.entries, currentMediaId())
                val eligible = reserved.flatMap { (entries, _) -> entries.filter(unchanged::contains) }
                if (eligible.isEmpty()) return@withContext Outcome.SKIPPED

                for (entry in eligible) {
                    val id = entry.request.id
                    manager.removeDownload(id)
                    remaining.add(id)
                    submitted = true
                }
                if (withTimeoutOrNull(30_000L) { finished.await() } != true) return@withContext Outcome.FAILED

                val ids = eligible.map { it.request.id }.toSet()
                val keys = eligible.map { it.cacheKey }.toSet()
                withContext(NzikDispatchers.DATA) {
                    val remainingRows = manager.downloadIndex.getDownloads().use { rows ->
                        var found = false
                        while (!found && rows.moveToNext()) found = rows.download.request.id in ids
                        found
                    }
                    val spansRemain = keys.any { cache.getCachedSpans(it).isNotEmpty() }
                    when {
                        remainingRows || spansRemain -> Outcome.FAILED
                        eligible.size != confirmed.size -> Outcome.SKIPPED
                        else -> Outcome.COMPLETED
                    }
                }
            } finally {
                // The manager can still be deleting after the UI timeout or cancellation.
                if (!submitted || finished.isCompleted) {
                    manager.removeListener(listener)
                    release()
                } else {
                    abandoned = true
                }
            }
        }

    internal fun unchangedEntries(confirmed: List<Entry>, current: List<Entry>, playing: String?): Set<Entry> {
        val confirmedSet = confirmed.toSet()
        val currentSet = current.toSet()
        val changedKeys = (
            confirmed.filter { it !in currentSet || it.request.isPlaying(playing) } +
                current.filterNot(confirmedSet::contains)
            ).mapTo(mutableSetOf()) { it.cacheKey }
        return confirmedSet.filterTo(mutableSetOf()) { it.cacheKey !in changedKeys }
    }

    private val Entry.cacheKey get() = request.cacheKey
    private val DownloadRequest.cacheKey get() = customCacheKey ?: id

    private fun List<DownloadRequest>.playingKeys(mediaId: String?): Set<String> =
        filter { it.isPlaying(mediaId) }.mapTo(mutableSetOf()) { it.cacheKey }

    private fun excludePlayingEntries(entries: List<Entry>, playing: String?): List<Entry> {
        val protectedKeys = entries.map { it.request }.playingKeys(playing)
        return entries.filter { it.cacheKey !in protectedKeys }
    }

    private suspend fun readEntries(
        context: Context,
        manager: DownloadManager,
        root: File,
    ): List<Entry> = withContext(NzikDispatchers.DATA) {
        check(root.canonicalFile == root.absoluteFile)
        val cache = MyDownloadHelper.getDownloadCache(context)
        manager.downloadIndex.getDownloads().use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val download = rows.download
                    val request = download.request
                    val spans = cache.getCachedSpans(request.cacheKey).map { span ->
                        val file = checkNotNull(span.file)
                        check(file.canonicalFile == file.absoluteFile && file.path.startsWith(root.path + File.separator))
                        Span(file, ProfileResetSnapshot.fingerprint(file))
                    }
                    add(Entry(request, download.startTimeMs, spans.sortedBy { it.file.path }))
                }
            }.sortedBy { it.request.id }
        }
    }

    private fun DownloadRequest.isPlaying(mediaId: String?): Boolean =
        mediaId != null && (id == mediaId || customCacheKey == mediaId)
}
