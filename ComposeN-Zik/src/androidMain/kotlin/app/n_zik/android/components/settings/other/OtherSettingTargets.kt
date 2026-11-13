package app.n_zik.android.components.settings.other

import app.n_zik.android.utils.defaultFolderKey
import app.n_zik.android.utils.isKeepScreenOnEnabledKey
import app.n_zik.android.utils.isProxyEnabledKey
import app.n_zik.android.utils.logDebugEnabledKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.proxyHostnameKey
import app.n_zik.android.utils.proxyModeKey
import app.n_zik.android.utils.proxyPasswordEncryptedKey
import app.n_zik.android.utils.proxyPortKey
import app.n_zik.android.utils.proxyUsernameKey
import app.n_zik.android.utils.regionOverrideKey
import app.n_zik.android.utils.showFoldersOnDeviceKey

/** Stable inventory for future settings search integration. */
internal object OtherSettingTargets {
    val cards = listOf("other.on-device", "other.service-lifetime", "other.proxy", "other.parental-control", "other.maintenance", "other.extensions", "other.rescue", "other.reset")
    val preferenceKeys = listOf(defaultFolderKey, showFoldersOnDeviceKey, isKeepScreenOnEnabledKey, isProxyEnabledKey, proxyHostnameKey, proxyPortKey, proxyModeKey, proxyUsernameKey, proxyPasswordEncryptedKey, regionOverrideKey, parentalControlEnabledKey, logDebugEnabledKey)
    val actions = listOf("other.on-device.blacklist", "other.service-lifetime.battery-optimization", "other.maintenance.open", "other.maintenance.export-logs", "other.extensions.open", "other.rescue.open", "other.reset.apply")
    val focusAliases = mapOf("debug" to "other.maintenance", "maintenance" to "other.maintenance", "extensions" to "other.extensions", "rescue" to "other.rescue")
}
