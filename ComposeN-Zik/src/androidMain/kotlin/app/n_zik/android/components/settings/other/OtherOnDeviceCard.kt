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
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.defaultFolderKey
import app.it.fast4x.rimusic.utils.isAtLeastAndroid10
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showFoldersOnDeviceKey
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.StringListDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
@Composable
internal fun OtherOnDeviceCard(search: Search) {
    val context = LocalContext.current
    var defaultFolder by rememberPreference(defaultFolderKey, "/")
    var showFolders by rememberPreference(showFoldersOnDeviceKey, true)

    var blackListedPaths by remember {
        val file = File(context.filesDir, "Blacklisted_paths.txt")
        if (file.exists()) {
            mutableStateOf(file.readLines())
        } else {
            mutableStateOf(emptyList())
        }
    }

    SettingsSectionCard(
        title = stringResource(R.string.library_on_device_title),
        icon = R.drawable.folder,
        content = {

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
                    add = { newPath: String ->
                        blackListedPaths = blackListedPaths + newPath
                        val snapshot = blackListedPaths.toList()
                        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                            withContext(NzikDispatchers.DATA) {
                                File(context.filesDir, "Blacklisted_paths.txt").writeText(snapshot.joinToString("\n"))
                            }
                        }
                    },
                    remove = { path: String ->
                        blackListedPaths = blackListedPaths.filter { it != path }
                        val snapshot = blackListedPaths.toList()
                        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                            withContext(NzikDispatchers.DATA) {
                                File(context.filesDir, "Blacklisted_paths.txt").writeText(snapshot.joinToString("\n"))
                            }
                        }
                    },
                    onDismiss = { showBlacklistDialog = false }
                )
            }

            if (search.inputValue.isBlank() || stringResource(R.string.settings_local_music_folders_label).contains(search.inputValue, true) || stringResource(R.string.settings_local_music_folders_description).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("other.on_device.folders"),
                    title = stringResource(R.string.settings_local_music_folders_label),
                    text = stringResource(R.string.settings_local_music_folders_description),
                    isChecked = showFolders,
                    onCheckedChange = { showFolders = it },
                    icon = R.drawable.playlist
                )
            }

            AnimatedVisibility(visible = showFolders) {

                var showFolderDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.settings_local_music_initial_folder_description).contains(search.inputValue, true)) {
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
                        onSetValue = { defaultFolder = it }
                    ).apply {
                        showDialog()
                        Render()
                    }
                }
            }
        }
    )
}
