package app.n_zik.android.components.ui.screens.listentogether

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.components.menu.menuHeaderBackLeading
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.glass.GlassTextField as TextField
import app.n_zik.android.components.glass.SampledMenuSheet
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.ui.UiButton as Button
import app.n_zik.android.components.ui.UiIconButton as IconButton
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.semiBold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateRoomSheet(
    showSheet: Boolean,
    savedUsername: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = colorPalette()
    var tempUsername by remember { mutableStateOf(savedUsername) }

    // The sheet remains composed while closed, so reload the saved draft on open.
    LaunchedEffect(showSheet) {
        if (showSheet) tempUsername = savedUsername
    }

    SampledMenuSheet(
        showSheet = showSheet,
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        modifier = Modifier.statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
            RoundedCornerShape(
                topStart = it.topStart,
                topEnd = it.topEnd,
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
        } ?: uiRoundnessShape(),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = Color.Transparent,
            ) {}
        },
    ) {
        // The menu otherwise expands to fill the whole window.
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeightDp * 0.5f).dp),
        ) {
            ListMenu.Menu(
                title = stringResource(R.string.listen_together_create_room),
                headerLeading = menuHeaderBackLeading(onDismiss),
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    TextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.listen_together_enter_username)) },
                        trailingIcon = {
                            if (tempUsername.isNotBlank()) {
                                IconButton(onClick = { tempUsername = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.common_action_clear),
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        colors = InputDialog.defaultTextFieldColors(),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    )
                    // Validate on click to preserve the app's accent button styling.
                    Button(
                        onClick = {
                            val name = tempUsername.trim()
                            if (name.isNotBlank()) onConfirm(name)
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.textSecondary,
                            ),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.listen_together_create_room),
                            style = typography().s.semiBold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JoinRoomSheet(
    showSheet: Boolean,
    savedUsername: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = colorPalette()
    var tempCode by remember { mutableStateOf("") }
    var tempUsername by remember { mutableStateOf(savedUsername) }

    // Re-sync the drafts each time the sheet opens (fresh code, saved username).
    LaunchedEffect(showSheet) {
        if (showSheet) {
            tempCode = ""
            tempUsername = savedUsername
        }
    }

    val hasValidCode = tempCode.length == 8
    val hasValidUsername = tempUsername.trim().isNotBlank()

    SampledMenuSheet(
        showSheet = showSheet,
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        modifier = Modifier.statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
            RoundedCornerShape(
                topStart = it.topStart,
                topEnd = it.topEnd,
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
        } ?: uiRoundnessShape(),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = Color.Transparent,
            ) {}
        },
    ) {
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeightDp * 0.5f).dp),
        ) {
            ListMenu.Menu(
                title = stringResource(R.string.listen_together_join_room),
                headerLeading = menuHeaderBackLeading(onDismiss),
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    TextField(
                        value = tempCode,
                        onValueChange = { if (it.length <= 8) tempCode = it.uppercase() },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.listen_together_enter_room_code)) },
                        trailingIcon = {
                            if (tempCode.isNotBlank()) {
                                IconButton(onClick = { tempCode = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.common_action_clear),
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        colors = InputDialog.defaultTextFieldColors(),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    )
                    TextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.listen_together_enter_username)) },
                        trailingIcon = {
                            if (tempUsername.isNotBlank()) {
                                IconButton(onClick = { tempUsername = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.common_action_clear),
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        colors = InputDialog.defaultTextFieldColors(),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    )
                    // Validate on click to preserve the app's accent button styling.
                    Button(
                        onClick = {
                            if (hasValidCode && hasValidUsername) {
                                onConfirm(tempCode, tempUsername.trim())
                            }
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.textSecondary,
                            ),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.listen_together_join_room),
                            style = typography().s.semiBold,
                        )
                    }
                }
            }
        }
    }
}
