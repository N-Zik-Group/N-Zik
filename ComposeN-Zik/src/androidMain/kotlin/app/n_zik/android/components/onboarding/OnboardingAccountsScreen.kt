package app.n_zik.android.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.navigation.compose.rememberNavController
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.settings.LastFmLoginContent
import app.n_zik.android.extensions.discord.DiscordLoginAndGetToken
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import it.fast4x.lastfm.LastFm
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fourth and final step of the first-launch onboarding flow: optional account
 * connections (Last.fm scrobbling and Discord rich presence), after the name choice.
 *
 * Reuses the existing login components — the same ones behind the Accounts tab:
 * [LastFmLoginContent] in its sheet (Last.fm) and [DiscordLoginAndGetToken] in its
 * sheet (Discord) — writing to the same encrypted prefs, so the onboarding and the
 * Accounts tab observe the same state.
 *
 * Everything is optional and connecting an account never advances the flow — the
 * cards only log in (same encrypted prefs as the Accounts tab) and update their
 * status. Leaving the step is always through the skip button — labeled "Skip" while
 * nothing is connected and "I'm done" once at least one account is logged in: with
 * a Discord token set, the restart prompt ([RestartAppDialog]) shows after the flag
 * is written —
 * the presence manager is created when the player service starts, so the fresh
 * token is only picked up on a new launch, and the restart lands directly in the
 * app. Last.fm never needs a restart. Its `Render()` is composed here because the
 * restart prompt is normally only composed inside the settings screen, which is
 * not alive during onboarding.
 *
 * @param onComplete skip pressed with no Discord token — the flow completes
 * @param onDiscordConnected skip pressed while a Discord token is set — the
 *   activity must complete the onboarding (flag written) BEFORE the restart prompt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingAccountsScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
    onDiscordConnected: () -> Unit,
) {
    // Last.fm state — the same encrypted prefs as the Accounts tab card
    var lastfmSession by rememberEncryptedPreference(lastfmSessionKey, "")
    var lastfmUsername by rememberEncryptedPreference(lastfmUsernameKey, "")
    var lastfmAvatarUrl by rememberEncryptedPreference(lastfmAvatarUrlKey, "")
    var loginLastfm by remember { mutableStateOf(false) }
    val lastfmCardScope = rememberCoroutineScope()

    // Discord state — the same encrypted prefs as the Accounts tab card
    var discordToken by rememberEncryptedPreference(discordPersonalAccessTokenKey, "")
    var discordUsername by rememberEncryptedPreference(discordUsernameKey, "")
    var discordAvatar by rememberEncryptedPreference(discordAvatarKey, "")
    var loginDiscord by remember { mutableStateOf(false) }

    // Like the Accounts tab card: render nothing for Last.fm when the API keys
    // are not configured
    val lastfmConfigured =
        !BuildConfig.LASTFM_API_KEY.isEmpty() && !BuildConfig.LASTFM_API_SECRET.isEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            // The flow replaces AppNavigation here: keep the app's edge-to-edge
            // behavior — background full-bleed, content clear of the system bars
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            // drawable (PNG) instead of mipmap: the adaptive-icon XML wins on device and
            // Compose painterResource only supports vectors/rasters
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            tint = null,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboard_accounts_title),
            style = typography().l,
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().text
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboard_accounts_desc),
            style = typography().s,
            color = colorPalette().textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (lastfmConfigured) {
                item(key = "lastfm") {
                    OnboardingAccountCard(
                        icon = R.drawable.logo_lastfm,
                        title = stringResource(R.string.social_lastfm),
                        description = if (lastfmSession.isNotEmpty()) {
                            stringResource(R.string.lastfm_connected)
                        } else {
                            stringResource(R.string.social_lastfm_info)
                        },
                        actionLabel = if (lastfmSession.isNotEmpty()) {
                            stringResource(R.string.lastfm_disconnect)
                        } else {
                            stringResource(R.string.lastfm_connect)
                        },
                        onAction = {
                            if (lastfmSession.isNotEmpty()) {
                                lastfmSession = ""
                                lastfmUsername = ""
                                lastfmAvatarUrl = ""
                                LastFm.sessionKey = null
                                Timber.tag("Onboarding").i("Last.fm account disconnected, card reset")
                            } else {
                                loginLastfm = true
                            }
                        }
                    )
                }
            }

            item(key = "discord") {
                OnboardingAccountCard(
                    icon = R.drawable.logo_discord,
                    title = stringResource(R.string.social_discord),
                    description = if (discordToken.isNotEmpty()) {
                        stringResource(R.string.discord_connected_to_discord_account)
                    } else {
                        stringResource(R.string.onboard_accounts_discord_desc)
                    },
                    actionLabel = if (discordToken.isNotEmpty()) {
                        stringResource(R.string.discord_disconnect)
                    } else {
                        stringResource(R.string.discord_connect)
                    },
                    onAction = {
                        if (discordToken.isNotEmpty()) {
                            discordToken = ""
                            discordUsername = ""
                            discordAvatar = ""
                            // No restart prompt here either — the user leaves the step
                            // with the skip button, which re-evaluates the token state
                            Timber.tag("Onboarding").i("Discord account disconnected, card reset")
                        } else {
                            loginDiscord = true
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (discordToken.isNotEmpty()) {
                    // A Discord token is set: the presence manager is created when the
                    // player service starts, so the restart must happen before the app
                    // opens — the flag is written first, so the restart lands directly
                    // in the app
                    Timber.tag("Onboarding").i("Accounts step skipped with Discord connected, restart requested")
                    onDiscordConnected()
                    RestartAppDialog.showDialog()
                } else {
                    Timber.tag("Onboarding").i("Accounts step skipped, onboarding complete")
                    onComplete()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = colorPalette().accent,
                contentColor = colorPalette().textSecondary
            ),
            shape = uiRoundnessShape(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                // "Skip" while nothing is connected, "I'm done" once at least one
                // account is logged in — the user leaves the step either way
                stringResource(
                    if (lastfmSession.isNotEmpty() || discordToken.isNotEmpty()) R.string.onboard_accounts_done
                    else R.string.onboard_accounts_skip
                )
            )
        }
    }

    // Last.fm login sheet — the same wrapper as the Accounts tab card
    if (lastfmConfigured) {
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
            // Cap the content at ~50% of the screen so the sheet keeps its
            // half-screen look and its anchor stays content-driven
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
                            Timber.tag("Onboarding").i("Last.fm account connected as $username")
                            // The connection only updates the card — the user leaves the
                            // step with the skip button. The fetch runs on the
                            // composition scope, which stays alive on this screen
                            lastfmCardScope.launch {
                                val avatarUrl = LastFm.getUserPicture(username).getOrNull().orEmpty()
                                if (avatarUrl.isNotEmpty()) lastfmAvatarUrl = avatarUrl
                            }
                        }
                    )
                }
            }
        }
    }

    // Discord login sheet — the same wrapper as the Accounts tab card
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
                discordToken = token
                discordUsername = username
                discordAvatar = avatar
                Timber.tag("Onboarding").i(
                    "Discord account connected${if (username.isNotEmpty()) " as $username" else ""}"
                )
                Toaster.i(R.string.discord_connected_to_discord_account)
                // The connection only updates the card — the restart prompt is deferred
                // to the skip button (the activity writes the flag just before it, so
                // the restart lands directly in the app)
            }
        )
    }

    // The restart prompt is normally composed inside the settings screen only —
    // onboarding is the other context that triggers a restart, so it must be
    // composed here for the post-Discord restart to be visible
    RestartAppDialog.Render()
}

/**
 * One onboarding account card (same pattern as the permission cards): the account,
 * its current status as description, and the connect/disconnect action.
 */
@Composable
private fun OnboardingAccountCard(
    icon: Int,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(containerColor = colorPalette().background1)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colorPalette().accent.copy(alpha = 0.1f), shape = uiRoundnessShape()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography().s,
                    fontWeight = FontWeight.SemiBold,
                    color = colorPalette().text
                )
                Text(
                    text = description,
                    style = typography().xxs,
                    color = colorPalette().textSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette().accent,
                    contentColor = colorPalette().textSecondary
                ),
                shape = uiRoundnessShape()
            ) {
                Text(actionLabel)
            }
        }
    }
}
