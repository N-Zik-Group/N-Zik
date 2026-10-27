package app.n_zik.android.components.dialog.updater

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.updater.models.UpdaterConstants

/** Stable<->Beta build transition warning, on the shared [DialogFrame]: alert header, message and a single confirm [ButtonRow] pill. */
object BuildTransitionWarningDialog {
    var isActive: Boolean by mutableStateOf(false)
    var transitionType: String? by mutableStateOf(null)

    @Composable
    fun Render(onConfirm: () -> Unit) {
        DialogPresence(target = transitionType.takeIf { isActive }) { transitionType ->

        val titleRes = if (transitionType == "${UpdaterConstants.TYPE_STABLE}-to-${UpdaterConstants.TYPE_BETA}") R.string.stable_to_beta_warning_title else R.string.beta_to_stable_warning_title
        val messageRes = if (transitionType == "${UpdaterConstants.TYPE_STABLE}-to-${UpdaterConstants.TYPE_BETA}") R.string.stable_to_beta_warning_message else R.string.beta_to_stable_warning_message

        DialogFrame(
            title = stringResource(titleRes),
            onDismiss = {
                isActive = false
                onConfirm()
            },
            bodyPadding = PaddingValues(0.dp),
        footer = {
                ButtonRow(
                    buttons = listOf(
                        DialogButton(
                            text = stringResource(R.string.major_update_warning_button),
                            icon = R.drawable.checkmark,
                            onClick = {
                                isActive = false
                                onConfirm()
                            },
                            primary = true
                        )
                    )
                )
            }
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.alert),
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))


                // Message
                BasicText(
                    text = stringResource(messageRes),
                    style = typography().s.semiBold.copy(color = colorPalette().text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        }
    }
}
