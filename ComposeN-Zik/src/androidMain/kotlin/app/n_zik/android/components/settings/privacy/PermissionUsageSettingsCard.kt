package app.n_zik.android.components.settings.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogButtonPlacement
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.typography
import java.text.DateFormat
import java.util.Date

@Composable
fun PermissionUsageSettingsCard(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var paused by rememberPreference(PermissionUsageJournal.PAUSED_KEY, false)
    SettingsSectionCard(
        title = stringResource(R.string.permissions_usage_journal),
        icon = R.drawable.history,
        modifier = modifier,
        content = {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.permissions_journal_pause),
                text = stringResource(R.string.permissions_journal_pause_description),
                isChecked = paused,
                onCheckedChange = { paused = it },
                icon = R.drawable.pause,
                modifier = Modifier.settingsSearchTarget("privacy.permissions.pause"),
            )
            OtherSettingsEntry(
                title = stringResource(R.string.permissions_journal_entries),
                text = stringResource(R.string.permissions_journal_entries_description),
                icon = R.drawable.history,
                onClick = { open = true },
                modifier = Modifier.settingsSearchTarget("privacy.permissions.open"),
            )
        },
    )
    DialogPresence(visible = open) {
        PermissionUsageDialog(onDismiss = { open = false })
    }
}

@Composable
private fun PermissionUsageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.preferences
    val events by remember(prefs) { PermissionUsageJournal.observe(prefs) }.collectAsStateWithLifecycle(emptyList())
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }
    var confirmClear by remember { mutableStateOf(false) }
    DialogFrame(
        bodyPadding = PaddingValues(horizontal = 24.dp),
        title = stringResource(R.string.permissions_usage_journal),
        onDismiss = onDismiss,
        modifier = Modifier.testTag("permission_journal_dialog"),
        footer = {
            ButtonRow(buttons = listOf(
                DialogButton(
                    text = stringResource(R.string.common_action_clear),
                    placement = DialogButtonPlacement.Start,
                    onClick = { confirmClear = true },
                    danger = true,
                ),
                DialogButton(text = stringResource(R.string.common_action_close), onClick = onDismiss, primary = true),
            ))
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("permission_journal_list"),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (events.isEmpty()) item {
                BasicText(
                    text = stringResource(R.string.permissions_journal_empty),
                    style = typography().xxs.secondary,
                )
            }
            itemsIndexed(events, key = { index, _ -> index }, contentType = { _, _ -> "permission_event" }) { _, event ->
                val timestamp = dateFormat.format(Date(event.timestamp))
                BasicText(
                    text = stringResource(R.string.permissions_journal_event, timestamp,
                        stringResource(featureLabel(event.feature)), stringResource(permissionLabel(event.permission)), stringResource(eventLabel(event.kind))),
                    style = typography().xxs.secondary,
                )
            }
        }
    }
    DialogPresence(visible = confirmClear) {
        ConfirmationDialog(
        title = stringResource(R.string.permissions_journal_clear),
        confirmText = stringResource(R.string.common_action_clear),
        text = stringResource(R.string.permissions_journal_clear_confirm),
        onDismiss = { confirmClear = false },
        onConfirm = {
            confirmClear = false
            if (!PermissionUsageJournal.clear(context)) {
                Toaster.i(context.getString(R.string.permissions_journal_busy))
            }
        },
    )
    }
}

private fun featureLabel(feature: PermissionFeature): Int = when (feature) {
    PermissionFeature.MUSIC_RECOGNITION -> R.string.permissions_feature_music_recognition
    PermissionFeature.VOICE_SEARCH -> R.string.permissions_feature_voice_search
    PermissionFeature.VISUALIZER -> R.string.permissions_feature_visualizer
    PermissionFeature.LOCAL_LIBRARY -> R.string.permissions_feature_local_library
    PermissionFeature.AUDIO_DEVICES -> R.string.permissions_feature_audio_devices
    PermissionFeature.PLAYBACK -> R.string.permissions_feature_playback
    PermissionFeature.DOWNLOADS -> R.string.permissions_feature_downloads
    PermissionFeature.REWIND -> R.string.permissions_feature_rewind
    PermissionFeature.LISTEN_TOGETHER -> R.string.permissions_feature_listen_together
    PermissionFeature.PC_SERVER -> R.string.permissions_feature_pc_server
    PermissionFeature.PC_PAIRING -> R.string.permissions_feature_pc_pairing
    PermissionFeature.ONBOARDING -> R.string.permissions_feature_onboarding
}
private fun permissionLabel(permission: JournalPermission): Int = when (permission) {
    JournalPermission.MICROPHONE -> R.string.permissions_label_microphone
    JournalPermission.MEDIA_AUDIO -> R.string.permissions_label_media_audio
    JournalPermission.BLUETOOTH -> R.string.permissions_label_bluetooth
    JournalPermission.LOCAL_NETWORK -> R.string.permissions_label_local_network
    JournalPermission.CAMERA -> R.string.permissions_label_camera
    JournalPermission.NOTIFICATIONS -> R.string.permissions_label_notifications
}
private fun eventLabel(kind: PermissionEventKind): Int = when (kind) {
    PermissionEventKind.REQUESTED -> R.string.permissions_event_requested
    PermissionEventKind.GRANTED -> R.string.permissions_event_granted
    PermissionEventKind.DENIED -> R.string.permissions_event_denied
    PermissionEventKind.USED -> R.string.permissions_event_used
}
