package app.n_zik.android.components.settings.data

import app.n_zik.android.components.settings.common.settingsSearchTarget
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.enums.CacheType
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.it.fast4x.rimusic.ui.components.themed.CacheSpaceIndicator
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.InputNumericDialog
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.CacheSettingsEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.clearImageCacheOffMain
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.waveformsDir
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Owns cache preferences, selectors and confirmed cache cleanup actions. */
@UnstableApi
@Composable
internal fun DataCacheCard(search: Search, restartState: MutableState<Boolean>) {
    val context = LocalContext.current
    var restartService by restartState
    val binder = LocalPlayerServiceBinder.current

    var coilDiskCacheMaxSize by rememberPreference(
        coilDiskCacheMaxSizeKey,
        CoilDiskCacheMaxSize.`128MB`
    )
    var exoPlayerDiskCacheMaxSize by rememberPreference(
        exoPlayerDiskCacheMaxSizeKey,
        ExoPlayerDiskCacheMaxSize.`2GB`
    )

    var exoPlayerDiskDownloadCacheMaxSize by rememberPreference(
        exoPlayerDiskDownloadCacheMaxSizeKey,
        ExoPlayerDiskDownloadCacheMaxSize.`2GB`
    )

    var exoPlayerCacheLocation by rememberPreference(
        exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System
    )

    var showExoPlayerCustomCacheDialog by remember { mutableStateOf(false) }
    var exoPlayerCustomCache by rememberPreference(
        exoPlayerCustomCacheKey,32
    )

    var showCoilCustomDiskCacheDialog by remember { mutableStateOf(false) }
    var coilCustomDiskCache by rememberPreference(
        coilCustomDiskCacheKey,32
    )

    var cleanCacheOfflineSongs by remember {
        mutableStateOf(false)
    }

    var cleanDownloadCache by remember {
        mutableStateOf(false)
    }
    var cleanCacheImages by remember {
        mutableStateOf(false)
    }

    var cacheCleanedCounter by remember {
        mutableIntStateOf(0)
    }

    if (cleanCacheOfflineSongs) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_delete_cache),
            onDismiss = {
                cleanCacheOfflineSongs = false
            },
            onConfirm = {
                cleanCacheOfflineSongs = false
                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                    withContext(NzikDispatchers.DATA) {
                        val cache = binder?.cache
                        cache?.keys?.forEach { song ->
                            cache.removeResource(song)
                        }
                        waveformsDir(context, getActiveProfile(context)).deleteRecursively()
                    }
                    WaveformExtractor.refreshSignal.tryEmit(System.currentTimeMillis())
                    cacheCleanedCounter++
                }
            }
        )
    }

    if (cleanDownloadCache) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_delete_cache),
            onDismiss = {
                cleanDownloadCache = false
            },
            onConfirm = {
                cleanDownloadCache = false
                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                    withContext(NzikDispatchers.DATA) {
                        val downloadCache = binder?.downloadCache
                        downloadCache?.keys?.forEach { songId ->
                            downloadCache.removeResource(songId)

                            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                Database.songTable
                                    .findById(songId)
                                    .first()
                                    ?.asMediaItem
                                    ?.let {
                                        MyDownloadHelper.removeDownload(context, it)
                                    }
                            }
                        }
                        waveformsDir(context, getActiveProfile(context)).deleteRecursively()
                    }
                    cacheCleanedCounter++
                }
            }
        )
    }

    if (cleanCacheImages) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_delete_cache),
            onDismiss = {
                cleanCacheImages = false
            },
            onConfirm = {
                cleanCacheImages = false
                // Issue #606 M4: the purge deletes the disk cache folder, so it runs off Main;
                // the counter is bumped once it is done.
                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                    clearImageCacheOffMain()
                    cacheCleanedCounter++
                }
            }
        )
    }

    SettingsSectionCard(
        modifier = Modifier.testTag("data.card.cache"),
        title = stringResource(R.string.cache),
        icon = R.drawable.server,
        description = stringResource(R.string.cache_cleared),
        content = {
            ImageCacheFactory.getDiskCache()?.let { diskCache ->
                val diskCacheSize = remember(diskCache.size, cleanCacheImages) {
                    diskCache.size
                }

                var showImageCacheDialog by remember { mutableStateOf(false) }

                key(cacheCleanedCounter) {
                    CacheSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("data.cache.image_cache_max_size"),
                        title = stringResource(R.string.image_cache_max_size),
                        text = stringResource(R.string.settings_desc_data_cache_image_cache_max_size),
                        selectedValueText = when (coilDiskCacheMaxSize) {
                            CoilDiskCacheMaxSize.Custom -> "${stringResource(R.string.custom)}: ${coilCustomDiskCache}MB"
                            else -> coilDiskCacheMaxSize.text
                        },
                        icon = R.drawable.image,
                        onClick = { showImageCacheDialog = true },
                        onTrashClick = { cleanCacheImages = true }
                    )

                    if (showImageCacheDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.image_cache_max_size),
                            selectedValue = coilDiskCacheMaxSize,
                            values = CoilDiskCacheMaxSize.values().toList(),
                            onValueSelected = {
                                coilDiskCacheMaxSize = it
                                if (coilDiskCacheMaxSize == CoilDiskCacheMaxSize.Custom)
                                    showCoilCustomDiskCacheDialog = true
                                restartService = true
                            },
                            valueText = { it.text },
                            onDismiss = { showImageCacheDialog = false }
                        )
                    }

                    RestartPlayerService(restartService, onRestart = { restartService = false })

                    if (showCoilCustomDiskCacheDialog) {
                        InputNumericDialog(
                            title = stringResource(R.string.set_custom_cache),
                            placeholder = stringResource(R.string.enter_value_in_mb),
                            value = coilCustomDiskCache.toString(),
                            valueMin = "32",
                            valueMax = "10000",
                            onDismiss = { showCoilCustomDiskCacheDialog = false },
                            setValue = {
                                coilCustomDiskCache = it.toInt()
                                showCoilCustomDiskCacheDialog = false
                                restartService = true
                            }
                        )
                    }

                    CacheSpaceIndicator(cacheType = CacheType.Images, horizontalPadding = 20.dp, showCacheInfo = false)

                    SettingsDescription(text = "${Formatter.formatShortFileSize(context, diskCacheSize)} ${stringResource(R.string.used)} (${if (coilDiskCacheMaxSize.bytes > 0) "${diskCacheSize * 100 / coilDiskCacheMaxSize.bytes}%" else stringResource(R.string.unlimited)})")

                }
            }

            binder?.cache?.let { cache ->
                val diskCacheSize = remember(cache.cacheSpace, cleanCacheOfflineSongs) {
                    cache.cacheSpace
                }

                var showSongCacheDialog by remember { mutableStateOf(false) }

                key(cacheCleanedCounter) {
                    CacheSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("data.cache.song_cache_max_size"),
                        title = stringResource(R.string.song_cache_max_size),
                        text = stringResource(R.string.settings_desc_data_cache_song_cache_max_size),
                        selectedValueText = when (exoPlayerDiskCacheMaxSize) {
                            ExoPlayerDiskCacheMaxSize.Custom -> "${stringResource(R.string.custom)}: ${exoPlayerCustomCache}MB"
                            ExoPlayerDiskCacheMaxSize.Disabled -> stringResource(R.string.turn_off)
                            else -> exoPlayerDiskCacheMaxSize.text
                        },
                        icon = R.drawable.music_file,
                        onClick = { showSongCacheDialog = true },
                        onTrashClick = { cleanCacheOfflineSongs = true }
                    )

                    if (showSongCacheDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.song_cache_max_size),
                            selectedValue = exoPlayerDiskCacheMaxSize,
                            values = ExoPlayerDiskCacheMaxSize.values().toList(),
                            onValueSelected = {
                                exoPlayerDiskCacheMaxSize = it
                                if (exoPlayerDiskCacheMaxSize == ExoPlayerDiskCacheMaxSize.Custom)
                                    showExoPlayerCustomCacheDialog = true
                                restartService = true
                            },
                            valueText = { it.text },
                            onDismiss = { showSongCacheDialog = false }
                        )
                    }

                    RestartPlayerService(restartService, onRestart = { restartService = false })

                    if (showExoPlayerCustomCacheDialog) {
                        InputNumericDialog(
                            title = stringResource(R.string.set_custom_cache),
                            placeholder = stringResource(R.string.enter_value_in_mb),
                            value = exoPlayerCustomCache.toString(),
                            valueMin = "32",
                            valueMax = "10000",
                            onDismiss = { showExoPlayerCustomCacheDialog = false },
                            setValue = {
                                exoPlayerCustomCache = it.toInt()
                                showExoPlayerCustomCacheDialog = false
                                restartService = true
                            }
                        )
                    }

                    CacheSpaceIndicator(cacheType = CacheType.CachedSongs, horizontalPadding = 20.dp, showCacheInfo = false)

                    SettingsDescription(text = "${Formatter.formatShortFileSize(context, diskCacheSize)} ${stringResource(R.string.used)} (${if (exoPlayerDiskCacheMaxSize.bytes > 0) "${diskCacheSize * 100 / exoPlayerDiskCacheMaxSize.bytes}%" else stringResource(R.string.unlimited)})")
                }
            }

            binder?.downloadCache?.let { downloadCache ->
                val diskDownloadCacheSize = remember(downloadCache.cacheSpace, cleanDownloadCache) {
                    downloadCache.cacheSpace
                }

                var showDownloadCacheDialog by remember { mutableStateOf(false) }

                key(cacheCleanedCounter) {
                    CacheSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("data.cache.song_download_max_size"),
                        title = stringResource(R.string.song_download_max_size),
                        text = stringResource(R.string.settings_desc_data_cache_song_download_max_size),
                        selectedValueText = exoPlayerDiskDownloadCacheMaxSize.text,
                        icon = R.drawable.download,
                        onClick = { showDownloadCacheDialog = true },
                        onTrashClick = { cleanDownloadCache = true }
                    )

                    RestartPlayerService(restartService, onRestart = { restartService = false })

                    if (showDownloadCacheDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.song_download_max_size),
                            selectedValue = exoPlayerDiskDownloadCacheMaxSize,
                            values = ExoPlayerDiskDownloadCacheMaxSize.values().toList(),
                            onValueSelected = {
                                exoPlayerDiskDownloadCacheMaxSize = it
                                restartService = true
                            },
                            valueText = { it.text },
                            onDismiss = { showDownloadCacheDialog = false }
                        )
                    }

                    CacheSpaceIndicator(cacheType = CacheType.DownloadedSongs, horizontalPadding = 20.dp, showCacheInfo = false)

                    SettingsDescription(text = "${Formatter.formatShortFileSize(context, diskDownloadCacheSize)} ${stringResource(R.string.used)} (${if (exoPlayerDiskDownloadCacheMaxSize.bytes > 0) "${diskDownloadCacheSize * 100 / exoPlayerDiskDownloadCacheMaxSize.bytes}%" else stringResource(R.string.unlimited)})")
                }
            }

            var showCacheLocationDialog by remember { mutableStateOf(false) }
            if (search.inputValue.isBlank() || stringResource(R.string.set_cache_location).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.cache.set_cache_location"),
                    title = stringResource(R.string.set_cache_location),
                    text = stringResource(R.string.settings_desc_data_cache_set_cache_location),
                    selectedValueText = exoPlayerCacheLocation.text,
                    icon = R.drawable.folder,
                    onClick = { showCacheLocationDialog = true }
                )
            }

            SettingsDescription(stringResource(R.string.info_private_cache_location_can_t_cleaned))

            if (showCacheLocationDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.set_cache_location),
                    selectedValue = exoPlayerCacheLocation,
                    values = ExoPlayerCacheLocation.values().toList(),
                    onValueSelected = {
                        exoPlayerCacheLocation = it
                        restartService = true
                    },
                    valueText = { it.text },
                    onDismiss = { showCacheLocationDialog = false }
                )
            }

            RestartPlayerService(restartService, onRestart = { restartService = false })

        }
    )
}
