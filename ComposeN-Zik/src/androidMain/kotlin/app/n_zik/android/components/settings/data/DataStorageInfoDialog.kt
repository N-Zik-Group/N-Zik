package app.n_zik.android.components.settings.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.typography

@Composable
internal fun DataStorageInfoDialog(onDismiss: () -> Unit) {
    DialogFrame(
        onDismiss = onDismiss,
        title = stringResource(R.string.storage_breakdown_info_title),
        footer = {
            ButtonRow(listOf(DialogButton(stringResource(R.string.common_action_close), onDismiss, primary = true)))
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (category in StorageCategory.entries) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(category.color, CircleShape))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(
                            text = stringResource(category.label),
                            style = typography().xs.semiBold.copy(color = colorPalette().text),
                        )
                        BasicText(
                            text = stringResource(category.description),
                            style = typography().xs.copy(color = colorPalette().textSecondary),
                        )
                    }
                }
            }
        }
    }
}
