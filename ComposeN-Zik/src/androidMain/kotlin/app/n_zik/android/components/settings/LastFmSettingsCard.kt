package app.n_zik.android.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.OtherSwitchSettingEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.ui.screens.settings.SliderSettingsEntry
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import it.fast4x.lastfm.LastFm
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Last.fm settings card shown in the Accounts tab.
 * Renders nothing when the API keys are not configured.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastFmSettingsCard() {
    if (BuildConfig.LASTFM_API_KEY.isEmpty() || BuildConfig.LASTFM_API_SECRET.isEmpty()) return

    SettingsSectionCard(
        title = stringResource(R.string.social_lastfm),
        icon = R.drawable.logo_lastfm,
        content = {
            var isLastfmScrobblingEnabled by rememberEncryptedPreference(isLastfmScrobblingEnabledKey, false)
            var isLastfmNowPlayingEnabled by rememberEncryptedPreference(isLastfmNowPlayingEnabledKey, true)
            var isLastfmScrobbleEnabled by rememberEncryptedPreference(isLastfmScrobbleEnabledKey, true)
            var lastfmMinTrackDurationSeconds by rememberEncryptedPreference(lastfmMinTrackDurationSecondsKey, 30)
            var lastfmScrobbleThresholdPercent by rememberEncryptedPreference(lastfmScrobbleThresholdPercentKey, 50)
            var lastfmMaxScrobbleDelaySeconds by rememberEncryptedPreference(lastfmMaxScrobbleDelaySecondsKey, 50)
            var lastfmSession by rememberEncryptedPreference(lastfmSessionKey, "")
            var lastfmUsername by rememberEncryptedPreference(lastfmUsernameKey, "")
            var lastfmAvatarUrl by rememberEncryptedPreference(lastfmAvatarUrlKey, "")
            var loginLastfm by remember { mutableStateOf(false) }
            val cardScope = rememberCoroutineScope()

            val minDurationInitial by remember { derivedStateOf { lastfmMinTrackDurationSeconds.toFloat() } }
            var minDurationUi by remember(minDurationInitial) { mutableFloatStateOf(minDurationInitial) }
            val thresholdInitial by remember { derivedStateOf { lastfmScrobbleThresholdPercent.toFloat() } }
            var thresholdUi by remember(thresholdInitial) { mutableFloatStateOf(thresholdInitial) }
            val maxDelayInitial by remember { derivedStateOf { lastfmMaxScrobbleDelaySeconds.toFloat() } }
            var maxDelayUi by remember(maxDelayInitial) { mutableFloatStateOf(maxDelayInitial) }

            OtherSwitchSettingEntry(
                title = stringResource(R.string.lastfm_enable_scrobbling),
                text = stringResource(R.string.social_lastfm_info),
                isChecked = isLastfmScrobblingEnabled,
                onCheckedChange = { isLastfmScrobblingEnabled = it },
                icon = R.drawable.musical_notes
            )

            AnimatedVisibility(visible = isLastfmScrobblingEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                Column {
                    if (lastfmSession.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (lastfmAvatarUrl.isNotEmpty()) {
                                ImageCacheFactory.AsyncImage(
                                    thumbnailUrl = lastfmAvatarUrl,
                                    contentDescription = stringResource(R.string.lastfm_username),
                                    modifier = Modifier
                                        .padding(start = 5.dp, top = 8.dp, bottom = 8.dp)
                                        .size(50.dp)
                                        .clip(thumbnailShape())
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.person),
                                    contentDescription = stringResource(R.string.lastfm_username),
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
                                    text = lastfmUsername,
                                    color = colorPalette().textSecondary,
                                    modifier = Modifier.padding(start = 5.dp),
                                    style = typography().m
                                )
                            }
                        }
                    }

                    OtherSettingsEntry(
                        title = if (lastfmSession.isNotEmpty()) stringResource(R.string.lastfm_disconnect) else stringResource(R.string.lastfm_connect),
                        text = if (lastfmSession.isNotEmpty()) stringResource(R.string.lastfm_connected) else stringResource(R.string.social_lastfm_info),
                        icon = R.drawable.logout,
                        onClick = {
                            if (lastfmSession.isNotEmpty()) {
                                lastfmSession = ""
                                lastfmUsername = ""
                                lastfmAvatarUrl = ""
                                LastFm.sessionKey = null
                            } else {
                                loginLastfm = true
                            }
                        }
                    )

                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.lastfm_now_playing),
                        text = stringResource(R.string.lastfm_now_playing_info),
                        isChecked = isLastfmNowPlayingEnabled,
                        onCheckedChange = { isLastfmNowPlayingEnabled = it },
                        icon = R.drawable.play
                    )

                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.lastfm_scrobble),
                        text = stringResource(R.string.lastfm_scrobble_info),
                        isChecked = isLastfmScrobbleEnabled,
                        onCheckedChange = { isLastfmScrobbleEnabled = it },
                        icon = R.drawable.history
                    )

                    // Gates Now Playing too, so it stays visible even when scrobbling is off
                    SliderSettingsEntry(
                        title = stringResource(R.string.lastfm_min_track_duration),
                        text = stringResource(R.string.lastfm_min_track_duration_info),
                        state = minDurationUi,
                        range = 10f..60f,
                        stepSize = 5f,
                        onSlide = { minDurationUi = it },
                        onSlideComplete = { lastfmMinTrackDurationSeconds = minDurationUi.toInt() },
                        toDisplay = { "${it.toInt()} s" },
                        isIntegerOnly = true,
                        icon = R.drawable.time
                    )

                    AnimatedVisibility(visible = isLastfmScrobbleEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                        Column {
                            SliderSettingsEntry(
                                title = stringResource(R.string.lastfm_scrobble_threshold),
                                text = stringResource(R.string.lastfm_scrobble_threshold_info),
                                state = thresholdUi,
                                range = 30f..95f,
                                stepSize = 5f,
                                onSlide = { thresholdUi = it },
                                onSlideComplete = { lastfmScrobbleThresholdPercent = thresholdUi.toInt() },
                                toDisplay = { "${it.toInt()} %" },
                                isIntegerOnly = true,
                                icon = R.drawable.playbackduration
                            )
                            SliderSettingsEntry(
                                title = stringResource(R.string.lastfm_max_scrobble_delay),
                                text = stringResource(R.string.lastfm_max_scrobble_delay_info),
                                state = maxDelayUi,
                                range = 30f..360f,
                                stepSize = 30f,
                                onSlide = { maxDelayUi = it },
                                onSlideComplete = { lastfmMaxScrobbleDelaySeconds = maxDelayUi.toInt() },
                                toDisplay = { "${it.toInt()} s" },
                                isIntegerOnly = true,
                                icon = R.drawable.playbackduration
                            )
                        }
                    }

                    CustomModalBottomSheet(
                        showSheet = loginLastfm,
                        onDismissRequest = {
                            loginLastfm = false
                        },
                        containerColor = Color.Transparent,
                        modifier = Modifier.statusBarsPadding(),
                        // Skip PartiallyExpanded: its anchor is a fixed 50% of the window
                        // height, while the sheet window is not resized by the keyboard
                        // (SOFT_INPUT_ADJUST_NOTHING on API 30+), so the fields stay hidden
                        // behind it. The Expanded anchor is content-driven
                        // (fullHeight - sheetHeight) and follows the IME insets applied to
                        // the sheet content by CustomModalBottomSheet.
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
                            RoundedCornerShape(
                                topStart = it.topStart,
                                topEnd = it.topEnd,
                                bottomStart = CornerSize(0.dp),
                                bottomEnd = CornerSize(0.dp)
                            )
                        } ?: uiRoundnessShape(),
                        dragHandle = {
                            Surface(
                                modifier = Modifier.padding(vertical = 0.dp),
                                color = Color.Transparent
                            ) {}
                        }
                    ) {
                        // Cap the content at ~50% of the screen: ListMenu.Menu stretches to
                        // the full screen height on its own, which would make the Expanded
                        // sheet cover the whole window. Bounded to half the screen, the
                        // sheet keeps its current half-screen look at rest while the anchor
                        // stays content-driven (it grows with the keyboard).
                        val screenHeightDp = LocalConfiguration.current.screenHeightDp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = (screenHeightDp * 0.5f).dp)
                        ) {
                            ListMenu.Menu(title = stringResource(R.string.social_lastfm)) {
                                LastFmLoginContent(
                                    onConnected = { sessionKey, username ->
                                        loginLastfm = false
                                        lastfmSession = sessionKey
                                        lastfmUsername = username
                                        lastfmAvatarUrl = ""
                                        cardScope.launch {
                                            val avatarUrl = LastFm.getUserPicture(username).getOrNull().orEmpty()
                                            if (avatarUrl.isNotEmpty()) lastfmAvatarUrl = avatarUrl
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * Content of the Last.fm login sheet: username/password fields and the login button.
 * Internal so it can be exercised by the Compose UI test.
 */
@Composable
internal fun LastFmLoginContent(
    onConnected: (sessionKey: String, username: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorStringId by remember { mutableStateOf<Int?>(null) }
    val usernameLabel = stringResource(R.string.lastfm_username)
    val passwordLabel = stringResource(R.string.lastfm_password)

    Column(
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        TextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            placeholder = { Text(text = usernameLabel) },
            colors = InputDialog.defaultTextFieldColors(),
            shape = uiRoundnessShape(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics { contentDescription = usernameLabel }
        )

        TextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painter = painterResource(if (passwordVisible) R.drawable.eye else R.drawable.eye_off),
                        contentDescription = stringResource(R.string.lastfm_show_password),
                        tint = colorPalette().textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            placeholder = { Text(text = passwordLabel) },
            colors = InputDialog.defaultTextFieldColors(),
            shape = uiRoundnessShape(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics {
                    contentDescription = passwordLabel
                    if (!passwordVisible) password()
                }
        )

        errorStringId?.let {
            Text(
                text = stringResource(it),
                color = colorPalette().red,
                style = typography().s,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank() || isLoggingIn) return@Button
                isLoggingIn = true
                errorStringId = null
                coroutineScope.launch {
                    LastFm.initialize(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_API_SECRET)
                    LastFm.getMobileSession(username, password)
                        .onSuccess { sessionKey ->
                            LastFm.sessionKey = sessionKey
                            onConnected(sessionKey, username)
                            Toaster.i(R.string.lastfm_connected)
                        }
                        .onFailure { e ->
                            Timber.tag("LastFmLogin").e(e, "LastFM mobile session failed")
                            errorStringId = R.string.lastfm_auth_failed
                            Toaster.e(R.string.lastfm_auth_failed)
                        }
                        .also { isLoggingIn = false }
                }
            },
            // Keep the button disabled until both credentials are entered, so the
            // disabled state is visible instead of a no-op click on empty fields.
            enabled = username.isNotBlank() && password.isNotBlank() && !isLoggingIn,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorPalette().accent,
                contentColor = colorPalette().textSecondary
            ),
            shape = uiRoundnessShape(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.lastfm_login))
        }
    }
}
