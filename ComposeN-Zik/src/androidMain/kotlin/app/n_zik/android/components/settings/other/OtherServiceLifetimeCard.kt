package app.n_zik.android.components.settings.other

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
        title = stringResource(R.string.service_lifetime),
        icon = R.drawable.battery,
        content = {
            if (search.inputValue.isBlank() || stringResource(R.string.keep_screen_on).contains(search.inputValue, true) || stringResource(R.string.prevents_screen_timeout).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.keep_screen_on),
                    text = stringResource(R.string.prevents_screen_timeout),
                    isChecked = isKeepScreenOnEnabled,
                    onCheckedChange = { isKeepScreenOnEnabled = it },
                    icon = R.drawable.devices
                )
            }

            ImportantSettingsDescription(text = stringResource(R.string.battery_optimizations_applied))

            if (isAtLeastAndroid12) {
                SettingsDescription(text = stringResource(R.string.is_android12))
            }

            val msgNoBatteryOptim = stringResource(R.string.not_find_battery_optimization_settings)

            if (search.inputValue.isBlank() || stringResource(R.string.ignore_battery_optimizations).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    title = stringResource(R.string.ignore_battery_optimizations),
                    text = if (isIgnoringBatteryOptimizations) {
                        stringResource(R.string.already_unrestricted)
                    } else {
                        stringResource(R.string.disable_background_restrictions)
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
                                Toaster.i(R.string.battery_optimization_msg, formatArgs = arrayOf(msgNoBatteryOptim, BuildConfig.APP_NAME))
                            }
                        }
                    }
                )
            }
        }
    )
}
