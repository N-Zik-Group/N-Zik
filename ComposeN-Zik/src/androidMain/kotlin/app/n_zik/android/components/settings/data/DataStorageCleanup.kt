package app.n_zik.android.components.settings.data

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.download.utils.ActiveDownloadCleanup
import app.n_zik.android.playback.services.ActiveStreamingCleanup
import app.n_zik.android.download.utils.InactiveDownloadCleanup
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.CardCategoryLabel
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsCardSectionDivider
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.ui.screens.rewind.isExpiredRewindCapture
import app.n_zik.android.components.ui.screens.rewind.withRewindCaptures
import app.n_zik.android.core.profiles.ScopedTemporaryFiles
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import app.n_zik.android.core.profiles.profileSharedItems
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.isLegacyStorageShared
import app.n_zik.android.core.profiles.isProcessStorageProfile
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.utils.debug.debugLogFiles
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.isCurrentProcessStorageProfile
import app.n_zik.android.core.profiles.coilImageCacheDir
import app.n_zik.android.utils.debug.diagnosticFileMutationLock
import app.n_zik.android.core.profiles.waveformsDir
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private val cleanupInProgress = MutableStateFlow(false)

internal enum class CleanupAction(
    val label: Int,
    val icon: Int,
    val description: Int,
    val dialogTitle: Int,
) {
    IMAGES(
        R.string.settings_cache_clear_images_dialog_title,
        R.drawable.image,
        R.string.storage_cleanup_images_action_description,
        R.string.settings_cache_clear_images_dialog_title,
    ),
    STREAMING(
        R.string.storage_cleanup_clear_streaming_cache,
        R.drawable.music_file,
        R.string.storage_cleanup_streaming_action_description,
        R.string.storage_cleanup_clear_streaming_cache,
    ),
    DOWNLOADS(
        R.string.storage_cleanup_clear_downloads,
        R.drawable.download,
        R.string.storage_cleanup_downloads_confirmed_description,
        R.string.storage_cleanup_clear_downloads,
    ),
    WAVEFORMS(
        R.string.storage_cleanup_waveforms_dialog_title,
        R.drawable.equalizer,
        R.string.storage_cleanup_waveforms_action_description,
        R.string.storage_cleanup_waveforms_dialog_title,
    ),
    NETWORK(
        R.string.storage_cleanup_clear_network_cache,
        R.drawable.server,
        R.string.storage_cleanup_network_description,
        R.string.storage_cleanup_clear_network_cache,
    ),
    DIAGNOSTIC_LOGS(
        R.string.storage_cleanup_diagnostic_logs,
        R.drawable.trash,
        R.string.storage_cleanup_logs_action_description,
        R.string.storage_cleanup_diagnostic_logs,
    ),
    TEMPORARY_FILES(
        R.string.storage_cleanup_clear_temporary_files,
        R.drawable.trash,
        R.string.storage_cleanup_temporary_action_description,
        R.string.storage_cleanup_clear_temporary_files,
    ),
    ALL(
        R.string.storage_cleanup_all,
        R.drawable.trash,
        R.string.storage_cleanup_all_confirmed_description,
        R.string.storage_cleanup_all,
    ),
}

private fun CleanupAction.category(): StorageCategory = when (this) {
    CleanupAction.IMAGES -> StorageCategory.IMAGES
    CleanupAction.STREAMING -> StorageCategory.STREAMING
    CleanupAction.DOWNLOADS -> StorageCategory.DOWNLOADS
    CleanupAction.WAVEFORMS -> StorageCategory.WAVEFORMS
    CleanupAction.NETWORK -> StorageCategory.NETWORK
    CleanupAction.DIAGNOSTIC_LOGS -> StorageCategory.DIAGNOSTICS
    CleanupAction.TEMPORARY_FILES -> StorageCategory.TEMPORARY
    CleanupAction.ALL -> StorageCategory.OTHER_NZIK
}

internal data class WaveformCleanupFile(
    val name: String,
    val device: Long,
    val inode: Long,
    val hash: String,
)

