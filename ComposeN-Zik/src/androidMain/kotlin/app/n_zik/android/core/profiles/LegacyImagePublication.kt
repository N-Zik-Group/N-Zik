package app.n_zik.android.core.profiles

import android.content.Context
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.readProfileIds
import app.n_zik.android.core.backup.DatabaseBackup
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.coil.ReadOnlyCoilCache
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import timber.log.Timber

/** Publishes private image caches and verified cover references before clearing legacy sharing. */
internal object LegacyImagePublication {
    fun resume(context: Context) {
        val consumers = context.readProfileIds().filter { context.profileShares(ProfileDataItem.IMAGES, it) }
        if (consumers.isEmpty()) return
        runCatching {
            val files = context.filesDir.canonicalFile
            val cache = context.cacheDir.canonicalFile
            val oldCovers = files.resolve(ProfileDataDirNames.COVERS_DIR)
            val sources = mapOf(
                "covers" to oldCovers,
                "coil-system" to cache.resolve(ProfileDataDirNames.IMAGE_CACHE_DIR),
                "coil-private" to files.resolve(ProfileDataDirNames.IMAGE_CACHE_DIR),
            )
            val checksums = sources.mapValues { ProfileResetSnapshot.fingerprint(it.value) }
            val inventory = checksums.entries.joinToString("\n") { (name, hash) -> "$name $hash" }
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(inventory.encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
            val history = files.resolve("protected-profile-history/images")
            val archive = history.resolve(fingerprint)
            if (!archive.exists()) {
                val temporary = history.resolve(".prepare-$fingerprint")
                ProfileResetSnapshot.deleteVerifiedTree(temporary)
                check(temporary.mkdirs())
                sources.forEach { (name, source) ->
                    val destination = temporary.resolve(name)
                    ProfileResetSnapshot.copyTree(source, destination)
                    check(ProfileResetSnapshot.fingerprint(destination) == checksums.getValue(name))
                    check(ProfileResetSnapshot.fingerprint(source) == checksums.getValue(name))
                }
                writeSynced(temporary.resolve("inventory.txt"), inventory)
                check(temporary.renameTo(archive))
            }
            check(archive.resolve("inventory.txt").readText() == inventory)
            checksums.forEach { (name, hash) -> check(ProfileResetSnapshot.fingerprint(archive.resolve(name)) == hash) }
            val system = profileCacheBaseDir(context, DEFAULT_PROFILE_ID).canonicalFile == cache
            val originalCache = archive.resolve(if (system) "coil-system" else "coil-private")
            consumers.forEach { id ->
                runCatching { publish(context, id, oldCovers, originalCache, archive) }
                    .onFailure {
                        LegacySeparationStatus.record(context, id, ProfileDataItem.IMAGES, it)
                        Timber.tag("LegacyImages").w(it, "Legacy image separation remains pending for %s", id)
                    }
            }
        }.onFailure { error ->
            consumers.forEach { id -> LegacySeparationStatus.record(context, id, ProfileDataItem.IMAGES, error) }
            Timber.tag("LegacyImages").w(error, "Could not preserve the historical image sources")
        }
    }

    private fun publish(context: Context, id: String, oldCovers: File, oldCache: File, archive: File) {
        val identity = checkNotNull(profileStorageIdentity(context, id))
        val names = resolveProfileDataDirNames(id, emptySet())
        val files = context.filesDir.canonicalFile
        val targetCache = profileCacheBaseDir(context, id).canonicalFile.resolve(names.imageCacheDir)
        if (oldCache.isDirectory) {
            // Reject corrupt journals that would be erased by Coil on opening the private destination.
            ReadOnlyCoilCache(oldCache, Long.MAX_VALUE).verifyReadableJournal()
            publishCopy(oldCache, targetCache)
            ReadOnlyCoilCache(targetCache, Long.MAX_VALUE).verifyReadableJournal()
        }

        val privateCovers = files.resolve(names.coversDir)
        val database = context.getDatabasePath("data_$id.db").let { path ->
            checkNotNull(path.parentFile).canonicalFile.resolve(path.name)
        }
        val work = files.resolve(".legacy-image-transfer")
        check(work.isDirectory || work.mkdirs())
        val stage = work.resolve(database.name)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            ProfileResetSnapshot.deleteVerifiedTree(File(stage.path + suffix))
        }
        try {
            val changed = DatabaseBackup.relocateSharedCovers(database, stage, oldCovers, privateCovers) { name ->
                val source = archive.resolve("covers/$name")
                check(source.isFile) { "A referenced shared cover is missing" }
                publishCopy(source, privateCovers.resolve(name))
            }
            check(identity == profileStorageIdentity(context, id))
            if (changed) {
                val replacements = listOf("", "-wal", "-shm", "-journal").associate { suffix ->
                    File(database.path + suffix) to stage.takeIf { suffix.isEmpty() }
                }
                DatabaseRestoreJournal.install(context.filesDir, database, replacements)
            }
            writeSynced(archive.resolve("consumer-${database.name}-${identity.generation}.txt"), "${identity.id}\n${identity.generation}")
            check(context.profilePreferences.edit().remove(profileShareKey(ProfileDataItem.IMAGES, id)).commit())
        } finally {
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                ProfileResetSnapshot.deleteVerifiedTree(File(stage.path + suffix))
            }
        }
    }

    private fun publishCopy(source: File, destination: File) {
        val expected = ProfileResetSnapshot.fingerprint(source)
        if (expected == ProfileResetSnapshot.fingerprint(destination)) return
        if (destination.exists()) throw LegacySeparationStatus.Conflict("An independent image differs from the historical copy")
        val temporary = checkNotNull(destination.parentFile).resolve(".nzik-separation-${destination.name}")
        ProfileResetSnapshot.deleteVerifiedTree(temporary)
        ProfileResetSnapshot.copyTree(source, temporary)
        check(ProfileResetSnapshot.fingerprint(temporary) == expected)
        check(temporary.renameTo(destination)) { "Could not publish the verified image copy" }
    }

    private fun writeSynced(file: File, content: String) {
        FileOutputStream(file).use { stream ->
            stream.write(content.encodeToByteArray())
            stream.fd.sync()
        }
    }
}
