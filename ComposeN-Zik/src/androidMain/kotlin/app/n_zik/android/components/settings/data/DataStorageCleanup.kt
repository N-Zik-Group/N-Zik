package app.n_zik.android.components.settings.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadService
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.settings.common.CardCategoryLabel
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.ui.screens.rewind.cleanupExpiredRewindCaptures
import app.n_zik.android.core.coil.clearImageCacheOffMain
import app.n_zik.android.core.profiles.waveformsDir
import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.debug.clearDiagnosticLogs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

private val cleanupInProgress = MutableStateFlow(false)

private enum class CleanupAction(
    val label: Int,
    val icon: Int,
    val description: Int,
    val dialogTitle: Int,
    val button: Int = R.string.common_action_clear,
) {
    IMAGES(
        StorageCategory.IMAGES.label,
        R.drawable.image,
        R.string.storage_cleanup_images_description,
        R.string.settings_cache_clear_images_dialog_title,
    ),
    STREAMING(
        StorageCategory.STREAMING.label,
        R.drawable.music_file,
        R.string.storage_cleanup_streaming_description,
        R.string.cache_clear_song_dialog_title,
    ),
    DOWNLOADS(
        StorageCategory.DOWNLOADS.label,
        R.drawable.download,
        R.string.storage_cleanup_downloads_description,
        R.string.downloads_delete_dialog_title,
        R.string.common_action_delete,
    ),
    WAVEFORMS(
        StorageCategory.WAVEFORMS.label,
        R.drawable.equalizer,
        R.string.storage_cleanup_waveforms_description,
        R.string.storage_cleanup_waveforms_dialog_title,
    ),
    DIAGNOSTIC_LOGS(
        R.string.storage_cleanup_diagnostic_logs,
        R.drawable.trash,
        R.string.storage_cleanup_diagnostic_logs_description,
        R.string.storage_cleanup_diagnostic_logs,
    ),
    TEMPORARY_FILES(
        R.string.storage_cleanup_temporary_files,
        R.drawable.trash,
        R.string.storage_cleanup_temporary_files_description,
        R.string.storage_cleanup_temporary_files,
        R.string.common_action_delete,
    ),
    ALL(
        R.string.storage_cleanup_all,
        R.drawable.trash,
        R.string.storage_cleanup_all_description,
        R.string.storage_cleanup_all,
    ),
}

@UnstableApi
@Composable
internal fun DataStorageCleanup(onCleanupComplete: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val binder = LocalPlayerServiceBinder.current
    var confirmation by remember(binder) { mutableStateOf<CleanupAction?>(null) }
    val running by cleanupInProgress.collectAsStateWithLifecycle()
    CardCategoryLabel(stringResource(R.string.storage_cleanup_title))
    for (action in CleanupAction.entries) {
        val enabled = !running && when (action) {
            CleanupAction.STREAMING, CleanupAction.DOWNLOADS, CleanupAction.ALL -> binder != null
            else -> true
        }
        OtherSettingsEntry(
            title = stringResource(action.label),
            text = stringResource(action.description),
            icon = action.icon,
            modifier = Modifier.testTag("data.storage.cleanup." + action.name.lowercase()),
            isEnabled = enabled,
            onClick = { confirmation = action },
        )
    }

    fun clearStreamingCache() {
        val cache = requireNotNull(binder).cache
        cache.keys.forEach(cache::removeResource)
    }

    fun clearWaveforms() {
        val dir = waveformsDir(context, getActiveProfile(context))
        val expected = context.filesDir.canonicalFile.resolve(dir.name)
        if (dir.exists()) {
            if (dir.canonicalFile != expected) throw IOException("Unexpected waveform directory")
            val files = dir.listFiles() ?: throw IOException("Cannot list waveform directory")
            for (file in files) {
                if (file.extension == "json" && file.isFile && !file.delete() && file.exists()) {
                    throw IOException("Cannot delete waveform file")
                }
            }
        }
        WaveformExtractor.refreshSignal.tryEmit(System.currentTimeMillis())
    }

    suspend fun clean(action: CleanupAction) {
        when (action) {
            CleanupAction.IMAGES -> clearImageCacheOffMain()
            CleanupAction.STREAMING -> clearStreamingCache()
            CleanupAction.DOWNLOADS -> DownloadService.sendRemoveAllDownloads(
                context, MyDownloadService::class.java, false,
            )
            CleanupAction.WAVEFORMS -> clearWaveforms()
            CleanupAction.DIAGNOSTIC_LOGS -> clearDiagnosticLogs(context.filesDir)
            CleanupAction.TEMPORARY_FILES -> {
                PickedImageEditor.cleanupOrphans(context)
                cleanupExpiredRewindCaptures(context.cacheDir)
            }
            CleanupAction.ALL -> {
                var failure: Exception? = null
                for (target in CleanupAction.entries) {
                    if (target == CleanupAction.ALL) continue
                    try {
                        clean(target)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        val first = failure
                        if (first == null) failure = error else first.addSuppressed(error)
                    }
                }
                failure?.let { throw it }
            }
        }
    }

    DialogPresence(target = confirmation) { action ->
        ConfirmationDialog(
            title = stringResource(action.dialogTitle),
            confirmText = stringResource(action.button),
            text = stringResource(action.description),
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                if (!cleanupInProgress.compareAndSet(false, true)) return@ConfirmationDialog
                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                    try {
                        withContext(NzikDispatchers.DATA) { clean(action) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.tag("DataStorageCleanup").w(error, "Storage cleanup failed")
                        Toaster.e(R.string.storage_cleanup_failed)
                    } finally {
                        cleanupInProgress.value = false
                        onCleanupComplete()
                    }
                }
            },
        )
    }
}
