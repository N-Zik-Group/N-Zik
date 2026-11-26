package app.n_zik.android.components.maintenance

import java.util.Locale

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.settings.common.settingsEntryEnter
import app.n_zik.android.components.settings.common.settingsEntryExit
import app.n_zik.android.core.maintenance.MaintenanceDedupState
import app.n_zik.android.typography

@Composable
internal fun ColumnScope.MaintenanceDatabaseSection(
    snapshot: MaintenanceSnapshot?,
    readPhase: MaintenanceReadPhase,
    statusColumnWidth: Dp,
    locale: Locale,
    sheetStatusColor: Color,
    dedupExpanded: Boolean,
    onToggleDedup: () -> Unit,
    convergenceExpanded: Boolean,
    onToggleConvergence: () -> Unit,
    dbCleanupExpanded: Boolean,
    onToggleDbCleanup: () -> Unit,
    downloadRevalidationExpanded: Boolean,
    onToggleDownloadRevalidation: () -> Unit,
    downloadSelfHealExpanded: Boolean,
    onToggleDownloadSelfHeal: () -> Unit,
) {
    MenuSectionTitle(stringResource(R.string.maintenance_section_database))

    val dedup = snapshot?.dedup
    DatabasePassRow(
        titleResId = R.string.maintenance_dedup,
        icon = R.drawable.trash,
        timestamp = dedup?.timestamp,
        status = dedupStatusRes(dedup),
        readPhase = readPhase,
        statusColumnWidth = statusColumnWidth,
        locale = locale,
        expanded = dedupExpanded,
        onToggle = onToggleDedup,
    ) { summary ->
        GroupedPassDetails(
            lines = if (dedupExpanded) dedupGroupLines(dedup) else emptyList(),
            summary = summary,
            emptyText = {
                stringResource(if (dedup == null) R.string.maintenance_no_data else R.string.maintenance_pass_clean)
            },
        )
    }

    val convergence = snapshot?.convergence
    DatabasePassRow(
        titleResId = R.string.maintenance_convergence,
        icon = R.drawable.sync,
        timestamp = convergence?.timestamp,
        status = convergenceStatusRes(convergence),
        readPhase = readPhase,
        statusColumnWidth = statusColumnWidth,
        locale = locale,
        expanded = convergenceExpanded,
        onToggle = onToggleConvergence,
        statusColor = sheetStatusColor,
    ) { summary ->
        val color = colorPalette().textSecondary
        Column {
            PassSummaryLine(summary, color)
            for (line in convergenceDetailLines(convergence?.summary)) {
                val count = line.count
                Text(
                    text = if (count != null) stringResource(line.resId, count) else stringResource(line.resId),
                    style = typography().xs,
                    color = color,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = detailListIndent)
                        .padding(vertical = 2.dp),
                )
            }
        }
    }

    val dbCleanup = snapshot?.dbCleanup
    DatabasePassRow(
        titleResId = R.string.maintenance_db_cleanup,
        icon = R.drawable.data_migration,
        timestamp = dbCleanup?.timestamp,
        status = dbCleanupStatusRes(dbCleanup),
        readPhase = readPhase,
        statusColumnWidth = statusColumnWidth,
        locale = locale,
        expanded = dbCleanupExpanded,
        onToggle = onToggleDbCleanup,
        statusColor = sheetStatusColor,
    ) { summary ->
        GroupedPassDetails(dbCleanupLines(dbCleanup), summary) {
            val (resId, count) = dbCleanupEmptyRes(dbCleanup)
            if (count != null) maintenanceCount(resId, count) else stringResource(resId)
        }
    }

    val downloadRevalidation = snapshot?.downloadRevalidation
    DatabasePassRow(
        titleResId = R.string.maintenance_download_revalidation,
        icon = R.drawable.download,
        timestamp = downloadRevalidation?.timestamp,
        status = downloadRevalidationStatusRes(downloadRevalidation),
        readPhase = readPhase,
        statusColumnWidth = statusColumnWidth,
        locale = locale,
        expanded = downloadRevalidationExpanded,
        onToggle = onToggleDownloadRevalidation,
    ) { summary ->
        GroupedPassDetails(downloadRevalidationLines(downloadRevalidation), summary) {
            val (resId, count) = downloadRevalidationEmptyRes(downloadRevalidation)
            if (count != null) maintenanceCount(resId, count) else stringResource(resId)
        }
    }

    val downloadSelfHeal = snapshot?.downloadSelfHeal
    DatabasePassRow(
        titleResId = R.string.maintenance_download_self_heal,
        icon = R.drawable.refresh,
        timestamp = downloadSelfHeal?.timestamp,
        status = downloadSelfHealStatusRes(downloadSelfHeal),
        readPhase = readPhase,
        statusColumnWidth = statusColumnWidth,
        locale = locale,
        expanded = downloadSelfHealExpanded,
        onToggle = onToggleDownloadSelfHeal,
    ) { summary ->
        GroupedPassDetails(downloadSelfHealLines(downloadSelfHeal), summary) {
            stringResource(if (downloadSelfHeal == null) R.string.maintenance_no_data else R.string.maintenance_pass_clean)
        }
    }
}

