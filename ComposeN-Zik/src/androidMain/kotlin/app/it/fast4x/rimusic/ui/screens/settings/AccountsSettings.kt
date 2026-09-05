package app.it.fast4x.rimusic.ui.screens.settings

import app.n_zik.android.components.tab.Search
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon

import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.R

import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.settings.SyncStatusDialog

import it.fast4x.innertube.utils.parseCookieString
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.extensions.discord.DiscordLoginAndGetToken
import app.n_zik.android.extensions.discord.DiscordPresenceManager
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet

import app.it.fast4x.rimusic.ui.components.themed.DefaultDialog
import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text

import androidx.compose.foundation.text.BasicText

import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon

import androidx.compose.ui.res.painterResource
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
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
import app.it.fast4x.rimusic.utils.syncStatus
import app.it.fast4x.rimusic.utils.getLastSyncTime
import androidx.compose.runtime.collectAsState
import app.it.fast4x.rimusic.utils.isAtLeastAndroid7
import app.it.fast4x.rimusic.utils.isDiscordBrowsingEnabledKey
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.kreate.android.me.knighthat.utils.Toaster

import app.it.fast4x.rimusic.utils.clearAllSyncedData
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.queueSync
import app.it.fast4x.rimusic.utils.syncPushHistoryKey
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
    
    var isDiscordBrowsingEnabled by rememberEncryptedPreference(isDiscordBrowsingEnabledKey, true)
    isDiscordBrowsingEnabled = true
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
                                    // Reset cookie status
                                    app.n_zik.android.MainApplication.cookieStatus = app.n_zik.android.MainApplication.CookieStatus.NOT_LOGGED_IN
                                    appContext().preferences.edit().remove(ytCookieExpiredKey).apply()

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
                                    }
                                    // Reset cookie status — will be revalidated on next playback
                                    app.n_zik.android.MainApplication.cookieStatus = app.n_zik.android.MainApplication.CookieStatus.NOT_LOGGED_IN
                                    appContext().preferences.edit().remove(ytCookieExpiredKey).apply()
                                }
                                // Clear stream caches and mark restart needed
                                clearStreamCaches()
                                appContext().preferences.edit().putBoolean(streamClientRestartNeededKey, true).apply()
                                // Clear audio cache
                                binder?.cache?.let { cache ->
                                    val keys = cache.keys
                                    keys.forEach { song ->
                                        cache.removeResource(song)
                                    }
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
                                    title = if (isLoggedIn) stringResource(R.string.youtube_disconnect) else stringResource(R.string.youtube_connect),
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
                            var useLoginForBrowse by rememberPreference(useLoginForBrowseKey, true)
                            if (search.inputValue.isBlank() || stringResource(R.string.login_for_browse).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                                    title = stringResource(R.string.login_for_browse),
                                    text = stringResource(R.string.login_for_browse_description),
                                    isChecked = useLoginForBrowse,
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
            )
        }

        // ========== SYNC SETTINGS ==========
        val useLoginForBrowseSync by rememberPreference(useLoginForBrowseKey, true)

        AnimatedVisibility(
            visible = useLoginForBrowseSync,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = tween(600),
                initialScale = 0.9f
            )
        ) {
            var isYouTubeSyncEnabled by rememberEncryptedPreference(enableYouTubeSyncKey, false)
            val isSyncEnabled = isYouTubeSyncEnabled && useLoginForBrowseSync
            var syncDirection by rememberPreference(syncDirectionKey, SyncDirection.TWO_WAY)
            val coroutineScope = rememberCoroutineScope()
            val syncStatusState by syncStatus.collectAsState()
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
                                var isDiscordBrowsingEnabled by rememberEncryptedPreference(isDiscordBrowsingEnabledKey, true)

                                if (search.inputValue.isBlank() || stringResource(R.string.discord_enable_browsing).contains(search.inputValue, true)) {
                                    OtherSwitchSettingEntry(
                                        title = stringResource(R.string.discord_enable_browsing),
                                        text = "",
                                        isChecked = isDiscordBrowsingEnabled,
                                        onCheckedChange = { isDiscordBrowsingEnabled = it },
                                        icon = R.drawable.discover
                                    )
                                }

                                if (showTokenError) {
                                    Text(
                                        text = stringResource(R.string.discord_token_text_invalid),
                                        color = colorPalette().red,
                                        style = typography().s,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                if (discordPersonalAccessToken.isNotEmpty()) {
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

                                if (search.inputValue.isBlank() || stringResource(R.string.discord_connect).contains(search.inputValue, true) || stringResource(R.string.discord_disconnect).contains(search.inputValue, true)) {
                                    OtherSettingsEntry(
                                        title = if (discordPersonalAccessToken.isNotEmpty()) stringResource(R.string.discord_disconnect) else stringResource(R.string.discord_connect),
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
                            dialogCoroutineScope.launch(Dispatchers.IO) {
                                val cleared = runCatching { clearAllSyncedData() }.getOrElse { false }
                                withContext(Dispatchers.Main) {
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
                                        Toaster.s(R.string.youtube_disconnect)
                                    } else {
                                        Toaster.w(R.string.youtube_disconnect)
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

fun isYouTubeLoginEnabled(): Boolean {
    val isYouTubeLoginEnabled = appContext().encryptedPreferences.getBoolean(enableYouTubeLoginKey, false)
    return isYouTubeLoginEnabled
}

fun isYouTubeSyncEnabled(): Boolean {
    val isYouTubeSyncEnabled = appContext().encryptedPreferences.getBoolean(enableYouTubeSyncKey, false)
    val useLoginForBrowse = appContext().preferences.getBoolean(useLoginForBrowseKey, true)
    return isYouTubeSyncEnabled && isYouTubeLoggedIn() && isYouTubeLoginEnabled() && useLoginForBrowse
}

fun isYouTubeLoggedIn(): Boolean {
    val cookie = appContext().encryptedPreferences.getString(ytCookieKey, "")
    val isLoggedIn = cookie?.let { parseCookieString(it) }?.contains("SAPISID") == true
    return isLoggedIn
}





