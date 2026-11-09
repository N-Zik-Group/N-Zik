package app.n_zik.android.components.settings.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.isProfileIdSafe
import app.it.fast4x.rimusic.utils.parseProfileIds
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.CardCategoryLabel
import app.n_zik.android.components.settings.common.SettingsCardSectionDivider
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.ui.InlineLoader
import app.n_zik.android.core.profiles.ProfileDataDirNames
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import java.io.IOException
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
    OTHER_NZIK(R.string.storage_breakdown_other_nzik_data, R.string.storage_info_other_nzik, Color(0xFFAA7B57)),
    OTHER_USED(R.string.storage_breakdown_other_used_space, R.string.storage_info_other_used, Color(0xFF488BDE)),
    FREE(R.string.storage_breakdown_free_space, R.string.storage_info_free, Color(0xFF90959B)),
}

internal data class StorageBreakdown(
    val totalBytes: Long,
    val bytes: Map<StorageCategory, Long>,
) {
    val nzikBytes: Long
        get() = totalBytes - bytes.getValue(StorageCategory.OTHER_USED) - bytes.getValue(StorageCategory.FREE)
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
    if (directory) return StorageCategory.OTHER_NZIK
    return when (category) {
        StorageCategory.IMAGES -> when (name) {
            "journal", "journal.tmp", "journal.bkp" -> StorageCategory.OTHER_NZIK
            else -> category
        }
        StorageCategory.STREAMING, StorageCategory.DOWNLOADS ->
            if (name.endsWith(".uid")) StorageCategory.OTHER_NZIK else category
        else -> category
    }
}

