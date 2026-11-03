package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
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
internal fun AiGeneralCard(search: Search, showRewindMaster: Boolean = true) {
    var enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)
    val rewindEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_ENABLED, true)
    val context = LocalContext.current
    val searchCtx_0 = search.inputValue.isBlank() || stringResource(R.string.settings_general_title).contains(search.inputValue, true) || stringResource(R.string.settings_recommendations_quick_picks_enabled_label).contains(search.inputValue, true) || stringResource(R.string.rewind_settings_enabled).contains(search.inputValue, true) || stringResource(R.string.rewind_settings_title).contains(search.inputValue, true) || stringResource(R.string.recommendations_section_visibility_description).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.settings_general_title),
        icon = R.drawable.settings,
        visible = searchCtx_0,
        content = {
            if (search.inputValue.isBlank() || stringResource(R.string.settings_recommendations_quick_picks_enabled_label).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("ai.tab_general.enable_quick_picks_page"),
                    title = stringResource(R.string.settings_recommendations_quick_picks_enabled_label),
                    text = stringResource(R.string.recommendations_page_enabled_description),
                    isChecked = enableQuickPicksPage,
                    onCheckedChange = {
                        enableQuickPicksPage = it
                    },
                    icon = R.drawable.sparkles
                )
            }
            if (showRewindMaster && (search.inputValue.isBlank() || stringResource(R.string.rewind_settings_enabled).contains(search.inputValue, true))) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("ai.tab_general.rw_settings_master"),
                    title = stringResource(R.string.rewind_settings_enabled),
                    text = stringResource(R.string.rewind_settings_enabled_description),
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
