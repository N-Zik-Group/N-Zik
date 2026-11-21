package app.n_zik.android.startup

import java.net.Proxy

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.Window
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.settings.ensureDefaultColorPaletteMode
import app.n_zik.android.core.settings.ensurePlayerBehaviorDefaults
import app.n_zik.android.core.settings.readProxyPort
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.closeWithBackButtonKey
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.isKeepScreenOnEnabledKey
import app.n_zik.android.utils.isProxyEnabledKey
import app.n_zik.android.utils.isValidIP
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.proxyHostnameKey
import app.n_zik.android.utils.proxyModeKey
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.utils.ProxyPreferenceItem
import app.n_zik.innertube.utils.getProxy

internal fun Context.initializeLaunchPreferences() {
    // A cold profile shortcut can change the active store after application startup.
    ensureDefaultColorPaletteMode(preferences)
    ensurePlayerBehaviorDefaults(preferences)
}

internal fun ComponentActivity.registerStartupCookieWarning() {
    val message = when (MainApplication.cookieStatus) {
        MainApplication.CookieStatus.INVALID -> R.string.account_youtube_cookie_invalid
        MainApplication.CookieStatus.EXPIRED -> R.string.account_youtube_session_expired
        MainApplication.CookieStatus.NOT_LOGGED_IN, MainApplication.CookieStatus.VALID -> return
    }
    lifecycleScope.launch {
        delay(3_000)
        Toaster.e(message)
    }
}

internal fun ComponentActivity.registerStartupBackCallback() {
    if (!preferences.getBoolean(closeWithBackButtonKey, true) && Build.VERSION.SDK_INT >= 33) {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}
    }
}

internal fun applyKeepScreenOnPreference(window: Window, preferences: SharedPreferences) {
    if (preferences.getBoolean(isKeepScreenOnEnabledKey, false)) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

internal fun applyStartupProxy(context: Context, preferences: SharedPreferences) {
    val proxy = with(preferences) {
        if (getBoolean(isProxyEnabledKey, false)) {
            val host = getString(proxyHostnameKey, null)
            val port = readProxyPort()
            val mode = getEnum(proxyModeKey, Proxy.Type.HTTP)
            if (isValidIP(host) && host != null) {
                getProxy(ProxyPreferenceItem(host, port, mode))
            } else {
                Timber.tag("MainActivity").w("Proxy preference is null or invalid, running without proxy")
                null
            }
        } else {
            Timber.tag("MainActivity").w("Proxy preference is null, running without proxy")
            null
        }
    }

    NetworkClientFactory.configure(
        proxy = proxy,
        cacheDir = NetworkClientFactory.profileCacheDirectory(context),
    )
    Innertube.proxy = proxy
}
