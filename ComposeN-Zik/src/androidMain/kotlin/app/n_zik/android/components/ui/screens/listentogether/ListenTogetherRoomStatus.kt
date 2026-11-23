package app.n_zik.android.components.ui.screens.listentogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.PageButton as Button
import app.n_zik.android.components.glass.glassAwareContentSize
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.ui.InlineLoader
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.semiBold

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
                        .glassAwareContentSize(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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

                when (connectionState) {
                    ConnectionState.DISCONNECTED,
                    ConnectionState.ERROR -> {
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
                        InlineLoader()
                    }

                    ConnectionState.CONNECTED -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
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
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.listen_together_room_code), roomCode))
                        Toaster.i(R.string.listen_together_copied_to_clipboard)
                    },
                )
                val inviteLink = "$LISTEN_INVITE_LINK_BASE?code=$roomCode"
                OtherSettingsEntry(
                    title = stringResource(R.string.listen_together_copy_link),
                    text = inviteLink,
                    icon = R.drawable.link,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.listen_together_invite_link), inviteLink))
                        Toaster.i(R.string.listen_together_copied_to_clipboard)
                    },
                )
            }
        },
    )
}
