package app.n_zik.android.components.settings.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.glass.GlassCustomChip
import app.n_zik.android.components.glass.GlassOutlinedTextField as OutlinedTextField
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsCardSectionDivider
import app.n_zik.android.components.settings.common.SettingsEntryVisibility
import app.n_zik.android.components.settings.common.normalizedSettingsText
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.tab.Search
import app.n_zik.android.extensions.discord.DiscordActivityBuilder
import app.n_zik.android.extensions.discord.DiscordRefreshIntervalEntry
import app.n_zik.android.extensions.discord.DiscordTemplateFieldActions
import app.n_zik.android.extensions.discord.DiscordTemplateRenderer
import app.n_zik.android.extensions.discord.discordAdvancedActivityTypeKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton1UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2EnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2LabelKey
import app.n_zik.android.extensions.discord.discordAdvancedButton2UrlKey
import app.n_zik.android.extensions.discord.discordAdvancedDetailsTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedIdleCloseEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedLargeImageTextKey
import app.n_zik.android.extensions.discord.discordAdvancedNameKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseClearEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPausePresenceEnabledKey
import app.n_zik.android.extensions.discord.discordAdvancedPauseTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedShowArtworkKey
import app.n_zik.android.extensions.discord.discordAdvancedShowDetailsKey
import app.n_zik.android.extensions.discord.discordAdvancedShowSmallImageKey
import app.n_zik.android.extensions.discord.discordAdvancedShowStateKey
import app.n_zik.android.extensions.discord.discordAdvancedShowTimestampsKey
import app.n_zik.android.extensions.discord.discordAdvancedSmallImageTextKey
import app.n_zik.android.extensions.discord.discordAdvancedStateTemplateKey
import app.n_zik.android.typography
import app.n_zik.android.utils.rememberEncryptedPreference
import app.n_zik.android.utils.semiBold

/** Uses sentence-case secondary text for Discord's advanced settings groups. */
@Composable
private fun DiscordSettingsGroup(
    title: String,
    target: String,
    showDivider: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        if (showDivider) SettingsCardSectionDivider()
        Text(
            text = title,
            style = typography().xs.semiBold.copy(colorPalette().textSecondary),
            modifier = Modifier.settingsSearchTarget(target)
                .padding(top = if (showDivider) 0.dp else 12.dp)
                .padding(start = 12.dp),
        )
        content()
    }
}

