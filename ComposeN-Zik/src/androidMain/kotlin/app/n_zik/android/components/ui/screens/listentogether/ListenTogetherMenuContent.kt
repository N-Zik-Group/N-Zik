package app.n_zik.android.components.ui.screens.listentogether

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.menuToggleSemantics
import app.n_zik.android.components.ui.InlineLoader
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.listentogether.JoinRequestPayload
import app.n_zik.android.listentogether.SuggestionReceivedPayload
import app.n_zik.android.listentogether.UserInfo
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.IconButton as ThemeIconButton
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.semiBold

internal class LtMenuContent(
    val connectionState: ConnectionState,
    val currentUserId: String,
    val roomCode: String?,
    val inviteLink: String?,
    val connectedUsers: List<UserInfo>,
    val isHost: Boolean,
    val canEditApprovals: Boolean,
    val waiting: Boolean,
    val joinErrorMessage: String?,
    val pendingJoinRequests: List<JoinRequestPayload>,
    val pendingSuggestions: List<SuggestionReceivedPayload>,
    val serverLabel: String,
    val usernameLabel: String,
    val autoApprovalJoins: Boolean,
    val autoApproveSuggestions: Boolean,
    val syncHostVolume: Boolean,
    val historyEnabled: Boolean,
    val blockedLabel: String,
    val onConnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onReconnect: () -> Unit,
    val onCopyCode: () -> Unit,
    val onCopyLink: () -> Unit,
    val onUserClick: (String, String) -> Unit,
    val onApproveJoin: (String) -> Unit,
    val onRejectJoin: (String) -> Unit,
    val onApproveSuggestion: (String) -> Unit,
    val onRejectSuggestion: (String) -> Unit,
    val onLeaveRoom: () -> Unit,
    val onCreateRoom: () -> Unit,
    val onJoinRoom: () -> Unit,
    val onCancelWaiting: () -> Unit,
    val onOpenServerChooser: () -> Unit,
    val onOpenUsernameSheet: () -> Unit,
    val onOpenLogs: () -> Unit,
    val onOpenBlockedUsers: () -> Unit,
    val onToggleAutoApprovalJoins: () -> Unit,
    val onToggleAutoApproveSuggestions: () -> Unit,
    val onToggleSyncVolume: () -> Unit,
    val onToggleHistory: () -> Unit,
)

