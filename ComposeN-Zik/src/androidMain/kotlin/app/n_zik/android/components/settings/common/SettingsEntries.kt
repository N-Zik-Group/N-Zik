package app.n_zik.android.components.settings.common

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.it.fast4x.rimusic.enums.ValidationType
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.IDialog
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DialogColorPicker
import app.n_zik.android.components.dialog.common.StringListDialog
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.ui.sliders.Slider
import app.n_zik.android.components.ui.toggles.Switch
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Entry row that opens a dialog to add/remove items of a string list. */
@Composable
inline fun StringListValueSelectorSettingsEntry(
    title: String,
    text: String,
    addTitle: String,
    addPlaceholder: String,
    conflictTitle: String,
    removeTitle: String,
    context: Context,
    list: List<String>,
    crossinline add: (String) -> Unit,
    crossinline remove: (String) -> Unit
) {
    var showStringListDialog by remember {
        mutableStateOf(false)
    }


    if (showStringListDialog) {
        StringListDialog(
            title = title,
            addTitle = addTitle,
            addPlaceholder = addPlaceholder,
            removeTitle = removeTitle,
            conflictTitle = conflictTitle,
            list = list,
            add = add,
            remove = remove,
            onDismiss = { showStringListDialog = false },
        )
    }
    SettingsEntry(
        title = title,
        text = text,
        onClick = {
            showStringListDialog = true
        }
    )
}

/** Generic value selector entry bound to an enum. */
@Composable
inline fun <reified T : Enum<T>> EnumValueSelectorSettingsEntry(
    title: String,
    titleSecondary: String? = null,
    text: String? = null,
    selectedValue: T,
    noinline onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    noinline valueText: @Composable (T) -> String  = { it.name },
    noinline trailingContent: (@Composable () -> Unit) = {}
) {
    ValueSelectorSettingsEntry(
        title = title,
        titleSecondary = titleSecondary,
        text = text,
        selectedValue = selectedValue,
        values = enumValues<T>().toList(),
        onValueSelected = onValueSelected,
        modifier = modifier,
        isEnabled = isEnabled,
        valueText = valueText,
        trailingContent = trailingContent,
    )
}

/** Entry row that opens a value selector dialog over an arbitrary list. */
@Composable
fun <T> ValueSelectorSettingsEntry(
    title: String,
    titleSecondary: String? = null,
    text: String? = null,
    selectedValue: T,
    values: List<T>,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    valueText: @Composable (T) -> String = { it.toString() },
    trailingContent: (@Composable () -> Unit) = {}
) {
    var isShowingDialog by remember {
        mutableStateOf(false)
    }

    if (isShowingDialog) {
        ValueSelectorDialog(
            onDismiss = { isShowingDialog = false },
            title = title,
            selectedValue = selectedValue,
            values = values,
            onValueSelected = onValueSelected,
            valueText = valueText
        )
    }

    SettingsEntry(
        title = title,
        titleSecondary = titleSecondary,
        text = valueText(selectedValue),
        modifier = modifier,
        isEnabled = isEnabled,
        onClick = { isShowingDialog = true },
        trailingContent = trailingContent
    )

    text?.let {
        BasicText(
            text = it,
            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary),
            modifier = Modifier
                .padding(start = 12.dp)
        )
    }
}

/** Icon entry row with a themed toggle switch. */
@Composable
fun OtherSwitchSettingEntry(
    title: String,
    text: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    icon: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clickable(enabled = enabled, onClick = { onCheckedChange(!isChecked) }),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (enabled) 1f else 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        tint = colorPalette().accent,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = title,
                        style = typography().s.semiBold.copy(
                            color = colorPalette().text
                        )
                    )
                    if (text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicText(
                            text = text,
                            style = typography().xs.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }

                // Switch fake Material 3 with theme colors
                Switch(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    checkedThumbColor = colorPalette().textSecondary,
                    checkedTrackColor = colorPalette().accent.copy(alpha = 0.3f),
                    uncheckedThumbColor = colorPalette().textSecondary,
                    uncheckedTrackColor = colorPalette().textSecondary.copy(alpha = 0.3f)
                )

            }
        }
    }
}

/** Plain entry row with a themed toggle switch as trailing content. */
@Composable
fun SwitchSettingEntry(
    title: String,
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    SettingsEntry(
        title = title,
        text = text,
        isEnabled = isEnabled,
        onClick = { onCheckedChange(!isChecked) },
        trailingContent = { Switch(checked = isChecked, onCheckedChange = null) },
        modifier = modifier
    )
}

