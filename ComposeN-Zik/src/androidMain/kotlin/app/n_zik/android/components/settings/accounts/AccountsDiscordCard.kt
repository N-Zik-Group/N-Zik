package app.n_zik.android.components.settings.accounts

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.components.glass.CustomModalBottomSheet
import app.n_zik.android.components.glass.GlassCustomChip
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.getVersionName
import app.it.fast4x.rimusic.utils.isAtLeastAndroid7
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.isExplicit
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.extensions.discord.DiscordActivityBuilder
import app.n_zik.android.extensions.discord.DiscordAdvancedSettings
import app.n_zik.android.extensions.discord.DiscordLoginAndGetToken
import app.n_zik.android.extensions.discord.DiscordMediaInfo
import app.n_zik.android.extensions.discord.DiscordPresenceManager
import app.n_zik.android.extensions.discord.DiscordRefreshIntervalEntry
import app.n_zik.android.extensions.discord.DiscordRpcError
import app.n_zik.android.extensions.discord.DiscordRpcErrorState
import app.n_zik.android.extensions.discord.DiscordStrings
import app.n_zik.android.extensions.discord.DiscordTemplateFieldActions
import app.n_zik.android.extensions.discord.DiscordTemplateRenderer
import app.n_zik.android.extensions.discord.discordAdvancedActivityTypeKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedDetailsTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedIdleCloseEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedLargeImageTextKey
import app.n_zik.android.extensions.discord.discordAdvancedNameKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseClearEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPausePresenceEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedShowArtworkKey
import app.n_zik.android.extensions.discord.discordAdvancedShowDetailsKey
import app.n_zik.android.extensions.discord.discordAdvancedShowSmallImageKey
import app.n_zik.android.extensions.discord.discordAdvancedShowStateKey
import app.n_zik.android.extensions.discord.discordAdvancedShowTimestampsKey
import app.n_zik.android.extensions.discord.discordAdvancedSmallImageTextKey
import app.n_zik.android.extensions.discord.discordAdvancedStateTemplateKey
import app.n_zik.android.extensions.discord.isDiscordAdvancedModeKey
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.albumTitleWithFallback
import app.n_zik.android.utils.artistTextWithFallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun AccountsDiscordCard(search: Search) {
    val context = LocalContext.current
    // Discord Section
    if (isAtLeastAndroid7) {
        AnimatedVisibility(
            visible = true,
            enter = settingsCardEnter,
            exit = settingsCardExit
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.social_discord) + " " + stringResource(R.string.beta_title),
                icon = R.drawable.logo_discord,
                content = {
                    // rememberEncryptedPreference only works correct with API 24 and up
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

                    if (search.inputValue.isBlank() || stringResource(R.string.discord_enable_rich_presence).contains(search.inputValue, true) || stringResource(R.string.beta_text).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_enable_rich_presence"),
                            title = stringResource(R.string.discord_enable_rich_presence),
                            text = stringResource(R.string.settings_description_with_value, stringResource(R.string.settings_desc_accounts_social_discord_discord_enable_rich_presence), stringResource(R.string.beta_text)),
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
                            // Item 9: durable error banner (invalid token / reconnect abandoned),
                            // dismissible — observed with collectAsStateWithLifecycle (app rule).
                            val discordRpcError by DiscordRpcErrorState.error.collectAsStateWithLifecycle()
                            // Content transition (Last.fm card pattern): the banner
                            // animates in/out with the default expand/collapse + fade.
                            AnimatedVisibility(discordRpcError != null) {
                                discordRpcError?.let { error ->
                                    DiscordRpcErrorBanner(error = error)
                                }
                            }

                            // Item 6: advanced presence settings + live preview (visible when
                            // the presence is on, i.e. inside this AnimatedVisibility).
                            var isDiscordAdvancedMode by rememberEncryptedPreference(isDiscordAdvancedModeKey, false)

                            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_mode).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_mode"),
                                    title = stringResource(R.string.discord_advanced_mode),
                                    text = stringResource(R.string.discord_advanced_mode_text),
                                    isChecked = isDiscordAdvancedMode,
                                    onCheckedChange = { isDiscordAdvancedMode = it },
                                    icon = R.drawable.pencil
                                )
                            }

                            // PW-5 (revised 2026-09-21): content transition matches the app's
                            // own pattern (Last.fm card).

                            // The default AnimatedVisibility expand/collapse + fade, not a fixed tween fade+scale.
                            AnimatedVisibility(visible = isDiscordAdvancedMode) {
                                DiscordAdvancedSection(search = search)
                            }

                            // Item 6 + PW-6: live preview of the RPC card — visible in
                            // both modes (advanced mode only changes the rendered content).

                            // Fed by the current player state, the position is polled
                            // ~100 ms (upstream parity). binder == null → neutral card.
                            val player = LocalPlayerServiceBinder.current?.player
                            var previewPosition by remember { mutableStateOf(0L) }
                            // The tick advances on every poll even while paused/idle, so
                            // the card stays reactive to settings changes in real time.

                            // The position alone only changes while playing.
                            var previewTick by remember { mutableIntStateOf(0) }
                            // State-chip selection: -1 = actual state (live follow of the
                            // real player state).

                            // 0/1 = pin the preview card to playing / paused.
                            var previewMode by remember { mutableIntStateOf(-1) }
                            // The real playback state (0 = playing, 1 = paused, 2 = idle) —
                            // drives the default chip highlight and the pinned fallback.
                            val realMode = when {
                                player != null && player.currentMediaItem != null && player.isPlaying -> 0
                                player != null && player.currentMediaItem != null -> 1
                                else -> 2
                            }
                            LaunchedEffect(player) {
                                while (isActive) {
                                    delay(100)
                                    runCatching {
                                        // The pinned "Paused" variant must freeze its progress
                                        // bar: while the card shows paused but the real player

                                        // still plays (pinned chip during playback), the live
                                        // position must stop advancing.

                                        // Live follow (-1) is unaffected — a real pause already
                                        // keeps currentPosition static.
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
                            // Same visual style as the "Account info" title below: regular
                            // text color, bold, start inset aligned (13.dp = column 8.dp +

                            // text 5.dp), vertical spacing kept.
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

                            // Playback state chips (2026-09-21), centered under the preview:
                            // "Actual state" (live follow — first), then Playing / Paused to

                            // pin that variant of the card for inspection (tap the pinned
                            // chip again to unpin).
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
                                    // index 0 = actual (previewMode -1), 1 = playing (0), 2 = paused (1)
                                    val selected = when (index) {
                                        0 -> previewMode == -1
                                        1 -> previewMode == 0
                                        else -> previewMode == 1
                                    }
                                    GlassCustomChip(
                                        color = if (selected) chipPalette.accent else chipPalette.accent.copy(alpha = 0.15f),
                                        onClick = {
                                            when (index) {
                                                0 -> previewMode = -1
                                                1 -> previewMode = if (previewMode == 0) -1 else 0
                                                else -> previewMode = if (previewMode == 1) -1 else 1
                                            }
                                        },
                                    ) { chipModifier ->
                                        Text(
                                            text = stringResource(labelRes),
                                            style = typography().s.copy(
                                                color = if (selected) chipPalette.onAccent else chipPalette.accent
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = chipModifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }

                            // Content transition (Last.fm card pattern): the token error
                            // line animates in/out with the default expand/collapse + fade.
                            AnimatedVisibility(visible = showTokenError) {
                                Text(
                                    text = stringResource(R.string.discord_token_text_invalid),
                                    color = colorPalette().red,
                                    style = typography().s,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            // Content transition (Last.fm card pattern): the account info
                            // row animates in/out with the default expand/collapse + fade.
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
                                            text = stringResource(R.string.account_info),
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

                            if (search.inputValue.isBlank() || stringResource(R.string.account_login).contains(search.inputValue, true) || stringResource(R.string.account_logoff).contains(search.inputValue, true)) {
                                OtherSettingsEntry(
                                    modifier = Modifier.settingsSearchTarget(if (discordPersonalAccessToken.isNotEmpty()) "accounts.social_discord.account_logoff" else "accounts.social_discord.account_login"),
                                    title = if (discordPersonalAccessToken.isNotEmpty()) stringResource(R.string.account_logoff) else stringResource(R.string.account_login),
                                    text = stringResource(R.string.settings_desc_accounts_social_discord_account_login),
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
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 18.dp, bottom = 6.dp)
                                            .size(width = 40.dp, height = 4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White)
                                    )
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

/**
 * Discord section only (item 9): durable RPC error banner — invalid token (4004) or
 * reconnect abandonment. Dismiss clears [DiscordRpcErrorState]; a new token / fresh
 * connection also clears it from the service side.
 */
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

/**
 * Discord section only: the calm group header of the advanced-settings groups. The
 * shared [SettingsEntryGroupText] renders uppercase accent text — too loud for this
 * section: the label stays sentence-case (one uppercase letter) in the secondary text
 * color, with a small gap before the group's entries.
 */
@Composable
private fun DiscordSettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = typography().xs.semiBold.copy(colorPalette().textSecondary),
            modifier = Modifier
                .padding(top = 12.dp)
                .padding(start = 12.dp),
        )
        content()
    }
}

/**
 * Discord section only (item 6): advanced presence settings — activity type,
 * templates (name/state/details/pause + 2 buttons) and the pause-presence toggle.
 * The live RPC preview lives in the main Discord section (PW-6: visible in both
 * modes). Legacy-approved edit, used only by the Discord section of this file.
 */
@Composable
internal fun DiscordAdvancedSection(search: Search) {
    var activityType by rememberEncryptedPreference(discordAdvancedActivityTypeKey, 2)
    var pausePresenceEnabled by rememberEncryptedPreference(discordAdvancedPausePresenceEnabledKey, true)
    var activityName by rememberEncryptedPreference(discordAdvancedNameKey, "")
    var stateTemplate by rememberEncryptedPreference(discordAdvancedStateTemplateKey, "")
    var detailsTemplate by rememberEncryptedPreference(discordAdvancedDetailsTemplateKey, "")
    var pauseTemplate by rememberEncryptedPreference(discordAdvancedPauseTemplateKey, "")
    var btn1Enabled by rememberEncryptedPreference(discordAdvancedButton1EnabledKey, true)
    var btn1Label by rememberEncryptedPreference(discordAdvancedButton1LabelKey, "")
    var btn1Url by rememberEncryptedPreference(discordAdvancedButton1UrlKey, "")
    var btn2Enabled by rememberEncryptedPreference(discordAdvancedButton2EnabledKey, true)
    var btn2Label by rememberEncryptedPreference(discordAdvancedButton2LabelKey, "")
    var btn2Url by rememberEncryptedPreference(discordAdvancedButton2UrlKey, "")
    // Per-section visibility (advanced mode only): default on = current behavior.
    var showState by rememberEncryptedPreference(discordAdvancedShowStateKey, true)
    var showDetails by rememberEncryptedPreference(discordAdvancedShowDetailsKey, true)
    var showArtwork by rememberEncryptedPreference(discordAdvancedShowArtworkKey, true)
    var showSmallImage by rememberEncryptedPreference(discordAdvancedShowSmallImageKey, true)
    var showTimestamps by rememberEncryptedPreference(discordAdvancedShowTimestampsKey, true)
    // Image tooltip templates (advanced mode; empty = the built-in default).
    var largeImageText by rememberEncryptedPreference(discordAdvancedLargeImageTextKey, "")
    var smallImageText by rememberEncryptedPreference(discordAdvancedSmallImageTextKey, "")
    // Inactivity timer toggles (advanced mode; default on = current behavior).
    var pauseClearEnabled by rememberEncryptedPreference(discordAdvancedPauseClearEnabledKey, true)
    var idleCloseEnabled by rememberEncryptedPreference(discordAdvancedIdleCloseEnabledKey, true)

    var showActivityTypeDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showStateDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showBtn1LabelDialog by remember { mutableStateOf(false) }
    var showBtn1UrlDialog by remember { mutableStateOf(false) }
    var showBtn2LabelDialog by remember { mutableStateOf(false) }
    var showBtn2UrlDialog by remember { mutableStateOf(false) }
    var showLargeImageTextDialog by remember { mutableStateOf(false) }
    var showSmallImageTextDialog by remember { mutableStateOf(false) }

    val activityTypeLabel = when (activityType) {
        0 -> stringResource(R.string.discord_activity_playing)
        3 -> stringResource(R.string.discord_activity_watching)
        5 -> stringResource(R.string.discord_activity_competing)
        else -> stringResource(R.string.discord_activity_listening)
    }
    // The image text field shows its own template default when empty (the album
    // template — DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE).

    // Exactly like the state/details fields show theirs; there is no longer a
    // "details - state" recovery expression for it.
    Column {
        // Grouped layout (2026-09-22): related entries live under a shared group header —
        // the presence card parts (lines, images, progress bar).

        // Then the actions (buttons) and the connection behavior.
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_identity)) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_activity_type).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_activity_type"),
                    title = stringResource(R.string.discord_activity_type),
                    text = stringResource(R.string.settings_desc_accounts_social_discord_discord_activity_type),
                    selectedValueText = activityTypeLabel,
                    icon = R.drawable.play,
                    onClick = { showActivityTypeDialog = true }
                )
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_name).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_name"),
                    title = stringResource(R.string.discord_advanced_name),
                    // Empty template = the built-in default below (the fixed name), shown
                    // as the current value instead of a "(default)" marker.
                    text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_name),
                    selectedValueText = activityName.ifEmpty { stringResource(R.string.discord_presence_name) },
                    icon = R.drawable.text,
                    onClick = { showNameDialog = true }
                )
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_lines)) {
            // Linked visibility: the section toggle comes first, and the linked template
            // entry animates in/out with it (Last.fm card pattern — the app's default

            // expand/collapse + fade); the toggle stays reachable so the part can be
            // restored.

            // The lines follow the CARD display order — the details line (bold) sits above
            // the state line, so it is configured first.
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_details).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_details"),
                    title = stringResource(R.string.discord_advanced_show_details),
                    text = stringResource(R.string.discord_advanced_show_details_text),
                    isChecked = showDetails,
                    onCheckedChange = { showDetails = it },
                    icon = R.drawable.musical_notes
                )
            }
            // The details line is hidden (animated) with its toggle.
            AnimatedVisibility(visible = showDetails) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_details).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_details"),
                        title = stringResource(R.string.discord_advanced_details),
                        text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_details),
                        selectedValueText = detailsTemplate.ifEmpty { DiscordActivityBuilder.DEFAULT_DETAILS_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showDetailsDialog = true }
                    )
                }
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_state).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_state"),
                    title = stringResource(R.string.discord_advanced_show_state),
                    text = stringResource(R.string.discord_advanced_show_state_text),
                    isChecked = showState,
                    onCheckedChange = { showState = it },
                    // The state line is the artist line — its own icon, distinct from the
                    // Aa template entry below it.
                    icon = R.drawable.artist
                )
            }
            AnimatedVisibility(visible = showState) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_state).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_state"),
                        title = stringResource(R.string.discord_advanced_state),
                        text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_state),
                        selectedValueText = stateTemplate.ifEmpty { DiscordActivityBuilder.DEFAULT_STATE_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showStateDialog = true }
                    )
                }
            }
            // PW-3: pause presence on/off (default on = current ⏸︎ behavior) — the toggle
            // comes first.

            // With it off, the paused presence is never sent, so the paused line template
            // is hidden (animated) behind it.
            if (search.inputValue.isBlank() || stringResource(R.string.discord_pause_presence).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_pause_presence"),
                    title = stringResource(R.string.discord_pause_presence),
                    text = stringResource(R.string.discord_pause_presence_text),
                    isChecked = pausePresenceEnabled,
                    onCheckedChange = { pausePresenceEnabled = it },
                    icon = R.drawable.pause
                )
            }
            // The paused line renders into the details line — hidden (animated) with it,
            // and with the pause presence off (nothing to configure behind it).
            AnimatedVisibility(visible = showDetails && pausePresenceEnabled) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_pause).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_pause"),
                        title = stringResource(R.string.discord_advanced_pause),
                        text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_pause),
                        selectedValueText = pauseTemplate.ifEmpty { stringResource(R.string.discord_presence_pause_default) },
                        icon = R.drawable.text,
                        onClick = { showPauseDialog = true }
                    )
                }
            }
            // The 60 s auto-clear of the stale presence after a pause (advanced option,
            // default on): only reachable with the pause presence off.

            // With it on, the paused presence is the active state and stays as long as
            // the player is paused — nothing to auto-clear, the option is hidden (animated).
            AnimatedVisibility(visible = !pausePresenceEnabled) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_pause_auto_clear).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_pause_auto_clear"),
                        title = stringResource(R.string.discord_advanced_pause_auto_clear),
                        text = stringResource(R.string.discord_advanced_pause_auto_clear_text),
                        isChecked = pauseClearEnabled,
                        onCheckedChange = { pauseClearEnabled = it },
                        icon = R.drawable.trash
                    )
                }
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_images)) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_artwork).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_artwork"),
                    title = stringResource(R.string.discord_advanced_show_artwork),
                    text = stringResource(R.string.discord_advanced_show_artwork_text),
                    isChecked = showArtwork,
                    onCheckedChange = { showArtwork = it },
                    icon = R.drawable.image
                )
            }
            // Image tooltips (advanced mode): the hover text over the artwork / the app
            // logo — each customizable with the template placeholders (empty = default).

            // No artwork = no hover tooltip — the template is hidden with it (animated).
            AnimatedVisibility(visible = showArtwork) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_image_text).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_image_text"),
                        title = stringResource(R.string.discord_advanced_image_text),
                        text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_image_text),
                        selectedValueText = largeImageText.ifEmpty { DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showLargeImageTextDialog = true }
                    )
                }
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_small_image).contains(search.inputValue, true)) {
                // The app logo keeps its own colors (import-menu rendering) — the shared
                // switch entry would tint it into a flat accent disc.
                DiscordLogoSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_small_image"),
                    title = stringResource(R.string.discord_advanced_show_small_image),
                    text = stringResource(R.string.discord_advanced_show_small_image_text),
                    onClick = { showSmallImage = !showSmallImage },
                    trailingContent = {
                        Switch(
                            checked = showSmallImage,
                            onCheckedChange = { showSmallImage = it },
                            checkedThumbColor = colorPalette().textSecondary,
                            checkedTrackColor = colorPalette().accent.copy(alpha = 0.3f),
                            uncheckedThumbColor = colorPalette().textSecondary,
                            uncheckedTrackColor = colorPalette().textSecondary.copy(alpha = 0.3f)
                        )
                    }
                )
            }
            // No app logo = no hover tooltip — the template is hidden with it (animated).
            AnimatedVisibility(visible = showSmallImage) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_logo_text).contains(search.inputValue, true)) {
                    // The default stays the placeholder template — the manager resolves it
                    // live to "v<app version>" when building the presence.
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_logo_text"),
                        title = stringResource(R.string.discord_advanced_logo_text),
                        text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_logo_text),
                        selectedValueText = smallImageText.ifEmpty { DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showSmallImageTextDialog = true }
                    )
                }
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_progress)) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_timestamps).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_timestamps"),
                    title = stringResource(R.string.discord_advanced_show_timestamps),
                    text = stringResource(R.string.discord_advanced_show_timestamps_text),
                    isChecked = showTimestamps,
                    onCheckedChange = { showTimestamps = it },
                    icon = R.drawable.playbackduration
                )
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_buttons)) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button1).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_button1"),
                    title = stringResource(R.string.discord_advanced_button1),
                    text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_button1),
                    isChecked = btn1Enabled,
                    onCheckedChange = { btn1Enabled = it },
                    // Button 1 is the "Get N-Zik" GitHub action by default — give its toggle a
                    // distinct icon so it is not confused with Button 2 (ytmusic).

                    // Reviewer finding: the two toggles shared one copy-pasted icon.
                    icon = R.drawable.github_icon
                )
            }
            // Content transition (Last.fm card pattern): the button sub-entries animate
            // in/out with the default expand/collapse + fade when the toggle flips.

            // Column: AnimatedVisibility lays its content out in a Box, so the two
            // sibling entries must be stacked explicitly.

            // Otherwise they overlap (rendered on top of each other at the same position).
            AnimatedVisibility(visible = btn1Enabled) {
                Column {
                    if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button_label).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_button1.label"),
                            title = "${stringResource(R.string.discord_advanced_button1)} — ${stringResource(R.string.discord_advanced_button_label)}",
                            text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_button1_label),
                            selectedValueText = btn1Label.ifEmpty { stringResource(R.string.discord_presence_button_get_nzik) },
                            icon = R.drawable.text,
                            onClick = { showBtn1LabelDialog = true }
                        )
                    }
                    if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button_url).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_button1.url"),
                            title = "${stringResource(R.string.discord_advanced_button1)} — ${stringResource(R.string.discord_advanced_button_url)}",
                            text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_button1_url),
                            selectedValueText = btn1Url.ifEmpty { DiscordActivityBuilder.DEFAULT_BUTTON1_URL },
                            icon = R.drawable.open,
                            onClick = { showBtn1UrlDialog = true }
                        )
                    }
                }
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button2).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_button2"),
                    title = stringResource(R.string.discord_advanced_button2),
                    text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_button2),
                    isChecked = btn2Enabled,
                    onCheckedChange = { btn2Enabled = it },
                    icon = R.drawable.ytmusic
                )
            }
            AnimatedVisibility(visible = btn2Enabled) {
                Column {
                    if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button_label).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_button2.label"),
                            title = "${stringResource(R.string.discord_advanced_button2)} — ${stringResource(R.string.discord_advanced_button_label)}",
                            text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_button2_label),
                            selectedValueText = btn2Label.ifEmpty { stringResource(R.string.discord_presence_button_listen_ytmusic) },
                            icon = R.drawable.text,
                            onClick = { showBtn2LabelDialog = true }
                        )
                    }
                    if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button_url).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_button2.url"),
                            title = "${stringResource(R.string.discord_advanced_button2)} — ${stringResource(R.string.discord_advanced_button_url)}",
                            text = stringResource(R.string.settings_desc_accounts_social_discord_discord_advanced_button2_url),
                            selectedValueText = btn2Url.ifEmpty { DiscordActivityBuilder.DEFAULT_BUTTON2_URL },
                            icon = R.drawable.open,
                            onClick = { showBtn2UrlDialog = true }
                        )
                    }
                }
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_connection)) {
            // Close the RPC connection after 10 min with no media event (advanced option,
            // default on = current behavior) — off keeps the connection open.
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_idle_close).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_idle_close"),
                    title = stringResource(R.string.discord_advanced_idle_close),
                    text = stringResource(R.string.discord_advanced_idle_close_text),
                    isChecked = idleCloseEnabled,
                    onCheckedChange = { idleCloseEnabled = it },
                    icon = R.drawable.link
                )
            }
            // Refresh tick interval while playing (advanced option, default 5 s) — the
            // manager re-reads it at every tick, so the change applies live (hot apply).
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_refresh_interval).contains(search.inputValue, true)) {
                DiscordRefreshIntervalEntry()
            }
        }
    }

    DialogPresence(visible = showActivityTypeDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.discord_activity_type),
            selectedValue = activityType,
            values = listOf(2, 0, 3, 5),
            onValueSelected = { activityType = it },
            onDismiss = { showActivityTypeDialog = false },
            valueText = { value ->
                when (value) {
                    0 -> stringResource(R.string.discord_activity_playing)
                    3 -> stringResource(R.string.discord_activity_watching)
                    5 -> stringResource(R.string.discord_activity_competing)
                    else -> stringResource(R.string.discord_activity_listening)
                }
            }
        )
    }
    DialogPresence(visible = showNameDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_name),
            value = activityName,
            placeholderValue = stringResource(R.string.discord_presence_name),
            onDone = { activityName = it },
            onDismiss = { showNameDialog = false }
        )
    }
    DialogPresence(visible = showStateDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_state),
            value = stateTemplate,
            placeholderValue = DiscordActivityBuilder.DEFAULT_STATE_TEMPLATE,
            onDone = { stateTemplate = it },
            onDismiss = { showStateDialog = false }
        )
    }
    DialogPresence(visible = showDetailsDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_details),
            value = detailsTemplate,
            placeholderValue = DiscordActivityBuilder.DEFAULT_DETAILS_TEMPLATE,
            onDone = { detailsTemplate = it },
            onDismiss = { showDetailsDialog = false }
        )
    }
    DialogPresence(visible = showPauseDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_pause),
            value = pauseTemplate,
            placeholderValue = stringResource(R.string.discord_presence_pause_default),
            onDone = { pauseTemplate = it },
            onDismiss = { showPauseDialog = false }
        )
    }
    DialogPresence(visible = showBtn1LabelDialog) {
        DiscordTemplateFieldDialog(
            title = "${stringResource(R.string.discord_advanced_button1)} — ${stringResource(R.string.discord_advanced_button_label)}",
            value = btn1Label,
            placeholderValue = stringResource(R.string.discord_presence_button_get_nzik),
            onDone = { btn1Label = it },
            onDismiss = { showBtn1LabelDialog = false }
        )
    }
    DialogPresence(visible = showBtn1UrlDialog) {
        DiscordTemplateFieldDialog(
            title = "${stringResource(R.string.discord_advanced_button1)} — ${stringResource(R.string.discord_advanced_button_url)}",
            value = btn1Url,
            placeholderValue = DiscordActivityBuilder.DEFAULT_BUTTON1_URL,
            onDone = { btn1Url = it },
            onDismiss = { showBtn1UrlDialog = false }
        )
    }
    DialogPresence(visible = showBtn2LabelDialog) {
        DiscordTemplateFieldDialog(
            title = "${stringResource(R.string.discord_advanced_button2)} — ${stringResource(R.string.discord_advanced_button_label)}",
            value = btn2Label,
            placeholderValue = stringResource(R.string.discord_presence_button_listen_ytmusic),
            onDone = { btn2Label = it },
            onDismiss = { showBtn2LabelDialog = false }
        )
    }
    DialogPresence(visible = showBtn2UrlDialog) {
        DiscordTemplateFieldDialog(
            title = "${stringResource(R.string.discord_advanced_button2)} — ${stringResource(R.string.discord_advanced_button_url)}",
            value = btn2Url,
            placeholderValue = DiscordActivityBuilder.DEFAULT_BUTTON2_URL,
            onDone = { btn2Url = it },
            onDismiss = { showBtn2UrlDialog = false }
        )
    }
    DialogPresence(visible = showLargeImageTextDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_image_text),
            value = largeImageText,
            placeholderValue = DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE,
            onDone = { largeImageText = it },
            onDismiss = { showLargeImageTextDialog = false }
        )
    }
    DialogPresence(visible = showSmallImageTextDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_logo_text),
            value = smallImageText,
            placeholderValue = DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE,
            onDone = { smallImageText = it },
            onDismiss = { showSmallImageTextDialog = false }
        )
    }
}

