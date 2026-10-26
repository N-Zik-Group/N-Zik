package app.n_zik.android.components.dialog.common

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.putEnum
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.maintenance.formatBytes
import app.n_zik.android.core.profiles.downloadsDir
import app.n_zik.android.core.profiles.mediaCacheDir
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.totalDirectorySize
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.typography
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TAG = "SamsungStorageAdviceDialog"

/** Samsung detection: [Build.MANUFACTURER] equals this value case-insensitively (the frozen rule, the Discord.kt idiom). */
private const val SAMSUNG_MANUFACTURER = "samsung"

/** Frozen summary threshold (review loop 1): the storage summary line is hidden below 512 KB of scanned storage. */
private const val SUMMARY_MIN_BYTES = 512L * 1024L

/**
 * One-time Samsung storage advice (spec-samsung-storage-advice-dialog): the OEM memory
 * management can delete N-Zik's downloads and cache files, so Samsung users are advised
 * to switch to the private (app) storage location. Shown at most once per device: the
 * shown flag is persisted when the dialog is SHOWN, even on a back-dismiss.
 *
 * The gate and the primary action consider and write the ACTIVE profile's OWN location
 * setting only (the same per-profile semantics as the settings card), never the base
 * profile's. The size scan counts the physical downloads + media cache directories
 * wherever they live, so the summary line presents the bytes without distinguishing
 * shared from separate (documented limitation, frozen decision, review loop 1: for data
 * items shared with the base profile, opt-in data sharing, those bytes sit under the
 * BASE profile's location).
 */
object SamsungStorageAdviceDialog : ConfirmDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.samsung_storage_advice_title)

    override var isActive: Boolean by mutableStateOf(false)

    /**
     * The scanned downloads + media cache size (bytes) of the active profile, written by
     * [triggerSamsungStorageAdvice] before [showDialog]; below [SUMMARY_MIN_BYTES] the body
     * hides the summary line (only title + message + buttons render).
     */
    internal var storageBytes: Long by mutableStateOf(0L)

    /**
     * Pure show decision (review loop 1): Samsung + never shown + the active profile's own
     * location still System.
     */
    fun shouldShow(isSamsung: Boolean, alreadyShown: Boolean, cacheLocation: ExoPlayerCacheLocation): Boolean =
        isSamsung && !alreadyShown && cacheLocation == ExoPlayerCacheLocation.System

    /**
     * Samsung detection helper: [Build.MANUFACTURER] case-insensitive equals "samsung".
     */
    fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)

    /**
     * The ACTIVE profile's OWN cache location setting (the per-profile store the settings
     * card reads/writes, `preferences` for the base, `preferences_<id>` otherwise).
     * Limitation (frozen): shared items physically follow the BASE profile's location
     * setting, which this advice does not consider.
     */
    internal fun activeProfileCacheLocation(context: Context): ExoPlayerCacheLocation =
        profileDataPrefs(context, getActiveProfile(context))
            .getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)

    /**
     * Total size (bytes) of the ACTIVE profile's downloads + media cache directories (0
     * when a cache is Disabled or a directory is absent). A scan failure or a zero total
     * simply hides the summary line, it never blocks showing the dialog.
     */
    internal fun activeProfileStorageSize(context: Context): Long {
        val profileId = getActiveProfile(context)
        val downloads = downloadsDir(context, profileId)?.let { totalDirectorySize(it) } ?: 0L
        val mediaCache = mediaCacheDir(context, profileId)?.let { totalDirectorySize(it) } ?: 0L
        return downloads + mediaCache
    }

    /**
     * The primary action (switch in place, then dismiss): writes Private to the ACTIVE
     * profile's own location setting (per-profile store, the settings card's semantics,
     * no base-profile switching), restarts the player service when it is bound, then
     * dismisses (the shown flag was already persisted at show time, so it never returns).
     *
     * @param binder the bound player binder, null when the service is not bound; the
     * restart (and its log) only happens when it is non-null.
     */
    internal fun onConfirm(binder: PlayerServiceModern.Binder?) {
        val context = appContext()
        val profileId = getActiveProfile(context)
        profileDataPrefs(context, profileId).edit()
            .putEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.Private)
            .apply()
        if (binder != null) {
            // The log claims the restart, so it is emitted only when the restart actually runs.
            Timber.tag(TAG).i("Switched to private storage, restarting player service")
            binder.restartForegroundOrStop()
        }
        hideDialog()
    }

    /** Interface confirm action: the production wiring is [Buttons], which passes the bound binder. */
    override fun onConfirm() = onConfirm(null)

    @Composable
    override fun Buttons() {
        // Captured during composition: the click handler itself has no composable context
        // to read the local from.
        val binder = LocalPlayerServiceBinder.current
        return ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(R.string.samsung_storage_advice_secondary_button),
                    onClick = ::hideDialog
                ),
                DialogButton(
                    text = stringResource(R.string.samsung_storage_advice_primary_button),
                    onClick = { onConfirm(binder) },
                    primary = true
                )
            )
        )
    }

    @Composable
    override fun DialogBody() {
        val palette = colorPalette()
        BasicText(
            text = stringResource(R.string.samsung_storage_advice_message),
            style = typography().xs.copy(color = palette.text),
            modifier = Modifier.padding(vertical = 20.dp)
        )
        if (storageBytes >= SUMMARY_MIN_BYTES) {
            BasicText(
                text = stringResource(R.string.samsung_storage_advice_storage, formatBytes(storageBytes)),
                style = typography().xs.copy(color = palette.textSecondary),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

/**
 * The one-shot trigger of [SamsungStorageAdviceDialog] (review loop 1 ordering): the pure
 * Samsung check runs BEFORE any I/O (the majority of devices are not Samsung and must not
 * pay for prefs reads), the storage scan runs on [NzikDispatchers.DATA] only when the
 * decision passes, and the shown flag is persisted AFTER [SamsungStorageAdviceDialog.showDialog]
 * (the flag is written when the dialog is SHOWN, so a back-dismiss or a crash with the
 * dialog open never re-shows it).
 *
 * internal + context-taking so the flag-write timing is pinnable under Robolectric without
 * composing MainActivity (the executeProfileSwitch precedent).
 *
 * @param context any context of the process (global app_settings store + active profile settings).
 * @param samsungDetector injectable Samsung check, [SamsungStorageAdviceDialog.isSamsungDevice]
 * in production; pinned by the Robolectric tests.
 */
internal suspend fun triggerSamsungStorageAdvice(
    context: Context,
    samsungDetector: () -> Boolean = SamsungStorageAdviceDialog::isSamsungDevice,
) {
    val isSamsung = samsungDetector()
    if (!isSamsung) return

    val alreadyShown = DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN)
    val cacheLocation = SamsungStorageAdviceDialog.activeProfileCacheLocation(context)
    if (!SamsungStorageAdviceDialog.shouldShow(isSamsung, alreadyShown, cacheLocation)) return

    // A scan failure or a zero total just hides the summary line, it never blocks showing.
    val storageBytes = withContext(NzikDispatchers.DATA) {
        runCatching { SamsungStorageAdviceDialog.activeProfileStorageSize(context) }
            .onFailure { e -> Timber.tag(TAG).w(e, "Storage scan failed, hiding the summary line") }
            .getOrDefault(0L)
    }
    SamsungStorageAdviceDialog.storageBytes = storageBytes
    SamsungStorageAdviceDialog.showDialog()
    // Persisted when the dialog is SHOWN: the "shown once" contract survives a
    // back-dismiss and a crash with the dialog open.
    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN, true)
    Timber.tag(TAG).i("Samsung storage advice shown")
}
