package app.n_zik.android.components.ui.screens.rescue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.ui.NzikCheckbox
import app.n_zik.android.typography
import app.n_zik.android.utils.DEFAULT_PROFILE_ID

/** A selectable rescue target profile (display label + active marker). */
@Immutable
internal data class ProfileOption(
    val id: String,
    val label: String,
    val isActive: Boolean
)

@Composable
internal fun RescueProfileSelectionDialog(
    title: String,
    profiles: List<ProfileOption>,
    selectedProfileIds: Set<String>,
    onDismiss: () -> Unit,
    onProfileCheckedChange: (String, Boolean) -> Unit,
    checkboxTagPrefix: String? = null,
    onNext: () -> Unit,
) {
    DialogFrame(
        onDismiss = onDismiss,
        title = title,
        bodyPadding = PaddingValues(0.dp),
        footer = {
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        danger = true,
                    ),
                    DialogButton(
                        text = stringResource(R.string.common_action_next),
                        primary = true,
                        enabled = selectedProfileIds.isNotEmpty(),
                        onClick = onNext,
                    ),
                )
            )
        }
    ) {
        val palette = colorPalette()
        // Scroll the profiles independently so the footer stays reachable.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            profiles.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NzikCheckbox(
                        checked = option.id in selectedProfileIds,
                        onCheckedChange = { checked -> onProfileCheckedChange(option.id, checked) },
                        modifier = checkboxTagPrefix?.let { Modifier.testTag(it + option.id) } ?: Modifier,
                    )
                    Text(
                        text = option.label,
                        color = palette.text,
                        modifier = Modifier.weight(1f)
                    )
                    if (option.isActive) {
                        Text(
                            text = stringResource(R.string.recovery_profile_active),
                            style = typography().xxs.copy(color = palette.accent)
                        )
                    }
                    if (option.id == DEFAULT_PROFILE_ID) {
                        Text(
                            text = stringResource(R.string.recovery_profile_base),
                            style = typography().xxs.copy(color = palette.accent)
                        )
                    }
                }
            }
        }
    }
}
