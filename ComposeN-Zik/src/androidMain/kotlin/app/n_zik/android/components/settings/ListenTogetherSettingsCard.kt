// Listen Together settings card embedded in the room screen (Metrolist port — N-Zik
// styling): server URL (known servers + custom), username, auto-approval
// toggles, host volume sync, connection logs and blocked users. The card is built
// from the app's own components (SettingsSectionCard, OtherSettingsEntry,
// OtherSwitchSettingEntry); the logs and blocked-users menus go through the shared
// MenuState like the app's own item menus, while the server URL and username are
// form sheets following the app's own form-sheet pattern (LastFm login / display
// name) with ListMenu.Menu chrome, app field colors and a full-width accent button.
// Reference: docs/Metrolist app/src/main/kotlin/com/metrolist/music/ui/screens/settings/integrations/ListenTogetherSettings.kt
package app.n_zik.android.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.OtherSwitchSettingEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.ui.screens.listentogether.ListenTogetherBlockedUsersMenu
import app.n_zik.android.components.ui.screens.listentogether.ListenTogetherLogsMenu
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.ListenTogetherServers
import app.n_zik.android.listentogether.RoomRole
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.DataStoreUtils
import timber.log.Timber

private const val TAG = "ListenTogetherSettings"
private const val PREF_USERNAME = "listen_together_username"

/**
 * Listen Together configuration card, rendered inside the room screen.
 *
 * Preferences are read straight from [DataStoreUtils] (SharedPreferences) on every
 * composition, so a value saved elsewhere (e.g. the username field in the room screen)
 * stays in sync without shared state. Writes go through the same keys the client
 * reads, so changes apply on the next connection attempt.
 */
