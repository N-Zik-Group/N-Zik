// Room screen for the Listen Together feature (Metrolist port — N-Zik styling):
// the UI is built exclusively from the app's own components — SettingsSectionCard /
// OtherSettingsEntry cards, app menus through the shared MenuState (ListMenu.Menu +
// ListMenu.Entry, same mechanism as the app's own item menus), themed
// app-standard Button / IconButton / app spinner (M3 LoadingIndicator, like the
// app's themed Loader but without its full-page bottom spacer) —
// with colorPalette(), typography() and uiRoundnessShape() throughout.
// Covers: connection status, create/join room, room code, connected users, host
// management (join requests, suggestions, kick/block/transfer) and the settings card.
// Reference: docs/Metrolist app/src/main/kotlin/com/metrolist/music/ui/screens/ListenTogetherScreen.kt
package app.n_zik.android.components.ui.screens.listentogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon

import app.it.fast4x.rimusic.ui.components.themed.IconButton as ThemeIconButton
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.settings.ListenTogetherSettingsCard
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.listentogether.JoinRequestPayload
import app.n_zik.android.listentogether.ListenTogetherEvent
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.LogEntry
import app.n_zik.android.listentogether.LogLevel
import app.n_zik.android.listentogether.SuggestionReceivedPayload
import app.n_zik.android.listentogether.UserInfo
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.DataStoreUtils
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAG = "ListenTogetherScreen"
internal const val PREF_USERNAME = "listen_together_username"

/** Metrolist web share format for the room invite link (per user request). */
internal const val LISTEN_INVITE_LINK_BASE = "https://metrolist.cc/listen"

/**
 * Live username preference (SharedPreferences string, same pattern as
 * [DataStoreUtils] readers): seeds from disk on first composition, writes through on change.
 */
@Composable
internal fun rememberUsernamePreference(context: Context): MutableState<String> =
    remember(context) {
        mutableStateOf(DataStoreUtils.getString(context, PREF_USERNAME))
    }

/**
 * Maps a join-rejection reason (server or host supplied) to the user-facing
 * message: blank reason → generic denial, "invalid" (case-insensitive, e.g.
 * "Invalid room code") → the invalid-code message, anything else → the denial
 * text with the reason appended. Shared by the room screen and the popup
 * [ListenTogetherMenu].
 */
internal fun joinRejectionMessage(
    reason: String?,
    deniedText: String,
    invalidCodeText: String,
): String = when {
    reason.isNullOrBlank() -> deniedText
    reason.contains("invalid", ignoreCase = true) -> invalidCodeText
    else -> "$deniedText: $reason"
}

