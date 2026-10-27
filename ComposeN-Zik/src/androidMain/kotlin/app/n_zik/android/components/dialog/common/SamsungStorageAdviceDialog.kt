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

/** Offers private storage once per device, using the active profile's own location setting. */
object SamsungStorageAdviceDialog : ConfirmDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.samsung_storage_advice_title)

    override var isActive: Boolean by mutableStateOf(false)

    /** Scanned active-profile storage size; summaries below [SUMMARY_MIN_BYTES] stay hidden. */
    internal var storageBytes: Long by mutableStateOf(0L)

    /** Shows only for Samsung devices still using system storage that have never seen the advice. */
    fun shouldShow(isSamsung: Boolean, alreadyShown: Boolean, cacheLocation: ExoPlayerCacheLocation): Boolean =
        isSamsung && !alreadyShown && cacheLocation == ExoPlayerCacheLocation.System

    /** Compares [Build.MANUFACTURER] case-insensitively. */
    fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)

    /** Reads the active profile's own setting; shared items still follow the base profile's location. */
    internal fun activeProfileCacheLocation(context: Context): ExoPlayerCacheLocation =
        profileDataPrefs(context, getActiveProfile(context))
            .getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)

    /** Totals the active profile's physical downloads and cache directories, including shared data. */
    internal fun activeProfileStorageSize(context: Context): Long {
        val profileId = getActiveProfile(context)
        val downloads = downloadsDir(context, profileId)?.let { totalDirectorySize(it) } ?: 0L
        val mediaCache = mediaCacheDir(context, profileId)?.let { totalDirectorySize(it) } ?: 0L
        return downloads + mediaCache
    }

    /** Switches the active profile to private storage, restarts the bound player, then dismisses. */
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
            modifier = Modifier.padding(bottom = if (storageBytes >= SUMMARY_MIN_BYTES) 20.dp else 0.dp)
        )
        if (storageBytes >= SUMMARY_MIN_BYTES) {
            BasicText(
                text = stringResource(R.string.samsung_storage_advice_storage, formatBytes(storageBytes)),
                style = typography().xs.copy(color = palette.textSecondary)
            )
        }
    }
}

/** Checks Samsung before any I/O, scans eligible storage off-main, then persists the shown flag. */
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