/**
 * Discord section only (item 6): template field dialog with placeholder chips
 * (upstream TemplateFieldDialog equivalent). When the field is empty, the effective
 * [placeholderValue] (the default that gets used) is shown in the field background.
 *
 * The buttons follow the toolbar settings dialogs (ToggleListDialog) pattern —
 * Reset, Cancel, OK — with the same look and behavior:
 * - Reset: clears the draft to the built-in default (empty value — the default
 *   template applies again) without saving; the dialog stays open so the user can
 *   confirm with OK or discard with Cancel.
 * - Cancel: discards the draft and closes the dialog.
 * - OK: saves the draft, closes the dialog and confirms with the "preference saved"
 *   toast, exactly like the toolbar dialogs.
 */
@Composable
private fun DiscordTemplateFieldDialog(
    title: String,
    value: String,
    placeholderValue: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textState = remember { mutableStateOf(value) }
    var text by textState
    val actions = remember { DiscordTemplateFieldActions(textState) }
    val palette = colorPalette()
    DefaultDialog(onDismiss = onDismiss, title = title,
        footer = {
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.reset),
                        textColor = palette.textDisabled,
                        onClick = { actions.reset() }
                    ),
                    DialogButton(
                        text = stringResource(R.string.cancel),
                        textColor = palette.red.copy(alpha = 0.3f),
                        onClick = onDismiss
                    ),
                    DialogButton(
                        text = stringResource(R.string.ok),
                        primary = true,
                        onClick = { actions.confirm(onDone, onDismiss) }
                    )
                )
            )
        }
    ) {
        // Palette-explicit colors: the dialog background is colorPalette().background1,
        // so the default Material3 scheme (dark onSurface text) made the typed text

        // and the placeholder unreadable on dark themes.
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholderValue, style = typography().s.copy(color = palette.textSecondary)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                cursorColor = palette.accent,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.textSecondary,
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.discord_advanced_placeholders),
            style = typography().s.copy(color = palette.textSecondary)
        )
        Spacer(Modifier.height(12.dp))
        // FlowRow: the chips must wrap on narrow dialogs — a single Row clips the
        // later placeholders ({song.id} and beyond became invisible on small screens).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DiscordTemplateRenderer.PLACEHOLDERS.forEach { placeholder ->
                GlassCustomChip(palette.accent.copy(alpha = 0.15f), onClick = { text += placeholder }) { chipModifier ->
                    Text(
                        text = placeholder,
                        style = typography().s.copy(color = palette.accent),
                        modifier = chipModifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

    }
}