@Composable
internal fun ListLtMenuContent(content: LtMenuContent) {
    val palette = colorPalette()
    val waitingForApprovalText = stringResource(R.string.listen_together_waiting_for_approval)

    MenuSectionTitle(stringResource(R.string.listen_together_connection))
    ConnectionStatusRow(content.connectionState)
    when (content.connectionState) {
        ConnectionState.DISCONNECTED,
        ConnectionState.ERROR ->
            ListMenu.Entry(
                text = stringResource(R.string.listen_together_connect),
                icon = { SettingIcon(R.drawable.link, palette.accent) },
                onClick = content.onConnect,
            )

        ConnectionState.CONNECTING,
        ConnectionState.RECONNECTING -> LoadingRow()

        ConnectionState.CONNECTED -> {
            ListMenu.Entry(
                text = stringResource(R.string.listen_together_reconnect),
                icon = { SettingIcon(R.drawable.link, palette.accent) },
                onClick = content.onReconnect,
            )
            ListMenu.Entry(
                text = stringResource(R.string.listen_together_disconnect),
                icon = { SettingIcon(R.drawable.logout, palette.red) },
                onClick = content.onDisconnect,
            )
        }
    }

    if (content.roomCode != null) {
        MenuSectionTitle(stringResource(R.string.listen_together_room_code))
        ListMenu.Entry(
            text = stringResource(R.string.listen_together_copy_code),
            subtitle = content.roomCode,
            icon = { SettingIcon(R.drawable.copy, palette.accent) },
            onClick = content.onCopyCode,
        )
        ListMenu.Entry(
            text = stringResource(R.string.listen_together_copy_link),
            subtitle = content.inviteLink,
            icon = { SettingIcon(R.drawable.link, palette.accent) },
            onClick = content.onCopyLink,
        )

        MenuSectionTitle(
            "${stringResource(R.string.listen_together_connected_users)} (${content.connectedUsers.size})",
        )
        content.connectedUsers.forEach { user ->
            ListMenu.Entry(
                text = user.username,
                subtitle =
                    when {
                        user.isHost -> stringResource(R.string.listen_together_host_label)
                        user.userId == content.currentUserId ->
                            stringResource(R.string.listen_together_you_label)
                        else -> null
                    },
                icon = {
                    SettingIcon(
                        if (user.isHost) R.drawable.star_brilliant else R.drawable.person,
                        if (user.isHost) palette.accent else palette.textSecondary,
                    )
                },
                onClick = if (content.isHost && user.userId != content.currentUserId) {
                    { content.onUserClick(user.userId, user.username) }
                } else null,
            )
        }

        if (content.isHost && content.pendingJoinRequests.isNotEmpty()) {
            MenuSectionTitle(stringResource(R.string.listen_together_join_requests))
            content.pendingJoinRequests.forEach { request ->
                ListMenu.Entry(
                    text = request.username,
                    icon = { SettingIcon(R.drawable.person, palette.accent) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ThemeIconButton(
                                onClick = { content.onApproveJoin(request.userId) },
                                contentDescription = stringResource(R.string.listen_together_action_approve),
                                icon = R.drawable.checkmark,
                                color = palette.accent,
                                modifier = Modifier.size(36.dp),
                            )
                            ThemeIconButton(
                                onClick = { content.onRejectJoin(request.userId) },
                                contentDescription = stringResource(R.string.listen_together_action_reject),
                                icon = R.drawable.close,
                                color = palette.red,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
            }
        }

        if (content.isHost && content.pendingSuggestions.isNotEmpty()) {
            MenuSectionTitle(stringResource(R.string.listen_together_pending_suggestions))
            content.pendingSuggestions.forEach { suggestion ->
                ListMenu.Entry(
                    text = suggestion.trackInfo.title.ifBlank { stringResource(R.string.metadata_unknown_title) },
                    subtitle = suggestion.fromUsername,
                    icon = { SettingIcon(R.drawable.musical_notes, palette.accent) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ThemeIconButton(
                                onClick = { content.onApproveSuggestion(suggestion.suggestionId) },
                                contentDescription = stringResource(R.string.listen_together_action_approve),
                                icon = R.drawable.checkmark,
                                color = palette.accent,
                                modifier = Modifier.size(36.dp),
                            )
                            ThemeIconButton(
                                onClick = { content.onRejectSuggestion(suggestion.suggestionId) },
                                contentDescription = stringResource(R.string.listen_together_action_reject),
                                icon = R.drawable.close,
                                color = palette.red,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
            }
        }

        ListMenu.Entry(
            text = stringResource(R.string.listen_together_leave_room),
            icon = { SettingIcon(R.drawable.logout, palette.red) },
            onClick = content.onLeaveRoom,
        )
    } else {
        MenuSectionTitle(stringResource(R.string.listen_together_room))
        if (content.waiting) {
            WaitingApprovalRow(waitingForApprovalText)
            ListMenu.Entry(
                text = stringResource(R.string.common_action_cancel),
                icon = { SettingIcon(R.drawable.close, palette.red) },
                onClick = content.onCancelWaiting,
            )
        } else {
            ListMenu.Entry(
                text = stringResource(R.string.listen_together_create_room),
                subtitle = stringResource(R.string.listen_together_create_room_description),
                icon = { SettingIcon(R.drawable.people, palette.accent) },
                onClick = content.onCreateRoom,
            )
            ListMenu.Entry(
                text = stringResource(R.string.listen_together_join_room),
                subtitle = stringResource(R.string.listen_together_enter_room_code),
                icon = { SettingIcon(R.drawable.people, palette.accent) },
                onClick = content.onJoinRoom,
            )
        }
        content.joinErrorMessage?.let { JoinErrorBox(it) }
    }

    MenuSectionTitle(stringResource(R.string.settings_title))
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_server_url),
        subtitle = content.serverLabel,
        icon = { SettingIcon(R.drawable.server, palette.accent) },
        onClick = content.onOpenServerChooser,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_username),
        subtitle = content.usernameLabel,
        icon = { SettingIcon(R.drawable.person, palette.accent) },
        onClick = content.onOpenUsernameSheet,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_auto_approval_joins),
        subtitle = stringResource(R.string.listen_together_auto_approval_joins_description),
        icon = { SettingIcon(R.drawable.checkmark, palette.accent) },
        enabled = content.canEditApprovals,
        modifier = Modifier.menuToggleSemantics(content.autoApprovalJoins),
        trailingContent = { ToggleSwitch(content.autoApprovalJoins) },
        onClick = content.onToggleAutoApprovalJoins,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_auto_approval_suggestions),
        subtitle = stringResource(R.string.listen_together_auto_approval_suggestions_description),
        icon = { SettingIcon(R.drawable.musical_notes, palette.accent) },
        enabled = content.canEditApprovals,
        modifier = Modifier.menuToggleSemantics(content.autoApproveSuggestions),
        trailingContent = { ToggleSwitch(content.autoApproveSuggestions) },
        onClick = content.onToggleAutoApproveSuggestions,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_sync_volume),
        subtitle = stringResource(R.string.listen_together_sync_volume_description),
        icon = { SettingIcon(R.drawable.volume_up, palette.accent) },
        modifier = Modifier.menuToggleSemantics(content.syncHostVolume),
        trailingContent = { ToggleSwitch(content.syncHostVolume) },
        onClick = content.onToggleSyncVolume,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_history),
        subtitle = stringResource(R.string.listen_together_history_description),
        icon = { SettingIcon(R.drawable.history, palette.accent) },
        modifier = Modifier.menuToggleSemantics(content.historyEnabled),
        trailingContent = { ToggleSwitch(content.historyEnabled) },
        onClick = content.onToggleHistory,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_view_logs),
        subtitle = stringResource(R.string.listen_together_view_logs_description),
        icon = { SettingIcon(R.drawable.bugs, palette.accent) },
        onClick = content.onOpenLogs,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_blocked_users),
        subtitle = content.blockedLabel,
        icon = { SettingIcon(R.drawable.close, palette.red) },
        onClick = content.onOpenBlockedUsers,
    )
}

