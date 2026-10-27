package app.n_zik.android.components.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OrganizedSettingsCategories
import app.n_zik.android.components.settings.common.SettingsCategories
/** Opens the per-page history choices beside the existing search display-order setting. */
@Composable
fun SearchHistoryPageSettings() {
    var open by remember { mutableStateOf(false) }
    OtherSettingsEntry(
        title = stringResource(R.string.search_history_pages),
        text = stringResource(R.string.search_history_pages_description),
        icon = R.drawable.history,
        onClick = { open = true },
    )
    if (open) SearchHistoryPagesDialog(onDismiss = { open = false })
}

/** Immediate, independent choices; disabling a page preserves its stored queries. */
@Composable
fun SearchHistoryPagesDialog(onDismiss: () -> Unit) {
    val pageTabs = mapOf(
        SearchHistoryStore.PAGE_SETTINGS_GENERAL to 0,
        SearchHistoryStore.PAGE_SETTINGS_UI to 1,
        SearchHistoryStore.PAGE_SETTINGS_APPEARANCE to 2,
        SearchHistoryStore.PAGE_SETTINGS_AI to 3,
        SearchHistoryStore.PAGE_SETTINGS_DATA to 4,
        SearchHistoryStore.PAGE_SETTINGS_ACCOUNTS to 5,
        SearchHistoryStore.PAGE_SETTINGS_NETWORK to 6,
        SearchHistoryStore.PAGE_SETTINGS_OTHER to 7,
    )
    val items = SearchHistoryStore.pages.map { page ->
        val legacyCategory = pageTabs[page]?.let { tab -> SettingsCategories.firstOrNull { it.tab == tab } }
        val category = OrganizedSettingsCategories.firstOrNull { "settings_category_${it.id}" == page }
        val labelRes = legacyCategory?.labelRes ?: category?.labelRes ?: R.string.settings_global_title
        val iconRes = legacyCategory?.iconRes ?: category?.iconRes ?: R.drawable.settings
        val label = when (page) {
            SearchHistoryStore.PAGE_HISTORY -> stringResource(R.string.history)
            SearchHistoryStore.PAGE_LOCAL -> stringResource(R.string.search_history_local)
            SearchHistoryStore.PAGE_ONLINE -> stringResource(R.string.search_history_online)
            SearchHistoryStore.PAGE_SETTINGS_GLOBAL -> stringResource(R.string.settings_global_title)
            else -> stringResource(
                R.string.search_history_settings_page,
                stringResource(labelRes),
            )
        }
        val icon = when (page) {
            SearchHistoryStore.PAGE_HISTORY -> R.drawable.history
            SearchHistoryStore.PAGE_LOCAL -> R.drawable.search_circle
            SearchHistoryStore.PAGE_ONLINE -> R.drawable.network
            SearchHistoryStore.PAGE_SETTINGS_GLOBAL -> R.drawable.settings
            else -> iconRes
        }
        ToggleItem(page, icon, label, SearchHistoryStore.enabledKey(page), true)
    }
    DialogFrame(onDismiss = onDismiss, title = stringResource(R.string.search_history_pages),
        bodyPadding = PaddingValues(horizontal = 24.dp),
        footer = {
            ButtonRow(listOf(DialogButton(stringResource(R.string.search_history_close), onDismiss, primary = true)))
        }
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            ToggleListDialog(items = items)
        }
    }
}
