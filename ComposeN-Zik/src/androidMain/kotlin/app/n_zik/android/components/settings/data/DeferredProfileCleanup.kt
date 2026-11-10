package app.n_zik.android.components.settings.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.download.utils.InactiveDownloadCleanup
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.core.maintenance.MaintenanceStorageCleanupOutcome
import app.n_zik.android.core.maintenance.MaintenanceStorageCleanupRecord
import app.n_zik.android.core.maintenance.MaintenanceStorageCleanupState
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.isLegacyStorageShared
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import app.n_zik.android.utils.debug.debugLogFiles
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

/** Retires confirmed regenerable caches before their writers open, under the library startup lock. */
@UnstableApi
internal object DeferredProfileCleanup {
    private const val ABSENT = "-"
    private const val PENDING = "pending"
    private const val APPLIED = "applied"
    private const val DIRECTORY = ".profile-cache-cleanup"
    private const val MAX_MOVES = 8

    private data class Move(val source: File, val hash: String)
    private data class Operation(
        val owner: ProfileStorageIdentity,
        val action: CleanupAction,
        val moves: List<Move>,
        val mediaFingerprint: String? = null,
    )

    val lastRun = MutableStateFlow<List<CleanupResult>>(emptyList())

    fun isSupported(action: CleanupAction): Boolean =
        action == CleanupAction.IMAGES || action == CleanupAction.NETWORK || action == CleanupAction.DIAGNOSTIC_LOGS

    private fun retiresWholeDirectory(target: WaveformCleanupTarget): Boolean =
        target.action != CleanupAction.DIAGNOSTIC_LOGS && target.directory.name != "shared_prefs"

    fun canSchedule(confirmed: List<WaveformCleanupTarget>): Boolean =
        confirmed.isNotEmpty() && isSupported(confirmed.first().action) &&
            confirmed.any { it.files.isNotEmpty() } &&
            confirmed.filter { it.files.isNotEmpty() && retiresWholeDirectory(it) }.all { target ->
                target.directory.list().orEmpty().toSet() == target.files.map { it.name }.toSet()
            }

    /** Existing confirmations are offered for restart again instead of being captured twice. */
    fun pendingActions(context: Context): Set<Pair<ProfileStorageIdentity, CleanupAction>> {
        val root = root(context)
        if (!root.isDirectory || root.canonicalFile != root) return emptySet()
        return operationDirs(root).mapNotNull { directory ->
            val operation = runCatching { readOperation(directory) }.getOrNull() ?: return@mapNotNull null
            if (profileStorageIdentity(context, operation.owner.id) != operation.owner) return@mapNotNull null
            if (runCatching { containsOnlyBookkeeping(context, directory, operation) }.getOrDefault(false)) {
                return@mapNotNull null
            }
            operation.owner to operation.action
        }.toSet()
    }

