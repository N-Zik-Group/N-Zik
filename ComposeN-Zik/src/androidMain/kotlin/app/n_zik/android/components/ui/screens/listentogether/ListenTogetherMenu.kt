// Popup "Listen Together" menu (port of the Metrolist `ListenTogetherDialog` in
// PlayerMenu.kt), shown through the app's shared menu state from the item menus
// (song / video / player) via menuState.display { }: the entry point to create or
// join a room, or to manage the current one (room code, connected users with host
// kick/block/transfer, pending join requests / suggestions, leave room) plus the
// full room-page settings (server URL, username, auto-approvals, volume sync,
// history, logs, blocked users).
//
// Design (per user request): styled exactly like the app's own item menus —
// follows the app's own "menu style" preference (MenuStyle): list mode renders
// [ListMenu.Menu] + accent section titles + [ListMenu.Entry] rows; grid mode
// renders [GridMenu.Menu] + full-span section titles + [GridMenu.Entry] cells —
// no cards in either mode. Form inputs (create room, join room, server URL,
// username) follow the app's own form-sheet pattern (CustomModalBottomSheet +
// ListMenu.Menu + app field colors + full-width accent button), same as the
// settings card's sheets.
package app.n_zik.android.components.ui.screens.listentogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.IconButton as ThemeIconButton
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.settings.ServerChooserSheet
import app.n_zik.android.components.settings.UsernameSheet
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.listentogether.JoinRequestPayload
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherEvent
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.ListenTogetherServers
import app.n_zik.android.listentogether.RoomRole
import app.n_zik.android.listentogether.SuggestionReceivedPayload
import app.n_zik.android.listentogether.UserInfo
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.DataStoreUtils
import timber.log.Timber

private const val TAG = "ListenTogetherMenu"

/**
 * Listen Together popup rendered through the app's shared menu state, styled like
 * the app's own item menus: follows the app's "menu style" preference — list rows
 * ([ListMenu.Menu]/[ListMenu.Entry]) or grid cells ([GridMenu.Menu]/[GridMenu.Entry]).
 * Content: connection, room (create/join or management), settings. Create/join
 * inputs open app form sheets; server/username reuse the settings card's sheets;
 * logs/blocked users/user actions reuse the app's nested menu displays.
 */
