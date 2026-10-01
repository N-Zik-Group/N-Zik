package app.n_zik.android.core.profiles

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.DefaultDatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.readProfileIds
import java.io.File

/**
 * The four shareable data items of a profile (spec-profile-data-separation, user decision
 * 2026-10-01): the Media3 downloads, the streaming media cache, the images (the custom
 * covers and the Coil image cache merged into one toggle) and the waveforms. Separate by
 * default, shared on opt-in (one flag per item, per profile — see [profileShares]).
 */
enum class ProfileDataItem(val shareToken: String) {
    DOWNLOADS("downloads"),
    MEDIA_CACHE("media_cache"),
    IMAGES("images"),
    WAVEFORMS("waveforms"),
    ;

    companion object {
        val ALL: List<ProfileDataItem> = values().toList()
    }
}

/**
 * The (pure) file names of one profile's data. The base profile ALWAYS keeps the
 * unsuffixed names; a user profile gets the suffixed `<item>_<id>` names for the items it
 * keeps separate and the unsuffixed (base) names for the items it shares.
 */
data class ProfileDataDirNames(
    val downloadsDir: String,
    val mediaCacheDir: String,
    val coversDir: String,
    val imageCacheDir: String,
    val waveformsDir: String,
    val downloadsIndexDb: String,
    val mediaCacheIndexDb: String,
) {
    companion object {
        const val DOWNLOADS_DIR = "exo_downloads"
        const val MEDIA_CACHE_DIR = "exoplayer"
        const val COVERS_DIR = "app_covers"
        const val IMAGE_CACHE_DIR = "coil"
        const val WAVEFORMS_DIR = "waveforms"

        /** The base name of the Media3 index database (no extension — the suffix goes BEFORE the `.db`). */
        const val INDEX_DB_BASE_NAME = "exoplayer_internal"

        /** The shared (base) Media3 index database — the downloads AND the media cache indexes live there today. */
        const val INDEX_DB_FILE = "$INDEX_DB_BASE_NAME.db"
    }
}

/**
 * Pure resolver (unit-tested without Android) of the file names of [profileId]'s data from
 * its sharing flags:
 * - the base profile (`default`) keeps EXACTLY the unsuffixed names, whatever the flags —
 *   bit-for-bit the current locations, no migration, no rename;
 * - a user profile keeps the unsuffixed (base) name for every item it SHARES and the
 *   suffixed `<item>_<id>` name for every item it keeps separate;
 * - the index rule is per cache instance: a shared item points at the base index
 *   `exoplayer_internal.db`, a separate item at the suffixed `exoplayer_internal_<id>.db` —
 *   a separate cache NEVER points at the base database (its entries would mingle with the
 *   base's); when both caches of a profile are separate, the single suffixed database
 *   carries both indexes (distinct tables, exactly like today's shared database).
 */
fun resolveProfileDataDirNames(profileId: String, shared: Set<ProfileDataItem>): ProfileDataDirNames {
    val isBase = profileId == DEFAULT_PROFILE_ID
    fun dirName(base: String, item: ProfileDataItem): String =
        if (isBase || item in shared) base else "${base}_$profileId"
    fun indexName(item: ProfileDataItem): String =
        if (isBase || item in shared) ProfileDataDirNames.INDEX_DB_FILE
        else "${ProfileDataDirNames.INDEX_DB_BASE_NAME}_$profileId.db"
    return ProfileDataDirNames(
        downloadsDir = dirName(ProfileDataDirNames.DOWNLOADS_DIR, ProfileDataItem.DOWNLOADS),
        mediaCacheDir = dirName(ProfileDataDirNames.MEDIA_CACHE_DIR, ProfileDataItem.MEDIA_CACHE),
        coversDir = dirName(ProfileDataDirNames.COVERS_DIR, ProfileDataItem.IMAGES),
        imageCacheDir = dirName(ProfileDataDirNames.IMAGE_CACHE_DIR, ProfileDataItem.IMAGES),
        waveformsDir = dirName(ProfileDataDirNames.WAVEFORMS_DIR, ProfileDataItem.WAVEFORMS),
        downloadsIndexDb = indexName(ProfileDataItem.DOWNLOADS),
        mediaCacheIndexDb = indexName(ProfileDataItem.MEDIA_CACHE),
    )
}

