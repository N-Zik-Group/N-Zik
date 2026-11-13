package app.n_zik.android.core.profiles

import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.enums.ExoPlayerCacheLocation
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.exoPlayerCacheLocationKey
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.readProfileIds
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import java.io.File
import java.util.UUID
import timber.log.Timber

internal object ProfileCacheLocationMigration {
    private const val REQUEST_PREFIX = "profile_cache_location_request_"
    private const val RETIRE_PREFIX = "profile_cache_location_retire_"
    private const val COPY_PREFIX = "profile_cache_location_copy_"

    private val governed = setOf(ProfileDataItem.DOWNLOADS, ProfileDataItem.MEDIA_CACHE, ProfileDataItem.IMAGES)

    fun isRequestKey(key: String): Boolean =
        key.startsWith(REQUEST_PREFIX) || key.startsWith(RETIRE_PREFIX) || key.startsWith(COPY_PREFIX)

    fun stageRequest(
        preferences: SharedPreferences,
        editor: SharedPreferences.Editor,
        target: ProfileStorageIdentity,
        destination: ExoPlayerCacheLocation,
    ) {
        val current = preferences.getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)
        val key = REQUEST_PREFIX + target.generation
        if (current == destination) editor.remove(key) else editor.putString(key, destination.name)
    }

    fun request(context: Context, target: ProfileStorageIdentity, destination: ExoPlayerCacheLocation): Boolean =
        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            if (target != profileStorageIdentity(context, target.id)) return@withProfileStorageMutation false
            val preferences = profileDataPrefs(context, target.id)
            val editor = preferences.edit()
            stageRequest(preferences, editor, target, destination)
            editor.commit()
        }

    fun requested(context: Context, target: ProfileStorageIdentity): ExoPlayerCacheLocation? {
        val raw = profileDataPrefs(context, target.id).getString(REQUEST_PREFIX + target.generation, null)
        return ExoPlayerCacheLocation.entries.firstOrNull { it.name == raw }
    }

    /** Runs while no cache has opened and the startup process holds the storage lock. */
    fun resume(context: Context, profileId: String, onPublished: () -> Unit = {}) {
        runCatching {
            val target = profileStorageIdentity(context, profileId) ?: return
            val preferences = profileDataPrefs(context, profileId)
            val retireKey = RETIRE_PREFIX + target.generation
            val names = resolveProfileDataDirNames(profileId, emptySet())
            val cacheNames = listOf(names.downloadsDir, names.mediaCacheDir, names.imageCacheDir)
            retireInactiveRoot(context, profileId, cacheNames, retireKey)
            val destination = requested(context, target) ?: return
            check(!isShared(context, profileId)) { "The legacy shared storage has not been separated" }
            val currentRoot = profileCacheBaseDir(context, profileId).canonicalFile
            val destinationRoot = when (destination) {
                ExoPlayerCacheLocation.System -> context.cacheDir
                ExoPlayerCacheLocation.Private -> context.filesDir
            }.canonicalFile
            val index = context.getDatabasePath(names.downloadsIndexDb)
            val databaseRoot = checkNotNull(index.parentFile).canonicalFile
            val indexFiles = listOf("", "-journal", "-wal", "-shm").map { databaseRoot.resolve(index.name + it) }
            val before = indexFiles.map(ProfileResetSnapshot::fingerprint)
            val retirement = if (currentRoot != destinationRoot) {
                val expected = cacheNames.map { name -> ProfileResetSnapshot.fingerprint(currentRoot.resolve(name)) }
                cacheNames.forEach { name ->
                    val source = currentRoot.resolve(name)
                    val copy = destinationRoot.resolve(name)
                    val receipt = copyReceipt(target, destination, name)
                    prepareDestination(preferences, receipt, source, copy)
                }
                cacheNames.forEachIndexed { position, name ->
                    check(ProfileResetSnapshot.fingerprint(currentRoot.resolve(name)) == expected[position] &&
                        ProfileResetSnapshot.fingerprint(destinationRoot.resolve(name)) == expected[position]) {
                        "Cache changed during storage transfer"
                    }
                }
                val origin = preferences.getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)
                (listOf(origin.name, UUID.randomUUID().toString()) + expected).joinToString(",")
            } else null
            check(before == indexFiles.map(ProfileResetSnapshot::fingerprint)) { "Media3 index changed during storage transfer" }
            check(target == profileStorageIdentity(context, profileId)) { "Profile storage identity changed" }
            val edit = preferences.edit()
                .putString(exoPlayerCacheLocationKey, destination.name)
                .remove(REQUEST_PREFIX + target.generation)
            cacheNames.forEach { edit.remove(copyReceipt(target, destination, it)) }
            if (retirement != null) edit.putString(retireKey, retirement)
            check(edit.commit()) { "Could not publish the cache location" }
            onPublished()
            retireInactiveRoot(context, profileId, cacheNames, retireKey)
        }.onFailure { error ->
            Timber.tag("ProfileCacheLocation").w(error, "Cache location change remains pending")
        }
    }

    private fun retireInactiveRoot(context: Context, profileId: String, cacheNames: List<String>, key: String) {
        val preferences = profileDataPrefs(context, profileId)
        val record = preferences.getString(key, null)?.split(',') ?: return
        check(record.size == cacheNames.size + 2)
        val origin = ExoPlayerCacheLocation.valueOf(record[0])
        val operation = UUID.fromString(record[1])
        check(origin != preferences.getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)) {
            "Cannot retire the active cache location"
        }
        val root = when (origin) {
            ExoPlayerCacheLocation.System -> context.cacheDir
            ExoPlayerCacheLocation.Private -> context.filesDir
        }.canonicalFile
        cacheNames.forEachIndexed { index, name ->
            val source = root.resolve(name)
            val retired = root.resolve(".nzik-retired-$operation-$index")
            val actual = ProfileResetSnapshot.fingerprint(source)
            if (actual != "-") {
                check(actual == record[index + 2] && !retired.exists()) { "The inactive cache changed after transfer" }
                check(source.renameTo(retired)) { "Could not retire the verified inactive cache" }
            }
            ProfileResetSnapshot.deleteVerifiedTree(retired)
        }
        check(preferences.edit().remove(key).commit()) { "Could not complete cache retirement" }
    }

    private fun isShared(context: Context, id: String): Boolean =
        if (id == DEFAULT_PROFILE_ID) {
            context.readProfileIds().any { profileId -> governed.any { context.profileShares(it, profileId) } }
        } else {
            governed.any { context.profileShares(it, id) }
        }

    private fun copyReceipt(target: ProfileStorageIdentity, location: ExoPlayerCacheLocation, name: String): String =
        "$COPY_PREFIX${target.generation}_${location.name}_$name"

    private fun prepareDestination(
        preferences: SharedPreferences,
        receipt: String,
        source: File,
        destination: File,
    ) {
        val expected = ProfileResetSnapshot.fingerprint(source)
        val published = ProfileResetSnapshot.fingerprint(destination)
        val obsolete = destination.resolveSibling(".nzik-location-obsolete-${destination.name}")
        ProfileResetSnapshot.deleteVerifiedTree(obsolete)
        if (expected == published) return
        if (published != "-") {
            check(published == preferences.getString(receipt, null)) {
                "The other cache location contains different data"
            }
            check(destination.renameTo(obsolete)) { "Could not retire the superseded transfer copy" }
            ProfileResetSnapshot.deleteVerifiedTree(obsolete)
        }
        if (expected == "-") return
        val stage = checkNotNull(destination.parentFile).resolve(".nzik-location-${destination.name}")
        if (ProfileResetSnapshot.fingerprint(stage) != expected) {
            ProfileResetSnapshot.deleteVerifiedTree(stage)
            ProfileResetSnapshot.copyTree(source, stage)
        }
        check(ProfileResetSnapshot.fingerprint(stage) == expected) { "Cache location copy verification failed" }
        check(preferences.edit().putString(receipt, expected).commit()) { "Could not journal the verified cache copy" }
        check(stage.renameTo(destination)) { "Could not publish the verified cache files" }
    }
}