internal data class WaveformCleanupTarget(
    val identity: ProfileStorageIdentity?,
    val directory: File,
    val device: Long,
    val inode: Long,
    val files: List<WaveformCleanupFile>,
    val action: CleanupAction = CleanupAction.WAVEFORMS,
)

internal enum class CleanupResult { COMPLETED, SKIPPED, FAILED, SCHEDULED }

private val okHttpCacheEntry = Regex("[0-9a-f]{32}\\.[01](\\.tmp)?")

private data class CleanupNode(val device: Long, val inode: Long, val directory: Boolean)

private fun readCleanupNode(file: File): CleanupNode {
    check(file.canonicalFile == file.absoluteFile)
    if (Build.VERSION.SDK_INT == 0 || File.separatorChar == '\\') {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        check(attributes.isDirectory || attributes.isRegularFile)
        return CleanupNode(
            Files.getFileStore(file.toPath()).name().hashCode().toLong(),
            (attributes.fileKey()?.toString() ?: file.path).hashCode().toLong(),
            attributes.isDirectory,
        )
    }
    val stat = Os.lstat(file.path)
    val directory = OsConstants.S_ISDIR(stat.st_mode)
    check(directory || (OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1L))
    return CleanupNode(stat.st_dev, stat.st_ino, directory)
}

private fun captureFileSet(
    identity: ProfileStorageIdentity?,
    action: CleanupAction,
    anchor: File,
    directory: File,
    allowDirectories: Boolean = false,
    presenceOnly: Boolean = false,
    include: (File) -> Boolean,
): WaveformCleanupTarget {
    check(directory.toPath().startsWith(anchor.toPath()))
    check(directory.canonicalFile == directory)
    if (!directory.exists()) {
        check(!Files.isSymbolicLink(directory.toPath()))
        return WaveformCleanupTarget(identity, directory, -1, -1, emptyList(), action)
    }
    val root = readCleanupNode(directory)
    check(root.directory && root.device == readCleanupNode(anchor).device) { "The cleanup root's volume could not be verified" }
    val files = checkNotNull(directory.listFiles()).filter { file ->
        (if (allowDirectories) file.isDirectory else file.isFile) && include(file)
    }.map { file ->
        val node = readCleanupNode(file)
        check(node.directory == allowDirectories) { "A cleanup candidate changed file type" }
        check(node.device == root.device) { "A cleanup candidate is on a different volume" }
        if (node.directory && !presenceOnly) validateCleanupTree(file, root.device)
        val hash = if (presenceOnly) "" else ProfileResetSnapshot.fingerprint(file)
        WaveformCleanupFile(file.name, node.device, node.inode, hash)
    }.sortedBy { it.name }
    return WaveformCleanupTarget(identity, directory, root.device, root.inode, files, action)
}

private fun validateCleanupTree(directory: File, device: Long) {
    checkNotNull(directory.listFiles()).forEach { child ->
        val node = readCleanupNode(child)
        check(node.device == device)
        if (node.directory) validateCleanupTree(child, device)
    }
}

private val coilCacheEntry = Regex("[0-9a-f]{64}\\.[01](\\.tmp)?")
private val coilJournals = setOf("journal", "journal.tmp", "journal.bkp")

/** Cache bookkeeping is measured on disk but does not by itself create a cleanup action. */
internal fun isMeasuredCleanupPayload(category: StorageCategory, file: File, size: Long): Boolean =
    when (category) {
        StorageCategory.IMAGES, StorageCategory.NETWORK -> file.name !in coilJournals
        StorageCategory.DIAGNOSTICS -> size > 0L
        StorageCategory.DOWNLOADS, StorageCategory.STREAMING -> file.extension != "uid"
        else -> true
    }