@Composable
fun ListenTogetherMenu() {
    val menuState = LocalMenuState.current
    val context = LocalContext.current
    val listenTogetherManager = remember { ListenTogetherManager.getInstance() }
    val usernamePref = rememberUsernamePreference(context)

    if (listenTogetherManager == null) {
        Timber.tag(TAG).w("Manager not initialized, cannot render Listen Together menu")
        return
    }

    val connectionState by listenTogetherManager.connectionState.collectAsStateWithLifecycle()
    val roomState by listenTogetherManager.roomState.collectAsStateWithLifecycle()
    val userId by listenTogetherManager.userId.collectAsStateWithLifecycle()
    val role by listenTogetherManager.role.collectAsStateWithLifecycle()
    val pendingJoinRequests by listenTogetherManager.pendingJoinRequests.collectAsStateWithLifecycle()
    val pendingSuggestions by listenTogetherManager.pendingSuggestions.collectAsStateWithLifecycle()
    val logs by listenTogetherManager.logs.collectAsStateWithLifecycle()
    val blockedUsernames by listenTogetherManager.blockedUsernames.collectAsStateWithLifecycle()
    val syncHostVolume by listenTogetherManager.syncHostVolumeEnabled.collectAsStateWithLifecycle()

    var isCreatingRoom by rememberSaveable { mutableStateOf(false) }
    var isJoiningRoom by rememberSaveable { mutableStateOf(false) }
    var joinErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    var showCreateRoomSheet by rememberSaveable { mutableStateOf(false) }
    var showJoinRoomSheet by rememberSaveable { mutableStateOf(false) }
    var showServerChooserSheet by rememberSaveable { mutableStateOf(false) }
    var showUsernameSheet by rememberSaveable { mutableStateOf(false) }

    val invalidRoomCodeText = stringResource(R.string.listen_together_invalid_room_code)
    val joinRequestDeniedText = stringResource(R.string.listen_together_join_request_denied)
    val connectionLostText = stringResource(R.string.listen_together_error)

    LaunchedEffect(listenTogetherManager) {
        listenTogetherManager.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinRejected -> {
                    val message = joinRejectionMessage(event.reason, joinRequestDeniedText, invalidRoomCodeText)
                    joinErrorMessage = message
                    isJoiningRoom = false
                    isCreatingRoom = false
                    // Covers the banned-user case ("You are blocked") and any denial reason
                    Toaster.e(message)
                }

                is ListenTogetherEvent.JoinApproved -> {
                    isJoiningRoom = false
                    joinErrorMessage = null
                }

                is ListenTogetherEvent.RoomCreated -> {
                    isCreatingRoom = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ListenTogetherRoom", event.roomCode))
                }

                // A failure must end the waiting state instead of leaving it stuck:
                // same branches as the room screen.
                is ListenTogetherEvent.ServerError -> {
                    if (isCreatingRoom || isJoiningRoom) {
                        isCreatingRoom = false
                        isJoiningRoom = false
                        joinErrorMessage = event.message
                        Toaster.e(event.message)
                    }
                }

                is ListenTogetherEvent.ConnectionError -> {
                    if (isCreatingRoom || isJoiningRoom) {
                        isCreatingRoom = false
                        isJoiningRoom = false
                        joinErrorMessage = event.error
                        Toaster.e(event.error)
                    }
                }

                is ListenTogetherEvent.Disconnected -> {
                    if (isCreatingRoom || isJoiningRoom) {
                        isCreatingRoom = false
                        isJoiningRoom = false
                        joinErrorMessage = connectionLostText
                    }
                }

                else -> {}
            }
        }
    }

    val isInRoom = listenTogetherManager.isInRoom
    val isHost = roomState?.hostId == userId
    // Guests cannot change host-side approvals while in a room (hosts always can)
    val canEditApprovals = !isInRoom || role != RoomRole.GUEST
    val waiting = isCreatingRoom || isJoiningRoom

    // DataStoreUtils reads are one-shot (plain SharedPreferences): bump the version
    // after every save so the entry texts and toggle states refresh right away.
    val settingsVersion = remember { mutableStateOf(0) }
    val serverUrl =
        remember(settingsVersion.value) {
            DataStoreUtils.getString(
                context,
                ListenTogetherClient.PREF_SERVER_URL,
                ListenTogetherServers.defaultServerUrl,
            )
        }
    val username =
        remember(settingsVersion.value) { DataStoreUtils.getString(context, PREF_USERNAME) }
    val autoApprovalJoins =
        remember(settingsVersion.value) {
            DataStoreUtils.getBoolean(context, ListenTogetherClient.PREF_AUTO_APPROVAL, false)
        }
    val autoApproveSuggestions =
        remember(settingsVersion.value) {
            DataStoreUtils.getBoolean(context, ListenTogetherClient.PREF_AUTO_APPROVE_SUGGESTIONS, false)
        }
    val historyEnabled =
        remember(settingsVersion.value) {
            DataStoreUtils.getBoolean(context, ListenTogetherClient.PREF_HISTORY, false)
        }

    val selectedServer = remember(serverUrl) { ListenTogetherServers.findByUrl(serverUrl) }
    // App menu style preference (same key as the app's own item menus)
    val menuStyle by rememberPreference(menuStyleKey, MenuStyle.List)

    val roomCode = roomState?.roomCode
    val connectedUsers = roomState?.usersList?.filter { it.isConnected }.orEmpty()
    val currentUserId = userId ?: ""

    val content =
        LtMenuContent(
            connectionState = connectionState,
            currentUserId = currentUserId,
            roomCode = roomCode,
            inviteLink = roomCode?.let { "$LISTEN_INVITE_LINK_BASE?code=$it" },
            connectedUsers = connectedUsers,
            isHost = isHost,
            canEditApprovals = canEditApprovals,
            waiting = waiting,
            joinErrorMessage = joinErrorMessage,
            pendingJoinRequests = pendingJoinRequests,
            pendingSuggestions = pendingSuggestions,
            serverLabel = selectedServer?.let { server -> "${server.name} — ${server.location}" } ?: serverUrl,
            usernameLabel = username.ifEmpty { stringResource(R.string.listen_together_not_set) },
            autoApprovalJoins = autoApprovalJoins,
            autoApproveSuggestions = autoApproveSuggestions,
            syncHostVolume = syncHostVolume,
            historyEnabled = historyEnabled,
            blockedLabel =
                if (blockedUsernames.isNotEmpty()) {
                    stringResource(R.string.listen_together_blocked_users_count, blockedUsernames.size)
                } else {
                    stringResource(R.string.listen_together_no_blocked_users)
                },
            onConnect = { listenTogetherManager.connect() },
            onDisconnect = { listenTogetherManager.disconnect() },
            onReconnect = { listenTogetherManager.forceReconnect() },
            onCopyCode = {
                roomCode?.let { code ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Room Code", code))
                    Toaster.i(R.string.listen_together_copied_to_clipboard)
                }
            },
            onCopyLink = {
                roomCode?.let { code ->
                    val link = "$LISTEN_INVITE_LINK_BASE?code=$code"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Listen Together Invite Link", link),
                    )
                    Toaster.i(R.string.listen_together_copied_to_clipboard)
                }
            },
            onUserClick = { clickedUserId, usernameToManage ->
                if (isHost && clickedUserId != currentUserId) {
                    // App menu mechanism (same as the app's own item menus)
                    menuState.display {
                        UserActionMenu(
                            username = usernameToManage,
                            onKick = { listenTogetherManager.kickUser(clickedUserId, "Removed by host") },
                            onPermanentKick = {
                                listenTogetherManager.blockUser(usernameToManage)
                                listenTogetherManager.kickUser(
                                    clickedUserId,
                                    context.getString(R.string.listen_together_user_blocked_by_host),
                                )
                            },
                            onTransferOwnership = { listenTogetherManager.transferHost(clickedUserId) },
                        )
                    }
                }
            },
            onApproveJoin = { listenTogetherManager.approveJoin(it) },
            onRejectJoin = { listenTogetherManager.rejectJoin(it, "Rejected by host") },
            onApproveSuggestion = { listenTogetherManager.approveSuggestion(it) },
            onRejectSuggestion = { listenTogetherManager.rejectSuggestion(it, "Rejected by host") },
            onLeaveRoom = { listenTogetherManager.leaveRoom() },
            onCreateRoom = { showCreateRoomSheet = true },
            onJoinRoom = { showJoinRoomSheet = true },
            onCancelWaiting = {
                listenTogetherManager.disconnect()
                isJoiningRoom = false
                isCreatingRoom = false
                joinErrorMessage = null
            },
            onOpenServerChooser = { showServerChooserSheet = true },
            onOpenUsernameSheet = {
                if (isInRoom) {
                    Toaster.i(R.string.listen_together_cannot_edit_username_in_room)
                } else {
                    showUsernameSheet = true
                }
            },
            onOpenLogs = {
                menuState.display {
                    ListenTogetherLogsMenu(logs = logs, onClear = { listenTogetherManager.clearLogs() })
                }
            },
            onOpenBlockedUsers = {
                menuState.display {
                    ListenTogetherBlockedUsersMenu(
                        blockedUsernames = blockedUsernames,
                        onUnblock = { listenTogetherManager.unblockUser(it) },
                    )
                }
            },
            onToggleAutoApprovalJoins = {
                DataStoreUtils.saveBoolean(context, ListenTogetherClient.PREF_AUTO_APPROVAL, !autoApprovalJoins)
                settingsVersion.value++
                Timber.tag(TAG).i("Auto-approve joins -> ${!autoApprovalJoins}")
            },
            onToggleAutoApproveSuggestions = {
                DataStoreUtils.saveBoolean(
                    context,
                    ListenTogetherClient.PREF_AUTO_APPROVE_SUGGESTIONS,
                    !autoApproveSuggestions,
                )
                settingsVersion.value++
                Timber.tag(TAG).i("Auto-approve suggestions -> ${!autoApproveSuggestions}")
            },
            onToggleSyncVolume = { listenTogetherManager.setSyncHostVolumeEnabled(!syncHostVolume) },
            onToggleHistory = {
                DataStoreUtils.saveBoolean(context, ListenTogetherClient.PREF_HISTORY, !historyEnabled)
                settingsVersion.value++
                Timber.tag(TAG).i("Local history -> ${!historyEnabled}")
            },
        )

    val palette = colorPalette()

    if (menuStyle == MenuStyle.List) {
        ListMenu.Menu(title = stringResource(R.string.listen_together)) {
            ListLtMenuContent(content)
        }
    } else {
        // The grid menu's content lambda is not a composable context (GridMenu.Menu's
        // `content` param carries no @Composable annotation, unlike ListMenu.Menu),
        // so the items are emitted by a plain function; composable calls only happen
        // inside the `item { }` lambdas, which are composable.
        GridMenu.Menu(title = stringResource(R.string.listen_together)) {
            GridLtMenuContent(content, palette)
        }
    }

    // Form sheets (app form-sheet pattern, same as the settings card's sheets)
    CreateRoomSheet(
        showSheet = showCreateRoomSheet,
        savedUsername = username,
        onConfirm = { finalUsername ->
            usernamePref.value = finalUsername
            DataStoreUtils.saveString(context, PREF_USERNAME, finalUsername)
            showCreateRoomSheet = false
            Toaster.i(R.string.listen_together_creating_room)
            isCreatingRoom = true
            isJoiningRoom = false
            joinErrorMessage = null
            listenTogetherManager.connect()
            listenTogetherManager.createRoom(finalUsername)
        },
        onDismiss = { showCreateRoomSheet = false },
    )
    JoinRoomSheet(
        showSheet = showJoinRoomSheet,
        savedUsername = username,
        onConfirm = { roomCodeInput, finalUsername ->
            usernamePref.value = finalUsername
            DataStoreUtils.saveString(context, PREF_USERNAME, finalUsername)
            showJoinRoomSheet = false
            Toaster.i(R.string.listen_together_joining_room, roomCodeInput)
            isJoiningRoom = true
            isCreatingRoom = false
            joinErrorMessage = null
            listenTogetherManager.connect()
            listenTogetherManager.joinRoom(roomCodeInput, finalUsername)
        },
        onDismiss = { showJoinRoomSheet = false },
    )
    ServerChooserSheet(
        showSheet = showServerChooserSheet,
        onSaved = { settingsVersion.value++ },
        onDismiss = { showServerChooserSheet = false },
        currentUrl = serverUrl,
    )
    UsernameSheet(
        showSheet = showUsernameSheet,
        onSaved = { settingsVersion.value++ },
        onDismiss = { showUsernameSheet = false },
        savedUsername = username,
    )
}

