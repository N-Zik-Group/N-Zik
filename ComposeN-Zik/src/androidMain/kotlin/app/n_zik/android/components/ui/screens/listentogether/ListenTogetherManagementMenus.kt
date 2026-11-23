package app.n_zik.android.components.ui.screens.listentogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.listentogether.LogEntry
import app.n_zik.android.listentogether.LogLevel
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState

/** Displays recent connection events and copies their localized details. */
@Composable
fun ListenTogetherLogsMenu(
    logs: List<LogEntry>,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val palette = colorPalette()

    ListMenu.Menu(title = stringResource(R.string.listen_together_logs)) {
        if (logs.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.listen_together_no_logs),
                    style = typography().xs,
                    color = colorPalette().textSecondary,
                )
            }
        } else {
            logs.forEach { log ->
                LogEntryRow(log)
            }
        }

        ListMenu.Entry(
            text = stringResource(R.string.listen_together_copy),
            icon = { SettingIcon(R.drawable.copy, palette.textSecondary) },
            enabled = logs.isNotEmpty(),
            onClick = {
                val textToCopy =
                    logs.joinToString("\n") { log ->
                        buildString {
                            append(log.timestamp)
                            append(" [")
                            append(context.getString(log.level.labelRes))
                            append("] ")
                            append(context.getString(log.messageRes))
                            log.details?.resolve(context.resources)?.let { d -> append(" -- $d") }
                        }
                    }
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.listen_together_logs), textToCopy))
                Toaster.i(R.string.listen_together_copied_to_clipboard)
                menuState.hide()
            },
        )
        ListMenu.Entry(
            text = stringResource(R.string.common_action_clear),
            icon = { SettingIcon(R.drawable.trash, palette.red) },
            enabled = logs.isNotEmpty(),
            onClick = {
                onClear()
                menuState.hide()
            },
        )
    }
}

@Composable
private fun LogEntryRow(log: LogEntry) {
    val palette = colorPalette()
    val levelColor =
        when (log.level) {
            LogLevel.ERROR -> palette.red
            LogLevel.WARNING -> palette.text
            LogLevel.DEBUG -> palette.textSecondary
            LogLevel.INFO -> palette.accent
        }
    val levelIcon =
        when (log.level) {
            LogLevel.ERROR, LogLevel.WARNING -> R.drawable.alert
            LogLevel.DEBUG -> R.drawable.bugs
            LogLevel.INFO -> R.drawable.checkmark
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .padding(vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(levelIcon),
            contentDescription = null,
            tint = levelColor,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(log.messageRes),
                style = typography().xs,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.listen_together_log_entry_metadata, log.timestamp, stringResource(log.level.labelRes)),
                style = typography().xxs,
                fontFamily = FontFamily.Monospace,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            log.details?.resolve(LocalResources.current)?.let { details ->
                Text(
                    text = details,
                    style = typography().xxs,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Lists blocked listeners with an unblock action on each entry. */
@Composable
fun ListenTogetherBlockedUsersMenu(
    blockedUsernames: Set<String>,
    onUnblock: (String) -> Unit,
) {
    val menuState = LocalMenuState.current
    val palette = colorPalette()

    ListMenu.Menu(title = stringResource(R.string.listen_together_blocked_users)) {
        if (blockedUsernames.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.listen_together_no_blocked_users),
                    style = typography().xs,
                    color = palette.textSecondary,
                )
            }
        } else {
            blockedUsernames.forEach { username ->
                ListMenu.Entry(
                    text = username,
                    subtitle = stringResource(R.string.listen_together_unblock),
                    icon = { SettingIcon(R.drawable.close, palette.red) },
                    onClick = {
                        onUnblock(username)
                        menuState.hide()
                    },
                )
            }
        }
    }
}
