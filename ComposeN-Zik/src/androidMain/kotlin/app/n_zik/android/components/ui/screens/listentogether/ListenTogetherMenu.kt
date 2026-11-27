package app.n_zik.android.components.ui.screens.listentogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import timber.log.Timber

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.settings.ServerChooserSheet
import app.n_zik.android.components.settings.UsernameSheet
import app.n_zik.android.core.navigation.screenPopEnter
import app.n_zik.android.core.navigation.screenPopExit
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.listentogether.LISTEN_TOGETHER_BLOCKED_REASON
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.ListenTogetherServers
import app.n_zik.android.listentogether.RoomRole
import app.n_zik.android.ui.components.LocalMenuScrollState
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.transitionEffectKey

private const val TAG = "ListenTogetherMenu"

private enum class RoomMenuPage { Room, Logs, BlockedUsers }

/** Opens room controls and settings using the chosen menu style. */
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
    var page by rememberSaveable { mutableStateOf(RoomMenuPage.Room) }

    var showCreateRoomSheet by rememberSaveable { mutableStateOf(false) }
    var showJoinRoomSheet by rememberSaveable { mutableStateOf(false) }
    var showServerChooserSheet by rememberSaveable { mutableStateOf(false) }
    var showUsernameSheet by rememberSaveable { mutableStateOf(false) }

    ObserveListenTogetherFormEvents(
        manager = listenTogetherManager,
        isWaiting = { isCreatingRoom || isJoiningRoom },
        setCreating = { isCreatingRoom = it },
        setJoining = { isJoiningRoom = it },
        setError = { joinErrorMessage = it },
    )

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
            serverLabel = selectedServer?.let { server -> "${server.name} - ${stringResource(server.locationRes)}" } ?: serverUrl,
            usernameLabel = username.ifEmpty { stringResource(R.string.listen_together_not_set) },
            autoApprovalJoins = autoApprovalJoins,
            autoApproveSuggestions = autoApproveSuggestions,
            syncHostVolume = syncHostVolume,
            historyEnabled = historyEnabled,
            blockedLabel =
                if (blockedUsernames.isNotEmpty()) {
                    pluralStringResource(R.plurals.listen_together_blocked_user_count, blockedUsernames.size, blockedUsernames.size)
                } else {
                    stringResource(R.string.listen_together_no_blocked_users)
                },
            onConnect = { listenTogetherManager.connect() },
            onDisconnect = { listenTogetherManager.disconnect() },
            onReconnect = { listenTogetherManager.forceReconnect() },
            onCopyCode = {
                roomCode?.let { code ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.listen_together_room_code), code))
                    Toaster.i(R.string.listen_together_copied_to_clipboard)
                }
            },
            onCopyLink = {
                roomCode?.let { code ->
                    val link = "$LISTEN_INVITE_LINK_BASE?code=$code"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(context.getString(R.string.listen_together_invite_link), link),
                    )
                    Toaster.i(R.string.listen_together_copied_to_clipboard)
                }
            },
            onUserClick = { clickedUserId, usernameToManage ->
                if (isHost && clickedUserId != currentUserId) {
                    menuState.display {
                        UserActionMenu(
                            username = usernameToManage,
                            onKick = { listenTogetherManager.kickUser(clickedUserId) },
                            onPermanentKick = {
                                listenTogetherManager.blockUser(usernameToManage)
                                listenTogetherManager.kickUser(
                                    clickedUserId,
                                    LISTEN_TOGETHER_BLOCKED_REASON,
                                )
                            },
                            onTransferOwnership = { listenTogetherManager.transferHost(clickedUserId) },
                        )
                    }
                }
            },
            onApproveJoin = { listenTogetherManager.approveJoin(it) },
            onRejectJoin = { listenTogetherManager.rejectJoin(it) },
            onApproveSuggestion = { listenTogetherManager.approveSuggestion(it) },
            onRejectSuggestion = { listenTogetherManager.rejectSuggestion(it) },
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
            onOpenLogs = { page = RoomMenuPage.Logs },
            onOpenBlockedUsers = { page = RoomMenuPage.BlockedUsers },
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
    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)

    BackHandler(enabled = menuState.isDisplayed && page != RoomMenuPage.Room) {
        page = RoomMenuPage.Room
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState == RoomMenuPage.Room) {
                screenPopEnter(transitionEffect) togetherWith screenPopExit(transitionEffect)
            } else {
                slideInHorizontally(tween(250)) { it / 2 } + fadeIn(tween(250)) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 2 } + fadeOut(tween(250)))
            }
        },
        label = "ListenTogetherMenuPage",
    ) { target ->
        CompositionLocalProvider(LocalMenuScrollState provides LocalMenuScrollState.current?.forPage(target)) {
            when (target) {
                RoomMenuPage.Room -> if (menuStyle == MenuStyle.List) {
                    ListMenu.Menu(
                        title = stringResource(R.string.listen_together_title),
                    ) {
                        ListLtMenuContent(content)
                    }
                } else {
                    GridMenu.Menu(
                        title = stringResource(R.string.listen_together_title),
                    ) {
                        GridLtMenuContent(content, palette)
                    }
                }
                RoomMenuPage.Logs -> ListenTogetherLogsMenu(
                    logs = logs,
                    onClear = { listenTogetherManager.clearLogs() },
                    onBack = { page = RoomMenuPage.Room },
                )
                RoomMenuPage.BlockedUsers -> ListenTogetherBlockedUsersMenu(
                    blockedUsernames = blockedUsernames,
                    onUnblock = { listenTogetherManager.unblockUser(it) },
                    onBack = { page = RoomMenuPage.Room },
                )
            }
        }
    }

    CompositionLocalProvider(LocalMenuScrollState provides null) {
        CreateRoomSheet(
            showSheet = showCreateRoomSheet,
            savedUsername = username,
            onConfirm = { finalUsername ->
                usernamePref.value = finalUsername
                DataStoreUtils.saveString(context, PREF_USERNAME, finalUsername)
                settingsVersion.value++
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
                settingsVersion.value++
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
            hasParentMenu = true,
        )
        UsernameSheet(
            showSheet = showUsernameSheet,
            onSaved = { settingsVersion.value++ },
            onDismiss = { showUsernameSheet = false },
            savedUsername = username,
            canEditUsername = { !listenTogetherManager.isInRoom },
            hasParentMenu = true,
        )
    }
}
