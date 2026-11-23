package app.n_zik.android.components.settings.data

import java.io.File
import java.io.IOException
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.ProfileDataDirNames
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ScopedTemporaryFiles
import app.n_zik.android.core.profiles.profileSharedItems
import app.n_zik.android.core.profiles.profileShares
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.PROFILE_NAMES_FILE_NAME
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.isProfileIdSafe
import app.n_zik.android.utils.parseProfileIds
import app.n_zik.android.utils.profileDisplayName
import app.n_zik.android.utils.resolveProfileDisplayName

internal val themeStorageDirectories = setOf(
    "theme-store-catalog", "theme-preview-v1", "theme-preview-working",
    "theme-preview-output", "theme-submission-previews", "theme-submission-imports",
)

internal fun storagePayloadBytes(allocated: Long, logical: Long): Long =
    logical.coerceIn(0L, allocated)

/** Subtract each category from the available budget, avoiding overflow from inconsistent inputs. */
internal fun partitionStorage(
    totalBytes: Long,
    freeBytes: Long,
    appBytes: Map<StorageCategory, Long>,
): StorageBreakdown? {
    if (totalBytes <= 0L || freeBytes !in 0L..totalBytes) return null
    var otherUsed = totalBytes - freeBytes
    for (category in StorageCategory.entries) {
        if (category == StorageCategory.OTHER_USED || category == StorageCategory.FREE) continue
        val size = appBytes[category] ?: 0L
        if (size !in 0L..otherUsed) return null
        otherUsed -= size
    }
    val bytes = StorageCategory.entries.associateWith { category ->
        when (category) {
            StorageCategory.OTHER_USED -> otherUsed
            StorageCategory.FREE -> freeBytes
            else -> appBytes[category] ?: 0L
        }
    }
    return StorageBreakdown(totalBytes, bytes)
}

internal class StorageDirectories(profileIds: List<String>) {
    private val knownNames = buildMap {
        for (profileId in listOf(DEFAULT_PROFILE_ID) + profileIds) {
            // Include separate folders left behind after a profile starts sharing an item.
            val names = resolveProfileDataDirNames(profileId, emptySet())
            put(names.imageCacheDir, StorageCategory.IMAGES)
            put(names.mediaCacheDir, StorageCategory.STREAMING)
            put(names.downloadsDir, StorageCategory.DOWNLOADS)
            put(names.coversDir, StorageCategory.COVERS)
            put(names.waveformsDir, StorageCategory.WAVEFORMS)
        }
    }
    private val audioPrefixes = knownNames.filterValues {
        it == StorageCategory.STREAMING || it == StorageCategory.DOWNLOADS
    }
    private val baseNames = mapOf(
        ProfileDataDirNames.IMAGE_CACHE_DIR to StorageCategory.IMAGES,
        ProfileDataDirNames.MEDIA_CACHE_DIR to StorageCategory.STREAMING,
        ProfileDataDirNames.DOWNLOADS_DIR to StorageCategory.DOWNLOADS,
        ProfileDataDirNames.COVERS_DIR to StorageCategory.COVERS,
        ProfileDataDirNames.WAVEFORMS_DIR to StorageCategory.WAVEFORMS,
    )

    fun category(name: String, inFiles: Boolean, inCache: Boolean, inTemp: Boolean): StorageCategory {
        if (inFiles || inCache) {
            knownNames[name]?.let { if (inFiles || it.isCache) return it }
        }
        if (inTemp) {
            for ((prefix, category) in audioPrefixes) {
                if (name.startsWith(prefix) && name.substring(prefix.length).toULongOrNull() != null) {
                    return category
                }
            }
        }
        if (inFiles || inCache) {
            // Older profile IDs may be absent from the current profiles list.
            for ((base, category) in baseNames) {
                val prefix = "${base}_"
                if ((inFiles || category.isCache) && name.startsWith(prefix)) {
                    val id = name.substring(prefix.length)
                    if (id.isNotBlank() && isProfileIdSafe(id)) return category
                }
            }
        }
        return StorageCategory.OTHER_NZIK
    }

    private val StorageCategory.isCache: Boolean
        get() = this != StorageCategory.COVERS && this != StorageCategory.WAVEFORMS
}

