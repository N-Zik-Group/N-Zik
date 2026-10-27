package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.customColorKey
import app.it.fast4x.rimusic.utils.customThemeDark_accentKey
import app.it.fast4x.rimusic.utils.customThemeDark_Background0Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background1Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background2Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background3Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background4Key
import app.it.fast4x.rimusic.utils.customThemeDark_iconButtonPlayerKey
import app.it.fast4x.rimusic.utils.customThemeDark_textDisabledKey
import app.it.fast4x.rimusic.utils.customThemeDark_TextKey
import app.it.fast4x.rimusic.utils.customThemeDark_textSecondaryKey
import app.it.fast4x.rimusic.utils.customThemeLight_accentKey
import app.it.fast4x.rimusic.utils.customThemeLight_Background0Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background1Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background2Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background3Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background4Key
import app.it.fast4x.rimusic.utils.customThemeLight_iconButtonPlayerKey
import app.it.fast4x.rimusic.utils.customThemeLight_textDisabledKey
import app.it.fast4x.rimusic.utils.customThemeLight_TextKey
import app.it.fast4x.rimusic.utils.customThemeLight_textSecondaryKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.UiTypeKey
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.ButtonBarSettingEntry
import app.n_zik.android.components.settings.common.ColorSettingEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.settingsEntryEnter
import app.n_zik.android.components.settings.common.settingsEntryExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Owns appearance preferences, palette confirmations and interface-mode selection. */
@Composable
internal fun UiThemeCard(search: Search) {
    val context = LocalContext.current
    val uiType by rememberPreference(UiTypeKey, UiType.RiMusic)
    var colorPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)
    var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    var customThemeLight_Background0 by rememberPreference(customThemeLight_Background0Key, DefaultLightColorPalette.background0.hashCode())
    var customThemeLight_Background1 by rememberPreference(customThemeLight_Background1Key, DefaultLightColorPalette.background1.hashCode())
    var customThemeLight_Background2 by rememberPreference(customThemeLight_Background2Key, DefaultLightColorPalette.background2.hashCode())
    var customThemeLight_Background3 by rememberPreference(customThemeLight_Background3Key, DefaultLightColorPalette.background3.hashCode())
    var customThemeLight_Background4 by rememberPreference(customThemeLight_Background4Key, DefaultLightColorPalette.background4.hashCode())
    var customThemeLight_Text by rememberPreference(customThemeLight_TextKey, DefaultLightColorPalette.text.hashCode())
    var customThemeLight_TextSecondary by rememberPreference(customThemeLight_textSecondaryKey, DefaultLightColorPalette.textSecondary.hashCode())
    var customThemeLight_TextDisabled by rememberPreference(customThemeLight_textDisabledKey, DefaultLightColorPalette.textDisabled.hashCode())
    var customThemeLight_IconButtonPlayer by rememberPreference(customThemeLight_iconButtonPlayerKey, DefaultLightColorPalette.iconButtonPlayer.hashCode())
    var customThemeLight_Accent by rememberPreference(customThemeLight_accentKey, DefaultLightColorPalette.accent.hashCode())
    var customThemeDark_Background0 by rememberPreference(customThemeDark_Background0Key, DefaultDarkColorPalette.background0.hashCode())
    var customThemeDark_Background1 by rememberPreference(customThemeDark_Background1Key, DefaultDarkColorPalette.background1.hashCode())
    var customThemeDark_Background2 by rememberPreference(customThemeDark_Background2Key, DefaultDarkColorPalette.background2.hashCode())
    var customThemeDark_Background3 by rememberPreference(customThemeDark_Background3Key, DefaultDarkColorPalette.background3.hashCode())
    var customThemeDark_Background4 by rememberPreference(customThemeDark_Background4Key, DefaultDarkColorPalette.background4.hashCode())
    var customThemeDark_Text by rememberPreference(customThemeDark_TextKey, DefaultDarkColorPalette.text.hashCode())
    var customThemeDark_TextSecondary by rememberPreference(customThemeDark_textSecondaryKey, DefaultDarkColorPalette.textSecondary.hashCode())
    var customThemeDark_TextDisabled by rememberPreference(customThemeDark_textDisabledKey, DefaultDarkColorPalette.textDisabled.hashCode())
    var customThemeDark_IconButtonPlayer by rememberPreference(customThemeDark_iconButtonPlayerKey, DefaultDarkColorPalette.iconButtonPlayer.hashCode())
    var customThemeDark_Accent by rememberPreference(customThemeDark_accentKey, DefaultDarkColorPalette.accent.hashCode())
    var customColor by rememberPreference(customColorKey, Color.Green.hashCode())
    var resetCustomLightThemeDialog by rememberSaveable { mutableStateOf(false) }
    var resetCustomDarkThemeDialog by rememberSaveable { mutableStateOf(false) }
    if (resetCustomLightThemeDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.title_reset_customized_light_colors),
            confirmText = stringResource(R.string.reset),
            text = stringResource(R.string.do_you_really_want_to_reset_the_custom_light_theme_colors),
            onDismiss = { resetCustomLightThemeDialog = false },
            onConfirm = {
                resetCustomLightThemeDialog = false
                resetUiPalette(context, dark = false)
            }
        )
    }

    if (resetCustomDarkThemeDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.title_reset_customized_dark_colors),
            confirmText = stringResource(R.string.reset),
            text = stringResource(R.string.do_you_really_want_to_reset_the_custom_dark_theme_colors),
            onDismiss = { resetCustomDarkThemeDialog = false },
            onConfirm = {
                resetCustomDarkThemeDialog = false
                resetUiPalette(context, dark = true)
            }
        )
    }

    val uiSearchContextMatch = search.inputValue.isBlank() || stringResource(R.string.theme).contains(search.inputValue, true) ||
        stringResource(R.string.interface_in_use).contains(search.inputValue, true) ||
        stringResource(R.string.theme).contains(search.inputValue, true) ||
        stringResource(R.string.theme_mode).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = uiSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.theme"),
            title = stringResource(R.string.theme),
            icon = R.drawable.color_palette,
            content = {
                var showUiTypeDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.interface_in_use).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.theme.interface_in_use"),
                        title = stringResource(R.string.interface_in_use),
                        text = stringResource(R.string.settings_desc_ui_theme_interface_in_use),
                        selectedValueText = uiType.text,
                        icon = R.drawable.ui,
                        onClick = { showUiTypeDialog = true }
                    )
                }

                if (showUiTypeDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.interface_in_use),
                        selectedValue = uiType,
                        onValueSelected = {
                            applyUiType(context, it)
                            RestartAppDialog.showDialog()
                        },
                        valueText = { it.text },
                        values = UiType.values().toList(),
                        onDismiss = { showUiTypeDialog = false }
                    )
                }

                var showThemeDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.theme).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.theme.theme"),
                        title = stringResource(R.string.theme),
                        text = stringResource(R.string.settings_desc_ui_theme_theme),
                        selectedValueText = colorPaletteName.text,
                        icon = R.drawable.color_palette,
                        onClick = { showThemeDialog = true }
                    )
                }

                if (showThemeDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.theme),
                        selectedValue = colorPaletteName,
                        onValueSelected = {
                            colorPaletteName = it
                            when (it) {
                                ColorPaletteName.PureBlack,
                                ColorPaletteName.ModernBlack -> colorPaletteMode = ColorPaletteMode.System
                                else -> {}
                            }
                        },
                        valueText = { it.text },
                        values = ColorPaletteName.values().toList(),
                        onDismiss = { showThemeDialog = false }
                    )
                }

                AnimatedVisibility(visible = colorPaletteName == ColorPaletteName.CustomColor, enter = settingsEntryEnter, exit = settingsEntryExit) {
                    Column {
                        if (search.inputValue.isBlank() || stringResource(R.string.customcolor).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                title = stringResource(R.string.customcolor),
                                text = stringResource(R.string.settings_desc_ui_theme_customcolor),
                                color = Color(customColor),
                                onColorSelected = { customColor = it.hashCode() },
                                modifier = (Modifier.padding(start = 25.dp)).settingsSearchTarget("ui.theme.customcolor")
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = colorPaletteName == ColorPaletteName.Customized, enter = settingsEntryEnter, exit = settingsEntryExit) {
                    Column(modifier = Modifier.padding(start = 25.dp)) {

                        if (search.inputValue.isBlank() || stringResource(R.string.info_click_to_reset_default_light_colors).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true) || stringResource(R.string.title_reset_customized_light_colors).contains(search.inputValue, true)) {
                            ButtonBarSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.title_reset_customized_light_colors"),
                                title = stringResource(R.string.title_reset_customized_light_colors),
                                text = stringResource(R.string.info_click_to_reset_default_light_colors),
                                icon = R.drawable.trash,
                                onClick = { resetCustomLightThemeDialog = true }
                            )
                        }

                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_background_1).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_1"),
                                title = stringResource(R.string.color_background_1),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_1),
                                color = Color(customThemeLight_Background0),
                                onColorSelected = { customThemeLight_Background0 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_background_2).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_2"),
                                title = stringResource(R.string.color_background_2),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_2),
                                color = Color(customThemeLight_Background1),
                                onColorSelected = { customThemeLight_Background1 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_background_3).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_3"),
                                title = stringResource(R.string.color_background_3),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_3),
                                color = Color(customThemeLight_Background2),
                                onColorSelected = { customThemeLight_Background2 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_background_4).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_4"),
                                title = stringResource(R.string.color_background_4),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_4),
                                color = Color(customThemeLight_Background3),
                                onColorSelected = { customThemeLight_Background3 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_background_5).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_5"),
                                title = stringResource(R.string.color_background_5),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_5),
                                color = Color(customThemeLight_Background4),
                                onColorSelected = { customThemeLight_Background4 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_text).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_text"),
                                title = stringResource(R.string.color_text),
                                text = stringResource(R.string.settings_desc_ui_theme_color_text),
                                color = Color(customThemeLight_Text),
                                onColorSelected = { customThemeLight_Text= it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_text_secondary).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_text_secondary"),
                                title = stringResource(R.string.color_text_secondary),
                                text = stringResource(R.string.settings_desc_ui_theme_color_text_secondary),
                                color = Color(customThemeLight_TextSecondary),
                                onColorSelected = { customThemeLight_TextSecondary = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_text_disabled).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_text_disabled"),
                                title = stringResource(R.string.color_text_disabled),
                                text = stringResource(R.string.settings_desc_ui_theme_color_text_disabled),
                                color = Color(customThemeLight_TextDisabled),
                                onColorSelected = { customThemeLight_TextDisabled = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_icon_button_player).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_icon_button_player"),
                                title = stringResource(R.string.color_icon_button_player),
                                text = stringResource(R.string.settings_desc_ui_theme_color_icon_button_player),
                                color = Color(customThemeLight_IconButtonPlayer),
                                onColorSelected = { customThemeLight_IconButtonPlayer = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string._light).contains(search.inputValue, true) || stringResource(R.string.color_accent).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_accent"),
                                title = stringResource(R.string.color_accent),
                                text = stringResource(R.string.settings_desc_ui_theme_color_accent),
                                color = Color(customThemeLight_Accent),
                                onColorSelected = { customThemeLight_Accent = it.hashCode() }
                            )
                        }


                        if (search.inputValue.isBlank() || stringResource(R.string.click_to_reset_default_dark_colors).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true) || stringResource(R.string.title_reset_customized_dark_colors).contains(search.inputValue, true)) {
                            ButtonBarSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.title_reset_customized_dark_colors"),
                                title = stringResource(R.string.title_reset_customized_dark_colors),
                                text = stringResource(R.string.click_to_reset_default_dark_colors),
                                icon = R.drawable.trash,
                                onClick = { resetCustomDarkThemeDialog = true }
                            )
                        }

                        if (search.inputValue.isBlank() || stringResource(R.string.color_background_1).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_1.dark"),
                                title = stringResource(R.string.color_background_1),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_1_dark),
                                color = Color(customThemeDark_Background0),
                                onColorSelected = { customThemeDark_Background0 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_background_2).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_2.dark"),
                                title = stringResource(R.string.color_background_2),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_2_dark),
                                color = Color(customThemeDark_Background1),
                                onColorSelected = { customThemeDark_Background1 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_background_3).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_3.dark"),
                                title = stringResource(R.string.color_background_3),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_3_dark),
                                color = Color(customThemeDark_Background2),
                                onColorSelected = { customThemeDark_Background2 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_background_4).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_4.dark"),
                                title = stringResource(R.string.color_background_4),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_4_dark),
                                color = Color(customThemeDark_Background3),
                                onColorSelected = { customThemeDark_Background3 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_background_5).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_background_5.dark"),
                                title = stringResource(R.string.color_background_5),
                                text = stringResource(R.string.settings_desc_ui_theme_color_background_5_dark),
                                color = Color(customThemeDark_Background4),
                                onColorSelected = { customThemeDark_Background4 = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_text).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_text.dark"),
                                title = stringResource(R.string.color_text),
                                text = stringResource(R.string.settings_desc_ui_theme_color_text_dark),
                                color = Color(customThemeDark_Text),
                                onColorSelected = { customThemeDark_Text= it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_text_secondary).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_text_secondary.dark"),
                                title = stringResource(R.string.color_text_secondary),
                                text = stringResource(R.string.settings_desc_ui_theme_color_text_secondary_dark),
                                color = Color(customThemeDark_TextSecondary),
                                onColorSelected = { customThemeDark_TextSecondary = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_text_disabled).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_text_disabled.dark"),
                                title = stringResource(R.string.color_text_disabled),
                                text = stringResource(R.string.settings_desc_ui_theme_color_text_disabled_dark),
                                color = Color(customThemeDark_TextDisabled),
                                onColorSelected = { customThemeDark_TextDisabled = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_icon_button_player).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_icon_button_player.dark"),
                                title = stringResource(R.string.color_icon_button_player),
                                text = stringResource(R.string.settings_desc_ui_theme_color_icon_button_player_dark),
                                color = Color(customThemeDark_IconButtonPlayer),
                                onColorSelected = { customThemeDark_IconButtonPlayer = it.hashCode() }
                            )
                        }
                        if (search.inputValue.isBlank() || stringResource(R.string.color_accent).contains(search.inputValue, true) || stringResource(R.string.dark).contains(search.inputValue, true) || stringResource(R.string.theme).contains(search.inputValue, true)) {
                            ColorSettingEntry(
                                modifier = Modifier.settingsSearchTarget("ui.theme.color_accent.dark"),
                                title = stringResource(R.string.color_accent),
                                text = stringResource(R.string.settings_desc_ui_theme_color_accent_dark),
                                color = Color(customThemeDark_Accent),
                                onColorSelected = { customThemeDark_Accent = it.hashCode() }
                            )
                        }

                    }
                }

                var showThemeModeDialog by remember { mutableStateOf(false) }
                val themeModeEnabled = when (colorPaletteName) {
                    ColorPaletteName.PureBlack -> false
                    ColorPaletteName.ModernBlack -> false
                    else -> true
                }
                if (search.inputValue.isBlank() || stringResource(R.string.theme_mode).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.theme.theme_mode"),
                        title = stringResource(R.string.theme_mode),
                        text = stringResource(R.string.settings_desc_ui_theme_theme_mode),
                        selectedValueText = colorPaletteMode.text,
                        icon = R.drawable.sparkles,
                        onClick = { if(themeModeEnabled) showThemeModeDialog = true }
                    )
                }

                if (showThemeModeDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.theme_mode),
                        selectedValue = colorPaletteMode,
                        onValueSelected = { colorPaletteMode = it },
                        valueText = { it.text },
                        values = ColorPaletteMode.values().toList(),
                        onDismiss = { showThemeModeDialog = false }
                    )
                }
            }
        )
    }
}
