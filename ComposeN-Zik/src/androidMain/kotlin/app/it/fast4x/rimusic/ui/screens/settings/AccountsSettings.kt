package app.it.fast4x.rimusic.ui.screens.settings

import app.n_zik.android.BuildConfig
import app.n_zik.android.components.tab.Search
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults

import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.R

import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.settings.SyncStatusDialog
import app.n_zik.android.components.dialog.settings.clearAudioCache

import it.fast4x.innertube.utils.parseCookieString
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.extensions.discord.DiscordActivityBuilder
import app.n_zik.android.extensions.discord.DiscordAdvancedSettings
import app.n_zik.android.extensions.discord.DiscordLoginAndGetToken
import app.n_zik.android.extensions.discord.DiscordMediaInfo
import app.n_zik.android.extensions.discord.DiscordPresenceManager
import app.n_zik.android.extensions.discord.DiscordRpcError
import app.n_zik.android.extensions.discord.DiscordRpcErrorState
import app.n_zik.android.extensions.discord.DiscordTemplateFieldActions
import app.n_zik.android.extensions.discord.DiscordTemplateRenderer
import app.n_zik.android.components.settings.DisplayNameSettingsCard
import app.n_zik.android.components.settings.LastFmSettingsCard
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet

import app.it.fast4x.rimusic.ui.components.themed.DefaultDialog
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

import androidx.compose.foundation.text.BasicText

import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon

import androidx.compose.ui.res.painterResource
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.isExplicit
import app.it.fast4x.rimusic.utils.getVersionName
import app.n_zik.android.extensions.discord.discordAdvancedActivityTypeKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedDetailsTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedIdleCloseEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedNameKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseClearEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPausePresenceEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedStateTemplateKey
import app.n_zik.android.extensions.discord.isDiscordAdvancedModeKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.streamClientRestartNeededKey
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.playback.services.clearStreamCaches
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.enums.SyncDirection
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.autosyncArtistsKey
import app.it.fast4x.rimusic.utils.autosyncAlbumsKey
import app.it.fast4x.rimusic.utils.autosyncPlaylistsKey
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.syncPushSongLikeKey
import app.it.fast4x.rimusic.utils.syncPushAlbumBookmarkKey
import app.it.fast4x.rimusic.utils.syncPushArtistFollowKey
import app.it.fast4x.rimusic.utils.syncPushPlaylistKey
import app.it.fast4x.rimusic.utils.syncPushEpisodeKey
import app.it.fast4x.rimusic.utils.syncImportHistoryKey
import app.it.fast4x.rimusic.utils.syncImportLibrarySongsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedSongsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedAlbumsKey
import app.it.fast4x.rimusic.utils.syncImportEpisodesKey
import app.it.fast4x.rimusic.utils.syncCooldownKey
import app.it.fast4x.rimusic.utils.syncShowDetailsKey
import app.it.fast4x.rimusic.utils.syncBackgroundGuardKey

import app.it.fast4x.rimusic.utils.SyncOperation
import app.it.fast4x.rimusic.utils.SyncStatus
import app.it.fast4x.rimusic.utils.syncStatus
import app.it.fast4x.rimusic.utils.getLastSyncTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.it.fast4x.rimusic.utils.isAtLeastAndroid7
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.useLoginForBrowseKey
import it.fast4x.innertube.Innertube

import app.it.fast4x.rimusic.utils.preferences

import app.it.fast4x.rimusic.utils.quickPicsDiscoverPageKey
import app.it.fast4x.rimusic.utils.quickPicsHomePageKey
import app.it.fast4x.rimusic.utils.quickPicsYtmQuickPicksKey
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.restartActivityKey
import androidx.core.content.edit
import app.it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import app.it.fast4x.rimusic.utils.ytAccountEmailKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytCookieExpiredKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.kreate.android.me.knighthat.utils.Toaster

import app.it.fast4x.rimusic.utils.clearAllSyncedData
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.queueSync
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.syncPushHistoryKey
import app.n_zik.android.extensions.discord.DiscordStrings
import app.n_zik.android.extensions.discord.discordAdvancedLargeImageTextKey
import app.n_zik.android.utils.albumTitleWithFallback
import app.n_zik.android.utils.artistTextWithFallback
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.extensions.discord.discordAdvancedShowArtworkKey
import app.n_zik.android.extensions.discord.discordAdvancedShowDetailsKey
import app.n_zik.android.extensions.discord.discordAdvancedShowSmallImageKey
import app.n_zik.android.extensions.discord.discordAdvancedShowStateKey
import app.n_zik.android.extensions.discord.discordAdvancedShowTimestampsKey
import app.n_zik.android.extensions.discord.discordAdvancedSmallImageTextKey
import app.n_zik.android.typography
import it.fast4x.innertube.Innertube.cookie


