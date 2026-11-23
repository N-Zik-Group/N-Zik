package app.n_zik.android.components.ui.screens.listentogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.listentogether.ListenTogetherEvent
import app.n_zik.android.listentogether.ListenTogetherManager

@Composable
internal fun ObserveListenTogetherFormEvents(
    manager: ListenTogetherManager,
    isWaiting: () -> Boolean,
    setCreating: (Boolean) -> Unit,
    setJoining: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    canEndOnDisconnect: () -> Boolean = { true },
) {
    val context = LocalContext.current
    val latestIsWaiting by rememberUpdatedState(isWaiting)
    val latestSetCreating by rememberUpdatedState(setCreating)
    val latestSetJoining by rememberUpdatedState(setJoining)
    val latestSetError by rememberUpdatedState(setError)
    val latestCanEndOnDisconnect by rememberUpdatedState(canEndOnDisconnect)
    val invalidRoomCodeText = stringResource(R.string.listen_together_invalid_room_code)
    val joinRequestDeniedText = stringResource(R.string.listen_together_join_request_denied)
    val blockedByHostText = stringResource(R.string.listen_together_user_blocked_by_host)
    val connectionLostText = stringResource(R.string.listen_together_error)
    val serverErrorText = stringResource(R.string.listen_together_server_request_failed)

    LaunchedEffect(manager, context, invalidRoomCodeText, joinRequestDeniedText, blockedByHostText, connectionLostText, serverErrorText) {
        manager.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinRejected -> {
                    val message = joinRejectionMessage(
                        event.reason,
                        joinRequestDeniedText,
                        invalidRoomCodeText,
                        blockedByHostText,
                    ) { reason ->
                        context.getString(R.string.listen_together_join_request_denied_reason, reason)
                    }
                    latestSetError(message)
                    latestSetJoining(false)
                    latestSetCreating(false)
                    Toaster.e(message)
                }

                is ListenTogetherEvent.JoinApproved -> {
                    latestSetJoining(false)
                    latestSetError(null)
                }

                is ListenTogetherEvent.RoomCreated -> {
                    latestSetCreating(false)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.listen_together_room_code), event.roomCode))
                }

                // A dropped connection must end a pending create or join.
                is ListenTogetherEvent.ServerError -> {
                    if (latestIsWaiting()) {
                        latestSetCreating(false)
                        latestSetJoining(false)
                        latestSetError(serverErrorText)
                        Toaster.e(R.string.listen_together_server_request_failed)
                    }
                }

                is ListenTogetherEvent.ConnectionError -> {
                    if (latestIsWaiting()) {
                        latestSetCreating(false)
                        latestSetJoining(false)
                        latestSetError(connectionLostText)

                        Toaster.noInternet()
                    }
                }

                is ListenTogetherEvent.Disconnected -> {
                    if (latestIsWaiting() && latestCanEndOnDisconnect()) {
                        latestSetCreating(false)
                        latestSetJoining(false)
                        latestSetError(connectionLostText)
                    }
                }

                else -> {}
            }
        }
    }
}
