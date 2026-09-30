package app.n_zik.android.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.navigation.compose.rememberNavController
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
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
import app.n_zik.android.BuildConfig
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.appContext
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
import app.n_zik.android.ytAccountName
import it.fast4x.innertube.Innertube
import it.fast4x.lastfm.LastFm
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * First-composition behavior of the accounts step, as a pure mapping (unit-tested in
 * [OnboardingAccountsStepActionTest]).
 */
enum class OnboardingAccountsStepAction {
    /** Show the step as usual. */
    SHOW,

    /** Auto-advance: all three accounts are already connected, nothing left to connect. */
    AUTO_ADVANCE
}

/**
 * All three optional accounts (YouTube, Last.fm, Discord) already connected (e.g.
 * restored by the import step) means there is nothing left to configure: the step
 * is skipped on its own, the same way the skip button would in that state (Discord
 * path: the restart prompt shows after the advance — a Discord token is only live
 * after a restart, which lands on the profile step with the flag unwritten).
 */
fun accountsStepInitialAction(
    youtubeConnected: Boolean,
    lastFmConnected: Boolean,
    discordConnected: Boolean,
): OnboardingAccountsStepAction =
    if (youtubeConnected && lastFmConnected && discordConnected) OnboardingAccountsStepAction.AUTO_ADVANCE
    else OnboardingAccountsStepAction.SHOW

