package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Preserves the original Data settings and actions. */
@UnstableApi
@Composable
internal fun AiDataCard(search: Search) {
    val context = LocalContext.current
    var clearEvents by remember { mutableStateOf(false) }
    val searchCtx_7 = search.inputValue.isBlank() || stringResource(R.string.tab_data).contains(search.inputValue, true) || stringResource(R.string.reset_quick_picks).contains(search.inputValue, true)
    if (clearEvents) {
        ConfirmationDialog(
            title = stringResource(R.string.dialog_delete_playback_events),
            confirmText = stringResource(R.string.delete),
            text = stringResource(R.string.do_you_really_want_to_delete_all_playback_events),
            onDismiss = { clearEvents = false },
            onConfirm = {
                Database.asyncTransaction {
                    eventTable.deleteAll()
                    Toaster.done()
                }
            }
        )
    }

    SettingsSectionCard(
        title = stringResource(R.string.tab_data),
        icon = R.drawable.server,
        visible = searchCtx_7,
        content = {
            val eventsCount by remember {
                Database.eventTable
                        .countAll()
            }.collectAsStateWithLifecycle(0L, context = NzikDispatchers.DATA)

            if (search.inputValue.isBlank() || stringResource(R.string.reset_quick_picks).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.tab_data.reset_quick_picks"),
                    title = stringResource(R.string.reset_quick_picks),
                    text = stringResource(R.string.settings_desc_ai_tab_data_reset_quick_picks),
                    selectedValueText = if (eventsCount > 0) {
                        stringResource(R.string.delete_playback_events, eventsCount)
                    } else {
                        stringResource(R.string.quick_picks_are_cleared)
                    },
                    icon = R.drawable.trash,
                    onClick = { clearEvents = true }
                )
            }
        }
    )
}
