package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
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

private val resetScopeNames = listOf("general", "ui", "appearance", "ai", "data", "accounts", "network", "other")

internal fun resetScopeForTarget(target: String): Int? = resetScopeNames.indices.firstOrNull { tab ->
    target == "card.$tab.settings_reset" || target == "${resetScopeNames[tab]}.settings_reset.settings_reset"
}

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
    if (open) {
        DialogFrame(
            onDismiss = { open = false },
            title = selectedScope?.let { scope ->
                stringResource(SettingsCategories.first { it.tab == scope }.labelRes)
            } ?: stringResource(R.string.settings_reset),
            footer = {
                ButtonRow(buttons = buildList {
                    if (selectedScope != null) {
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
            ) {
                val scope = selectedScope
                if (scope == null) {
                    scopes.forEach { tab ->
                        val label = SettingsCategories.first { it.tab == tab }.labelRes
                        OtherSettingsEntry(
                            title = stringResource(label),
                            text = stringResource(R.string.settings_reset_scope, stringResource(label)),
                            icon = R.drawable.refresh,
                            modifier = Modifier.testTag("settings.reset.scope.$tab"),
                            onClick = { selectedScope = tab },
                        )
                    }
                } else {
                    resetContent(scope)
                }
            }
        }
    }
}
