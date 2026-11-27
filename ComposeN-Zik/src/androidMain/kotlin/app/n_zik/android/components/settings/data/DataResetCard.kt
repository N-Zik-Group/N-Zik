package app.n_zik.android.components.settings.data

import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.settings.common.RestartPlayerService
import app.n_zik.android.components.settings.common.showDataResetFeedback
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DataResetCard(search: Search) {
    var restartPlayer by rememberSaveable { mutableStateOf(false) }
    val reset = rememberDataResetAction(
        onPlayerRestartRequired = { restartPlayer = true },
        onAppRestartRequired = {
            restartPlayer = false
            RestartAppDialog.showDialog()
        },
    )
    val visible = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
    DataResetCard(reset, visible) {
        RestartPlayerService(restartPlayer) { restartPlayer = false }
    }
}

/** Owns the reset in the host that displays the result, even after its dialog closes. */
@Composable
internal fun rememberDataResetAction(
    onPlayerRestartRequired: () -> Unit,
    onAppRestartRequired: () -> Unit = { RestartAppDialog.showDialog() },
): DataResetAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentRestartAction by rememberUpdatedState(onPlayerRestartRequired)
    val currentAppRestartAction by rememberUpdatedState(onAppRestartRequired)
    return remember(context, scope) {
        DataResetAction(context, scope, { currentRestartAction() }, { currentAppRestartAction() })
    }
}

internal class DataResetAction(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPlayerRestartRequired: () -> Unit,
    private val onAppRestartRequired: () -> Unit,
) {
    var running by mutableStateOf(false)
        private set

    fun start() {
        if (running) return
        val profileId = getActiveProfile(context)
        val priorLimits = readStorageCacheLimits(context, profileId)
        running = true
        scope.launch {
            try {
                var downloadLimitRetained = false
                val applied = withContext(NzikDispatchers.DATA) {
                    applyDataDefaults(context, profileId, onDownloadLimitRetained = { downloadLimitRetained = true })
                }
                if (applied && getActiveProfile(context) == profileId) {
                    showDataResetFeedback(downloadLimitRetained)
                    val currentLimits = readStorageCacheLimits(context, profileId)
                    if (hasPendingDataResetCacheMove(context, profileId) || currentLimits.requiresAppRestart(priorLimits)) {
                        onAppRestartRequired()
                    } else if (priorLimits.streaming != currentLimits.streaming) {
                        onPlayerRestartRequired()
                    }
                }
            } finally {
                running = false
            }
        }
    }
}

@Composable
internal fun DataResetCard(
    reset: DataResetAction,
    visible: Boolean = true,
    footer: @Composable () -> Unit = {},
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_reset),
        icon = R.drawable.refresh,
        modifier = Modifier.testTag("data.card.reset"),
        visible = visible,
        content = {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("data.settings_reset.settings_reset")
                    .testTag("settings.reset.data.action"),
                title = stringResource(R.string.settings_reset),
                text = stringResource(R.string.settings_restore_default_settings),
                icon = R.drawable.refresh,
                isEnabled = !reset.running,
                onClick = reset::start,
            )
            footer()
        }
    )
}
