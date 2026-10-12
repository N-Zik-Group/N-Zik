package app.n_zik.android.components.dialog.updater

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.enums.CheckUpdateState
import app.n_zik.android.BuildConfig
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.models.UpdaterConstants
import app.n_zik.android.updater.services.Updater

object CheckForUpdateDialog {

    private var isCanceled: Boolean by mutableStateOf(false)
    var isActive: Boolean by mutableStateOf(false)

    fun onDismiss() {
        isCanceled = true
        isActive = false
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Render() {
        if (isCanceled || !isActive) return

        var checkUpdateState by rememberPreference(checkUpdateStateKey, CheckUpdateState.Enabled)
        val checkBetaUpdates by rememberPreference(checkBetaUpdatesKey, Updater.extractVersionSuffix(BuildConfig.VERSION_NAME) == UpdaterConstants.SUFFIX_BETA.removePrefix("-"))

        DialogFrame(onDismiss = { onDismiss() }) {
            // Header with title
            Icon(
                painter = painterResource(R.drawable.update),
                contentDescription = null,
                tint = colorPalette().accent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = stringResource(R.string.check_at_github_for_updates),
                style = typography().l.bold.copy(color = colorPalette().text)
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = stringResource(R.string.when_an_update_is_available_you_will_be_asked_if_you_want_to_install_info),
                style = typography().xs.copy(color = colorPalette().textSecondary)
            )
            BasicText(
                text = stringResource(R.string.but_these_updates_would_not_go_through),
                style = typography().xs.copy(color = colorPalette().textSecondary)
            )
            BasicText(
                text = stringResource(R.string.you_can_still_turn_it_on_or_off_from_the_settings),
                style = typography().xs.copy(color = colorPalette().textSecondary)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Check for updates
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(uiRoundnessShape())
                    .clickable {
                        onDismiss()
                        Updater.checkForUpdate(checkBetaUpdates = checkBetaUpdates)
                    }
                    .background(colorPalette().accent)
                    .padding(16.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.check_update),
                    style = typography().xs.semiBold.copy(color = colorPalette().onAccent),
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
                Icon(
                    painter = painterResource(R.drawable.update),
                    contentDescription = null,
                    tint = colorPalette().onAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Option 2: Cancel (skip this time)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(uiRoundnessShape())
                    .clickable { onDismiss() }
                    .background(colorPalette().red)
                    .padding(16.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.cancel),
                    style = typography().xs.semiBold.copy(color = Color.White),
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
                Icon(
                    painter = painterResource(R.drawable.arrow_left),
                    contentDescription = null,
                    // Danger language: white on the filled-red cancel row.
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Option 3: Turn off auto check
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(uiRoundnessShape())
                    .clickable {
                        checkUpdateState = CheckUpdateState.Disabled
                        onDismiss()
                    }
                    .background(colorPalette().background2)
                    .padding(16.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.turn_off),
                    style = typography().xs.semiBold.copy(color = colorPalette().text),
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
