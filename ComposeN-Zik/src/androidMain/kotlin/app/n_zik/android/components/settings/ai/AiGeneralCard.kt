package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.settings.common.normalizedSettingsText
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Controls whether the Quick Picks page appears in the home navigation. */
@UnstableApi
@Composable
internal fun AiGeneralCard(search: Search) {
    var enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)
    val title = stringResource(R.string.settings_section_quick_picks_visibility_title)
    val label = stringResource(R.string.settings_recommendations_quick_picks_enabled_label)
    val description = stringResource(R.string.recommendations_page_enabled_description)
    val groupTitle = stringResource(R.string.settings_group_recommendation_sources)
    val query = normalizedSettingsText(search.inputValue)
    SettingsSectionCard(
        title = title,
        icon = R.drawable.settings,
        groupTitleRes = R.string.settings_group_recommendation_sources,
        visible = sequenceOf(title, groupTitle, label, description).any { normalizedSettingsText(it).contains(query) },
        content = {
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("ai.tab_general.enable_quick_picks_page"),
                title = label,
                text = description,
                isChecked = enableQuickPicksPage,
                onCheckedChange = { enableQuickPicksPage = it },
                icon = R.drawable.sparkles,
            )
        },
    )
}
