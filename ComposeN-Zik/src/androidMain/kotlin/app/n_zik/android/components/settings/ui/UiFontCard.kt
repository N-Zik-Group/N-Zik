package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.utils.applyFontPaddingKey
import app.it.fast4x.rimusic.utils.fontTypeKey
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.useSystemFontKey
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Owns the font settings and their local dialogs. */
@Composable
internal fun UiFontCard(search: Search) {
    var hideStatusBar by rememberPreference(hideStatusBarKey, false)
    var fontType by rememberPreference(fontTypeKey, FontType.Rubik)
    var useSystemFont by rememberPreference(useSystemFontKey, false)
    var applyFontPadding by rememberPreference(applyFontPaddingKey, false)

    val typoSearchContextMatch = search.inputValue.isBlank() ||
        stringResource(R.string.settings_use_font_type).contains(search.inputValue, true) ||
        stringResource(R.string.use_system_font).contains(search.inputValue, true) ||
        stringResource(R.string.apply_font_padding).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = typoSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.font"),
            title = stringResource(R.string.settings_use_font_type),
            icon = R.drawable.text,
            content = {
                var showFontDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.settings_use_font_type).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.settings_use_font_type"),
                        title = stringResource(R.string.settings_use_font_type),
                        text = fontType.name,
                        icon = R.drawable.text,
                        onClick = { showFontDialog = true }
                    )
                }
                if (showFontDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.settings_use_font_type),
                        selectedValue = fontType,
                        onValueSelected = { fontType = it },
                        valueText = { it.name },
                        values = FontType.values().toList(),
                        onDismiss = { showFontDialog = false }
                    )
                }

                if (search.inputValue.isBlank() || stringResource(R.string.use_system_font).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.use_system_font"),
                        title = stringResource(R.string.use_system_font),
                        text = stringResource(R.string.use_font_by_the_system),
                        isChecked = useSystemFont,
                        onCheckedChange = { useSystemFont = it },
                        icon = R.drawable.settings
                    )
                }

                if (search.inputValue.isBlank() || stringResource(R.string.apply_font_padding).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.apply_font_padding"),
                        title = stringResource(R.string.apply_font_padding),
                        text = stringResource(R.string.add_spacing_around_texts),
                        isChecked = applyFontPadding,
                        onCheckedChange = { applyFontPadding = it },
                        icon = R.drawable.resize
                    )
                }

                if (search.inputValue.isBlank() || stringResource(R.string.hide_status_bar).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.hide_status_bar"),
                        title = stringResource(R.string.hide_status_bar),
                        text = "",
                        isChecked = hideStatusBar,
                        onCheckedChange = { hideStatusBar = it },
                        icon = R.drawable.eye_off
                    )
                }
            }
        )
    }
}
