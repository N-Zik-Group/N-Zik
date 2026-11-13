package app.n_zik.android.components.settings.other

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.utils.defaultFolderKey
import app.n_zik.android.utils.isAtLeastAndroid10
import app.n_zik.android.utils.showFoldersOnDeviceKey
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.StringListDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.tab.Search
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
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
    var defaultFolder by remember(target) { mutableStateOf(prefs.getString(defaultFolderKey, "/").orEmpty()) }
    var showFolders by remember(target) { mutableStateOf(prefs.getBoolean(showFoldersOnDeviceKey, true)) }
    val file = remember(target) { blockedLocalFoldersFile(context, target.id) }
    val searchTarget = LocalSettingsTargets.current?.storageEntryTarget
    var blackListedPaths by remember(target) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(target) {
        blackListedPaths = withContext(NzikDispatchers.DATA) {
            if (file.isFile) file.readLines() else emptyList()
        }
    }

    fun saveBlockedPaths(paths: List<String>) {
        blackListedPaths = paths
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            try {
                DatabaseRestoreJournal.withProfileStorageMutation(context) {
                    if (!ProfileWorkOwner.exists(context, target)) return@withProfileStorageMutation
                    writeOwnedImageAtomically(file) { it.write(paths.joinToString("\n").toByteArray()) }
                }
            } catch (error: IOException) {
                Timber.tag("OnDeviceSettings").e(error, "Could not save blocked folders")
            }
        }
    }

    var showBlacklistDialog by remember { mutableStateOf(false) }
    if (search.inputValue.isBlank() || stringResource(R.string.settings_local_music_blocked_folders_title).contains(search.inputValue, true) || stringResource(R.string.settings_local_music_blocked_folders_description).contains(search.inputValue, true)) {
        OtherSettingsEntry(
            modifier = Modifier.settingsSearchTarget("other.on_device.blacklisted_folders"),
            title = stringResource(R.string.settings_local_music_blocked_folders_title),
            text = stringResource(R.string.settings_local_music_blocked_folders_description),
            icon = R.drawable.blacklisted_folder,
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
            list = blackListedPaths,
            add = { path -> saveBlockedPaths(blackListedPaths + path) },
            remove = { path -> saveBlockedPaths(blackListedPaths.filter { it != path }) },
            onDismiss = { showBlacklistDialog = false }
        )
    }

    if (search.inputValue.isBlank() || stringResource(R.string.settings_local_music_folders_label).contains(search.inputValue, true) || stringResource(R.string.settings_local_music_folders_description).contains(search.inputValue, true)) {
        OtherSwitchSettingEntry(
            modifier = Modifier.settingsSearchTarget("other.on_device.folders"),
            title = stringResource(R.string.settings_local_music_folders_label),
            text = stringResource(R.string.settings_local_music_folders_description),
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
        if (search.inputValue.isBlank() || stringResource(R.string.settings_local_music_initial_folder_description).contains(search.inputValue, true) || stringResource(R.string.library_on_device_start_folder_description).contains(search.inputValue, true)) {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("other.on_device.folder_that_will_show_when_you_open_on_device_page"),
                title = stringResource(R.string.settings_local_music_initial_folder_description),
                text = stringResource(R.string.library_on_device_start_folder_description),
                selectedValueText = defaultFolder,
                icon = R.drawable.music_file,
                onClick = { showFolderDialog = true }
            )
        }

        DialogPresence(visible = showFolderDialog) {
            SettingsInputDialog(
                title = stringResource(R.string.settings_local_music_initial_folder_description),
                initialValue = defaultFolder,
                placeholder = stringResource(R.string.settings_local_music_initial_folder_description),
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
