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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.components.menu.menuHeaderBackLeading
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.listentogether.LogEntry
import app.n_zik.android.listentogether.LogLevel
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference

/** Displays recent connection events and copies their localized details. */
@Composable
fun ListenTogetherLogsMenu(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val palette = colorPalette()
    val style by rememberPreference(menuStyleKey, MenuStyle.List)
    val title = stringResource(R.string.listen_together_logs)
    val onCopy = {
        val text = logs.joinToString("\n") { log ->
            buildString {
                append(log.timestamp)
                append(" [")
                append(context.getString(log.level.labelRes))
                append("] ")
                append(context.getString(log.messageRes))
                log.details?.resolve(context.resources)?.let { append(" -- $it") }
            }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(title, text))
        Toaster.i(R.string.listen_together_copied_to_clipboard)
        if (onBack == null) menuState.hide()
    }
    val onClearLogs = {
        onClear()
        if (onBack == null) menuState.hide()
    }
    val copyEntry: @Composable () -> Unit = {
        ListenTogetherManagementEntry(
            style, stringResource(R.string.listen_together_copy), null, R.drawable.copy,
            palette.textSecondary, enabled = logs.isNotEmpty(), onClick = onCopy,
        )
    }
    val clearEntry: @Composable () -> Unit = {
        ListenTogetherManagementEntry(
            style, stringResource(R.string.common_action_clear), null, R.drawable.trash,
            palette.red, enabled = logs.isNotEmpty(), onClick = onClearLogs,
        )
    }
    val empty: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.listen_together_no_logs),
                style = typography().xs,
                color = palette.textSecondary,
            )
        }
    }
    if (style == MenuStyle.List) {
        ListMenu.Menu(title = title, headerLeading = menuHeaderBackLeading(onBack)) {
            if (logs.isEmpty()) empty() else logs.forEach { LogEntryRow(it) }
            copyEntry()
            clearEntry()
        }
    } else {
        GridMenu.Menu(title = title, headerLeading = menuHeaderBackLeading(onBack)) {
            if (logs.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { empty() }
            else items(logs, span = { GridItemSpan(maxLineSpan) }) { LogEntryRow(it) }
            item { copyEntry() }
            item { clearEntry() }
        }
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
    onBack: (() -> Unit)? = null,
) {
    val menuState = LocalMenuState.current
    val palette = colorPalette()
    val style = rememberPreference(menuStyleKey, MenuStyle.List).value
    val title = stringResource(R.string.listen_together_blocked_users)
    val unblockLabel = stringResource(R.string.listen_together_unblock)
    val empty: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.listen_together_no_blocked_users),
                style = typography().xs,
                color = palette.textSecondary,
            )
        }
    }
    val entry: @Composable (String) -> Unit = { username ->
        ListenTogetherManagementEntry(style, username, unblockLabel, R.drawable.close, palette.red) {
            onUnblock(username)
            if (onBack == null) menuState.hide()
        }
    }

    if (style == MenuStyle.List) {
        ListMenu.Menu(title = title, headerLeading = menuHeaderBackLeading(onBack)) {
            if (blockedUsernames.isEmpty()) empty() else blockedUsernames.forEach { entry(it) }
        }
    } else {
        GridMenu.Menu(title = title, headerLeading = menuHeaderBackLeading(onBack)) {
            if (blockedUsernames.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { empty() }
            else items(blockedUsernames.toList(), key = { it }) { entry(it) }
        }
    }
}

@Composable
internal fun ListenTogetherManagementEntry(
    style: MenuStyle,
    label: String,
    subtitle: String?,
    icon: Int,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val entryIcon: @Composable () -> Unit = { SettingIcon(icon, tint) }
    if (style == MenuStyle.List) {
        ListMenu.Entry(text = label, subtitle = subtitle, icon = { entryIcon() }, enabled = enabled, onClick = onClick)
    } else {
        GridMenu.Entry(
            text = label,
            subtitle = subtitle,
            icon = { entryIcon() },
            iconHasOwnBackground = true,
            enabled = enabled,
            onClick = onClick,
        )
    }
}
