package app.n_zik.android.components.settings.other

import java.net.Proxy
import android.content.Context
import androidx.core.content.edit
import app.n_zik.innertube.Innertube
import app.n_zik.android.utils.isKeepScreenOnEnabledKey
import app.n_zik.android.utils.isProxyEnabledKey
import app.n_zik.android.utils.logDebugEnabledKey
import app.n_zik.android.utils.otherLanguageAppAlbumKey
import app.n_zik.android.utils.otherLanguageAppArtistKey
import app.n_zik.android.utils.otherLanguageAppPlaylistKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.proxyHostnameKey
import app.n_zik.android.utils.proxyModeKey
import app.n_zik.android.utils.proxyPortKey
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.regionOverrideKey
import app.n_zik.android.core.ondevice.applyDeviceLibraryDefaults
import app.n_zik.android.core.settings.defaultProxyPort

internal fun applyProxyDefaults(context: Context) {
    context.preferences.edit {
        putBoolean(isProxyEnabledKey, false)
        putString(proxyHostnameKey, "")
        putInt(proxyPortKey, defaultProxyPort)
        putEnum(proxyModeKey, Proxy.Type.HTTP)
        putString(regionOverrideKey, "")
    }
    Innertube.regionOverride = ""
    Innertube.regionOverrideActive = false
}

internal fun applyOtherDefaults(context: Context) {
    context.preferences.edit {
        applyDeviceLibraryDefaults()
        putBoolean(isKeepScreenOnEnabledKey, false)
        putBoolean(parentalControlEnabledKey, false)
        putBoolean(logDebugEnabledKey, false)
        remove(otherLanguageAppAlbumKey)
        remove(otherLanguageAppArtistKey)
        remove(otherLanguageAppPlaylistKey)
    }
}