internal suspend fun measureStorage(context: Context): StorageBreakdown? = withContext(NzikDispatchers.DATA) {
    try {
        val root = File(context.applicationInfo.dataDir).canonicalFile
        val rootStat = Os.lstat(root.path)
        val files = root.resolve("files")
        val cache = root.resolve("cache")
        val temp = System.getProperty("java.io.tmpdir")?.let { File(it).canonicalFile }
        val profiles = files.resolve(PROFILE_NAMES_FILE_NAME)
        val profileIds = if (profiles.canonicalFile == profiles && profiles.isFile) {
            parseProfileIds(profiles.readText())
        } else emptyList()
        val directories = StorageDirectories(profileIds)
        val volumeBefore = StatFs(root.path)
        val bytes = mutableMapOf<StorageCategory, Long>()
        val linkedInodes = mutableSetOf<Long>()
        val samplingContext = currentCoroutineContext()

        fun scan(file: File, inheritedCategory: StorageCategory) {
            samplingContext.ensureActive()
            val stat = try {
                Os.lstat(file.path)
            } catch (error: ErrnoException) {
                // Cache entries can be evicted after listing their parent.
                if (file != root && error.errno == OsConstants.ENOENT) return
                throw error
            }
            if (stat.st_dev != rootStat.st_dev) return
            val directory = OsConstants.S_ISDIR(stat.st_mode)
            if (!directory && !OsConstants.S_ISREG(stat.st_mode)) return
            if (!directory && stat.st_nlink > 1 && !linkedInodes.add(stat.st_ino)) return

            val category = if (directory && inheritedCategory == StorageCategory.OTHER_NZIK) {
                directories.category(file.name, file.parentFile == files, file.parentFile == cache, file.parentFile == temp)
            } else inheritedCategory
            // Use allocated bytes, in the same units as the volume usage, including sparse files.
            val allocated = Math.multiplyExact(stat.st_blocks, 512L)
            val allocationCategory = storageAllocationCategory(category, file.name, directory)
            bytes[allocationCategory] = Math.addExact(bytes[allocationCategory] ?: 0L, allocated)
            if (directory) {
                val children = file.listFiles() ?: throw IOException("Unable to list app storage")
                children.forEach { child -> scan(child, category) }
            }
        }

        scan(root, StorageCategory.OTHER_NZIK)
        val volumeAfter = StatFs(root.path)
        if (volumeBefore.totalBytes != volumeAfter.totalBytes) return@withContext null
        // Check both volume samples. A purge or concurrent write may invalidate the budget.
        if (partitionStorage(volumeBefore.totalBytes, volumeBefore.availableBytes, bytes) == null) {
            return@withContext null
        }
        partitionStorage(volumeAfter.totalBytes, volumeAfter.availableBytes, bytes)
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

@UnstableApi
@Composable
internal fun DataStorageCard() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val interactive = rememberScreenInteractive(appContext)
    var breakdown by remember(appContext) { mutableStateOf<StorageBreakdown?>(null) }
    var cleanupRevision by remember { mutableLongStateOf(0L) }
    var showStorageInfo by remember { mutableStateOf(false) }
    LaunchedEffect(appContext, lifecycle, interactive, cleanupRevision) {
        if (interactive) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            storageSamples { measureStorage(appContext) }.collect { breakdown = it }
        }
    }

    SettingsSectionCard(
        modifier = Modifier.testTag("data.card.storage"),
        title = stringResource(R.string.storage_breakdown_title),
        icon = R.drawable.server,
        headerAction = {
            val label = stringResource(R.string.storage_breakdown_info_title)
            // Center on the row chevrons: 12 dp inset plus half of a 16 dp icon.
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                HeaderIconButton(
                    onClick = { showStorageInfo = true },
                    icon = R.drawable.information,
                    color = colorPalette().accent,
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        },
        content = {
            CardCategoryLabel(stringResource(R.string.storage_breakdown_subtitle))
            AnimatedContent(
                targetState = breakdown != null,
                modifier = Modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "storageBreakdown",
            ) { ready ->
                val snapshot = if (ready) breakdown else null
                if (snapshot == null) {
                    val loadingDescription = stringResource(R.string.storage_breakdown_waiting)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        InlineLoader(Modifier.semantics { contentDescription = loadingDescription })
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Canvas(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                            var cumulativeBytes = 0L
                            var start = 0f
                            for ((category, bytes) in snapshot.bytes) {
                                cumulativeBytes += bytes
                                val end = (size.width * (cumulativeBytes.toDouble() / snapshot.totalBytes)).toFloat()
                                if (end > start) {
                                    drawRect(category.color, Offset(start, 0f), Size(end - start, size.height))
                                }
                                start = end
                            }
                        }
                        Column(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for ((category, bytes) in snapshot.bytes) {
                                val startsVolumeGroup = category == StorageCategory.OTHER_USED || category == StorageCategory.FREE
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(top = if (startsVolumeGroup) 6.dp else 0.dp)
                                        .semantics(mergeDescendants = true) {},
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(10.dp).background(category.color, CircleShape))
                                    BasicText(
                                        text = stringResource(category.label),
                                        modifier = Modifier.weight(1.2f),
                                        style = typography().xxs.color(colorPalette().text),
                                    )
                                    BasicText(
                                        text = Formatter.formatShortFileSize(context, bytes),
                                        modifier = Modifier.weight(1f),
                                        style = typography().xxs.color(colorPalette().text).copy(textAlign = TextAlign.End),
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                .semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val style = typography().xs.semiBold.copy(color = colorPalette().text)
                            BasicText(
                                text = stringResource(R.string.storage_breakdown_nzik_total),
                                modifier = Modifier.weight(1.2f),
                                style = style,
                            )
                            BasicText(
                                text = Formatter.formatShortFileSize(context, snapshot.nzikBytes),
                                modifier = Modifier.weight(1f),
                                style = style.copy(textAlign = TextAlign.End),
                            )
                        }
                    }
                }
            }
            SettingsCardSectionDivider()
            DataStorageCleanup(onCleanupComplete = { cleanupRevision++ })
        },
    )
    DialogPresence(visible = showStorageInfo) {
        DataStorageInfoDialog(onDismiss = { showStorageInfo = false })
    }
}
