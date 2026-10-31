package app.n_zik.android.components.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.glass.glassEnabledDefault
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget

/** Master switch on the dedicated page; disabling it does not disable its preview. */
@Composable
internal fun UiGlassCard() {
    var enabled by rememberPreference(glassEnabledKey, glassEnabledDefault)
    var showPerformanceWarning by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.liquid_glass)
    val enabledLabel = stringResource(R.string.liquid_glass_enable)
    SettingsSectionCard(title = label, icon = R.drawable.color_palette, content = {
        OtherSwitchSettingEntry(
            title = enabledLabel,
            modifier = Modifier.testTag("settings.glass.master").settingsSearchTarget("glass.master"),
            text = stringResource(R.string.liquid_glass_enable_desc),
            isChecked = enabled,
            onCheckedChange = { requested ->
                if (requested) showPerformanceWarning = true else enabled = false
            },
            icon = R.drawable.color_palette,
        )
    })
    DialogPresence(showPerformanceWarning) {
        ConfirmationDialog(
            title = stringResource(R.string.glass_enable_warning_title),
            text = stringResource(R.string.glass_enable_warning_description),
            confirmText = stringResource(R.string.glass_enable_confirm),
            onDismiss = { showPerformanceWarning = false },
            onConfirm = { enabled = true },
        )
    }
}
