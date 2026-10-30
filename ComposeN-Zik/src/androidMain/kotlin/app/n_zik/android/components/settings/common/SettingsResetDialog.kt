package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.ButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.glass.rememberGlassStore

private val resetScopeNames = listOf("general", "ui", "appearance", "ai", "data", "accounts", "network", "other")
private const val GLASS_RESET_SCOPE = -1

private data class ResetDialogStep(val scope: Int?)

internal fun resetScopeForTarget(target: String): Int? = if (target == "glass.settings_reset.settings_reset") GLASS_RESET_SCOPE
else resetScopeNames.indices.firstOrNull { tab ->
    target == "card.$tab.settings_reset" || target == "${resetScopeNames[tab]}.settings_reset.settings_reset"
}

private fun resetScopeLabel(scope: Int): Int = if (scope == GLASS_RESET_SCOPE) R.string.liquid_glass
    else SettingsCategories.first { it.tab == scope }.labelRes

/** Reuses each original reset action so its defaults and restart behavior remain unchanged. */
@Composable
internal fun SettingsResetCard(
    scopes: List<Int>,
    target: String,
    resetContent: @Composable (Int) -> Unit,
) {
    val initialScope = resetScopeForTarget(target)
    var open by remember(target) { mutableStateOf(initialScope != null) }
    var selectedScope by remember(target) { mutableStateOf(initialScope) }
    SettingsSectionCard(
        title = stringResource(R.string.settings_reset),
        icon = R.drawable.refresh,
        modifier = Modifier.testTag("settings.reset.card"),
        content = {
            OtherSettingsEntry(
                title = stringResource(R.string.settings_reset_choose_scope),
                text = stringResource(R.string.settings_reset_choose_scope_description),
                icon = R.drawable.refresh,
                modifier = Modifier.testTag("settings.reset.open"),
                onClick = { selectedScope = null; open = true },
            )
        },
    )
    DialogPresence(target = ResetDialogStep(selectedScope).takeIf { open }) { step ->
        // Render the retained step until the shared dialog fade reaches its invisible midpoint.
        val scope = step.scope
        DialogFrame(
            onDismiss = { open = false },
            bodyPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
            title = scope?.let { scope ->
                stringResource(resetScopeLabel(scope))
            } ?: stringResource(R.string.settings_reset),
            footer = {
                ButtonRow(buttons = buildList {
                    if (scope != null) {
                        add(DialogButton(
                            text = stringResource(R.string.settings_reset_scope_back),
                            onClick = { selectedScope = null },
                        ))
                    }
                    add(DialogButton(
                        text = stringResource(R.string.cancel),
                        onClick = { open = false },
                        danger = true,
                    ))
                })
            }
        ) {
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .testTag("settings.reset.dialog").verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp)
            ) {
                if (scope == null) {
                    val availableScopes = scopes.flatMap { if (it == 1) listOf(it, GLASS_RESET_SCOPE) else listOf(it) }
                    availableScopes.forEach { tab ->
                        val label = resetScopeLabel(tab)
                        OtherSettingsEntry(
                            title = stringResource(label),
                            text = stringResource(R.string.settings_reset_scope, stringResource(label)),
                            icon = R.drawable.refresh,
                            modifier = Modifier.testTag("settings.reset.scope.$tab"),
                            onClick = { if (open && selectedScope == null) selectedScope = tab },
                        )
                    }
                } else if (scope == GLASS_RESET_SCOPE) {
                    val store = rememberGlassStore()
                    OtherSettingsEntry(
                        title = stringResource(R.string.settings_reset),
                        text = stringResource(R.string.settings_restore_default_settings),
                        icon = R.drawable.refresh,
                        modifier = Modifier.testTag("settings.reset.glass.action"),
                        onClick = {
                            if (open && selectedScope == GLASS_RESET_SCOPE) {
                                store.reset()
                                Toaster.done()
                            }
                        },
                    )
                } else {
                    resetContent(scope)
                }
            }
        }
    }
}
