package app.n_zik.android.startup

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.core.settings.ensureDefaultColorPaletteMode
import app.n_zik.android.core.settings.ensurePlayerBehaviorDefaults
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.applyDeferredImportedAppLanguage
import app.n_zik.android.utils.closeWithBackButtonKey
import app.n_zik.android.utils.isKeepScreenOnEnabledKey
import app.n_zik.android.utils.preferences

internal fun Context.initializeLaunchPreferences() {
    // A cold profile shortcut can change the active store after application startup.
    ensureDefaultColorPaletteMode(preferences)
    ensurePlayerBehaviorDefaults(preferences)
    applyDeferredImportedAppLanguage()
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

internal fun ComponentActivity.observeWindowPreferences() {
    val preferences = preferences
    // Register before navigation so Back can still dismiss dialogs and pop screens.
    val backFallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }.also { onBackPressedDispatcher.addCallback(it) }
    } else null

    fun applyWindowPreferences(key: String?) {
        if (key == null || key == isKeepScreenOnEnabledKey) {
            val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (preferences.getBoolean(isKeepScreenOnEnabledKey, false)) window.addFlags(flag)
            else window.clearFlags(flag)
        }
        if (key == null || key == closeWithBackButtonKey) {
            backFallback?.isEnabled = !preferences.getBoolean(closeWithBackButtonKey, true)
        }
    }

    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> applyWindowPreferences(key) }
    preferences.registerOnSharedPreferenceChangeListener(listener)
    applyWindowPreferences(null)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            backFallback?.remove()
            owner.lifecycle.removeObserver(this)
        }
    })
}
