package app.n_zik.android.components.settings.other

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.isProxyEnabledKey
import app.it.fast4x.rimusic.utils.proxyHostnameKey
import app.it.fast4x.rimusic.utils.proxyModeKey
import app.it.fast4x.rimusic.utils.proxyPasswordEncryptedKey
import app.it.fast4x.rimusic.utils.proxyPasswordKey
import app.it.fast4x.rimusic.utils.proxyPasswordMigratedKey
import app.it.fast4x.rimusic.utils.proxyPortKey
import app.it.fast4x.rimusic.utils.proxyUsernameKey
import app.it.fast4x.rimusic.utils.regionOverrideKey
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import it.fast4x.innertube.Innertube
import java.net.Proxy

@UnstableApi
@Composable
internal fun OtherProxyCard(search: Search) {
    val context = LocalContext.current
    var isProxyEnabled by rememberPreference(isProxyEnabledKey, false)
    var proxyHost by rememberPreference(proxyHostnameKey, "")
    var proxyPort by rememberPreference(proxyPortKey, 1080)
    var proxyMode by rememberPreference(proxyModeKey, Proxy.Type.HTTP)
    var proxyUsername by rememberPreference(proxyUsernameKey, "")

    var isPasswordMigrated by rememberPreference(proxyPasswordMigratedKey, false)
    val oldPassword by rememberPreference(proxyPasswordKey, "")
    var proxyPassword by rememberEncryptedPreference(proxyPasswordEncryptedKey, "")

    LaunchedEffect(Unit) {
        if (!isPasswordMigrated && oldPassword.isNotEmpty()) {
            proxyPassword = oldPassword
            isPasswordMigrated = true

            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit()
                .remove(proxyPasswordKey)
                .apply()
        }
    }

    var regionOverride by rememberPreference(regionOverrideKey, "")

    SettingsSectionCard(
        title = stringResource(R.string.proxy),
        icon = R.drawable.network,
        content = {
            SettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))

            if (search.inputValue.isBlank() || stringResource(R.string.enable_proxy).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("other.proxy.enable_proxy"),
                    title = stringResource(R.string.enable_proxy),
                    text = "",
                    isChecked = isProxyEnabled,
                    onCheckedChange = {
                        isProxyEnabled = it

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

                    var showProxyModeDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.proxy_mode).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("other.proxy.proxy_mode"),
                            title = stringResource(R.string.proxy_mode),
                            text = proxyMode.name,
                            icon = R.drawable.server,
                            onClick = { showProxyModeDialog = true }
                        )
                    }

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

                    var showProxyHostDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.proxy_host).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("other.proxy.proxy_host"),
                            title = stringResource(R.string.proxy_host),
                            text = proxyHost,
                            icon = R.drawable.server,
                            onClick = { showProxyHostDialog = true }
                        )
                    }

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

                    var showProxyPortDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.proxy_port).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("other.proxy.proxy_port"),
                            title = stringResource(R.string.proxy_port),
                            text = proxyPort.toString(),
                            icon = R.drawable.server,
                            onClick = { showProxyPortDialog = true }
                        )
                    }

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

                    var showProxyUsernameDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || "proxy username".contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("other.proxy.settings_proxy_username"),
                            title = stringResource(R.string.settings_proxy_username),
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

                    var showProxyPasswordDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || "proxy password".contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("other.proxy.settings_proxy_password"),
                            title = stringResource(R.string.settings_proxy_password),
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

                    var showRegionOverrideDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || "region override".contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("other.proxy.settings_region_override"),
                            title = stringResource(R.string.settings_region_override),
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
