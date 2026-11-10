package app.n_zik.android.core.profiles

import android.content.Context
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.readProfileIds
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import java.io.File
import java.io.FileOutputStream
import timber.log.Timber

/** Separates regenerable shared waveforms without deleting or overwriting either source. */
internal object LegacyWaveformPublication {
    fun resume(context: Context) {
        val consumers = context.readProfileIds().filter { context.profileShares(ProfileDataItem.WAVEFORMS, it) }
        if (consumers.isEmpty()) return
        val root = context.filesDir.canonicalFile
        val source = root.resolve(ProfileDataDirNames.WAVEFORMS_DIR)
        runCatching {
            val hash = ProfileResetSnapshot.fingerprint(source)
            val archive = root.resolve("protected-profile-history/waveforms/$hash")
            publishCopy(source, archive, hash)
            consumers.forEach { id ->
                runCatching {
                    val identity = checkNotNull(profileStorageIdentity(context, id))
                    val name = resolveProfileDataDirNames(id, emptySet()).waveformsDir
                    publishCopy(archive, root.resolve(name), hash)
                    check(identity == profileStorageIdentity(context, id))
                    if (archive.isDirectory) {
                        val entry = archive.parentFile?.resolve("$hash-$name-${identity.generation}.txt")
                        FileOutputStream(checkNotNull(entry)).use { output ->
                            output.write("${identity.id}\n${identity.generation}".encodeToByteArray())
                            output.fd.sync()
                        }
                    }
                    check(context.profilePreferences.edit().remove(profileShareKey(ProfileDataItem.WAVEFORMS, id)).commit())
                }.onFailure { error ->
                    LegacySeparationStatus.record(context, id, ProfileDataItem.WAVEFORMS, error)
                    Timber.tag("LegacyWaveforms").w(error, "Waveform separation remains pending for %s", id)
                }
            }
        }.onFailure { error ->
            consumers.forEach { id -> LegacySeparationStatus.record(context, id, ProfileDataItem.WAVEFORMS, error) }
            Timber.tag("LegacyWaveforms").w(error, "Could not protect shared waveforms")
        }
    }

    private fun publishCopy(source: File, destination: File, expected: String) {
        val actual = ProfileResetSnapshot.fingerprint(destination)
        if (actual == expected) return
        // An empty shared store must not erase a former consumer's independent data.
        if (expected == "-" && destination.exists()) return
        if (destination.exists()) throw LegacySeparationStatus.Conflict("A private waveform already occupies this destination")
        val stage = checkNotNull(destination.parentFile).resolve(".nzik-separation-${destination.name}")
        ProfileResetSnapshot.deleteVerifiedTree(stage)
        ProfileResetSnapshot.copyTree(source, stage)
        check(ProfileResetSnapshot.fingerprint(stage) == expected &&
            ProfileResetSnapshot.fingerprint(source) == expected)
        check(stage.renameTo(destination)) { "Could not publish the verified waveform copy" }
    }
}
