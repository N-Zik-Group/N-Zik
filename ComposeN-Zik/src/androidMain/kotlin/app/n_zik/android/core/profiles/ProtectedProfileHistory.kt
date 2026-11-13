package app.n_zik.android.core.profiles

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.coil.ReadOnlyCoilCache
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Explicit recovery from preserved sources. A conflicting destination always keeps its own files. */
@UnstableApi
internal object ProtectedProfileHistory {
    enum class Kind(val directory: String, val item: ProfileDataItem?) {
        MEDIA("media3", null), IMAGES("images", ProfileDataItem.IMAGES), WAVEFORMS("waveforms", ProfileDataItem.WAVEFORMS),
    }

    data class Archive(val id: String, val kind: Kind, val media: LegacyMediaPublication.Archive? = null)

    private data class Copy(val source: File, val destination: File, val hash: String)
    private val archiveId = Regex("[0-9a-f]{64}")

    fun archives(context: Context): List<Archive> =
        LegacyMediaPublication.archives(context).map { Archive(it.id, Kind.MEDIA, it) } +
            listOf(Kind.IMAGES, Kind.WAVEFORMS).flatMap { kind ->
                directory(context, kind).listFiles().orEmpty().filter {
                    archiveId.matches(it.name) && it.isDirectory && it.canonicalFile == it
                }.sortedByDescending(File::lastModified).map { Archive(it.name, kind) }
            }

    fun hasPending(context: Context): Boolean = pending(context).exists() || LegacyMediaPublication.hasPendingRescueRecovery(context)

    fun recover(context: Context, archive: Archive, target: ProfileStorageIdentity) {
        if (archive.kind == Kind.MEDIA) {
            check(!pending(context).exists()) { "Another historical recovery is pending" }
            LegacyMediaPublication.recover(context, checkNotNull(archive.media), target)
            return
        }
        check(!LegacyMediaPublication.hasPendingRescueRecovery(context)) { "Another historical recovery is pending" }
        val item = checkNotNull(archive.kind.item)
        check(target == profileStorageIdentity(context, target.id) && !isLegacyStorageShared(context, target.id, item)) {
            "The destination profile or its private storage changed"
        }
        val copies = verifiedCopies(context, archive, target)
        copies.forEach { copy ->
            val actual = ProfileResetSnapshot.fingerprint(copy.destination)
            check(actual == "-" || actual == copy.hash) { "The selected profile already contains different files" }
        }
        val pending = pending(context)
        val value = listOf(archive.kind.name, archive.id, target.id, target.generation).joinToString("\n")
        ProfileResetSnapshot.fingerprint(pending)
        check(!pending.exists() || pending.readText() == value) { "Another historical recovery is pending" }
        if (!pending.exists()) {
            val staged = pending.resolveSibling(pending.name + ".stage")
            ProfileResetSnapshot.deleteVerifiedTree(staged)
            FileOutputStream(staged).use { output ->
                output.write(value.encodeToByteArray())
                output.fd.sync()
            }
            check(staged.renameTo(pending))
        }
        for ((source, destination, hash) in copies) {
            if (ProfileResetSnapshot.fingerprint(destination) == hash) continue
            val stage = destination.resolveSibling(".nzik-separation-${destination.name}")
            ProfileResetSnapshot.deleteVerifiedTree(stage)
            ProfileResetSnapshot.copyTree(source, stage)
            check(ProfileResetSnapshot.fingerprint(stage) == hash && ProfileResetSnapshot.fingerprint(source) == hash)
            check(!destination.exists() && stage.renameTo(destination)) { "Could not publish the verified recovery copy" }
        }
        check(copies.all { ProfileResetSnapshot.fingerprint(it.destination) == it.hash })
        check(pending.delete())
    }

    fun resume(context: Context) {
        val pending = pending(context)
        if (!pending.exists()) return
        ProfileResetSnapshot.fingerprint(pending)
        check(pending.length() <= 4096)
        val data = pending.readLines()
        check(data.size == 4)
        recover(context, Archive(data[1], Kind.valueOf(data[0])), ProfileStorageIdentity(data[2], data[3]))
    }

    private fun verifiedCopies(context: Context, archive: Archive, target: ProfileStorageIdentity): List<Copy> {
        check(archiveId.matches(archive.id))
        val source = directory(context, archive.kind).resolve(archive.id)
        check(source.isDirectory && source.canonicalFile == source)
        val names = resolveProfileDataDirNames(target.id, emptySet())
        val files = context.filesDir.canonicalFile
        if (archive.kind == Kind.WAVEFORMS) {
            check(ProfileResetSnapshot.fingerprint(source) == archive.id) { "The historical waveform inventory changed" }
            return listOf(Copy(source, files.resolve(names.waveformsDir), archive.id))
        }

        val inventory = source.resolve("inventory.txt")
        ProfileResetSnapshot.fingerprint(inventory)
        check(inventory.isFile && inventory.length() <= 1024)
        val text = inventory.readText()
        val hash = MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
        check(hash == archive.id)
        val expected = text.lineSequence().associate { line -> line.substringBefore(' ') to line.substringAfter(' ') }
        val destinations = mapOf(
            "covers" to files.resolve(names.coversDir),
            "coil-system" to context.cacheDir.canonicalFile.resolve(names.imageCacheDir),
            "coil-private" to files.resolve(names.imageCacheDir),
        )
        check(expected.keys == destinations.keys)
        return destinations.mapNotNull { (name, targetPath) ->
            val from = source.resolve(name)
            val fingerprint = checkNotNull(expected[name])
            check(ProfileResetSnapshot.fingerprint(from) == fingerprint) { "The historical image inventory changed" }
            if (fingerprint == "-") return@mapNotNull null
            if (name != "covers") ReadOnlyCoilCache(from, Long.MAX_VALUE).verifyReadableJournal()
            Copy(from, targetPath, fingerprint)
        }.also { check(it.isNotEmpty()) { "The archive contains no recoverable files" } }
    }

    private fun directory(context: Context, kind: Kind): File =
        context.filesDir.canonicalFile.resolve("protected-profile-history/${kind.directory}")

    private fun pending(context: Context): File = context.filesDir.canonicalFile.resolve(".historical-recovery.pending")
}