/**
 * The plain settings of [profileId] (`preferences` for the base, `preferences_<id>`
 * otherwise) — the same store the app itself uses (`Context.preferences` for the active
 * profile). Process-safe (plain SharedPreferences): usable at boot and in the `:rescue`
 * process, before or after a profile's settings are wiped.
 */
fun profileDataPrefs(context: Context, profileId: String): SharedPreferences =
    context.getSharedPreferences(
        "preferences${if (profileId == DEFAULT_PROFILE_ID) "" else "_$profileId"}",
        Context.MODE_PRIVATE,
    )

/**
 * The base directory of [profileId]'s cache-located data, from ITS location setting
 * (`System` → cacheDir, `Private` → filesDir) — the separated folders respect the settings
 * of the profile they belong to. Process-safe (plain SharedPreferences + file names only).
 */
fun profileCacheBaseDir(context: Context, profileId: String): File =
    when (profileDataPrefs(context, profileId).getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)) {
        ExoPlayerCacheLocation.System -> context.cacheDir
        ExoPlayerCacheLocation.Private -> context.filesDir
    }

/**
 * The settings profile that governs [item] for [profileId]: the BASE profile when the
 * item is SHARED (the data lives in the base's folder, so its size/Disabled check and
 * its location setting must be the base's — spec-profile-data-separation, PARTAGE_ON:
 * the base's shared folder + the base's index); the profile itself otherwise. A
 * profile whose own setting differs from the base's must never see its own (empty)
 * separate folder while writing into the base's index database.
 */
private fun settingsProfileFor(context: Context, profileId: String, item: ProfileDataItem): String =
    if (context.profileShares(item, profileId)) DEFAULT_PROFILE_ID else profileId

/**
 * The downloads directory of [profileId] (base name shared, `<name>_<id>` separate), null
 * when its download cache is Disabled — the cache then lives in a process-local temp dir
 * that deletes itself after close (the existing behavior, kept per profile). A shared item
 * is governed by the BASE profile's settings, a separate one by the profile's own
 * (see [settingsProfileFor]).
 */
fun downloadsDir(context: Context, profileId: String): File? {
    val settingsProfile = settingsProfileFor(context, profileId, ProfileDataItem.DOWNLOADS)
    val prefs = profileDataPrefs(context, settingsProfile)
    if (prefs.getEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`) == ExoPlayerDiskCacheMaxSize.Disabled) {
        return null
    }
    val names = resolveProfileDataDirNames(profileId, context.profileSharedItems(profileId))
    return profileCacheBaseDir(context, settingsProfile).resolve(names.downloadsDir)
}

/**
 * The media (streaming) cache directory of [profileId], null when Disabled (temp dir).
 * A shared item is governed by the BASE profile's settings, a separate one by the
 * profile's own (see [settingsProfileFor]).
 */
fun mediaCacheDir(context: Context, profileId: String): File? {
    val settingsProfile = settingsProfileFor(context, profileId, ProfileDataItem.MEDIA_CACHE)
    val prefs = profileDataPrefs(context, settingsProfile)
    if (prefs.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`) == ExoPlayerDiskCacheMaxSize.Disabled) {
        return null
    }
    val names = resolveProfileDataDirNames(profileId, context.profileSharedItems(profileId))
    return profileCacheBaseDir(context, settingsProfile).resolve(names.mediaCacheDir)
}

/**
 * The custom covers directory of [profileId] (`app_covers` / `app_covers_<id>`), ALWAYS
 * under filesDir (the same rule as today: the covers survive a cache clear, so the
 * location setting does not apply to them).
 */
