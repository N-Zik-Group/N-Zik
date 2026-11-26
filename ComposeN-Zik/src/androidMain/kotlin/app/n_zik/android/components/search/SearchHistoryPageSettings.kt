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
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.components.dialog.common.ToggleItem
import app.n_zik.android.components.dialog.common.ToggleListDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OrganizedSettingsCategories
import app.n_zik.android.components.settings.common.SettingsNestedOption
import app.n_zik.android.components.settings.common.ambiguousSettingsCategoryIds
import app.n_zik.android.utils.preferences

/** Opens the per-page history choices. */
@Composable
fun SearchHistoryPageSettings(modifier: Modifier = Modifier) {
    DisposableEffect(Unit) {
        onDispose { SearchHistoryPageDialog.hideDialog() }
    }
    OtherSettingsEntry(
        modifier = modifier,
        title = stringResource(R.string.search_history_pages),
        text = stringResource(R.string.search_history_pages_description),
        icon = R.drawable.history,
        onClick = SearchHistoryPageDialog::showDialog,
    )
    SearchHistoryPageDialog.Render()
}

private data class HistoryPageChoice(
    val page: String,
    val iconRes: Int,
    val labelRes: Int,
    val labelFormatRes: Int? = null,
    val categoryId: String? = null,
    val descriptionRes: Int? = null,
)

private val historyCategories = OrganizedSettingsCategories.filterNot { it.id == "about" }
private val historyPageChoices = listOf(
    HistoryPageChoice(SearchHistoryStore.PAGE_HISTORY, R.drawable.history, R.string.library_history_title),
    HistoryPageChoice(SearchHistoryStore.PAGE_LOCAL, R.drawable.search_circle, R.string.search_history_local),
    HistoryPageChoice(SearchHistoryStore.PAGE_ONLINE, R.drawable.network, R.string.search_history_online),
    HistoryPageChoice(SearchHistoryStore.PAGE_SETTINGS_GLOBAL, R.drawable.settings, R.string.settings_global_title),
    HistoryPageChoice(SearchHistoryStore.PAGE_SETTINGS_GLASS, R.drawable.droplet, R.string.glass_effect_title),
) + historyCategories.map { category ->
    HistoryPageChoice(
        SearchHistoryStore.categoryPage(category.id), category.iconRes, category.labelRes,
        labelFormatRes = R.string.search_history_settings_page,
        categoryId = category.id, descriptionRes = category.descriptionRes,
    )
}

internal object SearchHistoryPageDialog : InteractiveDialog {
    override var isActive by mutableStateOf(false)
    override val dialogTitle: String
        @Composable get() = stringResource(R.string.search_history_pages)
    override val bodyPadding: PaddingValues get() = PaddingValues(horizontal = 24.dp)

    fun searchOptions(): List<SettingsNestedOption> = historyPageChoices.map { choice ->
        SettingsNestedOption(choice.page, choice.labelRes, labelFormatRes = choice.labelFormatRes)
    }

    @Composable
    override fun DialogBody() = SearchHistoryPageChoices()

    @Composable
    override fun Buttons() = SearchHistoryPageButtons(::hideDialog)
}

/** A choice overrides the category's inherited preference and preserves its stored queries. */
@Composable
fun SearchHistoryPagesDialog(onDismiss: () -> Unit) {
    DialogFrame(onDismiss = onDismiss, title = stringResource(R.string.search_history_pages),
        bodyPadding = PaddingValues(horizontal = 24.dp),
        footer = { SearchHistoryPageButtons(onDismiss) }
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            SearchHistoryPageChoices()
        }
    }
}

@Composable
private fun SearchHistoryPageButtons(onDismiss: () -> Unit) {
    ButtonRow(listOf(DialogButton(stringResource(R.string.common_action_close), onDismiss, danger = true)))
}

@Composable
private fun SearchHistoryPageChoices() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val ambiguousIds = remember(context, configuration) { ambiguousSettingsCategoryIds(context, historyCategories) }
    val items = historyPageChoices.map { choice ->
        val label = stringResource(choice.labelRes)
        ToggleItem(
            id = choice.page,
            iconRes = choice.iconRes,
            label = choice.labelFormatRes?.let { stringResource(it, label) } ?: label,
            preferenceKey = SearchHistoryStore.enabledKey(choice.page),
            defaultValue = true,
            description = choice.descriptionRes?.takeIf { choice.categoryId?.let(ambiguousIds::contains) == true }
                ?.let { stringResource(it) },
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
    ToggleListDialog(
        items = items,
        checkedStatesOverride = enabled,
        onCheckedChange = { index, checked ->
            prefs.edit().putBoolean(items[index].preferenceKey, checked).apply()
        },
    )
}
