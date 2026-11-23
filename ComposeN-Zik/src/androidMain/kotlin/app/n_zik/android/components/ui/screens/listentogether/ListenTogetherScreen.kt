package app.n_zik.android.components.ui.screens.listentogether

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.glass.GlassTextField as TextField
import app.n_zik.android.components.glass.PageButton as Button
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.settings.ListenTogetherSettingsCard
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.ui.InlineLoader
import app.n_zik.android.components.ui.UiIconButton as IconButton
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.listentogether.INVITE_ROOM_CODE_KEY
import app.n_zik.android.listentogether.LISTEN_TOGETHER_BLOCKED_REASON
import app.n_zik.android.listentogether.LISTEN_TOGETHER_JOIN_BLOCKED_REASON
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.semiBold

private const val TAG = "ListenTogetherScreen"
internal const val PREF_USERNAME = "listen_together_username"

/** Metrolist room invitation URL. */
internal const val LISTEN_INVITE_LINK_BASE = "https://metrolist.cc/listen"

/** Initializes the username from the persisted Listen Together preference. */
@Composable
internal fun rememberUsernamePreference(context: Context): MutableState<String> =
    remember(context) {
        mutableStateOf(DataStoreUtils.getString(context, PREF_USERNAME))
    }

/**
 * Maps known server and host reasons to localized labels. Unknown reasons are kept as supplied.
 * Shared by the room screen and the popup [ListenTogetherMenu].
 */
internal fun joinRejectionMessage(
    reason: String?,
    deniedText: String,
    invalidCodeText: String,
    blockedText: String,
    formatReason: (String) -> String,
): String = when {
    reason.isNullOrBlank() || reason == "Rejected by host" -> deniedText
    reason == LISTEN_TOGETHER_JOIN_BLOCKED_REASON || reason == LISTEN_TOGETHER_BLOCKED_REASON -> blockedText
    reason.contains("invalid", ignoreCase = true) -> invalidCodeText
    else -> formatReason(reason)
}

/** Selects the action for a newly delivered invitation without changing the current room. */
internal fun inviteAction(roomCode: String, currentRoomCode: String?, savedUsername: String): InviteAction = when {
    currentRoomCode.equals(roomCode, ignoreCase = true) -> InviteAction.OPEN
    currentRoomCode != null -> InviteAction.CONFIRM_SWITCH
    savedUsername.isBlank() -> InviteAction.PREFILL
    else -> InviteAction.JOIN
}

internal enum class InviteAction { OPEN, CONFIRM_SWITCH, PREFILL, JOIN }

/** Removes an invitation before it can start a join or display a confirmation. */
internal fun consumeInvitation(handle: SavedStateHandle): String? {
    val code = handle.get<String>(INVITE_ROOM_CODE_KEY) ?: return null
    handle.set<String?>(INVITE_ROOM_CODE_KEY, null)
    return code
}