@Composable
private fun ColumnScope.DatabasePassRow(
    @StringRes titleResId: Int,
    @DrawableRes icon: Int,
    timestamp: Long?,
    status: PassStatus,
    readPhase: MaintenanceReadPhase,
    statusColumnWidth: Dp,
    locale: Locale,
    expanded: Boolean,
    onToggle: () -> Unit,
    statusColor: Color? = null,
    details: @Composable (summary: String?) -> Unit,
) {
    val palette = colorPalette()
    val statusText = passStatusText(status)
    val lastRun = lastRunLine(timestamp, statusText, stringResource(R.string.maintenance_no_data), locale)
    ListMenu.Entry(
        text = stringResource(titleResId),
        icon = { SettingIcon(icon, palette.accent) },
        modifier = maintenanceExpansionSemantics(expanded),
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(lastRun, statusColor ?: palette.textSecondary, statusColumnWidth, readPhase)
                Icon(
                    painter = painterResource(if (expanded) R.drawable.chevron_up else R.drawable.chevron_down),
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        enabled = readPhase == MaintenanceReadPhase.Ready,
        onClick = onToggle,
    )
    AnimatedVisibility(
        visible = expanded,
        enter = settingsEntryEnter,
        exit = settingsEntryExit,
    ) {
        details(statusText.takeIf { passHasState(status) })
    }
}

@Composable
internal fun maintenanceExpansionSemantics(expanded: Boolean): Modifier {
    val state = stringResource(if (expanded) R.string.accessibility_menu_open else R.string.accessibility_menu_closed)
    return Modifier.semantics {
        role = Role.Button
        stateDescription = state
    }
}

@Composable
private fun GroupedPassDetails(
    lines: List<GroupLine>,
    summary: String?,
    emptyText: @Composable () -> String,
) {
    val palette = colorPalette()
    val style = typography().xs
    if (lines.isEmpty()) {
        Text(
            text = emptyText(),
            style = style,
            color = palette.textSecondary,
            modifier = Modifier.fillMaxWidth()
                .padding(start = detailListIndent)
                .padding(vertical = 6.dp),
        )
    } else {
        Column {
            PassSummaryLine(summary, palette.textSecondary)
            for (line in lines) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = detailListIndent)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = line.name,
                        style = style,
                        color = palette.text,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = line.status,
                        style = style,
                        color = palette.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PassSummaryLine(text: String?, color: Color) {
    if (text != null) {
        Text(
            text = text,
            style = typography().xs,
            color = color,
            modifier = Modifier.fillMaxWidth()
                .padding(start = detailListIndent)
                .padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun dedupGroupLines(state: MaintenanceDedupState?): List<GroupLine> {
    val groups = state?.records?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
    val lines = ArrayList<GroupLine>(groups.size)
    for (record in groups) {
        val display = dedupGroupDisplay(record)
        val detailArgs = display.detailArgs
        val detailArgs2 = display.detailArgs2
        val detailResId = display.detailResId
        val detail = when {
            detailResId == null -> ""
            detailArgs2 != null && detailArgs != null -> ": " + stringResource(
                R.string.maintenance_dedup_counts_format,
                maintenanceCount(R.string.maintenance_dedup_merged, detailArgs),
                maintenanceCount(R.string.maintenance_dedup_flagged, detailArgs2),
            )
            detailArgs != null -> ": ${maintenanceCount(detailResId, detailArgs)}"
            else -> ": ${stringResource(detailResId)}"
        }
        lines.add(GroupLine(cleanPrefix(record.name), stringResource(display.statusResId) + detail))
    }
    return lines
}
