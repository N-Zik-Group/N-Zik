package app.n_zik.android.components.settings.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.PROFILE_NAMES_FILE_NAME
import app.n_zik.android.utils.isProfileIdSafe
import app.n_zik.android.utils.parseProfileIds
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.utils.profileDisplayName
import app.n_zik.android.utils.resolveProfileDisplayName
import app.n_zik.android.utils.semiBold
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.settings.SettingsDialogTabRow
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.components.settings.other.OtherOnDeviceCard
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.ui.InlineLoader
import app.n_zik.android.components.glass.GlassCustomChip
import app.n_zik.android.components.glass.glassChipBackground
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.core.profiles.LegacySeparationStatus
import app.n_zik.android.core.profiles.ProfileDataDirNames
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ScopedTemporaryFiles
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.profiles.profileShares
import app.n_zik.android.core.profiles.profileSharedItems
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.isProcessStorageProfile
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import app.n_zik.android.typography
import app.n_zik.android.thumbnailShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal enum class StorageCategory(val label: Int, val description: Int, val color: Color) {
    IMAGES(R.string.storage_breakdown_cached_images, R.string.storage_info_images, Color(0xFFE09B40)),
    STREAMING(R.string.storage_breakdown_streamed_audio, R.string.storage_info_streaming, Color(0xFF9B70D0)),
    DOWNLOADS(R.string.storage_breakdown_downloaded_audio, R.string.storage_info_downloads, Color(0xFF43A064)),
    WAVEFORMS(R.string.storage_breakdown_waveforms, R.string.storage_info_waveforms, Color(0xFF44A9A7)),
    COVERS(R.string.storage_breakdown_custom_covers, R.string.storage_info_covers, Color(0xFFCA6A96)),
    NETWORK(R.string.storage_breakdown_network, R.string.storage_info_network, Color(0xFF6B9AC0)),
    DIAGNOSTICS(R.string.storage_breakdown_diagnostics, R.string.storage_info_diagnostics, Color(0xFFC7789D)),
    TEMPORARY(R.string.storage_breakdown_temporary, R.string.storage_info_temporary, Color(0xFFAA7B57)),
    OTHER_NZIK(R.string.storage_breakdown_other_nzik_data, R.string.storage_info_other_nzik, Color(0xFFB58664)),
    INSTALLATION(R.string.storage_breakdown_installation, R.string.storage_info_installation, Color(0xFFBD8F7E)),
    HISTORICAL(R.string.storage_breakdown_historical, R.string.storage_info_historical, Color(0xFFB2A480)),
    MIGRATION(R.string.storage_breakdown_preparation, R.string.storage_info_preparation, Color(0xFF85775F)),
    OTHER_USED(R.string.storage_breakdown_other_used_space, R.string.storage_info_other_used, Color(0xFF608FE1)),
    FREE(R.string.storage_breakdown_free_space, R.string.storage_info_free, Color(0xFFA3A7B4)),
}

internal data class StorageBreakdown(
    val totalBytes: Long,
    val bytes: Map<StorageCategory, Long>,
    val profiles: List<ProfileStorageUsage> = emptyList(),
    val otherVolumes: List<StorageBreakdown> = emptyList(),
    val volumeLabel: String? = null,
    val filePresence: StorageFilePresence? = null,
) {
    val nzikBytes: Long
        get() = totalBytes - (bytes[StorageCategory.OTHER_USED] ?: 0L) - (bytes[StorageCategory.FREE] ?: 0L)
}

/** A volume with empty Android-created directories has no N-Zik files to show in its detail. */
internal data class StorageFilePresence(val any: Boolean, val owners: Set<String>)

internal fun StorageBreakdown.hasAppFilesFor(profile: ProfileStorageIdentity?): Boolean {
    val measured = filePresence
    if (measured != null && (if (profile == null) !measured.any else profile.id !in measured.owners)) return false
    return appChartFor(profile).nzikBytes > 0L
}

internal fun StorageBreakdown.appChartFor(profile: ProfileStorageIdentity?): StorageBreakdown =
    if (profile == null) this else profiles.firstOrNull { it.identity == profile }?.chart ?: StorageBreakdown(0L, emptyMap())

private val displayCategories = listOf(
    StorageCategory.DOWNLOADS, StorageCategory.STREAMING, StorageCategory.WAVEFORMS,
    StorageCategory.IMAGES, StorageCategory.NETWORK, StorageCategory.DIAGNOSTICS, StorageCategory.TEMPORARY,
    StorageCategory.OTHER_NZIK, StorageCategory.INSTALLATION, StorageCategory.COVERS,
    StorageCategory.HISTORICAL, StorageCategory.MIGRATION,
)

