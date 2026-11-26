package app.n_zik.android.components.menu.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.PressFeedbackHorizontalInset

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.glass.NativeIconActionSize
import app.n_zik.android.components.glass.TextContrast
import app.n_zik.android.components.glass.surfaceGlyphColor
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.menuToggleSemantics
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.components.ui.iconBadgeGlyphColor
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.semiBold

@Composable
internal fun SettingIcon(icon: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .iconBadgeBackground(
                color = colorPalette().accent.copy(alpha = 0.1f),
                shape = uiRoundnessShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            tint = iconBadgeGlyphColor(colorPalette().accent),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun SectionTitle(title: String) {
    BasicText(
        text = title,
        style = typography().xxs.semiBold.copy(
            color = surfaceGlyphColor(colorPalette().accent, minimum = TextContrast),
            textAlign = TextAlign.Start
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = PressFeedbackHorizontalInset)
    )
}

@Composable
internal fun LyricsSettingsMenu.ToggleSettingEntry(
    title: String,
    icon: Int,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    if (menuStyle == MenuStyle.List) {
        ListMenu.Entry(
            text = title,
            icon = { SettingIcon(icon) },
            subtitle = subtitle,
            modifier = Modifier.menuToggleSemantics(isChecked),
            onClick = { onCheckedChange(!isChecked) },
            trailingContent = {
                Switch(
                    checked = isChecked,
                    onCheckedChange = null,
                    modifier = Modifier.scale(0.8f),
                    checkedThumbColor = colorPalette().textSecondary,
                    checkedTrackColor = colorPalette().accent.copy(alpha = 0.3f),
                    uncheckedThumbColor = colorPalette().textSecondary,
                    uncheckedTrackColor = colorPalette().textSecondary.copy(alpha = 0.3f)
                )
            }
        )
    } else {
        GridMenu.Entry(
            text = title,
            icon = { SettingIcon(icon) },
            subtitle = subtitle,
            modifier = Modifier.menuToggleSemantics(isChecked),
            onClick = { onCheckedChange(!isChecked) },
            trailingContent = {
                Switch(
                    checked = isChecked,
                    onCheckedChange = null,
                    modifier = Modifier.scale(0.8f),
                    checkedThumbColor = colorPalette().textSecondary,
                    checkedTrackColor = colorPalette().accent.copy(alpha = 0.3f),
                    uncheckedThumbColor = colorPalette().textSecondary,
                    uncheckedTrackColor = colorPalette().textSecondary.copy(alpha = 0.3f)
                )
            }
        )
    }
}

@Composable
internal fun LyricsSettingsMenu.ActionSettingEntry(
    title: String,
    icon: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    if (menuStyle == MenuStyle.List) {
        ListMenu.Entry(
            text = title,
            icon = { SettingIcon(icon) },
            enabled = enabled,
            onClick = onClick,
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.chevron_forward),
                    tint = colorPalette().textSecondary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
    } else {
        GridMenu.Entry(
            text = title,
            icon = { SettingIcon(icon) },
            enabled = enabled,
            onClick = onClick,
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.chevron_forward),
                    tint = colorPalette().textSecondary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
    }
}

@Composable
internal fun <T : Enum<T>> LyricsSettingsMenu.EnumSettingEntry(
    title: String,
    icon: Int,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    onValueSelected: (T) -> Unit,
    trailingContent: @Composable () -> Unit = {
        Icon(
            painter = painterResource(R.drawable.chevron_forward),
            tint = colorPalette().textSecondary,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
) {
    var isShowingDialog by remember { mutableStateOf(false) }

    DialogPresence(visible = isShowingDialog) {
        ValueSelectorDialog(
            onDismiss = { isShowingDialog = false },
            title = title,
            selectedValue = selectedValue,
            values = values,
            onValueSelected = {
                onValueSelected(it)
                isShowingDialog = false
            },
            valueText = valueText
        )
    }

    if (menuStyle == MenuStyle.List) {
        ListMenu.Entry(
            text = title,
            icon = { SettingIcon(icon) },
            subtitle = valueText(selectedValue),
            onClick = { isShowingDialog = true },
            trailingContent = trailingContent
        )
    } else {
        GridMenu.Entry(
            text = title,
            icon = { SettingIcon(icon) },
            subtitle = valueText(selectedValue),
            onClick = { isShowingDialog = true },
            trailingContent = trailingContent
        )
    }
}

@Composable
private fun LyricsType.label(): String = when (this) {
    LyricsType.Karaoke -> stringResource(R.string.lyrics_mode_karaoke_label)
    LyricsType.Synced -> stringResource(R.string.lyrics_mode_synchronized_label)
    LyricsType.Unsynced -> stringResource(R.string.lyrics_mode_unsynchronized_label)
    LyricsType.Auto -> stringResource(R.string.lyrics_mode_automatic_label)
}

@Composable
internal fun LyricsSettingsMenu.LyricsTypeEntry(
    selected: LyricsType,
    onValueSelected: (LyricsType) -> Unit,
) {
    EnumSettingEntry(
        title = stringResource(R.string.recommendations_show_section_label),
        icon = R.drawable.time,
        selectedValue = selected,
        values = LyricsType.entries.toList(),
        valueText = { it.label() },
        onValueSelected = onValueSelected
    )
}

@Composable
internal fun LyricsSettingsMenu.LyricsCurrentTypeEntry(actualType: LyricsType?, label: String) {
    val actualStateString = actualType?.label()
    AnimatedContent(
        targetState = actualStateString,
        label = label
    ) { stateString ->
        if (stateString != null) {
            ActionSettingEntry(
                title = stringResource(R.string.lyrics_current_state_format, stateString),
                icon = R.drawable.information,
                enabled = false,
                onClick = {}
            )
        }
    }
}

@Composable
internal fun LyricsSettingsMenu.LyricsFontSizeEntry(
    selected: LyricsFontSize,
    onValueSelected: (LyricsFontSize) -> Unit,
    onShowLyricsSizeDialog: () -> Unit,
) {
    EnumSettingEntry(
        title = stringResource(R.string.lyrics_size),
        icon = R.drawable.text,
        selectedValue = selected,
        values = LyricsFontSize.entries.toList(),
        valueText = { value ->
            when (value) {
                LyricsFontSize.Light -> stringResource(R.string.lyrics_font_size_small_label)
                LyricsFontSize.Medium -> stringResource(R.string.common_size_medium)
                LyricsFontSize.Heavy -> stringResource(R.string.lyrics_font_size_large_label)
                LyricsFontSize.Large -> stringResource(R.string.lyrics_font_size_extra_large_label)
                LyricsFontSize.Custom -> stringResource(R.string.common_option_custom)
            }
        },
        onValueSelected = onValueSelected,
        trailingContent = {
            if (selected == LyricsFontSize.Custom) {
                Box(
                    modifier = Modifier
                        .size(NativeIconActionSize)
                        .clip(uiRoundnessShape())
                        .iconBadgeBackground(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape())
                        .clickable(
                            role = Role.Button,
                            onClick = onShowLyricsSizeDialog,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.text),
                        tint = iconBadgeGlyphColor(colorPalette().accent),
                        contentDescription = stringResource(R.string.lyrics_size),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )
}