internal fun LazyGridScope.GridLtMenuContent(content: LtMenuContent, palette: ColorPalette) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        MenuSectionTitle(stringResource(R.string.listen_together_connection))
    }
    item(span = { GridItemSpan(maxLineSpan) }) {
        ConnectionStatusRow(content.connectionState)
    }
    when (content.connectionState) {
        ConnectionState.DISCONNECTED,
        ConnectionState.ERROR ->
            item {
                GridMenu.Entry(
                    text = stringResource(R.string.listen_together_connect),
                    icon = { GridEntryIcon(R.drawable.link, palette.accent) },
                    onClick = content.onConnect,
                )
            }

        ConnectionState.CONNECTING,
        ConnectionState.RECONNECTING ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingRow()
            }

        ConnectionState.CONNECTED -> {
            item {
                GridMenu.Entry(
                    text = stringResource(R.string.listen_together_reconnect),
                    icon = { GridEntryIcon(R.drawable.link, palette.accent) },
                    onClick = content.onReconnect,
                )
            }
            item {
                GridMenu.Entry(
                    text = stringResource(R.string.listen_together_disconnect),
                    icon = { GridEntryIcon(R.drawable.logout, palette.red) },
                    onClick = content.onDisconnect,
                )
            }
        }
    }

    if (content.roomCode != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MenuSectionTitle(stringResource(R.string.listen_together_room_code))
        }
        item {
            GridMenu.Entry(
                text = stringResource(R.string.listen_together_copy_code),
                subtitle = content.roomCode,
                icon = { GridEntryIcon(R.drawable.copy, palette.accent) },
                onClick = content.onCopyCode,
            )
        }
        item {
            GridMenu.Entry(
                text = stringResource(R.string.listen_together_copy_link),
                subtitle = content.inviteLink,
                icon = { GridEntryIcon(R.drawable.link, palette.accent) },
                onClick = content.onCopyLink,
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            MenuSectionTitle(
                "${stringResource(R.string.listen_together_connected_users)} (${content.connectedUsers.size})",
            )
        }
        content.connectedUsers.forEach { user ->
            item {
                GridMenu.Entry(
                    text = user.username,
                    subtitle =
                        when {
                            user.isHost -> stringResource(R.string.listen_together_host_label)
                            user.userId == content.currentUserId ->
                                stringResource(R.string.listen_together_you_label)
                            else -> null
                        },
                    icon = {
                        GridEntryIcon(
                            if (user.isHost) R.drawable.star_brilliant else R.drawable.person,
                            if (user.isHost) palette.accent else palette.textSecondary,
                        )
                    },
                    onClick = if (content.isHost && user.userId != content.currentUserId) {
                        { content.onUserClick(user.userId, user.username) }
                    } else null,
                )
            }
        }

        if (content.isHost && content.pendingJoinRequests.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.listen_together_join_requests))
            }
            content.pendingJoinRequests.forEach { request ->
                item {
                    GridMenu.Entry(
                        text = request.username,
                        icon = { GridEntryIcon(R.drawable.person, palette.accent) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                ThemeIconButton(
                                    onClick = { content.onApproveJoin(request.userId) },
                                    contentDescription = stringResource(R.string.listen_together_action_approve),
                                    icon = R.drawable.checkmark,
                                    color = palette.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                                ThemeIconButton(
                                    onClick = { content.onRejectJoin(request.userId) },
                                    contentDescription = stringResource(R.string.listen_together_action_reject),
                                    icon = R.drawable.close,
                                    color = palette.red,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        if (content.isHost && content.pendingSuggestions.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.listen_together_pending_suggestions))
            }
            content.pendingSuggestions.forEach { suggestion ->
                item {
                    GridMenu.Entry(
                        text = suggestion.trackInfo.title.ifBlank { stringResource(R.string.metadata_unknown_title) },
                        subtitle = suggestion.fromUsername,
                        icon = { GridEntryIcon(R.drawable.musical_notes, palette.accent) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                ThemeIconButton(
                                    onClick = { content.onApproveSuggestion(suggestion.suggestionId) },
                                    contentDescription = stringResource(R.string.listen_together_action_approve),
                                    icon = R.drawable.checkmark,
                                    color = palette.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                                ThemeIconButton(
                                    onClick = { content.onRejectSuggestion(suggestion.suggestionId) },
                                    contentDescription = stringResource(R.string.listen_together_action_reject),
                                    icon = R.drawable.close,
                                    color = palette.red,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        item {
            GridMenu.Entry(
                text = stringResource(R.string.listen_together_leave_room),
                icon = { GridEntryIcon(R.drawable.logout, palette.red) },
                onClick = content.onLeaveRoom,
            )
        }
    } else {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MenuSectionTitle(stringResource(R.string.listen_together_room))
        }
        if (content.waiting) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                WaitingApprovalRow(stringResource(R.string.listen_together_waiting_for_approval))
            }
            item {
                GridMenu.Entry(
                    text = stringResource(R.string.common_action_cancel),
                    icon = { GridEntryIcon(R.drawable.close, palette.red) },
                    onClick = content.onCancelWaiting,
                )
            }
        } else {
            item {
                GridMenu.Entry(
                    text = stringResource(R.string.listen_together_create_room),
                    subtitle = stringResource(R.string.listen_together_create_room_description),
                    icon = { GridEntryIcon(R.drawable.people, palette.accent) },
                    onClick = content.onCreateRoom,
                )
            }
            item {
                GridMenu.Entry(
                    text = stringResource(R.string.listen_together_join_room),
                    subtitle = stringResource(R.string.listen_together_enter_room_code),
                    icon = { GridEntryIcon(R.drawable.people, palette.accent) },
                    onClick = content.onJoinRoom,
                )
            }
        }
        content.joinErrorMessage?.let { message ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                JoinErrorBox(message)
            }
        }
    }

    item(span = { GridItemSpan(maxLineSpan) }) {
        MenuSectionTitle(stringResource(R.string.settings_title))
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_server_url),
            subtitle = content.serverLabel,
            icon = { GridEntryIcon(R.drawable.server, palette.accent) },
            onClick = content.onOpenServerChooser,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_username),
            subtitle = content.usernameLabel,
            icon = { GridEntryIcon(R.drawable.person, palette.accent) },
            onClick = content.onOpenUsernameSheet,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_auto_approval_joins),
            icon = { GridEntryIcon(R.drawable.checkmark, palette.accent) },
            enabled = content.canEditApprovals,
            modifier = Modifier.menuToggleSemantics(content.autoApprovalJoins),
            trailingContent = { ToggleSwitch(content.autoApprovalJoins) },
            onClick = content.onToggleAutoApprovalJoins,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_auto_approval_suggestions),
            icon = { GridEntryIcon(R.drawable.musical_notes, palette.accent) },
            enabled = content.canEditApprovals,
            modifier = Modifier.menuToggleSemantics(content.autoApproveSuggestions),
            trailingContent = { ToggleSwitch(content.autoApproveSuggestions) },
            onClick = content.onToggleAutoApproveSuggestions,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_sync_volume),
            icon = { GridEntryIcon(R.drawable.volume_up, palette.accent) },
            modifier = Modifier.menuToggleSemantics(content.syncHostVolume),
            trailingContent = { ToggleSwitch(content.syncHostVolume) },
            onClick = content.onToggleSyncVolume,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_history),
            icon = { GridEntryIcon(R.drawable.history, palette.accent) },
            modifier = Modifier.menuToggleSemantics(content.historyEnabled),
            trailingContent = { ToggleSwitch(content.historyEnabled) },
            onClick = content.onToggleHistory,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_view_logs),
            icon = { GridEntryIcon(R.drawable.bugs, palette.accent) },
            onClick = content.onOpenLogs,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_blocked_users),
            subtitle = content.blockedLabel,
            icon = { GridEntryIcon(R.drawable.close, palette.red) },
            onClick = content.onOpenBlockedUsers,
        )
    }
}