/** Clickable settings row with a title, optional description and trailing content. */
@Composable
fun SettingsEntry(
    title: String,
    titleSecondary: String? = null,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(uiRoundnessShape()).clickable(enabled = isEnabled, onClick = onClick)
            .alpha(if (isEnabled) 1f else 0.5f)
            .padding(all = 12.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            BasicText(
                text = title,
                style = typography().xs.semiBold.copy(color = colorPalette().text),
                modifier = Modifier
                    .padding(bottom = 4.dp)
            )
            if (text != "")
                BasicText(
                    text = text,
                    style = typography().xs.semiBold.copy(color = colorPalette().textSecondary),
                )
        }

        trailingContent?.invoke()

        if (titleSecondary != null) {
            BasicText(
                text = titleSecondary,
                style = typography().xxs.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Entry row that opens a text input dialog. */
@Composable
fun TextDialogSettingEntry(
    title: String,
    text: String,
    currentText: String,
    onTextSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    validationType: ValidationType = ValidationType.None
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        SettingsInputDialog(
            title = title,
            initialValue = currentText,
            placeholder = title,
            onDismiss = { showDialog = false },
            onSetValue = {
                onTextSave(it)
            }
        ).apply {
            showDialog()
            Render()
        }
    }
    SettingsEntry(
        title = title,
        text = text,
        isEnabled = isEnabled,
        onClick = { showDialog = true },
        trailingContent = { },
        modifier = modifier
    )
}

/** Entry row that opens a color picker; shows a color swatch. */
@Composable
fun ColorSettingEntry(
    title: String,
    text: String,
    color: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SettingsEntry(
        title = title,
        text = text,
        isEnabled = isEnabled,
        onClick = { showColorPicker = true },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(color)
                    .border(BorderStroke(1.dp, Color.LightGray))
            )
        },
        modifier = modifier
    )

    if (showColorPicker)
        DialogColorPicker(onDismiss = { showColorPicker = false }) {
            onColorSelected(it)
            showColorPicker = false
            Toaster.n( R.string.info_color_s_applied, title )
        }

}

