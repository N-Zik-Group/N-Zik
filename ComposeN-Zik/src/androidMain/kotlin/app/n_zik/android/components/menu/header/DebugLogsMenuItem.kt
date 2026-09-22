package app.n_zik.android.components.menu.header

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.utils.debug.purgeDebugLogs
import java.io.File

/**
 * Debug log entry for the hamburger menu, visually identical to the standard menu items.
 *
 * material3 1.5.0-alpha28's `DropdownMenuItem` layout is reproduced 1:1 (values verified
 * against the library source): 48dp min height (`MenuListItemContainerHeight`), 12dp
 * horizontal / 0dp vertical content padding, a 24dp leading icon and a 12dp icon-to-text
 * gap (the text Box's start padding). A short tap toggles debug logging with the same
 * side effects as the "Enable debug logs" switch in Misc settings ("restart required"
 * toast on enable, log files purged on disable). A long press calls [onLongClick], used
 * to open the Misc settings screen directly on the Debug card.
 *
 * @param onLongClick called on long press (deep link to the Debug card in Misc settings)
 * @param onConsume called after a short tap so the caller can close the menu
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DebugLogsMenuItem(
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onConsume: () -> Unit = {}
) {
    val context = LocalContext.current
    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    logDebugEnabled = !logDebugEnabled
                    if (logDebugEnabled) {
                        Toaster.i(R.string.restarting_rimusic_is_required)
                    } else {
                        purgeDebugLogs(File(context.filesDir, "logs"))
                    }
                    onConsume()
                },
                onLongClick = onLongClick
            )
            .sizeIn(minHeight = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.bugs),
            contentDescription = null,
            tint = colorPalette().favoritesIcon,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(R.string.enable_log_debug),
            style = MaterialTheme.typography.labelLarge,
            color = colorPalette().textSecondary,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
