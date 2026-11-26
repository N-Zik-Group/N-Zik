package app.n_zik.android.components.search

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OrganizedSettingsCategories
import app.n_zik.android.components.settings.common.ambiguousSettingsCategoryIds
import app.n_zik.android.utils.preferences

/** Opens the per-page history choices. */
@Composable
fun SearchHistoryPageSettings(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OtherSettingsEntry(
        modifier = modifier,
        title = stringResource(R.string.search_history_pages),
        text = stringResource(R.string.search_history_pages_description),
        icon = R.drawable.history,
        onClick = { open = true },
    )
    DialogPresence(visible = open) {
        SearchHistoryPagesDialog(onDismiss = { open = false })
    }
}

/** A choice overrides the category's inherited preference and preserves its stored queries. */
@Composable
fun SearchHistoryPagesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val categories = remember { OrganizedSettingsCategories.filterNot { it.id == "about" } }
    val ambiguousIds = remember(context, configuration) { ambiguousSettingsCategoryIds(context, categories) }
    val items = listOf(
        historyPageToggle(SearchHistoryStore.PAGE_HISTORY, R.drawable.history, stringResource(R.string.library_history_title)),
        historyPageToggle(SearchHistoryStore.PAGE_LOCAL, R.drawable.search_circle, stringResource(R.string.search_history_local)),
        historyPageToggle(SearchHistoryStore.PAGE_ONLINE, R.drawable.network, stringResource(R.string.search_history_online)),
        historyPageToggle(SearchHistoryStore.PAGE_SETTINGS_GLOBAL, R.drawable.settings, stringResource(R.string.settings_global_title)),
        historyPageToggle(SearchHistoryStore.PAGE_SETTINGS_GLASS, R.drawable.droplet, stringResource(R.string.glass_effect_title)),
    ) + categories.map { category ->
        historyPageToggle(
            page = SearchHistoryStore.categoryPage(category.id),
            icon = category.iconRes,
            label = stringResource(R.string.search_history_settings_page, stringResource(category.labelRes)),
            description = if (category.id in ambiguousIds) stringResource(category.descriptionRes) else null,
        )
    }
    val prefs = context.preferences
    val pages = items.map { it.id }
    var enabled by remember(prefs, pages) {
        mutableStateOf(pages.map { SearchHistoryStore.isPageEnabled(prefs, it) })
    }
    DisposableEffect(prefs, pages) {
        fun refresh() {
            enabled = pages.map { SearchHistoryStore.isPageEnabled(prefs, it) }
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || SearchHistoryStore.isPageChoiceKey(key)) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        refresh()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DialogFrame(onDismiss = onDismiss, title = stringResource(R.string.search_history_pages),
        bodyPadding = PaddingValues(horizontal = 24.dp),
        footer = {
            ButtonRow(listOf(DialogButton(stringResource(R.string.common_action_close), onDismiss, danger = true)))
        }
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            ToggleListDialog(
                items = items,
                checkedStatesOverride = enabled,
                onCheckedChange = { index, checked ->
                    prefs.edit().putBoolean(items[index].preferenceKey, checked).apply()
                },
            )
        }
    }
}

private fun historyPageToggle(page: String, icon: Int, label: String, description: String? = null): ToggleItem =
    ToggleItem(page, icon, label, SearchHistoryStore.enabledKey(page), true, description)
