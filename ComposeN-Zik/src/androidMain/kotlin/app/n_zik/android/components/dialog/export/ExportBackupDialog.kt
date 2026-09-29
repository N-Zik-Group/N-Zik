package app.n_zik.android.components.dialog.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.dialog.export.ExportDatabaseDialog
import app.n_zik.android.components.dialog.export.ExportSettingsDialog
import app.n_zik.android.components.dialog.common.Dialog
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility

object ExportBackupDialog : Dialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.export_backup)

    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val exportDbDialog = ExportDatabaseDialog(context)
        val exportSettingsDialog = ExportSettingsDialog(context)
        val exportProfileStateDialog = ExportProfileStateDialog(context)

        // The flow state survives a rotation mid-dialog (same as the import
        // counterpart): a rotation must keep the chosen option and the credential
        // toggles — plain remember would reset them and silently change what the
        // export button exports.
        var selectedOption by rememberSaveable { mutableIntStateOf(0) }

        val databaseLabel = stringResource(R.string.database)
        val databaseDescription = stringResource(R.string.export_database_description)
        val settingsLabel = stringResource(R.string.settings)
        val settingsDescription = stringResource(R.string.export_settings_description)
        val bothLabel = stringResource(R.string.database_and_settings)
        val bothDescription = stringResource(R.string.export_both_description)
        // The option is the profile state (the list + every face), not the login
        // accounts: labeled "Profiles" like everywhere else in the app.
        val profilesLabel = stringResource(R.string.profiles)
        val profilesDescription = stringResource(R.string.export_accounts_description)
        val allLabel = stringResource(R.string.export_all)
        val allDescription = stringResource(R.string.export_all_description)

        val options = listOf(
            Triple(R.drawable.server, databaseLabel, databaseDescription),
            Triple(R.drawable.settings, settingsLabel, settingsDescription),
            Triple(R.drawable.server, bothLabel, bothDescription),
            Triple(R.drawable.person, profilesLabel, profilesDescription),
            Triple(R.drawable.server, allLabel, allDescription)
        )

        var includeYtbCredentials by rememberSaveable { mutableStateOf(false) }
        var includeDiscordCredentials by rememberSaveable { mutableStateOf(false) }
        var includeLastfmCredentials by rememberSaveable { mutableStateOf(false) }
        var includeProxyCredentials by rememberSaveable { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                options.forEachIndexed { index, (_, title, description) ->
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
                                style = typography().xs.semiBold,
                                color = colorPalette().text
                            )
                            Text(
                                text = description,
                                style = typography().xxs,
                                color = colorPalette().textSecondary
                            )
                        }
                    }
                    
                    // The credential toggles belong to the settings CSV, which options
                    // Settings / Database + Settings / All all export.
                    AnimatedVisibility(visible = selectedOption == index && (index == 1 || index == 2 || index == 4)) {
                        Column(modifier = Modifier.padding(start = 44.dp, bottom = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { includeYtbCredentials = !includeYtbCredentials }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (includeYtbCredentials) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                    contentDescription = null,
                                    tint = if (includeYtbCredentials) colorPalette().text else colorPalette().textSecondary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(stringResource(R.string.include_youtube_credentials), style = typography().xxs, color = colorPalette().text)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { includeDiscordCredentials = !includeDiscordCredentials }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (includeDiscordCredentials) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                    contentDescription = null,
                                    tint = if (includeDiscordCredentials) colorPalette().text else colorPalette().textSecondary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(stringResource(R.string.include_discord_credentials), style = typography().xxs, color = colorPalette().text)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { includeLastfmCredentials = !includeLastfmCredentials }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (includeLastfmCredentials) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                    contentDescription = null,
                                    tint = if (includeLastfmCredentials) colorPalette().text else colorPalette().textSecondary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(stringResource(R.string.include_lastfm_credentials), style = typography().xxs, color = colorPalette().text)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { includeProxyCredentials = !includeProxyCredentials }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (includeProxyCredentials) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                    contentDescription = null,
                                    tint = if (includeProxyCredentials) colorPalette().text else colorPalette().textSecondary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(stringResource(R.string.include_proxy_credentials), style = typography().xxs, color = colorPalette().text)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    when (selectedOption) {
                        0 -> exportDbDialog.export()
                        1 -> exportSettingsDialog.export(includeYtbCredentials, includeDiscordCredentials, includeLastfmCredentials, includeProxyCredentials)
                        2 -> {
                            exportDbDialog.export()
                            exportSettingsDialog.export(includeYtbCredentials, includeDiscordCredentials, includeLastfmCredentials, includeProxyCredentials)
                        }
                        3 -> exportProfileStateDialog.export()
                        4 -> {
                            // All: the three separate export sequences, like "both" plus the profile state.
                            exportDbDialog.export()
                            exportSettingsDialog.export(includeYtbCredentials, includeDiscordCredentials, includeLastfmCredentials, includeProxyCredentials)
                            exportProfileStateDialog.export()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette().accent,
                    contentColor = colorPalette().textSecondary
                ),
                shape = uiRoundnessShape()
            ) {
                Text(
                    text = stringResource(R.string.export),
                    style = typography().s.medium
                )
            }
        }
    }
}
