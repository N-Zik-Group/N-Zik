package app.n_zik.android.core.backup

import android.content.Context
import android.os.Build
import android.system.Os
import app.n_zik.android.components.settings.data.DeferredProfileCleanup
import app.n_zik.android.utils.isProfileIdSafe
import app.n_zik.android.utils.PROFILE_NAMES_FILE_NAME
import app.n_zik.android.core.profiles.ProfileCacheLocationMigration
import app.n_zik.android.core.profiles.LegacyMediaAccess
import app.n_zik.android.core.profiles.LegacyMediaPublication
import app.n_zik.android.core.profiles.LegacyWaveformPublication
import app.n_zik.android.core.profiles.LegacyImagePublication
import app.n_zik.android.core.profiles.ProtectedProfileHistory
import app.n_zik.android.core.profiles.bindSelectedProcessStorageProfile
import app.n_zik.android.download.utils.InactiveDownloadCleanup
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import app.n_zik.android.core.rescue.RescueFiles
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import timber.log.Timber

/** Serializes database replacement with startup and rolls back an interrupted installation. */
internal object DatabaseRestoreJournal {
    private const val TAG = "DatabaseRestoreJournal"
    private const val VERSION = 1
    private const val MAX_FILES = 50_004
    private const val IMPORT_PREFIX = "database-import-"
    private const val STAGE_SUFFIX = ".stage"
    private const val TRASH_SUFFIX = ".trash"
    private const val OWNER = ".owner"
    private const val RETIRED_JOURNAL_PREFIX = ".database-restore-retired-"
    private const val RETIRED_BACKUPS_PREFIX = ".rescue-backups-retired-"
    private val mainProcessLeases = mutableMapOf<File, FileLock>()
    private var lockedDirectory: File? = null
    private val activeImports = mutableSetOf<File>()

    /** Called before either process can access the library. The main process holds a lease until exit. */
    fun onProcessStart(context: Context, mainProcess: Boolean) {
        val filesDir = context.filesDir.canonicalFile
        val databaseDir = checkNotNull(context.getDatabasePath("data.db").parentFile)
        withLock(filesDir) {
            Transaction(filesDir, databaseDir).recover()
            Transaction(filesDir, filesDir, profileState = true).recover()
            ProfileStateArchive.resumePendingNames(context)
            if (mainProcess && mainProcessLeases[filesDir]?.isValid != true) {
                check(!ProfileResetSnapshot.hasPendingOperation(filesDir.resolve("rescue_backups/profiles"))) {
                    "Finish the pending profile operation in Rescue before opening the library"
                }
                val channel = RandomAccessFile(filesDir.resolve("database-main.lock"), "rw").channel
                try {
                    mainProcessLeases[filesDir] = channel.lock()
                    RescueFiles.resumeProfileDeletions(context, beforeStorageBound = true)
                    val profile = bindSelectedProcessStorageProfile(context)
                    InactiveDownloadCleanup.resume(context)
                    DeferredProfileCleanup.resume(context, discardUnstarted = true)
                    LegacyMediaPublication.resumeRescueRecovery(context)
                    ProtectedProfileHistory.resume(context)
                    LegacyMediaPublication.resume(context)
                    LegacyWaveformPublication.resume(context)
                    LegacyImagePublication.resume(context)
                    ProfileCacheLocationMigration.resume(context, profile)
                    LegacyMediaAccess.prepare(context, profile)
                } catch (error: Throwable) {
                    mainProcessLeases.remove(filesDir)
                    channel.close()
                    throw error
                }
            }
        }
    }

