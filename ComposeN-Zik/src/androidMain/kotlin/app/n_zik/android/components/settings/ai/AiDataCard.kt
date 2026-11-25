package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.ui.screens.home.quickpicks.resetQuickPicks
import app.n_zik.android.compose.persist.LocalPersistMap

/** Resets recommendations independently of listening history. */
@UnstableApi
@Composable
internal fun AiDataCard(search: Search) {
    val quickPicksCache = LocalPersistMap.current
    var confirmReset by remember { mutableStateOf(false) }
    val resetTitle = stringResource(R.string.recommendations_action_reset_quick_picks)
    val resetDescription = stringResource(R.string.recommendations_reset_content_description)
    val showReset = search.inputValue.isBlank() || resetTitle.contains(search.inputValue, true) ||
        resetDescription.contains(search.inputValue, true)
    val showCard = showReset || stringResource(R.string.settings_data_title).contains(search.inputValue, true)
    DialogPresence(visible = confirmReset) {
        ConfirmationDialog(
            title = resetTitle,
            confirmText = stringResource(R.string.common_action_reset),
            text = resetDescription,
            onDismiss = { confirmReset = false },
            onConfirm = {
                quickPicksCache?.let(::resetQuickPicks)
                Toaster.s(R.string.recommendations_reset_complete)
            }
        )
    }

    SettingsSectionCard(
        title = stringResource(R.string.settings_data_title),
        icon = R.drawable.server,
        visible = showCard,
        content = {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("ai.tab_data.reset_quick_picks"),
                title = resetTitle,
                text = resetDescription,
                icon = R.drawable.trash,
                onClick = { confirmReset = true }
            )
        }
    )
}
