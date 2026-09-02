package app.n_zik.android.components.menu.player

import app.n_zik.android.core.database.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.ui.sliders.SliderControl
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.setGlobalVolume
import app.n_zik.android.isBassBoostEnabled
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.bassboostLevelKey
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.blurStrengthKey
import app.it.fast4x.rimusic.utils.getDeviceVolume
import app.it.fast4x.rimusic.utils.loudnessBaseGainKey
import app.it.fast4x.rimusic.utils.playbackDurationKey
import app.it.fast4x.rimusic.utils.playbackDeviceVolumeKey
import app.it.fast4x.rimusic.utils.playbackPitchKey
import app.it.fast4x.rimusic.utils.playbackSpeedKey
import app.it.fast4x.rimusic.utils.playbackVolumeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.setDeviceVolume
import app.it.fast4x.rimusic.utils.volumeBoostLevelKey
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.n_zik.android.uiRoundnessShape
import androidx.media3.common.PlaybackParameters
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.BasicText
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent

@UnstableApi
class PlaybackSettingsMenu private constructor(
    private val binder: PlayerServiceModern.Binder,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>,
    private val onDismiss: () -> Unit,
    private val onBlurScaleChange: (Float) -> Unit
) : Menu {

    companion object {
        fun create(
            binder: PlayerServiceModern.Binder,
            menuState: MenuState,
            styleState: MutableState<MenuStyle>,
            onDismiss: () -> Unit,
            onBlurScaleChange: (Float) -> Unit
        ): PlaybackSettingsMenu =
            PlaybackSettingsMenu(
                binder = binder,
                menuState = menuState,
                styleState = styleState,
                onDismiss = onDismiss,
                onBlurScaleChange = onBlurScaleChange
            )
    }

    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() {
        val context = LocalContext.current

        var playbackSpeed by rememberPreference(playbackSpeedKey, 1f)
        var playbackPitch by rememberPreference(playbackPitchKey, 1f)
        var playbackVolume by rememberPreference(playbackVolumeKey, 1f)
        var playbackDeviceVolume by rememberPreference(playbackDeviceVolumeKey, getDeviceVolume(context))
        var playbackDuration by rememberPreference(playbackDurationKey, 0f)

        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                        context?.let { playbackDeviceVolume = getDeviceVolume(it) }
                    }
                }
            }
            context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
            onDispose { context.unregisterReceiver(receiver) }
        }
        var blurStrength by rememberPreference(blurStrengthKey, 25f)
        var bassBoost by rememberPreference(bassboostLevelKey, 0f)
        val volumeNormalization by rememberPreference(volumeNormalizationKey, false)
        var loudnessBaseGain by rememberPreference(loudnessBaseGainKey, 0f)
        var volumeBoostLevel by rememberPreference(volumeBoostLevelKey, 0f)

        ListMenu.Menu(title = stringResource(R.string.controls_header_customize)) {
            // Section: Playback
            SectionTitle(stringResource(R.string.playback))

            // Speed
            ListSliderMenuItem(
                icon = R.drawable.slow_motion,
                title = stringResource(R.string.controls_title_playback_speed),
                value = playbackSpeed,
                onValueChange = {
                    val rounded = kotlin.math.round(it * 10f) / 10f
                    playbackSpeed = rounded
                    binder.player.playbackParameters =
                        PlaybackParameters(rounded, playbackPitch)
                },
                valueRange = 0.1f..10f,
                displayValue = { "%.1fx".format(it).replace(",", ".") },
                stepSize = 0f,
                defaultValue = 1f,
                drawValuePoints = true,
                onReset = {
                    playbackSpeed = 1f
                    binder.player.playbackParameters =
                        PlaybackParameters(playbackSpeed, playbackPitch)
                }
            )

            // Pitch
            ListSliderMenuItem(
                icon = R.drawable.equalizer,
                title = stringResource(R.string.controls_title_playback_pitch),
                value = playbackPitch,
                onValueChange = {
                    val rounded = kotlin.math.round(it * 10f) / 10f
                    playbackPitch = rounded
                    binder.player.playbackParameters =
                        PlaybackParameters(playbackSpeed, rounded)
                },
                valueRange = 0.1f..5f,
                displayValue = { "%.1fx".format(it).replace(",", ".") },
                stepSize = 0f,
                defaultValue = 1f,
                drawValuePoints = true,
                onReset = {
                    playbackPitch = 1f
                    binder.player.playbackParameters =
                        PlaybackParameters(playbackSpeed, playbackPitch)
                }
            )

            // Medley Duration
            ListSliderMenuItem(
                icon = R.drawable.playbackduration,
                title = stringResource(R.string.controls_title_medley_duration),
                value = playbackDuration,
                onValueChange = { playbackDuration = kotlin.math.round(it) },
                valueRange = 0f..60f,
                displayValue = { "%.0f".format(it) },
                stepSize = 0f,
                defaultValue = 0f,
                drawValuePoints = true,
                onReset = { playbackDuration = 0f }
            )

            // Section: Volume
            SectionTitle(stringResource(R.string.volume))

            // Playback Volume
            ListSliderMenuItem(
                icon = R.drawable.volume_up,
                title = stringResource(R.string.controls_title_playback_volume),
                value = playbackVolume,
                onValueChange = {
                    val rounded = kotlin.math.round(it * 100f) / 100f
                    playbackVolume = rounded
                    binder.player.volume = playbackVolume
                    binder.player.setGlobalVolume(playbackVolume)
                },
                valueRange = 0f..1f,
                displayValue = { "${(it * 100).toInt()}%" },
                stepSize = 0f,
                defaultValue = 1f,
                drawValuePoints = true,
                onReset = {
                    playbackVolume = 1f
                    binder.player.volume = playbackVolume
                    binder.player.setGlobalVolume(playbackVolume)
                }
            )

            // Device Volume
            ListSliderMenuItem(
                icon = R.drawable.master_volume,
                title = stringResource(R.string.controls_title_device_volume),
                value = playbackDeviceVolume,
                onValueChange = {
                    val rounded = kotlin.math.round(it * 100f) / 100f
                    playbackDeviceVolume = rounded
                    setDeviceVolume(context, playbackDeviceVolume)
                },
                valueRange = 0f..1f,
                displayValue = { "${(it * 100).toInt()}%" },
                stepSize = 0f,
                onReset = {
                    playbackDeviceVolume = getDeviceVolume(context)
                    setDeviceVolume(context, playbackDeviceVolume)
                }
            )

            // Section: Player Effects
            SectionTitle(stringResource(R.string.player_effects))

            // Blur Effect
            ListSliderMenuItem(
                icon = R.drawable.droplet,
                title = stringResource(R.string.controls_title_blur_effect),
                value = blurStrength,
                onValueChange = {
                    blurStrength = kotlin.math.round(it)
                    onBlurScaleChange(blurStrength)
                },
                valueRange = 0f..50f,
                displayValue = { "${it.toInt()}" },
                stepSize = 0f,
                defaultValue = 25f,
                drawValuePoints = true,
                onReset = { blurStrength = 25f }
            )

            // Section: Effects
            SectionTitle(stringResource(R.string.audio_effects))

            // Bass Boost
            ListSliderMenuItem(
                icon = R.drawable.musical_notes,
                title = stringResource(R.string.settings_bass_boost_level),
                value = bassBoost,
                onValueChange = { bassBoost = kotlin.math.round(it * 10000f) / 10000f },
                valueRange = 0f..1f,
                displayValue = { "%.2f dB".format(it * 15f).replace(",", ".") },
                stepSize = 0f,
                defaultValue = 0f,
                drawValuePoints = true,
                onReset = { bassBoost = 0f },
                isEnabled = isBassBoostEnabled()
            )

            // Loudness Base Gain
            var tempLoudnessGain by remember { mutableFloatStateOf(loudnessBaseGain) }
            ListSliderMenuItem(
                icon = R.drawable.volume_up,
                title = stringResource(R.string.settings_loudness_base_gain),
                value = tempLoudnessGain,
                onValueChange = { tempLoudnessGain = it },
                onSlideComplete = { loudnessBaseGain = tempLoudnessGain },
                valueRange = -20f..20f,
                displayValue = { "%.2f dB".format(it) },
                onReset = {
                    tempLoudnessGain = 0f
                    loudnessBaseGain = 0f
                },
                isEnabled = volumeNormalization,
                stepSize = 0f,
                defaultValue = 0f,
                drawValuePoints = true
            )

            // Volume Boost Level
            var tempVolumeBoost by remember { mutableFloatStateOf(volumeBoostLevel) }
            ListSliderMenuItem(
                icon = R.drawable.volume_up,
                title = stringResource(R.string.loudness_boost_level),
                value = tempVolumeBoost,
                onValueChange = { tempVolumeBoost = it },
                onSlideComplete = { volumeBoostLevel = tempVolumeBoost },
                valueRange = -30f..30f,
                displayValue = { "%.2f dB".format(it) },
                onReset = {
                    tempVolumeBoost = 0f
                    volumeBoostLevel = 0f
                },
                isEnabled = volumeNormalization,
                stepSize = 0f,
                defaultValue = 0f,
                drawValuePoints = true
            )
        }
    }

    @Composable
    override fun GridMenu() {
        val context = LocalContext.current

        var playbackSpeed by rememberPreference(playbackSpeedKey, 1f)
        var playbackPitch by rememberPreference(playbackPitchKey, 1f)
        var playbackVolume by rememberPreference(playbackVolumeKey, 1f)
        var playbackDeviceVolume by rememberPreference(playbackDeviceVolumeKey, getDeviceVolume(context))
        var playbackDuration by rememberPreference(playbackDurationKey, 0f)
        var blurStrength by rememberPreference(blurStrengthKey, 25f)
        var bassBoost by rememberPreference(bassboostLevelKey, 0f)
        val volumeNormalization by rememberPreference(volumeNormalizationKey, false)
        var loudnessBaseGain by rememberPreference(loudnessBaseGainKey, 0f)
        var volumeBoostLevel by rememberPreference(volumeBoostLevelKey, 0f)

        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                        context?.let { playbackDeviceVolume = getDeviceVolume(it) }
                    }
                }
            }
            context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
            onDispose { context.unregisterReceiver(receiver) }
        }

        var tempLoudnessGain by remember { mutableFloatStateOf(loudnessBaseGain) }
        var tempVolumeBoost by remember { mutableFloatStateOf(volumeBoostLevel) }

        GridMenu.Menu(title = stringResource(R.string.controls_header_customize), showDragHandle = true) {
            // Section: Playback
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.playback)) }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.controls_title_playback_speed),
                    icon = R.drawable.slow_motion,
                    value = playbackSpeed,
                    toDisplay = { "%.1fx".format(it).replace(",", ".") },
                    onValueChange = {
                        val rounded = kotlin.math.round(it * 10f) / 10f
                        playbackSpeed = rounded
                        binder.player.playbackParameters =
                            PlaybackParameters(rounded, playbackPitch)
                    },
                    valueRange = 0.1f..10f,
                    stepSize = 0f,
                    defaultValue = 1f,
                    drawValuePoints = true,
                    onReset = {
                        playbackSpeed = 1f
                        binder.player.playbackParameters =
                            PlaybackParameters(1f, playbackPitch)
                    }
                )
            }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.controls_title_playback_pitch),
                    icon = R.drawable.equalizer,
                    value = playbackPitch,
                    toDisplay = { "%.1fx".format(it).replace(",", ".") },
                    onValueChange = {
                        val rounded = kotlin.math.round(it * 10f) / 10f
                        playbackPitch = rounded
                        binder.player.playbackParameters =
                            PlaybackParameters(playbackSpeed, rounded)
                    },
                    valueRange = 0.1f..5f,
                    stepSize = 0f,
                    defaultValue = 1f,
                    drawValuePoints = true,
                    onReset = {
                        playbackPitch = 1f
                        binder.player.playbackParameters =
                            PlaybackParameters(playbackSpeed, 1f)
                    }
                )
            }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.controls_title_medley_duration),
                    icon = R.drawable.playbackduration,
                    value = playbackDuration,
                    toDisplay = { "${it.toInt()}" },
                    onValueChange = { playbackDuration = kotlin.math.round(it) },
                    valueRange = 0f..60f,
                    stepSize = 0f,
                    defaultValue = 0f,
                    drawValuePoints = true,
                    onReset = { playbackDuration = 0f }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.volume)) }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.controls_title_playback_volume),
                    icon = R.drawable.volume_up,
                    value = playbackVolume,
                    toDisplay = { "${(it * 100).toInt()}%" },
                    onValueChange = {
                        val rounded = kotlin.math.round(it * 100f) / 100f
                        playbackVolume = rounded
                        binder.player.volume = playbackVolume
                        binder.player.setGlobalVolume(playbackVolume)
                    },
                    valueRange = 0f..1f,
                    stepSize = 0f,
                    defaultValue = 1f,
                    drawValuePoints = true,
                    onReset = {
                        playbackVolume = 1f
                        binder.player.volume = 1f
                        binder.player.setGlobalVolume(1f)
                    }
                )
            }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.controls_title_device_volume),
                    icon = R.drawable.master_volume,
                    value = playbackDeviceVolume,
                    toDisplay = { "${(it * 100).toInt()}%" },
                    onValueChange = {
                        val rounded = kotlin.math.round(it * 100f) / 100f
                        playbackDeviceVolume = rounded
                        setDeviceVolume(context, playbackDeviceVolume)
                    },
                    valueRange = 0f..1f,
                    stepSize = 0f,
                    onReset = {
                        playbackDeviceVolume = getDeviceVolume(context)
                        setDeviceVolume(context, playbackDeviceVolume)
                    }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.player_effects)) }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.controls_title_blur_effect),
                    icon = R.drawable.droplet,
                    value = blurStrength,
                    toDisplay = { "${it.toInt()}" },
                    onValueChange = {
                        blurStrength = kotlin.math.round(it)
                        onBlurScaleChange(blurStrength)
                    },
                    valueRange = 0f..50f,
                    stepSize = 0f,
                    defaultValue = 25f,
                    drawValuePoints = true,
                    onReset = { blurStrength = 25f }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.audio_effects)) }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.settings_bass_boost_level),
                    icon = R.drawable.musical_notes,
                    value = bassBoost,
                    toDisplay = { "%.2f dB".format(it * 15f).replace(",", ".") },
                    onValueChange = { bassBoost = kotlin.math.round(it * 10000f) / 10000f },
                    valueRange = 0f..1f,
                    stepSize = 0f,
                    defaultValue = 0f,
                    drawValuePoints = true,
                    onReset = { bassBoost = 0f },
                    isEnabled = isBassBoostEnabled()
                )
            }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.settings_loudness_base_gain),
                    icon = R.drawable.volume_up,
                    value = tempLoudnessGain,
                    toDisplay = { "%.2f dB".format(it) },
                    onValueChange = { tempLoudnessGain = kotlin.math.round(it * 100f) / 100f },
                    onSlideComplete = { loudnessBaseGain = tempLoudnessGain },
                    valueRange = -20f..20f,
                    onReset = {
                        tempLoudnessGain = 0f
                        loudnessBaseGain = 0f
                    },
                    isEnabled = volumeNormalization,
                    stepSize = 0f,
                    defaultValue = 0f,
                    drawValuePoints = true
                )
            }

            item {
                SliderGridEntry(
                    title = stringResource(R.string.loudness_boost_level),
                    icon = R.drawable.volume_up,
                    value = tempVolumeBoost,
                    toDisplay = { "%.2f dB".format(it).replace(",", ".") },
                    onValueChange = { tempVolumeBoost = kotlin.math.round(it * 100f) / 100f },
                    onSlideComplete = { volumeBoostLevel = tempVolumeBoost },
                    valueRange = -30f..30f,
                    onReset = {
                        tempVolumeBoost = 0f
                        volumeBoostLevel = 0f
                    },
                    isEnabled = volumeNormalization,
                    stepSize = 0f,
                    defaultValue = 0f,
                    drawValuePoints = true
                )
            }
        }
    }

    @Composable
    override fun MenuComponent() {
        if (menuStyle == MenuStyle.List)
            ListMenu()
        else
            GridMenu()
    }

    @Composable
    private fun SectionTitle(title: String) {
        BasicText(
            text = title,
            style = typography().xxs.semiBold.copy(
                color = colorPalette().accent,
                textAlign = TextAlign.Start
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp)
        )
    }

    @Composable
    private fun SettingIcon(icon: Int) {
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
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ListSliderMenuItem(
        icon: Int,
        title: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        onSlideComplete: () -> Unit = {},
        valueRange: ClosedFloatingPointRange<Float>,
        displayValue: @Composable (Float) -> String,
        onReset: () -> Unit,
        isEnabled: Boolean = true,
        stepSize: Float = 0.1f,
        defaultValue: Float? = null,
        drawValuePoints: Boolean = false
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .let { if (!isEnabled) it.then(Modifier.alpha(0.5f)) else it }
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = colorPalette().accent.copy(alpha = 0.1f),
                        shape = uiRoundnessShape()
                    )
                    .clip(uiRoundnessShape())
                    .combinedClickable(
                        onLongClick = onReset,
                        onClick = {}
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

            val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)

            BasicText(
                text = title,
                maxLines = 1,
                style = typography().s.semiBold.copy(color = colorPalette().text),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
                    .conditional(!isScrollingTextDisabled) {
                        basicMarquee(iterations = Int.MAX_VALUE)
                    }
            )

            BasicText(
                text = displayValue(value),
                style = typography().xxs.semiBold.copy(color = colorPalette().accent),
                modifier = Modifier.padding(end = 8.dp)
            )

            SliderControl(
                state = value,
                onSlide = { if (isEnabled) onValueChange(it) },
                onSlideComplete = onSlideComplete,
                toDisplay = displayValue,
                range = valueRange,
                stepSize = stepSize,
                defaultValue = defaultValue,
                drawValuePoints = drawValuePoints,
                showValue = false,
                modifier = Modifier.weight(1.5f)
            )
        }
    }


    @Composable
    private fun SliderGridEntry(
        title: String,
        icon: Int,
        value: Float,
        toDisplay: @Composable (Float) -> String,
        onValueChange: (Float) -> Unit,
        onSlideComplete: () -> Unit = {},
        valueRange: ClosedFloatingPointRange<Float>,
        onReset: () -> Unit,
        isEnabled: Boolean = true,
        stepSize: Float = 0.1f,
        defaultValue: Float? = null,
        drawValuePoints: Boolean = false
    ) {
        var isShowingDialog by remember { mutableStateOf(false) }

        if (isShowingDialog) {
            SliderDialog(
                title = title,
                icon = icon,
                value = value,
                valueRange = valueRange,
                stepSize = stepSize,
                defaultValue = defaultValue,
                drawValuePoints = drawValuePoints,
                onValueChange = onValueChange,
                onSlideComplete = onSlideComplete,
                onReset = onReset,
                toDisplay = toDisplay,
                onDismiss = { isShowingDialog = false }
            )
        }

        GridMenu.Entry(
            text = title,
            icon = { SettingIcon(icon) },
            subtitle = toDisplay(value),
            onClick = { isShowingDialog = true },
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

    @Composable
    private fun SliderDialog(
        title: String,
        icon: Int,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        stepSize: Float = 0.1f,
        defaultValue: Float? = null,
        drawValuePoints: Boolean = false,
        onValueChange: (Float) -> Unit,
        onSlideComplete: () -> Unit = {},
        onReset: () -> Unit,
        toDisplay: @Composable (Float) -> String = { it.toInt().toString() },
        onDismiss: () -> Unit
    ) {
        var currentValue by remember { mutableFloatStateOf(value) }

        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorPalette().background1)
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingIcon(icon)
                    Spacer(modifier = Modifier.size(12.dp))
                    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)

                    BasicText(
                        text = title,
                        maxLines = 1,
                        style = typography().s.semiBold.copy(
                            color = colorPalette().text
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .conditional(!isScrollingTextDisabled) {
                                basicMarquee(iterations = Int.MAX_VALUE)
                            }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Value display
                BasicText(
                    text = toDisplay(currentValue),
                    style = typography().l.semiBold.copy(
                        color = colorPalette().accent
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Slider
                SliderControl(
                    state = currentValue,
                    onSlide = { currentValue = it },
                    onSlideComplete = {
                        onValueChange(currentValue)
                        onSlideComplete()
                    },
                    toDisplay = toDisplay,
                    range = valueRange,
                    stepSize = stepSize,
                    defaultValue = defaultValue,
                    drawValuePoints = drawValuePoints,
                    showValue = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            onReset()
                            onDismiss()
                        },
                        icon = R.drawable.refresh,
                        color = colorPalette().textSecondary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onDismiss,
                        icon = R.drawable.checkmark,
                        color = colorPalette().accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}