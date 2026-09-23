package app.n_zik.android.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.import.ImportDatabase
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import timber.log.Timber

/**
 * Second step of the first-launch onboarding flow (after permissions, before the name
 * step): an optional restore of the database and/or the settings from a backup file
 * (created by the app's Backup & Restore feature or by a previous version).
 *
 * Reuses the existing import pipeline ([ImportDatabase] / [ImportSettings], the same
 * components behind Settings -> Backup and restore). A successful import ends with the
 * restart prompt ([RestartAppDialog]) — its `Render()` is composed here because it is
 * normally only composed inside the settings screen, which is not alive during onboarding.
 *
 * Two exits: "skip" calls [onComplete] (the flow moves on to the name step); a
 * successful restore calls [onRestoreDone] just before the restart prompt — the
 * activity marks the onboarding complete there, so the restart lands directly in the
 * main app with the restored data (the restored settings already contain the display
 * name and the YouTube account).
 */
@Composable
fun OnboardingImportScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
    onRestoreDone: () -> Unit,
) {
    val context = LocalContext.current

    // Default to restoring both — the most useful outcome for a first-launch restore.
    // rememberSaveable: a rotation mid-flow must keep the chosen option and the
    // both-mode flag (a late picker result is evaluated against it)
    var selectedOption by rememberSaveable { mutableIntStateOf(2) }
    var bothMode by rememberSaveable { mutableStateOf(false) }

    // Settings import finished -> the activity marks the onboarding complete (flag
    // written), then the restart prompt so the app re-reads every restored value
    val importSettings = ImportSettings(context) {
        Timber.tag("Onboarding").i("Restore done, completing the onboarding before the app restart")
        onRestoreDone()
        RestartAppDialog.showDialog()
    }

    // Database import finished -> in both mode the settings import runs next,
    // otherwise straight to the restart prompt (same "complete first" behavior)
    val importDatabase = ImportDatabase(context) {
        if (bothMode) {
            Timber.tag("Onboarding").d("Database import done, chaining the settings import (both mode)")
            importSettings.onShortClick()
        } else {
            Timber.tag("Onboarding").i("Restore done, completing the onboarding before the app restart")
            onRestoreDone()
            RestartAppDialog.showDialog()
        }
    }

    val options = listOf(
        Triple(0, stringResource(R.string.database), stringResource(R.string.import_database_description)),
        Triple(1, stringResource(R.string.settings), stringResource(R.string.import_settings_description)),
        Triple(2, stringResource(R.string.import_both), stringResource(R.string.import_both_description))
    )

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
            text = stringResource(R.string.onboard_restore_title),
            style = typography().l,
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().text
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboard_restore_desc),
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
                            painter = painterResource(R.drawable.server),
                            contentDescription = null,
                            tint = colorPalette().accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.import_backup),
                            style = typography().s,
                            fontWeight = FontWeight.SemiBold,
                            color = colorPalette().text
                        )
                        Text(
                            text = stringResource(R.string.import_backup_description),
                            style = typography().xxs,
                            color = colorPalette().textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // The three restore options — same layout as ImportBackupDialog
                options.forEach { (index, title, description) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable { selectedOption = index }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == index,
                            onClick = { selectedOption = index },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorPalette().text,
                                unselectedColor = colorPalette().textSecondary
                            ),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = typography().xs,
                                fontWeight = FontWeight.SemiBold,
                                color = colorPalette().text
                            )
                            Text(
                                text = description,
                                style = typography().xxs,
                                color = colorPalette().textSecondary
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        bothMode = selectedOption == 2
                        Timber.tag("Onboarding").d("Restore started, option: $selectedOption (both: $bothMode)")
                        when (selectedOption) {
                            0 -> importDatabase.onShortClick()
                            1 -> importSettings.onShortClick()
                            else -> importDatabase.onShortClick()
                        }
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
                    Text(stringResource(R.string.import_button))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                        painter = painterResource(R.drawable.settings),
                        contentDescription = null,
                        tint = colorPalette().accent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboard_restore_skip),
                        style = typography().s,
                        fontWeight = FontWeight.SemiBold,
                        color = colorPalette().text
                    )
                    Text(
                        text = stringResource(R.string.onboard_restore_skip_desc),
                        style = typography().xxs,
                        color = colorPalette().textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        Timber.tag("Onboarding").i("Restore step skipped, onboarding continues")
                        onComplete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary
                    ),
                    shape = uiRoundnessShape()
                ) {
                    Text(stringResource(R.string.onboard_restore_skip_button))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboard_restore_restart_note),
            style = typography().xxs,
            color = colorPalette().textSecondary
        )
    }

    // The restart prompt is normally composed inside the settings screen only —
    // onboarding is the other context that triggers an import, so it must be
    // composed here for the post-import restart to be visible
    RestartAppDialog.Render()
}