@Composable
fun ListenTogetherSettingsCard() {
    val context = LocalContext.current
    val manager = remember { ListenTogetherManager.getInstance() }
    if (manager == null) {
        Timber.tag(TAG).w("Manager not initialized, settings card not rendered")
        return
    }

    val roomState by manager.roomState.collectAsStateWithLifecycle()
    val role by manager.role.collectAsStateWithLifecycle()
    val logs by manager.logs.collectAsStateWithLifecycle()
    val blockedUsernames by manager.blockedUsernames.collectAsStateWithLifecycle()
    val syncHostVolume by manager.syncHostVolumeEnabled.collectAsStateWithLifecycle()

    // DataStoreUtils reads are one-shot (plain SharedPreferences): bump the version
    // after every save so the entry texts and toggle states refresh right away,
    // instead of waiting for an unrelated recomposition ("username saves nothing"
    // and "toggles unresponsive until leaving the screen" reports).
    val settingsVersion = remember { mutableStateOf(0) }
    val serverUrl =
        remember(settingsVersion.value) {
            DataStoreUtils.getString(
                context,
                ListenTogetherClient.PREF_SERVER_URL,
                ListenTogetherServers.defaultServerUrl,
            )
        }
    val username =
        remember(settingsVersion.value) { DataStoreUtils.getString(context, PREF_USERNAME) }
    val autoApprovalJoins =
        remember(settingsVersion.value) {
            DataStoreUtils.getBoolean(context, ListenTogetherClient.PREF_AUTO_APPROVAL, false)
        }
    val autoApproveSuggestions =
        remember(settingsVersion.value) {
            DataStoreUtils.getBoolean(context, ListenTogetherClient.PREF_AUTO_APPROVE_SUGGESTIONS, false)
        }
    val historyEnabled =
        remember(settingsVersion.value) {
            DataStoreUtils.getBoolean(context, ListenTogetherClient.PREF_HISTORY, false)
        }

    // App shared menu state (same mechanism as the app's own item menus):
    // display() pushes a menu into the root menu sheet rendered by MainActivity.
    val menuState = LocalMenuState.current

    // Form sheets (server URL, username) follow the app's own form-sheet pattern
    // (LastFm login, display name): a dedicated transparent sheet whose anchor
    // stays content-driven, so it grows with the keyboard instead of the shared
    // item-menu sheet, which gets stuck after the keyboard closes.
    var showServerChooserSheet by rememberSaveable { mutableStateOf(false) }
    var showUsernameSheet by rememberSaveable { mutableStateOf(false) }

    val isInRoom = roomState != null
    // Guests cannot change host-side approvals while in a room (hosts always can)
    val canEditApprovals = !isInRoom || role != RoomRole.GUEST

    val selectedServer = remember(serverUrl) { ListenTogetherServers.findByUrl(serverUrl) }

    SettingsSectionCard(
        title = stringResource(R.string.listen_together),
        icon = R.drawable.people,
        description = stringResource(R.string.listen_together_settings_desc),
        content = {
        // Server URL
        OtherSettingsEntry(
            title = stringResource(R.string.listen_together_server_url),
            text =
                selectedServer?.let { server -> "${server.name} — ${server.location}" }
                    ?: serverUrl,
            icon = R.drawable.server,
            onClick = { showServerChooserSheet = true },
        )

        // Username (read-only while in a room, per protocol)
        OtherSettingsEntry(
            title = stringResource(R.string.listen_together_username),
            text = username.ifEmpty { stringResource(R.string.listen_together_not_set) },
            icon = R.drawable.person,
            onClick = {
                if (isInRoom) {
                    Toaster.i(R.string.listen_together_cannot_edit_username_in_room)
                } else {
                    showUsernameSheet = true
                }
            },
        )

        // Auto-approve join requests
        OtherSwitchSettingEntry(
            title = stringResource(R.string.listen_together_auto_approval_joins),
            text = stringResource(R.string.listen_together_auto_approval_joins_desc),
            isChecked = autoApprovalJoins,
            enabled = canEditApprovals,
            onCheckedChange = {
                DataStoreUtils.saveBoolean(context, ListenTogetherClient.PREF_AUTO_APPROVAL, it)
                settingsVersion.value++
                Timber.tag(TAG).i("Auto-approve joins -> $it")
            },
            icon = R.drawable.checkmark,
        )

        // Auto-approve suggestions
        OtherSwitchSettingEntry(
            title = stringResource(R.string.listen_together_auto_approval_suggestions),
            text = stringResource(R.string.listen_together_auto_approval_suggestions_desc),
            isChecked = autoApproveSuggestions,
            enabled = canEditApprovals,
            onCheckedChange = {
                DataStoreUtils.saveBoolean(
                    context,
                    ListenTogetherClient.PREF_AUTO_APPROVE_SUGGESTIONS,
                    it,
                )
                settingsVersion.value++
                Timber.tag(TAG).i("Auto-approve suggestions -> $it")
            },
            icon = R.drawable.musical_notes,
        )

        // Host volume sync (live, in-memory on the manager)
        OtherSwitchSettingEntry(
            title = stringResource(R.string.listen_together_sync_volume),
            text = stringResource(R.string.listen_together_sync_volume_desc),
            isChecked = syncHostVolume,
            onCheckedChange = { manager.setSyncHostVolumeEnabled(it) },
            icon = R.drawable.volume_up,
        )

        // Record LT tracks in the local history (off by default — host metadata
        // belongs to the host app; only a browseId-only placeholder row is written)
        OtherSwitchSettingEntry(
            title = stringResource(R.string.listen_together_history),
            text = stringResource(R.string.listen_together_history_desc),
            isChecked = historyEnabled,
            onCheckedChange = {
                DataStoreUtils.saveBoolean(context, ListenTogetherClient.PREF_HISTORY, it)
                settingsVersion.value++
                Timber.tag(TAG).i("Local history -> $it")
            },
            icon = R.drawable.history,
        )

        // Connection logs
        OtherSettingsEntry(
            title = stringResource(R.string.listen_together_view_logs),
            text = stringResource(R.string.listen_together_view_logs_desc),
            icon = R.drawable.bugs,
            onClick = {
                menuState.display {
                    ListenTogetherLogsMenu(logs = logs, onClear = { manager.clearLogs() })
                }
            },
        )

        // Blocked users
        OtherSettingsEntry(
            title = stringResource(R.string.listen_together_blocked_users),
            text =
                if (blockedUsernames.isNotEmpty()) {
                    stringResource(
                        R.string.listen_together_blocked_users_count,
                        blockedUsernames.size,
                    )
                } else {
                    stringResource(R.string.listen_together_no_blocked_users)
                },
            icon = R.drawable.close,
            onClick = {
                menuState.display {
                    ListenTogetherBlockedUsersMenu(
                        blockedUsernames = blockedUsernames,
                        onUnblock = { manager.unblockUser(it) },
                    )
                }
            },
        )
        }
    )

    // Form sheets, placed after the card like the app's own settings sheets.
    ServerChooserSheet(
        showSheet = showServerChooserSheet,
        onSaved = { settingsVersion.value++ },
        onDismiss = { showServerChooserSheet = false },
        currentUrl = serverUrl,
    )
    UsernameSheet(
        showSheet = showUsernameSheet,
        onSaved = { settingsVersion.value++ },
        onDismiss = { showUsernameSheet = false },
        savedUsername = username,
    )
}