internal fun storageAllocationCategory(category: StorageCategory, name: String, directory: Boolean): StorageCategory {
    if (directory) return when (category) {
        StorageCategory.IMAGES, StorageCategory.STREAMING, StorageCategory.DOWNLOADS,
        StorageCategory.WAVEFORMS, StorageCategory.NETWORK, StorageCategory.DIAGNOSTICS,
        StorageCategory.TEMPORARY, StorageCategory.MIGRATION -> StorageCategory.OTHER_NZIK
        else -> category
    }
    return when (category) {
        StorageCategory.IMAGES, StorageCategory.NETWORK -> when (name) {
            "journal", "journal.tmp", "journal.bkp" -> StorageCategory.OTHER_NZIK
            else -> category
        }
        StorageCategory.STREAMING, StorageCategory.DOWNLOADS ->
            if (name.endsWith(".uid")) StorageCategory.OTHER_NZIK else category
        else -> category
    }
}

private data class StorageAllocation(val category: StorageCategory, val owner: String? = null)

private class StorageVolumeAccumulator(private val path: File) {
    private val before = StatFs(path.path)
    val bytes = mutableMapOf<StorageCategory, Long>()
    var hasFiles = false
    val ownersWithFiles = mutableSetOf<String>()

    fun partition(): StorageBreakdown? {
        val after = StatFs(path.path)
        if (before.totalBytes != after.totalBytes || partitionStorage(before.totalBytes, before.availableBytes, bytes) == null) return null
        return partitionStorage(after.totalBytes, after.availableBytes, bytes)
    }
}

private data class StorageVolume(val identity: String, val label: String?)
private const val INTERNAL_STORAGE_UUID = "41217664-9172-527a-b3d5-edabb50a7d69"

internal fun storageVolumeIdentity(device: Long, uuid: String?, primaryEmulated: Boolean): String =
    when {
        primaryEmulated -> "uuid:$INTERNAL_STORAGE_UUID"
        uuid != null -> "uuid:${uuid.lowercase()}"
        else -> "device:$device"
    }

private fun resolveVolume(context: Context, manager: StorageManager?, path: File, device: Long): StorageVolume {
    val storage = runCatching { manager?.getStorageVolume(path) }.getOrNull()
    val uuid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { manager?.getUuidForPath(path)?.toString() }.getOrNull()
    } else null
    val primaryEmulated = storage?.isPrimary == true && storage.isEmulated && storage.uuid == null
    val knownInternal = path.path == "/data" || path.path.startsWith("/data/")
    return StorageVolume(
        storageVolumeIdentity(device, uuid ?: storage?.uuid ?: INTERNAL_STORAGE_UUID.takeIf { knownInternal }, primaryEmulated),
        storage?.getDescription(context),
    )
}