/** Discord section only: the image text tooltip popup — shown when the preview card's artwork or app logo is tapped, mirroring Discord's hover tooltip (the text shown over the large artwork / the small app logo). */
@Composable
private fun DiscordImageTooltipDialog(
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
                        text = stringResource(R.string.ok),
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

/**
 * Discord section only: a settings row carrying the real app logo (the "Show app
 * logo" toggle and the logo-text entry). The shared entry components tint every
 * icon with the accent color, which would flatten the logo into a solid disc —
 * the logo is drawn in its own colors (like the import menu), on the standard
 * 32.dp accent background box, 18.dp untinted icon.
 */
@Composable
private fun DiscordLogoSettingsEntry(
    modifier: Modifier = Modifier,
    title: String,
    text: String,
    onClick: () -> Unit,
    trailingContent: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // The app logo in its own colors (untinted — the accent tint would
                // flatten it into a solid disc), on the standard entry background box.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .iconBadgeBackground(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher),
                        tint = Color.Unspecified,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = title,
                        style = typography().s.semiBold.copy(
                            color = colorPalette().text
                        )
                    )
                    if (text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicText(
                            text = text,
                            style = typography().xs.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }

                trailingContent()
            }
        }
    }
}

/**
 * Discord section only (item 6): live preview of the Discord RPC card, rendered with
 * the same [DiscordActivityBuilder] the presence manager uses, so what you see here is
 * what gets sent. When nothing is playing, the fallback card shows the template
 * variables as-is (the current customization) with the app's standard missing-image
 * icon (the ImageCacheFactory fallback drawable). [tick] drives recomposition so the
 * card stays reactive to settings changes even while paused/idle. [mode] pins the
 * preview to a playback state (0 = playing, 1 = paused, 2 = idle) from the state
 * chips below the card; a pinned playing/paused state falls back to idle when no
 * media is loaded.
 */