/**
 * Server chooser sheet following the app's own form-sheet pattern (LastFm login /
 * display name): a transparent [CustomModalBottomSheet] with an empty drag handle,
 * [ListMenu.Menu] as the only chrome, app field colors ([InputDialog]) and a
 * full-width accent button. The sheet anchor is content-driven
 * (skipPartiallyExpanded), so it grows with the keyboard instead of getting stuck
 * after the keyboard closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerChooserSheet(
    showSheet: Boolean,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    currentUrl: String,
) {
    val context = LocalContext.current
    val palette = colorPalette()
    var customUrl by remember { mutableStateOf(currentUrl) }
    val trimmedCustomUrl = customUrl.trim()

    // Re-sync the draft each time the sheet opens (the composable stays composed
    // across open/close, so plain remember would keep a stale value).
    LaunchedEffect(showSheet) {
        if (showSheet) customUrl = currentUrl
    }

    CustomModalBottomSheet(
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
        // Cap the content at ~50% of the screen: ListMenu.Menu stretches to the
        // full screen height on its own, which would make the Expanded sheet
        // cover the whole window. Bounded to half the screen, the sheet keeps
        // its half-screen look at rest while the anchor stays content-driven
        // (it grows with the keyboard).
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeightDp * 0.5f).dp),
        ) {
            ListMenu.Menu(title = stringResource(R.string.listen_together_choose_server)) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    ListenTogetherServers.servers.forEach { server ->
                        val isSelected = server.url == currentUrl
                        ListMenu.Entry(
                            text = server.name,
                            subtitle = "${server.location} · ${server.operator}",
                            icon = { SettingIcon(R.drawable.server, palette.accent) },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(R.drawable.checkmark),
                                        contentDescription = null,
                                        tint = palette.accent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            onClick = {
                                DataStoreUtils.saveString(
                                    context,
                                    ListenTogetherClient.PREF_SERVER_URL,
                                    server.url,
                                )
                                Timber.tag(TAG).i("Server URL -> ${server.url}")
                                onSaved()
                                onDismiss()
                            },
                        )
                    }

                    HorizontalDivider(
                        color = palette.background2,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.listen_together_custom_server),
                        style = typography().xs.semiBold,
                        color = palette.text,
                    )
                    TextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.listen_together_server_url_hint)) },
                        colors = InputDialog.defaultTextFieldColors(),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    )
                    Button(
                        onClick = {
                            DataStoreUtils.saveString(
                                context,
                                ListenTogetherClient.PREF_SERVER_URL,
                                trimmedCustomUrl,
                            )
                            Timber.tag(TAG).i("Server URL (custom) -> $trimmedCustomUrl")
                            onSaved()
                            onDismiss()
                        },
                        enabled = trimmedCustomUrl.isNotBlank(),
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
                        Text(stringResource(R.string.listen_together_use_custom_server))
                    }
                }
            }
        }
    }
}

/**
 * Username edit sheet (Metrolist username dialog port) following the app's own
 * form-sheet pattern (LastFm login / display name): a transparent
 * [CustomModalBottomSheet] with [ListMenu.Menu] as the only chrome, app field
 * colors ([InputDialog]) and a full-width accent button. Saving an empty value
 * resets the username (the user then types one before joining).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsernameSheet(
    showSheet: Boolean,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
    savedUsername: String,
) {
    val context = LocalContext.current
    val palette = colorPalette()
    var tempUsername by remember { mutableStateOf(savedUsername) }

    // Re-sync the draft each time the sheet opens (the composable stays composed
    // across open/close, so plain remember would keep a stale value).
    LaunchedEffect(showSheet) {
        if (showSheet) tempUsername = savedUsername
    }

    CustomModalBottomSheet(
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
        // Cap the content at ~50% of the screen: ListMenu.Menu stretches to the
        // full screen height on its own, which would make the Expanded sheet
        // cover the whole window. Bounded to half the screen, the sheet keeps
        // its half-screen look at rest while the anchor stays content-driven
        // (it grows with the keyboard).
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeightDp * 0.5f).dp),
        ) {
            ListMenu.Menu(title = stringResource(R.string.listen_together_username)) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    TextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.listen_together_username_hint)) },
                        trailingIcon = {
                            IconButton(onClick = { tempUsername = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = stringResource(R.string.clear),
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        colors = InputDialog.defaultTextFieldColors(),
                        shape = uiRoundnessShape(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    )
                    Button(
                        onClick = {
                            val value = tempUsername.trim()
                            DataStoreUtils.saveString(context, PREF_USERNAME, value)
                            Timber.tag(TAG).i("Username -> $value")
                            onSaved()
                            onDismiss()
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
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

/**
 * Standard menu icon chip (same pattern as the other app menus): tinted rounded
 * 32dp square with an 18dp icon.
 */
@Composable
private fun SettingIcon(@DrawableRes icon: Int, color: Color) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.1f), uiRoundnessShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
    }
}
