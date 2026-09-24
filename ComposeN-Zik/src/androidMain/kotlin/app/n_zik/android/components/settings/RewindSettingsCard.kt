package app.n_zik.android.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.ui.screens.settings.OtherSwitchSettingEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.n_zik.android.R
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference
import timber.log.Timber

/**
 * Rewind settings cards of the AI tab, grouped like the Quick Picks sections (user
 * re-negotiation, 2026-09-24 passes 5-7): the master switch lives in the screen's
 * existing "General" card, then three grouped cards — "Rewind" (recap type toggles),
 * "Rewind notifications" (every notification toggle together) and "Rewind playlists"
 * (the two creation toggles, decoupled from the master switch per spec decision).
 *
 * Card visibility is delegated to [SettingsSectionCard]'s `visible` parameter, which
 * animates the card height to/from zero globally (expand/collapse + fade) so the other
 * cards slide naturally. Entry visibility inside the notifications card uses the shared
 * [settingsEntryEnter]/[settingsEntryExit] specs so the card shrinks smoothly when a
 * type is off.
 *
 * Every key is read live from [DataStoreUtils]: the master is flipped from the General
 * card and the same keys gate the Rewind home entries, the hamburger menu item and the
 * monthly and yearly reminder workers, so a flip takes effect without an app restart.
 *
 * The four playlist keys are the frozen contract spec 2 reads for its worker (creation
 * gate) and its notification (creation gate + notification gate). A notification entry
 * hides while its parent type is off (off type = off notification).
 */
@Composable
fun RewindSettingsCard() {
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

    // "Rewind" card: recap type toggles, only relevant while the feature is on
    SettingsSectionCard(
        title = stringResource(R.string.rw_settings_title),
        icon = R.drawable.sparkles,
        visible = rewindEnabled,
        content = {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.rw_settings_yearly),
                text = stringResource(R.string.rw_settings_yearly_description),
                isChecked = yearlyEnabled,
                onCheckedChange = {
                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, it)
                    Timber.tag("RewindReminder").i("Rewind yearly -> $it")
                },
                icon = R.drawable.trending
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.rw_settings_monthly),
                text = stringResource(R.string.rw_settings_monthly_description),
                isChecked = monthlyEnabled,
                onCheckedChange = {
                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, it)
                    Timber.tag("RewindReminder").i("Rewind monthly -> $it")
                },
                icon = R.drawable.calendar
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.rw_settings_global),
                text = stringResource(R.string.rw_settings_global_description),
                isChecked = globalEnabled,
                onCheckedChange = {
                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, it)
                    Timber.tag("RewindReminder").i("Rewind all-time -> $it")
                },
                icon = R.drawable.star_brilliant
            )
        }
    )

    // "Rewind notifications" card: every notification toggle together. Each entry hides
    // with its parent type (off type = off notification); the card hides only when every
    // parent it can show is off.
    SettingsSectionCard(
        title = stringResource(R.string.rw_settings_notifications),
        icon = R.drawable.notification2,
        visible = rewindEnabled || monthlyPlaylistEnabled || yearlyPlaylistEnabled,
        content = {
            AnimatedVisibility(visible = rewindEnabled && yearlyEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.rw_settings_yearly_notification),
                    text = stringResource(R.string.rw_settings_yearly_notification_description),
                    isChecked = yearlyNotifEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind yearly notification -> $it")
                    },
                    icon = R.drawable.notification2
                )
            }
            AnimatedVisibility(visible = rewindEnabled && monthlyEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.rw_settings_monthly_notification),
                    text = stringResource(R.string.rw_settings_monthly_notification_description),
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
                    title = stringResource(R.string.rw_settings_playlists_monthly_notification),
                    text = stringResource(R.string.rw_settings_playlists_monthly_notification_description),
                    isChecked = monthlyPlaylistNotifEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind monthly playlist notification -> $it")
                    },
                    icon = R.drawable.notification2
                )
            }
            AnimatedVisibility(visible = yearlyPlaylistEnabled, enter = settingsEntryEnter, exit = settingsEntryExit) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.rw_settings_playlists_yearly_notification),
                    text = stringResource(R.string.rw_settings_playlists_yearly_notification_description),
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

    // "Rewind playlists" card: decoupled from the master switch — always visible. Spec 2
    // wires these keys to the worker and its notification.
    SettingsSectionCard(
        title = stringResource(R.string.rw_settings_playlists_title),
        icon = R.drawable.calendar,
        content = {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.rw_settings_playlists_monthly),
                text = stringResource(R.string.rw_settings_playlists_monthly_description),
                isChecked = monthlyPlaylistEnabled,
                onCheckedChange = {
                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, it)
                    Timber.tag("RewindReminder").i("Rewind monthly playlist -> $it")
                },
                icon = R.drawable.calendar
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.rw_settings_playlists_yearly),
                text = stringResource(R.string.rw_settings_playlists_yearly_description),
                isChecked = yearlyPlaylistEnabled,
                onCheckedChange = {
                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, it)
                    Timber.tag("RewindReminder").i("Rewind yearly playlist -> $it")
                },
                icon = R.drawable.trending
            )
        }
    )
}
