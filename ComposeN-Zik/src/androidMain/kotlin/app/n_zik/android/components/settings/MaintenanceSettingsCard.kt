package app.n_zik.android.components.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.ui.screens.settings.ImportantSettingsDescription
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.OtherSwitchSettingEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.logs.CopyLogsDialog
import app.n_zik.android.components.maintenance.MaintenanceHealth
import app.n_zik.android.components.maintenance.MaintenanceSheet
import app.n_zik.android.components.maintenance.MaintenanceSnapshot
import app.n_zik.android.components.maintenance.convergenceStatusRes
import app.n_zik.android.components.maintenance.dbCleanupStatusRes
import app.n_zik.android.components.maintenance.dedupStatusRes
import app.n_zik.android.components.maintenance.lastRunLine
import app.n_zik.android.components.maintenance.loadMaintenanceSnapshot
import app.n_zik.android.components.maintenance.maintenanceHealth
import app.n_zik.android.components.maintenance.passStatusText
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.discord.DiscordRpcErrorState
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.services.UpdateDownloadManager
import app.n_zik.android.typography
import app.n_zik.android.utils.debug.purgeDebugLogs
import timber.log.Timber

/**
 * Maintenance settings card (Misc tab, spec "Maintenance — état de l'app en un regard"):
 * a section header with the shield icon, two categories (user decision, post-approval
 * iteration):
 * - **Stats**: a pass table in the exact Network/Quality status cards pattern
 *   ([OtherInfoSettingsEntry] layout: icon + title, trailing status underneath — one row
 *   per boot pass with the sheet's "date · status" trailing, plus a live-subsystems health
 *   row), then an "Open" entry that opens the SAME [MaintenanceSheet] as the burger
 *   "Maintenance" item.
 * - **Debug**: the debug-log switch (moved here from the removed legacy Debug card — the
 *   single source of truth for the switch) with the restart note, then the ALWAYS-visible
 *   "Export logs" entry (the [CopyLogsDialog] greys out the "Debug log" / "Both logs"
 *   options while the switch is off).
 *
 * The pass rows are a one-shot read at composition (the snapshot loader dispatches its
 * disk I/O to the DATA dispatcher and guards every chunk); the Discord error, the update
 * download state and the failed-download count are observed live, exactly like the sheet.
 * Only the sheet's RED rows count as broken systems — neutral states (not connected
 * services, battery warnings, ...) do not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceSettingsCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = colorPalette()
    var showSheet by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<MaintenanceSnapshot?>(null) }

    // Same one-shot snapshot as the sheet: the loader dispatches its disk I/O to the
    // DATA dispatcher and guards each chunk, so one failure only blanks its own row.
    LaunchedEffect(Unit) {
        snapshot = runCatching { loadMaintenanceSnapshot(context) }
            .onFailure { Timber.tag("MaintenanceSettingsCard").w(it, "Failed to load the maintenance health") }
            .getOrNull()
    }

    // The live states the health row depends on (the sheet observes the same Flows)
    val discordError by DiscordRpcErrorState.error.collectAsStateWithLifecycle()
    val updateState by UpdateDownloadManager.downloadState.collectAsStateWithLifecycle()
    val downloads by MyDownloadHelper.downloads.collectAsStateWithLifecycle()
    val downloadsFailed = downloads.values.count { it.state == Download.STATE_FAILED }

    val health = maintenanceHealth(snapshot, discordError, updateState, downloadsFailed)
    val healthText = when (health) {
        MaintenanceHealth.NoData -> stringResource(R.string.maintenance_no_data)
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
    // always visible (the dialog greys out the debug-dependent options when it is off)
    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)

    // The boot-pass table shares the sheet's chevron rows trailing status (the same
    // "date · status" cell, minus the chevron and the expansion)
    val dedup = snapshot?.dedup
    val convergence = snapshot?.convergence
    val dbCleanup = snapshot?.dbCleanup

    SettingsSectionCard(
        title = stringResource(R.string.maintenance),
        icon = R.drawable.shield_checkmark,
        description = stringResource(R.string.maintenance_card_desc),
        modifier = modifier,
        content = {
            // STATS category: the boot-pass table + the live health row
            CardCategoryLabel(stringResource(R.string.maintenance_stats))
            MaintenancePassRow(
                title = stringResource(R.string.maintenance_dedup),
                status = lastRunLine(
                    dedup?.timestamp,
                    passStatusText(dedupStatusRes(dedup)),
                    stringResource(R.string.maintenance_no_data),
                ),
                icon = R.drawable.trash,
            )
            MaintenancePassRow(
                title = stringResource(R.string.maintenance_convergence),
                status = lastRunLine(
                    convergence?.timestamp,
                    passStatusText(convergenceStatusRes(convergence)),
                    stringResource(R.string.maintenance_no_data),
                ),
                icon = R.drawable.sync,
            )
            MaintenancePassRow(
                title = stringResource(R.string.maintenance_db_cleanup),
                status = lastRunLine(
                    dbCleanup?.timestamp,
                    passStatusText(dbCleanupStatusRes(dbCleanup)),
                    stringResource(R.string.maintenance_no_data),
                ),
                icon = R.drawable.data_migration,
            )
            MaintenancePassRow(
                title = stringResource(R.string.maintenance_live_subsystems),
                status = healthText,
                statusColor = healthColor,
                icon = R.drawable.shield_checkmark,
            )
            OtherSettingsEntry(
                title = stringResource(R.string.maintenance_open),
                text = stringResource(R.string.maintenance_open_desc),
                icon = R.drawable.chevron_forward,
                onClick = { showSheet = true },
            )
            // DEBUG category: the switch (moved here from the removed legacy Debug card)
            // + the restart note + the ALWAYS-visible export entry (the dialog greys out
            // the debug-dependent options while the switch is off)
            CardCategoryLabel(stringResource(R.string.debug))
            OtherSwitchSettingEntry(
                title = stringResource(R.string.enable_log_debug),
                text = stringResource(R.string.if_enabled_create_a_log_file_to_highlight_errors),
                isChecked = logDebugEnabled,
                onCheckedChange = {
                    logDebugEnabled = it
                    if (!it) {
                        // Only the debug log is purged — the crash log is captured
                        // independently of this switch (always installed at startup)
                        // and wipes itself after two clean boots (CrashLogAutoWipe)
                        purgeDebugLogs(context.filesDir.resolve("logs"))
                    } else {
                        Toaster.i(R.string.restarting_rimusic_is_required)
                    }
                },
                icon = R.drawable.information,
            )
            ImportantSettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))
            OtherSettingsEntry(
                title = stringResource(R.string.export_logs),
                text = stringResource(R.string.export_debug_log_description),
                icon = R.drawable.copy,
                onClick = { CopyLogsDialog.showDialog() },
            )
            // The same sheet as the burger item (composed here so the card is self-contained).
            // renderLogsDialog = false: the single CopyLogsDialog.Render() host lives in
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

/**
 * Accent category label inside the card (the sheet's MenuSectionTitle pattern, compact
 * 8dp vertical padding for the card layout).
 */
@Composable
private fun CardCategoryLabel(label: String) {
    BasicText(
        text = label,
        style = typography().xxs.semiBold.copy(
            color = colorPalette().accent,
            textAlign = TextAlign.Start,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

/**
 * One row of the card's pass table: the exact [OtherInfoSettingsEntry] layout (the
 * Network/Quality status cards pattern — icon box, title, trailing status underneath),
 * with an endless marquee on the status so a long "date · status" line scrolls instead
 * of wrapping or truncating.
 */
@Composable
private fun MaintenancePassRow(
    title: String,
    status: String,
    icon: Int,
    statusColor: Color? = null,
) {
    val palette = colorPalette()
    Surface(
        modifier = Modifier
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
                    .background(
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
                BasicText(
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
