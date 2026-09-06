package app.it.fast4x.rimusic.ui.screens.settings

import app.n_zik.android.components.tab.Search
import app.n_zik.android.uiRoundnessShape

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.ValidationType
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.StringListDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.n_zik.android.components.dialog.logs.CopyLogsDialog
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.defaultFolderKey
import app.it.fast4x.rimusic.utils.extraspaceKey
import app.it.fast4x.rimusic.utils.isAtLeastAndroid10
import app.it.fast4x.rimusic.utils.isAtLeastAndroid12
import app.it.fast4x.rimusic.utils.isAtLeastAndroid6
import app.it.fast4x.rimusic.utils.isIgnoringBatteryOptimizations
import app.it.fast4x.rimusic.utils.isKeepScreenOnEnabledKey
import app.it.fast4x.rimusic.utils.isProxyEnabledKey
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.disabledStreamClientsKey
import app.it.fast4x.rimusic.utils.proxyHostnameKey
import app.it.fast4x.rimusic.utils.proxyModeKey
import app.it.fast4x.rimusic.utils.proxyPortKey
import app.it.fast4x.rimusic.utils.proxyUsernameKey
import app.it.fast4x.rimusic.utils.proxyPasswordKey
import app.it.fast4x.rimusic.utils.proxyPasswordEncryptedKey
import app.it.fast4x.rimusic.utils.proxyPasswordMigratedKey
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.it.fast4x.rimusic.utils.regionOverrideKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showFoldersOnDeviceKey
import app.it.fast4x.rimusic.utils.textCopyToClipboard
import app.kreate.android.me.knighthat.utils.Toaster
import it.fast4x.innertube.Innertube
import java.io.File
import java.net.Proxy
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun DefaultOtherSettings() {

    var isProxyEnabled by rememberPreference(isProxyEnabledKey, false)
    isProxyEnabled = false

    var proxyHost by rememberPreference(proxyHostnameKey, "")
    proxyHost = ""

    var proxyPort by rememberPreference(proxyPortKey, 1080)
    proxyPort = 1080

    var proxyMode by rememberPreference(proxyModeKey, Proxy.Type.HTTP)
    proxyMode = Proxy.Type.HTTP

    var defaultFolder by rememberPreference(defaultFolderKey, "/")
    defaultFolder = "/"

    var isKeepScreenOnEnabled by rememberPreference(isKeepScreenOnEnabledKey, false)
    isKeepScreenOnEnabled = false

    var showFolders by rememberPreference(showFoldersOnDeviceKey, true)
    showFolders = true

    var parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    parentalControlEnabled = false

    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)
    logDebugEnabled = false

    var extraspace by rememberPreference(extraspaceKey, false)
    extraspace = false
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("BatteryLife")
@ExperimentalAnimationApi
@Composable
fun OtherSettings() {
    val search = Search()

    val context = LocalContext.current

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations)
    }

    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations
        }

    var isProxyEnabled by rememberPreference(isProxyEnabledKey, false)
    var proxyHost by rememberPreference(proxyHostnameKey, "")
    var proxyPort by rememberPreference(proxyPortKey, 1080)
    var proxyMode by rememberPreference(proxyModeKey, Proxy.Type.HTTP)
    var proxyUsername by rememberPreference(proxyUsernameKey, "")
    
    // Migrate proxy password to encrypted preferences
    var isPasswordMigrated by rememberPreference(proxyPasswordMigratedKey, false)
    val oldPassword by rememberPreference(proxyPasswordKey, "")
    var proxyPassword by rememberEncryptedPreference(proxyPasswordEncryptedKey, "")
    
    // One-time migration from regular to encrypted preferences
    LaunchedEffect(Unit) {
        if (!isPasswordMigrated && oldPassword.isNotEmpty()) {
            proxyPassword = oldPassword
            isPasswordMigrated = true
            // Clear old unencrypted value
            context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                .edit()
                .remove(proxyPasswordKey)
                .apply()
        }
    }
    
    var regionOverride by rememberPreference(regionOverrideKey, "")

    var defaultFolder by rememberPreference(defaultFolderKey, "/")
    var isKeepScreenOnEnabled by rememberPreference(isKeepScreenOnEnabledKey, false)
    var showFolders by rememberPreference(showFoldersOnDeviceKey, true)

    var blackListedPaths by remember {
        val file = File(context.filesDir, "Blacklisted_paths.txt")
        if (file.exists()) {
            mutableStateOf(file.readLines())
        } else {
            mutableStateOf(emptyList())
        }
    }

    var parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)
    var extraspace by rememberPreference(extraspaceKey, false)

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {

        HeaderWithIcon(
            title = stringResource(R.string.tab_miscellaneous),
            iconId = R.drawable.equalizer,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        SettingsDescription(
            text = stringResource(R.string.other_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        ) 

        search.ToolBarButton()
        search.SearchBar( this )

        /* Removed Spacer */

        // On Device Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = tween(600),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.on_device),
                icon = R.drawable.folder,
                content = {
                    // Modern wrapper with icon
                    var showBlacklistDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.blacklisted_folders).contains(search.inputValue, true) || stringResource(R.string.edit_blacklist_for_on_device_songs).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.blacklisted_folders),
                            text = stringResource(R.string.edit_blacklist_for_on_device_songs),
                            icon = R.drawable.blacklisted_folder,
                            onClick = { showBlacklistDialog = true }
                        )
                    }
                    
                    // Only the dialog, not the old component
                    if (showBlacklistDialog) {
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
                                val file = File(context.filesDir, "Blacklisted_paths.txt")
                                file.writeText(blackListedPaths.joinToString("\n"))
                            },
                            remove = { path: String ->
                                blackListedPaths = blackListedPaths.filter { it != path }
                                val file = File(context.filesDir, "Blacklisted_paths.txt")
                                file.writeText(blackListedPaths.joinToString("\n"))
                            },
                            onDismiss = { showBlacklistDialog = false }
                        )
                    }
                    


                    if (search.inputValue.isBlank() || stringResource(R.string.folders).contains(search.inputValue, true) || stringResource(R.string.show_folders_in_on_device_page).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.folders),
                            text = stringResource(R.string.show_folders_in_on_device_page),
                            isChecked = showFolders,
                            onCheckedChange = { showFolders = it },
                            icon = R.drawable.playlist
                        )
                    }
                    
                    AnimatedVisibility(visible = showFolders) {
                        // Modern wrapper with icon
                        var showFolderDialog by remember { mutableStateOf(false) }
                        if (search.inputValue.isBlank() || stringResource(R.string.folder_that_will_show_when_you_open_on_device_page).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                title = stringResource(R.string.folder_that_will_show_when_you_open_on_device_page),
                                text = defaultFolder,
                                icon = R.drawable.music_file,
                                onClick = { showFolderDialog = true }
                            )
                        }
                        
                        // Only the dialog, not the old component
                        if (showFolderDialog) {
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

        /* Removed Spacer */

        // Android Head Unit Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                animationSpec = tween(800),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.androidheadunit),
                icon = R.drawable.car,
                content = {
                    if (search.inputValue.isBlank() || stringResource(R.string.extra_space).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.extra_space),
                            text = "",
                            isChecked = extraspace,
                            onCheckedChange = { extraspace = it },
                            icon = R.drawable.space
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Service Lifetime Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                animationSpec = tween(1000),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.service_lifetime),
                icon = R.drawable.battery,
                content = {
                    if (search.inputValue.isBlank() || stringResource(R.string.keep_screen_on).contains(search.inputValue, true) || stringResource(R.string.prevents_screen_timeout).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.keep_screen_on),
                            text = stringResource(R.string.prevents_screen_timeout),
                            isChecked = isKeepScreenOnEnabled,
                            onCheckedChange = { isKeepScreenOnEnabled = it },
                            icon = R.drawable.devices
                        )
                    }

                    ImportantSettingsDescription(text = stringResource(R.string.battery_optimizations_applied))

                    if (isAtLeastAndroid12) {
                        SettingsDescription(text = stringResource(R.string.is_android12))
                    }

                    val msgNoBatteryOptim = stringResource(R.string.not_find_battery_optimization_settings)

                                            if (search.inputValue.isBlank() || stringResource(R.string.ignore_battery_optimizations).contains(search.inputValue, true)) {
                                                OtherSettingsEntry(
                                title = stringResource(R.string.ignore_battery_optimizations),
                                text = if (isIgnoringBatteryOptimizations) {
                                    stringResource(R.string.already_unrestricted)
                                } else {
                                    stringResource(R.string.disable_background_restrictions)
                                },
                                icon = R.drawable.battery_opti,
                            onClick = {
                                if (!isAtLeastAndroid6) return@OtherSettingsEntry

                                try {
                                    activityResultLauncher.launch(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    )
                                } catch (e: ActivityNotFoundException) {
                                    try {
                                        activityResultLauncher.launch(
                                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        )
                                    } catch (e: ActivityNotFoundException) {
                                        Toaster.i(R.string.battery_optimization_msg, formatArgs = arrayOf(msgNoBatteryOptim, BuildConfig.APP_NAME))
                                    }
                                }
                            }
                        )
                                            }
                }
            )
        }

        /* Removed Spacer */

        // Proxy Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(1200)) + scaleIn(
                animationSpec = tween(1200),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.proxy),
                icon = R.drawable.network,
                content = {
                    SettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))
                    
                    if (search.inputValue.isBlank() || stringResource(R.string.enable_proxy).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.enable_proxy),
                            text = "",
                            isChecked = isProxyEnabled,
                            onCheckedChange = { 
                                isProxyEnabled = it
                                // Cascade: disable region override when proxy is disabled
                                if (!it) {
                                    regionOverride = ""
                                    Innertube.regionOverride = ""
                                    Innertube.regionOverrideActive = false
                                }
                            },
                            icon = R.drawable.server
                        )
                    }

                    AnimatedVisibility(visible = isProxyEnabled) {
                        Column {
                            // Modern wrapper with icon
                            var showProxyModeDialog by remember { mutableStateOf(false) }
                            if (search.inputValue.isBlank() || stringResource(R.string.proxy_mode).contains(search.inputValue, true)) {
                                OtherSettingsEntry(
                                    title = stringResource(R.string.proxy_mode),
                                    text = proxyMode.name,
                                    icon = R.drawable.server,
                                    onClick = { showProxyModeDialog = true }
                                )
                            }
                            
                            // Only the dialog, not the old component
                            if (showProxyModeDialog) {
                                ValueSelectorDialog(
                                    title = stringResource(R.string.proxy_mode),
                                    selectedValue = proxyMode,
                                    values = Proxy.Type.values().toList(),
                                    onValueSelected = { proxyMode = it },
                                    valueText = { it.name },
                                    onDismiss = { showProxyModeDialog = false }
                                )
                            }
                            // Modern wrapper with icon
                            var showProxyHostDialog by remember { mutableStateOf(false) }
                            if (search.inputValue.isBlank() || stringResource(R.string.proxy_host).contains(search.inputValue, true)) {
                                OtherSettingsEntry(
                                    title = stringResource(R.string.proxy_host),
                                    text = proxyHost,
                                    icon = R.drawable.server,
                                    onClick = { showProxyHostDialog = true }
                                )
                            }
                            
                            // Only the dialog, not the old component
                            if (showProxyHostDialog) {
                                SettingsInputDialog(
                                    title = stringResource(R.string.proxy_host),
                                    initialValue = proxyHost,
                                    placeholder = stringResource(R.string.proxy_host),
                                    onDismiss = { showProxyHostDialog = false },
                                    onSetValue = { proxyHost = it }
                                ).apply {
                                    showDialog()
                                    Render()
                                }
                            }
                            // Modern wrapper with icon
                            var showProxyPortDialog by remember { mutableStateOf(false) }
                            if (search.inputValue.isBlank() || stringResource(R.string.proxy_port).contains(search.inputValue, true)) {
                                OtherSettingsEntry(
                                    title = stringResource(R.string.proxy_port),
                                    text = proxyPort.toString(),
                                    icon = R.drawable.server,
                                    onClick = { showProxyPortDialog = true }
                                )
                            }
                            
                            // Only the dialog, not the old component
                            if (showProxyPortDialog) {
                                SettingsInputDialog(
                                    title = stringResource(R.string.proxy_port),
                                    initialValue = proxyPort.toString(),
                                    placeholder = stringResource(R.string.proxy_port),
                                    onDismiss = { showProxyPortDialog = false },
                                    onSetValue = { proxyPort = it.toIntOrNull() ?: 1080 }
                                ).apply {
                                    showDialog()
                                    Render()
                                }
                            }
                            // Proxy Username
                            var showProxyUsernameDialog by remember { mutableStateOf(false) }
                            if (search.inputValue.isBlank() || "proxy username".contains(search.inputValue, true)) {
                                OtherSettingsEntry(
                                    title = "Proxy Username",
                                    text = if (proxyUsername.isBlank()) "None" else proxyUsername,
                                    icon = R.drawable.server,
                                    onClick = { showProxyUsernameDialog = true }
                                )
                            }
                            if (showProxyUsernameDialog) {
                                SettingsInputDialog(
                                    title = "Proxy Username",
                                    initialValue = proxyUsername,
                                    placeholder = "Username (optional)",
                                    allowEmpty = true,
                                    onDismiss = { showProxyUsernameDialog = false },
                                    onSetValue = { proxyUsername = it }
                                ).apply {
                                    showDialog()
                                    Render()
                                }
                            }
                            // Proxy Password
                            var showProxyPasswordDialog by remember { mutableStateOf(false) }
                            if (search.inputValue.isBlank() || "proxy password".contains(search.inputValue, true)) {
                                OtherSettingsEntry(
                                    title = "Proxy Password",
                                    text = if (proxyPassword.isBlank()) "None" else "••••••",
                                    icon = R.drawable.server,
                                    onClick = { showProxyPasswordDialog = true }
                                )
                            }
                            if (showProxyPasswordDialog) {
                                SettingsInputDialog(
                                    title = "Proxy Password",
                                    initialValue = proxyPassword,
                                    placeholder = "Password (optional)",
                                    allowEmpty = true,
                                    isPassword = true,
                                    onDismiss = { showProxyPasswordDialog = false },
                                    onSetValue = { proxyPassword = it }
                                ).apply {
                                    showDialog()
                                    Render()
                                }
                            }
                            // Region Override
                            var showRegionOverrideDialog by remember { mutableStateOf(false) }
                            if (search.inputValue.isBlank() || "region override".contains(search.inputValue, true)) {
                                OtherSettingsEntry(
                                    title = "Region Override",
                                    text = if (regionOverride.isBlank()) "Auto (from phone)" else regionOverride,
                                    icon = R.drawable.server,
                                    onClick = { showRegionOverrideDialog = true }
                                )
                            }
                            if (showRegionOverrideDialog) {
                                SettingsInputDialog(
                                    title = "Region Override",
                                    initialValue = regionOverride,
                                    placeholder = "e.g. US, FR, DE (empty = auto)",
                                    allowEmpty = true,
                                    onDismiss = { showRegionOverrideDialog = false },
                                    onSetValue = { 
                                        regionOverride = it
                                        Innertube.regionOverride = it
                                        Innertube.regionOverrideActive = it.isNotBlank()
                                    }
                                ).apply {
                                    showDialog()
                                    Render()
                                }
                            }
                        }
                    }
                }
            )
        }

        /* Removed Spacer */

        // Parental Control Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(1400)) + scaleIn(
                animationSpec = tween(1400),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.parental_control),
                icon = R.drawable.shield_checkmark,
                content = {
                    if (search.inputValue.isBlank() || stringResource(R.string.parental_control).contains(search.inputValue, true) || stringResource(R.string.info_prevent_play_songs_with_age_limitation).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.parental_control),
                            text = stringResource(R.string.info_prevent_play_songs_with_age_limitation),
                            isChecked = parentalControlEnabled,
                            onCheckedChange = { parentalControlEnabled = it },
                            icon = R.drawable.parental_control
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Debug Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(1600)) + scaleIn(
                animationSpec = tween(1600),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.debug),
                icon = R.drawable.bugs,
                content = {
                    CopyLogsDialog.Render()

                    if (search.inputValue.isBlank() || stringResource(R.string.enable_log_debug).contains(search.inputValue, true) || stringResource(R.string.if_enabled_create_a_log_file_to_highlight_errors).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.enable_log_debug),
                            text = stringResource(R.string.if_enabled_create_a_log_file_to_highlight_errors),
                            isChecked = logDebugEnabled,
                            onCheckedChange = {
                                logDebugEnabled = it
                                if (!it) {
                                    val file = File(context.filesDir.resolve("logs"), "N-Zik_log.txt")
                                    if (file.exists())
                                        file.delete()

                                    val filec = File(context.filesDir.resolve("logs"), "N-Zik_crash_log.txt")
                                    if (filec.exists())
                                        filec.delete()
                                } else
                                    Toaster.i(R.string.restarting_rimusic_is_required)
                            },
                            icon = R.drawable.information
                        )
                    }
                    
                    ImportantSettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))
                    
                    if (logDebugEnabled) {
                        if (search.inputValue.isBlank() || stringResource(R.string.export_logs).contains(search.inputValue, true) || stringResource(R.string.export_debug_log_description).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                title = stringResource(R.string.export_logs),
                                text = stringResource(R.string.export_debug_log_description),
                                icon = R.drawable.copy,
                                onClick = { CopyLogsDialog.showDialog() }
                            )
                        }
                    }
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
                        DefaultOtherSettings()
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