/** Configures presence templates, visible sections, buttons and connection timing. */
@Composable
internal fun DiscordAdvancedSection(search: Search) {
    val resources = LocalResources.current
    val buttonTitles = DiscordButtonField.entries.associateWith { it.title(resources::getString) }
    val query = normalizedSettingsText(search.inputValue)
    fun matchesButtonField(field: DiscordButtonField): Boolean =
        normalizedSettingsText(buttonTitles.getValue(field)).contains(query) ||
            normalizedSettingsText(field.title(resources::getString, separator = " ")).contains(query) ||
            normalizedSettingsText(resources.getString(field.fieldRes)).contains(query)

    var activityType by rememberEncryptedPreference(discordAdvancedActivityTypeKey, 2)
    var pausePresenceEnabled by rememberEncryptedPreference(discordAdvancedPausePresenceEnabledKey, true)
    var activityName by rememberEncryptedPreference(discordAdvancedNameKey, "")
    var stateTemplate by rememberEncryptedPreference(discordAdvancedStateTemplateKey, "")
    var detailsTemplate by rememberEncryptedPreference(discordAdvancedDetailsTemplateKey, "")
    var pauseTemplate by rememberEncryptedPreference(discordAdvancedPauseTemplateKey, "")
    var btn1Enabled by rememberEncryptedPreference(discordAdvancedButton1EnabledKey, true)
    var btn1Label by rememberEncryptedPreference(discordAdvancedButton1LabelKey, "")
    var btn1Url by rememberEncryptedPreference(discordAdvancedButton1UrlKey, "")
    var btn2Enabled by rememberEncryptedPreference(discordAdvancedButton2EnabledKey, true)
    var btn2Label by rememberEncryptedPreference(discordAdvancedButton2LabelKey, "")
    var btn2Url by rememberEncryptedPreference(discordAdvancedButton2UrlKey, "")
    var showState by rememberEncryptedPreference(discordAdvancedShowStateKey, true)
    var showDetails by rememberEncryptedPreference(discordAdvancedShowDetailsKey, true)
    var showArtwork by rememberEncryptedPreference(discordAdvancedShowArtworkKey, true)
    var showSmallImage by rememberEncryptedPreference(discordAdvancedShowSmallImageKey, true)
    var showTimestamps by rememberEncryptedPreference(discordAdvancedShowTimestampsKey, true)
    var largeImageText by rememberEncryptedPreference(discordAdvancedLargeImageTextKey, "")
    var smallImageText by rememberEncryptedPreference(discordAdvancedSmallImageTextKey, "")
    var pauseClearEnabled by rememberEncryptedPreference(discordAdvancedPauseClearEnabledKey, true)
    var idleCloseEnabled by rememberEncryptedPreference(discordAdvancedIdleCloseEnabledKey, true)

    var showActivityTypeDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showStateDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showBtn1LabelDialog by remember { mutableStateOf(false) }
    var showBtn1UrlDialog by remember { mutableStateOf(false) }
    var showBtn2LabelDialog by remember { mutableStateOf(false) }
    var showBtn2UrlDialog by remember { mutableStateOf(false) }
    var showLargeImageTextDialog by remember { mutableStateOf(false) }
    var showSmallImageTextDialog by remember { mutableStateOf(false) }

    val activityTypeLabel = when (activityType) {
        0 -> stringResource(R.string.discord_activity_playing)
        3 -> stringResource(R.string.discord_activity_watching)
        5 -> stringResource(R.string.discord_activity_competing)
        else -> stringResource(R.string.discord_activity_listening)
    }

    val showGroupDividers = search.inputValue.isBlank()
    Column {

        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_identity), target = "accounts.social_discord.section.identity") {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_activity_type).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_activity_type"),
                    title = stringResource(R.string.discord_activity_type),
                    text = stringResource(R.string.discord_activity_type_description),
                    selectedValueText = activityTypeLabel,
                    icon = R.drawable.play,
                    onClick = { showActivityTypeDialog = true }
                )
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_name).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_name"),
                    title = stringResource(R.string.discord_advanced_name),
                    text = stringResource(R.string.discord_activity_name_description),
                    selectedValueText = activityName.ifEmpty { stringResource(R.string.discord_presence_name) },
                    icon = R.drawable.text,
                    onClick = { showNameDialog = true }
                )
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_lines), target = "accounts.social_discord.section.lines", showDivider = showGroupDividers) {
            // Keep the toggle reachable while the linked template is hidden.

            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_details).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_details"),
                    title = stringResource(R.string.discord_advanced_show_details),
                    text = stringResource(R.string.discord_advanced_show_details_description),
                    isChecked = showDetails,
                    onCheckedChange = { showDetails = it },
                    icon = R.drawable.musical_notes
                )
            }
            SettingsEntryVisibility(visible = showDetails) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_details).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_details"),
                        title = stringResource(R.string.discord_advanced_details),
                        text = stringResource(R.string.discord_details_template_description),
                        selectedValueText = detailsTemplate.ifEmpty { DiscordActivityBuilder.DEFAULT_DETAILS_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showDetailsDialog = true }
                    )
                }
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_state).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_state"),
                    title = stringResource(R.string.discord_advanced_show_state),
                    text = stringResource(R.string.discord_advanced_show_state_description),
                    isChecked = showState,
                    onCheckedChange = { showState = it },
                    icon = R.drawable.artist
                )
            }
            SettingsEntryVisibility(visible = showState) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_state).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_state"),
                        title = stringResource(R.string.discord_advanced_state),
                        text = stringResource(R.string.discord_state_template_description),
                        selectedValueText = stateTemplate.ifEmpty { DiscordActivityBuilder.DEFAULT_STATE_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showStateDialog = true }
                    )
                }
            }

            if (search.inputValue.isBlank() || stringResource(R.string.discord_pause_presence).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_pause_presence"),
                    title = stringResource(R.string.discord_pause_presence),
                    text = stringResource(R.string.discord_pause_presence_description),
                    isChecked = pausePresenceEnabled,
                    onCheckedChange = { pausePresenceEnabled = it },
                    icon = R.drawable.pause
                )
            }
            SettingsEntryVisibility(visible = showDetails && pausePresenceEnabled) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_pause).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_pause"),
                        title = stringResource(R.string.discord_advanced_pause),
                        text = stringResource(R.string.discord_pause_template_description),
                        selectedValueText = pauseTemplate.ifEmpty { stringResource(R.string.discord_presence_pause_default) },
                        icon = R.drawable.text,
                        onClick = { showPauseDialog = true }
                    )
                }
            }
            // Auto-clear only applies when paused presence is disabled.

            SettingsEntryVisibility(visible = !pausePresenceEnabled) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_pause_auto_clear).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_pause_auto_clear"),
                        title = stringResource(R.string.discord_advanced_pause_auto_clear),
                        text = stringResource(R.string.discord_advanced_pause_auto_clear_description),
                        isChecked = pauseClearEnabled,
                        onCheckedChange = { pauseClearEnabled = it },
                        icon = R.drawable.trash
                    )
                }
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_images), target = "accounts.social_discord.section.images", showDivider = showGroupDividers) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_artwork).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_artwork"),
                    title = stringResource(R.string.discord_advanced_show_artwork),
                    text = stringResource(R.string.discord_advanced_show_artwork_description),
                    isChecked = showArtwork,
                    onCheckedChange = { showArtwork = it },
                    icon = R.drawable.image
                )
            }

            SettingsEntryVisibility(visible = showArtwork) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_image_text).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_image_text"),
                        title = stringResource(R.string.discord_advanced_image_text),
                        text = stringResource(R.string.discord_artwork_tooltip_description),
                        selectedValueText = largeImageText.ifEmpty { DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showLargeImageTextDialog = true }
                    )
                }
            }
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_small_image).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_small_image"),
                    title = stringResource(R.string.discord_advanced_show_small_image),
                    text = stringResource(R.string.discord_advanced_show_small_image_description),
                    isChecked = showSmallImage,
                    onCheckedChange = { showSmallImage = it },
                    icon = R.drawable.ic_launcher,
                    iconTint = Color.Unspecified,
                )
            }
            SettingsEntryVisibility(visible = showSmallImage) {
                if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_logo_text).contains(search.inputValue, true)) {
                    // The manager resolves the version placeholder when it builds the presence.
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_logo_text"),
                        title = stringResource(R.string.discord_advanced_logo_text),
                        text = stringResource(R.string.discord_logo_tooltip_description),
                        selectedValueText = smallImageText.ifEmpty { DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE },
                        icon = R.drawable.text,
                        onClick = { showSmallImageTextDialog = true }
                    )
                }
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_progress), target = "accounts.social_discord.section.progress", showDivider = showGroupDividers) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_show_timestamps).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_show_timestamps"),
                    title = stringResource(R.string.discord_advanced_show_timestamps),
                    text = stringResource(R.string.discord_advanced_show_timestamps_description),
                    isChecked = showTimestamps,
                    onCheckedChange = { showTimestamps = it },
                    icon = R.drawable.playbackduration
                )
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_buttons), target = "accounts.social_discord.section.buttons", showDivider = showGroupDividers) {
            if (normalizedSettingsText(stringResource(R.string.discord_advanced_button1)).contains(query) ||
                matchesButtonField(DiscordButtonField.FirstLabel) || matchesButtonField(DiscordButtonField.FirstUrl)
            ) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_button1"),
                    title = stringResource(R.string.discord_advanced_button1),
                    text = stringResource(R.string.discord_first_button_enabled_description),
                    isChecked = btn1Enabled,
                    onCheckedChange = { btn1Enabled = it },

                    icon = R.drawable.github_icon
                )
            }
            // Stack the entries because AnimatedVisibility lays its children out in a Box.

            SettingsEntryVisibility(visible = btn1Enabled) {
                Column {
                    if (matchesButtonField(DiscordButtonField.FirstLabel)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget(DiscordButtonField.FirstLabel.targetId),
                            title = buttonTitles.getValue(DiscordButtonField.FirstLabel),
                            text = stringResource(R.string.discord_first_button_label_description),
                            selectedValueText = btn1Label.ifEmpty { stringResource(R.string.discord_presence_button_get_nzik) },
                            icon = R.drawable.text,
                            onClick = { showBtn1LabelDialog = true }
                        )
                    }
                    if (matchesButtonField(DiscordButtonField.FirstUrl)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget(DiscordButtonField.FirstUrl.targetId),
                            title = buttonTitles.getValue(DiscordButtonField.FirstUrl),
                            text = stringResource(R.string.discord_first_button_url_description),
                            selectedValueText = btn1Url.ifEmpty { DiscordActivityBuilder.DEFAULT_BUTTON1_URL },
                            icon = R.drawable.open,
                            onClick = { showBtn1UrlDialog = true }
                        )
                    }
                }
            }
            if (normalizedSettingsText(stringResource(R.string.discord_advanced_button2)).contains(query) ||
                matchesButtonField(DiscordButtonField.SecondLabel) || matchesButtonField(DiscordButtonField.SecondUrl)
            ) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_button2"),
                    title = stringResource(R.string.discord_advanced_button2),
                    text = stringResource(R.string.discord_second_button_enabled_description),
                    isChecked = btn2Enabled,
                    onCheckedChange = { btn2Enabled = it },
                    icon = R.drawable.ytmusic
                )
            }
            SettingsEntryVisibility(visible = btn2Enabled) {
                Column {
                    if (matchesButtonField(DiscordButtonField.SecondLabel)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget(DiscordButtonField.SecondLabel.targetId),
                            title = buttonTitles.getValue(DiscordButtonField.SecondLabel),
                            text = stringResource(R.string.discord_second_button_label_description),
                            selectedValueText = btn2Label.ifEmpty { stringResource(R.string.discord_presence_button_listen_ytmusic) },
                            icon = R.drawable.text,
                            onClick = { showBtn2LabelDialog = true }
                        )
                    }
                    if (matchesButtonField(DiscordButtonField.SecondUrl)) {
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget(DiscordButtonField.SecondUrl.targetId),
                            title = buttonTitles.getValue(DiscordButtonField.SecondUrl),
                            text = stringResource(R.string.discord_second_button_url_description),
                            selectedValueText = btn2Url.ifEmpty { DiscordActivityBuilder.DEFAULT_BUTTON2_URL },
                            icon = R.drawable.open,
                            onClick = { showBtn2UrlDialog = true }
                        )
                    }
                }
            }
        }
        DiscordSettingsGroup(title = stringResource(R.string.discord_advanced_group_connection), target = "accounts.social_discord.section.connection", showDivider = showGroupDividers) {
            if (search.inputValue.isBlank() || stringResource(R.string.discord_advanced_idle_close).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_idle_close"),
                    title = stringResource(R.string.discord_advanced_idle_close),
                    text = stringResource(R.string.discord_advanced_idle_close_description),
                    isChecked = idleCloseEnabled,
                    onCheckedChange = { idleCloseEnabled = it },
                    icon = R.drawable.link
                )
            }
            // The manager reads the interval on each tick so edits apply live.
            if (search.inputValue.isBlank() || listOf(
                    stringResource(R.string.discord_advanced_refresh_interval),
                    stringResource(R.string.discord_advanced_refresh_interval_description),
                ).any { normalizedSettingsText(it).contains(query) }
            ) {
                DiscordRefreshIntervalEntry(Modifier.settingsSearchTarget("accounts.social_discord.discord_advanced_refresh_interval"))
            }
        }
    }

    DialogPresence(visible = showActivityTypeDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.discord_activity_type),
            selectedValue = activityType,
            values = listOf(2, 0, 3, 5),
            onValueSelected = { activityType = it },
            onDismiss = { showActivityTypeDialog = false },
            valueText = { value ->
                when (value) {
                    0 -> stringResource(R.string.discord_activity_playing)
                    3 -> stringResource(R.string.discord_activity_watching)
                    5 -> stringResource(R.string.discord_activity_competing)
                    else -> stringResource(R.string.discord_activity_listening)
                }
            }
        )
    }
    DialogPresence(visible = showNameDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_name),
            value = activityName,
            placeholderValue = stringResource(R.string.discord_presence_name),
            onDone = { activityName = it },
            onDismiss = { showNameDialog = false }
        )
    }
    DialogPresence(visible = showStateDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_state),
            value = stateTemplate,
            placeholderValue = DiscordActivityBuilder.DEFAULT_STATE_TEMPLATE,
            onDone = { stateTemplate = it },
            onDismiss = { showStateDialog = false }
        )
    }
    DialogPresence(visible = showDetailsDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_details),
            value = detailsTemplate,
            placeholderValue = DiscordActivityBuilder.DEFAULT_DETAILS_TEMPLATE,
            onDone = { detailsTemplate = it },
            onDismiss = { showDetailsDialog = false }
        )
    }
    DialogPresence(visible = showPauseDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_pause),
            value = pauseTemplate,
            placeholderValue = stringResource(R.string.discord_presence_pause_default),
            onDone = { pauseTemplate = it },
            onDismiss = { showPauseDialog = false }
        )
    }
    DialogPresence(visible = showBtn1LabelDialog) {
        DiscordTemplateFieldDialog(
            title = buttonTitles.getValue(DiscordButtonField.FirstLabel),
            value = btn1Label,
            placeholderValue = stringResource(R.string.discord_presence_button_get_nzik),
            onDone = { btn1Label = it },
            onDismiss = { showBtn1LabelDialog = false }
        )
    }
    DialogPresence(visible = showBtn1UrlDialog) {
        DiscordTemplateFieldDialog(
            title = buttonTitles.getValue(DiscordButtonField.FirstUrl),
            value = btn1Url,
            placeholderValue = DiscordActivityBuilder.DEFAULT_BUTTON1_URL,
            onDone = { btn1Url = it },
            onDismiss = { showBtn1UrlDialog = false }
        )
    }
    DialogPresence(visible = showBtn2LabelDialog) {
        DiscordTemplateFieldDialog(
            title = buttonTitles.getValue(DiscordButtonField.SecondLabel),
            value = btn2Label,
            placeholderValue = stringResource(R.string.discord_presence_button_listen_ytmusic),
            onDone = { btn2Label = it },
            onDismiss = { showBtn2LabelDialog = false }
        )
    }
    DialogPresence(visible = showBtn2UrlDialog) {
        DiscordTemplateFieldDialog(
            title = buttonTitles.getValue(DiscordButtonField.SecondUrl),
            value = btn2Url,
            placeholderValue = DiscordActivityBuilder.DEFAULT_BUTTON2_URL,
            onDone = { btn2Url = it },
            onDismiss = { showBtn2UrlDialog = false }
        )
    }
    DialogPresence(visible = showLargeImageTextDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_image_text),
            value = largeImageText,
            placeholderValue = DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE,
            onDone = { largeImageText = it },
            onDismiss = { showLargeImageTextDialog = false }
        )
    }
    DialogPresence(visible = showSmallImageTextDialog) {
        DiscordTemplateFieldDialog(
            title = stringResource(R.string.discord_advanced_logo_text),
            value = smallImageText,
            placeholderValue = DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE,
            onDone = { smallImageText = it },
            onDismiss = { showSmallImageTextDialog = false }
        )
    }
}