@Composable
private fun DiscordRpcPreviewCard(
    // Player (not ExoPlayer): the guarded Listen Together facade is a plain Player — this
    // preview only reads currentMediaItem (spec-listen-together-guest-lock-hardening).
    player: Player?,
    positionMs: Long,
    tick: Int,
    settings: DiscordAdvancedSettings,
    // Pinned state from the state chips: 0 = playing, 1 = paused, 2 = nothing playing
    // (-1 = follow the real player state, resolved by the caller).
    mode: Int
) {
    val palette = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Inset to match the section content (13.dp = the "Account info"
            // indent and the "Rich Presence preview" title inset).
            .padding(horizontal = 13.dp)
            .padding(bottom = 12.dp)
            // background2 (not background1): a visibly distinct card surface.
            .background(palette.background2, uiRoundnessShape())
            .clip(uiRoundnessShape())
            .padding(12.dp)
    ) {
        val item = player?.currentMediaItem
        val info = item?.let { item ->
            val cleanTitle = cleanPrefix(item.mediaMetadata.title?.toString() ?: "").takeIf { it.isNotBlank() }
                ?: stringResource(R.string.unknown_title)
            DiscordMediaInfo(
                // The explicit marker, exactly like the real presence / notification:
                // prepended when the app's MediaItem.isExplicit flags the track.
                title = if (item.isExplicit) "\uD83C\uDD74 $cleanTitle" else cleanTitle,
                // Artist resolved metadata → DB, the same sources the real presence uses
                // (artistTextOrDb) — the preview stays "what you see is what gets sent".
                artist = item.artistTextWithFallback(),
                albumName = item.albumTitleWithFallback(),
                songId = item.mediaId
            )
        } ?: // Idle state: never used (the fallback card renders the templates as-is).
            DiscordMediaInfo("", "", null, "")
        // Pinned preview state (state chips): 0 = playing, 1 = paused, 2 = idle.
        // A pinned playing/paused preview without a loaded media renders the raw templates.

        // The paused variant shows the pause template, so the three variants stay
        // distinguishable even with nothing playing.
        val effectiveMode = mode.coerceIn(0, 2)
        val isIdle = effectiveMode == 2
        val isPlaying = effectiveMode == 0
        // Localized strings for the presence content (no hardcoded user-facing text).
        val strings = DiscordStrings(
            nameFallback = stringResource(R.string.discord_presence_name),
            buttonGetNZik = stringResource(R.string.discord_presence_button_get_nzik),
            buttonListenYtmusic = stringResource(R.string.discord_presence_button_listen_ytmusic),
            pausedLineDefault = stringResource(R.string.discord_presence_pause_default),
            unknownAlbum = stringResource(R.string.discord_template_unknown_album),
            appVersion = getVersionName(),
        )
        // PW-3: the pause presence is optional — when disabled, no paused update is
        // ever sent.

        // The real Discord keeps the last playing presence, so the preview shows the
        // playing card too (the paused variant does not exist).
        val content = when {
            isIdle ->
                // Fallback card: the template variables as-is (current customization).
                DiscordActivityBuilder.buildIdlePreview(settings, strings)
            isPlaying && item != null ->
                DiscordActivityBuilder.buildForPlaying(info, settings, strings)
            isPlaying ->
                // Pinned playing preview without media: the template variables as-is.
                DiscordActivityBuilder.buildIdlePreview(settings, strings)
            item != null && !settings.pausePresenceEnabled ->
                // Paused with the pause presence disabled: keep the playing card
                // (what the real presence shows).
                DiscordActivityBuilder.buildForPlaying(info, settings, strings)
            item != null ->
                DiscordActivityBuilder.buildForPlaying(info, settings, strings)
                    .copy(details = DiscordActivityBuilder.buildPausedLine(info, settings, strings))
            !settings.pausePresenceEnabled ->
                // Pinned paused preview without media, pause presence disabled: no
                // paused variant exists — the playing-style fallback.
                DiscordActivityBuilder.buildIdlePreview(settings, strings)
            else ->
                // Pinned paused preview without media: the pause template as-is.
                DiscordActivityBuilder.buildIdlePreview(settings, strings)
                    .copy(details = settings.pauseTemplate.ifBlank { strings.pausedLineDefault })
        }
        // Header line ("Listening N-Zik" style, Discord card layout): type + activity name.
        val typeLabel = when (settings.activityType) {
            0 -> stringResource(R.string.discord_activity_playing)
            3 -> stringResource(R.string.discord_activity_watching)
            5 -> stringResource(R.string.discord_activity_competing)
            else -> stringResource(R.string.discord_activity_listening)
        }
        // PW-4: media artwork on the left (Discord card layout); the standard
        // missing-image icon when the media carries no artwork URI (or nothing is playing).
        val artworkUrl = item?.mediaMetadata?.artworkUri?.toString()?.takeIf { it.isNotBlank() }
        // The section toggles hide the matching card parts (advanced mode only — normal
        // mode keeps the frozen identity, exactly like the real presence).
        val showArtworkSection = !settings.advancedMode || settings.showArtwork
        val showSmallImageSection = !settings.advancedMode || settings.showSmallImage
        val showTimestampsSection = !settings.advancedMode || settings.showTimestamps
        // The large image text (the artwork's Discord hover tooltip): rendered as its own
        // card line so the customization is visible in the preview (on Discord itself it

        // only shows as a tooltip over the artwork). A custom template renders with the
        // media (as-is on the fallback card).

        // The built-in default is the live album value when the app knows it,
        // otherwise the current configuration's "details - state" expression —

        // the same the real presence computes (manager sendActivity). The paused
        // line renders into details, so the paused state shows in the image text too.
        val imageText =
            if (settings.advancedMode && settings.largeImageTextTemplate.isNotBlank()) {
                if (item != null) {
                    DiscordTemplateRenderer.render(
                        settings.largeImageTextTemplate,
                        info.title,
                        info.artist,
                        info.albumName,
                        info.songId,
                        strings.unknownAlbum,
                        strings.appVersion,
                    )
                } else {
                    settings.largeImageTextTemplate
                }
            } else {
                // Built-in default, exactly like the real presence: the album value when
                // the app knows it (live — metadata then DB).

                // Unknown album while media is loaded → the current configuration's
                // "details - state" expression (never a rendered "Unknown Album").

                // No media (fallback card) → the current image template,
                // i.e. the built-in album template as-is.
                if (item != null) {
                    info.albumName?.takeIf { it.isNotBlank() }
                        ?: if (content.state.isNotBlank()) "${content.details} - ${content.state}" else content.details
                } else {
                    DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE
                }
            }
        // The app logo tooltip content: rendered while media is loaded (the same value
        // the real presence sends).

        // The template as-is otherwise (the current template IS the fallback).
        // The artwork tooltip reuses the card line above (`imageText`): rendered content

        // with media, template as-is without.
        val smallImageTooltipText =
            if (item != null) {
                DiscordTemplateRenderer.render(
                    settings.smallImageTextTemplate.ifBlank { DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE },
                    info.title,
                    info.artist,
                    info.albumName,
                    info.songId,
                    strings.unknownAlbum,
                    strings.appVersion,
                )
            } else {
                settings.smallImageTextTemplate.ifBlank { DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE }
            }
        // Tooltip popups: tapping the artwork / the app logo shows the matching image
        // text, mirroring Discord's hover tooltips.
        val cardContext = LocalContext.current
        var showLargeImageTooltip by remember { mutableStateOf(false) }
        var showSmallImageTooltip by remember { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header line, full width ABOVE the artwork row (Discord card layout):
            // type + activity name.
            Text(
                text = "$typeLabel ${content.name}",
                // The card mimics the (dark) Discord rich presence: all text white.
                style = typography().s.copy(color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (showArtworkSection) {
                    // Tap = the large image text tooltip popup (the Discord hover equivalent).
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { showLargeImageTooltip = true },
                    ) {
                        if (artworkUrl != null) {
                            ImageCacheFactory.AsyncImage(
                                shape = RoundedCornerShape(12.dp),
                                thumbnailUrl = artworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            // Fallback artwork: the app's standard missing-image icon.

                            // The same drawable ImageCacheFactory uses for errors/absent
                            // thumbnails.
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_box),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        // Small image overlay at the artwork's bottom-right corner
                        // (Discord's small-image position — the tooltip is the app version).

                        // ic_launcher = the circular app icon (the one the import-CSV menu
                        // uses); ic_launcher_box is a square and reads badly at this size.
                        if (showSmallImageSection) {
                            // Tap = the small image text tooltip popup (the app version).
                            Image(
                                painter = painterResource(R.drawable.ic_launcher),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { showSmallImageTooltip = true }
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    // The main (bold) line: the rendered details. The idle fallback card
                    // (no media) keeps the N-Zik identity name when the details template

                    // is empty; a DISABLED details section (playing/paused) hides the line
                    // entirely — the preview mirrors the real presence, where a disabled

                    // section is omitted, not replaced.
                    val boldLine = when {
                        content.details.isNotEmpty() -> content.details
                        isIdle -> content.name
                        else -> null
                    }
                    if (boldLine != null) {
                        Text(
                            text = boldLine,
                            style = typography().m.copy(color = Color.White),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Second body line: the state (the artist by default).
                    if (content.state.isNotEmpty()) {
                        Text(
                            text = content.state,
                            style = typography().s.copy(color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Third body line: the large image text (hidden with the artwork
                    // section — no artwork = no tooltip).
                    if (showArtworkSection && imageText.isNotEmpty()) {
                        Text(
                            text = imageText,
                            style = typography().xs.copy(color = palette.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val duration = player?.duration ?: 0L
                    // The progress bar shows in EVERY preview state (playing / paused /
                    // fallback): real media -> live progress.

                    // No media -> an empty bar with "0:00 / 0:00" (the card stays complete).
                    // Material's LinearProgressIndicator, the app's standard bar.
                    if (showTimestampsSection) {
                        val progress =
                            if (duration > 0) (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            else 0f
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = palette.accent,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            strokeCap = StrokeCap.Round,
                            progress = { progress },
                        )
                        // Time labels under the bar (Discord shows "elapsed / total").
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatPreviewTime(if (duration > 0) positionMs else 0L),
                                style = typography().xs.copy(color = palette.textSecondary),
                                maxLines = 1
                            )
                            Text(
                                text = formatPreviewTime(duration),
                                style = typography().xs.copy(color = palette.textSecondary),
                                maxLines = 1
                            )
                        }
                    }
                    if (content.buttons.isNotEmpty()) {
                        // FlowRow: custom button labels can be long — wrapping keeps the
                        // buttons from overflowing/overlapping each other in the card.
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            content.buttons.forEach { button ->
                                // Tap = open the button link in the external browser (the
                                // real Discord buttons open the same URL).
                                GlassCustomChip(palette.accent.copy(alpha = 0.15f),
                                    onClick = { cardContext.openDiscordButtonUrl(button.url) }) { chipModifier ->
                                    Text(
                                        text = button.label,
                                        style = typography().s.copy(color = Color.White),
                                        modifier = chipModifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // The image text tooltip popups (tap targets: the artwork and the app logo).
        if (showLargeImageTooltip) {
            DiscordImageTooltipDialog(
                title = stringResource(R.string.discord_advanced_image_text),
                text = imageText,
                onDismiss = { showLargeImageTooltip = false },
            )
        }
        if (showSmallImageTooltip) {
            DiscordImageTooltipDialog(
                title = stringResource(R.string.discord_advanced_logo_text),
                text = smallImageTooltipText,
                onDismiss = { showSmallImageTooltip = false },
            )
        }
    }
}

/** Discord section only: "m:ss" time label for the preview card's progress bar (pure time formatting — not user-facing text, nothing to localize). */
private fun formatPreviewTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** Discord section only: opens a preview card button URL in the external browser — the real Discord buttons open the same link, and the preview must let the user verify where each button points. */
private fun Context.openDiscordButtonUrl(url: String) {
    if (url.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
