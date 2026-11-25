package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.normalizedSettingsText
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
import app.n_zik.android.enums.FontType
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.useSystemFontKey
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
    var fontType by rememberPreference(fontTypeKey, FontType.Rubik)
    var useSystemFont by rememberPreference(useSystemFontKey, false)
    var applyFontPadding by rememberPreference(applyFontPaddingKey, false)

    val query = normalizedSettingsText(search.inputValue)
    val title = stringResource(R.string.settings_use_font_type)
    val groupTitle = stringResource(R.string.settings_group_app_typography)
    fun matches(vararg values: String) = values.any { normalizedSettingsText(it).contains(query) }

    val showGroup = matches(title, groupTitle)
    val showFontType = showGroup || matches(stringResource(R.string.interface_font_type_description))
    val showSystemFont = showGroup || matches(
        stringResource(R.string.settings_font_use_system_label),
        stringResource(R.string.settings_font_use_system_description),
    )
    val showFontPadding = showGroup || matches(
        stringResource(R.string.settings_font_include_padding_label),
        stringResource(R.string.settings_font_include_padding_description),
    )

    AnimatedVisibility(
        visible = showFontType || showSystemFont || showFontPadding,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.font"),
            title = title,
            groupTitleRes = R.string.settings_group_app_typography,
            icon = R.drawable.text,
            content = {
                var showFontDialog by remember { mutableStateOf(false) }
                if (showFontType) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.settings_use_font_type"),
                        title = stringResource(R.string.settings_use_font_type),
                        text = stringResource(R.string.interface_font_type_description),
                        selectedValueText = fontType.name,
                        icon = R.drawable.text,
                        onClick = { showFontDialog = true }
                    )
                }
                DialogPresence(visible = showFontDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.settings_use_font_type),
                        selectedValue = fontType,
                        onValueSelected = { fontType = it },
                        valueText = { it.name },
                        values = FontType.values().toList(),
                        onDismiss = { showFontDialog = false }
                    )
                }

                if (showSystemFont) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.use_system_font"),
                        title = stringResource(R.string.settings_font_use_system_label),
                        text = stringResource(R.string.settings_font_use_system_description),
                        isChecked = useSystemFont,
                        onCheckedChange = { useSystemFont = it },
                        icon = R.drawable.settings
                    )
                }

                if (showFontPadding) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.apply_font_padding"),
                        title = stringResource(R.string.settings_font_include_padding_label),
                        text = stringResource(R.string.settings_font_include_padding_description),
                        isChecked = applyFontPadding,
                        onCheckedChange = { applyFontPadding = it },
                        icon = R.drawable.resize
                    )
                }


            }
        )
    }
}
