package app.n_zik.android.components.dialog.updater

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.updater.models.CheckUpdateState
import app.n_zik.android.BuildConfig
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
        DialogPresence(visible = isActive && !isCanceled, inherit = true) {

        var checkUpdateState by rememberPreference(checkUpdateStateKey, CheckUpdateState.Enabled)
        val checkBetaUpdates by rememberPreference(checkBetaUpdatesKey, Updater.isBetaFamily(BuildConfig.VERSION_NAME))

        DialogFrame(
            title = stringResource(R.string.check_update),
            onDismiss = { onDismiss() },
            bodyPadding = PaddingValues(0.dp),
        footer = {
                ButtonRow(listOf(
                    DialogButton(stringResource(R.string.check_update), {
                        onDismiss()
                        Updater.checkForUpdate(checkBetaUpdates = checkBetaUpdates)
                    }, primary = true, icon = R.drawable.update),
                    DialogButton(stringResource(R.string.cancel), { onDismiss() }, danger = true, icon = R.drawable.arrow_left),
                    DialogButton(stringResource(R.string.turn_off), {
                        checkUpdateState = CheckUpdateState.Disabled
                        onDismiss()
                    }, icon = R.drawable.close)
                ))
            }
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.update),
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                BasicText(
                    text = stringResource(R.string.check_at_github_for_updates),
                    style = typography().s.copy(color = colorPalette().text)
                )
                Spacer(modifier = Modifier.height(8.dp))
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
            }
        }
        }
    }
}
