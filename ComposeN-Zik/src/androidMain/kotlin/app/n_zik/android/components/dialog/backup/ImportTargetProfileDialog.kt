package app.n_zik.android.components.dialog.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.medium
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.components.import.ProfileTargetOption
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.semiBold

/**
 * The "Import into which profile?" dialog (NZik dialog frame, like [ImportBackupDialog]):
 * a radio list of the local profiles (the active one marked) plus the file-tag row
 * when it is unknown. The import button stays disabled until a profile is picked, so
 * an import can never run without an explicit target; the outside tap cancels and
 * reports back to the host, which aborts the pending chain (nothing was written yet).
 */
object ImportTargetProfileDialog : InteractiveDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.import_target_profile_title)

    override var isActive: Boolean by mutableStateOf(false)

    private var options: List<ProfileTargetOption> by mutableStateOf(emptyList())
    private var selectedId: String? by mutableStateOf(null)
    private var onTarget: ((String) -> Unit)? by mutableStateOf(null)
    private var onDismiss: (() -> Unit)? by mutableStateOf(null)

    /** Shows the dialog. [onDismiss] fires when it is cancelled (outside tap). */
    fun show(
        options: List<ProfileTargetOption>,
        preselectedId: String?,
        onTarget: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        this.options = options
        selectedId = preselectedId
        this.onTarget = onTarget
        this.onDismiss = onDismiss
        isActive = true
    }

    // The framework calls hideDialog() on the outside tap — report the cancel so the
    // host can abort the pending chain.
    override fun hideDialog() {
        isActive = false
        onDismiss?.invoke()
    }

    @Composable
    override fun DialogBody() {
        val activeLabel = stringResource(R.string.profile_active)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(uiRoundnessShape())
                        .clickable { selectedId = option.id }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedId == option.id,
                        onClick = { selectedId = option.id },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colorPalette().text,
                            unselectedColor = colorPalette().textSecondary
                        ),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildString {
                                append(option.displayName)
                                if (option.isActive) {
                                    append("  (")
                                    append(activeLabel)
                                    append(")")
                                }
                            },
                            style = typography().xs.semiBold,
                            color = colorPalette().text
                        )
                        if (option.fromFileTag) {
                            Text(
                                text = stringResource(R.string.import_target_from_file),
                                style = typography().xxs,
                                color = colorPalette().textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun Buttons() {
        Button(
            enabled = selectedId != null,
            onClick = {
                val id = selectedId ?: return@Button
                val action = onTarget
                isActive = false
                action?.invoke(id)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorPalette().accent,
                contentColor = colorPalette().textSecondary
            ),
            shape = uiRoundnessShape()
        ) {
            Text(stringResource(R.string.import_button), style = typography().s.medium)
        }
    }
}
