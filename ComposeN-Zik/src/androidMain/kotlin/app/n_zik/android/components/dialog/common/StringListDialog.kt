package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.uiRoundnessShape

/** Editable string list dialog on the shared [DialogFrame]: a header with the "add" action, a scrollable list (trash icon per item) and a cancel [ButtonRow]. */
@Composable
inline fun StringListDialog(
    title: String,
    addTitle: String,
    addPlaceholder: String,
    removeTitle: String,
    conflictTitle: String,
    list: List<String>,
    crossinline add: (String) -> Unit,
    crossinline remove: (String) -> Unit,
    noinline onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showStringAddDialog by remember {
        mutableStateOf(false)
    }
    var showStringRemoveDialog by remember {
        mutableStateOf(false)
    }
    var removingItem by remember { mutableStateOf("") }
    var errorDialog by remember { mutableStateOf(false) }

    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        // Header
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                text = title,
                style = typography().l.semiBold.copy(
                    color = colorPalette().text
                )
            )
            Button(
                onClick = { showStringAddDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette().accent,
                    contentColor = colorPalette().textSecondary
                ),
                shape = uiRoundnessShape()
            ) {
                Text(
                    text = addTitle,
                    style = typography().s.semiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // List items
        if (list.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                list.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable { }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicText(
                            text = item,
                            style = typography().s.copy(
                                color = colorPalette().text
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                removingItem = item
                                showStringRemoveDialog = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.trash),
                                contentDescription = stringResource(R.string.delete),
                                tint = colorPalette().red,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = stringResource(R.string.no_items),
                    style = typography().s.copy(
                        color = colorPalette().textSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    danger = true
                )
            )
        )
    }

    if (showStringAddDialog) {
        SettingsInputDialog(
            title = addTitle,
            initialValue = "",
            placeholder = addPlaceholder,
            onDismiss = { showStringAddDialog = false },
            onSetValue = {
                if (it !in list) {
                    add(it)
                } else {
                    errorDialog = true
                }
            }
        ).apply {
            showDialog()
            Render()
        }
    }

    if (showStringRemoveDialog) {
        ConfirmationDialog(
            text = removeTitle,
            onDismiss = { showStringRemoveDialog = false },
            onConfirm = {
                remove(removingItem)
            }
        )
    }

    if (errorDialog) {
        ConfirmationDialog(
            text = conflictTitle,
            onDismiss = { errorDialog = false },
            onConfirm = { errorDialog = false },
            confirmText = stringResource(R.string.confirm)
        )
    }
}
