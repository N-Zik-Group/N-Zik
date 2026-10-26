package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.RecommendationsNumber
import app.it.fast4x.rimusic.utils.recommendationsNumberKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Preserves the original Recommendations settings and actions. */
@UnstableApi
@Composable
internal fun AiRecommendationsCard(search: Search) {
    var recommendationsNumber by rememberPreference(recommendationsNumberKey, RecommendationsNumber.Adaptive)
    val searchCtx_4 = search.inputValue.isBlank() || stringResource(R.string.smart_recommendations).contains(search.inputValue, true) || stringResource(R.string.smart_recommendations_number).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.smart_recommendations),
        icon = R.drawable.smart_shuffle,
        visible = searchCtx_4,
        content = {
            var showRecommendationsDialog by remember { mutableStateOf(false) }
            if (search.inputValue.isBlank() || stringResource(R.string.smart_recommendations_number).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.smart_recommendations.smart_recommendations_number"),
                    title = stringResource(R.string.smart_recommendations_number),
                    text = if (recommendationsNumber == RecommendationsNumber.Adaptive)
                        stringResource(R.string.smart_recommendations_adaptive_description) else recommendationsNumber.text,
                    icon = R.drawable.shuffle,
                    onClick = { showRecommendationsDialog = true }
                )
            }

            if (showRecommendationsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.smart_recommendations_number),
                    selectedValue = recommendationsNumber,
                    values = RecommendationsNumber.values().toList(),
                    onValueSelected = { recommendationsNumber = it },
                    valueText = {
                        when (it) {
                            RecommendationsNumber.Adaptive -> stringResource(R.string.smart_recommendations_adaptive)
                            else -> it.text
                        }
                    },
                    onDismiss = { showRecommendationsDialog = false }
                )
            }
        }
    )
}
