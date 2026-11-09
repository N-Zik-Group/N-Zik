package app.n_zik.android.core.backup

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.TimeUnit
import timber.log.Timber

/** Owns prepared export files across processes and the document picker handoff. */
internal object DatabaseExportStaging {
    private const val TAG = "DatabaseExportStaging"
    private const val PREFIX = "database-backup-"
    private const val STAGE = ".stage"
    private const val TRASH = ".trash"
    private const val OWNER = ".owner"
    private const val PICKER = ".picker"
    private val pickerGraceMs = TimeUnit.DAYS.toMillis(7)
    private val active = mutableSetOf<File>()

    internal class Lease internal constructor(
        val directory: File,
        private val filesDir: File,
        private var owner: RandomAccessFile?,
    ) : Closeable {
        fun handOffToPicker() = withLock(filesDir) {
            checkNotNull(owner) { "Backup export is not owned" }
            check(directory.resolve(PICKER).createNewFile()) { "Could not retain backup for the picker" }
            release()
        }

        override fun close() {
            runCatching {
                val retired = withLock(filesDir) {
                    if (owner == null) {
                        // The picker launch can fail after handoff. Only retire if no callback
                        // or other process has already acquired the stage.
                        if (!directory.isDirectory) return@withLock null
                        owner = acquire(directory) ?: return@withLock null
                    }
                    release()
                    retire(directory)
                }
                if (retired != null) remove(retired)
            }.onFailure { Timber.tag(TAG).w(it, "Could not clean staged backup export") }
        }

        private fun release() {
            owner?.close()
            owner = null
            active.remove(directory)
        }
    }

    fun create(context: Context): Lease = withLock(context.filesDir) {
        val directory = File.createTempFile(PREFIX, STAGE, context.cacheDir.canonicalFile)
        check(directory.delete() && directory.mkdir()) { "Could not create backup export staging directory" }
        Lease(directory, context.filesDir, checkNotNull(acquire(directory)))
    }

    fun resume(context: Context, file: File): Lease = withLock(context.filesDir) {
        val directory = checkNotNull(file.parentFile).canonicalFile
        require(directory.parentFile == context.cacheDir.canonicalFile && isStage(directory) &&
            file.canonicalFile == directory.resolve(file.name) && file.isFile
        ) { "Invalid staged database export" }
        val owner = checkNotNull(acquire(directory)) { "Backup export is already in use" }
        val picker = directory.resolve(PICKER)
        if (picker.exists() && !picker.delete()) {
            owner.close()
            active.remove(directory)
            error("Could not resume backup export")
        }
        Lease(directory, context.filesDir, owner)
    }

    fun cleanAbandoned(context: Context, now: Long = System.currentTimeMillis()) {
        val retired = withLock(context.filesDir) {
            context.cacheDir.canonicalFile.listFiles().orEmpty().mapNotNull { directory ->
                if (!directory.isDirectory || directory.canonicalFile != directory ||
                    !directory.name.startsWith(PREFIX)
                ) return@mapNotNull null
                if (directory.name.endsWith(TRASH)) return@mapNotNull directory
                if (!isStage(directory) || directory in active) return@mapNotNull null

                val picker = directory.resolve(PICKER)
                val ownerFile = directory.resolve(OWNER)
                // Retain a picker handoff across process recreation. Older prepared exports
                // lacked an owner and marker, so give those the same grace from their mtime.
                val retainedAt = if (picker.exists()) picker.lastModified()
                    else if (!ownerFile.exists()) directory.lastModified() else null
                if (retainedAt != null && now - retainedAt < pickerGraceMs) return@mapNotNull null

                val owner = acquire(directory) ?: return@mapNotNull null
                owner.close()
                active.remove(directory)
                retire(directory)
            }
        }
        retired.forEach(::remove)
    }

    private fun acquire(directory: File): RandomAccessFile? {
        // POSIX locks are process-owned. Closing another descriptor to an active owner's
        // inode can release its lock, so do not open it again in this process.
        if (directory in active) return null
        val owner = RandomAccessFile(directory.resolve(OWNER), "rw")
        try {
            val lock = try {
                owner.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock != null) {
                active.add(directory)
                return owner
            }
        } catch (error: Throwable) {
            owner.close()
            throw error
        }
        owner.close()
        return null
    }

    private fun isStage(directory: File): Boolean =
        directory.name.startsWith(PREFIX) && directory.name.endsWith(STAGE)

    private fun retire(directory: File): File {
        val retired = directory.resolveSibling(directory.name.removeSuffix(STAGE) + TRASH)
        check(directory.renameTo(retired)) { "Could not retire backup export staging directory" }
        return retired
    }

    private fun remove(directory: File) {
        if (!directory.deleteRecursively() && directory.exists()) {
            Timber.tag(TAG).w("Could not remove abandoned backup export")
        }
    }

    @Synchronized
    private fun <T> withLock(filesDir: File, action: () -> T): T =
        RandomAccessFile(filesDir.resolve("database-export.lock"), "rw").use { file ->
            file.channel.lock().use { action() }
        }
}
