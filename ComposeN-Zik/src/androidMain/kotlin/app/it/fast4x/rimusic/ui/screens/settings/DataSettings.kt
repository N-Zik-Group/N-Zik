package app.it.fast4x.rimusic.ui.screens.settings

import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.database.*
import android.annotation.SuppressLint
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.CacheType
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.n_zik.android.download.utils.MyDownloadHelper
import app.it.fast4x.rimusic.ui.components.themed.CacheSpaceIndicator
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.InputNumericDialog
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.pauseListenHistoryKey
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.n_zik.android.components.dialog.export.ExportDatabaseDialog
import app.n_zik.android.components.dialog.export.ExportSettingsDialog
import app.n_zik.android.components.import.ImportDatabase
import app.n_zik.android.components.import.ImportMigration
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.components.dialog.export.ExportBackupDialog
import app.n_zik.android.components.dialog.backup.ImportBackupDialog
import app.kreate.android.me.knighthat.utils.Toaster
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.n_zik.android.core.backup.ui.AutoBackupSettingsBlock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File


@Composable
fun DefaultDataSettings() {

    var coilDiskCacheMaxSize by rememberPreference(
        coilDiskCacheMaxSizeKey,
        CoilDiskCacheMaxSize.`128MB`
    )
    coilDiskCacheMaxSize = CoilDiskCacheMaxSize.`128MB`

    var exoPlayerDiskCacheMaxSize by rememberPreference(
        exoPlayerDiskCacheMaxSizeKey,
        ExoPlayerDiskCacheMaxSize.`2GB`
    )
    exoPlayerDiskCacheMaxSize = ExoPlayerDiskCacheMaxSize.`2GB`

    var exoPlayerDiskDownloadCacheMaxSize by rememberPreference(
        exoPlayerDiskDownloadCacheMaxSizeKey,
        ExoPlayerDiskDownloadCacheMaxSize.`2GB`
    )
    exoPlayerDiskDownloadCacheMaxSize = ExoPlayerDiskDownloadCacheMaxSize.`2GB`

    var exoPlayerCacheLocation by rememberPreference(
        exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System
    )
    exoPlayerCacheLocation = ExoPlayerCacheLocation.System

    var exoPlayerCustomCache by rememberPreference(
        exoPlayerCustomCacheKey,32
    )
    exoPlayerCustomCache = 32

    var coilCustomDiskCache by rememberPreference(
        coilCustomDiskCacheKey,32
    )
    coilCustomDiskCache = 32

    var pauseSearchHistory by rememberPreference(pauseSearchHistoryKey, false)
    pauseSearchHistory = false

    var pauseListenHistory by rememberPreference(pauseListenHistoryKey, false)
    pauseListenHistory = false
}

