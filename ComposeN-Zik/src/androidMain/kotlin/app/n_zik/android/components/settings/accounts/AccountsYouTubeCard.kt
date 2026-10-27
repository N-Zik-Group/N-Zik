package app.n_zik.android.components.settings.accounts

import app.n_zik.android.components.settings.common.settingsSearchTarget
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.autosyncAlbumsKey
import app.it.fast4x.rimusic.utils.autosyncArtistsKey
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.autosyncPlaylistsKey
import app.it.fast4x.rimusic.utils.clearAllSyncedData
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.quickPicsDiscoverPageKey
import app.it.fast4x.rimusic.utils.quickPicsHomePageKey
import app.it.fast4x.rimusic.utils.quickPicsYtmQuickPicksKey
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.streamClientRestartNeededKey
import app.it.fast4x.rimusic.utils.syncImportEpisodesKey
import app.it.fast4x.rimusic.utils.syncImportHistoryKey
import app.it.fast4x.rimusic.utils.syncImportLibrarySongsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedAlbumsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedSongsKey
import app.it.fast4x.rimusic.utils.syncPushAlbumBookmarkKey
import app.it.fast4x.rimusic.utils.syncPushArtistFollowKey
import app.it.fast4x.rimusic.utils.syncPushEpisodeKey
import app.it.fast4x.rimusic.utils.syncPushHistoryKey
import app.it.fast4x.rimusic.utils.syncPushPlaylistKey
import app.it.fast4x.rimusic.utils.syncPushSongLikeKey
import app.it.fast4x.rimusic.utils.useLoginForBrowseKey
import app.it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import app.it.fast4x.rimusic.utils.ytAccountEmailKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieExpiredKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.settings.clearAudioCache
import app.n_zik.android.components.settings.YtSearchAccountToggle
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.services.clearStreamCaches
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.utils.parseCookieString
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun AccountsYouTubeCard(search: Search) {
    val context = LocalContext.current
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

    // YouTube Music Section
    AnimatedVisibility(
        visible = true,
        enter = settingsCardEnter,
        exit = settingsCardExit
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
                        modifier = Modifier.settingsSearchTarget("accounts.youtube_music.enable_youtube_music_login"),
                        title = stringResource(R.string.enable_youtube_music_login),
                        text = stringResource(R.string.settings_desc_accounts_youtube_music_enable_youtube_music_login),
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
                                modifier = Modifier.settingsSearchTarget(if (isLoggedIn) "accounts.youtube_music.account_logoff" else "accounts.youtube_music.account_login"),
                                title = if (isLoggedIn) stringResource(R.string.account_logoff) else stringResource(R.string.account_login),
                                text = stringResource(R.string.settings_desc_accounts_youtube_music_account_login),
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
                                modifier = Modifier.settingsSearchTarget("accounts.youtube_music.login_for_browse"),
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
                        YtSearchAccountToggle(isYouTubeLoginEnabled = isYouTubeLoginEnabled, isLoggedIn = isLoggedIn, searchQuery = search.inputValue)


                    }
                }
            }
        )
    }

    // Clear synced data confirmation dialog
    val dialogCoroutineScope = rememberCoroutineScope()
    if (showClearSyncDialog) {
        DefaultDialog(
            title = stringResource(R.string.dialog_clear_synced_data),
            onDismiss = { showClearSyncDialog = false },
            footer = {
                ButtonRow(listOf(
                    DialogButton(stringResource(R.string.cancel), { showClearSyncDialog = false }, danger = true),
                    DialogButton(stringResource(R.string.clear), primary = true, onClick = {
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
                    })
                ))
            }
        ) {
            BasicText(
                text = stringResource(R.string.clear_synced_data_confirm),
                style = typography().s.copy(color = colorPalette().text)
            )

        }
    }
}
