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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.R
import io.ktor.http.Url
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.it.fast4x.compose.persist.persistList
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
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.themed.DefaultDialog
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.n_zik.android.components.menu.ListMenu
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.streamClientRestartNeededKey
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.playback.services.clearStreamCaches
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
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
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.n_zik.android.typography
import app.n_zik.android.components.dialog.settings.SettingsInputDialog

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
                    var visitorData by rememberEncryptedPreference(key = ytVisitorDataKey, defaultValue = "")
                    var dataSyncId by rememberEncryptedPreference(key = ytDataSyncIdKey, defaultValue = "")
                    var cookie by rememberEncryptedPreference(key = ytCookieKey, defaultValue = "")

                    var accountName by rememberEncryptedPreference(key = ytAccountNameKey, defaultValue = "")
                    var accountEmail by rememberEncryptedPreference(key = ytAccountEmailKey, defaultValue = "")
                    var accountChannelHandle by rememberEncryptedPreference(
                        key = ytAccountChannelHandleKey,
                        defaultValue = ""
                    )
                    var accountThumbnail by rememberEncryptedPreference(key = ytAccountThumbnailKey, defaultValue = "")
                    var isLoggedIn = remember(cookie) {
                        "SAPISID" in parseCookieString(cookie)
                    }
                    val binder = LocalPlayerServiceBinder.current

                    if (search.inputValue.isBlank() || stringResource(R.string.enable_youtube_music_login).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.enable_youtube_music_login),
                            text = "",
                            isChecked = isYouTubeLoginEnabled,
                            onCheckedChange = {
                                isYouTubeLoginEnabled = it
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
                                            cookie = ""
                                            accountName = ""
                                            accountChannelHandle = ""
                                            accountEmail = ""
                                            accountThumbnail = ""
                                            visitorData = ""
                                            dataSyncId = ""
                                            loginYouTube = false
                                            // Reset cookie status
                                            app.n_zik.android.MainApplication.cookieStatus = app.n_zik.android.MainApplication.CookieStatus.NOT_LOGGED_IN
                                            appContext().preferences.edit().remove(ytCookieExpiredKey).apply()
                                            // Clear stream caches and restart player
                                            clearStreamCaches()
                                            appContext().preferences.edit().putBoolean(streamClientRestartNeededKey, true).apply()
                                            //Delete cookies after logout
                                            val cookieManager = CookieManager.getInstance()
                                            cookieManager.removeAllCookies(null)
                                            cookieManager.flush()
                                            WebStorage.getInstance().deleteAllData()
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

                            if (search.inputValue.isBlank() || stringResource(R.string.sync_data_with_ytm_account).contains(search.inputValue, true) || stringResource(R.string.playlists_albums_artists_history_like_etc).contains(search.inputValue, true)) {
                                Box(modifier = Modifier.alpha(0.5f)) {
                                    OtherSwitchSettingEntry(
                                        title = stringResource(R.string.sync_data_with_ytm_account) + " (WIP)",
                                        text = "",
                                        isChecked = false,
                                        onCheckedChange = { },
                                        icon = R.drawable.sync
                                    )
                                }
                            }
                            // Login for Browse option
                            var useLoginForBrowse by rememberPreference(useLoginForBrowseKey, false)
                            if (search.inputValue.isBlank() || "login for browse".contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                                    title = "Login for Browse",
                                    text = "Use login for browse requests (disabled by default)",
                                    isChecked = useLoginForBrowse,
                                    onCheckedChange = { 
                                        useLoginForBrowse = it
                                        Innertube.useLoginForBrowse = it
                                    },
                                    icon = R.drawable.person
                                )
                            }
                        }
                    }
                }
            )
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
    return isYouTubeSyncEnabled && isYouTubeLoggedIn() && isYouTubeLoginEnabled()
}

fun isYouTubeLoggedIn(): Boolean {
    val cookie = appContext().encryptedPreferences.getString(ytCookieKey, "")
    val isLoggedIn = cookie?.let { parseCookieString(it) }?.contains("SAPISID") == true
    return isLoggedIn
}





