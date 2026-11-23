package app.n_zik.android.components.ui.screens.rescue

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

import app.n_zik.android.R

@Composable
internal fun RescueGlobalProfilesSection(
    hasProfileState: Boolean,
    hasDeletableProfiles: Boolean,
    hasProfileBackups: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    onRestore: () -> Unit,
) {
    RescueSectionCard(
        title = stringResource(R.string.recovery_group_profiles),
        iconRes = R.drawable.person,
        content = {
            RescueActionCard(
                iconRes = R.drawable.person,
                title = stringResource(R.string.profile_action_export),
                description = stringResource(R.string.recovery_export_profiles_description),
                enabled = hasProfileState,
                disabledReason = stringResource(R.string.recovery_no_profile_state),
                onClick = onExport
            )

            RescueActionCard(
                iconRes = R.drawable.person,
                title = stringResource(R.string.profile_action_import),
                description = stringResource(R.string.recovery_import_profiles_description),
                onClick = onImport
            )

            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.recovery_delete_profiles),
                description = stringResource(R.string.recovery_delete_profiles_description),
                enabled = hasDeletableProfiles,
                disabledReason = stringResource(R.string.recovery_no_profiles_to_delete),
                onClick = onDelete
            )

            RescueActionCard(
                iconRes = R.drawable.person,
                title = stringResource(R.string.recovery_reset_profiles),
                description = stringResource(R.string.recovery_reset_profiles_description),
                onClick = onReset
            )

            RescueActionCard(
                iconRes = R.drawable.restore,
                title = stringResource(R.string.recovery_restore_profiles),
                description = stringResource(R.string.recovery_restore_profiles_description),
                enabled = hasProfileBackups,
                disabledReason = stringResource(R.string.recovery_no_profiles_to_restore),
                onClick = onRestore
            )
        }
    )
}

@Composable
internal fun RescueLogsSection(
    hasLogs: Boolean,
    hasDeletableLogs: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    RescueSectionCard(
        title = stringResource(R.string.recovery_group_logs),
        iconRes = R.drawable.bugs,
        content = {
            RescueActionCard(
                iconRes = R.drawable.bugs,
                title = stringResource(R.string.recovery_export_logs),
                description = stringResource(R.string.recovery_export_logs_description),
                enabled = hasLogs,
                disabledReason = stringResource(R.string.recovery_no_logs),
                onClick = onExport
            )

            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.recovery_delete_logs),
                description = stringResource(R.string.recovery_delete_logs_description),
                enabled = hasDeletableLogs,
                disabledReason = stringResource(R.string.recovery_no_logs),
                onClick = onDelete
            )
        }
    )
}

@Composable
internal fun RescueStorageSection(
    hasBackups: Boolean,
    onClearCache: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onDeleteBackups: () -> Unit,
) {
    RescueSectionCard(
        title = stringResource(R.string.recovery_group_storage),
        iconRes = R.drawable.trash,
        content = {
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.recovery_clear_cache),
                description = stringResource(R.string.recovery_clear_cache_description),
                onClick = onClearCache
            )

            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.recovery_delete_downloads),
                description = stringResource(R.string.recovery_delete_downloads_description),
                onClick = onDeleteDownloads
            )

            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.recovery_delete_backups),
                description = stringResource(R.string.recovery_delete_backups_description),
                enabled = hasBackups,
                disabledReason = stringResource(R.string.recovery_no_backups_to_delete),
                onClick = onDeleteBackups
            )
        }
    )
}

@Composable
internal fun RescueMainAppSection(
    canKillApp: Boolean,
    onKillApp: () -> Unit,
) {
    RescueSectionCard(
        title = stringResource(R.string.recovery_group_app),
        iconRes = R.drawable.logout,
        content = {

            RescueActionCard(
                iconRes = R.drawable.logout,
                title = stringResource(R.string.recovery_kill_app),
                description = stringResource(R.string.recovery_kill_app_description),
                enabled = canKillApp,
                onClick = onKillApp
            )
        }
    )
}