private fun captureDiskCache(
    identity: ProfileStorageIdentity,
    action: CleanupAction,
    anchor: File,
    directory: File,
    presenceOnly: Boolean,
    entryPattern: Regex,
): WaveformCleanupTarget {
    check(directory.canonicalFile == directory)
    val hasPayload = directory.listFiles().orEmpty().any { entryPattern.matches(it.name) }
    return captureFileSet(identity, action, anchor, directory, presenceOnly = presenceOnly) {
        entryPattern.matches(it.name) || (hasPayload && it.name in coilJournals)
    }
}


internal fun captureFiles(
    context: Context,
    identity: ProfileStorageIdentity?,
    action: CleanupAction,
    presenceOnly: Boolean = false,
    allowActive: Boolean = false,
): List<WaveformCleanupTarget>? {
    if (identity != null && profileStorageIdentity(context, identity.id) != identity) return null
    val cache = context.cacheDir.canonicalFile
    if (identity == null) {
        if (action != CleanupAction.TEMPORARY_FILES) return null
        return listOf(captureFileSet(null, action, cache, ScopedTemporaryFiles.publicArtwork(context), presenceOnly = presenceOnly) {
            ScopedTemporaryFiles.isExpiredArtwork(it)
        })
    }
    val files = context.filesDir.canonicalFile
    val active = isProcessStorageProfile(context, identity.id)
    return when (action) {
        CleanupAction.WAVEFORMS -> {
            if (!presenceOnly && isLegacyStorageShared(context, identity.id, ProfileDataItem.WAVEFORMS)) return null
            listOf(captureFileSet(identity, action, files, files.resolve(waveformsDir(context, identity.id).name), presenceOnly = presenceOnly) {
                it.extension == "json"
            })
        }
        CleanupAction.DIAGNOSTIC_LOGS -> {
            if (active && !presenceOnly && !allowActive) return null
            val directory = files.resolve("profiles/${identity.id}/diagnostics")
            val names = debugLogFiles(directory).map { it.name }.toSet()
            listOf(captureFileSet(identity, action, files, directory, presenceOnly = presenceOnly) { it.name in names && it.length() > 0L })
        }
        CleanupAction.NETWORK -> {
            if (active && !presenceOnly && !allowActive) return null
            val configured = NetworkClientFactory.profileCacheDirectory(context, identity.id)
            val original = context.externalCacheDir ?: context.cacheDir
            val anchor = original.canonicalFile
            listOf(captureDiskCache(
                identity, action, anchor, anchor.resolve(configured.relativeTo(original).path), presenceOnly, okHttpCacheEntry,
            ))
        }
        CleanupAction.IMAGES -> {
            if (!presenceOnly && ((active && !allowActive) || isLegacyStorageShared(context, identity.id, ProfileDataItem.IMAGES))) return null
            val name = resolveProfileDataDirNames(identity.id, if (presenceOnly) context.profileSharedItems(identity.id) else emptySet()).imageCacheDir
            val caches = listOf(files, cache).distinct().map { anchor ->
                captureDiskCache(identity, action, anchor, anchor.resolve(name), presenceOnly, coilCacheEntry)
            }
            val data = File(context.applicationInfo.dataDir).canonicalFile
            val metadata = ImageCacheFactory.profileMetadataFiles(identity)
            caches + captureFileSet(identity, action, data, data.resolve("shared_prefs"), presenceOnly = presenceOnly) { it.name in metadata }
        }
        CleanupAction.TEMPORARY_FILES -> {
            fun directory(namespace: String) = ScopedTemporaryFiles.directory(context, namespace, identity)
            listOf(
                captureFileSet(identity, action, cache, directory(ScopedTemporaryFiles.IMAGE_EDITOR), allowDirectories = true, presenceOnly = presenceOnly) {
                    PickedImageEditor.isAbandonedDirectory(it)
                },
                captureFileSet(identity, action, cache, directory(ScopedTemporaryFiles.REWIND_CAPTURE), presenceOnly = presenceOnly) {
                    isExpiredRewindCapture(it)
                },
                captureFileSet(identity, action, cache, directory(ScopedTemporaryFiles.PRIVATE_ARTWORK), presenceOnly = presenceOnly) {
                    ScopedTemporaryFiles.isExpiredArtwork(it)
                },
            )
        }
        else -> null
    }
}

