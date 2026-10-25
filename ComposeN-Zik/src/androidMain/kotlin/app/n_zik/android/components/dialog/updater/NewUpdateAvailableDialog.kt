package app.n_zik.android.components.dialog.updater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.updateCancelledKey
import app.n_zik.android.appContext
import app.n_zik.android.updater.services.Updater

object NewUpdateAvailableDialog {

    /**
     * `false` by default. When set to `true`, the update dialog is not shown even when
     * [isActive] is `true` — a one-shot dismissal that resets when the app restarts.
     */
    var isCancelled: Boolean by mutableStateOf( !BuildConfig.IS_AUTOUPDATE )

    var isActive: Boolean by mutableStateOf( false )

    fun onDismiss() {
        isCancelled = true
        isActive = false

        // Mark update as cancelled when user cancels (but don't update the check time)
        val sharedPrefs = appContext().getSharedPreferences("settings", 0)
        sharedPrefs.edit()
            .putBoolean(updateCancelledKey, true)
            .apply()
    }

    @Composable
    fun Render(onNavigateToUpdater: () -> Unit = {}) {
        if( isCancelled || !isActive ) return

        DialogFrame(onDismiss = { onDismiss() }) {
            val updateVersion = Updater.getDisplayVersion()
            val updateLabelModel = Updater.flavorLabel(updateVersion) ?: Updater.FlavorLabel(R.string.release_title)
            val updateLabel = if (updateLabelModel.literal != null) updateLabelModel.literal
            else stringResource(updateLabelModel.resId) + if (updateLabelModel.append32) " 32" else ""

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .testTag("updateDialogColumn")
                    .fillMaxWidth()
            ) {
                // Header with title
                Icon(
                    painter = painterResource(R.drawable.update),
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    text = "$updateLabel ${stringResource(R.string.update_available)}",
                    style = typography().l.bold.copy(color = colorPalette().text)
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = stringResource(R.string.app_update_dialog_version, updateVersion),
                    style = typography().xs.copy(color = colorPalette().textSecondary)
                )
                BasicText(
                    text = stringResource(R.string.app_update_dialog_size, Updater.build.readableSize.ifEmpty { "?" }),
                    style = typography().xs.copy(color = colorPalette().textSecondary)
                )

                Spacer(modifier = Modifier.height(16.dp))

                ButtonRow(
                    buttons = listOf(
                        DialogButton(
                            text = stringResource(R.string.cancel),
                            icon = R.drawable.close,
                            onClick = { onDismiss() },
                            danger = true
                        ),
                        DialogButton(
                            text = stringResource(R.string.go_to_download),
                            icon = R.drawable.chevron_forward,
                            onClick = {
                                onDismiss()
                                onNavigateToUpdater()
                            },
                            primary = true
                        )
                    )
                )
            }
        }
    }
}