    /** Called under the restore lock, which also gates acquisition of the main process lease. */
    fun requireMainProcessStopped(context: Context) {
        val filesDir = context.filesDir.canonicalFile
        check(mainProcessLeases[filesDir]?.isValid != true) { "The main process is using the database" }
        RandomAccessFile(filesDir.resolve("database-main.lock"), "rw").use { file ->
            val lease = try {
                file.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            checkNotNull(lease) { "The main process is using the database" }.release()
        }
    }

    /** Serializes rescue changes with archive installs and main process startup. */
    fun <T> withRescueMutation(context: Context, action: () -> T): T =
        withRescueMutation(context, allowLegacyRecovery = false, action)

    fun <T> withRescueMutation(context: Context, allowLegacyRecovery: Boolean, action: () -> T): T {
        try {
            return withLock(context.filesDir) {
                requireMainProcessStopped(context)
                check(allowLegacyRecovery || !ProtectedProfileHistory.hasPending(context)) {
                    "Finish the pending historical recovery first"
                }
                Transaction(context.filesDir, checkNotNull(context.getDatabasePath("data.db").parentFile)).recover()
                action()
            }
        } finally {
            cleanRetiredFiles(context.filesDir)
        }
    }

    /** Serializes a raw backup snapshot with restores while allowing a live main process. */
    fun <T> withRescueSnapshot(context: Context, action: () -> T): T = withLock(context.filesDir) {
        val databaseDir = checkNotNull(context.getDatabasePath("data.db").parentFile)
        check(!Transaction(context.filesDir, databaseDir).hasPendingRestore()) {
            "The database restore has not finished"
        }
        action()
    }

    /** Serializes storage identity checks and changes with Rescue and library startup. */
    fun <T> withProfileStorageMutation(context: Context, action: () -> T): T =
        withLock(context.filesDir, action)

    /** Keeps import staging alive while its owning process validates and installs the archive. */
    class ImportStage internal constructor(
        val directory: File,
        private val filesDir: File,
        private val owner: RandomAccessFile,
    ) : Closeable {
        override fun close() {
            runCatching {
                val trash = withLock(filesDir) {
                    owner.close()
                    activeImports.remove(directory.canonicalFile)
                    retireImportStage(directory)
                }
                removeImportStage(trash)
            }.onFailure { Timber.tag(TAG).w(it, "Could not clean database import staging directory") }
        }
    }

    fun createImportStage(context: Context): ImportStage = withLock(context.filesDir) {
        val directory = File.createTempFile(IMPORT_PREFIX, STAGE_SUFFIX, context.cacheDir)
        check(directory.delete() && directory.mkdir()) { "Could not create database import staging directory" }
        val owner = RandomAccessFile(directory.resolve(OWNER), "rw")
        try {
            owner.channel.lock()
            activeImports.add(directory.canonicalFile)
            ImportStage(directory, context.filesDir, owner)
        } catch (error: Throwable) {
            owner.close()
            throw error
        }
    }

    /** Removes abandoned imports without touching active imports or pending export-picker files. */
    fun cleanAbandonedImports(context: Context) {
        val cache = context.cacheDir.canonicalFile
        val trash = withLock(context.filesDir) {
            cache.listFiles().orEmpty().mapNotNull { directory ->
                // Closing a second descriptor on a process-owned inode can release its lease.
                if (directory in activeImports || !directory.isDirectory || directory.canonicalFile != directory ||
                    !directory.name.startsWith(IMPORT_PREFIX)
                ) return@mapNotNull null
                when {
                    directory.name.endsWith(TRASH_SUFFIX) -> directory
                    directory.name.endsWith(STAGE_SUFFIX) && isAbandoned(directory) -> retireImportStage(directory)
                    else -> null
                }
            }
        }
        trash.forEach(::removeImportStage)
    }

    private fun isAbandoned(directory: File): Boolean =
        RandomAccessFile(directory.resolve(OWNER), "rw").use { file ->
            try {
                file.channel.tryLock()?.use { true } ?: false
            } catch (_: OverlappingFileLockException) {
                false
            }
        }

    private fun retireImportStage(directory: File): File {
        val trash = directory.resolveSibling(directory.name.removeSuffix(STAGE_SUFFIX) + TRASH_SUFFIX)
        check(directory.renameTo(trash)) { "Could not retire database import staging directory" }
        return trash
    }

    private fun removeImportStage(directory: File) {
        if (!directory.deleteRecursively() && directory.exists()) {
            Timber.tag(TAG).w("Could not remove abandoned database import staging directory")
        }
    }

    /** Installs staged files as one recoverable operation. A null source removes a SQLite sidecar. */
    fun install(
        filesDir: File,
        database: File,
        files: Map<File, File?>,
        beforeReplace: () -> Unit = {},
    ) {
        try {
            withLock(filesDir) {
                val transaction = Transaction(filesDir, checkNotNull(database.parentFile))
                transaction.recover()
                beforeReplace()
                transaction.install(database, files)
            }
        } finally {
            cleanRetiredFiles(filesDir)
        }
    }

    /** Uses the same rollback protocol with a separate profile-state journal and allowlist. */
    fun installProfileState(filesDir: File, files: Map<File, File?>) {
        try {
            withLock(filesDir) {
                val transaction = Transaction(filesDir, filesDir, profileState = true)
                transaction.recover()
                transaction.install(filesDir.resolve(PROFILE_NAMES_FILE_NAME), files)
            }
        } finally {
            cleanRetiredFiles(filesDir)
        }
    }

    /** Detaches a rescue backup while the restore lock is held. */
    fun retireRescueBackups(directory: File): File = retireForDeletion(directory, RETIRED_BACKUPS_PREFIX)

    private fun retireForDeletion(directory: File, prefix: String): File {
        val retired = directory.resolveSibling(prefix + UUID.randomUUID())
        check(directory.renameTo(retired)) { "Could not detach ${directory.name} for deletion" }
        return retired
    }

    /** Deletes detached files outside the lock that gates startup. Rescue deletes its own backups. */
    fun cleanRetiredFiles(filesDir: File, includeRescueBackups: Boolean = false) {
        runCatching {
            val root = filesDir.canonicalFile
            for (directory in root.listFiles().orEmpty()) {
                val retired = directory.name.startsWith(RETIRED_JOURNAL_PREFIX) ||
                    (includeRescueBackups && directory.name.startsWith(RETIRED_BACKUPS_PREFIX))
                if (retired && directory.isDirectory && directory.canonicalFile == directory &&
                    !directory.deleteRecursively() && directory.exists()
                ) {
                    Timber.tag(TAG).w("Could not remove retired database restore files")
                }
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not clean retired database restore files") }
    }

    @Synchronized
    private fun <T> withLock(filesDir: File, action: () -> T): T {
        val directory = filesDir.canonicalFile
        val heldDirectory = lockedDirectory
        if (heldDirectory != null) {
            // This monitor is reentrant. Nested import staging shares the outer rescue lock;
            // opening a second descriptor for the same inode could release that lock.
            check(heldDirectory == directory) { "Nested database restore lock for another directory" }
            return action()
        }
        return RandomAccessFile(directory.resolve("database-restore.lock"), "rw").use { file ->
            file.channel.lock().use {
                lockedDirectory = directory
                try {
                    action()
                } finally {
                    lockedDirectory = null
                }
            }
        }
    }

    internal class Transaction(filesDir: File, databaseDir: File, private val profileState: Boolean = false) {
        private val imageRoot = filesDir.canonicalFile
        private val databaseRoot = databaseDir.canonicalFile
        private val journal = imageRoot.resolve(if (profileState) "profile-state-restore-journal" else "database-restore-journal")
        private val pending = journal.resolve("pending")

        private data class Entry(val target: File, val existed: Boolean)

        fun hasPendingRestore(): Boolean = pending.isFile

        fun recover() {
            if (!journal.exists()) return
            if (pending.isFile) {
                val entries = DataInputStream(pending.inputStream().buffered()).use { input ->
                    check(input.readInt() == VERSION) { "Unsupported database restore journal" }
                    val count = input.readInt()
                    check(count in 1..MAX_FILES) { "Invalid database restore journal size" }
                    List(count) {
                        val isDatabase = input.readBoolean()
                        val target = (if (isDatabase) databaseRoot else imageRoot).resolve(input.readUTF())
                        checkTarget(target)
                        Entry(target, input.readBoolean())
                    }
                }
                for ((index, entry) in entries.withIndex().reversed()) {
                    val previous = previous(index)
                    when {
                        previous.isFile -> replace(previous, entry.target)
                        !entry.existed && entry.target.exists() -> check(entry.target.delete())
                        entry.existed -> check(entry.target.isFile) { "Original restore target is missing" }
                    }
                }
                // Backups become disposable only after all live targets are restored.
                check(pending.delete()) { "Could not finish database restore recovery" }
            }
            retire()
        }

        fun install(database: File, files: Map<File, File?>) {
            check(files.size in 1..MAX_FILES)
            val sources = files.entries.map { (target, source) ->
                if (profileState) checkTarget(target)
                target.canonicalFile to source
            }
            val main = database.canonicalFile
            val entries = sources.map { (target, source) ->
                checkTarget(target)
                check(!target.exists() || target.isFile) { "Invalid restore destination" }
                if (source != null) {
                    check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
                        "Could not create restore destination"
                    }
                }
                Entry(target, target.exists())
            }
            check(journal.mkdir()) { "Could not create database restore journal" }
            try {
                writePending(entries)
                for ((index, entry) in entries.withIndex()) {
                    if (!entry.existed) continue
                    if (entry.target == main) preserveMain(entry.target, previous(index))
                    else replace(entry.target, previous(index))
                }
                for ((target, source) in sources) {
                    if (source != null) replace(source, target)
                }
                // Atomic publication of the commit marker distinguishes success from rollback.
                replace(pending, journal.resolve("committed"))
            } catch (error: Throwable) {
                runCatching { recover() }.onFailure(error::addSuppressed)
                throw error
            }
            retire()
        }

        private fun writePending(entries: List<Entry>) {
            val draft = journal.resolve("manifest.tmp")
            FileOutputStream(draft).use { file ->
                val output = DataOutputStream(file)
                output.writeInt(VERSION)
                output.writeInt(entries.size)
                for (entry in entries) {
                    val isDatabase = entry.target.parentFile == databaseRoot
                    val root = if (isDatabase) databaseRoot else imageRoot
                    output.writeBoolean(isDatabase)
                    output.writeUTF(entry.target.relativeTo(root).invariantSeparatorsPath)
                    output.writeBoolean(entry.existed)
                }
                output.flush()
                file.fd.sync()
            }
            replace(draft, pending)
        }

        private fun preserveMain(source: File, previous: File) {
            // A hard link keeps the original inode without copying a potentially large database.
            val linked = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Files.createLink(previous.toPath(), source.toPath())
                else Os.link(source.path, previous.path)
            }.isSuccess
            if (!linked) {
                val copy = journal.resolve("main.tmp")
                FileOutputStream(copy).use { output ->
                    source.inputStream().use { it.copyTo(output) }
                    output.fd.sync()
                }
                replace(copy, previous)
            }
        }

        private fun checkTarget(target: File) {
            val path = target.canonicalFile
            val parent = path.parentFile
            if (profileState) {
                val isRegistry = parent == imageRoot &&
                    target.name in setOf(PROFILE_NAMES_FILE_NAME, ProfileStateArchive.PENDING_NAMES_FILE)
                val isAvatar = target.name == "avatar.jpg" && parent != null && parent.parentFile == imageRoot.resolve("profiles") &&
                    parent.name.isNotBlank() && isProfileIdSafe(parent.name)
                val isGeneration = parent == imageRoot.resolve("profile-storage-generations") &&
                    target.name.isNotBlank() && isProfileIdSafe(target.name)
                check(target == path && (isRegistry || isAvatar || isGeneration)) { "Invalid profile state restore path" }
                return
            }
            val isImage = parent != null && parent.parentFile == imageRoot &&
                (parent.name == "thumbnail" || parent.name == "app_covers" || parent.name.startsWith("app_covers_"))
            check(target == path && (parent == databaseRoot || isImage)) { "Invalid database restore path" }
        }

        private fun previous(index: Int): File = journal.resolve("previous-$index")

        private fun retire() {
            // A retired journal has no pending work. Publish that by renaming it, so startup
            // does not have to wait for a recursive delete or discover half-deleted backups.
            runCatching { retireForDeletion(journal, RETIRED_JOURNAL_PREFIX) }
                .onFailure { Timber.tag(TAG).w(it, "Could not retire database restore journal") }
        }
    }

    private fun replace(source: File, target: File) {
        if (Build.VERSION.SDK_INT == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Os.rename(source.path, target.path)
        }
    }
}