fun coversDir(context: Context, profileId: String): File {
    val names = resolveProfileDataDirNames(profileId, context.profileSharedItems(profileId))
    return context.filesDir.resolve(names.coversDir)
}

/**
 * The Coil image-cache directory of [profileId] (`coil` / `coil_<id>` under its location
 * setting). A shared item is governed by the BASE profile's location setting, a separate
 * one by the profile's own (see [settingsProfileFor]).
 */
fun coilImageCacheDir(context: Context, profileId: String): File {
    val settingsProfile = settingsProfileFor(context, profileId, ProfileDataItem.IMAGES)
    val names = resolveProfileDataDirNames(profileId, context.profileSharedItems(profileId))
    return profileCacheBaseDir(context, settingsProfile).resolve(names.imageCacheDir)
}

/**
 * The custom covers folder NAME of the ACTIVE profile (`app_covers` for the base,
 * `app_covers_<id>` otherwise) — the single source the cover dialogs pass to their save
 * step, so the save side can never drift back to a literal dir name
 * (spec-profile-data-separation).
 */
fun activeCoversDirName(context: Context): String =
    coversDir(context, getActiveProfile(context)).name

/**
 * The waveforms directory of [profileId] (`waveforms` / `waveforms_<id>`), ALWAYS under
 * filesDir (the same rule as today: the waveforms survive a cache clear).
 */
fun waveformsDir(context: Context, profileId: String): File {
    val names = resolveProfileDataDirNames(profileId, context.profileSharedItems(profileId))
    return context.filesDir.resolve(names.waveformsDir)
}

/**
 * The suffixed index database of [profileId] (`databases/exoplayer_internal_<id>.db`) — the
 * single database that carries BOTH indexes of the profile when both caches are separate.
 * The base profile has no suffixed database: its index is [ProfileDataDirNames.INDEX_DB_FILE].
 */
fun profileIndexDbFile(context: Context, profileId: String): File =
    context.getDatabasePath("${ProfileDataDirNames.INDEX_DB_BASE_NAME}_$profileId.db")

/**
 * The [DatabaseProvider] of a cache pointing at [dbName] (the Media3 index): the base
 * database keeps [StandaloneDatabaseProvider] bit-for-bit (Media3 1.10.1 has no
 * custom-name constructor on it); a suffixed profile database wraps an anonymous
 * [SQLiteOpenHelper] with EMPTY `onCreate`/`onUpgrade` — the same behavior as
 * StandaloneDatabaseProvider: the Media3 features create their own tables when missing.
 */
fun cacheDatabaseProvider(context: Context, dbName: String): DatabaseProvider =
    if (dbName == ProfileDataDirNames.INDEX_DB_FILE) StandaloneDatabaseProvider(context)
    else DefaultDatabaseProvider(
        object : SQLiteOpenHelper(context, dbName, null, 1) {
            // Empty on purpose: StandaloneDatabaseProvider does the same.
            override fun onCreate(db: SQLiteDatabase) = Unit
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
    )

/**
 * The [dirOf] directories of the base AND of every user profile, deduplicated by absolute
 * path — a profile that shares the item maps onto the base directory, which is already
 * present, so the base location is counted exactly once. Null dirs (a Disabled cache,
 * process-local temp dir) are dropped.
 */
fun allDataDirs(context: Context, dirOf: (String) -> File?): List<File> =
    (listOf(dirOf(DEFAULT_PROFILE_ID)) + context.readProfileIds().map { dirOf(it) })
        .filterNotNull()
        .distinctBy { it.absolutePath }

/** Total size in bytes of [dir] (recursive; 0 when it does not exist). */
fun totalDirectorySize(dir: File): Long {
    val children = dir.listFiles() ?: return 0L
    return children.sumOf { if (it.isDirectory) totalDirectorySize(it) else it.length() }
}
