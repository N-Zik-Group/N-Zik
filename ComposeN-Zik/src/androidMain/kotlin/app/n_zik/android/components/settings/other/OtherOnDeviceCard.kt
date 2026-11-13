package app.n_zik.android.components.settings.other

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import app.n_zik.android.core.ondevice.deviceAudioPermission
import app.n_zik.android.core.ondevice.hasDeviceAudioPermission
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.ui.InlineCircularLoader
import app.n_zik.android.core.ondevice.DeviceLibrarySnapshot
import app.n_zik.android.core.ondevice.DeviceLibraryResults
import app.n_zik.android.core.ondevice.SCAN_DEVICE_LIBRARY_ON_STARTUP_KEY
import app.n_zik.android.core.ondevice.DEVICE_LIBRARY_ENABLED_KEY
import app.n_zik.android.core.ondevice.observeDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.image.writeOwnedImageAtomically
import app.n_zik.android.components.ui.screens.home.onDevice.blockedLocalFoldersFile
import app.n_zik.android.components.ui.screens.home.onDevice.localSongRefreshRequests
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.utils.defaultFolderKey
import app.n_zik.android.utils.isAtLeastAndroid10
import app.n_zik.android.utils.showFoldersOnDeviceKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.StringListDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.tab.Search
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import timber.log.Timber

@UnstableApi
@Composable
internal fun OtherOnDeviceCard(search: Search, target: ProfileStorageIdentity, pageEntryTargets: Set<String>) {
    SettingsSectionCard(
        title = stringResource(R.string.library_on_device_title),
        icon = R.drawable.folder,
        headerModifier = Modifier.settingsSearchTarget("other.on_device.section"),
        content = { OnDeviceSettings(search, target, pageEntryTargets) },
    )
}