/**
 * Listen Together room screen. Rendered inside the app [Skeleton] scaffold — header,
 * background, nav bar and mini-player handling all come from there (same pattern as the
 * other pushed screens); the manager is the shared [ListenTogetherManager] instance
 * created at app start.
 */
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

    if (listenTogetherManager == null) {
        Timber.tag(TAG).w("Manager not initialized, cannot render room screen")
        return
    }

    // App shared menu state (same mechanism as the app's own item menus):
    // display() pushes a menu into the root menu sheet rendered by MainActivity.
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

    val waitingForApprovalText = stringResource(R.string.listen_together_waiting_for_approval)
    val invalidRoomCodeText = stringResource(R.string.listen_together_invalid_room_code)
    val joinRequestDeniedText = stringResource(R.string.listen_together_join_request_denied)
    val connectionLostText = stringResource(R.string.listen_together_error)

    // Restore the saved username into the input field on first composition
    LaunchedEffect(usernamePref.value) {
        if (usernameInput.isBlank() && usernamePref.value.isNotBlank()) {
            usernameInput = usernamePref.value
        }
    }

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

                // A failure must end the waiting state instead of leaving it stuck: the server
                // can reject the operation (e.g. host_not_allowed) or the connection can drop
                // while a create/join is still in flight (user-reported: stuck on "waiting for
                // host approval"). A user-initiated cancel already reset the flags, so the
                // Disconnected branch is a no-op in that case.
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

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val bringIntoViewScope = rememberCoroutineScope()

    // This screen registers a single nav item, so the app draws no navigation bar
    // (both bars bail out with < 2 items). Reserve only the mini-player room below
    // the content instead of the full Dimensions.bottomSpacer (which assumes a bar).
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

    // The app scaffold provides the screen background, header and nav-bar handling;
    // the single nav item keeps the screen registered in the bar without drawing a tab row
    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.listen_together), R.drawable.people)
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // App cards (SettingsSectionCard) carry their own 16dp horizontal padding and a
            // trailing 16dp spacer, so the list adds no extra horizontal padding or spacing;
            // top is flush under the app header (the page header brings its own padding,
            // same as the app's settings pages).
            // No imePadding and no own background here (unlike the Metrolist port): the app's
            // screens let the keyboard overlay the list — the app's own search/queue screens do
            // the same — and the Skeleton scaffold paints the screen background. Padding the
            // list with the IME insets left a stuck dark block behind the keyboard on this
            // page (user-reported).
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        bottom = bottomContentSpacer,
                    ),
            ) {
                // Page header — same as the app's settings pages
                item {
                    HeaderWithIcon(
                        title = stringResource(R.string.listen_together),
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

                // Connection status card
                item {
                    ConnectionStatusCard(
                        connectionState = connectionState,
                        onConnect = { listenTogetherManager.connect() },
                        onDisconnect = { listenTogetherManager.disconnect() },
                        onReconnect = { listenTogetherManager.forceReconnect() },
                    )
                }

                if (isInRoom) {
                    // Room status card
                    roomState?.let { room ->
                        item {
                            RoomStatusCard(
                                roomCode = room.roomCode,
                                isHost = isHost,
                                context = context,
                            )
                        }

                        // Connected users
                        val connectedUsers = room.usersList.filter { it.isConnected }
                        val currentUserIdValue = userId ?: ""
                        item {
                            ConnectedUsersSection(
                                users = connectedUsers,
                                isHost = isHost,
                                currentUserId = currentUserIdValue,
                                onUserClick = { clickedUserId, username ->
                                    if (isHost && clickedUserId != currentUserIdValue) {
                                        // App menu mechanism (same as the app's own item menus)
                                        menuState.display {
                                            UserActionMenu(
                                                username = username,
                                                onKick = {
                                                    listenTogetherManager.kickUser(clickedUserId, "Removed by host")
                                                    menuState.hide()
                                                },
                                                onPermanentKick = {
                                                    listenTogetherManager.blockUser(username)
                                                    listenTogetherManager.kickUser(
                                                        clickedUserId,
                                                        context.getString(R.string.listen_together_user_blocked_by_host),
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
                                    onReject = { listenTogetherManager.rejectJoin(it, "Rejected by host") },
                                )
                            }
                        }

                        // Pending suggestions (host only)
                        if (isHost && pendingSuggestions.isNotEmpty()) {
                            item {
                                PendingSuggestionsSection(
                                    suggestions = pendingSuggestions,
                                    onApprove = { listenTogetherManager.approveSuggestion(it) },
                                    onReject = { listenTogetherManager.rejectSuggestion(it, "Rejected by host") },
                                )
                            }
                        }

                        // Leave room button — full-width destructive, app standard button
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

/**
 * Connection status card built on the app's [SettingsSectionCard]; the status row is the
 * only LT-specific layout (no app equivalent) and uses the app palette/typography.
 */
@Composable
internal fun ConnectionStatusCard(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
) {
    val palette = colorPalette()
    val stateColor =
        when (connectionState) {
            ConnectionState.CONNECTED -> palette.accent
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> palette.accent.copy(alpha = 0.6f)
            ConnectionState.ERROR -> palette.red
            ConnectionState.DISCONNECTED -> palette.textDisabled
        }

    SettingsSectionCard(
        title = stringResource(R.string.listen_together_connection),
        icon = R.drawable.link,
        content = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Status indicator row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(stateColor),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text =
                            when (connectionState) {
                                ConnectionState.CONNECTED -> stringResource(R.string.listen_together_connected)
                                ConnectionState.CONNECTING -> stringResource(R.string.listen_together_connecting)
                                ConnectionState.RECONNECTING -> stringResource(R.string.listen_together_reconnecting)
                                ConnectionState.ERROR -> stringResource(R.string.listen_together_error)
                                ConnectionState.DISCONNECTED -> stringResource(R.string.listen_together_disconnected)
                            },
                        style = typography().s.semiBold,
                        color = stateColor,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // One UI per state: connect only when down, spinner only while (re)connecting,
                // disconnect/reconnect only once connected (the action buttons used to show
                // ahead of time while the spinner was still running — user-reported).
                when (connectionState) {
                    ConnectionState.DISCONNECTED,
                    ConnectionState.ERROR -> {
                        // Primary action, app standard accent button (LastFm / SleepTimer pattern)
                        Button(
                            onClick = onConnect,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = palette.accent,
                                    contentColor = palette.textSecondary,
                                ),
                            shape = uiRoundnessShape(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.listen_together_connect),
                                style = typography().s.semiBold,
                            )
                        }
                    }

                    ConnectionState.CONNECTING,
                    ConnectionState.RECONNECTING -> {
                        // App standard spinner (default size, like the app's own screens)
                        InlineLoader()
                    }

                    ConnectionState.CONNECTED -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Secondary action (SleepTimer pattern: background2 button)
                            Button(
                                onClick = onReconnect,
                                modifier = Modifier.weight(1f),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = palette.background2,
                                        contentColor = palette.text,
                                    ),
                                shape = uiRoundnessShape(),
                            ) {
                                Text(
                                    stringResource(R.string.listen_together_reconnect),
                                    style = typography().s.semiBold,
                                )
                            }
                            Button(
                                onClick = onDisconnect,
                                modifier = Modifier.weight(1f),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = palette.accent,
                                        contentColor = palette.textSecondary,
                                    ),
                                shape = uiRoundnessShape(),
                            ) {
                                Text(
                                    stringResource(R.string.listen_together_disconnect),
                                    style = typography().s.semiBold,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

/**
 * Room code card built on the app's [SettingsSectionCard]; the large code display is
 * LT-specific layout rendered in the app typography/palette, the copy action reuses
 * the app's [OtherSettingsEntry] row.
 */
@Composable
internal fun RoomStatusCard(
    roomCode: String,
    isHost: Boolean,
    context: Context,
) {
    val palette = colorPalette()

    SettingsSectionCard(
        title = stringResource(R.string.listen_together_room_code),
        icon = R.drawable.link,
        description =
            if (isHost) {
                stringResource(R.string.listen_together_you_are_host)
            } else {
                stringResource(R.string.listen_together_you_are_guest)
            },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = roomCode,
                    style = typography().xxxl.semiBold,
                    color = palette.accent,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))
                OtherSettingsEntry(
                    title = stringResource(R.string.listen_together_copy_code),
                    text = "",
                    icon = R.drawable.copy,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Room Code", roomCode))
                        Toaster.i(R.string.listen_together_copied_to_clipboard)
                    },
                )
                // Invite link (Metrolist web share format) — shown to host and
                // guests alike, per user request
                val inviteLink = "$LISTEN_INVITE_LINK_BASE?code=$roomCode"
                OtherSettingsEntry(
                    title = stringResource(R.string.listen_together_copy_link),
                    text = inviteLink,
                    icon = R.drawable.link,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Listen Together Invite Link", inviteLink))
                        Toaster.i(R.string.listen_together_copied_to_clipboard)
                    },
                )
            }
        },
    )
}

/**
 * Connected users card built on the app's [SettingsSectionCard]; the horizontally
 * scrolling avatar row is LT-specific layout (no app avatar component) in app style.
 */
@Composable
internal fun ConnectedUsersSection(
    users: List<UserInfo>,
    isHost: Boolean,
    currentUserId: String,
    onUserClick: (String, String) -> Unit,
) {
    SettingsSectionCard(
        title = "${stringResource(R.string.listen_together_connected_users)} (${users.size})",
        icon = R.drawable.people,
        content = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                users.forEach { user ->
                    UserAvatar(
                        user = user,
                        isCurrentUser = user.userId == currentUserId,
                        isClickable = isHost && user.userId != currentUserId,
                        onClick = { onUserClick(user.userId, user.username) },
                    )
                }
            }
        },
    )
}

@Composable
private fun UserAvatar(
    user: UserInfo,
    isCurrentUser: Boolean,
    isClickable: Boolean,
    onClick: () -> Unit,
) {
    val palette = colorPalette()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(72.dp)
                .clickable(enabled = isClickable, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                user.isHost -> palette.accent
                                isCurrentUser -> palette.accent.copy(alpha = 0.45f)
                                else -> palette.background2
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = user.username.take(1).uppercase(),
                    style = typography().l.semiBold,
                    color =
                        when {
                            user.isHost -> palette.onAccent
                            else -> palette.text
                        },
                )
            }

            if (user.isHost || isCurrentUser) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (user.isHost) palette.accent else palette.accent.copy(alpha = 0.6f),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (user.isHost) R.drawable.star_brilliant else R.drawable.person,
                            ),
                        contentDescription = null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = user.username,
            style = typography().xs.semiBold,
            color = if (user.isHost) palette.accent else palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (user.isHost) {
            Text(
                text = stringResource(R.string.listen_together_host_label),
                style = typography().xxs,
                color = palette.accent.copy(alpha = 0.8f),
            )
        } else if (isCurrentUser) {
            Text(
                text = stringResource(R.string.listen_together_you_label),
                style = typography().xxs,
                color = palette.textSecondary,
            )
        }
    }
}

