package app.n_zik.android.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.settings.OtherSwitchSettingEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.n_zik.android.R
import app.n_zik.android.utils.DataStoreUtils
import timber.log.Timber

/**
 * Rewind settings card in the AI tab (spec GH-275, roadmap item 1): a master switch plus one
 * toggle per recap type (yearly / monthly / all-time) and one notification toggle per type
 * that has a worker (monthly now; yearly stays inert until the yearly worker lands).
 *
 * The same [DataStoreUtils] keys gate the Rewind home entries, the hamburger menu item and
 * the monthly reminder worker, so a flip here takes effect without an app restart: the home
 * and the worker re-read the keys, and the card writes every toggle immediately.
 *
 * Visual hierarchy (device feedback, 2026-09-24): the type toggles stay aligned with the
 * master, and only the notification toggles are indented under their recap type (25.dp) —
 * both notification toggles share the same bell icon (house pattern of the "Player
 * notification" section).
 */
@Composable
fun RewindSettingsCard() {
    val context = LocalContext.current
    var rewindEnabled by remember {
        mutableStateOf(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true))
    }
    var yearlyEnabled by remember {
        mutableStateOf(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true))
    }
    var monthlyEnabled by remember {
        mutableStateOf(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true))
    }
    var globalEnabled by remember {
        mutableStateOf(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, true))
    }
    var monthlyNotifEnabled by remember {
        mutableStateOf(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, true))
    }
    var yearlyNotifEnabled by remember {
        mutableStateOf(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, true))
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(600)) + scaleIn(
            animationSpec = tween(600),
            initialScale = 0.9f
        )
    ) {
        SettingsSectionCard(
            title = stringResource(R.string.rw_settings_title),
            icon = R.drawable.sparkles,
            content = {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.rw_settings_master),
                    text = stringResource(R.string.rw_settings_master_description),
                    isChecked = rewindEnabled,
                    onCheckedChange = {
                        rewindEnabled = it
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, it)
                        Timber.tag("RewindReminder").i("Rewind master -> $it")
                    },
                    icon = R.drawable.sparkles
                )

                // The type toggles are only relevant while the feature is on
                AnimatedVisibility(visible = rewindEnabled) {
                    Column {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.rw_settings_yearly),
                            text = stringResource(R.string.rw_settings_yearly_description),
                            isChecked = yearlyEnabled,
                            onCheckedChange = {
                                yearlyEnabled = it
                                DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, it)
                                Timber.tag("RewindReminder").i("Rewind yearly -> $it")
                            },
                            icon = R.drawable.trending
                        )

                        // Notification toggle nested under its type: off type = off notification
                        AnimatedVisibility(visible = yearlyEnabled) {
                            OtherSwitchSettingEntry(
                                title = stringResource(R.string.rw_settings_yearly_notification),
                                text = stringResource(R.string.rw_settings_yearly_notification_description),
                                isChecked = yearlyNotifEnabled,
                                onCheckedChange = {
                                    yearlyNotifEnabled = it
                                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, it)
                                    Timber.tag("RewindReminder").i("Rewind yearly notification -> $it")
                                },
                                icon = R.drawable.notification2,
                                modifier = Modifier.padding(start = 25.dp)
                            )
                        }

                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.rw_settings_monthly),
                            text = stringResource(R.string.rw_settings_monthly_description),
                            isChecked = monthlyEnabled,
                            onCheckedChange = {
                                monthlyEnabled = it
                                DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, it)
                                Timber.tag("RewindReminder").i("Rewind monthly -> $it")
                            },
                            icon = R.drawable.calendar
                        )

                        // Notification toggle nested under its type: off type = off notification
                        AnimatedVisibility(visible = monthlyEnabled) {
                            OtherSwitchSettingEntry(
                                title = stringResource(R.string.rw_settings_monthly_notification),
                                text = stringResource(R.string.rw_settings_monthly_notification_description),
                                isChecked = monthlyNotifEnabled,
                                onCheckedChange = {
                                    monthlyNotifEnabled = it
                                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, it)
                                    Timber.tag("RewindReminder").i("Rewind monthly notification -> $it")
                                },
                                icon = R.drawable.notification2,
                                modifier = Modifier.padding(start = 25.dp)
                            )
                        }

                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.rw_settings_global),
                            text = stringResource(R.string.rw_settings_global_description),
                            isChecked = globalEnabled,
                            onCheckedChange = {
                                globalEnabled = it
                                DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, it)
                                Timber.tag("RewindReminder").i("Rewind all-time -> $it")
                            },
                            icon = R.drawable.star_brilliant
                        )
                    }
                }
            }
        )
    }
}