@Composable
fun SettingIcon(@DrawableRes icon: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = colorPalette().accent.copy(alpha = 0.1f),
                shape = uiRoundnessShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            tint = colorPalette().accent,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun DefaultAccountsSettings() {
    var restartActivity by rememberPreference(restartActivityKey, false)
    restartActivity = false

    var isYouTubeLoginEnabled by rememberEncryptedPreference(enableYouTubeLoginKey, false)
    isYouTubeLoginEnabled = false

    var isYouTubeSyncEnabled by rememberEncryptedPreference(enableYouTubeSyncKey, false)
    isYouTubeSyncEnabled = false

    var isDiscordPresenceEnabled by rememberEncryptedPreference(isDiscordPresenceEnabledKey, false)
    isDiscordPresenceEnabled = false

    // PW-1/PW-2/PW-3 + review: the Discord advanced settings reset with the section
    // (browsing was removed; the user-status no longer exists).
    var isDiscordAdvancedMode by rememberEncryptedPreference(isDiscordAdvancedModeKey, false)
    isDiscordAdvancedMode = false
    var discordAdvancedActivityType by rememberEncryptedPreference(discordAdvancedActivityTypeKey, 2)
    discordAdvancedActivityType = 2
    var discordAdvancedPausePresenceEnabled by rememberEncryptedPreference(discordAdvancedPausePresenceEnabledKey, true)
    discordAdvancedPausePresenceEnabled = true
    var discordAdvancedName by rememberEncryptedPreference(discordAdvancedNameKey, "")
    discordAdvancedName = ""
    var discordAdvancedStateTemplate by rememberEncryptedPreference(discordAdvancedStateTemplateKey, "")
    discordAdvancedStateTemplate = ""
    var discordAdvancedDetailsTemplate by rememberEncryptedPreference(discordAdvancedDetailsTemplateKey, "")
    discordAdvancedDetailsTemplate = ""
    var discordAdvancedPauseTemplate by rememberEncryptedPreference(discordAdvancedPauseTemplateKey, "")
    discordAdvancedPauseTemplate = ""
    var discordAdvancedButton1Enabled by rememberEncryptedPreference(discordAdvancedButton1EnabledKey, true)
    discordAdvancedButton1Enabled = true
    var discordAdvancedButton1Label by rememberEncryptedPreference(discordAdvancedButton1LabelKey, "")
    discordAdvancedButton1Label = ""
    var discordAdvancedButton1Url by rememberEncryptedPreference(discordAdvancedButton1UrlKey, "")
    discordAdvancedButton1Url = ""
    var discordAdvancedButton2Enabled by rememberEncryptedPreference(discordAdvancedButton2EnabledKey, true)
    discordAdvancedButton2Enabled = true
    var discordAdvancedButton2Label by rememberEncryptedPreference(discordAdvancedButton2LabelKey, "")
    discordAdvancedButton2Label = ""
    var discordAdvancedButton2Url by rememberEncryptedPreference(discordAdvancedButton2UrlKey, "")
    discordAdvancedButton2Url = ""
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("BatteryLife")
@ExperimentalAnimationApi
@Composable
fun AccountsSettings() {
    val search = Search()

    val context = LocalContext.current
    
    var restartActivity by rememberPreference(restartActivityKey, false)
    var restartService by rememberSaveable { mutableStateOf(false) }
    var showClearSyncDialog by remember { mutableStateOf(false) }

    var visitorData by rememberEncryptedPreference(key = ytVisitorDataKey, defaultValue = "")
    var dataSyncId by rememberEncryptedPreference(key = ytDataSyncIdKey, defaultValue = "")
    var cookie by rememberEncryptedPreference(key = ytCookieKey, defaultValue = "")

    var accountName by rememberEncryptedPreference(key = ytAccountNameKey, defaultValue = "")
    var accountEmail by rememberEncryptedPreference(key = ytAccountEmailKey, defaultValue = "")
    var accountChannelHandle by rememberEncryptedPreference(key = ytAccountChannelHandleKey, defaultValue = "")
    var accountThumbnail by rememberEncryptedPreference(key = ytAccountThumbnailKey, defaultValue = "")
    var isLoggedIn = remember(cookie, app.n_zik.android.MainApplication.cookieStatus) {
        "SAPISID" in parseCookieString(cookie) || app.n_zik.android.MainApplication.cookieStatus in listOf(
            app.n_zik.android.MainApplication.CookieStatus.VALID,
            app.n_zik.android.MainApplication.CookieStatus.INVALID,
            app.n_zik.android.MainApplication.CookieStatus.EXPIRED
        )
    }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {

        HeaderWithIcon(
            title = stringResource(R.string.tab_accounts),
            iconId = R.drawable.person,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        SettingsDescription(
            text = stringResource(R.string.accounts_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        ) 

        search.ToolBarButton()
        search.SearchBar( this )

        /* Removed Spacer */

        DisplayNameSettingsCard()

        // YouTube Music Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = tween(600),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.youtube_music),
                icon = R.drawable.ytmusic,
                content = {
                    // rememberEncryptedPreference only works correct with API 24 and up
                    var isYouTubeLoginEnabled by rememberEncryptedPreference(enableYouTubeLoginKey, false)
                    var isYouTubeSyncEnabled by rememberEncryptedPreference(enableYouTubeSyncKey, false)
                    var loginYouTube by remember { mutableStateOf(false) }
                    val binder = LocalPlayerServiceBinder.current

                    LaunchedEffect(isYouTubeLoginEnabled) {
                        if (!isYouTubeLoginEnabled && isYouTubeSyncEnabled) {
                            isYouTubeSyncEnabled = false
                        }
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.enable_youtube_music_login).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.enable_youtube_music_login),
                            text = "",
                            isChecked = isYouTubeLoginEnabled,
                            onCheckedChange = {
                                isYouTubeLoginEnabled = it
                                app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger++
                                if (!it) {
                                    // Only clear Innertube singleton (stop using account)
                                    // Keep account info so user doesn't have to reconnect
                                    Innertube.cookie = null
                                    Innertube.dataSyncId = null
                                    Innertube.visitorData = Innertube.DEFAULT_VISITOR_DATA
                                    Innertube.useLoginForBrowse = false
                                    // Reset cookie status
                                    app.n_zik.android.MainApplication.cookieStatus = app.n_zik.android.MainApplication.CookieStatus.NOT_LOGGED_IN
                                    appContext().preferences.edit().remove(ytCookieExpiredKey).apply()

                                    // Disable sync and all sub-toggles
                                    appContext().encryptedPreferences.edit {
                                        putBoolean(enableYouTubeSyncKey, false)
                                    }
                                    appContext().preferences.edit {
                                        putBoolean(useLoginForBrowseKey, false)
                                        putBoolean(autosyncArtistsKey, false)
                                        putBoolean(autosyncAlbumsKey, false)
                                        putBoolean(autosyncPlaylistsKey, false)
                                        putBoolean(autosyncLikesKey, false)
                                        putBoolean(syncImportHistoryKey, false)
                                        putBoolean(syncImportLibrarySongsKey, false)
                                        putBoolean(syncImportUploadedSongsKey, false)
                                        putBoolean(syncImportUploadedAlbumsKey, false)
                                        putBoolean(syncImportEpisodesKey, false)
                                        putBoolean(syncPushHistoryKey, false)
                                        putBoolean(syncPushSongLikeKey, false)
                                        putBoolean(syncPushAlbumBookmarkKey, false)
                                        putBoolean(syncPushArtistFollowKey, false)
                                        putBoolean(syncPushPlaylistKey, false)
                                        putBoolean(syncPushEpisodeKey, false)
                                    }

                                    // Clear cached data
                                    appContext().preferences.edit {
                                        remove(quickPicsHomePageKey)
                                        remove(quickPicsYtmQuickPicksKey)
                                        remove(quickPicsDiscoverPageKey)
                                    }
                                } else {
                                    // Re-enable: restore Innertube from saved preferences
                                    val savedCookie = appContext().encryptedPreferences.getString(ytCookieKey, "") ?: ""
                                    if (savedCookie.isNotEmpty()) {
                                        Innertube.cookie = savedCookie
                                        Innertube.dataSyncId = appContext().encryptedPreferences.getString(ytDataSyncIdKey, null)
                                        Innertube.visitorData = appContext().encryptedPreferences.getString(ytVisitorDataKey, null) ?: Innertube.DEFAULT_VISITOR_DATA
                                        // Only activate browse if we have real credentials
                                        if ("SAPISID" in parseCookieString(savedCookie)) {
                                            appContext().preferences.edit().putBoolean(useLoginForBrowseKey, true).apply()
                                            Innertube.useLoginForBrowse = true
                                        }
                                    }
                                    // Reset cookie status — will be revalidated on next playback
                                    app.n_zik.android.MainApplication.cookieStatus = app.n_zik.android.MainApplication.CookieStatus.NOT_LOGGED_IN
                                    appContext().preferences.edit().remove(ytCookieExpiredKey).apply()
                                }
                                // Clear stream caches and mark restart needed
                                clearStreamCaches()
                                appContext().preferences.edit().putBoolean(streamClientRestartNeededKey, true).apply()
                                // Clear audio cache
                                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                    clearAudioCache(binder?.cache)
                                }
                                Toaster.i(R.string.preferred_stream_client_changed)
                                Toaster.w(R.string.stream_client_redownload_recommendation)
                            },
                            icon = R.drawable.ytmusic
                        )
                    }

                    val isStreamRestartNeeded by rememberPreference(streamClientRestartNeededKey, false)
                    RestartPlayerService(
                        restartService = isStreamRestartNeeded,
                        onRestart = {
                            appContext().preferences.edit().putBoolean(streamClientRestartNeededKey, false).apply()
                        }
                    )

                    AnimatedVisibility(visible = isYouTubeLoginEnabled) {
                        Column {
                            if (isLoggedIn) {
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
                                                .padding(top = 8.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (accountThumbnail.isNotEmpty()) {
                                                ImageCacheFactory.AsyncImage(
                                                    thumbnailUrl = accountThumbnail,
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
                                                    .weight(1f)
                                                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Text(
                                                        text = accountName,
                                                        color = colorPalette().text,
                                                        modifier = Modifier.padding(start = 5.dp),
                                                        style = typography().m
                                                    )
                                                    if (accountChannelHandle.isNotEmpty()) {
                                                        Text(
                                                            text = accountChannelHandle,
                                                            color = colorPalette().textSecondary,
                                                            modifier = Modifier.padding(start = 5.dp),
                                                            style = typography().xs
                                                        )
                                                    }
                                                    if (accountEmail.isNotEmpty()) {
                                                        Text(
                                                            text = accountEmail,
                                                            color = colorPalette().textSecondary,
                                                            modifier = Modifier.padding(start = 5.dp),
                                                            style = typography().xs
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Cookie status warning — show when cookie is invalid/expired
                            if (isLoggedIn && app.n_zik.android.MainApplication.cookieStatus in listOf(app.n_zik.android.MainApplication.CookieStatus.INVALID, app.n_zik.android.MainApplication.CookieStatus.EXPIRED)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.information),
                                        contentDescription = null,
                                        tint = colorPalette().textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.error_cookie_invalid),
                                        color = colorPalette().textSecondary,
                                        modifier = Modifier.padding(start = 8.dp),
                                        style = typography().xs
                                    )
                                }
                            }

                            if (search.inputValue.isBlank() || true) {
                                OtherSettingsEntry(
                                    title = if (isLoggedIn) stringResource(R.string.account_logoff) else stringResource(R.string.account_login),
                                    text = "",
                                    icon = if (isLoggedIn) R.drawable.logout else R.drawable.person,
                                    onClick = {
                                        if (isLoggedIn) {
                                            showClearSyncDialog = true
                                        } else {
                                            loginYouTube = true
                                        }
                                    }
                                )
                            }

                            CustomModalBottomSheet(
                                showSheet = loginYouTube,
                                onDismissRequest = {
                                    loginYouTube = false
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
                                YouTubeLogin(
                                    onLogin = { cookieRetrieved ->
                                        if (cookieRetrieved.contains("SAPISID")) {
                                            isLoggedIn = true
                                            loginYouTube = false
                                            // Clear expired flag on fresh login
                                            appContext().preferences.edit().putBoolean(ytCookieExpiredKey, false).apply()
                                            app.n_zik.android.MainApplication.cookieStatus = app.n_zik.android.MainApplication.CookieStatus.VALID
                                            // Re-enable useLoginForBrowse since we now have valid credentials
                                            appContext().preferences.edit().putBoolean(useLoginForBrowseKey, true).apply()
                                            Innertube.useLoginForBrowse = true
                                            // Force re-read account info from encrypted prefs (YouTubeLogin saved them)
                                            val ep = appContext().encryptedPreferences
                                            accountName = ep.getString(ytAccountNameKey, "") ?: ""
                                            accountEmail = ep.getString(ytAccountEmailKey, "") ?: ""
                                            accountChannelHandle = ep.getString(ytAccountChannelHandleKey, "") ?: ""
                                            accountThumbnail = ep.getString(ytAccountThumbnailKey, "") ?: ""
                                            Toaster.i( context.getString(R.string.youtube_login_successful) )
                                        }
                                    }
                                )
                            }

                            // Login for Browse option (must be enabled for sync to work)
                            if (isYouTubeLoginEnabled) {
                            var useLoginForBrowse by rememberPreference(useLoginForBrowseKey, true)
                            if (search.inputValue.isBlank() || stringResource(R.string.login_for_browse).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.login_for_browse),
                                    text = if (!isLoggedIn) stringResource(R.string.youtube_connect_first) else stringResource(R.string.login_for_browse_description),
                                    isChecked = isLoggedIn && useLoginForBrowse,
                                    enabled = isLoggedIn,
                                    onCheckedChange = {
                                        useLoginForBrowse = it
                                        Innertube.useLoginForBrowse = it
                                        if (!it) {
                                            // Reset sync and all sub-toggles when Login for Browse is disabled
                                            appContext().encryptedPreferences.edit {
                                                putBoolean(enableYouTubeSyncKey, false)
                                            }
                                            appContext().preferences.edit {
                                                putBoolean(autosyncArtistsKey, false)
                                                putBoolean(autosyncAlbumsKey, false)
                                                putBoolean(autosyncPlaylistsKey, false)
                                                putBoolean(autosyncLikesKey, false)
                                                putBoolean(syncImportHistoryKey, false)
                                                putBoolean(syncImportLibrarySongsKey, false)
                                                putBoolean(syncImportUploadedSongsKey, false)
                                                putBoolean(syncImportUploadedAlbumsKey, false)
                                                putBoolean(syncImportEpisodesKey, false)
                                                putBoolean(syncPushHistoryKey, false)
                                                putBoolean(syncPushSongLikeKey, false)
                                                putBoolean(syncPushAlbumBookmarkKey, false)
                                                putBoolean(syncPushArtistFollowKey, false)
                                                putBoolean(syncPushPlaylistKey, false)
                                                putBoolean(syncPushEpisodeKey, false)
                                            }
                                        }
                                    },
                                    icon = R.drawable.person
                                )
                            }
                            }


                        }
                    }
                }
            )
        }

        // ========== SYNC SETTINGS ==========
        val useLoginForBrowseSync by rememberPreference(useLoginForBrowseKey, true)
        val isYouTubeLoginActive by rememberEncryptedPreference(enableYouTubeLoginKey, false)

        AnimatedVisibility(
            visible = useLoginForBrowseSync && isYouTubeLoginActive,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = tween(600),
                initialScale = 0.9f
            )
        ) {
            var isYouTubeSyncEnabled by rememberEncryptedPreference(enableYouTubeSyncKey, false)
            val isSyncEnabled = isYouTubeSyncEnabled && useLoginForBrowseSync
            var syncDirection by rememberPreference(syncDirectionKey, SyncDirection.TWO_WAY)
            val coroutineScope = rememberCoroutineScope()
            val syncStatusState by syncStatus.collectAsStateWithLifecycle(initialValue = SyncStatus(), context = NzikDispatchers.DATA)
            val lastSyncTime = remember { getLastSyncTime() }

            // Re-sync state when Login for Browse changes (prefs reset externally)
            LaunchedEffect(useLoginForBrowseSync) {
                if (!useLoginForBrowseSync) {
                    isYouTubeSyncEnabled = false
                }
            }

            Column {
                // Sync data toggle + Sync Now + Direction
                SettingsSectionCard(
                    title = stringResource(R.string.sync_data_with_ytm_account),
                    icon = R.drawable.sync,
                    content = {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.sync_data_with_ytm_account),
                            text = stringResource(R.string.playlists_albums_artists_history_like_etc),
                            isChecked = isYouTubeSyncEnabled,
                            enabled = BuildConfig.BUILD_TYPE in listOf("debug", "dev", "dev32", "beta", "beta32"),
                            onCheckedChange = {
                                isYouTubeSyncEnabled = it
                                if (!it) {
                                    // Reset all sub-toggles when sync is disabled
                                    appContext().preferences.edit {
                                        putBoolean(autosyncArtistsKey, false)
                                        putBoolean(autosyncAlbumsKey, false)
                                        putBoolean(autosyncPlaylistsKey, false)
                                        putBoolean(autosyncLikesKey, false)
                                        putBoolean(syncImportHistoryKey, false)
                                        putBoolean(syncImportLibrarySongsKey, false)
                                        putBoolean(syncImportUploadedSongsKey, false)
                                        putBoolean(syncImportUploadedAlbumsKey, false)
                                        putBoolean(syncImportEpisodesKey, false)
                                        putBoolean(syncPushHistoryKey, false)
                                        putBoolean(syncPushSongLikeKey, false)
                                        putBoolean(syncPushAlbumBookmarkKey, false)
                                        putBoolean(syncPushArtistFollowKey, false)
                                        putBoolean(syncPushPlaylistKey, false)
                                        putBoolean(syncPushEpisodeKey, false)
                                    }
                                }
                            },
                            icon = R.drawable.sync
                        )

                        AnimatedVisibility(visible = isSyncEnabled) {
                            Column {
                                // Sync status overview
                                val statusText = when {
                                    syncStatusState.isRunning -> stringResource(R.string.sync_status_running, syncStatusState.currentOperation)
                                    else -> {
                                        val lastSync = getLastSyncTime()
                                        if (lastSync > 0) {
                                            val elapsed = ((System.currentTimeMillis() - lastSync) / 60000).toInt()
                                            stringResource(R.string.sync_status_last, elapsed)
                                        } else stringResource(R.string.sync_status_never)
                                    }
                                }
                                val statusColor = when {
                                    syncStatusState.isRunning -> colorPalette().accent
                                    else -> colorPalette().textDisabled
                                }
                                OtherInfoSettingsEntry(
                                    title = statusText,
                                    text = "",
                                    icon = R.drawable.sync
                                )

                                // Sync status detail
                                OtherSettingsEntry(
                                    title = stringResource(R.string.sync_status_detail),
                                    text = stringResource(R.string.sync_status_detail_description),
                                    icon = R.drawable.information,
                                    onClick = { SyncStatusDialog.showDialog() }
                                )
                                SyncStatusDialog.Render()

                                OtherSettingsEntry(
                                    title = stringResource(R.string.sync_now),
                                    text = stringResource(R.string.sync_now_description),
                                    icon = R.drawable.sync,
                                    onClick = {
                                        // Queue all imports + pushes via sync queue (forced)
                                        queueSync(SyncOperation.FullSync)
                                        queueSync(SyncOperation.PushLikedSongs(force = true))
                                        queueSync(SyncOperation.PushAlbumBookmarks(force = true))
                                        queueSync(SyncOperation.PushArtistFollows(force = true))
                                        queueSync(SyncOperation.PushPlaylists(force = true))
                                        queueSync(SyncOperation.PushEpisodes(force = true))
                                    }
                                )
                                OtherEnumValueSelectorSettingsEntry(
                                    icon = R.drawable.sync,
                                    title = stringResource(R.string.sync_direction),
                                    selectedValue = syncDirection,
                                    onValueSelected = { syncDirection = it },
                                    valueText = { stringResource(it.stringResource) }
                                )

                                var syncCooldown by rememberPreference(syncCooldownKey, 30)
                                SliderSettingsEntry(
                                    title = stringResource(R.string.sync_cooldown),
                                    text = stringResource(R.string.sync_cooldown_description),
                                    state = syncCooldown.toFloat(),
                                    range = 5f..120f,
                                    stepSize = 15f,
                                    onSlide = { syncCooldown = it.toInt() },
                                    toDisplay = { "${it.toInt()} min" },
                                    isIntegerOnly = true,
                                    icon = R.drawable.time
                                )
                                var syncShowDetails by rememberPreference(syncShowDetailsKey, true)
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_show_details),
                                    text = stringResource(R.string.sync_show_details_description),
                                    isChecked = syncShowDetails,
                                    onCheckedChange = { syncShowDetails = it },
                                    icon = R.drawable.information
                                )
                                var syncBackgroundGuard by rememberPreference(syncBackgroundGuardKey, true)
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_background_guard),
                                    text = stringResource(R.string.sync_background_guard_description),
                                    isChecked = syncBackgroundGuard,
                                    onCheckedChange = { syncBackgroundGuard = it },
                                    icon = R.drawable.pause
                                )
                            }
                        }
                    }
                )

                // Auto-Sync, Import, Push (only when sync is enabled)
                AnimatedVisibility(visible = isSyncEnabled) {
                    Column {
                        // Auto-Sync (per feature)
                        SettingsSectionCard(
                            title = stringResource(R.string.autosync),
                            icon = R.drawable.sync,
                            content = {
                                var autosyncArtists by rememberPreference(autosyncArtistsKey, false)
                                var autosyncAlbums by rememberPreference(autosyncAlbumsKey, false)
                                var autosyncPlaylists by rememberPreference(autosyncPlaylistsKey, false)
                                var autosyncLikes by rememberPreference(autosyncLikesKey, false)

                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.autosync_channels),
                                    text = stringResource(R.string.autosync_channels_description),
                                    isChecked = autosyncArtists,
                                    onCheckedChange = { autosyncArtists = it },
                                    icon = R.drawable.people
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.autosync_albums),
                                    text = stringResource(R.string.autosync_albums_description),
                                    isChecked = autosyncAlbums,
                                    onCheckedChange = { autosyncAlbums = it },
                                    icon = R.drawable.album
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.autosync),
                                    text = stringResource(R.string.autosync_playlists_description),
                                    isChecked = autosyncPlaylists,
                                    onCheckedChange = { autosyncPlaylists = it },
                                    icon = R.drawable.playlist
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.autosync_likes),
                                    text = stringResource(R.string.autosync_likes_description),
                                    isChecked = autosyncLikes,
                                    onCheckedChange = { autosyncLikes = it },
                                    icon = R.drawable.heart
                                )
                            }
                        )

                        // Import from YouTube
                        SettingsSectionCard(
                            title = stringResource(R.string.sync_import_from_youtube),
                            icon = R.drawable.download,
                            content = {
                                var syncImportHistory by rememberPreference(syncImportHistoryKey, false)
                                var syncImportLibrarySongs by rememberPreference(syncImportLibrarySongsKey, false)
                                var syncImportUploadedSongs by rememberPreference(syncImportUploadedSongsKey, false)
                                var syncImportUploadedAlbums by rememberPreference(syncImportUploadedAlbumsKey, false)
                                var syncImportEpisodes by rememberPreference(syncImportEpisodesKey, false)

                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_import_history),
                                    text = stringResource(R.string.sync_import_history_description),
                                    isChecked = syncImportHistory,
                                    onCheckedChange = { syncImportHistory = it },
                                    icon = R.drawable.history
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_import_library_songs),
                                    text = stringResource(R.string.sync_import_library_songs_description),
                                    isChecked = syncImportLibrarySongs,
                                    onCheckedChange = { syncImportLibrarySongs = it },
                                    icon = R.drawable.heart
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_import_uploaded_songs),
                                    text = stringResource(R.string.sync_import_uploaded_songs_description),
                                    isChecked = syncImportUploadedSongs,
                                    onCheckedChange = { syncImportUploadedSongs = it },
                                    icon = R.drawable.download
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_import_uploaded_albums),
                                    text = stringResource(R.string.sync_import_uploaded_albums_description),
                                    isChecked = syncImportUploadedAlbums,
                                    onCheckedChange = { syncImportUploadedAlbums = it },
                                    icon = R.drawable.album
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_import_episodes),
                                    text = stringResource(R.string.sync_import_episodes_description),
                                    isChecked = syncImportEpisodes,
                                    onCheckedChange = { syncImportEpisodes = it },
                                    icon = R.drawable.podcast
                                )
                            }
                        )

                        // Push to YouTube
                        SettingsSectionCard(
                            title = stringResource(R.string.sync_push_to_youtube),
                            icon = R.drawable.arrow_up,
                            content = {
                                var syncPushSongLike by rememberPreference(syncPushSongLikeKey, false)
                                var syncPushAlbumBookmark by rememberPreference(syncPushAlbumBookmarkKey, false)
                                var syncPushArtistFollow by rememberPreference(syncPushArtistFollowKey, false)
                                var syncPushPlaylist by rememberPreference(syncPushPlaylistKey, false)
                                var syncPushHistory by rememberPreference(syncPushHistoryKey, false)

                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_push_history),
                                    text = stringResource(R.string.sync_push_history_description),
                                    isChecked = syncPushHistory,
                                    onCheckedChange = { syncPushHistory = it },
                                    icon = R.drawable.history
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_push_song_like),
                                    text = stringResource(R.string.sync_push_song_like_description),
                                    isChecked = syncPushSongLike,
                                    onCheckedChange = { syncPushSongLike = it },
                                    icon = R.drawable.heart
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_push_album_bookmark),
                                    text = stringResource(R.string.sync_push_album_bookmark_description),
                                    isChecked = syncPushAlbumBookmark,
                                    onCheckedChange = { syncPushAlbumBookmark = it },
                                    icon = R.drawable.album
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_push_artist_follow),
                                    text = stringResource(R.string.sync_push_artist_follow_description),
                                    isChecked = syncPushArtistFollow,
                                    onCheckedChange = { syncPushArtistFollow = it },
                                    icon = R.drawable.people
                                )
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_push_playlist),
                                    text = stringResource(R.string.sync_push_playlist_description),
                                    isChecked = syncPushPlaylist,
                                    onCheckedChange = { syncPushPlaylist = it },
                                    icon = R.drawable.playlist
                                )
                                var syncPushEpisode by rememberPreference(syncPushEpisodeKey, false)
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.sync_push_episode),
                                    text = stringResource(R.string.sync_push_episode_description),
                                    isChecked = syncPushEpisode,
                                    onCheckedChange = { syncPushEpisode = it },
                                    icon = R.drawable.podcast
                                )
                            }
                        )
                    }
                }
            }

        }

        /* Removed Spacer */

        // Last.fm Section
        LastFmSettingsCard()

        // Discord Section
        if (isAtLeastAndroid7) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                    animationSpec = tween(1000),
                    initialScale = 0.9f
                )
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
                        var isTokenValid by remember { mutableStateOf(true) }
                        var showTokenError by remember { mutableStateOf(false) }

                        LaunchedEffect(discordPersonalAccessToken) {
                            if (discordPersonalAccessToken.isNotEmpty()) {
                                val presenceManager = DiscordPresenceManager(context, { discordPersonalAccessToken })
                                when (presenceManager.validateToken(discordPersonalAccessToken)) {
                                    true -> {
                                        isTokenValid = true
                                        showTokenError = false
                                    }
                                    false -> {
                                        isTokenValid = false
                                        showTokenError = true
                                        discordPersonalAccessToken = ""
                                        discordUsername = ""
                                        discordAvatar = ""
                                        Toaster.e(R.string.discord_token_text_invalid)
                                    }
                                    null -> { // Network error
                                        isTokenValid = false
                                        showTokenError = false
                                    }
                                }
                            }
                        }

                        if (search.inputValue.isBlank() || stringResource(R.string.discord_enable_rich_presence).contains(search.inputValue, true) || stringResource(R.string.beta_text).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                                title = stringResource(R.string.discord_enable_rich_presence),
                                text = stringResource(R.string.beta_text),
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
                                        title = stringResource(R.string.discord_advanced_mode),
                                        text = stringResource(R.string.discord_advanced_mode_text),
                                        isChecked = isDiscordAdvancedMode,
                                        onCheckedChange = { isDiscordAdvancedMode = it },
                                        icon = R.drawable.pencil
                                    )
                                }

                                // PW-5 (revised 2026-09-21): content transition matches the app's
                                // own pattern (Last.fm card) — the default AnimatedVisibility
                                // expand/collapse + fade, not a fixed tween fade+scale.
                                AnimatedVisibility(visible = isDiscordAdvancedMode) {
                                    DiscordAdvancedSection(search = search)
                                }

                                // Item 6 + PW-6: live preview of the RPC card — visible in
                                // both modes (advanced mode only changes the rendered
                                // content). Fed by the current player state, the position
                                // is polled ~100 ms (upstream parity). binder == null →
                                // neutral card.
                                val player = LocalPlayerServiceBinder.current?.player
                                var previewPosition by remember { mutableStateOf(0L) }
                                // The tick advances on every poll even while paused/idle, so
                                // the card stays reactive to settings changes in real time
                                // (the position alone only changes while playing).
                                var previewTick by remember { mutableIntStateOf(0) }
                                // State-chip selection: -1 = actual state (live follow of
                                // the real player state); 0/1 = pin the preview card to
                                // playing / paused.
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
                                            // position must stop advancing. Live follow (-1) is
                                            // unaffected — a real pause already keeps
                                            // currentPosition static.
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
                                // Same visual style as the "Account info" title below:
                                // regular text color, bold, start inset aligned (13.dp =
                                // column 8.dp + text 5.dp), vertical spacing kept.
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

                                // Playback state chips (2026-09-21), centered under the
                                // preview: "Actual state" (live follow — first), then
                                // Playing / Paused to pin that variant of the card for
                                // inspection (tap the pinned chip again to unpin).
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
                                        Text(
                                            text = stringResource(labelRes),
                                            style = typography().s.copy(
                                                color = if (selected) chipPalette.onAccent else chipPalette.accent
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .clip(uiRoundnessShape())
                                                .background(
                                                    if (selected) chipPalette.accent
                                                    else chipPalette.accent.copy(alpha = 0.15f),
                                                    uiRoundnessShape()
                                                )
                                                .clickable {
                                                    when (index) {
                                                        0 -> previewMode = -1
                                                        1 -> previewMode = if (previewMode == 0) -1 else 0
                                                        else -> previewMode = if (previewMode == 1) -1 else 1
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
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
                                        title = if (discordPersonalAccessToken.isNotEmpty()) stringResource(R.string.account_logoff) else stringResource(R.string.account_login),
                                        text = if (discordPersonalAccessToken.isNotEmpty()) stringResource(R.string.discord_connected_to_discord_account) else "",
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

    

        val searchCtx_Reset = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
        AnimatedVisibility(
            visible = searchCtx_Reset,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f)
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_reset),
                icon = R.drawable.refresh,
                content = {
                    var resetToDefault by remember { mutableStateOf(false) }
                    
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true) || stringResource(R.string.settings_reset).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.settings_reset),
                            text = stringResource(R.string.settings_restore_default_settings),
                            icon = R.drawable.refresh,
                            onClick = { 
                                resetToDefault = true
                                Toaster.done()
                            }
                        )
                    }

                    if (resetToDefault) {
                        DefaultAccountsSettings()
                        LaunchedEffect(Unit) {
                            resetToDefault = false
                        }
                    }
                }
            )
        }
        
        // Clear synced data confirmation dialog
        val dialogCoroutineScope = rememberCoroutineScope()
        if (showClearSyncDialog) {
            DefaultDialog(
                onDismiss = { showClearSyncDialog = false }
            ) {
                BasicText(
                    text = stringResource(R.string.clear_synced_data_confirm),
                    style = typography().s.copy(color = colorPalette().text)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { showClearSyncDialog = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorPalette().background2,
                            contentColor = colorPalette().text
                        ),
                        shape = uiRoundnessShape()
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            showClearSyncDialog = false
                            dialogCoroutineScope.launch(NzikDispatchers.DATA) {
                                val cleared = runCatching { clearAllSyncedData() }.getOrElse { false }
                                withContext(NzikDispatchers.UI) {
                                    val ep = appContext().encryptedPreferences
                                    ep.edit().putString(ytCookieKey, "").apply()
                                    ep.edit().putString(ytAccountNameKey, "").apply()
                                    ep.edit().putString(ytAccountChannelHandleKey, "").apply()
                                    ep.edit().putString(ytAccountEmailKey, "").apply()
                                    ep.edit().putString(ytAccountThumbnailKey, "").apply()
                                    ep.edit().putString(ytVisitorDataKey, "").apply()
                                    ep.edit().putString(ytDataSyncIdKey, "").apply()
                                    
                                    // Manually update states to force UI recomposition
                                    cookie = ""
                                    accountName = ""
                                    accountEmail = ""
                                    accountChannelHandle = ""
                                    accountThumbnail = ""
                                    visitorData = ""
                                    dataSyncId = ""
                                    
                                    app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger++
                                    
                                    appContext().preferences.edit().putBoolean(enableYouTubeSyncKey, false).apply()
                                    app.n_zik.android.MainApplication.cookieStatus = app.n_zik.android.MainApplication.CookieStatus.NOT_LOGGED_IN
                                    appContext().preferences.edit().remove(ytCookieExpiredKey).apply()
                                    clearStreamCaches()
                                    appContext().preferences.edit().putBoolean(streamClientRestartNeededKey, true).apply()
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.removeAllCookies(null)
                                    cookieManager.flush()
                                    WebStorage.getInstance().deleteAllData()
                                    if (cleared) {
                                        Toaster.s(R.string.account_logoff)
                                    } else {
                                        Toaster.w(R.string.account_logoff)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorPalette().accent,
                            contentColor = colorPalette().textSecondary
                        ),
                        shape = uiRoundnessShape()
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

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
private fun DiscordAdvancedSection(search: Search) {
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
    // template — DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE), exactly like
    // the state/details fields show theirs; there is no longer a "details - state"
    // recovery expression for it.
    Column {
        // Grouped layout (2026-09-22): related entries live under a shared group header —
        // the presence card parts (lines, images, progress bar), then the actions
        // (buttons) and the connection behavior.
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_identity)) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_activity_type).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    title = stringResource(R.string.discord_activity_type),
                    text = activityTypeLabel,
                    icon = R.drawable.play,
                    onClick = { showActivityTypeDialog = true }
                )
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_name).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    title = stringResource(R.string.discord_advanced_name),
                    // Empty template = the built-in default below (the fixed name), shown
                    // as the current value instead of a "(default)" marker.
                    text = activityName.ifEmpty { stringResource(R.string.discord_presence_name) },
                    icon = R.drawable.text,
                    onClick = { showNameDialog = true }
                )
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_lines)) {
            // Linked visibility: the section toggle comes first, and the linked template
            // entry animates in/out with it (Last.fm card pattern — the app's default
            // expand/collapse + fade); the toggle stays reachable so the part can be
            // restored. The lines follow the CARD display order — the details line (bold)
            // sits above the state line, so it is configured first.
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_details).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
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
                        title = stringResource(R.string.discord_advanced_details),
                        text = detailsTemplate.ifEmpty { DiscordActivityBuilder.DEFAULT_DETAILS_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showDetailsDialog = true }
                    )
                }
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_state).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
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
                        title = stringResource(R.string.discord_advanced_state),
                        text = stateTemplate.ifEmpty { DiscordActivityBuilder.DEFAULT_STATE_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showStateDialog = true }
                    )
                }
            }
            // PW-3: pause presence on/off (default on = current ⏸︎ behavior) — the toggle
            // comes first: with it off, the paused presence is never sent, so the paused
            // line template is hidden (animated) behind it.
            if (search.inputValue.isBlank() || stringResource(R.string.discord_pause_presence).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
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
                        title = stringResource(R.string.discord_advanced_pause),
                        text = pauseTemplate.ifEmpty { stringResource(R.string.discord_presence_pause_default) },
                        icon = R.drawable.text,
                        onClick = { showPauseDialog = true }
                    )
                }
            }
            // The 60 s auto-clear of the stale presence after a pause (advanced option,
            // default on): only reachable with the pause presence off — with it on, the
            // paused presence is the active state and stays as long as the player is
            // paused, so there is nothing to auto-clear and the option is hidden (animated).
            AnimatedVisibility(visible = !pausePresenceEnabled) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_pause_auto_clear).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
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
                        title = stringResource(R.string.discord_advanced_image_text),
                        text = largeImageText.ifEmpty { DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showLargeImageTextDialog = true }
                    )
                }
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_small_image).contains(search.inputValue, true)) {
                // The app logo keeps its own colors (import-menu rendering) — the shared
                // switch entry would tint it into a flat accent disc.
                DiscordLogoSettingsEntry(
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
                        title = stringResource(R.string.discord_advanced_logo_text),
                        text = smallImageText.ifEmpty { DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showSmallImageTextDialog = true }
                    )
                }
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_progress)) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_timestamps).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
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
                    title = stringResource(R.string.discord_advanced_button1),
                    text = "",
                    isChecked = btn1Enabled,
                    onCheckedChange = { btn1Enabled = it },
                    // Button 1 is the "Get N-Zik" GitHub action by default — give its toggle a
                    // distinct icon so it is not confused with Button 2 (ytmusic) — reviewer
                    // finding: the two toggles shared one copy-pasted icon.
                    icon = R.drawable.github_icon
                )
            }
            // Content transition (Last.fm card pattern): the button sub-entries animate
            // in/out with the default expand/collapse + fade when the toggle flips.
            // Column: AnimatedVisibility lays its content out in a Box, so the two
            // sibling entries must be stacked explicitly or they overlap (rendered
            // on top of each other at the same position).
            AnimatedVisibility(visible = btn1Enabled) {
                Column {
                    if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button_label).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = "${stringResource(R.string.discord_advanced_button1)} — ${stringResource(R.string.discord_advanced_button_label)}",
                            text = btn1Label.ifEmpty { stringResource(R.string.discord_presence_button_get_nzik) },
                            icon = R.drawable.text,
                            onClick = { showBtn1LabelDialog = true }
                        )
                    }
                    if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button_url).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = "${stringResource(R.string.discord_advanced_button1)} — ${stringResource(R.string.discord_advanced_button_url)}",
                            text = btn1Url.ifEmpty { DiscordActivityBuilder.DEFAULT_BUTTON1_URL },
                            icon = R.drawable.open,
                            onClick = { showBtn1UrlDialog = true }
                        )
                    }
                }
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button2).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.discord_advanced_button2),
                    text = "",
                    isChecked = btn2Enabled,
                    onCheckedChange = { btn2Enabled = it },
                    icon = R.drawable.ytmusic
                )
            }
            AnimatedVisibility(visible = btn2Enabled) {
                Column {
                    if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button_label).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = "${stringResource(R.string.discord_advanced_button2)} — ${stringResource(R.string.discord_advanced_button_label)}",
                            text = btn2Label.ifEmpty { stringResource(R.string.discord_presence_button_listen_ytmusic) },
                            icon = R.drawable.text,
                            onClick = { showBtn2LabelDialog = true }
                        )
                    }
                    if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_button_url).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = "${stringResource(R.string.discord_advanced_button2)} — ${stringResource(R.string.discord_advanced_button_url)}",
                            text = btn2Url.ifEmpty { DiscordActivityBuilder.DEFAULT_BUTTON2_URL },
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
                    title = stringResource(R.string.discord_advanced_idle_close),
                    text = stringResource(R.string.discord_advanced_idle_close_text),
                    isChecked = idleCloseEnabled,
                    onCheckedChange = { idleCloseEnabled = it },
                    icon = R.drawable.link
                )
            }
        }
    }

    if (showActivityTypeDialog) {
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
    if (showNameDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_name),
            value = activityName,
            placeholderValue = stringResource(R.string.discord_presence_name),
            onDone = { activityName = it },
            onDismiss = { showNameDialog = false }
        )
    }
    if (showStateDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_state),
            value = stateTemplate,
            placeholderValue = DiscordActivityBuilder.DEFAULT_STATE_TEMPLATE,
            onDone = { stateTemplate = it },
            onDismiss = { showStateDialog = false }
        )
    }
    if (showDetailsDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_details),
            value = detailsTemplate,
            placeholderValue = DiscordActivityBuilder.DEFAULT_DETAILS_TEMPLATE,
            onDone = { detailsTemplate = it },
            onDismiss = { showDetailsDialog = false }
        )
    }
    if (showPauseDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_pause),
            value = pauseTemplate,
            placeholderValue = stringResource(R.string.discord_presence_pause_default),
            onDone = { pauseTemplate = it },
            onDismiss = { showPauseDialog = false }
        )
    }
    if (showBtn1LabelDialog) {
        DiscordTemplateFieldDialog(
            title = "${stringResource(R.string.discord_advanced_button1)} — ${stringResource(R.string.discord_advanced_button_label)}",
            value = btn1Label,
            placeholderValue = stringResource(R.string.discord_presence_button_get_nzik),
            onDone = { btn1Label = it },
            onDismiss = { showBtn1LabelDialog = false }
        )
    }
    if (showBtn1UrlDialog) {
        DiscordTemplateFieldDialog(
            title = "${stringResource(R.string.discord_advanced_button1)} — ${stringResource(R.string.discord_advanced_button_url)}",
            value = btn1Url,
            placeholderValue = DiscordActivityBuilder.DEFAULT_BUTTON1_URL,
            onDone = { btn1Url = it },
            onDismiss = { showBtn1UrlDialog = false }
        )
    }
    if (showBtn2LabelDialog) {
        DiscordTemplateFieldDialog(
            title = "${stringResource(R.string.discord_advanced_button2)} — ${stringResource(R.string.discord_advanced_button_label)}",
            value = btn2Label,
            placeholderValue = stringResource(R.string.discord_presence_button_listen_ytmusic),
            onDone = { btn2Label = it },
            onDismiss = { showBtn2LabelDialog = false }
        )
    }
    if (showBtn2UrlDialog) {
        DiscordTemplateFieldDialog(
            title = "${stringResource(R.string.discord_advanced_button2)} — ${stringResource(R.string.discord_advanced_button_url)}",
            value = btn2Url,
            placeholderValue = DiscordActivityBuilder.DEFAULT_BUTTON2_URL,
            onDone = { btn2Url = it },
            onDismiss = { showBtn2UrlDialog = false }
        )
    }
    if (showLargeImageTextDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_image_text),
            value = largeImageText,
            placeholderValue = DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE,
            onDone = { largeImageText = it },
            onDismiss = { showLargeImageTextDialog = false }
        )
    }
    if (showSmallImageTextDialog) {
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
    DefaultDialog(onDismiss = onDismiss) {
        Text(
            text = title,
            style = typography().m.copy(color = palette.text),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        // Palette-explicit colors: the dialog background is colorPalette().background1,
        // so the default Material3 scheme (dark onSurface text) made the typed text and
        // the placeholder unreadable on dark themes.
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
                Text(
                    text = placeholder,
                    style = typography().s.copy(color = palette.accent),
                    modifier = Modifier
                        .clip(uiRoundnessShape())
                        .clickable { text += placeholder }
                        .background(palette.accent.copy(alpha = 0.15f), uiRoundnessShape())
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Toolbar settings dialogs' button bar (ToggleListDialog): Reset — Cancel — OK,
        // same look and same behavior. Reset clears the draft only (the dialog stays
        // open), Cancel discards it, OK saves it and toasts the confirmation.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            BasicText(
                text = stringResource(R.string.reset),
                style = typography().xs.medium.copy(
                    color = palette.textDisabled,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .clip(uiRoundnessShape())
                    .clickable { actions.reset() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = stringResource(R.string.cancel),
                style = typography().xs.medium.copy(
                    color = palette.red.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .clip(uiRoundnessShape())
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = stringResource(R.string.ok),
                style = typography().xs.semiBold.copy(
                    color = palette.onAccent,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .clip(uiRoundnessShape())
                    .background(palette.accent)
                    .clickable { actions.confirm(onDone, onDismiss) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * Discord section only: the image text tooltip popup — shown when the preview card's
 * artwork or app logo is tapped, mirroring Discord's hover tooltip (the text shown
 * over the large artwork / the small app logo).
 */
@Composable
private fun DiscordImageTooltipDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    val palette = colorPalette()
    DefaultDialog(onDismiss = onDismiss) {
        Text(
            text = title,
            style = typography().m.copy(color = palette.text),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = typography().s.copy(color = palette.text)
        )
        Spacer(Modifier.height(16.dp))
        // Same OK button as the toolbar settings dialogs (ToggleListDialog).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            BasicText(
                text = stringResource(R.string.ok),
                style = typography().xs.semiBold.copy(
                    color = palette.onAccent,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .clip(uiRoundnessShape())
                    .background(palette.accent)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
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
        modifier = Modifier
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
                        .background(
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
    player: ExoPlayer?,
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
        // A pinned playing/paused preview without a loaded media renders the raw
        // templates — the paused variant shows the pause template, so the three
        // variants stay distinguishable even with nothing playing.
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
        // ever sent and the real Discord keeps the last playing presence, so the
        // preview shows the playing card too (the paused variant does not exist).
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
        // media (as-is on the fallback card). The built-in default is the live album
        // value when the app knows it, otherwise the current configuration's
        // "details - state" expression — the same the real presence computes (manager
        // sendActivity); the paused line renders into details, so the paused state shows
        // in the image text too.
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
                // the app knows it (live — metadata then DB). Unknown album while media
                // is loaded → the current configuration's "details - state" expression
                // (never a rendered "Unknown Album"). No media (fallback card) → the
                // current image template, i.e. the built-in album template as-is.
                if (item != null) {
                    info.albumName?.takeIf { it.isNotBlank() }
                        ?: if (content.state.isNotBlank()) "${content.details} - ${content.state}" else content.details
                } else {
                    DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE
                }
            }
        // The app logo tooltip content: rendered while media is loaded (the same value
        // the real presence sends), the template as-is otherwise (the current template
        // IS the fallback). The artwork tooltip reuses the card line above (`imageText`)
        // — rendered content with media, template as-is without.
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
                                thumbnailUrl = artworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            // Fallback artwork: the app's standard missing-image icon (the
                            // same drawable ImageCacheFactory uses for errors/absent
                            // thumbnails).
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
                    // fallback): real media -> live progress; no media -> an empty bar
                    // with "0:00 / 0:00" (the card stays complete). Material's
                    // LinearProgressIndicator, the app's standard bar.
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
                                Text(
                                    text = button.label,
                                    style = typography().s.copy(color = Color.White),
                                    modifier = Modifier
                                        .clip(uiRoundnessShape())
                                        .background(palette.accent.copy(alpha = 0.15f), uiRoundnessShape())
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .clickable { cardContext.openDiscordButtonUrl(button.url) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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

/**
 * Discord section only: "m:ss" time label for the preview card's progress bar
 * (pure time formatting — not user-facing text, nothing to localize).
 */
private fun formatPreviewTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * Discord section only: opens a preview card button URL in the external browser —
 * the real Discord buttons open the same link, and the preview must let the user
 * verify where each button points.
 */
private fun Context.openDiscordButtonUrl(url: String) {
    if (url.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

fun isYouTubeLoginEnabled(): Boolean {
    val isYouTubeLoginEnabled = appContext().encryptedPreferences.getBoolean(enableYouTubeLoginKey, false)
    return isYouTubeLoginEnabled
}

fun isYouTubeSyncEnabled(): Boolean {
    val isDevBuild = BuildConfig.BUILD_TYPE in listOf("debug", "dev", "dev32", "beta", "beta32")
    if (!isDevBuild) return false
    val isYouTubeSyncEnabled = appContext().encryptedPreferences.getBoolean(enableYouTubeSyncKey, false)
    val useLoginForBrowse = appContext().preferences.getBoolean(useLoginForBrowseKey, true)
    return isYouTubeSyncEnabled && isYouTubeLoggedIn() && isYouTubeLoginEnabled() && useLoginForBrowse
}

fun isYouTubeLoggedIn(): Boolean {
    val cookie = appContext().encryptedPreferences.getString(ytCookieKey, "")
    val isLoggedIn = cookie?.let { parseCookieString(it) }?.contains("SAPISID") == true
    return isLoggedIn
}