    fun schedule(context: Context, confirmed: List<WaveformCleanupTarget>): Boolean =
        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            val first = confirmed.first()
            val owner = checkNotNull(first.identity)
            if (captureFiles(context, owner, first.action, allowActive = true) != confirmed || !canSchedule(confirmed)) {
                return@withProfileStorageMutation false
            }
            val moves = confirmed.filter { it.files.isNotEmpty() }.flatMap { target ->
                if (!retiresWholeDirectory(target)) {
                    target.files.map { Move(target.directory.resolve(it.name), it.hash) }
                } else {
                    val hash = ProfileResetSnapshot.fingerprintDirectoryEntries(target.files.associate { it.name to it.hash })
                    listOf(Move(target.directory, hash))
                }
            }
            check(moves.size in 1..MAX_MOVES)
            enqueue(context, Operation(owner, first.action, moves))
        }

    fun scheduleMedia(context: Context, confirmed: InactiveDownloadCleanup.Target): Boolean =
        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            val current = InactiveDownloadCleanup.capture(context, confirmed.identity, confirmed.kind, allowSelected = true)
            if (current != confirmed) return@withProfileStorageMutation false
            val action = if (confirmed.kind == InactiveDownloadCleanup.Kind.DOWNLOADS) CleanupAction.DOWNLOADS
                else CleanupAction.STREAMING
            enqueue(context, Operation(
                confirmed.identity, action, emptyList(), InactiveDownloadCleanup.confirmationFingerprint(confirmed),
            ))
        }

    private fun enqueue(context: Context, operation: Operation): Boolean {
        val root = root(context)
        check(root.canonicalFile == root && (root.isDirectory || root.mkdir()))
        if ((operation.owner to operation.action) in pendingActions(context)) return true
        val directory = root.resolve(UUID.randomUUID().toString())
        check(directory.mkdir())
        write(directory, operation)
        return true
    }

    private fun mediaKind(action: CleanupAction): InactiveDownloadCleanup.Kind? = when (action) {
        CleanupAction.DOWNLOADS -> InactiveDownloadCleanup.Kind.DOWNLOADS
        CleanupAction.STREAMING -> InactiveDownloadCleanup.Kind.STREAMING
        else -> null
    }

    /** Runs before Coil or OkHttp is created. A changed snapshot stays in place and is reported as skipped. */
    fun resume(context: Context, discardUnstarted: Boolean = false) {
        val root = root(context)
        if (!root.exists()) return
        if (root.canonicalFile != root || !root.isDirectory) {
            lastRun.value = listOf(CleanupResult.FAILED)
            return
        }
        val records = mutableMapOf<ProfileStorageIdentity, MutableList<MaintenanceStorageCleanupRecord>>()
        lastRun.value = operationDirs(root).mapNotNull { directory ->
            val operation = runCatching { readOperation(directory) }.getOrNull()
            if (discardUnstarted && operation != null && !directory.resolve(APPLIED).exists() &&
                operation.moves.indices.none { retired(directory, operation.moves[it], it).exists() }
            ) {
                runCatching { ProfileResetSnapshot.deleteVerifiedTree(directory) }
                    .onFailure { Timber.tag("DeferredProfileCleanup").w(it, "Could not retire an obsolete cleanup plan") }
                return@mapNotNull null
            }
            val outcome = runCatching { replay(context, directory) }
                .onFailure { Timber.tag("DeferredProfileCleanup").w(it, "Could not finish a confirmed cleanup") }
                .getOrElse { error ->
                    if (InactiveDownloadCleanup.hasPendingMutation(context)) throw error
                    CleanupResult.FAILED
                }
            if (operation != null && outcome != null && outcome != CleanupResult.SCHEDULED) {
                val record = MaintenanceStorageCleanupRecord(operation.action.name, MaintenanceStorageCleanupOutcome.valueOf(outcome.name))
                records.getOrPut(operation.owner) { mutableListOf() }.add(record)
            }
            outcome
        }
        val timestamp = System.currentTimeMillis()
        records.forEach { (owner, results) ->
            runCatching { MaintenanceStateStore.saveStorageCleanup(context, owner, MaintenanceStorageCleanupState(timestamp, results)) }
                .onFailure { Timber.tag("DeferredProfileCleanup").w(it, "Could not save the cleanup run to Maintenance") }
        }
    }

    private fun readOperation(directory: File): Operation? =
        read(directory.resolve(APPLIED).takeIf(File::exists) ?: directory.resolve(PENDING))

    private fun containsOnlyBookkeeping(context: Context, directory: File, operation: Operation): Boolean {
        if (operation.mediaFingerprint != null || directory.resolve(APPLIED).exists() ||
            operation.moves.indices.any { retired(directory, operation.moves[it], it).exists() }
        ) return false
        val current = captureFiles(context, operation.owner, operation.action, presenceOnly = true, allowActive = true)
        return current != null && current.all { it.files.isEmpty() }
    }

    private fun replay(context: Context, directory: File): CleanupResult? {
        val pending = directory.resolve(PENDING)
        val applied = directory.resolve(APPLIED)
        val completed = applied.exists()
        val operation = readOperation(directory)
        if (operation == null) {
            ProfileResetSnapshot.deleteVerifiedTree(directory)
            return null
        }
        val owner = operation.owner
        if (profileStorageIdentity(context, owner.id) != owner ||
            (operation.action == CleanupAction.IMAGES && isLegacyStorageShared(context, owner.id, ProfileDataItem.IMAGES))
        ) {
            // A retired cache from an interrupted operation belongs to the old generation.
            // Keep it outside the live store if we can no longer verify the owner.
            if (operation.moves.indices.none { retired(directory, operation.moves[it], it).exists() }) {
                ProfileResetSnapshot.deleteVerifiedTree(directory)
            }
            return CleanupResult.SKIPPED
        }
        if (operation.mediaFingerprint != null) {
            val kind = checkNotNull(mediaKind(operation.action))
            val current = InactiveDownloadCleanup.capture(context, owner, kind, allowSelected = true)
            val cleaned = current != null &&
                InactiveDownloadCleanup.confirmationFingerprint(current) == operation.mediaFingerprint &&
                InactiveDownloadCleanup.execute(context, current, allowSelected = true)
            ProfileResetSnapshot.deleteVerifiedTree(directory)
            return if (cleaned) CleanupResult.COMPLETED else CleanupResult.SKIPPED
        }
        if (containsOnlyBookkeeping(context, directory, operation)) {
            ProfileResetSnapshot.deleteVerifiedTree(directory)
            return CleanupResult.SKIPPED
        }
        val roots = checkNotNull(captureFiles(context, owner, operation.action, presenceOnly = true, allowActive = true))
        val permitted = roots.flatMap { target ->
            when {
                target.action == CleanupAction.DIAGNOSTIC_LOGS -> debugLogFiles(target.directory)
                target.directory.name == "shared_prefs" ->
                    ImageCacheFactory.profileMetadataFiles(owner).map(target.directory::resolve)
                else -> listOf(target.directory)
            }
        }.toSet()
        check(operation.moves.all { it.source in permitted }) { "The confirmed cache location changed" }
        val moves = operation.moves.mapIndexed { index, move -> move to retired(directory, move, index) }
        if (!completed) {
            val unchanged = moves.all { (move, retired) ->
                val live = ProfileResetSnapshot.fingerprint(move.source)
                val detached = ProfileResetSnapshot.fingerprint(retired)
                (live == move.hash && detached == ABSENT) || (live == ABSENT && detached == move.hash)
            }
            if (!unchanged) {
                // If this process interrupted retirement, return verified entries to their original empty names.
                moves.asReversed().forEach { (move, retired) ->
                    if (!retired.exists()) return@forEach
                    check(ProfileResetSnapshot.fingerprint(retired) == move.hash && !move.source.exists())
                    check(retired.renameTo(move.source))
                }
                ProfileResetSnapshot.deleteVerifiedTree(directory)
                return CleanupResult.SKIPPED
            }
            moves.forEach { (move, retired) ->
                if (!retired.exists()) check(move.source.renameTo(retired)) { "Could not retire the confirmed cache" }
            }
            check(pending.renameTo(applied)) { "Could not record completed retirement" }
        }
        moves.forEach { (_, retired) -> ProfileResetSnapshot.deleteVerifiedTree(retired) }
        ProfileResetSnapshot.deleteVerifiedTree(directory)
        return CleanupResult.COMPLETED
    }

    private fun root(context: Context): File = context.filesDir.canonicalFile.resolve(DIRECTORY)

    private fun operationDirs(root: File): List<File> = root.listFiles().orEmpty().filter {
        it.isDirectory && it.canonicalFile == it &&
            runCatching { UUID.fromString(it.name).toString() == it.name }.getOrDefault(false)
    }.sortedBy { it.name }

    private fun retired(directory: File, move: Move, index: Int): File =
        checkNotNull(move.source.parentFile).resolve(".nzik-deferred-${directory.name}-$index")

    private fun write(directory: File, operation: Operation) {
        val staging = directory.resolve("pending.stage")
        FileOutputStream(staging).use { stream ->
            val output = DataOutputStream(stream.buffered())
            output.writeUTF(operation.owner.id)
            output.writeUTF(operation.owner.generation)
            output.writeUTF(operation.action.name)
            output.writeInt(operation.moves.size)
            operation.moves.forEach { move ->
                output.writeUTF(move.source.path)
                output.writeUTF(move.hash)
            }
            operation.mediaFingerprint?.let(output::writeUTF)
            output.flush()
            stream.fd.sync()
        }
        check(staging.renameTo(directory.resolve(PENDING)))
    }

    private fun read(file: File): Operation? {
        if (ProfileResetSnapshot.fingerprint(file) == ABSENT) return null
        check(file.isFile && file.length() <= 64 * 1024)
        return DataInputStream(file.inputStream().buffered()).use { input ->
            val owner = ProfileStorageIdentity(input.readUTF(), input.readUTF())
            val action = CleanupAction.valueOf(input.readUTF())
            val media = mediaKind(action) != null
            check(media || isSupported(action))
            val count = input.readInt()
            check(if (media) count == 0 else count in 1..MAX_MOVES)
            val moves = List(count) {
                Move(File(input.readUTF()), input.readUTF().also { hash ->
                    check(hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' })
                })
            }
            val fingerprint = if (media) input.readUTF().also {
                check(it.length == 64 && it.all { char -> char in '0'..'9' || char in 'a'..'f' })
            } else null
            check(input.read() == -1 && moves.map { it.source }.distinct().size == moves.size)
            Operation(owner, action, moves, fingerprint)
        }
    }
}
