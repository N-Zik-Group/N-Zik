package app.n_zik.android.components.settings.other

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.isAtLeastAndroid12
import app.it.fast4x.rimusic.utils.isAtLeastAndroid6
import app.it.fast4x.rimusic.utils.isIgnoringBatteryOptimizations
import app.it.fast4x.rimusic.utils.isKeepScreenOnEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.ImportantSettingsDescription
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

@SuppressLint("BatteryLife")
@UnstableApi
@Composable
internal fun OtherServiceLifetimeCard(search: Search) {
    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations)
    }

    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations
        }

    var isKeepScreenOnEnabled by rememberPreference(isKeepScreenOnEnabledKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.settings_section_service_lifetime_title),
        icon = R.drawable.battery,
        content = {
            if (search.inputValue.isBlank() || stringResource(R.string.settings_service_keep_screen_on_label).contains(search.inputValue, true) || stringResource(R.string.settings_service_keep_screen_on_description).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("other.service_lifetime.keep_screen_on"),
                    title = stringResource(R.string.settings_service_keep_screen_on_label),
                    text = stringResource(R.string.settings_service_keep_screen_on_description),
                    isChecked = isKeepScreenOnEnabled,
                    onCheckedChange = { isKeepScreenOnEnabled = it },
                    icon = R.drawable.devices
                )
            }

            ImportantSettingsDescription(text = stringResource(R.string.settings_service_battery_optimizations_warning))

            if (isAtLeastAndroid12) {
                SettingsDescription(text = stringResource(R.string.settings_service_android_12_requirement_description))
            }

            val msgNoBatteryOptim = stringResource(R.string.settings_service_battery_settings_unavailable_message)

            if (search.inputValue.isBlank() || stringResource(R.string.settings_service_ignore_battery_optimizations_label).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("other.service_lifetime.ignore_battery_optimizations"),
                    title = stringResource(R.string.settings_service_ignore_battery_optimizations_label),
                    text = stringResource(R.string.settings_service_lifetime_battery_optimization_exemption_description),
                    selectedValueText = if (isIgnoringBatteryOptimizations) {
                        stringResource(R.string.settings_service_battery_unrestricted_status)
                    } else {
                        stringResource(R.string.settings_service_action_disable_background_restrictions)
                    },
                    icon = R.drawable.battery_opti,
                    onClick = {
                        if (!isAtLeastAndroid6) return@OtherSettingsEntry
                        try {
                            activityResultLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        } catch (e: ActivityNotFoundException) {
                            try {
                                activityResultLauncher.launch(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            } catch (e: ActivityNotFoundException) {
                                Toaster.i(R.string.settings_service_battery_status_message_format, formatArgs = arrayOf(msgNoBatteryOptim, BuildConfig.APP_NAME))
                            }
                        }
                    }
                )
            }
        }
    )
}
