package app.n_zik.android.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.useLoginForBrowseKey
import app.it.fast4x.rimusic.utils.ytCookieExpiredKey
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.ytAccountName
import it.fast4x.innertube.Innertube
import timber.log.Timber

/**
 * Third step of the first-launch onboarding flow: pick where the display name
 * shown on the Rewind slides comes from.
 *
 * "Log in (YouTube)" reuses the existing cookie WebView login from the Accounts tab
 * (same [YouTubeLogin] composable, same on-login side effects); success switches the
 * source to `youtube`. "Continue as guest" stores an optional custom name and switches
 * the source to `custom` (blank name keeps the app default). Either choice moves on to
 * the optional accounts step — nothing is enforced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingNameScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var loginYouTube by remember { mutableStateOf(false) }
    // Prefilled from an existing custom name, so returning users keep their name
    var guestName by remember {
        mutableStateOf(DataStoreUtils.getString(context, DataStoreUtils.KEY_USERNAME, ""))
    }

    // One-shot reads: the screen leaves as soon as a choice is made, so a stale value
    // (no login side-effect recomposition here) is acceptable by contract.
    val ytLoggedIn = remember { isYouTubeLoggedIn() }
    // The account name is only read while an account is actually connected
    // (the choice is custom (guest) or YouTube)
    val ytName = remember { if (ytLoggedIn) ytAccountName() else "" }

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
            text = stringResource(R.string.onboard_name_title),
            style = typography().l,
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().text
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboard_name_greeting),
            style = typography().s,
            color = colorPalette().textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

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
                        painter = painterResource(R.drawable.logo_youtube),
                        contentDescription = null,
                        tint = colorPalette().accent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboard_name_youtube),
                        style = typography().s,
                        fontWeight = FontWeight.SemiBold,
                        color = colorPalette().text
                    )
                    Text(
                        text = if (ytLoggedIn && ytName.isNotBlank()) {
                            stringResource(R.string.onboard_name_youtube_account, ytName)
                        } else {
                            stringResource(R.string.onboard_name_youtube_desc)
                        },
                        style = typography().xxs,
                        color = colorPalette().textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { loginYouTube = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary
                    ),
                    shape = uiRoundnessShape()
                ) {
                    Text(stringResource(R.string.onboard_name_youtube_button))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = uiRoundnessShape(),
            colors = CardDefaults.cardColors(containerColor = colorPalette().background1)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colorPalette().accent.copy(alpha = 0.1f), shape = uiRoundnessShape()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            tint = colorPalette().accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.onboard_name_guest),
                            style = typography().s,
                            fontWeight = FontWeight.SemiBold,
                            color = colorPalette().text
                        )
                        Text(
                            text = stringResource(R.string.onboard_name_guest_desc),
                            style = typography().xxs,
                            color = colorPalette().textSecondary
                        )
                    }
                }

                // stringResource is @Composable: hoisted out of the non-composable
                // semantics lambda
                val fieldHint = stringResource(R.string.onboard_name_field_hint)
                TextField(
                    value = guestName,
                    onValueChange = { guestName = it },
                    singleLine = true,
                    placeholder = { Text(fieldHint) },
                    colors = InputDialog.defaultTextFieldColors(),
                    shape = uiRoundnessShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics {
                            contentDescription = fieldHint
                        }
                )

                Button(
                    onClick = {
                        val name = guestName.trim()
                        DataStoreUtils.saveString(context, DataStoreUtils.KEY_USERNAME, name)
                        DataStoreUtils.saveString(
                            context,
                            DataStoreUtils.KEY_DISPLAY_NAME_SOURCE,
                            DataStoreUtils.DISPLAY_NAME_SOURCE_CUSTOM
                        )
                        Timber.tag("Onboarding").i("Guest display name saved (blank keeps the default), source=custom")
                        onComplete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary
                    ),
                    shape = uiRoundnessShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(stringResource(R.string.onboard_name_continue))
                }
            }
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
                        loginYouTube = false
                        // Same side effects as the Accounts tab login, then the source toggle
                        appContext().preferences.edit().putBoolean(ytCookieExpiredKey, false).apply()
                        MainApplication.cookieStatus = MainApplication.CookieStatus.VALID
                        appContext().preferences.edit().putBoolean(useLoginForBrowseKey, true).apply()
                        Innertube.useLoginForBrowse = true
                        DataStoreUtils.saveString(
                            context,
                            DataStoreUtils.KEY_DISPLAY_NAME_SOURCE,
                            DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE
                        )
                        Timber.tag("Onboarding").i("YouTube login done, display name source=youtube")
                        Toaster.i(R.string.youtube_login_successful)
                        onComplete()
                    }
                }
            )
        }
    }
}
