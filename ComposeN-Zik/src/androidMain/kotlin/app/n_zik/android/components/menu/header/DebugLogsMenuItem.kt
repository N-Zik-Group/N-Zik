package app.n_zik.android.components.menu.header

import app.n_zik.android.utils.debug.profileDiagnosticsDir
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
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
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.logDebugEnabledKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.menu.DropdownMenuRowFeedback
import app.n_zik.android.components.menu.menuToggleSemantics
import app.n_zik.android.components.ui.LeadingIconBadge
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.header.clampedLabelMaxWidth
import app.n_zik.android.utils.debug.purgeDebugLogs

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
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    val labelStyle = MaterialTheme.typography.labelLarge
    val disableLabel = stringResource(R.string.navigation_action_disable_and_clear_debug_logs)
    val labelMaxWidth = maxOf(
        clampedLabelMaxWidth(stringResource(R.string.recovery_center), labelStyle),
        clampedLabelMaxWidth(disableLabel, labelStyle),
    )
    val label = if (logDebugEnabled) disableLabel else stringResource(R.string.maintenance_debug_logging_title)

    DropdownMenuRowFeedback(
        modifier = modifier.fillMaxWidth().menuToggleSemantics(logDebugEnabled),
        onClick = {
            logDebugEnabled = !logDebugEnabled
            if (logDebugEnabled) {
                Toaster.i(R.string.settings_restart_required_message)
            } else {
                purgeDebugLogs(profileDiagnosticsDir(context))
            }
            onConsume()
        },
        onLongClickLabel = stringResource(R.string.settings_title),
        onLongClick = onLongClick,
    ) { rowModifier ->
        Row(
            modifier = rowModifier
                .sizeIn(minHeight = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LeadingIconBadge(
                iconRes = if (logDebugEnabled) R.drawable.bugs_stop else R.drawable.bugs,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
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
}