/**
 * Join requests card: app [SettingsSectionCard] container with app [ListMenu.Entry]
 * rows and themed icon buttons for approve/reject.
 */
@Composable
internal fun PendingJoinRequestsSection(
    requests: List<JoinRequestPayload>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    val palette = colorPalette()

    SettingsSectionCard(
        title = stringResource(R.string.listen_together_join_requests),
        icon = R.drawable.people,
        content = {
            requests.forEach { request ->
                ListMenu.Entry(
                    text = request.username,
                    icon = { SettingIcon(R.drawable.person, palette.accent) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ThemeIconButton(
                                onClick = { onApprove(request.userId) },
                                icon = R.drawable.checkmark,
                                color = palette.accent,
                                modifier = Modifier.size(36.dp),
                            )
                            ThemeIconButton(
                                onClick = { onReject(request.userId) },
                                icon = R.drawable.close,
                                color = palette.red,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
            }
        },
    )
}

/**
 * Song suggestions card: app [SettingsSectionCard] container with app [ListMenu.Entry]
 * rows and themed icon buttons for approve/reject.
 */
@Composable
internal fun PendingSuggestionsSection(
    suggestions: List<SuggestionReceivedPayload>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    val palette = colorPalette()

    SettingsSectionCard(
        title = stringResource(R.string.listen_together_pending_suggestions),
        icon = R.drawable.musical_notes,
        content = {
            suggestions.forEach { suggestion ->
                ListMenu.Entry(
                    text = suggestion.trackInfo.title,
                    subtitle = suggestion.fromUsername,
                    icon = { SettingIcon(R.drawable.musical_notes, palette.accent) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ThemeIconButton(
                                onClick = { onApprove(suggestion.suggestionId) },
                                icon = R.drawable.checkmark,
                                color = palette.accent,
                                modifier = Modifier.size(36.dp),
                            )
                            ThemeIconButton(
                                onClick = { onReject(suggestion.suggestionId) },
                                icon = R.drawable.close,
                                color = palette.red,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
            }
        },
    )
}

/**
 * Join/create room card built on the app's [SettingsSectionCard]: app-styled
 * [TextField] inputs with [InputDialog] colors (like the app's own form fields),
 * app spinner ([InlineLoader], same as the connection card) and app standard buttons.
 */
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
        description = stringResource(R.string.listen_together_create_room_desc),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Username input (app field: plain TextField + InputDialog colors,
                // like the app's own form fields)
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

                // Room code input
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

                // Waiting state: app standard loader (same as the connection card), the
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
                                stringResource(R.string.cancel),
                                style = typography().s.semiBold,
                            )
                        }
                    }
                }

                // Error message
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

                // Action buttons
                val hasUsername = usernameInput.trim().isNotBlank() || savedUsername.isNotBlank()
                val hasRoomCode = roomCodeInput.length == 8

                // Create Room button - visible when username is provided and no join/create is in flight
                AnimatedVisibility(visible = !isJoiningRoom && !isCreatingRoom && hasUsername && !hasRoomCode) {
                    RoomActionButton(
                        enabled = hasUsername,
                        text = stringResource(R.string.listen_together_create_room),
                        onClick = onCreateRoom,
                    )
                }

                // Join Room button - visible when username and room code are provided and no join/create is in flight
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

/**
 * Full-width room action button, app standard (LastFm / SleepTimer pattern): accent
 * [Button] with the native disabled state (M3 dims it automatically) and text only.
 */
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

/**
 * Inline variant of the app's themed [Loader] for card-sized containers: the exact
 * same M3 expressive LoadingIndicator (accent color, default 100dp) without the
 * themed wrapper's full-page bottom spacer — that spacer reserves room for the
 * floating nav bar / mini-player and creates a large gap under the spinner inside
 * the LT cards (user-reported).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InlineLoader(modifier: Modifier = Modifier) {
    LoadingIndicator(
        color = colorPalette().accent,
        modifier = modifier.size(100.dp),
    )
}

/**
 * Standard menu icon chip (same pattern as the other app menus): tinted rounded
 * 32dp square with an 18dp icon.
 */
@Composable
internal fun SettingIcon(@DrawableRes icon: Int, color: Color) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.1f), uiRoundnessShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Host-only user management menu (kick, permanent block, transfer ownership),
 * rendered through the app's shared menu state: same [ListMenu.Menu] +
 * [ListMenu.Entry] rows as the app's own item menus — no local sheet chrome.
 */
@Composable
internal fun UserActionMenu(
    username: String,
    onKick: () -> Unit,
    onPermanentKick: () -> Unit,
    onTransferOwnership: () -> Unit,
) {
    val menuState = LocalMenuState.current
    val palette = colorPalette()

    ListMenu.Menu(title = stringResource(R.string.listen_together_manage_user)) {
        Text(
            text = username,
            style = typography().xs,
            color = palette.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Kick
        ListMenu.Entry(
            text = stringResource(R.string.listen_together_kick_user),
            subtitle = stringResource(R.string.listen_together_kick_user_desc),
            icon = { SettingIcon(R.drawable.trash, palette.red) },
            onClick = {
                onKick()
                menuState.hide()
            },
        )

        // Permanent block
        ListMenu.Entry(
            text = stringResource(R.string.listen_together_permanently_kick_user),
            subtitle = stringResource(R.string.listen_together_permanently_kick_user_desc),
            icon = { SettingIcon(R.drawable.close, palette.red) },
            onClick = {
                onPermanentKick()
                menuState.hide()
            },
        )

        // Transfer ownership
        ListMenu.Entry(
            text = stringResource(R.string.listen_together_transfer_ownership),
            subtitle = stringResource(R.string.listen_together_transfer_ownership_desc),
            icon = { SettingIcon(R.drawable.star_brilliant, palette.accent) },
            onClick = {
                onTransferOwnership()
                menuState.hide()
            },
        )
    }
}

// ── Logs / blocked users menus (shared with the settings card) ──────────────────────────

/**
 * Connection logs menu (Metrolist `LogsDialog` port) shown through the app's shared
 * menu state: app [ListMenu.Menu] with rows styled like the app's own debug-log menu
 * item ([DebugLogsMenuItem] pattern — leading level icon, message + metadata lines)
 * and the app themed dialog buttons. The menu's own scroll handles the list, so no
 * nested scrollable is added here.
 */
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

        // Actions as app menu entries (the app's own item menus carry no button
        // bars).
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
                            append(log.level.name)
                            append("] ")
                            append(log.message)
                            log.details?.let { d -> append(" -- $d") }
                        }
                    }
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ListenTogetherLogs", textToCopy))
                Toaster.i(R.string.listen_together_copied_to_clipboard)
                menuState.hide()
            },
        )
        ListMenu.Entry(
            text = stringResource(R.string.clear),
            icon = { SettingIcon(R.drawable.trash, palette.red) },
            enabled = logs.isNotEmpty(),
            onClick = {
                onClear()
                menuState.hide()
            },
        )
    }
}

/**
 * Single connection log row, styled after the app's own debug-log menu item:
 * leading level icon (palette tinted), message line, monospace timestamp/level
 * metadata and an optional details line.
 */
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
                text = log.message,
                style = typography().xs,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${log.timestamp} [${log.level.name}]",
                style = typography().xxs,
                fontFamily = FontFamily.Monospace,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            log.details?.let { details ->
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

/**
 * Blocked users menu (Metrolist `BlockedUsersDialog` port) shown through the app's
 * shared menu state: app [ListMenu.Menu] with app [ListMenu.Entry] rows; tapping a
 * row unblocks the user (no trailing buttons, like the app's own item menus).
 */
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
            // Tapping a blocked user unblocks them (the app's item menus perform
            // their action on the entry row, with no trailing buttons).
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
