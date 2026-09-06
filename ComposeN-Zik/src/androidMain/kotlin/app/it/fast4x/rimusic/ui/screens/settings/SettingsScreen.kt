package app.it.fast4x.rimusic.ui.screens.settings

import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.ValidationType
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.ui.components.themed.DialogColorPicker
import app.it.fast4x.rimusic.ui.components.themed.IDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.ui.sliders.Slider
import app.it.fast4x.rimusic.ui.components.themed.StringListDialog
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.n_zik.android.components.ui.toggles.Switch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.uiRoundnessShape


@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun SettingsScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
) {
    //val context = LocalContext.current
    val saveableStateHolder = rememberSaveableStateHolder()

    val (tabIndex, onTabChanged) = rememberSaveable {
        mutableIntStateOf(0)
    }

    val trigger = encryptedPreferencesUpdateTrigger
    var ytLoggedIn by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        ytLoggedIn = withContext(Dispatchers.IO) {
            isYouTubeLoginEnabled() && isYouTubeLoggedIn()
        }
    }

    Skeleton(
        navController,
        tabIndex,
        onTabChanged,
        miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.tab_general), R.drawable.ic_launcher_monochrome)
            item(1, stringResource(R.string.ui_tab), R.drawable.ui)
            item(2, stringResource(R.string.player_appearance), R.drawable.color_palette)
            item(3, if (!ytLoggedIn) stringResource(R.string.ai_recommendations)
            else stringResource(R.string.home), if (!ytLoggedIn) R.drawable.sparkles
            else R.drawable.ytmusic)
            item(4, stringResource(R.string.tab_data), R.drawable.server)
            item(5, stringResource(R.string.tab_accounts), R.drawable.person)
            item(6, stringResource(R.string.tab_network), R.drawable.network)
            item(7, stringResource(R.string.tab_miscellaneous), R.drawable.equalizer)
            item(8, stringResource(R.string.about), R.drawable.information)

        }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(currentTabIndex) {
            when (currentTabIndex) {
                0 -> GeneralSettings(navController = navController)
                1 -> UiSettings(navController = navController)
                2 -> AppearanceSettings(navController = navController)
                3 -> AIRecommendationSettings(navController = navController, ytLoggedIn = ytLoggedIn)
                4 -> DataSettings()
                5 -> AccountsSettings()
                6 -> NetworkSettings(navController = navController)
                7 -> OtherSettings()
                8 -> About(navController = navController)

            }
        }
    }

    RestartAppDialog.Render()
}

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


@Composable
fun OtherSwitchSettingEntry(
    title: String,
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: Int,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clip(uiRoundnessShape()).clickable(onClick = { onCheckedChange(!isChecked) }),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
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
            //.padding(start = 16.dp)
            //.padding(all = 16.dp)
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
                //modifier = Modifier
                //    .padding(vertical = 8.dp, horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun SettingsTopDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = typography().xs.secondary,
        modifier = modifier
            .padding(start = 12.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsDescription(
    text: String,
    modifier: Modifier = Modifier,
    important: Boolean = false,
    textAlign: TextAlign? = null,
) {
    if (textAlign != null) {
        Text(
            text = text,
            style = if (important) typography().xxs.semiBold.color(colorPalette().red)
            else typography().xxs.secondary,
            textAlign = textAlign,
            modifier = modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )
    } else {
        BasicText(
            text = text,
            style = if (important) typography().xxs.semiBold.color(colorPalette().red)
            else typography().xxs.secondary,
            modifier = modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
fun ImportantSettingsDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = typography().xxs.semiBold.color(colorPalette().red),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsEntryGroupText(
    title: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = title.uppercase(),
        style = typography().xs.semiBold.copy(colorPalette().accent),
        modifier = modifier
            .padding(start = 12.dp)
            //.padding(horizontal = 12.dp)
    )
}

@Composable
fun SettingsGroupSpacer(
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
    )
}


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
    //val context = LocalContext.current

    if (showDialog) {
        SettingsInputDialog(
            title = title,
            initialValue = currentText,
            placeholder = title,
            onDismiss = { showDialog = false },
            onSetValue = {
                onTextSave(it)
                //context.toast(context.getString(R.string.toast_preference_saved))
            }
        ).apply {
            showDialog()
            Render()
        }
        /*
        TextFieldDialog(hintText = title ,
            onDismiss = { showDialog = false },
            onDone ={ value ->
                onTextSave(value)
                //context.toast(context.getString(R.string.toast_preference_saved))
            },
            //doneText = "Save",
            initialTextInput = currentText
        )
         */
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
            //usePadding = usePadding
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

@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    description: String? = null,
    important: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) = Column(modifier = modifier) {
    if (title != null) {
        SettingsEntryGroupText(title = title)
    }

    description?.let { description ->
        SettingsDescription(
            text = description,
            important = important
        )
    }

    content()

    SettingsGroupSpacer()
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: Int,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    
    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = uiRoundnessShape(),
                    spotColor = colorPalette().accent.copy(alpha = 0.2f)
                ),
            shape = uiRoundnessShape(),
            colors = CardDefaults.cardColors(
                containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                    Color(0xFF1A1A1A) // Gray dark for pitch black themes
                } else {
                    colorPalette().background1
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Section Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
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
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    BasicText(
                        text = title,
                        style = typography().xs.semiBold.copy(
                            color = colorPalette().accent
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
    
                if (description != null) {
                    BasicText(
                        text = description,
                        style = typography().xxs.copy(
                            color = colorPalette().textSecondary
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
    
                // Content
                content()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}


@Composable
fun OtherSettingsEntry(
    title: String,
    text: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clip(uiRoundnessShape()).clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
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



@Composable
fun CacheSettingsEntry(
    title: String,
    text: String,
    icon: Int,
    onClick: () -> Unit,
    onTrashClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(uiRoundnessShape())
            .clip(uiRoundnessShape()).clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
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
            .clip(uiRoundnessShape()).clickable(onClick = onClick)
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