internal fun storageAppCategories(isProfile: Boolean) =
    if (isProfile) displayCategories.filterNot { it == StorageCategory.INSTALLATION } else displayCategories

internal fun storageCategoryLabel(category: StorageCategory, isProfile: Boolean): Int = when (category) {
    StorageCategory.OTHER_NZIK -> if (isProfile) R.string.storage_breakdown_other_profile_data
        else R.string.storage_breakdown_other_profiles_data
    StorageCategory.OTHER_USED -> R.string.storage_breakdown_other_device_data
    StorageCategory.FREE -> R.string.storage_breakdown_device_free
    else -> category.label
}

internal fun StorageBreakdown.displayChart(includeDevice: Boolean): StorageBreakdown {
    val order = if (includeDevice) listOf(StorageCategory.OTHER_USED) + displayCategories + StorageCategory.FREE
        else displayCategories
    val ordered = order.associateWith { bytes[it] ?: 0L }.filterValues { it > 0L }
    return StorageBreakdown(if (includeDevice) totalBytes else nzikBytes, ordered)
}

internal data class ProfileStorageUsage(
    val identity: ProfileStorageIdentity,
    val label: String,
    val bytes: Map<StorageCategory, Long>,
    val sharedItems: Set<ProfileDataItem> = emptySet(),
    val fileCategories: Set<StorageCategory> = emptySet(),
) {
    val chart: StorageBreakdown get() = StorageBreakdown(bytes.values.sum(), bytes)
}

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
            add(volume.bytes, category, allocated)
            allocation.owner?.let { owner ->
                val key = physical.identity to owner
                add(profileUsage.getOrPut(key) { mutableMapOf() }, category, allocated)
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

internal fun storageSamples(measure: suspend () -> StorageBreakdown?): Flow<StorageBreakdown> = flow {
    while (true) {
        measure()?.let { emit(it) }
        delay(2_000)
    }
}

@Composable
private fun rememberScreenInteractive(context: Context): Boolean {
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var interactive by remember(power) { mutableStateOf(power.isInteractive) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                interactive = power.isInteractive
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        interactive = power.isInteractive
        onDispose { context.unregisterReceiver(receiver) }
    }
    return interactive
}

private data class StorageSummaryScope(val ready: Boolean, val profile: ProfileStorageIdentity?)
private enum class StorageDialog { INFO, VOLUMES }

private fun Modifier.disableOutgoingStorageContent(active: Boolean): Modifier =
    if (active) this else clearAndSetSemantics {}.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

@UnstableApi
@Composable
internal fun DataStorageCard(
    search: Search,
    restartState: MutableState<Boolean>,
    measure: suspend (Context) -> StorageBreakdown? = ::measureStorage,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val interactive = rememberScreenInteractive(appContext)
    var breakdown by remember(appContext) { mutableStateOf<StorageBreakdown?>(null) }
    var cleanupRevision by remember { mutableLongStateOf(0L) }
    var storageDialog by remember { mutableStateOf<StorageDialog?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGeneration by rememberSaveable { mutableStateOf<String?>(null) }
    val searchProfile = LocalSettingsTargets.current?.storageProfileTarget
    val pageQuery = search.inputValue
    val pageEntryTargets = remember(context, pageQuery) {
        if (pageQuery.isBlank()) emptySet() else searchSettingsEntries(context, pageQuery).map { it.id }.toSet()
    }
    val selected = breakdown?.profiles?.firstOrNull {
        it.identity.id == selectedId && it.identity.generation == selectedGeneration
    }
    val additionalVolumes = breakdown?.otherVolumes.orEmpty().filter { it.hasAppFilesFor(selected?.identity) }
    val summaryScope = StorageSummaryScope(breakdown != null, selected?.identity)
    val cacheTarget = selected?.identity
    LaunchedEffect(searchProfile) {
        if (searchProfile != null) {
            selectedId = searchProfile.id
            selectedGeneration = searchProfile.generation
        }
    }
    LaunchedEffect(pageQuery) {
        if (pageQuery.isBlank() || selectedId != null || searchProfile != null) return@LaunchedEffect
        if (pageEntryTargets.none {
                (it.startsWith("data.cache.") && !it.startsWith("data.cache.clear-")) ||
                    it.startsWith("embedded.auto_backup.") || it.startsWith("other.on_device.")
            }
        ) return@LaunchedEffect
        val active = withContext(NzikDispatchers.DATA) {
            profileStorageIdentity(context, getActiveProfile(context))
        }
        if (selectedId == null && active != null) {
            selectedId = active.id
            selectedGeneration = active.generation
        }
    }
    LaunchedEffect(breakdown, selectedId, selectedGeneration) {
        if (breakdown != null && selectedId != null && selected == null) {
            selectedId = null
            selectedGeneration = null
        }
    }
    LaunchedEffect(appContext, lifecycle, interactive, cleanupRevision, measure) {
        if (interactive) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val sample = measure(appContext)
                unavailable = sample == null
                if (sample != null) breakdown = sample
                delay(2_000)
            }
        }
    }

    Column {
        SettingsSectionCard(
            modifier = Modifier.testTag("data.card.storage"),
            title = stringResource(R.string.storage_breakdown_title),
            icon = R.drawable.folder,
            headerAction = {
                val label = stringResource(R.string.storage_breakdown_info_title)
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                    val accent = colorPalette().accent
                    GlassCustomChip(
                        color = accent.copy(alpha = 0.1f),
                        modifier = Modifier.size(32.dp).testTag("data.storage.info"),
                        role = Role.Button,
                        onClick = { storageDialog = StorageDialog.INFO },
                    ) { chipModifier ->
                        Box(chipModifier, contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.information),
                                contentDescription = label,
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
            content = {
                val profiles = breakdown?.profiles.orEmpty()
                val storageInset = 16.dp
                Box(Modifier.fillMaxWidth().padding(horizontal = storageInset)) {
                    SettingsDialogTabRow(
                        labels = listOf(stringResource(R.string.storage_breakdown_tab_total)) + profiles.map { it.label },
                        selectedTabIndex = profiles.indexOfFirst { it.identity == selected?.identity } + 1,
                        containerTestTag = "data.storage.tabs",
                        onTabSelected = { index ->
                            val identity = profiles.getOrNull(index - 1)?.identity
                            selectedId = identity?.id
                            selectedGeneration = identity?.generation
                        },
                        equalWidthWhenFitting = true,
                        followNestedTarget = false,
                    )
                }
                if (unavailable) SettingsDescription(
                    stringResource(
                        if (breakdown == null) R.string.storage_breakdown_measurement_unavailable
                        else R.string.storage_breakdown_measurement_stale,
                    ),
                    important = true,
                )
                AnimatedContent(
                    targetState = summaryScope,
                    modifier = Modifier.padding(horizontal = storageInset).padding(top = 14.dp, bottom = 4.dp),
                    transitionSpec = {
                        (fadeIn(tween(160)) togetherWith fadeOut(tween(160)))
                            .using(SizeTransform { _, _ -> tween(160) })
                    },
                    label = "storageSummaryScope",
                ) { scope ->
                    val snapshot = if (scope.ready) breakdown else null
                    val scoped = snapshot?.profiles?.firstOrNull { it.identity == scope.profile }
                    Box(Modifier.fillMaxWidth().disableOutgoingStorageContent(scope == summaryScope)) {
                        if (snapshot == null && !unavailable) {
                            val loadingDescription = stringResource(R.string.storage_breakdown_waiting)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                InlineLoader(Modifier.semantics { contentDescription = loadingDescription })
                            }
                        } else if (snapshot != null && (scope.profile == null || scoped != null)) {
                            StorageScopeSummary(snapshot, scoped)
                        }
                    }
                }
                AnimatedContent(
                    targetState = additionalVolumes.size,
                    transitionSpec = {
                        (fadeIn(tween(160)) togetherWith fadeOut(tween(160)))
                            .using(SizeTransform { _, _ -> tween(160) })
                    },
                    label = "storageOtherVolumes",
                ) { count ->
                    if (count > 0) {
                        GlassCustomChip(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            modifier = Modifier.padding(horizontal = storageInset)
                                .heightIn(min = 48.dp)
                                .disableOutgoingStorageContent(count == additionalVolumes.size)
                                .testTag("data.storage.additional-volumes"),
                            onClick = { if (additionalVolumes.isNotEmpty()) storageDialog = StorageDialog.VOLUMES },
                        ) { chipModifier ->
                            Box(chipModifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                BasicText(
                                    text = stringResource(R.string.storage_breakdown_other_volume_details, count),
                                    style = typography().xxs.semiBold.copy(color = colorPalette().accent),
                                )
                            }
                        }
                    }
                }
                StorageSeparationPending(profiles, selected)
                key(selected?.identity) {
                    val scopeWithAllVolumes = profiles.map { profile ->
                        val otherFileCategories = breakdown?.otherVolumes.orEmpty().flatMap { volume ->
                            volume.profiles.firstOrNull { it.identity == profile.identity }?.fileCategories.orEmpty()
                        }
                        profile.copy(fileCategories = profile.fileCategories + otherFileCategories)
                    }
                    DataStorageCleanup(
                        scopeWithAllVolumes,
                        selected?.let { current -> scopeWithAllVolumes.firstOrNull { it.identity == current.identity } },
                        onCleanupComplete = { cleanupRevision++ },
                    )
                }
            },
        )
        if (cacheTarget != null) key(cacheTarget) {
            DataCacheCard(search, restartState, cacheTarget)
            DataAutoBackupCard(cacheTarget, pageEntryTargets)
            OtherOnDeviceCard(search, cacheTarget, pageEntryTargets)
        }
    }
    DialogPresence(target = storageDialog) { dialog ->
        DataStorageInfoDialog(
            otherVolumes = if (dialog == StorageDialog.VOLUMES) additionalVolumes else emptyList(),
            selectedProfile = selected?.identity,
            sectionTitle = if (dialog == StorageDialog.INFO) storageSectionTitle(selected) else null,
            onDismiss = { storageDialog = null },
        )
    }
}

@Composable
private fun StorageSeparationPending(profiles: List<ProfileStorageUsage>, selected: ProfileStorageUsage?) {
    val consumers = profiles.filter { it.identity.id != DEFAULT_PROFILE_ID }
    val pending = selected?.let { profile -> profile.sharedItems.map { profile to it } }
        ?: consumers.flatMap { profile -> profile.sharedItems.map { profile to it } }
    val failures by LegacySeparationStatus.failures.collectAsStateWithLifecycle()
    val reasons = pending.map { (profile, item) ->
        val cause = failures[profile.identity to item] ?: if (profile.identity.id == DEFAULT_PROFILE_ID) {
            consumers.firstNotNullOfOrNull { failures[it.identity to item] }
        } else null
        val label = stringResource(when (item) {
            ProfileDataItem.DOWNLOADS -> StorageCategory.DOWNLOADS.label
            ProfileDataItem.MEDIA_CACHE -> StorageCategory.STREAMING.label
            ProfileDataItem.IMAGES -> StorageCategory.IMAGES.label
            ProfileDataItem.WAVEFORMS -> StorageCategory.WAVEFORMS.label
        })
        val reason = stringResource(when (cause) {
            LegacySeparationStatus.Cause.CONFLICT -> R.string.storage_separation_conflict
            LegacySeparationStatus.Cause.QUOTA -> R.string.storage_separation_quota
            LegacySeparationStatus.Cause.SPACE -> R.string.storage_separation_space
            LegacySeparationStatus.Cause.UNVERIFIED, null -> R.string.storage_separation_unverified
        })
        if (selected == null) "${profile.label} · $label · $reason" else "$label · $reason"
    }
    AnimatedVisibility(
        visible = pending.isNotEmpty(),
        enter = fadeIn(tween(170)) + expandVertically(initialHeight = { it * 9 / 10 }),
        exit = fadeOut(tween(110)) + shrinkVertically(targetHeight = { it * 9 / 10 }),
    ) {
        OtherSettingsEntry(
            title = stringResource(R.string.storage_separation_retry),
            text = reasons.joinToString("\n"),
            icon = R.drawable.refresh,
            modifier = Modifier.disableOutgoingStorageContent(pending.isNotEmpty())
                .testTag("data.storage.separation.pending"),
            onClick = { if (pending.isNotEmpty()) RestartAppDialog.showDialog() },
        )
    }
}

@Composable
private fun storageSectionTitle(profile: ProfileStorageUsage?): String {
    if (profile == null) return stringResource(R.string.storage_breakdown_nzik_section)
    val context = LocalContext.current
    val label = if (isProcessStorageProfile(context, profile.identity.id)) R.string.storage_breakdown_named_active_profile
        else R.string.storage_breakdown_named_inactive_profile
    return stringResource(label, profile.label)
}

@Composable
private fun StorageScopeSummary(volume: StorageBreakdown, profile: ProfileStorageUsage?) {
    val context = LocalContext.current
    val ownBytes = profile?.chart?.nzikBytes ?: volume.nzikBytes
    val free = volume.bytes.getValue(StorageCategory.FREE)
    val chart = (profile?.chart ?: volume).displayChart(includeDevice = profile == null)
    val section = storageSectionTitle(profile)
    val totalLabel = stringResource(
        if (profile == null) R.string.storage_breakdown_nzik_total else R.string.storage_breakdown_profile_total,
    )
    val barTotal = Formatter.formatShortFileSize(context, chart.totalBytes)
    val barDescription = if (profile == null) {
        stringResource(R.string.storage_breakdown_device_bar_accessibility, barTotal)
    } else {
        stringResource(R.string.storage_breakdown_bar_accessibility, section, barTotal)
    }

    StorageChart(chart) {
        val chartScope = this
        StorageSectionBand(section, Modifier.testTag("data.storage.nzik-section"))
        if (chart.totalBytes > 0) {
            Spacer(Modifier.height(16.dp))
            Bar(barDescription)
            Spacer(Modifier.height(7.dp))
        } else Spacer(Modifier.height(4.dp))
        storageAppCategories(profile != null).forEach { category ->
            val bytes = chart.bytes[category] ?: 0L
            val label = stringResource(storageCategoryLabel(category, profile != null))
            StorageAmountRow(label, bytes, category.color, chart = chartScope, category = category)
        }
        Spacer(Modifier.height(12.dp))
        StorageAmountRow(totalLabel, ownBytes, emphasizeLabel = true, modifier = Modifier.testTag("data.storage.used"))

        if (profile == null) {
            Column(Modifier.testTag("data.storage.device")) {
                Spacer(Modifier.height(16.dp))
                StorageSectionBand(stringResource(R.string.storage_breakdown_device_section))
                Spacer(Modifier.height(7.dp))
                val other = volume.bytes.getValue(StorageCategory.OTHER_USED)
                StorageAmountRow(
                    stringResource(R.string.storage_breakdown_other_device_data),
                    other,
                    StorageCategory.OTHER_USED.color,
                    chart = chartScope,
                    category = StorageCategory.OTHER_USED,
                )
                StorageAmountRow(
                    stringResource(R.string.storage_breakdown_device_free),
                    free,
                    StorageCategory.FREE.color,
                    chart = chartScope,
                    category = StorageCategory.FREE,
                )
                Spacer(Modifier.height(12.dp))
                StorageAmountRow(
                    stringResource(R.string.storage_breakdown_device_capacity),
                    volume.totalBytes,
                    emphasizeLabel = true,
                    compact = true,
                )
            }
            StorageUsagePercentage(volume.totalBytes - free, volume.totalBytes)
        }
    }
}

@Composable
private fun storageUsagePercentageText(used: Long, capacity: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val ratio = if (capacity > 0L) (used.toDouble() / capacity).coerceIn(0.0, 1.0) else 0.0
    return if (ratio > 0.0 && ratio < 0.01) {
        stringResource(R.string.storage_breakdown_under_percent, formatter.format(0.01))
    } else formatter.format(ratio)
}

@Composable
private fun StorageUsagePercentage(used: Long, capacity: Long) {
    val percentage = storageUsagePercentageText(used, capacity)
    val style = typography().xxs.semiBold.copy(color = colorPalette().text)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(stringResource(R.string.storage_breakdown_usage), Modifier.weight(1f), style = style)
        BasicText(
            text = percentage,
            modifier = Modifier.testTag("data.storage.chart.percentage"),
            style = style.copy(textAlign = TextAlign.End),
            maxLines = 1,
        )
    }
}

