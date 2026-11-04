package app.n_zik.android.components.settings.general

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.compose.persist.findActivityNullable
import app.it.fast4x.rimusic.enums.PipModule
import app.it.fast4x.rimusic.utils.enablePictureInPictureAutoKey
import app.it.fast4x.rimusic.utils.enablePictureInPictureKey
import app.it.fast4x.rimusic.utils.isAtLeastAndroid12
import app.it.fast4x.rimusic.utils.pipModuleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.RestartActivity
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget
import timber.log.Timber

/**
 * Picture in Picture card. [onCardTopYChanged] reports the card's root Y so the
 * tab can auto-scroll here when opened with focus=pip (burger long press).
 */
@Composable
fun GeneralPipCard(
    visible: Boolean,
    restartActivity: Boolean,
    onRestartActivitySet: () -> Unit,
    onRestartActivityClear: () -> Unit,
    onCardTopYChanged: (Float) -> Unit,
) {
    var enablePictureInPicture by rememberPreference(enablePictureInPictureKey, false)
    var enablePictureInPictureAuto by rememberPreference(enablePictureInPictureAutoKey, false)
    var pipModule by rememberPreference(pipModuleKey, PipModule.Cover)

    var showPipModuleDialog by remember { mutableStateOf(false) }

    // The burger long press (focus=pip) scrolls to this card.
    SettingsSectionCard(
        title = stringResource(R.string.settings_picture_in_picture_title),
        icon = R.drawable.video,
        modifier = Modifier.onGloballyPositioned { onCardTopYChanged(it.positionInRoot().y) },
        visible = visible,
        content = {
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.picture_in_picture.settings_enable_pip"),
                title = stringResource(R.string.settings_enable_pip),
                text = stringResource(R.string.player_picture_in_picture_enabled_description),
                isChecked = enablePictureInPicture,
                onCheckedChange = {
                    enablePictureInPicture = it
                    onRestartActivitySet()
                },
                icon = R.drawable.logo_youtube
            )
            RestartActivity(restartActivity, onRestart = { onRestartActivityClear() })
            AnimatedVisibility(visible = enablePictureInPicture) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("general.picture_in_picture.settings_pip_module"),
                        title = stringResource(R.string.settings_pip_module),
                        text = stringResource(R.string.player_picture_in_picture_content_description),
                        selectedValueText = when (pipModule) {
                            PipModule.Cover -> stringResource(R.string.settings_picture_in_picture_artwork_module_label)
                        },
                        onClick = { showPipModuleDialog = true },
                        icon = R.drawable.logo_youtube
                    )

                    DialogPresence(visible = showPipModuleDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.settings_pip_module),
                            selectedValue = pipModule,
                            onValueSelected = {
                                pipModule = it
                                onRestartActivitySet()
                            },
                            valueText = {
                                when (it) {
                                    PipModule.Cover -> stringResource(R.string.settings_picture_in_picture_artwork_module_label)
                                }
                            },
                            values = PipModule.values().toList(),
                            onDismiss = { showPipModuleDialog = false }
                        )
                    }

                    if (isAtLeastAndroid12) {
                        OtherSwitchSettingEntry(
                            modifier = Modifier.settingsSearchTarget("general.picture_in_picture.settings_enable_pip_auto"),
                            title = stringResource(R.string.settings_enable_pip_auto),
                            text = stringResource(R.string.settings_picture_in_picture_android_12_auto_description),
                            isChecked = enablePictureInPictureAuto,
                            onCheckedChange = {
                                enablePictureInPictureAuto = it
                                onRestartActivitySet()
                            },
                            icon = R.drawable.logo_youtube
                        )
                    }
                    OpenPipButton(enabled = enablePictureInPicture)
                }
            }
        }
    )
}

@Composable
private fun OpenPipButton(enabled: Boolean) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    ) return

    val activity = remember(context) { context.findActivityNullable() } ?: return
    OtherSettingsEntry(
        modifier = Modifier.settingsSearchTarget("general.picture_in_picture.open"),
        title = stringResource(R.string.modules_open),
        text = stringResource(R.string.settings_picture_in_picture_open),
        icon = R.drawable.chevron_forward,
        isEnabled = enabled,
        onClick = {
            val entered = runCatching {
                activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            }.onFailure { Timber.tag("PictureInPicture").w(it, "Unable to enter PiP from settings") }
                .getOrDefault(false)
            if (!entered) Toaster.e(R.string.settings_picture_in_picture_open_failed)
        },
    )
}
