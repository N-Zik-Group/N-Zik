package app.n_zik.android.core.backup.ui

import app.n_zik.android.components.dialog.backup.BackupContent
import app.n_zik.android.components.dialog.backup.BackupContentOption
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.core.backup.autoBackupSelection
import app.n_zik.android.core.backup.encodeAutoBackupSelection
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.ImportantSettingsDescription
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.profiles.profileDataPrefs
import java.time.DayOfWeek
import java.time.format.TextStyle
import timber.log.Timber

@Composable
internal fun AutoBackupSettingsBlock(target: ProfileStorageIdentity, pageEntryTargets: Set<String>) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val navigationTarget = LocalSettingsTargets.current?.storageEntryTarget
    val searchTargets = (pageEntryTargets + listOfNotNull(navigationTarget))
        .filter { it.startsWith("embedded.auto_backup.") }
        .map { it.substringAfterLast('.') }

    val legacyEnabled = remember(context, target) { BackupManager.isEnabled(profileDataPrefs(context, target.id)) }
    var autoBackupEnabled by rememberBackupPreference(context, target, BackupManager.PREF_ENABLED, legacyEnabled)
    var autoBackupInterval by rememberBackupPreference(context, target, BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_NONE)
    var autoBackupOnDbChange by rememberBackupPreference(context, target, BackupManager.PREF_DB_CHANGE, false)
    var autoBackupUri by rememberBackupPreference(context, target, BackupManager.PREF_URI, "")
    var autoBackupMaxBackups by rememberBackupPreference(context, target, BackupManager.PREF_MAX_BACKUPS, 5)
    var autoBackupCustomIntervalValue by rememberBackupPreference(context, target, BackupManager.PREF_CUSTOM_INTERVAL_VALUE, 12)
    var autoBackupCustomIntervalUnit by rememberBackupPreference(context, target, BackupManager.PREF_CUSTOM_INTERVAL_UNIT, BackupManager.UNIT_HOURS)
    var autoBackupScheduleHour by rememberBackupPreference(context, target, BackupManager.PREF_SCHEDULE_HOUR, 2)
    var autoBackupScheduleDayOfWeek by rememberBackupPreference(context, target, BackupManager.PREF_SCHEDULE_DAY_OF_WEEK, 1)
    var autoBackupScheduleDayOfMonth by rememberBackupPreference(context, target, BackupManager.PREF_SCHEDULE_DAY_OF_MONTH, 1)
    var autoBackupTarget by rememberBackupPreference(context, target, BackupManager.PREF_TARGET, BackupManager.TARGET_DATABASE)
    var autoBackupIncludeYtb by rememberBackupPreference(context, target, BackupManager.PREF_INCLUDE_YTB, false)
    var autoBackupIncludeDiscord by rememberBackupPreference(context, target, BackupManager.PREF_INCLUDE_DISCORD, false)
    var autoBackupIncludeLastfm by rememberBackupPreference(context, target, BackupManager.PREF_INCLUDE_LASTFM, false)
    var autoBackupIncludeGitHub by rememberBackupPreference(context, target, BackupManager.PREF_INCLUDE_GITHUB, false)
    var autoBackupIncludeProxy by rememberBackupPreference(context, target, BackupManager.PREF_INCLUDE_PROXY, false)
    var autoBackupPreInstall by rememberBackupPreference(context, target, BackupManager.PREF_PRE_INSTALL, false)

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
            if (uri != null && ProfileWorkOwner.exists(context, target)) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    autoBackupUri = uri.toString()
                    BackupManager.scheduleBackup(context, autoBackupInterval, target)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to take persistable URI permission")
                }
            }
        }
    )

    val decodedUri = runCatching { Uri.parse(autoBackupUri).lastPathSegment?.let { Uri.decode(it) } }.getOrNull() ?: autoBackupUri

    val toggleModifier = if (autoBackupEnabled) {
        Modifier.settingsSearchTarget("embedded.auto_backup.enabled")
    } else {
        (pageEntryTargets + listOfNotNull(navigationTarget)).filter {
            it.startsWith("embedded.auto_backup.") && it != "embedded.auto_backup.enabled"
        }.fold(Modifier.settingsSearchTarget("embedded.auto_backup.enabled")) { modifier, id ->
            modifier.settingsSearchTarget(id)
        }
    }
    OtherSwitchSettingEntry(
        modifier = toggleModifier,
        title = stringResource(R.string.backup_auto_enabled_title),
        text = stringResource(R.string.backup_auto_enabled_description),
        icon = R.drawable.backup,
        isChecked = autoBackupEnabled,
        onCheckedChange = {
            autoBackupEnabled = it
            BackupManager.scheduleBackup(context, autoBackupInterval, target)
        },
    )

    AnimatedVisibility(visible = autoBackupEnabled) {
        Column {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_location"),
                title = stringResource(R.string.backup_auto_location),
                text = stringResource(R.string.settings_description_with_value, stringResource(R.string.backup_auto_location_description), if (autoBackupUri.isEmpty()) stringResource(R.string.backup_auto_location_not_set) else decodedUri),
                icon = R.drawable.folder,
                onClick = { documentTreeLauncher.launch(null) }
            )

            if (autoBackupUri.isEmpty()) {
                ImportantSettingsDescription(text = stringResource(R.string.backup_auto_location_warning))
            }

            val targetSelection = autoBackupSelection(autoBackupTarget)
            val targetNames = BackupContent.entries.filter { it.isSelected(targetSelection) }
                .map { stringResource(it.labelRes) }
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_target"),
                title = stringResource(R.string.backup_auto_target),
                text = stringResource(R.string.settings_description_with_value, stringResource(R.string.backup_auto_content_format_description), targetNames.joinToString(", ")),
                icon = R.drawable.server,
                onClick = { showTargetDialog = true }
            )

            AnimatedVisibility(visible = BackupContent.Settings.isSelected(targetSelection) ||
                searchTargets.any { it.startsWith("include_") }) {
                Column {
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.backup_include_youtube_credentials),
                        text = stringResource(R.string.backup_auto_include_youtube_credentials_description),
                        isChecked = autoBackupIncludeYtb,
                        onCheckedChange = { autoBackupIncludeYtb = it },
                        icon = R.drawable.logo_youtube,
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.include_youtube_credentials").padding(start = 24.dp)
                    )
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.backup_include_discord_credentials),
                        text = stringResource(R.string.backup_auto_include_discord_credentials_description),
                        isChecked = autoBackupIncludeDiscord,
                        onCheckedChange = { autoBackupIncludeDiscord = it },
                        icon = R.drawable.logo_discord,
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.include_discord_credentials").padding(start = 24.dp)
                    )
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.backup_include_lastfm_credentials),
                        text = stringResource(R.string.backup_auto_include_lastfm_credentials_description),
                        isChecked = autoBackupIncludeLastfm,
                        onCheckedChange = { autoBackupIncludeLastfm = it },
                        icon = R.drawable.logo_lastfm,
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.include_lastfm_credentials").padding(start = 24.dp)
                    )
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.backup_include_github_identity_credentials),
                        text = "",
                        isChecked = autoBackupIncludeGitHub,
                        onCheckedChange = { autoBackupIncludeGitHub = it },
                        icon = R.drawable.github_icon,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.backup_include_proxy_password),
                        text = stringResource(R.string.backup_auto_include_proxy_credentials_description),
                        isChecked = autoBackupIncludeProxy,
                        onCheckedChange = { autoBackupIncludeProxy = it },
                        icon = R.drawable.shield_checkmark,
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.include_proxy_credentials").padding(start = 24.dp)
                    )
                }
            }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_max_backups"),
                title = stringResource(R.string.backup_auto_max_backups),
                text = stringResource(R.string.settings_description_with_value, stringResource(R.string.backup_auto_maximum_backups_description), if (autoBackupMaxBackups == -1) stringResource(R.string.common_limit_unlimited_label) else autoBackupMaxBackups.toString()),
                icon = R.drawable.server,
                onClick = { showMaxBackupsDialog = true }
            )

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("embedded.auto_backup.pre_install_backup"),
                title = stringResource(R.string.settings_backup_before_install_label),
                text = stringResource(R.string.backup_before_update_description),
                isChecked = autoBackupPreInstall,
                onCheckedChange = {
                    autoBackupPreInstall = it
                },
                icon = R.drawable.server
            )

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_interval"),
                title = stringResource(R.string.backup_auto_interval),
                text = stringResource(R.string.settings_description_with_value, stringResource(R.string.backup_auto_interval_description), when (autoBackupInterval) {
                    BackupManager.INTERVAL_HOURLY -> stringResource(R.string.backup_auto_interval_hourly)
                    BackupManager.INTERVAL_DAILY -> stringResource(R.string.backup_auto_interval_daily)
                    BackupManager.INTERVAL_WEEKLY -> stringResource(R.string.backup_auto_interval_weekly)
                    BackupManager.INTERVAL_MONTHLY -> stringResource(R.string.backup_auto_interval_monthly)
                    BackupManager.INTERVAL_CUSTOM -> stringResource(R.string.backup_auto_interval_custom)
                    else -> stringResource(R.string.backup_auto_interval_none)
                }),
                icon = R.drawable.history,
                onClick = { showIntervalDialog = true }
            )

            DialogPresence(visible = showIntervalDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.backup_auto_interval),
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
                        BackupManager.scheduleBackup(context, it, target)
                        showIntervalDialog = false
                    },
                    valueText = {
                        when (it) {
                            BackupManager.INTERVAL_HOURLY -> stringResource(R.string.backup_auto_interval_hourly)
                            BackupManager.INTERVAL_DAILY -> stringResource(R.string.backup_auto_interval_daily)
                            BackupManager.INTERVAL_WEEKLY -> stringResource(R.string.backup_auto_interval_weekly)
                            BackupManager.INTERVAL_MONTHLY -> stringResource(R.string.backup_auto_interval_monthly)
                            BackupManager.INTERVAL_CUSTOM -> stringResource(R.string.backup_auto_interval_custom)
                            else -> stringResource(R.string.backup_auto_interval_none)
                        }
                    },
                    onDismiss = { showIntervalDialog = false }
                )
            }

            AnimatedVisibility(visible = autoBackupInterval == BackupManager.INTERVAL_CUSTOM ||
                searchTargets.any { it.startsWith("auto_backup_custom_interval_") }) {
                Column {
                    OtherSettingsEntry(
                        title = stringResource(R.string.backup_auto_custom_interval_value),
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.backup_auto_custom_interval_value_description), autoBackupCustomIntervalValue.toString()),
                        icon = R.drawable.time,
                        onClick = { showCustomValueDialog = true },
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_custom_interval_value").padding(start = 24.dp)
                    )
                    OtherSettingsEntry(
                        title = stringResource(R.string.backup_auto_custom_interval_unit),
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.backup_auto_custom_interval_unit_description), when (autoBackupCustomIntervalUnit) {
                            BackupManager.UNIT_MINUTES -> stringResource(R.string.backup_auto_unit_minutes)
                            BackupManager.UNIT_HOURS -> stringResource(R.string.backup_auto_unit_hours)
                            BackupManager.UNIT_DAYS -> stringResource(R.string.backup_auto_unit_days)
                            BackupManager.UNIT_WEEKS -> stringResource(R.string.backup_auto_unit_weeks)
                            BackupManager.UNIT_MONTHS -> stringResource(R.string.backup_auto_unit_months)
                            else -> stringResource(R.string.backup_auto_unit_hours)
                        }),
                        icon = R.drawable.calendar,
                        onClick = { showCustomUnitDialog = true },
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_custom_interval_unit").padding(start = 24.dp)
                    )
                }
            }

            DialogPresence(visible = showCustomValueDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.backup_auto_custom_interval_value),
                    selectedValue = autoBackupCustomIntervalValue,
                    values = (1..60).toList(),
                    onValueSelected = {
                        autoBackupCustomIntervalValue = it
                        BackupManager.scheduleBackup(context, autoBackupInterval, target)
                        showCustomValueDialog = false
                    },
                    valueText = { it.toString() },
                    onDismiss = { showCustomValueDialog = false }
                )
            }

            DialogPresence(visible = showCustomUnitDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.backup_auto_custom_interval_unit),
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
                        BackupManager.scheduleBackup(context, autoBackupInterval, target)
                        showCustomUnitDialog = false
                    },
                    valueText = {
                        when (it) {
                            BackupManager.UNIT_MINUTES -> stringResource(R.string.backup_auto_unit_minutes)
                            BackupManager.UNIT_HOURS -> stringResource(R.string.backup_auto_unit_hours)
                            BackupManager.UNIT_DAYS -> stringResource(R.string.backup_auto_unit_days)
                            BackupManager.UNIT_WEEKS -> stringResource(R.string.backup_auto_unit_weeks)
                            BackupManager.UNIT_MONTHS -> stringResource(R.string.backup_auto_unit_months)
                            else -> stringResource(R.string.backup_auto_unit_hours)
                        }
                    },
                    onDismiss = { showCustomUnitDialog = false }
                )
            }

            AnimatedVisibility(visible = autoBackupInterval in listOf(BackupManager.INTERVAL_DAILY, BackupManager.INTERVAL_WEEKLY, BackupManager.INTERVAL_MONTHLY) ||
                "auto_backup_schedule_time" in searchTargets) {
                Column {
                    OtherSettingsEntry(
                        title = stringResource(R.string.backup_auto_schedule_time),
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.backup_auto_schedule_time_description), "${autoBackupScheduleHour.toString().padStart(2, '0')}:00"),
                        icon = R.drawable.time,
                        onClick = { showScheduleHourDialog = true },
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_schedule_time").padding(start = 24.dp)
                    )
                }
            }

            DialogPresence(visible = showScheduleHourDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.backup_auto_schedule_time),
                    selectedValue = autoBackupScheduleHour,
                    values = (0..23).toList(),
                    onValueSelected = {
                        autoBackupScheduleHour = it
                        BackupManager.scheduleBackup(context, autoBackupInterval, target)
                        showScheduleHourDialog = false
                    },
                    valueText = { "${it.toString().padStart(2, '0')}:00" },
                    onDismiss = { showScheduleHourDialog = false }
                )
            }

            AnimatedVisibility(visible = autoBackupInterval == BackupManager.INTERVAL_WEEKLY ||
                "auto_backup_schedule_day_of_week" in searchTargets) {
                Column {
                    OtherSettingsEntry(
                        title = stringResource(R.string.backup_auto_schedule_day_of_week),
                        text = stringResource(
                            R.string.settings_description_with_value,
                            stringResource(R.string.backup_auto_schedule_day_of_week_description),
                            DayOfWeek.of(autoBackupScheduleDayOfWeek.takeIf { it in 1..7 } ?: 1).getDisplayName(TextStyle.FULL_STANDALONE, locale),
                        ),
                        icon = R.drawable.calendar,
                        onClick = { showScheduleDayOfWeekDialog = true },
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_schedule_day_of_week").padding(start = 24.dp)
                    )
                }
            }

            DialogPresence(visible = showScheduleDayOfWeekDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.backup_auto_schedule_day_of_week),
                    selectedValue = autoBackupScheduleDayOfWeek,
                    values = (1..7).toList(),
                    onValueSelected = {
                        autoBackupScheduleDayOfWeek = it
                        BackupManager.scheduleBackup(context, autoBackupInterval, target)
                        showScheduleDayOfWeekDialog = false
                    },
                    valueText = { DayOfWeek.of(it).getDisplayName(TextStyle.FULL_STANDALONE, locale) },
                    onDismiss = { showScheduleDayOfWeekDialog = false }
                )
            }

            AnimatedVisibility(visible = autoBackupInterval == BackupManager.INTERVAL_MONTHLY ||
                "auto_backup_schedule_day_of_month" in searchTargets) {
                Column {
                    OtherSettingsEntry(
                        title = stringResource(R.string.backup_auto_schedule_day_of_month),
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.backup_auto_schedule_day_of_month_description), autoBackupScheduleDayOfMonth.toString()),
                        icon = R.drawable.calendar_clear,
                        onClick = { showScheduleDayOfMonthDialog = true },
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_schedule_day_of_month").padding(start = 24.dp)
                    )
                }
            }

            DialogPresence(visible = showScheduleDayOfMonthDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.backup_auto_schedule_day_of_month),
                    selectedValue = autoBackupScheduleDayOfMonth,
                    values = (1..28).toList(),
                    onValueSelected = {
                        autoBackupScheduleDayOfMonth = it
                        BackupManager.scheduleBackup(context, autoBackupInterval, target)
                        showScheduleDayOfMonthDialog = false
                    },
                    valueText = { it.toString() },
                    onDismiss = { showScheduleDayOfMonthDialog = false }
                )
            }

            AnimatedVisibility(visible = autoBackupInterval != BackupManager.INTERVAL_NONE ||
                "auto_backup_on_change" in searchTargets) {
                Column {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("embedded.auto_backup.auto_backup_on_change"),
                        title = stringResource(R.string.backup_auto_on_change),
                        text = stringResource(R.string.backup_auto_on_change_description),
                        isChecked = autoBackupOnDbChange,
                        onCheckedChange = {
                            autoBackupOnDbChange = it
                        },
                        icon = R.drawable.sync
                    )
                }
            }

            DialogPresence(visible = showTargetDialog) {
                var selection by remember(targetSelection) { mutableStateOf(targetSelection) }
                DialogFrame(
                    title = stringResource(R.string.backup_auto_target),
                    onDismiss = { showTargetDialog = false },
                    footer = {
                        ButtonRow(listOf(
                            DialogButton(stringResource(R.string.common_action_cancel), { showTargetDialog = false }, danger = true),
                            DialogButton(
                                text = stringResource(R.string.common_action_save),
                                enabled = selection != 0,
                                primary = true,
                                onClick = {
                                    autoBackupTarget = encodeAutoBackupSelection(selection)
                                    showTargetDialog = false
                                },
                            ),
                        ))
                    },
                ) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        BackupContent.entries.forEach { option ->
                            BackupContentOption(option.labelRes, option.exportDescriptionRes, option.isSelected(selection)) {
                                selection = selection xor option.flag
                            }
                        }
                        BackupContentOption(
                            R.string.backup_export_all, R.string.backup_export_select_all_description,
                            selected = selection == BackupContent.all,
                        ) {
                            selection = if (selection == BackupContent.all) 0 else BackupContent.all
                        }
                    }
                }
            }

            DialogPresence(visible = showMaxBackupsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.backup_auto_max_backups),
                    selectedValue = autoBackupMaxBackups,
                    values = listOf(1, 3, 5, 10, 20, -1),
                    onValueSelected = {
                        autoBackupMaxBackups = it
                        showMaxBackupsDialog = false
                    },
                    valueText = { if (it == -1) stringResource(R.string.common_limit_unlimited_label) else it.toString() },
                    onDismiss = { showMaxBackupsDialog = false }
                )
            }
        }
    }
}

@Composable
private fun <T : Any> rememberBackupPreference(
    context: Context,
    target: ProfileStorageIdentity,
    key: String,
    default: T,
): MutableState<T> = remember(context, target, key) {
    val preferences = profileDataPrefs(context, target.id)
    @Suppress("UNCHECKED_CAST")
    val initial = (preferences.all[key] ?: default) as T
    val state = mutableStateOf(initial)
    object : MutableState<T> by state {
        override var value: T
            get() = state.value
            set(value) {
                if (!ProfileWorkOwner.exists(context, target)) return
                val editor = preferences.edit()
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                }
                editor.apply()
                state.value = value
            }

        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }
}
