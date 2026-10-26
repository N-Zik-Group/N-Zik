package app.n_zik.android.components.settings.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference

/** Preserves the original General settings and actions. */
@UnstableApi
@Composable
internal fun AiGeneralCard(search: Search) {
    var enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)
    val rewindEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_ENABLED, true)
    val context = LocalContext.current
    val searchCtx_0 = search.inputValue.isBlank() || stringResource(R.string.tab_general).contains(search.inputValue, true) || stringResource(R.string.enable_quick_picks_page).contains(search.inputValue, true) || stringResource(R.string.rw_settings_master).contains(search.inputValue, true) || stringResource(R.string.rw_settings_title).contains(search.inputValue, true) || stringResource(R.string.disable_if_you_do_not_want_to_see).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.tab_general),
        icon = R.drawable.settings,
        visible = searchCtx_0,
        content = {
            if (search.inputValue.isBlank() || stringResource(R.string.enable_quick_picks_page).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.enable_quick_picks_page),
                    text = "",
                    isChecked = enableQuickPicksPage,
                    onCheckedChange = {
                        enableQuickPicksPage = it
                    },
                    icon = R.drawable.sparkles
                )
            }
            if (search.inputValue.isBlank() || stringResource(R.string.rw_settings_master).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.rw_settings_master),
                    text = stringResource(R.string.rw_settings_master_description),
                    isChecked = rewindEnabled,
                    onCheckedChange = {
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, it)



                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, it)
                        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, it)
                    },
                    icon = R.drawable.sparkles
                )
            }
        }
    )
}