internal suspend fun measureStorage(context: Context): StorageBreakdown? = withContext(NzikDispatchers.DATA) {
    try {
        val root = File(context.applicationInfo.dataDir).canonicalFile
        val files = root.resolve("files")
        val cache = root.resolve("cache")
        val prefs = root.resolve("shared_prefs")
        val databases = root.resolve("databases")
        val externalRoots = (context.externalCacheDirs.asList() + context.getExternalFilesDirs(null).asList())
            .filterNotNull().filter { it.isDirectory }.map { it.canonicalFile }.distinct()
        val temp = System.getProperty("java.io.tmpdir")?.let { File(it).canonicalFile }
        val profileFile = files.resolve(PROFILE_NAMES_FILE_NAME)
        fun readIdentities(): Map<String, ProfileStorageIdentity> {
            val registered = if (profileFile.canonicalFile == profileFile && profileFile.isFile) {
                parseProfileIds(profileFile.readText())
            } else emptyList()
            return (listOf(DEFAULT_PROFILE_ID) + registered).mapNotNull { id ->
                profileStorageIdentity(context, id)?.let { id to it }
            }.toMap()
        }
        val identities = DatabaseRestoreJournal.withProfileStorageMutation(context, ::readIdentities)
        val ids = identities.keys
        val profileIds = ids.filterNot { it == DEFAULT_PROFILE_ID }
        val scopedTempNamespaces = setOf(
            ScopedTemporaryFiles.IMAGE_EDITOR, ScopedTemporaryFiles.REWIND_CAPTURE, ScopedTemporaryFiles.PRIVATE_ARTWORK,
        )
        val directories = StorageDirectories(profileIds)
        val media = buildMap {
            for (id in ids) {
                val names = resolveProfileDataDirNames(id, emptySet())
                fun register(name: String, item: ProfileDataItem, category: StorageCategory) {
                    val shared = id == DEFAULT_PROFILE_ID && profileIds.any { context.profileShares(item, it) }
                    put(name, if (shared) StorageAllocation(StorageCategory.HISTORICAL) else StorageAllocation(category, id))
                }
                register(names.imageCacheDir, ProfileDataItem.IMAGES, StorageCategory.IMAGES)
                register(names.coversDir, ProfileDataItem.IMAGES, StorageCategory.COVERS)
                register(names.mediaCacheDir, ProfileDataItem.MEDIA_CACHE, StorageCategory.STREAMING)
                register(names.downloadsDir, ProfileDataItem.DOWNLOADS, StorageCategory.DOWNLOADS)
                register(names.waveformsDir, ProfileDataItem.WAVEFORMS, StorageCategory.WAVEFORMS)
            }
        }
        val databasesByName = buildMap {
            for (id in ids) {
                val suffix = if (id == DEFAULT_PROFILE_ID) "" else "_$id"
                val index = "exoplayer_internal$suffix.db"
                val sharedIndex = id == DEFAULT_PROFILE_ID && profileIds.any {
                    context.profileShares(ProfileDataItem.DOWNLOADS, it) || context.profileShares(ProfileDataItem.MEDIA_CACHE, it)
                }
                for (side in listOf("", "-wal", "-shm", "-journal")) {
                    put("data$suffix.db$side", StorageAllocation(StorageCategory.OTHER_NZIK, id))
                    put(index + side, if (sharedIndex) StorageAllocation(StorageCategory.HISTORICAL)
                    else StorageAllocation(StorageCategory.OTHER_NZIK, id))
                }
            }
        }
        val prefsByName = buildMap {
            for (id in ids) {
                val suffix = if (id == DEFAULT_PROFILE_ID) "" else "_$id"
                val names = listOf("preferences$suffix", "secure_preferences$suffix", GlassProfilePreferences.fileName(id))
                for (name in names) {
                    put("$name.xml", StorageAllocation(StorageCategory.OTHER_NZIK, id))
                    put("$name.xml.bak", StorageAllocation(StorageCategory.OTHER_NZIK, id))
                }
                identities[id]?.let(ImageCacheFactory::profileMetadataFiles)?.forEach { name ->
                    put(name, StorageAllocation(StorageCategory.OTHER_NZIK, id))
                }
            }
        }

        fun classify(file: File, parent: StorageAllocation): StorageAllocation {
            val name = file.name
            if (name in setOf(".profile-cache-cleanup", ".historical-recovery.pending", "profile-state-restore-journal", "profile-state-names.pending") || name.startsWith(".nzik-deferred-") || name.startsWith(".download-probe-") || name.startsWith(".profile-state-prepare-")) {
                return StorageAllocation(StorageCategory.MIGRATION)
            }
            val parentFile = file.parentFile
            if (parentFile == files && name in themeStorageDirectories) {
                return StorageAllocation(StorageCategory.THEMES)
            }
            if (parentFile == cache && name == ScopedTemporaryFiles.PUBLIC_ARTWORK) {
                return StorageAllocation(StorageCategory.TEMPORARY)
            }
            if (parentFile != null && parentFile.parentFile == cache && parentFile.name in scopedTempNamespaces) {
                return StorageAllocation(StorageCategory.HISTORICAL)
            }
            val namespace = parentFile?.parentFile
            if (namespace != null && namespace.parentFile == cache && namespace.name in scopedTempNamespaces) {
                val owner = parentFile?.name?.let(identities::get)
                return if (owner != null && owner.generation == name) StorageAllocation(StorageCategory.TEMPORARY, owner.id)
                else StorageAllocation(StorageCategory.HISTORICAL)
            }
            if (parentFile == files || parentFile == cache || parentFile == temp) {
                if (name.startsWith(".nzik-location-") || name.startsWith(".nzik-retired-") ||
                    name.startsWith(".nzik-separation-") || name.startsWith(".legacy-media") ||
                    name.startsWith(".legacy-image") || name.startsWith(".download-cleanup-") || name == ".profile-download-cleanup") {
                    return StorageAllocation(StorageCategory.MIGRATION)
                }
                if (name in setOf("rescue_backups", "protected-profile-history", "logs", "thumbnail", "n_zik_http_cache")) {
                    return StorageAllocation(StorageCategory.HISTORICAL)
                }
                if (name.startsWith("waveform_temp_")) {
                    val owner = name.removePrefix("waveform_temp_").takeIf { it in ids }
                    return if (owner == null) StorageAllocation(StorageCategory.HISTORICAL)
                    else StorageAllocation(StorageCategory.TEMPORARY, owner)
                }
                media[name]?.let { return it }
                if (directories.category(name, parentFile == files, parentFile == cache, parentFile == temp) != StorageCategory.OTHER_NZIK) {
                    return StorageAllocation(StorageCategory.HISTORICAL)
                }
            }
            if (parentFile == files.resolve("profiles")) {
                return if (name in ids) StorageAllocation(StorageCategory.OTHER_NZIK, name)
                else StorageAllocation(StorageCategory.HISTORICAL)
            }
            if (name == "diagnostics" && parent.owner != null && parentFile == files.resolve("profiles/${parent.owner}")) {
                return StorageAllocation(StorageCategory.DIAGNOSTICS, parent.owner)
            }
            if (parentFile?.name == "nzik-profile-network" &&
                parentFile.parentFile?.let { it == cache || it in externalRoots } == true
            ) {
                return if (name in ids) StorageAllocation(StorageCategory.NETWORK, name)
                else StorageAllocation(StorageCategory.HISTORICAL)
            }
            if (parentFile == databases) {
                databasesByName[name]?.let { return it }
                return StorageAllocation(StorageCategory.HISTORICAL)
            }
            if (parentFile == prefs) {
                prefsByName[name]?.let { return it }
                if (name.startsWith("preferences_") || name.startsWith("secure_preferences_")) {
                    return StorageAllocation(StorageCategory.HISTORICAL)
                }
            }
            return parent
        }

        val volumes = linkedMapOf<String, StorageVolumeAccumulator>()
        val volumeLabels = mutableMapOf<String, String>()
        val profileUsage = mutableMapOf<Pair<String, String>, MutableMap<StorageCategory, Long>>()
        val profileFileCategories = mutableMapOf<Pair<String, String>, MutableSet<StorageCategory>>()
        val seen = mutableSetOf<Pair<Long, Long>>()
        val samplingContext = currentCoroutineContext()
        fun add(bytes: MutableMap<StorageCategory, Long>, category: StorageCategory, allocated: Long) {
            bytes[category] = Math.addExact(bytes[category] ?: 0L, allocated)
        }
        val storageManager = context.getSystemService(StorageManager::class.java)
        val mainDevice = Os.lstat(root.path).st_dev
        val mainVolume = resolveVolume(context, storageManager, root, mainDevice)
        val aliases = mutableSetOf<Pair<String, String>>()
        fun scan(file: File, inherited: StorageAllocation, rootVolumes: MutableMap<Long, StorageVolume>) {
            samplingContext.ensureActive()
            val stat = try {
                Os.lstat(file.path)
            } catch (error: ErrnoException) {
                if (file != root && error.errno == OsConstants.ENOENT) return
                throw error
            }
            val directory = OsConstants.S_ISDIR(stat.st_mode)
            if (!directory && !OsConstants.S_ISREG(stat.st_mode)) return
            if (!seen.add(stat.st_dev to stat.st_ino)) return
            val physical = rootVolumes.getOrPut(stat.st_dev) { resolveVolume(context, storageManager, file, stat.st_dev) }
            physical.label?.let { volumeLabels.putIfAbsent(physical.identity, it) }
            val volume = volumes.getOrPut(physical.identity) { StorageVolumeAccumulator(file) }
            val allocation = if (!directory && stat.st_nlink > 1) StorageAllocation(StorageCategory.HISTORICAL)
                else classify(file, inherited)
            val allocated = Math.multiplyExact(stat.st_blocks, 512L)
            if (!directory && allocated > 0L) {
                volume.hasFiles = true
                allocation.owner?.let(volume.ownersWithFiles::add)
            }
            val category = storageAllocationCategory(allocation.category, file.name, directory)
            val owner = allocation.owner
            val nestedPayload = when {
                directory || owner == null || category != StorageCategory.OTHER_NZIK -> null
                file == prefs.resolve("preferences${if (owner == DEFAULT_PROFILE_ID) "" else "_$owner"}.xml") ->
                    StorageCategory.THEMES to themeProfilePayloadBytes(context, owner)
                file == databases.resolve(Database.fileNameForProfile(owner)) ->
                    StorageCategory.LYRICS to measureLyricsTextBytes(file)
                else -> null
            }
            val payload = storagePayloadBytes(allocated, nestedPayload?.second ?: 0L)
            add(volume.bytes, category, allocated - payload)
            if (nestedPayload != null) add(volume.bytes, nestedPayload.first, payload)
            owner?.let {
                val key = physical.identity to owner
                val profileBytes = profileUsage.getOrPut(key) { mutableMapOf() }
                add(profileBytes, category, allocated - payload)
                if (nestedPayload != null) add(profileBytes, nestedPayload.first, payload)
                if (!directory && isMeasuredCleanupPayload(allocation.category, file, stat.st_size)) {
                    profileFileCategories.getOrPut(key) { mutableSetOf() }.add(allocation.category)
                }
            }
            if (directory) {
                val children = file.listFiles() ?: throw IOException("Unable to list app storage")
                children.forEach { child -> scan(child, allocation, rootVolumes) }
            }
        }

        val installation = StorageAllocation(StorageCategory.INSTALLATION)
        scan(root, installation, mutableMapOf(mainDevice to mainVolume))
        externalRoots.forEach { external ->
            val device = Os.lstat(external.path).st_dev
            val physical = resolveVolume(context, storageManager, external, device)
            val androidRelative = external.invariantSeparatorsPath.substringAfter("/Android/", missingDelimiterValue = "")
            if (androidRelative.isEmpty() || aliases.add(physical.identity to androidRelative)) {
                scan(external, installation, mutableMapOf(device to physical))
            }
        }
        val primary = volumes.getValue(mainVolume.identity).partition() ?: return@withContext null
        val measuredProfiles = DatabaseRestoreJournal.withProfileStorageMutation(context) {
            if (readIdentities() != identities) return@withProfileStorageMutation null
            identities.map { (id, identity) ->
                val label = resolveProfileDisplayName(id, context.profileDisplayName(id), context.getString(R.string.profile_base_name))
                val shared = if (id == DEFAULT_PROFILE_ID) {
                    val consumed = profileIds.flatMap { context.profileSharedItems(it) }.toSet()
                    if (ProfileDataItem.DOWNLOADS in consumed || ProfileDataItem.MEDIA_CACHE in consumed) {
                        consumed + ProfileDataItem.DOWNLOADS + ProfileDataItem.MEDIA_CACHE
                    } else consumed
                } else context.profileSharedItems(id)
                val key = mainVolume.identity to id
                ProfileStorageUsage(
                    identity, label, profileUsage[key].orEmpty(), shared, profileFileCategories[key].orEmpty().toSet(),
                )
            }
        } ?: return@withContext null
        primary.copy(
            profiles = measuredProfiles,
            otherVolumes = volumes.filterKeys { it != mainVolume.identity }.mapNotNull { (identity, volume) ->
                volume.partition()?.copy(
                    volumeLabel = volumeLabels[identity],
                    filePresence = StorageFilePresence(volume.hasFiles, volume.ownersWithFiles.toSet()),
                    profiles = measuredProfiles.map { profile ->
                        val key = identity to profile.identity.id
                        profile.copy(
                            bytes = profileUsage[key].orEmpty(),
                            fileCategories = profileFileCategories[key].orEmpty().toSet(),
                        )
                    },
                )
            },
        )
    } catch (_: ErrnoException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