/** Edits a draft template whose empty value uses the built-in default. */
@Composable
private fun DiscordTemplateFieldDialog(
    title: String,
    value: String,
    placeholderValue: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textState = remember { mutableStateOf(value) }
    var text by textState
    val actions = remember { DiscordTemplateFieldActions(textState) }
    val palette = colorPalette()
    DefaultDialog(onDismiss = onDismiss, title = title,
        footer = {
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.common_action_reset),
                        textColor = palette.textDisabled,
                        onClick = { actions.reset() }
                    ),
                    DialogButton(
                        text = stringResource(R.string.common_action_cancel),
                        onClick = onDismiss, danger = true
                    ),
                    DialogButton(
                        text = stringResource(R.string.common_action_ok),
                        primary = true,
                        onClick = { actions.confirm(onDone, onDismiss) }
                    )
                )
            )
        }
    ) {
        // Match the dialog palette so text stays readable in dark themes.

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholderValue, style = typography().s.copy(color = palette.textSecondary)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                cursorColor = palette.accent,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.textSecondary,
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.discord_advanced_placeholders),
            style = typography().s.copy(color = palette.textSecondary)
        )
        Spacer(Modifier.height(12.dp))
        // Wrap placeholders so the end of the list remains visible on narrow screens.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DiscordTemplateRenderer.PLACEHOLDERS.forEach { placeholder ->
                GlassCustomChip(palette.accent.copy(alpha = 0.15f), onClick = { text += placeholder }) { chipModifier ->
                    Text(
                        text = placeholder,
                        style = typography().s.copy(color = palette.accent),
                        modifier = chipModifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

    }
}
