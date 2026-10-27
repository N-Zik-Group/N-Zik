package app.n_zik.android.components.settings.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference

@Composable
fun PrivacyRewindCard() {
    val context = LocalContext.current
    val enabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_ENABLED, true)
    SettingsSectionCard(
        title = stringResource(R.string.rw_settings_master), icon = R.drawable.sparkles,
        content = {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.rw_settings_master),
                text = stringResource(R.string.rw_settings_master_description),
                isChecked = enabled,
                onCheckedChange = {
                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, it)
                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, it)
                    DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, it)
                },
                icon = R.drawable.sparkles,
                modifier = Modifier.settingsSearchTarget("ai.tab_general.rw_settings_master"),
            )
        },
    )
}
