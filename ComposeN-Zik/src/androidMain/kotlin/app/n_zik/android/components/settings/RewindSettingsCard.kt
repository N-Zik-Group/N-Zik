package app.n_zik.android.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.RewindMasterSettingsTarget
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsEntryEnter
import app.n_zik.android.components.settings.common.settingsEntryExit
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference
import timber.log.Timber

/** Rewind activation and dependent recap, playlist and notification controls. */
@Composable
fun RewindSettingsCard(onOpenRewind: (() -> Unit)? = null) {
    val context = LocalContext.current
    val rewindEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_ENABLED, true)
    val yearlyEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true)
    val monthlyEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true)
    val globalEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, true)
    val yearlyNotifEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, true)
    val monthlyNotifEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, true)
    val monthlyPlaylistEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)
    val yearlyPlaylistEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)
    val monthlyPlaylistNotifEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED, true)
    val yearlyPlaylistNotifEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_NOTIF_ENABLED, true)
    val bgmEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_BGM_ENABLED, true)

    Column {
        SettingsSectionCard(
            title = stringResource(R.string.rewind_settings_title),
            groupTitleRes = R.string.settings_group_rewind_summaries,
            icon = R.drawable.sparkles,
            content = {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget(RewindMasterSettingsTarget),
                    title = stringResource(R.string.rewind_settings_enabled),
                    text = stringResource(R.string.rewind_settings_enabled_description),
                    isChecked = rewindEnabled,
                    onCheckedChange = { DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, it) },
                    icon = R.drawable.sparkles,
                )
                if (!rewindEnabled) return@SettingsSectionCard

                // Optional deep link to the Rewind home screen (wired by the AI tab,
                // spec-customizable-burger-menu goal B).
                onOpenRewind?.let { onOpen ->
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("embedded.rewind.module_open"),
                        title = stringResource(R.string.modules_open),
                        text = stringResource(R.string.rewind_open_desc),
                        icon = R.drawable.chevron_forward,
                        onClick = onOpen
                    )
                }
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_monthly"),
                    title = stringResource(R.string.rewind_settings_monthly),
                    text = stringResource(R.string.rewind_settings_monthly_description),
                    isChecked = monthlyEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind monthly -> $it")
                    },
                    icon = R.drawable.calendar
                )

                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_yearly"),
                    title = stringResource(R.string.rewind_settings_yearly),
                    text = stringResource(R.string.rewind_settings_yearly_description),
                    isChecked = yearlyEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind yearly -> $it")
                    },
                    icon = R.drawable.trending
                )

                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_global"),
                    title = stringResource(R.string.rewind_settings_global),
                    text = stringResource(R.string.rewind_settings_global_description),
                    isChecked = globalEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind all-time -> $it")
                    },
                    icon = R.drawable.star_brilliant
                )
                // Deck background music (spec 3): the same key as the deck's mute button, a flip
                // here is live for a deck already open, and off means no player is created
                // when the deck opens.
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_bgmusic_settings"),
                    title = stringResource(R.string.rewind_background_music_title),
                    text = stringResource(R.string.settings_description_with_value, stringResource(R.string.rewind_background_music_description), stringResource(R.string.rewind_background_music_toggle_accessibility)),
                    isChecked = bgmEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_BGM_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind background music -> $it")
                    },
                    icon = R.drawable.volume_up
                )
            }
        )

        // "Rewind playlists" card: the two creation toggles, hidden with the section while
        // the master is off (user decision, 2026-10-25: master off stops every feature).
        SettingsSectionCard(
            title = stringResource(R.string.rewind_settings_playlists_title),
            groupTitleRes = R.string.settings_group_rewind_playlist_creation,
            icon = R.drawable.calendar,
            visible = rewindEnabled,
            content = {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_playlists_monthly"),
                    title = stringResource(R.string.rewind_settings_playlists_monthly),
                    text = stringResource(R.string.rewind_settings_playlists_monthly_description),
                    isChecked = monthlyPlaylistEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind monthly playlist -> $it")
                    },
                    icon = R.drawable.calendar
                )
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_playlists_yearly"),
                    title = stringResource(R.string.rewind_settings_playlists_yearly),
                    text = stringResource(R.string.rewind_settings_playlists_yearly_description),
                    isChecked = yearlyPlaylistEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind yearly playlist -> $it")
                    },
                    icon = R.drawable.trending
                )
            }
        )

        // "Rewind notifications" card: every notification toggle together. Each entry hides
        // with its parent type (off type = off notification).
        SettingsSectionCard(
            title = stringResource(R.string.rewind_settings_notifications),
            groupTitleRes = R.string.settings_group_rewind_alerts,
            icon = R.drawable.notification2,
            visible = rewindEnabled,
            content = {

                AnimatedVisibility(visible = rewindEnabled && monthlyEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_monthly_notification"),
                        title = stringResource(R.string.rewind_settings_monthly_notification),
                        text = stringResource(R.string.rewind_settings_monthly_notification_description),
                        isChecked = monthlyNotifEnabled,
                        onCheckedChange = {
                            DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, it)
                            Timber.tag("RewindReminder").i("Rewind monthly notification -> $it")
                        },
                        icon = R.drawable.notification2
                    )
                }

                AnimatedVisibility(visible = monthlyPlaylistEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_playlists_monthly_notification"),
                        title = stringResource(R.string.rewind_settings_playlists_monthly_notification),
                        text = stringResource(R.string.rewind_settings_playlists_monthly_notification_description),
                        isChecked = monthlyPlaylistNotifEnabled,
                        onCheckedChange = {
                            DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED, it)
                            Timber.tag("RewindReminder").i("Rewind monthly playlist notification -> $it")
                        },
                        icon = R.drawable.notification2
                    )
                }
                AnimatedVisibility(visible = rewindEnabled && yearlyEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_yearly_notification"),
                        title = stringResource(R.string.rewind_settings_yearly_notification),
                        text = stringResource(R.string.rewind_settings_yearly_notification_description),
                        isChecked = yearlyNotifEnabled,
                        onCheckedChange = {
                            DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, it)
                            Timber.tag("RewindReminder").i("Rewind yearly notification -> $it")
                        },
                        icon = R.drawable.notification2
                    )
                }

                AnimatedVisibility(visible = yearlyPlaylistEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("embedded.rewind.rw_settings_playlists_yearly_notification"),
                        title = stringResource(R.string.rewind_settings_playlists_yearly_notification),
                        text = stringResource(R.string.rewind_settings_playlists_yearly_notification_description),
                        isChecked = yearlyPlaylistNotifEnabled,
                        onCheckedChange = {
                            DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_NOTIF_ENABLED, it)
                            Timber.tag("RewindReminder").i("Rewind yearly playlist notification -> $it")
                        },
                        icon = R.drawable.notification2
                    )
                }
            }
        )
    }
}
