package app.n_zik.android.components.settings.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.privacy.blockScreenCaptureKey

@Composable
fun PrivacyScreenCaptureCard() {
    var blocked by rememberPreference(blockScreenCaptureKey, false)
    SettingsSectionCard(
        title = stringResource(R.string.privacy_screen_protection),
        icon = R.drawable.shield_checkmark,
        content = {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.privacy_block_screenshots),
                text = stringResource(R.string.privacy_block_screenshots_description),
                isChecked = blocked,
                onCheckedChange = { blocked = it },
                icon = R.drawable.shield_checkmark,
                modifier = Modifier.settingsSearchTarget("privacy.screen.block_capture"),
            )
        },
    )
}
