package app.n_zik.android.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.resolveDisplayName
import app.n_zik.android.ytAccountName
import timber.log.Timber

/**
 * Display-name card at the top of the Accounts tab: shows the name currently shown on
 * the Rewind slides, toggles its source (YouTube account / custom name) and edits the
 * custom name.
 *
 * Values are seeded once from [DataStoreUtils] and persisted back through it — the same
 * `app_settings` store the Rewind deck ViewModel reads, so the next deck load picks the
 * change up. The YouTube reads are one-shot: no login side-effect recomposes this card,
 * and the contract is "reflected on the next deck load".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayNameSettingsCard() {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(600)) + scaleIn(
            animationSpec = tween(600),
            initialScale = 0.9f
        )
    ) {
        SettingsSectionCard(
            title = stringResource(R.string.display_name_title),
            icon = R.drawable.person,
            description = stringResource(R.string.display_name_description),
            content = {
                val context = LocalContext.current
                var source by remember {
                    mutableStateOf(
                        DataStoreUtils.getString(
                            context,
                            DataStoreUtils.KEY_DISPLAY_NAME_SOURCE,
                            DataStoreUtils.DISPLAY_NAME_SOURCE_CUSTOM
                        )
                    )
                }
                var customName by remember {
                    mutableStateOf(DataStoreUtils.getString(context, DataStoreUtils.KEY_USERNAME, ""))
                }
                val ytLoggedIn = remember { isYouTubeLoggedIn() }
                // The account name is only read while the stored source is the YouTube
                // account (the display-name choice is custom (guest) or YouTube); the
                // remember key follows the source so the preview updates on toggle
                val ytName = remember(source) {
                    if (source == DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE && ytLoggedIn) ytAccountName() else ""
                }
                var showSourceDialog by remember { mutableStateOf(false) }
                var editName by remember { mutableStateOf(false) }
                var nameDraft by remember { mutableStateOf("") }

                val effectiveName = resolveDisplayName(
                    source = source,
                    ytLoggedIn = ytLoggedIn,
                    ytName = ytName,
                    customName = customName,
                    default = stringResource(R.string.display_name_default)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(colorPalette().accent.copy(alpha = 0.1f), shape = uiRoundnessShape()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            tint = colorPalette().accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.display_name_shown_as),
                            color = colorPalette().textSecondary,
                            style = typography().xxs
                        )
                        Text(
                            text = effectiveName,
                            color = colorPalette().text,
                            style = typography().s
                        )
                    }
                }

                OtherSettingsEntry(
                    title = stringResource(R.string.display_name_source),
                    text = if (source == DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE) {
                        stringResource(R.string.display_name_source_youtube)
                    } else {
                        stringResource(R.string.display_name_source_custom)
                    },
                    icon = R.drawable.logo_youtube,
                    onClick = { showSourceDialog = true }
                )

                OtherSettingsEntry(
                    title = stringResource(R.string.display_name_edit),
                    text = customName.ifBlank { stringResource(R.string.display_name_not_set) },
                    icon = R.drawable.pencil,
                    onClick = { editName = true }
                )

                if (showSourceDialog) {
                    ValueSelectorDialog(
                        onDismiss = { showSourceDialog = false },
                        title = stringResource(R.string.display_name_source),
                        selectedValue = source,
                        values = listOf(
                            DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE,
                            DataStoreUtils.DISPLAY_NAME_SOURCE_CUSTOM
                        ),
                        valueText = { value ->
                            if (value == DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE) {
                                stringResource(R.string.display_name_source_youtube)
                            } else {
                                stringResource(R.string.display_name_source_custom)
                            }
                        },
                        onValueSelected = { value ->
                            source = value
                            DataStoreUtils.saveString(context, DataStoreUtils.KEY_DISPLAY_NAME_SOURCE, value)
                            Timber.tag("DisplayName").i("Display name source -> $value")
                        }
                    )
                }

                LaunchedEffect(editName) {
                    if (editName) nameDraft = customName
                }

                CustomModalBottomSheet(
                    showSheet = editName,
                    onDismissRequest = {
                        editName = false
                    },
                    containerColor = colorPalette().background0,
                    contentColor = colorPalette().background0,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape = uiRoundnessShape(),
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(top = 18.dp, bottom = 6.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // stringResource is @Composable: hoisted out of the non-composable
                        // semantics lambda
                        val fieldHint = stringResource(R.string.display_name_hint)
                        TextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            singleLine = true,
                            placeholder = { Text(fieldHint) },
                            colors = InputDialog.defaultTextFieldColors(),
                            shape = uiRoundnessShape(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .semantics {
                                    contentDescription = fieldHint
                                }
                        )

                        Button(
                            onClick = {
                                val trimmed = nameDraft.trim()
                                DataStoreUtils.saveString(context, DataStoreUtils.KEY_USERNAME, trimmed)
                                customName = trimmed
                                editName = false
                                Timber.tag("DisplayName").i("Custom display name saved: '$trimmed'")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPalette().accent,
                                contentColor = colorPalette().textSecondary
                            ),
                            shape = uiRoundnessShape(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.display_name_save))
                        }
                    }
                }
            }
        )
    }
}
