package app.n_zik.android.components.settings.data

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.settings.common.RestartPlayerService
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.enums.CoilDiskCacheMaxSize
import app.n_zik.android.enums.ExoPlayerCacheLocation
import app.n_zik.android.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.android.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.n_zik.android.utils.coilDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerCacheLocationKey
import app.n_zik.android.utils.exoPlayerCustomCacheKey
import app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.InputNumericDialog
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.image.ORIGINAL_IMAGE_QUALITY_KEY
import app.n_zik.android.components.image.readOriginalImageQuality
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.coil.PROFILE_IMAGE_CACHE_CUSTOM_MB
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.imageCacheCustomMb
import app.n_zik.android.core.profiles.ProfileCacheLocationMigration
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.profileDownloadSpanBytes
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import android.content.SharedPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns cache limits and location preferences. */
@UnstableApi
@Composable
internal fun DataCacheCard(
    search: Search,
    restartState: MutableState<Boolean>,
    target: ProfileStorageIdentity,
) {
    SettingsSectionCard(
        title = stringResource(R.string.storage_cache_settings_title),
        icon = R.drawable.folder,
        headerModifier = Modifier.settingsSearchTarget("data.cache.section"),
        content = { DataCacheSettings(search, restartState, target) },
    )
}

@UnstableApi
@Composable
private fun DataCacheSettings(
    search: Search,
    restartState: MutableState<Boolean>,
    target: ProfileStorageIdentity,
) {
    var restartService by restartState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store by produceState<SharedPreferences?>(null, target) {
        value = withContext(NzikDispatchers.DATA) { profileDataPrefs(context, target.id) }
    }
    val preferences = store ?: return
    val isActive = getActiveProfile(context) == target.id
    var pendingLocation by remember(target, preferences) {
        mutableStateOf(ProfileCacheLocationMigration.requested(context, target))
    }

    var downloadLimitBlocked by remember(target) { mutableStateOf(false) }

    fun save(key: String, value: Any, updated: () -> Unit) {
        scope.launch {
            val saved = withContext(NzikDispatchers.DATA) {
                DatabaseRestoreJournal.withProfileStorageMutation(context) {
                    if (profileStorageIdentity(context, target.id) != target) return@withProfileStorageMutation false
                    if (value is ExoPlayerDiskDownloadCacheMaxSize &&
                        value != ExoPlayerDiskDownloadCacheMaxSize.Unlimited &&
                        runCatching {
                            val limit = if (value == ExoPlayerDiskDownloadCacheMaxSize.Disabled) 0L else value.bytes
                            profileDownloadSpanBytes(context, target.id) > limit
                        }.getOrDefault(true)
                    ) return@withProfileStorageMutation false
                    val editor = preferences.edit()
                    when (value) {
                        is Enum<*> -> editor.putString(key, value.name)
                        is Int -> editor.putInt(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                    }
                    editor.commit()
                }
            }
            if (key == exoPlayerDiskDownloadCacheMaxSizeKey) downloadLimitBlocked = !saved
            if (saved) updated()
        }
    }

    var coilDiskCacheMaxSize by remember(preferences) {
        mutableStateOf(preferences.getEnum(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.`128MB`))
    }
    var exoPlayerDiskCacheMaxSize by remember(preferences) {
        mutableStateOf(preferences.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`))
    }
    var exoPlayerDiskDownloadCacheMaxSize by remember(preferences) {
        mutableStateOf(preferences.getEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskDownloadCacheMaxSize.`2GB`))
    }
    val exoPlayerCacheLocation = remember(preferences) {
        preferences.getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)
    }
    var showExoPlayerCustomCacheDialog by remember { mutableStateOf(false) }
    var exoPlayerCustomCache by remember(preferences) { mutableStateOf(preferences.getInt(exoPlayerCustomCacheKey, 32)) }
    var showCoilCustomDiskCacheDialog by remember { mutableStateOf(false) }
    var coilCustomDiskCache by remember(preferences) { mutableStateOf(imageCacheCustomMb(preferences)) }
    var originalArtwork by remember(preferences) {
        mutableStateOf(readOriginalImageQuality(preferences))
    }
    var showArtworkQualityDialog by remember(target) { mutableStateOf(false) }
    val artworkQualityTitle = stringResource(R.string.storage_image_quality_title)
    val artworkQualityLabel: @Composable (Boolean) -> String = {
        stringResource(if (it) R.string.storage_image_quality_original else R.string.storage_image_quality_optimized)
    }
    OtherSettingsEntry(
        title = artworkQualityTitle,
        text = stringResource(R.string.storage_image_quality_description),
        selectedValueText = artworkQualityLabel(originalArtwork),
        icon = R.drawable.cover_edit,
        modifier = Modifier.settingsSearchTarget("data.cache.artwork_quality"),
        onClick = { showArtworkQualityDialog = true },
    )
    DialogPresence(visible = showArtworkQualityDialog) {
        ValueSelectorDialog(
            title = artworkQualityTitle,
            selectedValue = originalArtwork,
            values = listOf(false, true),
            onValueSelected = { value ->
                save(ORIGINAL_IMAGE_QUALITY_KEY, value) {
                    originalArtwork = value
                    if (isActive) ImageCacheFactory.refreshArtworkQuality()
                }
            },
            valueText = artworkQualityLabel,
            onDismiss = { showArtworkQualityDialog = false },
        )
    }
    var showImageCacheDialog by remember { mutableStateOf(false) }

    OtherSettingsEntry(
        modifier = Modifier.settingsSearchTarget("data.cache.image_cache_max_size"),
        title = stringResource(R.string.settings_cache_image_size_limit_label),
        text = stringResource(R.string.cache_image_limit_description),
        selectedValueText = when (coilDiskCacheMaxSize) {
            CoilDiskCacheMaxSize.Custom -> "${stringResource(R.string.common_option_custom)}: ${coilCustomDiskCache}MB"
            else -> coilDiskCacheMaxSize.text
        },
        icon = R.drawable.image,
        onClick = { showImageCacheDialog = true },
    )

    DialogPresence(visible = showImageCacheDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.settings_cache_image_size_limit_label),
            selectedValue = coilDiskCacheMaxSize,
            values = CoilDiskCacheMaxSize.values().toList(),
            onValueSelected = {
                save(coilDiskCacheMaxSizeKey, it) {
                    coilDiskCacheMaxSize = it
                    showCoilCustomDiskCacheDialog = it == CoilDiskCacheMaxSize.Custom
                    if (isActive) restartService = true
                }
            },
            valueText = { it.text },
            onDismiss = { showImageCacheDialog = false }
        )
    }

    DialogPresence(visible = showCoilCustomDiskCacheDialog) {
        InputNumericDialog(
            title = stringResource(R.string.settings_cache_action_set_custom_size),
            placeholder = stringResource(R.string.settings_cache_custom_size_input_hint),
            value = coilCustomDiskCache.toString(),
            valueMin = "32",
            valueMax = "10000",
            onDismiss = { showCoilCustomDiskCacheDialog = false },
            setValue = {
                val size = it.toInt()
                save(PROFILE_IMAGE_CACHE_CUSTOM_MB, size) {
                    coilCustomDiskCache = size
                    showCoilCustomDiskCacheDialog = false
                    if (isActive) restartService = true
                }
            }
        )
    }

    var showSongCacheDialog by remember { mutableStateOf(false) }

    OtherSettingsEntry(
        modifier = Modifier.settingsSearchTarget("data.cache.song_cache_max_size"),
        title = stringResource(R.string.settings_cache_song_size_limit_label),
        text = stringResource(R.string.cache_streaming_limit_description),
        selectedValueText = when (exoPlayerDiskCacheMaxSize) {
            ExoPlayerDiskCacheMaxSize.Custom -> "${stringResource(R.string.common_option_custom)}: ${exoPlayerCustomCache}MB"
            ExoPlayerDiskCacheMaxSize.Disabled -> stringResource(R.string.common_action_turn_off)
            else -> exoPlayerDiskCacheMaxSize.text
        },
        icon = R.drawable.music_file,
        onClick = { showSongCacheDialog = true },
    )

    DialogPresence(visible = showSongCacheDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.settings_cache_song_size_limit_label),
            selectedValue = exoPlayerDiskCacheMaxSize,
            values = ExoPlayerDiskCacheMaxSize.values().toList(),
            onValueSelected = {
                save(exoPlayerDiskCacheMaxSizeKey, it) {
                    exoPlayerDiskCacheMaxSize = it
                    showExoPlayerCustomCacheDialog = it == ExoPlayerDiskCacheMaxSize.Custom
                    if (isActive) restartService = true
                }
            },
            valueText = { it.text },
            onDismiss = { showSongCacheDialog = false }
        )
    }

    DialogPresence(visible = showExoPlayerCustomCacheDialog) {
        InputNumericDialog(
            title = stringResource(R.string.settings_cache_action_set_custom_size),
            placeholder = stringResource(R.string.settings_cache_custom_size_input_hint),
            value = exoPlayerCustomCache.toString(),
            valueMin = "32",
            valueMax = "10000",
            onDismiss = { showExoPlayerCustomCacheDialog = false },
            setValue = {
                val size = it.toInt()
                save(exoPlayerCustomCacheKey, size) {
                    exoPlayerCustomCache = size
                    showExoPlayerCustomCacheDialog = false
                    if (isActive) restartService = true
                }
            }
        )
    }

    var showDownloadCacheDialog by remember { mutableStateOf(false) }

    OtherSettingsEntry(
        modifier = Modifier.settingsSearchTarget("data.cache.song_download_max_size"),
        title = stringResource(R.string.settings_downloads_storage_limit_label),
        text = stringResource(R.string.cache_downloads_limit_description),
        selectedValueText = exoPlayerDiskDownloadCacheMaxSize.text,
        icon = R.drawable.download,
        onClick = { showDownloadCacheDialog = true },
    )

    DialogPresence(visible = showDownloadCacheDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.settings_downloads_storage_limit_label),
            selectedValue = exoPlayerDiskDownloadCacheMaxSize,
            values = ExoPlayerDiskDownloadCacheMaxSize.values().toList(),
            onValueSelected = {
                save(exoPlayerDiskDownloadCacheMaxSizeKey, it) {
                    exoPlayerDiskDownloadCacheMaxSize = it
                    if (isActive) RestartAppDialog.showDialog()
                }
            },
            valueText = { it.text },
            onDismiss = { showDownloadCacheDialog = false }
        )
    }

    if (downloadLimitBlocked) SettingsDescription(stringResource(R.string.storage_download_limit_preserves_audio), important = true)

    var showCacheLocationDialog by remember { mutableStateOf(false) }
    if (search.inputValue.isBlank() || stringResource(R.string.settings_cache_location_label).contains(search.inputValue, true)) {
        OtherSettingsEntry(
            modifier = Modifier.settingsSearchTarget("data.cache.set_cache_location"),
            title = stringResource(R.string.settings_cache_location_label),
            text = stringResource(R.string.cache_location_description),
            selectedValueText = exoPlayerCacheLocation.text,
            icon = R.drawable.folder,
            onClick = { showCacheLocationDialog = true }
        )
    }

    SettingsDescription(stringResource(R.string.settings_cache_private_location_description), important = true)
    if (pendingLocation != null) {
        SettingsDescription(stringResource(R.string.settings_cache_location_change_pending), important = true)
    }

    DialogPresence(visible = showCacheLocationDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.settings_cache_location_label),
            selectedValue = exoPlayerCacheLocation,
            values = ExoPlayerCacheLocation.values().toList(),
            onValueSelected = {
                scope.launch {
                    val accepted = withContext(NzikDispatchers.DATA) {
                        ProfileCacheLocationMigration.request(context, target, it)
                    }
                    if (accepted) {
                        pendingLocation = it.takeUnless { location -> location == exoPlayerCacheLocation }
                        if (isActive && pendingLocation != null) RestartAppDialog.showDialog()
                    }
                }
            },
            valueText = { it.text },
            onDismiss = { showCacheLocationDialog = false }
        )
    }

    if (isActive) RestartPlayerService(restartService, onRestart = { restartService = false })
}