@SuppressLint("SuspiciousIndentation")
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun DataSettings() {
    val search = Search()


    val context = LocalContext.current
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
    
    var pauseSearchHistory by rememberPreference(pauseSearchHistoryKey, false)
    var pauseListenHistory by rememberPreference(pauseListenHistoryKey, false)

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
                binder?.cache?.let { cache ->
                    val keys = cache.keys
                    keys.forEach { song ->
                        cache.removeResource(song)
                    }
                }
                File(context.filesDir, "waveforms").deleteRecursively()
                WaveformExtractor.refreshSignal.tryEmit(System.currentTimeMillis())
                cleanCacheOfflineSongs = false
                cacheCleanedCounter++
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
                binder?.downloadCache?.let { downloadCache ->
                    val keys = downloadCache.keys
                    keys.forEach { songId ->
                        downloadCache.removeResource(songId)

                        CoroutineScope(Dispatchers.IO).launch {
                            Database.songTable
                                .findById(songId)
                                .first()
                                ?.asMediaItem
                                ?.let {
                                    MyDownloadHelper.removeDownload(context, it)
                                }
                        }
                    }
                }
                File(context.filesDir, "waveforms").deleteRecursively()
                cleanDownloadCache = false
                cacheCleanedCounter++
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
                // Use a new safe method to clear the cache
                ImageCacheFactory.clearImageCache()
                cleanCacheImages = false
                cacheCleanedCounter++
            }
        )
    }

    var restartService by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {

        HeaderWithIcon(
            title = stringResource(R.string.tab_data),
                       iconId = R.drawable.server,
                       enabled = false,
                       showIcon = true,
                       modifier = Modifier,
                       onClick = {}
        )

        SettingsDescription(
            text = stringResource(R.string.data_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        ) 
        /* Removed Spacer */

        search.ToolBarButton()
        search.SearchBar( this )

        // Cache Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = tween(600),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
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
                            title = stringResource(R.string.image_cache_max_size),
                            text = when (coilDiskCacheMaxSize) {
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
                            title = stringResource(R.string.song_cache_max_size),
                            text = when (exoPlayerDiskCacheMaxSize) {
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
                            title = stringResource(R.string.song_download_max_size),
                            text = exoPlayerDiskDownloadCacheMaxSize.text,
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
                            title = stringResource(R.string.set_cache_location),
                            text = exoPlayerCacheLocation.text,
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

        /* Removed Spacer */

        // Backup Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                animationSpec = tween(800),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.title_backup_and_restore),
                icon = R.drawable.server,
                content = {
                    ExportBackupDialog.Render()
                    ImportBackupDialog.Render()

                    if (search.inputValue.isBlank() || stringResource(R.string.export_backup).contains(search.inputValue, true) || stringResource(R.string.export_backup_description).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.export_backup),
                            text = stringResource(R.string.export_backup_description),
                            icon = R.drawable.export_outline,
                            onClick = { ExportBackupDialog.showDialog() }
                        )
                    }
                    ImportantSettingsDescription(text = stringResource(
                        R.string.personal_preference
                    ))

                    if (search.inputValue.isBlank() || stringResource(R.string.import_backup).contains(search.inputValue, true) || stringResource(R.string.import_backup_description).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.import_backup),
                            text = stringResource(R.string.import_backup_description),
                            icon = R.drawable.import_outline,
                            onClick = { ImportBackupDialog.showDialog() }
                        )
                    }
                    ImportantSettingsDescription(text = stringResource(
                        R.string.existing_data_will_be_overwritten,
                        context.applicationInfo.nonLocalizedLabel
                    ))

                    val importMigration = ImportMigration(context, binder)

                    if (search.inputValue.isBlank() || stringResource(R.string.title_import_settings_migration).contains(search.inputValue, true) || stringResource(R.string.description_import_settings_migration).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.title_import_settings_migration),
                            text = stringResource(R.string.description_import_settings_migration),
                            icon = R.drawable.data_migration,
                            onClick = importMigration::onShortClick
                        )
                    }
                }
            )
        }

        // Auto Backup Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                animationSpec = tween(800),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.auto_backup_title),
                icon = R.drawable.history,
                content = {
                    AutoBackupSettingsBlock()
                }
            )
        }

        /* Removed Spacer */

        // History Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                animationSpec = tween(1000),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.history),
                icon = R.drawable.history,
                content = {
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.pause_search_history),
                        text = stringResource(R.string.neither_save_new_searched_query),
                        isChecked = pauseSearchHistory,
                        onCheckedChange = {
                            pauseSearchHistory = it
                            restartService = true
                        },
                        icon = R.drawable.pause
                    )

                    val queriesCount by remember {
                        Database.searchTable
                            .findAllContain("")
                            .map { it.size }
                    }.collectAsState(0, Dispatchers.IO)
                    
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.player_pause_listen_history),
                        text = stringResource(R.string.player_pause_listen_history_info),
                        isChecked = pauseListenHistory,
                        onCheckedChange = {
                            pauseListenHistory = it
                            restartService = true
                        },
                        icon = R.drawable.pause
                    )
                    
                    val eventsCount by remember {
                        Database.eventTable.countAll()
                    }.collectAsState(0L, Dispatchers.IO)

                    if (search.inputValue.isBlank() || stringResource(R.string.clear_search_history).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.clear_search_history),
                            text = if (queriesCount > 0) {
                                "${stringResource(R.string.delete)} " + queriesCount + stringResource(R.string.search_queries)
                            } else {
                                stringResource(R.string.history_is_empty)
                            },
                            icon = R.drawable.trash,
                            onClick = {
                                Database.asyncTransaction {
                                    searchTable.deleteAll()
                                }
                                Toaster.done()
                            }
                        )
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.clear_listen_history).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.clear_listen_history),
                            text = if (eventsCount > 0) {
                                stringResource(R.string.delete_playback_events, eventsCount.toString())
                            } else {
                                stringResource(R.string.history_is_empty)
                            },
                            icon = R.drawable.trash,
                            onClick = {
                                Database.asyncTransaction {
                                    eventTable.deleteAll()
                                }
                                File(context.filesDir, "waveforms").deleteRecursively()
                                WaveformExtractor.refreshSignal.tryEmit(System.currentTimeMillis())
                                Toaster.done()
                            }
                        )
                    }
                    
                    RestartPlayerService(restartService, onRestart = { restartService = false })
                }
            )
        }

        
        val searchCtx_Reset = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
        AnimatedVisibility(
            visible = searchCtx_Reset,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f)
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_reset),
                icon = R.drawable.refresh,
                content = {
                    var resetToDefault by remember { mutableStateOf(false) }
                    
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true) || stringResource(R.string.settings_reset).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.settings_reset),
                            text = stringResource(R.string.settings_restore_default_settings),
                            icon = R.drawable.refresh,
                            onClick = { 
                                resetToDefault = true
                                Toaster.done()
                            }
                        )
                    }

                    if (resetToDefault) {
                        DefaultDataSettings()
                        LaunchedEffect(Unit) {
                            resetToDefault = false
                        }
                    }
                }
            )
        }
        
        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}





