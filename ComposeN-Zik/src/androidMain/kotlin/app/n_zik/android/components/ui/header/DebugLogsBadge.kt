package app.n_zik.android.components.ui.header

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.utils.debug.purgeDebugLogs
import java.io.File

/**
 * Reactive "DEBUG" badge for the app header (title = [HEADER_BADGE_REF_TITLE], which is
 * also the width reference capping every header badge).
 *
 * Only visible while debug logging is enabled. The preference is read live —
 * [rememberPreference] seeds the value from disk and registers a shared-preferences
 * listener, which matters because this header stays composed across the whole session, so
 * a one-shot read would keep a stale value after a flip in the settings or the burger menu.
 *
 * A tap toggles the preference with the exact same side effects as the "Enable debug logs"
 * item in the burger menu: a "restart required" toast on enable, a purge of the debug log
 * files on disable. Since the badge is hidden while off, a tap on it can only ever disable
 * the mode (re-enabling goes through the burger menu or Misc settings).
 *
 * @param modifier applied to the badge container
 */
@Composable
fun DebugLogsBadge(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)

    if (!logDebugEnabled) return

    HeaderBadgeBox(
        text = HEADER_BADGE_REF_TITLE,
        color = colorPalette().red,
        onToggle = {
            logDebugEnabled = !logDebugEnabled
            if (logDebugEnabled) {
                Toaster.i(R.string.restarting_rimusic_is_required)
            } else {
                purgeDebugLogs(File(context.filesDir, "logs"))
            }
        },
        modifier = modifier
    )
}
