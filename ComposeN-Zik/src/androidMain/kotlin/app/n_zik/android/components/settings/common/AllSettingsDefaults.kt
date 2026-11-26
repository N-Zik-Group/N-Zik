package app.n_zik.android.components.settings.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.defaultGlassQuality
import app.n_zik.android.components.glass.glassEnabledDefault
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.settings.accounts.applyAccountsDefaults
import app.n_zik.android.components.settings.ai.applyAiDefaults
import app.n_zik.android.components.settings.appearance.applyAppearanceDefaults
import app.n_zik.android.components.settings.data.applyDataDefaults
import app.n_zik.android.components.settings.general.applyGeneralDefaults
import app.n_zik.android.components.settings.general.resetGeneralApplicationLocales
import app.n_zik.android.components.settings.network.applyNetworkDefaults
import app.n_zik.android.components.settings.network.applyDownloadDefaults
import app.n_zik.android.components.settings.other.applyOtherDefaults
import app.n_zik.android.components.settings.ui.applyUiDefaults
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Explicit settings writes preserve credentials, profile records and user content. */
internal suspend fun applyAllSettingsDefaults(
    context: Context,
    onDownloadLimitRetained: () -> Unit = {},
    onSettingsApplied: () -> Unit = {},
): Boolean =
    withContext(NonCancellable) {
        val profileId = getActiveProfile(context)
        val applied = withContext(NzikDispatchers.DATA) {
            DatabaseRestoreJournal.withProfileStorageMutation(context) {
                if (!applyDataDefaults(context, profileId, onDownloadLimitRetained)) return@withProfileStorageMutation false
                applyRemainingSettingsDefaults(context)
                true
            }
        }
        if (applied) withContext(NzikDispatchers.UI.immediate) {
            if (getActiveProfile(context) == profileId) {
                resetGeneralApplicationLocales()
                onSettingsApplied()
            }
        }
        applied
    }

private fun applyRemainingSettingsDefaults(context: Context) {
    applyGeneralDefaults(context, resetLocales = false)
    applyUiDefaults(context)
    applyAppearanceDefaults(context)
    applyAiDefaults(context)
    applyAccountsDefaults(context)
    applyNetworkDefaults(context)
    applyDownloadDefaults(context)
    applyOtherDefaults(context)
    context.preferences.edit { putBoolean(glassEnabledKey, glassEnabledDefault) }
    val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    GlassStore(context).apply {
        reset()
        setProfile(defaultGlassQuality(Build.VERSION.SDK_INT, lowRam))
    }
    resetAdditionalSettingsDefaults(context)
}
