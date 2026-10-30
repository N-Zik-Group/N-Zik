package app.n_zik.android.components.menu.header

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.ui.LeadingIconBadge
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.header.clampedLabelMaxWidth
import app.n_zik.android.utils.debug.purgeDebugLogs
import java.io.File

/** Toggles debug logging on tap and opens its settings on long press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DebugLogsMenuItem(
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onConsume: () -> Unit = {}
) {
    val context = LocalContext.current
    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)
    // The "Rescue Center" label is the width reference of the burger menu: this label
    // is clamped to its rendered width and scrolls in a marquee when longer (same
    // width-cap pattern as the header badges' "DEBUG" cap).
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    val labelStyle = MaterialTheme.typography.labelLarge
    val labelMaxWidth = clampedLabelMaxWidth(stringResource(R.string.rescue_center), labelStyle)
    // The label follows the live state so the item reads as reactive: "Enable" while
    // off, "Disable" while on (flips without a restart, like the header badge).
    val labelRes = if (logDebugEnabled) R.string.disable_log_debug else R.string.enable_log_debug

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
        LeadingIconBadge(
            iconRes = R.drawable.bugs,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(labelRes),
            style = labelStyle,
            color = colorPalette().textSecondary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 12.dp)
                .widthIn(max = labelMaxWidth)
                .conditional(!isScrollingTextDisabled) {
                    basicMarquee(iterations = Int.MAX_VALUE)
                }
        )
    }
}