/**
 * Step of the first-launch onboarding flow between the import and the profile
 * step: optional account connections (YouTube, Last.fm scrobbling and Discord
 * rich presence). The accounts come before the clone's identity so the account
 * face sources can already be unlocked on the profile step.
 *
 * Reuses the existing login components — the same ones behind the Accounts tab:
 * [YouTubeLogin] in its sheet (YouTube), [LastFmLoginContent] in its sheet
 * (Last.fm) and [DiscordLoginAndGetToken] in its sheet (Discord) — writing to the
 * same encrypted prefs, so the onboarding and the Accounts tab observe the same
 * state. A YouTube login applies the same side effects as the Accounts tab (cookie
 * live immediately) and updates the card without a restart.
 *
 * Everything is optional and connecting an account never advances the flow — the
 * cards only log in (same encrypted prefs as the Accounts tab) and update their
 * status. Leaving the step is always through the skip button — labeled "Skip" while
 * nothing is connected and "I'm done" once at least one account is logged in.
 * Leaving advances to the profile step (persisted); with a Discord token set, the
 * restart prompt ([RestartAppDialog]) shows after the advance — the presence
 * manager is created when the player service starts, so the fresh token is only
 * picked up on a new launch, and the restart lands on the profile step (the
 * onboarding-complete flag stays unwritten). YouTube and Last.fm never need a
 * restart. The prompt's `Render()` lives in the activity's onboarding container —
 * it must survive the step transition, which a screen-local composition would not.
 *
 * Auto-skip: when ALL THREE accounts are already connected on first composition
 * (restored by the import step, or a returning state), the step is skipped on its
 * own — the same way the skip button would in that state
 * ([accountsStepInitialAction]): the flow advances to the profile step and the
 * restart prompt shows (a Discord token is only live after a restart).
 *
 * @param onComplete leave the step — the flow advances to the profile step (the
 *   activity persists it; with a Discord token set the restart prompt shows and
 *   the restart lands on the profile step)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingAccountsScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    // YouTube state — the same secure prefs as the Accounts tab card
    var loginYoutube by remember { mutableStateOf(false) }
    // One-shot read of the login state at first composition (a cookie restored by
    // the import step, or a returning state): it can only change from this screen
    // (the login / logoff below flip it explicitly), so a stale read is acceptable
    // by contract.
    var ytLoggedIn by remember { mutableStateOf(isYouTubeLoggedIn()) }
    var ytName by remember { mutableStateOf(if (ytLoggedIn) ytAccountName() else "") }

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

    // Logoff: the minimal mirror of the Accounts tab logout — the destructive
    // synced-data clear is skipped because onboarding is a fresh install, there
    // is nothing synced to wipe
    fun logOffYouTube() {
        val ep = appContext().encryptedPreferences
        ep.edit().putString(ytCookieKey, "").apply()
        ep.edit().putString(ytAccountNameKey, "").apply()
        ep.edit().putString(ytAccountChannelHandleKey, "").apply()
        ep.edit().putString(ytAccountEmailKey, "").apply()
        ep.edit().putString(ytAccountThumbnailKey, "").apply()
        ep.edit().putString(ytVisitorDataKey, "").apply()
        ep.edit().putString(ytDataSyncIdKey, "").apply()
        // Force recomposition of every encrypted-prefs observer (Accounts tab pattern)
        encryptedPreferencesUpdateTrigger++
        appContext().preferences.edit().remove(ytCookieExpiredKey).apply()
        appContext().preferences.edit().putBoolean(useLoginForBrowseKey, false).apply()
        Innertube.useLoginForBrowse = false
        MainApplication.cookieStatus = MainApplication.CookieStatus.NOT_LOGGED_IN
        ytLoggedIn = false
        ytName = ""
        Timber.tag("Onboarding").i("YouTube logged off from onboarding")
    }

    // All three accounts already connected (restored by the import step, or a
    // returning state): skip the step the same way the skip button would —
    // advance to the profile step, then the restart prompt (a Discord token is
    // only live after a restart; the restart lands on the profile step)
    LaunchedEffect(Unit) {
        if (
            accountsStepInitialAction(ytLoggedIn, lastfmSession.isNotEmpty(), discordToken.isNotEmpty()) ==
            OnboardingAccountsStepAction.AUTO_ADVANCE
        ) {
            Timber.tag("Onboarding").i("All accounts already connected, auto-skipping the accounts step")
            onComplete()
            RestartAppDialog.showDialog()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            // The flow replaces AppNavigation here: keep the app's edge-to-edge
            // behavior — background full-bleed, content clear of the system bars
            .statusBarsPadding()
            .navigationBarsPadding()
            // Small screens / enlarged fonts: header + cards + the skip button can exceed
            // the viewport — the whole step scrolls instead of clipping
            .verticalScroll(rememberScrollState())
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

        // No screen-level description: the three cards each carry their own, and the
        // removed one predates the YouTube card (it only listed Last.fm + Discord)
        Spacer(modifier = Modifier.height(24.dp))

        // Plain Column: three cards at most, no recycling needed — and the screen
        // column scrolls, so a nested lazy list would fight it for the gesture
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingActionCard(
                icon = R.drawable.logo_youtube,
                title = stringResource(R.string.onboard_accounts_youtube),
                description = if (ytLoggedIn && ytName.isNotBlank()) {
                    stringResource(R.string.onboard_accounts_youtube_account, ytName)
                } else {
                    stringResource(R.string.onboard_accounts_youtube_desc)
                },
                action = {
                    OnboardingAccountActionButton(
                        label = stringResource(onboardingAccountButtonResId(ytLoggedIn)),
                        onClick = {
                            if (ytLoggedIn) logOffYouTube() else loginYoutube = true
                        }
                    )
                }
            )

            if (lastfmConfigured) {
                OnboardingActionCard(
                    icon = R.drawable.logo_lastfm,
                    title = stringResource(R.string.social_lastfm),
                    description = if (lastfmSession.isNotEmpty()) {
                        stringResource(R.string.lastfm_connected)
                    } else {
                        stringResource(R.string.social_lastfm_info)
                    },
                    action = {
                        OnboardingAccountActionButton(
                            label = stringResource(onboardingAccountButtonResId(lastfmSession.isNotEmpty())),
                            onClick = {
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
                )
            }

            OnboardingActionCard(
                icon = R.drawable.logo_discord,
                title = stringResource(R.string.social_discord),
                description = if (discordToken.isNotEmpty()) {
                    stringResource(R.string.discord_connected_to_discord_account)
                } else {
                    stringResource(R.string.onboard_accounts_discord_desc)
                },
                action = {
                    OnboardingAccountActionButton(
                        label = stringResource(onboardingAccountButtonResId(discordToken.isNotEmpty())),
                        onClick = {
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
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // The accounts step no longer ends the flow — the profile step
                // follows: advance (the step is persisted), and with a Discord
                // token set the restart prompt shows after the advance — the
                // presence manager is created when the player service starts,
                // so the fresh token is only picked up on a new launch, and the
                // restart lands on the profile step (flag unwritten)
                Timber.tag("Onboarding").i(
                    if (discordToken.isNotEmpty()) {
                        "Accounts step skipped with Discord connected, restart requested"
                    } else {
                        "Accounts step skipped"
                    }
                )
                onComplete()
                if (discordToken.isNotEmpty()) RestartAppDialog.showDialog()
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
                    if (ytLoggedIn || lastfmSession.isNotEmpty() || discordToken.isNotEmpty()) R.string.onboard_accounts_done
                    else R.string.onboard_accounts_skip
                )
            )
        }
    }

    // YouTube login sheet — the same wrapper as the old name step's sheet
    CustomModalBottomSheet(
        showSheet = loginYoutube,
        onDismissRequest = {
            loginYoutube = false
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
                    loginYoutube = false
                    // Same side effects as the Accounts tab login — the cookie is
                    // live immediately, so the card updates without a restart
                    appContext().preferences.edit().putBoolean(ytCookieExpiredKey, false).apply()
                    MainApplication.cookieStatus = MainApplication.CookieStatus.VALID
                    appContext().preferences.edit().putBoolean(useLoginForBrowseKey, true).apply()
                    Innertube.useLoginForBrowse = true
                    ytLoggedIn = true
                    ytName = ytAccountName()
                    Timber.tag("Onboarding").i("YouTube account connected from onboarding")
                    Toaster.i(R.string.youtube_login_successful)
                }
            }
        )
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
                // to the skip button (the restart lands on the profile step — the
                // onboarding-complete flag stays unwritten until the flow ends)
            }
        )
    }

}

/**
 * Action button of an onboarding account card (same look as the other onboarding
 * buttons): the label is bounded by [OnboardingActionLabel] so it can never push
 * the card off-screen on small screens / large fonts.
 */
@Composable
private fun OnboardingAccountActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = colorPalette().accent,
            contentColor = colorPalette().textSecondary
        ),
        shape = uiRoundnessShape()
    ) {
        OnboardingActionLabel(label)
    }
}
