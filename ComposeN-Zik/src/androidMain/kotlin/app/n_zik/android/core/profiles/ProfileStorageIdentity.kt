package app.n_zik.android.core.profiles

import android.content.Context
import android.os.Build
import android.system.Os
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.isProfileIdSafe
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.utils.setActiveProfile
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal data class ProfileStorageIdentity(val id: String, val generation: String)

@Volatile
private var processStorageProfile: ProfileStorageIdentity? = null
private val profileSelectionLock = Any()

/** Freezes the selected owner before startup opens or prepares any of its stores. */
internal fun bindSelectedProcessStorageProfile(context: Context): String =
    synchronized(profileSelectionLock) {
        processStorageProfile?.let { return@synchronized it.id }
        val selected = getActiveProfile(context)
        processStorageProfile = checkNotNull(profileStorageIdentity(context, selected)) {
            "The selected storage profile is unavailable"
        }
        selected
    }

/** A launcher shortcut can select another profile in-place only before startup claims one. */
internal fun selectColdStorageProfile(context: Context, id: String): Boolean =
    synchronized(profileSelectionLock) {
        if (processStorageProfile != null) return@synchronized false
        setActiveProfile(id, context)
        true
    }

internal fun isProcessStorageProfile(context: Context, id: String): Boolean =
    processStorageProfile?.id == id || getActiveProfile(context) == id

/** A worker may use the live library only for the process's bound, still registered owner. */
internal fun isCurrentProcessStorageProfile(context: Context, owner: ProfileStorageIdentity): Boolean =
    getActiveProfile(context) == owner.id && profileStorageIdentity(context, owner.id) == owner &&
        (processStorageProfile?.let { it == owner } ?: true)

internal fun profileStorageIdentity(context: Context, id: String): ProfileStorageIdentity? {
    if (id != DEFAULT_PROFILE_ID && id !in context.readProfileIds()) return null
    return ProfileStorageIdentity(id, profileStorageGeneration(context, id))
}

internal fun profileStorageGeneration(context: Context, id: String): String {
    val marker = storageGenerationFile(context, id)
    if (!marker.exists()) return "legacy"
    return marker.readText().also { UUID.fromString(it) }
}

/** Called under the storage mutation lock before removing a registered ID. */
internal fun invalidateProfileStorageIdentity(context: Context, id: String) {
    val marker = storageGenerationFile(context, id)
    val directory = checkNotNull(marker.parentFile)
    check(directory.isDirectory || directory.mkdirs()) { "Could not open profile storage registry" }
    val temporary = File.createTempFile(".generation-", ".tmp", directory)
    try {
        FileOutputStream(temporary).use { output ->
            output.write(UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (Build.VERSION.SDK_INT == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.move(temporary.toPath(), marker.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Os.rename(temporary.path, marker.path)
        }
    } finally {
        temporary.delete()
    }
}

private fun storageGenerationFile(context: Context, id: String): File {
    require(id.isNotBlank() && isProfileIdSafe(id))
    return context.filesDir.resolve("profile-storage-generations").resolve(id)
}