/**
 * Shared state + callbacks for the list and grid renderings of the LT menu.
 */
private class LtMenuContent(
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

/**
 * List rendering: [ListMenu.Menu] + accent section titles + [ListMenu.Entry] rows
 * (same design as the app's own list item menus).
 */
@Composable
private fun ListLtMenuContent(content: LtMenuContent) {
    val palette = colorPalette()
    val waitingForApprovalText = stringResource(R.string.listen_together_waiting_for_approval)

    // ── Connection ──────────────────────────────────────────────────────────
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
        // ── Room code ───────────────────────────────────────────────────────
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

        // ── Connected users ─────────────────────────────────────────────────
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
                onClick = { content.onUserClick(user.userId, user.username) },
            )
        }

        // ── Join requests (host only) ───────────────────────────────────────
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
                                icon = R.drawable.checkmark,
                                color = palette.accent,
                                modifier = Modifier.size(36.dp),
                            )
                            ThemeIconButton(
                                onClick = { content.onRejectJoin(request.userId) },
                                icon = R.drawable.close,
                                color = palette.red,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
            }
        }

        // ── Pending suggestions (host only) ─────────────────────────────────
        if (content.isHost && content.pendingSuggestions.isNotEmpty()) {
            MenuSectionTitle(stringResource(R.string.listen_together_pending_suggestions))
            content.pendingSuggestions.forEach { suggestion ->
                ListMenu.Entry(
                    text = suggestion.trackInfo.title,
                    subtitle = suggestion.fromUsername,
                    icon = { SettingIcon(R.drawable.musical_notes, palette.accent) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ThemeIconButton(
                                onClick = { content.onApproveSuggestion(suggestion.suggestionId) },
                                icon = R.drawable.checkmark,
                                color = palette.accent,
                                modifier = Modifier.size(36.dp),
                            )
                            ThemeIconButton(
                                onClick = { content.onRejectSuggestion(suggestion.suggestionId) },
                                icon = R.drawable.close,
                                color = palette.red,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
            }
        }

        // ── Leave room (destructive, last entry) ────────────────────────────
        ListMenu.Entry(
            text = stringResource(R.string.listen_together_leave_room),
            icon = { SettingIcon(R.drawable.logout, palette.red) },
            onClick = content.onLeaveRoom,
        )
    } else {
        // ── Room: create / join ─────────────────────────────────────────────
        MenuSectionTitle(stringResource(R.string.listen_together_room))
        if (content.waiting) {
            WaitingApprovalRow(waitingForApprovalText)
            ListMenu.Entry(
                text = stringResource(R.string.cancel),
                icon = { SettingIcon(R.drawable.close, palette.red) },
                onClick = content.onCancelWaiting,
            )
        } else {
            ListMenu.Entry(
                text = stringResource(R.string.listen_together_create_room),
                subtitle = stringResource(R.string.listen_together_create_room_desc),
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

    // ── Settings (full room-page configuration) ────────────────────────────
    MenuSectionTitle(stringResource(R.string.settings))
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
        subtitle = stringResource(R.string.listen_together_auto_approval_joins_desc),
        icon = { SettingIcon(R.drawable.checkmark, palette.accent) },
        enabled = content.canEditApprovals,
        trailingContent = { ToggleSwitch(content.autoApprovalJoins) },
        onClick = content.onToggleAutoApprovalJoins,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_auto_approval_suggestions),
        subtitle = stringResource(R.string.listen_together_auto_approval_suggestions_desc),
        icon = { SettingIcon(R.drawable.musical_notes, palette.accent) },
        enabled = content.canEditApprovals,
        trailingContent = { ToggleSwitch(content.autoApproveSuggestions) },
        onClick = content.onToggleAutoApproveSuggestions,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_sync_volume),
        subtitle = stringResource(R.string.listen_together_sync_volume_desc),
        icon = { SettingIcon(R.drawable.volume_up, palette.accent) },
        trailingContent = { ToggleSwitch(content.syncHostVolume) },
        onClick = content.onToggleSyncVolume,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_history),
        subtitle = stringResource(R.string.listen_together_history_desc),
        icon = { SettingIcon(R.drawable.history, palette.accent) },
        trailingContent = { ToggleSwitch(content.historyEnabled) },
        onClick = content.onToggleHistory,
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_view_logs),
        subtitle = stringResource(R.string.listen_together_view_logs_desc),
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

/**
 * Grid rendering: [GridMenu.Menu] + full-span section titles + [GridMenu.Entry]
 * cells (same design as the app's own grid item menus). Same content as the list
 * rendering; actions that need two buttons (approve/reject, toggles) use the
 * cell's trailing slot. Plain (non-composable) function with a [LazyGridScope]
 * receiver: [GridMenu.Menu]'s content lambda is not a composable context, so this
 * registers the items from there; all composable calls happen inside the
 * `item { }` lambdas. The palette is passed in (cannot call [colorPalette] here).
 */
private fun LazyGridScope.GridLtMenuContent(content: LtMenuContent, palette: ColorPalette) {
    // ── Connection ──────────────────────────────────────────────────────────
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
        // ── Room code ───────────────────────────────────────────────────────
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

        // ── Connected users ─────────────────────────────────────────────────
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
                    onClick = { content.onUserClick(user.userId, user.username) },
                )
            }
        }

        // ── Join requests (host only) ───────────────────────────────────────
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
                                    icon = R.drawable.checkmark,
                                    color = palette.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                                ThemeIconButton(
                                    onClick = { content.onRejectJoin(request.userId) },
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

        // ── Pending suggestions (host only) ─────────────────────────────────
        if (content.isHost && content.pendingSuggestions.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MenuSectionTitle(stringResource(R.string.listen_together_pending_suggestions))
            }
            content.pendingSuggestions.forEach { suggestion ->
                item {
                    GridMenu.Entry(
                        text = suggestion.trackInfo.title,
                        subtitle = suggestion.fromUsername,
                        icon = { GridEntryIcon(R.drawable.musical_notes, palette.accent) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                ThemeIconButton(
                                    onClick = { content.onApproveSuggestion(suggestion.suggestionId) },
                                    icon = R.drawable.checkmark,
                                    color = palette.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                                ThemeIconButton(
                                    onClick = { content.onRejectSuggestion(suggestion.suggestionId) },
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

        // ── Leave room (destructive, last entry) ────────────────────────────
        item {
            GridMenu.Entry(
                text = stringResource(R.string.listen_together_leave_room),
                icon = { GridEntryIcon(R.drawable.logout, palette.red) },
                onClick = content.onLeaveRoom,
            )
        }
    } else {
        // ── Room: create / join ─────────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            MenuSectionTitle(stringResource(R.string.listen_together_room))
        }
        if (content.waiting) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                WaitingApprovalRow(stringResource(R.string.listen_together_waiting_for_approval))
            }
            item {
                GridMenu.Entry(
                    text = stringResource(R.string.cancel),
                    icon = { GridEntryIcon(R.drawable.close, palette.red) },
                    onClick = content.onCancelWaiting,
                )
            }
        } else {
            item {
                GridMenu.Entry(
                    text = stringResource(R.string.listen_together_create_room),
                    subtitle = stringResource(R.string.listen_together_create_room_desc),
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

    // ── Settings (full room-page configuration) ────────────────────────────
    item(span = { GridItemSpan(maxLineSpan) }) {
        MenuSectionTitle(stringResource(R.string.settings))
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
            trailingContent = { ToggleSwitch(content.autoApprovalJoins) },
            onClick = content.onToggleAutoApprovalJoins,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_auto_approval_suggestions),
            icon = { GridEntryIcon(R.drawable.musical_notes, palette.accent) },
            enabled = content.canEditApprovals,
            trailingContent = { ToggleSwitch(content.autoApproveSuggestions) },
            onClick = content.onToggleAutoApproveSuggestions,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_sync_volume),
            icon = { GridEntryIcon(R.drawable.volume_up, palette.accent) },
            trailingContent = { ToggleSwitch(content.syncHostVolume) },
            onClick = content.onToggleSyncVolume,
        )
    }
    item {
        GridMenu.Entry(
            text = stringResource(R.string.listen_together_history),
            icon = { GridEntryIcon(R.drawable.history, palette.accent) },
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

/**
 * Grid cell icon: 18dp tinted painter inside the [GridMenu.Entry] 32dp chip.
 */
@Composable
private fun GridEntryIcon(@DrawableRes icon: Int, color: Color) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(18.dp),
    )
}

/**
 * Accent section title, same styling as the app's own item menus (SongItemMenu
 * `SectionTitle`): xxs semiBold accent text with 12dp vertical padding. Used as a
 * full-span grid item in grid mode.
 */
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

/**
 * Non-clickable connection status row (dot + status text), same colors as the
 * room screen's connection card, laid out as a menu row / full-span grid item.
 */
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

/**
 * Centered app spinner row (waiting states: connecting / (re)connecting).
 */
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

/**
 * Waiting-for-host-approval row: app spinner + accent waiting text (same as the
 * room screen's join form).
 */
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

/**
 * Join/create error box (red tinted container with alert icon), same as the
 * room screen's join form.
 */
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

/**
 * Switch rendered in app theme colors (same as the settings entries); the row /
 * cell itself handles the click (the switch is visual only, like the app's
 * `SwitchSettingEntry`).
 */
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

/**
 * Create-room form sheet, following the app's own form-sheet pattern (LastFm
 * login / display name, settings card server/username sheets): a transparent
 * [CustomModalBottomSheet] with [ListMenu.Menu] chrome, app field colors
 * ([InputDialog]) and a full-width accent button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRoomSheet(
    showSheet: Boolean,
    savedUsername: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = colorPalette()
    var tempUsername by remember { mutableStateOf(savedUsername) }

    // Re-sync the draft each time the sheet opens (the composable stays composed
    // across open/close, so plain remember would keep a stale value).
    LaunchedEffect(showSheet) {
        if (showSheet) tempUsername = savedUsername
    }

    CustomModalBottomSheet(
        showSheet = showSheet,
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        modifier = Modifier.statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
            RoundedCornerShape(
                topStart = it.topStart,
                topEnd = it.topEnd,
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
        } ?: uiRoundnessShape(),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = Color.Transparent,
            ) {}
        },
    ) {
        // Cap the content at ~50% of the screen: ListMenu.Menu stretches to the
        // full screen height on its own, which would make the Expanded sheet
        // cover the whole window.
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeightDp * 0.5f).dp),
        ) {
            ListMenu.Menu(title = stringResource(R.string.listen_together_create_room)) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    TextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.listen_together_enter_username)) },
                        trailingIcon = {
                            if (tempUsername.isNotBlank()) {
                                IconButton(onClick = { tempUsername = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.clear),
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        colors = InputDialog.defaultTextFieldColors(),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    )
                    // App button pattern (InputDialog / UsernameSheet): always accent —
                    // no material3 `enabled` state, which would render the disabled
                    // button grey/translucent instead of accent. Validation is done
                    // in the click instead.
                    Button(
                        onClick = {
                            val name = tempUsername.trim()
                            if (name.isNotBlank()) onConfirm(name)
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.textSecondary,
                            ),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.listen_together_create_room),
                            style = typography().s.semiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Join-room form sheet, same app form-sheet pattern as [CreateRoomSheet]: room
 * code field (8 chars, uppercased, like the room screen's form) + username
 * field + full-width accent button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinRoomSheet(
    showSheet: Boolean,
    savedUsername: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = colorPalette()
    var tempCode by remember { mutableStateOf("") }
    var tempUsername by remember { mutableStateOf(savedUsername) }

    // Re-sync the drafts each time the sheet opens (fresh code, saved username).
    LaunchedEffect(showSheet) {
        if (showSheet) {
            tempCode = ""
            tempUsername = savedUsername
        }
    }

    val hasValidCode = tempCode.length == 8
    val hasValidUsername = tempUsername.trim().isNotBlank()

    CustomModalBottomSheet(
        showSheet = showSheet,
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        modifier = Modifier.statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
            RoundedCornerShape(
                topStart = it.topStart,
                topEnd = it.topEnd,
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
        } ?: uiRoundnessShape(),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = Color.Transparent,
            ) {}
        },
    ) {
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeightDp * 0.5f).dp),
        ) {
            ListMenu.Menu(title = stringResource(R.string.listen_together_join_room)) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    TextField(
                        value = tempCode,
                        onValueChange = { if (it.length <= 8) tempCode = it.uppercase() },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.listen_together_enter_room_code)) },
                        trailingIcon = {
                            if (tempCode.isNotBlank()) {
                                IconButton(onClick = { tempCode = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.clear),
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        colors = InputDialog.defaultTextFieldColors(),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    )
                    TextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.listen_together_enter_username)) },
                        trailingIcon = {
                            if (tempUsername.isNotBlank()) {
                                IconButton(onClick = { tempUsername = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.clear),
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        colors = InputDialog.defaultTextFieldColors(),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    )
                    // App button pattern: always accent, validation in the click
                    // (see CreateRoomSheet — material3 `enabled` drops the accent color).
                    Button(
                        onClick = {
                            if (hasValidCode && hasValidUsername) {
                                onConfirm(tempCode, tempUsername.trim())
                            }
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.textSecondary,
                            ),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.listen_together_join_room),
                            style = typography().s.semiBold,
                        )
                    }
                }
            }
        }
    }
}
