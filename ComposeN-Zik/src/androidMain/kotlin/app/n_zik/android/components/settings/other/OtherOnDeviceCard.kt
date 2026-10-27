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
        title = stringResource(R.string.on_device),
        icon = R.drawable.folder,
        content = {

            var showBlacklistDialog by remember { mutableStateOf(false) }
            if (search.inputValue.isBlank() || stringResource(R.string.blacklisted_folders).contains(search.inputValue, true) || stringResource(R.string.edit_blacklist_for_on_device_songs).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("other.on_device.blacklisted_folders"),
                    title = stringResource(R.string.blacklisted_folders),
                    text = stringResource(R.string.edit_blacklist_for_on_device_songs),
                    icon = R.drawable.blacklisted_folder,
                    onClick = { showBlacklistDialog = true }
                )
            }

            DialogPresence(visible = showBlacklistDialog) {
                StringListDialog(
                    title = stringResource(R.string.blacklisted_folders),
                    addTitle = stringResource(R.string.add_folder),
                    addPlaceholder = if (isAtLeastAndroid10) {
                        "Android/media/com.whatsapp/WhatsApp/Media"
                    } else {
                        "/storage/emulated/0/Android/media/com.whatsapp/"
                    },
                    conflictTitle = stringResource(R.string.this_folder_already_exists),
                    removeTitle = stringResource(R.string.are_you_sure_you_want_to_remove_this_folder_from_the_blacklist),
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

            if (search.inputValue.isBlank() || stringResource(R.string.folders).contains(search.inputValue, true) || stringResource(R.string.show_folders_in_on_device_page).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("other.on_device.folders"),
                    title = stringResource(R.string.folders),
                    text = stringResource(R.string.show_folders_in_on_device_page),
                    isChecked = showFolders,
                    onCheckedChange = { showFolders = it },
                    icon = R.drawable.playlist
                )
            }

            AnimatedVisibility(visible = showFolders) {

                var showFolderDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.folder_that_will_show_when_you_open_on_device_page).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("other.on_device.folder_that_will_show_when_you_open_on_device_page"),
                        title = stringResource(R.string.folder_that_will_show_when_you_open_on_device_page),
                        text = stringResource(R.string.settings_desc_other_on_device_folder_that_will_show_when_you_open_on_device_page),
                        selectedValueText = defaultFolder,
                        icon = R.drawable.music_file,
                        onClick = { showFolderDialog = true }
                    )
                }

                DialogPresence(visible = showFolderDialog) {
                    SettingsInputDialog(
                        title = stringResource(R.string.folder_that_will_show_when_you_open_on_device_page),
                        initialValue = defaultFolder,
                        placeholder = stringResource(R.string.folder_that_will_show_when_you_open_on_device_page),
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
