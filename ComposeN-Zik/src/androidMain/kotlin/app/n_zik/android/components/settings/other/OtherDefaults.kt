package app.n_zik.android.components.settings.other

import java.net.Proxy
import android.content.Context
import androidx.core.content.edit
import it.fast4x.innertube.Innertube
import app.it.fast4x.rimusic.utils.defaultFolderKey
import app.it.fast4x.rimusic.utils.isKeepScreenOnEnabledKey
import app.it.fast4x.rimusic.utils.isProxyEnabledKey
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.otherLanguageAppAlbumKey
import app.it.fast4x.rimusic.utils.otherLanguageAppArtistKey
import app.it.fast4x.rimusic.utils.otherLanguageAppPlaylistKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.proxyHostnameKey
import app.it.fast4x.rimusic.utils.proxyModeKey
import app.it.fast4x.rimusic.utils.proxyPortKey
import app.it.fast4x.rimusic.utils.putEnum
import app.it.fast4x.rimusic.utils.regionOverrideKey
import app.it.fast4x.rimusic.utils.showFoldersOnDeviceKey
import app.n_zik.android.core.settings.defaultProxyPort

internal fun applyOtherDefaults(context: Context) {
    context.preferences.edit {
        putBoolean(isProxyEnabledKey, false)
        putString(proxyHostnameKey, "")
        putInt(proxyPortKey, defaultProxyPort)
        putEnum(proxyModeKey, Proxy.Type.HTTP)
        putString(regionOverrideKey, "")
        putString(defaultFolderKey, "/")
        putBoolean(isKeepScreenOnEnabledKey, false)
        putBoolean(showFoldersOnDeviceKey, true)
        putBoolean(parentalControlEnabledKey, false)
        putBoolean(logDebugEnabledKey, false)
        remove(otherLanguageAppAlbumKey)
        remove(otherLanguageAppArtistKey)
        remove(otherLanguageAppPlaylistKey)
    }
    Innertube.regionOverride = ""
    Innertube.regionOverrideActive = false
}