/** Renders the room using the shared manager and page scaffold. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListenTogetherScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val palette = colorPalette()
    val listenTogetherManager = remember { ListenTogetherManager.getInstance() }
    val usernamePref = rememberUsernamePreference(context)
    val inviteHandle = remember(navController) { navController.currentBackStackEntry?.savedStateHandle }
    val inviteCode by remember(inviteHandle) {
        inviteHandle?.getStateFlow<String?>(INVITE_ROOM_CODE_KEY, null)
            ?: MutableStateFlow<String?>(null)
    }.collectAsStateWithLifecycle()

    if (listenTogetherManager == null) {
        Timber.tag(TAG).w("Manager not initialized, cannot render room screen")
        return
    }

    val menuState = LocalMenuState.current

    val connectionState by listenTogetherManager.connectionState.collectAsStateWithLifecycle()
    val roomState by listenTogetherManager.roomState.collectAsStateWithLifecycle()
    val userId by listenTogetherManager.userId.collectAsStateWithLifecycle()
    val pendingJoinRequests by listenTogetherManager.pendingJoinRequests.collectAsStateWithLifecycle()
    val pendingSuggestions by listenTogetherManager.pendingSuggestions.collectAsStateWithLifecycle()

    var roomCodeInput by rememberSaveable { mutableStateOf("") }
    var usernameInput by rememberSaveable { mutableStateOf(usernamePref.value) }

    var isCreatingRoom by rememberSaveable { mutableStateOf(false) }
    var isJoiningRoom by rememberSaveable { mutableStateOf(false) }
    var joinErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var roomSwitchCode by rememberSaveable { mutableStateOf<String?>(null) }

    fun joinInvitation(code: String) {
        roomCodeInput = code
        val savedUsername = DataStoreUtils.getString(context, PREF_USERNAME).trim()
        if (savedUsername.isBlank()) return
        usernameInput = savedUsername
        usernamePref.value = savedUsername
        isJoiningRoom = true
        isCreatingRoom = false
        joinErrorMessage = null
        Toaster.i(R.string.listen_together_joining_room, code)
        listenTogetherManager.joinRoom(code, savedUsername)
    }

    val waitingForApprovalText = stringResource(R.string.listen_together_waiting_for_approval)
    // Restore the saved username into the input field on first composition
    LaunchedEffect(usernamePref.value) {
        if (usernameInput.isBlank() && usernamePref.value.isNotBlank()) {
            usernameInput = usernamePref.value
        }
    }

    ObserveListenTogetherFormEvents(
        manager = listenTogetherManager,
        isWaiting = { isCreatingRoom || isJoiningRoom },
        setCreating = { isCreatingRoom = it },
        setJoining = { isJoiningRoom = it },
        setError = { joinErrorMessage = it },
        canEndOnDisconnect = { listenTogetherManager.connectionState.value == ConnectionState.DISCONNECTED },
    )

    val isInRoom = listenTogetherManager.isInRoom
    val isHost = roomState?.hostId == userId

    LaunchedEffect(inviteCode) {
        if (inviteCode == null) return@LaunchedEffect
        val code = inviteHandle?.let(::consumeInvitation) ?: return@LaunchedEffect
        roomSwitchCode = null
        val savedUsername = DataStoreUtils.getString(context, PREF_USERNAME).trim()
        when (inviteAction(code, listenTogetherManager.roomState.value?.roomCode, savedUsername)) {
            InviteAction.OPEN -> Unit
            InviteAction.CONFIRM_SWITCH -> roomSwitchCode = code
            InviteAction.PREFILL -> {
                if (isJoiningRoom || isCreatingRoom) listenTogetherManager.disconnect()
                isJoiningRoom = false
                isCreatingRoom = false
                roomCodeInput = code
                joinErrorMessage = null
            }
            InviteAction.JOIN -> {
                if (isJoiningRoom || isCreatingRoom) listenTogetherManager.disconnect()
                joinInvitation(code)
            }
        }
    }

    roomSwitchCode?.let { code ->
        ConfirmationDialog(
            title = stringResource(R.string.listen_together_title),
            text = stringResource(R.string.listen_together_switch_room_confirmation, code),
            confirmText = stringResource(R.string.listen_together_leave_room),
            onDismiss = { roomSwitchCode = null },
            onConfirm = {
                roomSwitchCode = null
                if (!listenTogetherManager.roomState.value?.roomCode.equals(code, ignoreCase = true)) {
                    listenTogetherManager.leaveRoom()
                    joinInvitation(code)
                }
            },
        )
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val bringIntoViewScope = rememberCoroutineScope()

    // A single navigation item hides the bar. Reserve space only for the mini-player.
    val isFloatingNav = NavigationBarPosition.BottomFloating.isCurrent()
    val miniPlayerRoom =
        Dimensions.navBarBottomPadding(isFloatingNav) +
            if (isFloatingNav) 10.dp else 15.dp
    val bottomContentSpacer =
        if (LocalPlayerServiceBinder.current?.player?.currentMediaItem != null) {
            Dimensions.miniPlayerHeight + miniPlayerRoom
        } else {
            miniPlayerRoom
        }

    Skeleton(
        navController = navController,
        topInsetHandledByContent = true,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.listen_together_title), R.drawable.people)
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Cards own horizontal padding. Applying IME insets here left a backdrop behind the keyboard.
            // page (user-reported).
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = rememberPageChromeContentPadding(
                    PaddingValues(
                        bottom = bottomContentSpacer,
                    ),
                ),
            ) {
                item {
                    HeaderWithIcon(
                        title = stringResource(R.string.listen_together_title),
                        iconId = R.drawable.people,
                        enabled = false,
                        showIcon = true,
                        modifier = Modifier,
                        onClick = {},
                    )
                    SettingsDescription(
                        text = stringResource(R.string.listen_together_description),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                item {
                    ConnectionStatusCard(
                        connectionState = connectionState,
                        onConnect = { listenTogetherManager.connect() },
                        onDisconnect = { listenTogetherManager.disconnect() },
                        onReconnect = { listenTogetherManager.forceReconnect() },
                    )
                }

                if (isInRoom) {
                    roomState?.let { room ->
                        item {
                            RoomStatusCard(
                                roomCode = room.roomCode,
                                isHost = isHost,
                                context = context,
                            )
                        }

                        val connectedUsers = room.usersList.filter { it.isConnected }
                        val currentUserIdValue = userId ?: ""
                        item {
                            ConnectedUsersSection(
                                users = connectedUsers,
                                isHost = isHost,
                                currentUserId = currentUserIdValue,
                                onUserClick = { clickedUserId, username ->
                                    if (isHost && clickedUserId != currentUserIdValue) {
                                        menuState.display {
                                            UserActionMenu(
                                                username = username,
                                                onKick = {
                                                    listenTogetherManager.kickUser(clickedUserId)
                                                    menuState.hide()
                                                },
                                                onPermanentKick = {
                                                    listenTogetherManager.blockUser(username)
                                                    listenTogetherManager.kickUser(
                                                        clickedUserId,
                                                        LISTEN_TOGETHER_BLOCKED_REASON,
                                                    )
                                                    menuState.hide()
                                                },
                                                onTransferOwnership = {
                                                    listenTogetherManager.transferHost(clickedUserId)
                                                    menuState.hide()
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        // Pending join requests (host only)
                        if (isHost && pendingJoinRequests.isNotEmpty()) {
                            item {
                                PendingJoinRequestsSection(
                                    requests = pendingJoinRequests,
                                    onApprove = { listenTogetherManager.approveJoin(it) },
                                    onReject = { listenTogetherManager.rejectJoin(it) },
                                )
                            }
                        }

                        // Pending suggestions (host only)
                        if (isHost && pendingSuggestions.isNotEmpty()) {
                            item {
                                PendingSuggestionsSection(
                                    suggestions = pendingSuggestions,
                                    onApprove = { listenTogetherManager.approveSuggestion(it) },
                                    onReject = { listenTogetherManager.rejectSuggestion(it) },
                                )
                            }
                        }

                        item {
                            Button(
                                onClick = { listenTogetherManager.leaveRoom() },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = palette.red,
                                        contentColor = palette.textSecondary,
                                    ),
                                shape = uiRoundnessShape(),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp),
                            ) {
                                Text(
                                    stringResource(R.string.listen_together_leave_room),
                                    style = typography().s.semiBold,
                                )
                            }
                        }
                    }
                } else {
                    // Join/Create room section
                    item {
                        JoinCreateRoomSection(
                            usernameInput = usernameInput,
                            onUsernameChange = { usernameInput = it },
                            roomCodeInput = roomCodeInput,
                            onRoomCodeChange = { roomCodeInput = it },
                            savedUsername = usernamePref.value,
                            isJoiningRoom = isJoiningRoom,
                            isCreatingRoom = isCreatingRoom,
                            joinErrorMessage = joinErrorMessage,
                            waitingForApprovalText = waitingForApprovalText,
                            bringIntoViewRequester = bringIntoViewRequester,
                            onCreateRoom = {
                                val finalUsername = usernameInput.trim().ifEmpty { usernamePref.value }
                                if (finalUsername.isNotBlank()) {
                                    usernamePref.value = finalUsername
                                    DataStoreUtils.saveString(context, PREF_USERNAME, finalUsername)
                                    Toaster.i(R.string.listen_together_creating_room)
                                    isCreatingRoom = true
                                    isJoiningRoom = false
                                    joinErrorMessage = null
                                    listenTogetherManager.connect()
                                    listenTogetherManager.createRoom(finalUsername)
                                } else {
                                    Toaster.e(R.string.listen_together_error_username_empty)
                                }
                            },
                            onJoinRoom = {
                                val finalUsername = usernameInput.trim().ifEmpty { usernamePref.value }
                                if (finalUsername.isNotBlank()) {
                                    usernamePref.value = finalUsername
                                    DataStoreUtils.saveString(context, PREF_USERNAME, finalUsername)
                                    Toaster.i(R.string.listen_together_joining_room, roomCodeInput)
                                    isJoiningRoom = true
                                    isCreatingRoom = false
                                    joinErrorMessage = null
                                    listenTogetherManager.connect()
                                    listenTogetherManager.joinRoom(roomCodeInput, finalUsername)
                                } else {
                                    Toaster.e(R.string.listen_together_error_username_empty)
                                }
                            },
                            onCancel = {
                                listenTogetherManager.disconnect()
                                isJoiningRoom = false
                                isCreatingRoom = false
                                joinErrorMessage = null
                            },
                            onFieldFocused = {
                                bringIntoViewScope.launch { bringIntoViewRequester.bringIntoView() }
                            },
                        )
                    }
                }

                // Settings (server URL, username, auto-approvals, logs, blocked users)
                item {
                    ListenTogetherSettingsCard()
                }
            }
        }
    }
}

@Composable
internal fun JoinCreateRoomSection(
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    savedUsername: String,
    isJoiningRoom: Boolean,
    isCreatingRoom: Boolean,
    joinErrorMessage: String?,
    waitingForApprovalText: String,
    bringIntoViewRequester: BringIntoViewRequester,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onCancel: () -> Unit,
    onFieldFocused: () -> Unit = {},
) {
    val palette = colorPalette()

    SettingsSectionCard(
        title = stringResource(R.string.listen_together_room),
        icon = R.drawable.people,
        description = stringResource(R.string.listen_together_create_room_description),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextField(
                    value = usernameInput,
                    onValueChange = onUsernameChange,
                    placeholder = { Text(stringResource(R.string.listen_together_enter_username)) },
                    trailingIcon = {
                        if (usernameInput.isNotBlank()) {
                            IconButton(onClick = { onUsernameChange("") }) {
                                Icon(
                                    painterResource(R.drawable.close),
                                    null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = uiRoundnessShape(),
                    colors = InputDialog.defaultTextFieldColors(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) onFieldFocused() },
                )

                TextField(
                    value = roomCodeInput,
                    onValueChange = { if (it.length <= 8) onRoomCodeChange(it.uppercase()) },
                    placeholder = { Text(stringResource(R.string.listen_together_enter_room_code)) },
                    trailingIcon = {
                        if (roomCodeInput.isNotBlank()) {
                            IconButton(onClick = { onRoomCodeChange("") }) {
                                Icon(
                                    painterResource(R.drawable.close),
                                    null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = uiRoundnessShape(),
                    colors = InputDialog.defaultTextFieldColors(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onFocusChanged { if (it.isFocused) onFieldFocused() },
                )

                // waiting text and a cancel action (create/join had no way to be cancelled)
                AnimatedVisibility(
                    visible = isJoiningRoom || isCreatingRoom,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        InlineLoader()
                        Text(
                            text = waitingForApprovalText,
                            style = typography().s.semiBold,
                            color = palette.accent,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onCancel,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = palette.background2,
                                    contentColor = palette.text,
                                ),
                            shape = uiRoundnessShape(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.common_action_cancel),
                                style = typography().s.semiBold,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = joinErrorMessage != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
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
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = joinErrorMessage ?: "",
                                style = typography().xs,
                                color = palette.red,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                val hasUsername = usernameInput.trim().isNotBlank() || savedUsername.isNotBlank()
                val hasRoomCode = roomCodeInput.length == 8

                AnimatedVisibility(visible = !isJoiningRoom && !isCreatingRoom && hasUsername && !hasRoomCode) {
                    RoomActionButton(
                        enabled = hasUsername,
                        text = stringResource(R.string.listen_together_create_room),
                        onClick = onCreateRoom,
                    )
                }

                AnimatedVisibility(visible = !isJoiningRoom && !isCreatingRoom && hasUsername && hasRoomCode) {
                    RoomActionButton(
                        enabled = hasUsername && hasRoomCode,
                        text = stringResource(R.string.listen_together_join_room),
                        onClick = onJoinRoom,
                    )
                }
            }
        },
    )
}

@Composable
private fun RoomActionButton(
    enabled: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    val palette = colorPalette()
    Button(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.textSecondary,
            ),
        shape = uiRoundnessShape(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = typography().s.semiBold,
        )
    }
}
