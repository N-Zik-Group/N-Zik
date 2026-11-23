package app.n_zik.android.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.backup.BackupContent
import app.n_zik.android.components.dialog.backup.ImportBackupContentOptions
import app.n_zik.android.components.dialog.backup.ImportBackupFlow
import app.n_zik.android.typography
import timber.log.Timber

/** Offers an optional backup restore before onboarding continues. */
@Composable
fun OnboardingImportScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    ImportBackupFlow(
        initialOptions = BackupContent.Database.flag or BackupContent.Settings.flag,
        onComplete = onComplete,
        onProfilesImported = {
            Timber.tag("Onboarding").i("Profile state restored, advancing the onboarding")
            onComplete()
        },
    ) { flow ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colorPalette().background0)
                // The flow replaces AppNavigation here: keep the app's edge-to-edge behavior
                // - background full-bleed, content extends under the nav bar, the top bar is
                // still cleared; the nav inset is re-applied to the last child (the restart
                // note), so the final text never lands on the system nav buttons
                .statusBarsPadding()
                // Small screens / enlarged fonts: header + both cards + the note can exceed
                // the viewport, so the whole step scrolls instead of clipping
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
                text = stringResource(R.string.onboarding_restore_title),
                style = typography().l,
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().text
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_restore_description),
                style = typography().s,
                color = colorPalette().textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            OnboardingActionCard(
                icon = R.drawable.server,
                title = stringResource(R.string.backup_action_import),
                description = stringResource(R.string.backup_import_with_themes_description),
                extraContent = {
                    Spacer(modifier = Modifier.height(12.dp))

                    ImportBackupContentOptions(flow.selectedOptions, flow.selectOptions) { flow.themeBackup.Content() }

                    Spacer(modifier = Modifier.height(12.dp))
                    ButtonRow(listOf(
                        DialogButton(
                            text = stringResource(R.string.common_action_cancel),
                            onClick = flow.clear,
                            danger = true,
                        ),
                        DialogButton(
                            text = stringResource(if (flow.includeThemes) flow.themeBackup.primaryLabelRes else R.string.common_action_import),
                            onClick = flow.submit,
                            primary = true,
                            enabled = flow.canSubmit,
                        ),
                    ))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OnboardingActionCard(
                icon = R.drawable.settings,
                title = stringResource(R.string.onboarding_restore_skip),
                description = stringResource(R.string.onboarding_restore_skip_description),
                action = {
                    OnboardingPrimaryButton(
                        onClick = {
                            Timber.tag("Onboarding").i("Restore step skipped, onboarding continues")
                            onComplete()
                        },
                    ) {
                        // Bounded label: never pushes the card off-screen on small screens / large fonts
                        OnboardingActionLabel(stringResource(R.string.onboarding_restore_skip_button))
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // The note is the last child of the step: it re-applies the nav inset the column
            // dropped, so the final text stays clear of the system nav buttons
            Text(
                text = stringResource(R.string.onboarding_restore_restart_note),
                style = typography().xxs,
                color = colorPalette().textSecondary,
                modifier = Modifier.navigationBarsPadding()
            )
        }

        flow.dialogs()
    }
}
