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
            title = stringResource(R.string.updates_action_check),
            onDismiss = { onDismiss() },
            bodyPadding = PaddingValues(0.dp),
        footer = {
                ButtonRow(listOf(
                    DialogButton(stringResource(R.string.updates_action_check), {
                        onDismiss()
                        Updater.checkForUpdate(checkBetaUpdates = checkBetaUpdates)
                    }, primary = true, icon = R.drawable.update),
                    DialogButton(stringResource(R.string.common_action_cancel), { onDismiss() }, danger = true, icon = R.drawable.arrow_left),
                    DialogButton(stringResource(R.string.common_action_turn_off), {
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
                    text = stringResource(R.string.updates_check_permission_message),
                    style = typography().s.copy(color = colorPalette().text)
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    text = stringResource(R.string.updates_check_permission_description),
                    style = typography().xs.copy(color = colorPalette().textSecondary)
                )
                BasicText(
                    text = stringResource(R.string.updates_check_permission_screening_note),
                    style = typography().xs.copy(color = colorPalette().textSecondary)
                )
                BasicText(
                    text = stringResource(R.string.updates_check_permission_settings_note),
                    style = typography().xs.copy(color = colorPalette().textSecondary)
                )
            }
        }
        }
    }
}
