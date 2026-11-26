package app.n_zik.android.components.settings

import app.n_zik.android.utils.debug.profileDiagnosticsDir
import app.n_zik.android.components.settings.common.SearchableSettingsSectionHeading
import app.n_zik.android.components.settings.common.settingsSearchTarget

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.components.settings.common.ImportantSettingsDescription
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.utils.logDebugEnabledKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.logs.CopyLogsDialog
import app.n_zik.android.components.maintenance.MaintenanceHealth
import app.n_zik.android.components.maintenance.MaintenanceSheet
import app.n_zik.android.components.maintenance.MaintenanceSnapshot
import app.n_zik.android.components.maintenance.loadMaintenanceSnapshot
import app.n_zik.android.components.maintenance.maintenanceHealth
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.discord.DiscordRpcErrorState
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.services.UpdateDownloadManager
import app.n_zik.android.typography
import app.n_zik.android.utils.debug.purgeDebugLogs
import timber.log.Timber
import kotlinx.coroutines.CancellationException
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder

/** Shows live health and opens the detailed maintenance sheet from Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceSettingsCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = colorPalette()
    var showSheet by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<MaintenanceSnapshot?>(null) }
    var snapshotReadFailed by remember { mutableStateOf(false) }

    // Same one-shot snapshot as the sheet: the loader dispatches its disk I/O to the
    // DATA dispatcher and guards each chunk, so one failure only blanks its own row.
    LaunchedEffect(Unit) {
        try {
            snapshot = loadMaintenanceSnapshot(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("MaintenanceSettingsCard").w(error, "Failed to load the maintenance health")
            snapshotReadFailed = true
        }
    }

    // The live states the health row depends on (the sheet observes the same Flows)
    val discordError by DiscordRpcErrorState.error.collectAsStateWithLifecycle()
    val updateState by UpdateDownloadManager.downloadState.collectAsStateWithLifecycle()
    val downloads by MyDownloadHelper.downloads.collectAsStateWithLifecycle()
    val downloadsFailed = downloads.values.count { it.state == Download.STATE_FAILED }

    val health = maintenanceHealth(snapshot, discordError, updateState, downloadsFailed)
    val healthText = when (health) {
        MaintenanceHealth.NoData -> if (snapshotReadFailed) stringResource(R.string.maintenance_no_data) else null
        MaintenanceHealth.Operational -> stringResource(R.string.maintenance_all_systems_ok)
        is MaintenanceHealth.NeedsAttention ->
            context.resources.getQuantityString(
                R.plurals.maintenance_systems_need_attention,
                health.count,
                health.count,
            )
    }
    val healthColor = if (health is MaintenanceHealth.NeedsAttention) palette.red else palette.textSecondary

    // The debug-log switch moved from the (removed) legacy Debug card into this card —
    // it is the single source of truth for the switch; the Export entry below it stays
//
    // always visible (the dialog greys out the debug-dependent options when it is off)
    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.maintenance_title),
        icon = R.drawable.shield_checkmark,
        modifier = modifier,
        content = {
            SearchableSettingsSectionHeading(R.string.settings_group_maintenance_tools, "embedded.maintenance.section.tools", showDivider = false)
            MaintenanceHealthRow(
                modifier = Modifier.settingsSearchTarget("embedded.maintenance.live_subsystems"),
                title = stringResource(R.string.maintenance_live_subsystems),
                status = healthText,
                statusColor = healthColor,
                icon = R.drawable.shield_checkmark,
            )
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("embedded.maintenance.maintenance_open"),
                title = stringResource(R.string.maintenance_open),
                text = stringResource(R.string.maintenance_open_desc),
                icon = R.drawable.chevron_forward,
                onClick = { showSheet = true },
            )
            // DEBUG category: the switch (moved here from the removed legacy Debug card)
            // + the restart note + the ALWAYS-visible export entry (the dialog greys out
//
            // the debug-dependent options while the switch is off)
            SearchableSettingsSectionHeading(R.string.common_debug_title, "embedded.maintenance.section.debug")
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("embedded.maintenance.enable_log_debug"),
                title = stringResource(R.string.maintenance_debug_logging_title),
                text = stringResource(R.string.settings_logging_error_file_description),
                isChecked = logDebugEnabled,
                onCheckedChange = {
                    logDebugEnabled = it
                    if (!it) {
                        // Only the debug log is purged — the crash log is captured
                        // independently of this switch (always installed at startup)
//
                        // and wipes itself after two clean boots (CrashLogAutoWipe)
                        purgeDebugLogs(profileDiagnosticsDir(context))
                    } else {
                        Toaster.i(R.string.settings_restart_required_message)
                    }
                },
                icon = R.drawable.information,
            )
            ImportantSettingsDescription(text = stringResource(R.string.settings_restart_required_message))
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("embedded.maintenance.export_logs"),
                title = stringResource(R.string.logs_action_export),
                text = stringResource(R.string.logs_export_debug_description),
                icon = R.drawable.copy,
                onClick = { CopyLogsDialog.showDialog() },
            )
            // The same sheet as the burger item (composed here so the card is self-contained).
            // renderLogsDialog = false: the single CopyLogsDialog.Render() host lives in
//
            // the persistent header, and Render() is a singleton — a second Render() from
            // this sheet would stack a second dialog on top of the first.
            MaintenanceSheet(
                showSheet = showSheet,
                onDismissRequest = { showSheet = false },
                renderLogsDialog = false,
            )
        },
    )
}

@Composable
private fun MaintenanceHealthRow(
    title: String,
    status: String?,
    icon: Int,
    statusColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val palette = colorPalette()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape()),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon (the shared settings icon box)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .iconBadgeBackground(
                        color = palette.accent.copy(alpha = 0.1f),
                        shape = uiRoundnessShape(),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    tint = palette.accent,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = title,
                    style = typography().s.semiBold.copy(
                        color = palette.text,
                    ),
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (status == null) {
                    ItemTextPlaceholder(null, typography().xs, width = 0.45f)
                } else BasicText(
                    text = status,
                    style = typography().xs.copy(
                        color = statusColor ?: palette.textSecondary,
                    ),
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }
    }
}
