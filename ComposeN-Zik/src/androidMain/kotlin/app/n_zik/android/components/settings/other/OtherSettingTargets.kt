package app.n_zik.android.components.settings.other

import app.it.fast4x.rimusic.utils.defaultFolderKey
import app.it.fast4x.rimusic.utils.extraspaceKey
import app.it.fast4x.rimusic.utils.isKeepScreenOnEnabledKey
import app.it.fast4x.rimusic.utils.isProxyEnabledKey
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.proxyHostnameKey
import app.it.fast4x.rimusic.utils.proxyModeKey
import app.it.fast4x.rimusic.utils.proxyPasswordEncryptedKey
import app.it.fast4x.rimusic.utils.proxyPortKey
import app.it.fast4x.rimusic.utils.proxyUsernameKey
import app.it.fast4x.rimusic.utils.regionOverrideKey
import app.it.fast4x.rimusic.utils.showFoldersOnDeviceKey

/** Stable inventory for future settings search integration. */
internal object OtherSettingTargets {
    val cards = listOf("other.on-device", "other.head-unit", "other.service-lifetime", "other.proxy", "other.parental-control", "other.maintenance", "other.extensions", "other.rescue", "other.reset")
    val preferenceKeys = listOf(defaultFolderKey, showFoldersOnDeviceKey, extraspaceKey, isKeepScreenOnEnabledKey, isProxyEnabledKey, proxyHostnameKey, proxyPortKey, proxyModeKey, proxyUsernameKey, proxyPasswordEncryptedKey, regionOverrideKey, parentalControlEnabledKey, logDebugEnabledKey)
    val actions = listOf("other.on-device.blacklist", "other.service-lifetime.battery-optimization", "other.maintenance.open", "other.maintenance.export-logs", "other.extensions.open", "other.rescue.open", "other.reset.apply")
    val focusAliases = mapOf("debug" to "other.maintenance", "maintenance" to "other.maintenance", "extensions" to "other.extensions", "rescue" to "other.rescue")
}