@Composable
private fun GridEntryIcon(@DrawableRes icon: Int, color: Color) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun MenuSectionTitle(title: String) {
    BasicText(
        text = title,
        style = typography().xxs.semiBold.copy(
            color = colorPalette().accent,
            textAlign = TextAlign.Start,
        ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
private fun ConnectionStatusRow(connectionState: ConnectionState) {
    val palette = colorPalette()
    val stateColor =
        when (connectionState) {
            ConnectionState.CONNECTED -> palette.accent
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> palette.accent.copy(alpha = 0.6f)
            ConnectionState.ERROR -> palette.red
            ConnectionState.DISCONNECTED -> palette.textDisabled
        }
    val statusText =
        when (connectionState) {
            ConnectionState.CONNECTED -> stringResource(R.string.listen_together_connected)
            ConnectionState.CONNECTING -> stringResource(R.string.listen_together_connecting)
            ConnectionState.RECONNECTING -> stringResource(R.string.listen_together_reconnecting)
            ConnectionState.ERROR -> stringResource(R.string.listen_together_error)
            ConnectionState.DISCONNECTED -> stringResource(R.string.listen_together_disconnected)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(stateColor),
        )
        Text(
            text = statusText,
            style = typography().s.semiBold,
            color = stateColor,
        )
    }
}

@Composable
private fun LoadingRow() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
    ) {
        InlineLoader()
    }
}

@Composable
private fun WaitingApprovalRow(waitingText: String) {
    val palette = colorPalette()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        InlineLoader()
        Text(
            text = waitingText,
            style = typography().s.semiBold,
            color = palette.accent,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun JoinErrorBox(message: String) {
    val palette = colorPalette()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(palette.red.copy(alpha = 0.12f), uiRoundnessShape()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.alert),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.red,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = message,
                style = typography().xs,
                color = palette.red,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean) {
    val palette = colorPalette()
    Switch(
        checked = checked,
        onCheckedChange = null,
        checkedThumbColor = palette.textSecondary,
        checkedTrackColor = palette.accent.copy(alpha = 0.3f),
        uncheckedThumbColor = palette.textSecondary,
        uncheckedTrackColor = palette.textSecondary.copy(alpha = 0.3f),
    )
}
