package app.n_zik.android.components.settings.data

import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.settings.common.showDataResetFeedback
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Applies defaults once in response to the reset action. */
@Composable
internal fun DataResetCard(search: Search) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resetting by remember { mutableStateOf(false) }
    val visible = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.settings_reset),
        icon = R.drawable.refresh,
        modifier = Modifier.testTag("data.card.reset"),
        visible = visible,
        content = {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("data.settings_reset.settings_reset"),
                title = stringResource(R.string.settings_reset),
                text = stringResource(R.string.settings_restore_default_settings),
                icon = R.drawable.refresh,
                isEnabled = !resetting,
                onClick = {
                    if (!resetting) {
                        val profileId = getActiveProfile(context)
                        resetting = true
                        scope.launch {
                            try {
                                var downloadLimitRetained = false
                                val applied = withContext(NzikDispatchers.DATA) {
                                    applyDataDefaults(context, profileId, onDownloadLimitRetained = { downloadLimitRetained = true })
                                }
                                if (applied && getActiveProfile(context) == profileId) {
                                    showDataResetFeedback(downloadLimitRetained)
                                    if (hasPendingDataResetCacheMove(context, profileId)) RestartAppDialog.showDialog()
                                }
                            } finally {
                                resetting = false
                            }
                        }
                    }
                },
            )
        }
    )
}
