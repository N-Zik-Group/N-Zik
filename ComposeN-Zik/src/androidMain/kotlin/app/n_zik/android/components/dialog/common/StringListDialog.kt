package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.n_zik.android.uiRoundnessShape

/** Editable string list with shared add and close footer actions. */
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
        title = title,
        bodyPadding = PaddingValues(0.dp),
        onDismiss = onDismiss,
        modifier = modifier,
        footer = {
            ButtonRow(
                buttons = listOf(
                    DialogButton(text = addTitle, onClick = { showStringAddDialog = true }, primary = true),
                    DialogButton(
                        text = stringResource(R.string.dialog_close),
                        onClick = onDismiss,
                        danger = true
                    )
                )
            )
        }
    ) {
        Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(24.dp)) {
            // List items
            if (list.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
        }
    }

    DialogPresence(visible = showStringAddDialog) {
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

    DialogPresence(visible = showStringRemoveDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.dialog_remove_item),
            confirmText = stringResource(R.string.dialog_remove),
            text = removeTitle,
            onDismiss = { showStringRemoveDialog = false },
            onConfirm = {
                remove(removingItem)
            }
        )
    }

    DialogPresence(visible = errorDialog) {
        DefaultDialog(
            title = stringResource(R.string.dialog_name_conflict),
            onDismiss = { errorDialog = false },
            footer = {
                ButtonRow(listOf(DialogButton(stringResource(R.string.dialog_close), onClick = { errorDialog = false })))
            }
        ) {
            BasicText(text = conflictTitle, style = typography().s.copy(color = colorPalette().text))
        }
    }
}