internal fun cleanConfirmedWaveforms(context: Context, confirmed: WaveformCleanupTarget): CleanupResult =
    cleanConfirmedFiles(context, listOf(confirmed))

internal fun cleanConfirmedFiles(context: Context, confirmed: List<WaveformCleanupTarget>): CleanupResult =
    DatabaseRestoreJournal.withProfileStorageMutation(context) {
        runCatching {
            val first = confirmed.first()
            val current = captureFiles(context, first.identity, first.action)
                ?: return@withProfileStorageMutation CleanupResult.SKIPPED
            if (confirmed.any { previous ->
                current.none {
                    it.identity == previous.identity && it.action == previous.action &&
                        it.directory == previous.directory && it.device == previous.device && it.inode == previous.inode &&
                        if (first.action == CleanupAction.IMAGES) it.files == previous.files else it.files.containsAll(previous.files)
                }
            }) return@withProfileStorageMutation CleanupResult.SKIPPED
            confirmed.forEach { root ->
                root.files.forEach { file -> ProfileResetSnapshot.deleteVerifiedTree(root.directory.resolve(file.name)) }
            }
            CleanupResult.COMPLETED
        }.getOrElse { error ->
            Timber.tag("DataStorageCleanup").w(error, "Could not clean the confirmed profile files")
            CleanupResult.FAILED
        }
    }

private fun cleanLiveFiles(context: Context, confirmed: List<WaveformCleanupTarget>, imageRevision: Long?): CleanupResult =
    DatabaseRestoreJournal.withProfileStorageMutation(context) {
        val first = confirmed.first()
        val owner = checkNotNull(first.identity)
        if (!isCurrentProcessStorageProfile(context, owner)) return@withProfileStorageMutation CleanupResult.SKIPPED
        if (first.action == CleanupAction.DIAGNOSTIC_LOGS) {
            return@withProfileStorageMutation synchronized(diagnosticFileMutationLock) {
                val current = captureFiles(context, owner, first.action, presenceOnly = true, allowActive = true)
                val verified = confirmed.all { old ->
                    current.orEmpty().any { root ->
                        root.directory == old.directory && root.device == old.device && root.inode == old.inode &&
                            old.files.all { file ->
                                root.files.any { it.name == file.name && it.device == file.device && it.inode == file.inode }
                            }
                    }
                }
                if (!verified) CleanupResult.SKIPPED
                else {
                    confirmed.forEach { root ->
                        root.files.forEach { file -> ProfileResetSnapshot.deleteVerifiedTree(root.directory.resolve(file.name)) }
                    }
                    CleanupResult.COMPLETED
                }
            }
        }
        val verify = { captureFiles(context, owner, first.action, allowActive = true) == confirmed }
        when (first.action) {
            CleanupAction.IMAGES -> {
                val expected = coilImageCacheDir(context, owner.id).canonicalFile
                val disk = confirmed.singleOrNull { it.directory == expected }
                val metadata = confirmed.firstOrNull { it.directory.name == "shared_prefs" }
                    ?.files.orEmpty().map { it.name }.toSet()
                if (isLegacyStorageShared(context, owner.id, ProfileDataItem.IMAGES) ||
                    imageRevision == null || disk == null ||
                    !ImageCacheFactory.clearBoundProfileCache(context, owner, expected, imageRevision, disk.files.isNotEmpty(), metadata, verify)
                ) return@withProfileStorageMutation CleanupResult.SKIPPED
                // The other physical root is closed and was included in the exact preflight.
                confirmed.filter { it.directory != expected && it.directory.name != "shared_prefs" }.forEach { root ->
                    root.files.forEach { file -> ProfileResetSnapshot.deleteVerifiedTree(root.directory.resolve(file.name)) }
                }
                CleanupResult.COMPLETED
            }
            CleanupAction.NETWORK -> {
                val directory = confirmed.single().directory
                val entries = first.files.map { it.name }.filter(okHttpCacheEntry::matches)
                val keys = entries.map { it.substringBefore('.') }.toSet()
                if (entries.any { it.endsWith(".tmp") } || keys.isEmpty()) CleanupResult.SKIPPED
                else if (NetworkClientFactory.clearBoundProfileCache(directory, keys, verify)) CleanupResult.COMPLETED
                else CleanupResult.SKIPPED
            }
            else -> CleanupResult.SKIPPED
        }
    }