@Composable
private fun OnDeviceSettings(search: Search, target: ProfileStorageIdentity, pageEntryTargets: Set<String>) {
    val context = LocalContext.current
    val prefs = remember(target) { profileDataPrefs(context, target.id) }
    val enabled by remember(context, target) {
        context.observeDeviceLibraryEnabled(target.id)
    }.collectAsStateWithLifecycle(context.isDeviceLibraryEnabled(target.id))
    var pendingPermission by rememberSaveable(target.id, target.generation) { mutableStateOf(false) }

    fun setEnabled(checked: Boolean) {
        if (ProfileWorkOwner.exists(context, target)) {
            prefs.edit().putBoolean(DEVICE_LIBRARY_ENABLED_KEY, checked).apply()
            localSongRefreshRequests.request(target.id)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (pendingPermission && granted) setEnabled(true)
        pendingPermission = false
    }
    OtherSwitchSettingEntry(
        modifier = Modifier.settingsSearchTarget("other.on_device.enabled"),
        title = stringResource(R.string.settings_local_music_enabled_title),
        text = stringResource(R.string.settings_local_music_enabled_description),
        icon = R.drawable.folder,
        isChecked = enabled,
        enabled = !pendingPermission,
        onCheckedChange = { checked ->
            if (!checked || context.hasDeviceAudioPermission()) {
                setEnabled(checked)
            } else {
                pendingPermission = true
                permissionLauncher.launch(deviceAudioPermission)
            }
        },
    )
    androidx.compose.animation.AnimatedVisibility(visible = enabled) {
        Column { OnDeviceOptions(search, target, pageEntryTargets) }
    }
}

@Composable
private fun OnDeviceOptions(search: Search, target: ProfileStorageIdentity, pageEntryTargets: Set<String>) {
    val context = LocalContext.current
    val prefs = remember(target) { profileDataPrefs(context, target.id) }
    var defaultFolder by remember(target) { mutableStateOf(prefs.getString(defaultFolderKey, "/").orEmpty()) }
    var showFolders by remember(target) { mutableStateOf(prefs.getBoolean(showFoldersOnDeviceKey, true)) }
    val file = remember(target) { blockedLocalFoldersFile(context, target.id) }
    val searchTarget = LocalSettingsTargets.current?.storageEntryTarget
    var blackListedPaths by remember(target) { mutableStateOf<List<String>?>(null) }
    val blockedPathWrites = remember(target) { Mutex() }
    LaunchedEffect(target) {
        try {
            blackListedPaths = withContext(NzikDispatchers.DATA) {
                if (file.isFile) file.readLines() else emptyList()
            }
        } catch (error: IOException) {
            Timber.tag("OnDeviceSettings").e(error, "Could not read blocked folders")
        }
    }

    fun changeBlockedPaths(change: (List<String>) -> List<String>) {
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            blockedPathWrites.withLock {
                try {
                    val savedPaths = withContext(NzikDispatchers.DATA) {
                        DatabaseRestoreJournal.withProfileStorageMutation(context) {
                            if (!ProfileWorkOwner.exists(context, target)) return@withProfileStorageMutation null
                            val currentPaths = if (file.isFile) file.readLines() else emptyList()
                            val updatedPaths = change(currentPaths)
                            writeOwnedImageAtomically(file) { it.write(updatedPaths.joinToString("\n").toByteArray()) }
                            updatedPaths
                        }
                    }
                    if (savedPaths != null) {
                        blackListedPaths = savedPaths
                        localSongRefreshRequests.request(target.id)
                    }
                } catch (error: IOException) {
                    Timber.tag("OnDeviceSettings").e(error, "Could not save blocked folders")
                }
            }
        }
    }

    val activeProfile = target.id == getActiveProfile(context)
    var scanOnStartup by remember(target) { mutableStateOf(prefs.getBoolean(SCAN_DEVICE_LIBRARY_ON_STARTUP_KEY, true)) }
    val latestScan by remember(target) { DeviceLibraryResults.forProfile(target.id) }
        .collectAsStateWithLifecycle(DeviceLibraryResults.current(target.id))
    val scope = rememberCoroutineScope()
    var showSummary by remember(target) { mutableStateOf(false) }
    var scanning by remember(target) { mutableStateOf(false) }
    var summary by remember(target) { mutableStateOf<DeviceLibrarySnapshot?>(null) }

    fun refreshLibrary(notifyBrowsers: Boolean) {
        if (scanning || target.id != getActiveProfile(context) || !ProfileWorkOwner.exists(context, target)) return
        scanning = true
        scope.launch {
            try {
                summary = context.scanDeviceLibrary(refresh = notifyBrowsers)
            } finally {
                scanning = false
            }
        }
    }

    val summaryTitle = stringResource(R.string.settings_local_music_summary_title)
    val summaryDescription = stringResource(R.string.settings_local_music_summary_description)
    val songCount = (latestScan ?: summary)?.let {
        pluralStringResource(R.plurals.library_song_count, it.songIds.size, it.songIds.size)
    }
    if (activeProfile && (summaryTitle.contains(search.inputValue, true) || summaryDescription.contains(search.inputValue, true))) {
        OtherSettingsEntry(
            title = summaryTitle,
            text = summaryDescription,
            icon = R.drawable.folder,
            onClick = {
                showSummary = true
                refreshLibrary(notifyBrowsers = false)
            },
        )
    }
    DialogPresence(visible = showSummary && activeProfile) {
        DialogFrame(
            onDismiss = { showSummary = false },
            title = summaryTitle,
            footer = {
                ButtonRow(listOf(DialogButton(
                    text = stringResource(R.string.common_action_ok),
                    onClick = { showSummary = false },
                    primary = true,
                )))
            },
        ) {
            val result = summary
            if (scanning || result == null) {
                InlineCircularLoader(Modifier.size(24.dp))
            } else {
                val entries = listOf(
                    Triple(R.drawable.musical_notes, R.string.library_songs_title, result.songIds.size),
                    Triple(R.drawable.album, R.string.library_albums_title, result.albumIds.size),
                    Triple(R.drawable.artist, R.string.library_artists_title, result.artistIds.size),
                    Triple(R.drawable.folder, R.string.settings_local_music_folders_label, result.folders.size),
                )
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                ) {
                    entries.forEachIndexed { index, (icon, label, count) ->
                        if (index > 0) HorizontalDivider(color = colorPalette().textSecondary.copy(alpha = 0.16f))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = colorPalette().accent)
                            Text(stringResource(label), Modifier.weight(1f), color = colorPalette().text, style = typography().s)
                            Text("%d".format(count), color = colorPalette().text, style = typography().s,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    result.folders.sorted().forEach { folder ->
                        Text(folder.ifEmpty { "/" }, Modifier.padding(start = 30.dp, bottom = 6.dp),
                            color = colorPalette().textSecondary, style = typography().xs)
                    }
                }
            }
        }
    }

    val startupTitle = stringResource(R.string.settings_local_music_scan_on_startup_title)
    val startupDescription = stringResource(R.string.settings_local_music_scan_on_startup_description)
    if (startupTitle.contains(search.inputValue, true) || startupDescription.contains(search.inputValue, true)) {
        OtherSwitchSettingEntry(
            modifier = Modifier.settingsSearchTarget("other.on_device.scan_on_startup"),
            title = startupTitle,
            text = startupDescription,
            icon = R.drawable.refresh,
            isChecked = scanOnStartup,
            onCheckedChange = { enabled ->
                if (ProfileWorkOwner.exists(context, target)) {
                    prefs.edit().putBoolean(SCAN_DEVICE_LIBRARY_ON_STARTUP_KEY, enabled).apply()
                    scanOnStartup = enabled
                }
            },
        )
    }

    val refreshTitle = stringResource(R.string.settings_local_music_refresh_title)
    val refreshDescription = stringResource(R.string.settings_local_music_refresh_description)
    if (activeProfile && (refreshTitle.contains(search.inputValue, true) || refreshDescription.contains(search.inputValue, true))) {
        OtherSettingsEntry(
            modifier = Modifier.settingsSearchTarget("other.on_device.refresh"),
            title = refreshTitle,
            text = refreshDescription,
            icon = R.drawable.refresh,
            isEnabled = !scanning,
            selectedValueText = songCount ?: stringResource(R.string.settings_local_music_scan_not_run),
            trailingContent = if (scanning) {{ InlineCircularLoader(Modifier.size(20.dp)) }} else null,
            onClick = { refreshLibrary(notifyBrowsers = true) },
        )
    }

    var showBlacklistDialog by remember(target) { mutableStateOf(false) }
    if (search.inputValue.isBlank() || stringResource(R.string.settings_local_music_blocked_folders_title).contains(search.inputValue, true) || stringResource(R.string.settings_local_music_folder_exclusions_description).contains(search.inputValue, true)) {
        OtherSettingsEntry(
            modifier = Modifier.settingsSearchTarget("other.on_device.blacklisted_folders"),
            title = stringResource(R.string.settings_local_music_blocked_folders_title),
            text = stringResource(R.string.settings_local_music_folder_exclusions_description),
            icon = R.drawable.blacklisted_folder,
            isEnabled = blackListedPaths != null,
            onClick = { showBlacklistDialog = true }
        )
    }

    DialogPresence(visible = showBlacklistDialog) {
        StringListDialog(
            title = stringResource(R.string.settings_local_music_blocked_folders_title),
            addTitle = stringResource(R.string.settings_local_music_action_add_blocked_folder),
            addPlaceholder = if (isAtLeastAndroid10) {
                "Android/media/com.whatsapp/WhatsApp/Media"
            } else {
                "/storage/emulated/0/Android/media/com.whatsapp/"
            },
            conflictTitle = stringResource(R.string.settings_local_music_blocked_folder_exists_message),
            removeTitle = stringResource(R.string.settings_local_music_remove_blocked_folder_confirmation),
            list = blackListedPaths.orEmpty(),
            add = { path -> changeBlockedPaths { (it + path).distinct() } },
            remove = { path -> changeBlockedPaths { paths -> paths.filter { it != path } } },
            onDismiss = { showBlacklistDialog = false }
        )
    }

    if (search.inputValue.isBlank() || stringResource(R.string.settings_local_music_folders_label).contains(search.inputValue, true) || stringResource(R.string.settings_local_music_folder_mode_description).contains(search.inputValue, true)) {
        OtherSwitchSettingEntry(
            modifier = Modifier.settingsSearchTarget("other.on_device.folders"),
            title = stringResource(R.string.settings_local_music_folders_label),
            text = stringResource(R.string.settings_local_music_folder_mode_description),
            isChecked = showFolders,
            onCheckedChange = {
                if (ProfileWorkOwner.exists(context, target)) {
                    prefs.edit().putBoolean(showFoldersOnDeviceKey, it).apply()
                    showFolders = it
                }
            },
            icon = R.drawable.playlist
        )
    }

    val startFolderTarget = "other.on_device.folder_that_will_show_when_you_open_on_device_page"
    AnimatedVisibility(visible = showFolders || searchTarget == startFolderTarget || startFolderTarget in pageEntryTargets) {

        var showFolderDialog by remember { mutableStateOf(false) }
        if (search.inputValue.isBlank() || stringResource(R.string.settings_local_music_initial_folder_description).contains(search.inputValue, true) || stringResource(R.string.settings_local_music_start_folder_path_description).contains(search.inputValue, true)) {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget(startFolderTarget),
                title = stringResource(R.string.settings_local_music_initial_folder_description),
                text = stringResource(R.string.settings_local_music_start_folder_path_description),
                selectedValueText = defaultFolder,
                icon = R.drawable.music_file,
                onClick = { showFolderDialog = true }
            )
        }

        DialogPresence(visible = showFolderDialog) {
            SettingsInputDialog(
                title = stringResource(R.string.settings_local_music_initial_folder_description),
                initialValue = defaultFolder,
                placeholder = "Music/Live",
                onDismiss = { showFolderDialog = false },
                onSetValue = {
                    if (ProfileWorkOwner.exists(context, target)) {
                        prefs.edit().putString(defaultFolderKey, it).apply()
                        defaultFolder = it
                    }
                }
            ).apply {
                showDialog()
                Render()
            }
        }
    }
}
