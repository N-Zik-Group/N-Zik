package app.n_zik.android.core.backup.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.OtherSwitchSettingEntry
import app.it.fast4x.rimusic.ui.screens.settings.ImportantSettingsDescription
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.core.backup.BackupManager
import timber.log.Timber

@Composable
fun AutoBackupSettingsBlock() {
    val context = LocalContext.current

    var autoBackupInterval by rememberPreference(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_NONE)
    var autoBackupOnDbChange by rememberPreference(BackupManager.PREF_DB_CHANGE, false)
    var autoBackupUri by rememberPreference(BackupManager.PREF_URI, "")
    var autoBackupMaxBackups by rememberPreference(BackupManager.PREF_MAX_BACKUPS, 5)
    var autoBackupCustomIntervalValue by rememberPreference(BackupManager.PREF_CUSTOM_INTERVAL_VALUE, 12)
    var autoBackupCustomIntervalUnit by rememberPreference(BackupManager.PREF_CUSTOM_INTERVAL_UNIT, BackupManager.UNIT_HOURS)
    var autoBackupScheduleHour by rememberPreference(BackupManager.PREF_SCHEDULE_HOUR, 2)
    var autoBackupScheduleDayOfWeek by rememberPreference(BackupManager.PREF_SCHEDULE_DAY_OF_WEEK, 1)
    var autoBackupScheduleDayOfMonth by rememberPreference(BackupManager.PREF_SCHEDULE_DAY_OF_MONTH, 1)
    var autoBackupTarget by rememberPreference(BackupManager.PREF_TARGET, BackupManager.TARGET_DATABASE)
    var autoBackupIncludeYtb by rememberPreference(BackupManager.PREF_INCLUDE_YTB, false)
    var autoBackupIncludeDiscord by rememberPreference(BackupManager.PREF_INCLUDE_DISCORD, false)
    var autoBackupIncludeLastfm by rememberPreference(BackupManager.PREF_INCLUDE_LASTFM, false)
    var autoBackupIncludeProxy by rememberPreference(BackupManager.PREF_INCLUDE_PROXY, false)
    var autoBackupPreInstall by rememberPreference(BackupManager.PREF_PRE_INSTALL, false)

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showMaxBackupsDialog by remember { mutableStateOf(false) }
    var showCustomValueDialog by remember { mutableStateOf(false) }
    var showCustomUnitDialog by remember { mutableStateOf(false) }
    var showScheduleHourDialog by remember { mutableStateOf(false) }
    var showScheduleDayOfWeekDialog by remember { mutableStateOf(false) }
    var showScheduleDayOfMonthDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    autoBackupUri = uri.toString()
                    BackupManager.scheduleBackup(context, autoBackupInterval)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to take persistable URI permission")
                }
            }
        }
    )

    val decodedUri = runCatching { Uri.parse(autoBackupUri).lastPathSegment?.let { Uri.decode(it) } }.getOrNull() ?: autoBackupUri

    OtherSettingsEntry(
        title = stringResource(R.string.auto_backup_location),
        text = if (autoBackupUri.isEmpty()) stringResource(R.string.auto_backup_location_not_set) else decodedUri,
        icon = R.drawable.folder,
        onClick = { documentTreeLauncher.launch(null) }
    )

    if (autoBackupUri.isEmpty()) {
        ImportantSettingsDescription(text = stringResource(R.string.auto_backup_location_warning))
    }

    OtherSettingsEntry(
        title = stringResource(R.string.auto_backup_target),
        text = when (autoBackupTarget) {
            BackupManager.TARGET_DATABASE -> stringResource(R.string.database)
            BackupManager.TARGET_SETTINGS -> stringResource(R.string.settings)
            BackupManager.TARGET_BOTH -> stringResource(R.string.database_and_settings)
            // The profile-state target — labeled "Profiles" like everywhere else
            // (the import/export dialogs, the Profiles page).
            BackupManager.TARGET_ACCOUNTS -> stringResource(R.string.profiles)
            BackupManager.TARGET_ALL -> stringResource(R.string.export_all)
            else -> stringResource(R.string.database)
        },
        icon = R.drawable.server,
        onClick = { showTargetDialog = true }
    )

    AnimatedVisibility(visible = autoBackupTarget == BackupManager.TARGET_SETTINGS || autoBackupTarget == BackupManager.TARGET_BOTH || autoBackupTarget == BackupManager.TARGET_ALL) {
        Column {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.include_youtube_credentials),
                text = "",
                isChecked = autoBackupIncludeYtb,
                onCheckedChange = { autoBackupIncludeYtb = it },
                icon = R.drawable.logo_youtube,
                modifier = Modifier.padding(start = 24.dp)
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.include_discord_credentials),
                text = "",
                isChecked = autoBackupIncludeDiscord,
                onCheckedChange = { autoBackupIncludeDiscord = it },
                icon = R.drawable.logo_discord,
                modifier = Modifier.padding(start = 24.dp)
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.include_lastfm_credentials),
                text = "",
                isChecked = autoBackupIncludeLastfm,
                onCheckedChange = { autoBackupIncludeLastfm = it },
                icon = R.drawable.logo_lastfm,
                modifier = Modifier.padding(start = 24.dp)
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.include_proxy_credentials),
                text = "",
                isChecked = autoBackupIncludeProxy,
                onCheckedChange = { autoBackupIncludeProxy = it },
                icon = R.drawable.shield_checkmark,
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }

    OtherSettingsEntry(
        title = stringResource(R.string.auto_backup_max_backups),
        text = if (autoBackupMaxBackups == -1) stringResource(R.string.unlimited) else autoBackupMaxBackups.toString(),
        icon = R.drawable.server,
        onClick = { showMaxBackupsDialog = true }
    )

    OtherSwitchSettingEntry(
        title = stringResource(R.string.pre_install_backup),
        text = stringResource(R.string.pre_install_backup_description),
        isChecked = autoBackupPreInstall,
        onCheckedChange = {
            autoBackupPreInstall = it
        },
        icon = R.drawable.server
    )

    OtherSettingsEntry(
        title = stringResource(R.string.auto_backup_interval),
        text = when (autoBackupInterval) {
            BackupManager.INTERVAL_HOURLY -> stringResource(R.string.auto_backup_interval_hourly)
            BackupManager.INTERVAL_DAILY -> stringResource(R.string.auto_backup_interval_daily)
            BackupManager.INTERVAL_WEEKLY -> stringResource(R.string.auto_backup_interval_weekly)
            BackupManager.INTERVAL_MONTHLY -> stringResource(R.string.auto_backup_interval_monthly)
            BackupManager.INTERVAL_CUSTOM -> stringResource(R.string.auto_backup_interval_custom)
            else -> stringResource(R.string.auto_backup_interval_none)
        },
        icon = R.drawable.history,
        onClick = { showIntervalDialog = true }
    )

    if (showIntervalDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.auto_backup_interval),
            selectedValue = autoBackupInterval,
            values = listOf(
                BackupManager.INTERVAL_NONE,
                BackupManager.INTERVAL_HOURLY,
                BackupManager.INTERVAL_DAILY,
                BackupManager.INTERVAL_WEEKLY,
                BackupManager.INTERVAL_MONTHLY,
                BackupManager.INTERVAL_CUSTOM
            ),
            onValueSelected = {
                autoBackupInterval = it
                BackupManager.scheduleBackup(context, it)
                showIntervalDialog = false
            },
            valueText = {
                when (it) {
                    BackupManager.INTERVAL_HOURLY -> stringResource(R.string.auto_backup_interval_hourly)
                    BackupManager.INTERVAL_DAILY -> stringResource(R.string.auto_backup_interval_daily)
                    BackupManager.INTERVAL_WEEKLY -> stringResource(R.string.auto_backup_interval_weekly)
                    BackupManager.INTERVAL_MONTHLY -> stringResource(R.string.auto_backup_interval_monthly)
                    BackupManager.INTERVAL_CUSTOM -> stringResource(R.string.auto_backup_interval_custom)
                    else -> stringResource(R.string.auto_backup_interval_none)
                }
            },
            onDismiss = { showIntervalDialog = false }
        )
    }

    AnimatedVisibility(visible = autoBackupInterval == BackupManager.INTERVAL_CUSTOM) {
        Column {
            OtherSettingsEntry(
                title = stringResource(R.string.auto_backup_custom_interval_value),
                text = autoBackupCustomIntervalValue.toString(),
                icon = R.drawable.time,
                onClick = { showCustomValueDialog = true },
                modifier = Modifier.padding(start = 24.dp)
            )
            OtherSettingsEntry(
                title = stringResource(R.string.auto_backup_custom_interval_unit),
                text = when (autoBackupCustomIntervalUnit) {
                    BackupManager.UNIT_MINUTES -> stringResource(R.string.auto_backup_unit_minutes)
                    BackupManager.UNIT_HOURS -> stringResource(R.string.auto_backup_unit_hours)
                    BackupManager.UNIT_DAYS -> stringResource(R.string.auto_backup_unit_days)
                    BackupManager.UNIT_WEEKS -> stringResource(R.string.auto_backup_unit_weeks)
                    BackupManager.UNIT_MONTHS -> stringResource(R.string.auto_backup_unit_months)
                    else -> stringResource(R.string.auto_backup_unit_hours)
                },
                icon = R.drawable.calendar,
                onClick = { showCustomUnitDialog = true },
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }

    if (showCustomValueDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.auto_backup_custom_interval_value),
            selectedValue = autoBackupCustomIntervalValue,
            values = (1..60).toList(),
            onValueSelected = {
                autoBackupCustomIntervalValue = it
                BackupManager.scheduleBackup(context, autoBackupInterval)
                showCustomValueDialog = false
            },
            valueText = { it.toString() },
            onDismiss = { showCustomValueDialog = false }
        )
    }

    if (showCustomUnitDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.auto_backup_custom_interval_unit),
            selectedValue = autoBackupCustomIntervalUnit,
            values = listOf(
                BackupManager.UNIT_MINUTES,
                BackupManager.UNIT_HOURS,
                BackupManager.UNIT_DAYS,
                BackupManager.UNIT_WEEKS,
                BackupManager.UNIT_MONTHS
            ),
            onValueSelected = {
                autoBackupCustomIntervalUnit = it
                BackupManager.scheduleBackup(context, autoBackupInterval)
                showCustomUnitDialog = false
            },
            valueText = {
                when (it) {
                    BackupManager.UNIT_MINUTES -> stringResource(R.string.auto_backup_unit_minutes)
                    BackupManager.UNIT_HOURS -> stringResource(R.string.auto_backup_unit_hours)
                    BackupManager.UNIT_DAYS -> stringResource(R.string.auto_backup_unit_days)
                    BackupManager.UNIT_WEEKS -> stringResource(R.string.auto_backup_unit_weeks)
                    BackupManager.UNIT_MONTHS -> stringResource(R.string.auto_backup_unit_months)
                    else -> stringResource(R.string.auto_backup_unit_hours)
                }
            },
            onDismiss = { showCustomUnitDialog = false }
        )
    }

    AnimatedVisibility(visible = autoBackupInterval in listOf(BackupManager.INTERVAL_DAILY, BackupManager.INTERVAL_WEEKLY, BackupManager.INTERVAL_MONTHLY)) {
        Column {
            OtherSettingsEntry(
                title = stringResource(R.string.auto_backup_schedule_time),
                text = "${autoBackupScheduleHour.toString().padStart(2, '0')}:00",
                icon = R.drawable.time,
                onClick = { showScheduleHourDialog = true },
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }

    if (showScheduleHourDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.auto_backup_schedule_time),
            selectedValue = autoBackupScheduleHour,
            values = (0..23).toList(),
            onValueSelected = {
                autoBackupScheduleHour = it
                BackupManager.scheduleBackup(context, autoBackupInterval)
                showScheduleHourDialog = false
            },
            valueText = { "${it.toString().padStart(2, '0')}:00" },
            onDismiss = { showScheduleHourDialog = false }
        )
    }

    AnimatedVisibility(visible = autoBackupInterval == BackupManager.INTERVAL_WEEKLY) {
        Column {
            OtherSettingsEntry(
                title = stringResource(R.string.auto_backup_schedule_day_of_week),
                text = when (autoBackupScheduleDayOfWeek) {
                    1 -> "Monday"
                    2 -> "Tuesday"
                    3 -> "Wednesday"
                    4 -> "Thursday"
                    5 -> "Friday"
                    6 -> "Saturday"
                    7 -> "Sunday"
                    else -> "Monday"
                },
                icon = R.drawable.calendar,
                onClick = { showScheduleDayOfWeekDialog = true },
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }

    if (showScheduleDayOfWeekDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.auto_backup_schedule_day_of_week),
            selectedValue = autoBackupScheduleDayOfWeek,
            values = (1..7).toList(),
            onValueSelected = {
                autoBackupScheduleDayOfWeek = it
                BackupManager.scheduleBackup(context, autoBackupInterval)
                showScheduleDayOfWeekDialog = false
            },
            valueText = {
                when (it) {
                    1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"
                    5 -> "Friday"; 6 -> "Saturday"; 7 -> "Sunday"; else -> "Monday"
                }
            },
            onDismiss = { showScheduleDayOfWeekDialog = false }
        )
    }

    AnimatedVisibility(visible = autoBackupInterval == BackupManager.INTERVAL_MONTHLY) {
        Column {
            OtherSettingsEntry(
                title = stringResource(R.string.auto_backup_schedule_day_of_month),
                text = autoBackupScheduleDayOfMonth.toString(),
                icon = R.drawable.calendar_clear,
                onClick = { showScheduleDayOfMonthDialog = true },
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }

    if (showScheduleDayOfMonthDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.auto_backup_schedule_day_of_month),
            selectedValue = autoBackupScheduleDayOfMonth,
            values = (1..28).toList(),
            onValueSelected = {
                autoBackupScheduleDayOfMonth = it
                BackupManager.scheduleBackup(context, autoBackupInterval)
                showScheduleDayOfMonthDialog = false
            },
            valueText = { it.toString() },
            onDismiss = { showScheduleDayOfMonthDialog = false }
        )
    }

    AnimatedVisibility(visible = autoBackupInterval != BackupManager.INTERVAL_NONE) {
        Column {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.auto_backup_on_change),
                text = stringResource(R.string.auto_backup_on_change_description),
                isChecked = autoBackupOnDbChange,
                onCheckedChange = {
                    autoBackupOnDbChange = it
                },
                icon = R.drawable.sync
            )
        }
    }

    if (showTargetDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.auto_backup_target),
            selectedValue = autoBackupTarget,
            values = listOf(
                BackupManager.TARGET_DATABASE,
                BackupManager.TARGET_SETTINGS,
                BackupManager.TARGET_BOTH,
                BackupManager.TARGET_ACCOUNTS,
                BackupManager.TARGET_ALL
            ),
            onValueSelected = {
                autoBackupTarget = it
                showTargetDialog = false
            },
            valueText = {
                when (it) {
                    BackupManager.TARGET_DATABASE -> stringResource(R.string.database)
                    BackupManager.TARGET_SETTINGS -> stringResource(R.string.settings)
                    BackupManager.TARGET_BOTH -> stringResource(R.string.database_and_settings)
                    // The profile-state target — labeled "Profiles" like everywhere else
            // (the import/export dialogs, the Profiles page).
            BackupManager.TARGET_ACCOUNTS -> stringResource(R.string.profiles)
                    BackupManager.TARGET_ALL -> stringResource(R.string.export_all)
                    else -> stringResource(R.string.database)
                }
            },
            onDismiss = { showTargetDialog = false }
        )
    }

    if (showMaxBackupsDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.auto_backup_max_backups),
            selectedValue = autoBackupMaxBackups,
            values = listOf(1, 3, 5, 10, 20, -1),
            onValueSelected = {
                autoBackupMaxBackups = it
                showMaxBackupsDialog = false
            },
            valueText = { if (it == -1) stringResource(R.string.unlimited) else it.toString() },
            onDismiss = { showMaxBackupsDialog = false }
        )
    }
}