private val verifiedActions = setOf(
    CleanupAction.IMAGES, CleanupAction.STREAMING, CleanupAction.DOWNLOADS, CleanupAction.WAVEFORMS, CleanupAction.DIAGNOSTIC_LOGS,
    CleanupAction.TEMPORARY_FILES, CleanupAction.NETWORK,
)

private sealed interface ConfirmedStorageTarget {
    val files: List<String>

    data class FileSet(val target: List<WaveformCleanupTarget>) : ConfirmedStorageTarget {
        override val files get() = target.flatMap { root -> root.files.map { "${root.directory.name}/${it.name}" } }
    }

    data class LiveFiles(val target: List<WaveformCleanupTarget>, val imageRevision: Long? = null) : ConfirmedStorageTarget {
        override val files get() = target.flatMap { root -> root.files.map { "${root.directory.name}/${it.name}" } }
    }

    data class InactiveMedia(val target: InactiveDownloadCleanup.Target) : ConfirmedStorageTarget {
        override val files get() = target.displayedFiles
    }

    data class ActiveStreaming(val target: ActiveStreamingCleanup.Target) : ConfirmedStorageTarget {
        override val files get() = target.displayedFiles
    }

    data class ActiveDownloads(val target: ActiveDownloadCleanup.Target) : ConfirmedStorageTarget {
        override val files get() = target.eligibleEntries.flatMap { entry ->
            listOf(entry.request.id) + entry.spans.map { it.file.relativeTo(target.root).invariantSeparatorsPath }
        }
    }
}

private data class CleanupPlanItem(
    val profile: ProfileStorageUsage?,
    val action: CleanupAction,
    val target: ConfirmedStorageTarget?,
    val blockedReason: Int? = null,
)

private data class StorageCleanupPlan(
    val action: CleanupAction,
    val targets: List<CleanupPlanItem>,
    val scopeLabel: String,
)

