package app.n_zik.android.components.settings.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.defaultGlassQuality
import app.n_zik.android.components.glass.glassEnabledDefault
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.settings.accounts.applyAccountsDefaults
import app.n_zik.android.components.settings.ai.applyAiDefaults
import app.n_zik.android.components.settings.appearance.applyAppearanceDefaults
import app.n_zik.android.components.settings.data.applyDataDefaults
import app.n_zik.android.components.settings.general.applyGeneralDefaults
import app.n_zik.android.components.settings.network.applyNetworkDefaults
import app.n_zik.android.components.settings.other.applyOtherDefaults
import app.n_zik.android.components.settings.ui.applyUiDefaults

/** Explicit settings writes preserve credentials, profile records and user content. */
internal fun applyAllSettingsDefaults(context: Context, onSettingsApplied: () -> Unit = {}) {
    applyGeneralDefaults(context)
    applyUiDefaults(context)
    applyAppearanceDefaults(context)
    applyAiDefaults(context)
    applyDataDefaults(context)
    val accountPreferences = context.encryptedPreferences
    val hadLoginPreference = accountPreferences.contains(enableYouTubeLoginKey)
    val loginEnabled = accountPreferences.getBoolean(enableYouTubeLoginKey, false)
    applyAccountsDefaults(context)
    accountPreferences.edit {
        if (hadLoginPreference) putBoolean(enableYouTubeLoginKey, loginEnabled)
        else remove(enableYouTubeLoginKey)
    }
    applyNetworkDefaults(context)
    applyOtherDefaults(context)
    context.preferences.edit { putBoolean(glassEnabledKey, glassEnabledDefault) }
    val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    GlassStore(context).apply {
        reset()
        setProfile(defaultGlassQuality(Build.VERSION.SDK_INT, lowRam))
    }
    resetAdditionalSettingsDefaults(context)
    // Consumers can request one service restart only after every category has been restored.
    onSettingsApplied()
}
