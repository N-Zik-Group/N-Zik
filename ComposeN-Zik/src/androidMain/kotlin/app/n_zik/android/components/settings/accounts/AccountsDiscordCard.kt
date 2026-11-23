package app.n_zik.android.components.settings.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.glass.CustomModalBottomSheet
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.extensions.discord.DiscordAdvancedSettings
import app.n_zik.android.extensions.discord.DiscordLoginAndGetToken
import app.n_zik.android.extensions.discord.DiscordPresenceManager
import app.n_zik.android.extensions.discord.DiscordRpcError
import app.n_zik.android.extensions.discord.DiscordRpcErrorState
import app.n_zik.android.extensions.discord.isDiscordAdvancedModeKey
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.discordAvatarKey
import app.n_zik.android.utils.discordPersonalAccessTokenKey
import app.n_zik.android.utils.discordUsernameKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.isAtLeastAndroid7
import app.n_zik.android.utils.isDiscordPresenceEnabledKey
import app.n_zik.android.utils.rememberEncryptedPreference

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun AccountsDiscordCard(search: Search) {
    val context = LocalContext.current
    if (isAtLeastAndroid7) {
        AnimatedVisibility(
            visible = true,
            enter = settingsCardEnter,
            exit = settingsCardExit
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_discord_title) + " " + stringResource(R.string.updates_build_beta_label),
                icon = R.drawable.logo_discord,
                content = {
                    // The encrypted preference implementation needs API 24 or later.
                    var isDiscordPresenceEnabled by rememberEncryptedPreference(isDiscordPresenceEnabledKey, false)
                    var loginDiscord by remember { mutableStateOf(false) }
                    var discordPersonalAccessToken by rememberEncryptedPreference(
                        key = discordPersonalAccessTokenKey,
                        defaultValue = ""
                    )
                    var discordAvatar by rememberEncryptedPreference(
                        key = discordAvatarKey,
                        defaultValue = ""
                    )
                    var discordUsername by rememberEncryptedPreference(
                        key = discordUsernameKey,
                        defaultValue = ""
                    )
                    var showTokenError by remember { mutableStateOf(false) }

                    LaunchedEffect(discordPersonalAccessToken) {
                        if (discordPersonalAccessToken.isNotEmpty()) {
                            val presenceManager = DiscordPresenceManager(context, { discordPersonalAccessToken })
                            when (presenceManager.validateToken(discordPersonalAccessToken)) {
                                true -> {
                                    showTokenError = false
                                }
                                false -> {
                                    showTokenError = true
                                    discordPersonalAccessToken = ""
                                    discordUsername = ""
                                    discordAvatar = ""
                                    Toaster.e(R.string.discord_token_text_invalid)
                                }
                                null -> { // Network error
                                    showTokenError = false
                                }
                            }
                        }
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.discord_enable_rich_presence).contains(search.inputValue, true) || stringResource(R.string.discord_beta_warning).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_enable_rich_presence"),
                            title = stringResource(R.string.discord_enable_rich_presence),
                            text = stringResource(R.string.settings_description_with_value, stringResource(R.string.discord_enabled_description), stringResource(R.string.discord_beta_warning)),
                            isChecked = isDiscordPresenceEnabled,
                            onCheckedChange = {
                                isDiscordPresenceEnabled = it
                                if (!it) {
                                    RestartAppDialog.showDialog()
                                }
                            },
                            icon = R.drawable.musical_notes
                        )
                    }

                    AnimatedVisibility(visible = isDiscordPresenceEnabled) {
                        Column {
                            val discordRpcError by DiscordRpcErrorState.error.collectAsStateWithLifecycle()
                            AnimatedVisibility(discordRpcError != null) {
                                discordRpcError?.let { error ->
                                    DiscordRpcErrorBanner(error = error)
                                }
                            }

                            var isDiscordAdvancedMode by rememberEncryptedPreference(isDiscordAdvancedModeKey, false)

                            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_mode).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_mode"),
                                    title = stringResource(R.string.discord_advanced_mode),
                                    text = stringResource(R.string.discord_advanced_mode_description),
                                    isChecked = isDiscordAdvancedMode,
                                    onCheckedChange = { isDiscordAdvancedMode = it },
                                    icon = R.drawable.pencil
                                )
                            }

                            AnimatedVisibility(visible = isDiscordAdvancedMode) {
                                DiscordAdvancedSection(search = search)
                            }

                            val player = LocalPlayerServiceBinder.current?.player
                            var previewPosition by remember { mutableStateOf(0L) }
                            // Tick while idle or paused so the preview still reflects settings edits.

                            var previewTick by remember { mutableIntStateOf(0) }
                            // Use -1 to follow the player, and 0 or 1 pins the preview to playing or paused.

                            var previewMode by remember { mutableIntStateOf(-1) }
                            val realMode = when {
                                player != null && player.currentMediaItem != null && player.isPlaying -> 0
                                player != null && player.currentMediaItem != null -> 1
                                else -> 2
                            }
                            LaunchedEffect(player) {
                                while (isActive) {
                                    delay(100)
                                    runCatching {
                                        // Freeze the paused preview even when the real player keeps playing.

                                        val effectiveMode =
                                            if (previewMode >= 0) previewMode
                                            else when {
                                                player != null && player.currentMediaItem != null && player.isPlaying -> 0
                                                player != null && player.currentMediaItem != null -> 1
                                                else -> 2
                                            }
                                        val frozen = effectiveMode == 1 && player?.isPlaying == true
                                        if (player != null && !frozen) previewPosition = player.currentPosition
                                        previewTick++
                                    }
                                }
                            }

                            Text(
                                text = stringResource(R.string.discord_preview_title),
                                color = colorPalette().text,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 13.dp, top = 8.dp, bottom = 8.dp)
                            )
                            DiscordRpcPreviewCard(
                                player = player,
                                positionMs = previewPosition,
                                tick = previewTick,
                                settings = DiscordAdvancedSettings.read(context.encryptedPreferences),
                                mode = if (previewMode >= 0) previewMode else realMode
                            )

                            val chipPalette = colorPalette()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                            ) {
                                listOf(
                                    R.string.discord_preview_state_actual,
                                    R.string.discord_preview_state_playing,
                                    R.string.discord_preview_state_paused,
                                ).forEachIndexed { index, labelRes ->
                                    val selected = when (index) {
                                        0 -> previewMode == -1
                                        1 -> previewMode == 0
                                        else -> previewMode == 1
                                    }
                                    DiscordPreviewChip(
                                        label = stringResource(labelRes),
                                        color = if (selected) chipPalette.accent else chipPalette.accent.copy(alpha = 0.15f),
                                        foreground = if (selected) chipPalette.onAccent else chipPalette.accent,
                                        onClick = {
                                            when (index) {
                                                0 -> previewMode = -1
                                                1 -> previewMode = if (previewMode == 0) -1 else 0
                                                else -> previewMode = if (previewMode == 1) -1 else 1
                                            }
                                        },
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showTokenError) {
                                Text(
                                    text = stringResource(R.string.discord_token_text_invalid),
                                    color = colorPalette().red,
                                    style = typography().s,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            AnimatedVisibility(visible = discordPersonalAccessToken.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_account_information_label),
                                            color = colorPalette().text,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 5.dp),
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (discordAvatar.isNotEmpty()) {
                                                ImageCacheFactory.AsyncImage(
                                                    shape = thumbnailShape(),
                                                    thumbnailUrl = discordAvatar,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .padding(start = 5.dp, top = 8.dp, bottom = 8.dp)
                                                        .size(50.dp)
                                                        .clip(thumbnailShape())
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.person),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .padding(start = 5.dp, top = 8.dp, bottom = 8.dp)
                                                        .size(50.dp)
                                                        .clip(thumbnailShape()),
                                                    tint = colorPalette().textSecondary
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .padding(start = 8.dp)
                                                    .height(50.dp)
                                                    .padding(top = 8.dp, bottom = 8.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = discordUsername,
                                                    color = colorPalette().textSecondary,
                                                    modifier = Modifier.padding(start = 5.dp),
                                                    style = typography().m
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (search.inputValue.isBlank() || stringResource(R.string.account_action_login).contains(search.inputValue, true) || stringResource(R.string.account_action_logout).contains(search.inputValue, true)) {
                                OtherSettingsEntry(
                                    modifier = Modifier.settingsSearchTarget(if (discordPersonalAccessToken.isNotEmpty()) "accounts.social_discord.account_logoff" else "accounts.social_discord.account_login"),
                                    title = if (discordPersonalAccessToken.isNotEmpty()) stringResource(R.string.account_action_logout) else stringResource(R.string.account_action_login),
                                    text = stringResource(R.string.discord_login_description),
                                    selectedValueText = if (discordPersonalAccessToken.isNotEmpty()) stringResource(R.string.discord_connected_to_discord_account) else "",
                                    icon = R.drawable.logout,
                                    onClick = {
                                        if (discordPersonalAccessToken.isNotEmpty()) {
                                            discordPersonalAccessToken = ""
                                            discordUsername = ""
                                            discordAvatar = ""
                                            showTokenError = false
                                            RestartAppDialog.showDialog()
                                        } else
                                            loginDiscord = true
                                    }
                                )
                            }

                            CustomModalBottomSheet(
                                showSheet = loginDiscord,
                                onDismissRequest = {
                                    loginDiscord = false
                                },
                                containerColor = colorPalette().background0,
                                contentColor = colorPalette().background0,
                                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                shape = uiRoundnessShape(),
                                dragHandle = {
                                    MenuSheetDragHandle(minTouchHeight = 48.dp)
                                }
                            ) {
                                DiscordLoginAndGetToken(
                                    navController = rememberNavController(),
                                    onGetToken = { token, username, avatar ->
                                        loginDiscord = false
                                        discordPersonalAccessToken = token
                                        discordUsername = username
                                        discordAvatar = avatar
                                        Toaster.i(context.getString(R.string.discord_connected_to_discord_account))
                                        RestartAppDialog.showDialog()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }

}

/** Dismisses the error from the same state that the connection clears on recovery. */
@Composable
private fun DiscordRpcErrorBanner(error: DiscordRpcError) {
    val palette = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(palette.red.copy(alpha = 0.15f), uiRoundnessShape())
            .clip(uiRoundnessShape())
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.discord_error_banner_title),
                style = typography().m.copy(color = palette.red),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    when (error) {
                        DiscordRpcError.INVALID_TOKEN -> R.string.discord_error_invalid_token
                        DiscordRpcError.RECONNECT_FAILED -> R.string.discord_error_reconnect_failed
                    }
                ),
                style = typography().s.copy(color = palette.text)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.discord_error_dismiss),
                style = typography().s.copy(color = palette.accent),
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(uiRoundnessShape())
                    .clickable { DiscordRpcErrorState.clear() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** Shows the artwork or app logo tooltip when the preview image is tapped. */
@Composable
internal fun DiscordImageTooltipDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    val palette = colorPalette()
    DefaultDialog(onDismiss = onDismiss, title = title,
        footer = {
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.common_action_ok),
                        primary = true,
                        onClick = onDismiss
                    )
                )
            )
        }
    ) {
        Text(
            text = text,
            style = typography().s.copy(color = palette.text)
        )

    }
}