@UnstableApi
@Composable
internal fun DataStorageCleanup(
    profiles: List<ProfileStorageUsage>,
    selected: ProfileStorageUsage?,
    onCleanupComplete: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = if (selected == null) profiles else listOf(selected)
    val binder = LocalPlayerServiceBinder.current
    val planningScope = rememberCoroutineScope()
    var planning by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<StorageCleanupPlan?>(null) }
    var incompleteResult by remember { mutableStateOf<String?>(null) }
    SettingsCardSectionDivider()
    CardCategoryLabel(stringResource(if (selected == null) R.string.storage_cleanup_global_title else R.string.storage_cleanup_profile_title))

    fun unavailableReason(profile: ProfileStorageUsage, action: CleanupAction): Int? =
        when {
            action == CleanupAction.IMAGES && isLegacyStorageShared(context, profile.identity.id, ProfileDataItem.IMAGES) ->
                R.string.storage_cleanup_separation_pending
            action == CleanupAction.WAVEFORMS && ProfileDataItem.WAVEFORMS in profile.sharedItems ->
                R.string.storage_cleanup_separation_pending
            action == CleanupAction.DOWNLOADS && ProfileDataItem.DOWNLOADS in profile.sharedItems ->
                R.string.storage_cleanup_separation_pending
            action == CleanupAction.STREAMING && ProfileDataItem.MEDIA_CACHE in profile.sharedItems ->
                R.string.storage_cleanup_separation_pending
            action !in verifiedActions && action != CleanupAction.ALL -> R.string.storage_cleanup_unverified
            else -> null
        }

    suspend fun inspect(profile: ProfileStorageUsage, action: CleanupAction): CleanupPlanItem? {
        var reason = unavailableReason(profile, action)
        val playing = binder?.player?.currentMediaItem?.mediaId
        val streaming = binder?.streamingCleanupCache
        val hasData = withContext(NzikDispatchers.DATA) {
            try {
                when (action) {
                    CleanupAction.DOWNLOADS, CleanupAction.STREAMING -> {
                        val kind = if (action == CleanupAction.DOWNLOADS) InactiveDownloadCleanup.Kind.DOWNLOADS
                            else InactiveDownloadCleanup.Kind.STREAMING
                        val onDisk = InactiveDownloadCleanup.hasData(context, profile.identity, kind)
                        onDisk || (reason == null && binder != null && isProcessStorageProfile(context, profile.identity.id) &&
                            if (action == CleanupAction.DOWNLOADS) ActiveDownloadCleanup.hasRemovableEntries(context, playing)
                            else ActiveStreamingCleanup.hasRemovableData(context, profile.identity, streaming, playing))
                    }
                    else -> captureFiles(context, profile.identity, action, presenceOnly = true)
                        .orEmpty().any { it.files.isNotEmpty() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("DataStorageCleanup").w(error, "Could not inspect a cleanup category")
                reason = R.string.storage_cleanup_unverified
                profile.bytes.getOrDefault(action.category(), 0L) > 0L
            }
        }
        if (!hasData) {
            if (action.category() !in profile.fileCategories) return null
            return CleanupPlanItem(profile, action, null, reason ?: R.string.storage_cleanup_unverified)
        }
        return CleanupPlanItem(profile, action, null, reason)
    }

    suspend fun capture(profile: ProfileStorageUsage, action: CleanupAction): CleanupPlanItem {
        val blocked = unavailableReason(profile, action)
        if (blocked != null) return CleanupPlanItem(profile, action, null, blocked)
        return try {
            val target = withContext(NzikDispatchers.DATA) {
                if (action == CleanupAction.DOWNLOADS || action == CleanupAction.STREAMING) {
                    val kind = if (action == CleanupAction.DOWNLOADS) InactiveDownloadCleanup.Kind.DOWNLOADS
                        else InactiveDownloadCleanup.Kind.STREAMING
                    val active = isProcessStorageProfile(context, profile.identity.id)
                    val live = if (active && binder != null) {
                        try {
                            val current = withContext(NzikDispatchers.UI) { binder.player.currentMediaItem?.mediaId }
                            if (action == CleanupAction.DOWNLOADS) {
                                ActiveDownloadCleanup.capture(context, profile.identity, current)
                                    ?.let { ConfirmedStorageTarget.ActiveDownloads(it) }
                            } else {
                                ActiveStreamingCleanup.capture(context, profile.identity, binder.streamingCleanupCache, current)
                                    ?.let { ConfirmedStorageTarget.ActiveStreaming(it) }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Timber.tag("DataStorageCleanup").w(error, "Live cleanup could not verify the Media3 target")
                            null
                        }
                    } else null
                    if (active) live?.takeIf { it.files.isNotEmpty() }
                    else DatabaseRestoreJournal.withProfileStorageMutation(context) {
                        InactiveDownloadCleanup.capture(context, profile.identity, kind)?.let { ConfirmedStorageTarget.InactiveMedia(it) }
                    }
                } else {
                    DatabaseRestoreJournal.withProfileStorageMutation(context) {
                        val live = action in setOf(CleanupAction.IMAGES, CleanupAction.NETWORK, CleanupAction.DIAGNOSTIC_LOGS) &&
                            isProcessStorageProfile(context, profile.identity.id)
                        if (live && action == CleanupAction.IMAGES) {
                            ImageCacheFactory.captureCleanupState { revision ->
                                captureFiles(context, profile.identity, action, allowActive = true)?.let { files ->
                                    ConfirmedStorageTarget.LiveFiles(files, revision)
                                }
                            }
                        } else {
                            captureFiles(context, profile.identity, action, allowActive = live)?.let { files ->
                                if (live) ConfirmedStorageTarget.LiveFiles(files) else ConfirmedStorageTarget.FileSet(files)
                            }
                        }
                    }
                }
            }
            val executable = target?.takeIf { it.files.isNotEmpty() }
            CleanupPlanItem(
                profile, action, executable,
                if (target == null) {
                    if (action == CleanupAction.STREAMING || action == CleanupAction.DOWNLOADS) R.string.storage_cleanup_active_files
                    else R.string.storage_cleanup_unverified
                } else R.string.storage_cleanup_no_files.takeIf { executable == null },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("DataStorageCleanup").w(error, "Could not confirm the profile cleanup target")
            CleanupPlanItem(profile, action, null, R.string.storage_cleanup_unverified)
        }
    }

    for (action in CleanupAction.entries) {
        val rowTarget = when (action) {
            CleanupAction.IMAGES -> "data.cache.clear-images"
            CleanupAction.STREAMING -> "data.cache.clear-songs"
            CleanupAction.DOWNLOADS -> "data.cache.clear-downloads"
            CleanupAction.WAVEFORMS -> "data.storage.clear-waveforms"
            CleanupAction.ALL -> "data.storage.clear-all"
            else -> "data.storage.cleanup." + action.name.lowercase()
        }
        OtherSettingsEntry(
            title = stringResource(action.label),
            text = stringResource(action.description),
            icon = action.icon,
            modifier = Modifier.testTag("data.storage.cleanup." + action.name.lowercase()).settingsSearchTarget(rowTarget),
            onClick = {
                if (planning || cleanupInProgress.value) return@OtherSettingsEntry
                val profilesAtClick = scope.toList()
                val globalAtClick = selected == null
                val scopeLabel = selected?.label ?: context.getString(R.string.storage_cleanup_all_profiles)
                planning = true
                planningScope.launch {
                    try {
                        val actions = if (action == CleanupAction.ALL) verifiedActions else setOf(action)
                        val confirmed = profilesAtClick.flatMap { profile ->
                            actions.mapNotNull { category ->
                                val candidate = inspect(profile, category) ?: return@mapNotNull null
                                if (candidate.blockedReason != null) candidate else capture(profile, category)
                            }
                        }
                        val installation = if (globalAtClick && CleanupAction.TEMPORARY_FILES in actions) {
                            withContext(NzikDispatchers.DATA) {
                                DatabaseRestoreJournal.withProfileStorageMutation(context) {
                                    runCatching { captureFiles(context, null, CleanupAction.TEMPORARY_FILES) }
                                        .onFailure { Timber.tag("DataStorageCleanup").w(it, "Could not confirm installation cleanup") }
                                        .getOrNull()
                                        ?.let { ConfirmedStorageTarget.FileSet(it) }
                                        ?.takeIf { it.files.isNotEmpty() }
                                        ?.let { CleanupPlanItem(null, CleanupAction.TEMPORARY_FILES, it) }
                                }
                            }
                        } else null
                        val targets = confirmed + listOfNotNull(installation)
                        confirmation = StorageCleanupPlan(action, targets, scopeLabel)
                    } finally {
                        planning = false
                    }
                }
            },
        )
    }

    DialogPresence(target = confirmation) { plan ->
        val blocked = plan.targets.isNotEmpty() && plan.targets.none { it.target != null } &&
            plan.targets.any { it.blockedReason != R.string.storage_cleanup_no_files }
        val description = when {
            blocked -> stringResource(R.string.storage_cleanup_dialog_unavailable)
            plan.targets.none { it.target != null } -> stringResource(R.string.storage_cleanup_no_files)
            plan.action == CleanupAction.ALL -> stringResource(R.string.storage_cleanup_dialog_all)
            else -> stringResource(plan.action.description)
        }
        val containsDownloads = plan.targets.any { it.action == CleanupAction.DOWNLOADS && it.target != null }
        val details = buildString {
            append(context.getString(R.string.storage_cleanup_dialog_scope, plan.scopeLabel, description))
            if (containsDownloads) {
                append(' ')
                append(context.getString(R.string.storage_cleanup_downloads_warning))
            }
        }
        if (blocked) {
            DialogFrame(
                onDismiss = { confirmation = null },
                title = stringResource(plan.action.dialogTitle),
                footer = {
                    ButtonRow(listOf(DialogButton(
                        stringResource(R.string.common_action_close), { confirmation = null }, primary = true,
                    )))
                },
            ) {
                BasicText(
                    details,
                    style = typography().s.copy(color = colorPalette().text),
                )
            }
        } else ConfirmationDialog(
            title = stringResource(plan.action.dialogTitle),
            confirmText = stringResource(
                R.string.common_action_clear,
            ),
            text = details,
            bodyModifier = Modifier.testTag("data.storage.cleanup.confirmation"),
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                if (!cleanupInProgress.compareAndSet(false, true)) return@ConfirmationDialog
                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                    try {
                        val outcomes = withContext(NzikDispatchers.DATA) {
                            plan.targets.filter { it.target != null || it.blockedReason != R.string.storage_cleanup_no_files }.map { item ->
                                try {
                                    when (val target = item.target) {
                                        null -> CleanupResult.SKIPPED
                                        is ConfirmedStorageTarget.FileSet -> {
                                            if (item.action == CleanupAction.TEMPORARY_FILES) {
                                                withRewindCaptures { cleanConfirmedFiles(context, target.target) }
                                            } else cleanConfirmedFiles(context, target.target)
                                        }
                                        is ConfirmedStorageTarget.LiveFiles -> cleanLiveFiles(context, target.target, target.imageRevision)
                                        is ConfirmedStorageTarget.InactiveMedia -> {
                                            if (InactiveDownloadCleanup.execute(context, target.target)) CleanupResult.COMPLETED
                                            else CleanupResult.SKIPPED
                                        }
                                        is ConfirmedStorageTarget.ActiveStreaming -> {
                                            val current = withContext(NzikDispatchers.UI) { binder?.player?.currentMediaItem?.mediaId }
                                            if (ActiveStreamingCleanup.execute(context, target.target, binder?.streamingCleanupCache, current)) {
                                                CleanupResult.COMPLETED
                                            } else CleanupResult.SKIPPED
                                        }
                                        is ConfirmedStorageTarget.ActiveDownloads -> when (
                                            ActiveDownloadCleanup.execute(context, target.target) { binder?.player?.currentMediaItem?.mediaId }
                                        ) {
                                            ActiveDownloadCleanup.Outcome.COMPLETED -> CleanupResult.COMPLETED
                                            ActiveDownloadCleanup.Outcome.SKIPPED -> CleanupResult.SKIPPED
                                            ActiveDownloadCleanup.Outcome.FAILED -> CleanupResult.FAILED
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    Timber.tag("DataStorageCleanup").w(error, "Could not clean the confirmed profile storage")
                                    CleanupResult.FAILED
                                }
                            }
                        }
                        if (outcomes.any { it == CleanupResult.SKIPPED || it == CleanupResult.FAILED }) {
                            incompleteResult = context.getString(
                                R.string.storage_cleanup_result_counts,
                                outcomes.count { it == CleanupResult.COMPLETED },
                                outcomes.count { it == CleanupResult.SKIPPED },
                                outcomes.count { it == CleanupResult.FAILED },
                            )
                        }
                        WaveformExtractor.refreshSignal.tryEmit(System.currentTimeMillis())
                    } finally {
                        cleanupInProgress.value = false
                        onCleanupComplete()
                    }
                }
            },
        )
    }
    DialogPresence(target = incompleteResult) { result ->
        DialogFrame(
            onDismiss = { incompleteResult = null },
            title = stringResource(R.string.storage_breakdown_title),
            footer = {
                ButtonRow(listOf(DialogButton(
                    stringResource(R.string.common_action_close), { incompleteResult = null }, primary = true,
                )))
            },
        ) {
            BasicText(result, style = typography().s.copy(color = colorPalette().text))
        }
    }
}