@Composable
private fun StorageSectionBand(label: String, modifier: Modifier = Modifier) {
    val palette = colorPalette()
    BasicText(
        text = label,
        modifier = modifier.fillMaxWidth()
            .glassChipBackground(palette.accent, thumbnailShape())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = typography().xxs.semiBold.copy(color = palette.onAccent),
    )
}

@Composable
internal fun StorageAmountRow(
    label: String,
    bytes: Long,
    marker: Color? = null,
    emphasizeLabel: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    chart: StorageChartScope? = null,
    category: StorageCategory? = null,
) {
    val context = LocalContext.current
    val palette = colorPalette()
    val normal = palette.text
    val color = if (chart != null && category != null) chart.color(category, normal) else normal
    val row = if (chart != null && category != null) chart.row(category) else Modifier.semantics(mergeDescendants = true) {}
    val style = typography().xxs.copy(color = color)
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = if (compact) 18.dp else 30.dp)
            .then(row),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (marker != null) {
            val dotColor = if (chart != null && category != null) chart.color(category, marker) else marker
            val dot = if (chart != null && category != null) chart.marker(category) else Modifier
            Box(Modifier.size(10.dp).then(dot).background(dotColor, CircleShape))
        }
        BasicText(text = label, modifier = Modifier.weight(1f), style = if (emphasizeLabel) style.semiBold else style)
        BasicText(
            text = Formatter.formatShortFileSize(context, bytes),
            style = style.semiBold.copy(textAlign = TextAlign.End),
        )
    }
}