/** Entry row with an icon as trailing content instead of an arrow. */
@Composable
fun ButtonBarSettingEntry(
    title: String,
    text: String,
    icon: Int,
    iconSize: Dp = 24.dp,
    iconColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    SettingsEntry(
        title = title,
        text = text,
        isEnabled = isEnabled,
        onClick = onClick,
        trailingContent = {
            Image(
                painter = painterResource(icon),
                colorFilter = ColorFilter.tint(iconColor ?: colorPalette().text),
                modifier = Modifier.size(iconSize),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        },
        modifier = modifier
    )

}

/**
 * Slider entry with a manual value entry dialog; renders an icon row plus the slider.
 */
@Composable
fun SliderSettingsEntry(
    title: String,
    text: String,
    state: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onSlide: (Float) -> Unit = { },
    onSlideComplete: () -> Unit = { },
    toDisplay: @Composable (Float) -> String = { it.toInt().toString() },
    stepSize: Float = 0.1f,
    defaultValue: Float? = null,
    drawValuePoints: Boolean = false,
    isEnabled: Boolean = true,
    usePadding: Boolean = true,
    icon: Int? = null,
    isIntegerOnly: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null
) = Column(modifier = modifier) {

    val manualEnterDialog = object: IDialog {

        var valueFloat: Float by remember( state ) { mutableFloatStateOf( state ) }

        override val dialogTitle: String
            @Composable
            get() = stringResource( R.string.enter_the_value )

        override var isActive: Boolean by rememberSaveable { mutableStateOf(false) }
        override var value: String by remember( valueFloat ) {
            val isInteger = isIntegerOnly || kotlin.math.abs(valueFloat - kotlin.math.round(valueFloat)) < 0.001f
            val formatted = if (isInteger) kotlin.math.round(valueFloat).toInt().toString() else "%.1f".format( valueFloat ).replace(",", ".")
            mutableStateOf( formatted )
        }

        override fun onSet( newValue: String ) {
            val parsed = newValue.replace(",", ".").toFloatOrNull() ?: return
            this.valueFloat = parsed.coerceIn(range.start, range.endInclusive)
            onSlide( this.valueFloat )
            onSlideComplete()

            onDismiss()
        }
    }
    manualEnterDialog.Render()

    if (icon != null) {
        OtherSettingsEntry(
            title = title,
            text = "$text (${toDisplay(state)})",
            icon = icon,
            onClick = manualEnterDialog::onShortClick,
            trailingContent = trailingContent,
            modifier = Modifier.alpha(if (isEnabled) 1f else 0.5f)
        )
    } else {
        SettingsEntry(
            title = title,
            text = "$text (${toDisplay(state)})",
            onClick = manualEnterDialog::onShortClick,
            isEnabled = isEnabled,
            trailingContent = trailingContent
        )
    }

    Slider(
        state = state,
        setState = { value: Float ->
            manualEnterDialog.valueFloat = value
            onSlide(value)
        },
        onSlideComplete = onSlideComplete,
        range = range,
        stepSize = stepSize,
        defaultValue = defaultValue,
        drawValuePoints = drawValuePoints,
        modifier = Modifier
            .height(36.dp)
            .alpha(if (isEnabled) 1f else 0.5f)
            .let { if (usePadding) it.padding(horizontal = 16.dp) else it }
            .padding(vertical = 16.dp)
            .fillMaxWidth()
    )
}

/** Icon entry row with a chevron arrow, for navigation-style settings. */
@Composable
fun OtherSettingsEntry(
    title: String,
    text: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        tint = colorPalette().accent,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = title,
                        style = typography().s.semiBold.copy(
                            color = colorPalette().text
                        )
                    )
                    if (text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicText(
                            text = text,
                            style = typography().xs.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }

                if (trailingContent != null) {
                    trailingContent()
                }

                // Arrow indicator
                Icon(
                    painter = painterResource(R.drawable.chevron_forward),
                    tint = colorPalette().textSecondary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Icon entry row that opens an enum value selector dialog. */
@Composable
inline fun <reified T : Enum<T>> OtherEnumValueSelectorSettingsEntry(
    title: String,
    icon: Int,
    text: String? = null,
    selectedValue: T,
    noinline onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    noinline trailingContent: (@Composable () -> Unit)? = null,
    noinline valueText: @Composable (T) -> String  = { it.name }
) {
    var isShowingDialog by remember {
        mutableStateOf(false)
    }

    if (isShowingDialog) {
        ValueSelectorDialog(
            onDismiss = { isShowingDialog = false },
            title = title,
            selectedValue = selectedValue,
            values = enumValues<T>().toList(),
            onValueSelected = {
                onValueSelected(it)
                isShowingDialog = false
            },
            valueText = valueText
        )
    }

    OtherSettingsEntry(
        title = title,
        text = text ?: valueText(selectedValue),
        icon = icon,
        modifier = modifier,
        trailingContent = trailingContent,
        onClick = { isShowingDialog = true }
    )
}

/** Icon entry row for display-only (non-clickable) information. */
@Composable
fun OtherInfoSettingsEntry(
    title: String,
    text: String,
    icon: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape()),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        tint = colorPalette().accent,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = title,
                        style = typography().s.semiBold.copy(
                            color = colorPalette().text
                        )
                    )
                    if (text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicText(
                            text = text,
                            style = typography().xs.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

/** Icon entry row with a trash action and a chevron arrow. */
@Composable
fun CacheSettingsEntry(
    title: String,
    text: String,
    icon: Int,
    onClick: () -> Unit,
    onTrashClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        tint = colorPalette().accent,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = title,
                        style = typography().s.semiBold.copy(
                            color = colorPalette().text
                        )
                    )
                    if (text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicText(
                            text = text,
                            style = typography().xs.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }

                // Trash button
                HeaderIconButton(
                    icon = R.drawable.trash,
                    enabled = true,
                    color = colorPalette().text,
                    onClick = onTrashClick
                )

                // Arrow indicator
                Icon(
                    painter = painterResource(R.drawable.chevron_forward),
                    tint = colorPalette().textSecondary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Compact icon entry row with title, text and a chevron arrow. */
@Composable
fun ModernSettingsEntry(
    title: String,
    text: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clickable(onClick = onClick)
            .padding(12.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        tint = colorPalette().accent,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = title,
                        style = typography().xs.semiBold.copy(color = colorPalette().text),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    if (text.isNotEmpty()) {
                        BasicText(
                            text = text,
                            style = typography().xxs.secondary.copy(color = colorPalette().textSecondary),
                        )
                    }
                }

                // Arrow indicator
                Icon(
                    painter = painterResource(R.drawable.chevron_forward),
                    tint = colorPalette().textSecondary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
    }
}
