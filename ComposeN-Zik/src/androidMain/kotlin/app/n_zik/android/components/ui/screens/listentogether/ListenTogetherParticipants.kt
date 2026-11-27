package app.n_zik.android.components.ui.screens.listentogether

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.components.ui.screens.profiles.ProfileAvatarFrame
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.listentogether.JoinRequestPayload
import app.n_zik.android.listentogether.SuggestionReceivedPayload
import app.n_zik.android.listentogether.UserInfo
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.themed.IconButton as ThemeIconButton
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold

@Composable
internal fun ConnectedUsersSection(
    users: List<UserInfo>,
    isHost: Boolean,
    currentUserId: String,
    onUserClick: (String, String) -> Unit,
) {
    SettingsSectionCard(
        title = stringResource(
            R.string.listen_together_connected_users_title_format,
            stringResource(R.string.listen_together_connected_users),
            users.size,
        ),
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
internal fun UserAvatar(
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
            ProfileAvatarFrame(size = 56.dp, area = GlassArea.SettingsCards, shape = CircleShape,
                modifier = Modifier.testTag("listen.avatar.face.${user.userId}")) {
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
            }

            if (user.isHost || isCurrentUser) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(20.dp)
                            .testTag("listen.avatar.badge.${user.userId}")
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
                    text = suggestion.trackInfo.title.ifBlank { stringResource(R.string.metadata_unknown_title) },
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

@Composable
internal fun SettingIcon(@DrawableRes icon: Int, color: Color) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .iconBadgeBackground(color.copy(alpha = 0.1f), uiRoundnessShape()),
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

@Composable
internal fun UserActionMenu(
    username: String,
    onKick: () -> Unit,
    onPermanentKick: () -> Unit,
    onTransferOwnership: () -> Unit,
) {
    val menuState = LocalMenuState.current
    val palette = colorPalette()
    val style = rememberPreference(menuStyleKey, MenuStyle.List).value
    val title = stringResource(R.string.listen_together_manage_user)
    val backButton = managementBackButton(if (menuState.hasPrevious) menuState::pop else null)
    val header: @Composable () -> Unit = {
        Text(
            text = username,
            style = typography().xs,
            color = palette.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }
    val entries = listOf<@Composable () -> Unit>(
        {
            ListenTogetherManagementEntry(
                style, stringResource(R.string.listen_together_kick_user),
                stringResource(R.string.listen_together_kick_user_description), R.drawable.trash, palette.red,
            ) {
                onKick()
                menuState.hide()
            }
        },
        {
            ListenTogetherManagementEntry(
                style, stringResource(R.string.listen_together_permanently_kick_user),
                stringResource(R.string.listen_together_permanently_kick_user_description), R.drawable.close, palette.red,
            ) {
                onPermanentKick()
                menuState.hide()
            }
        },
        {
            ListenTogetherManagementEntry(
                style, stringResource(R.string.listen_together_transfer_ownership),
                stringResource(R.string.listen_together_transfer_ownership_description), R.drawable.star_brilliant, palette.accent,
            ) {
                onTransferOwnership()
                menuState.hide()
            }
        },
    )
    if (style == MenuStyle.List) {
        ListMenu.Menu(title = title, headerLeading = backButton) {
            header()
            entries.forEach { it() }
        }
    } else {
        GridMenu.Menu(title = title, headerLeading = backButton) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
            items(entries) { it() }
        }
    }
}
