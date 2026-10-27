package app.n_zik.android.components.settings.general

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.PresetsReverb
import app.it.fast4x.rimusic.utils.audioReverbPresetKey
import app.it.fast4x.rimusic.utils.bassboostEnabledKey
import app.it.fast4x.rimusic.utils.bassboostLevelKey
import app.it.fast4x.rimusic.utils.handleAudioFocusEnabledKey
import app.it.fast4x.rimusic.utils.loudnessBaseGainKey
import app.it.fast4x.rimusic.utils.minimumSilenceDurationKey
import app.it.fast4x.rimusic.utils.rememberEqualizerLauncher
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.skipSilenceKey
import app.it.fast4x.rimusic.utils.volumeBoostLevelKey
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import app.n_zik.android.uiRoundnessShape

/**
 * Audio Effects card: skip silence, loudness, bass boost, reverb, audio focus,
 * equalizer. Owns the player binder + equalizer launcher (the only card that
 * reaches the ExoPlayer).
 */
@OptIn(UnstableApi::class)
@Composable
fun GeneralAudioEffectsCard(
    visible: Boolean,
    onRestartServiceSet: () -> Unit,
) {
    val binder = LocalPlayerServiceBinder.current
    val launchEqualizer by rememberEqualizerLauncher(audioSessionId = { binder?.player?.audioSessionId })

    var skipSilence by rememberPreference(skipSilenceKey, false)
    var minimumSilenceDuration by rememberPreference(minimumSilenceDurationKey, 2_000_000L)
    var volumeNormalization by rememberPreference(volumeNormalizationKey, false)
    var loudnessBaseGain by rememberPreference(loudnessBaseGainKey, 5.00f)
    var volumeBoostLevel by rememberPreference(volumeBoostLevelKey, 0f)
    var bassboostEnabled by rememberPreference(bassboostEnabledKey, false)
    var bassboostLevel by rememberPreference(bassboostLevelKey, 0f)
    var audioReverb by rememberPreference(audioReverbPresetKey, PresetsReverb.NONE)
    var audioFocusEnabled by rememberPreference(handleAudioFocusEnabledKey, true)

    var showAudioReverbDialog by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = stringResource(R.string.audio_effects),
        icon = R.drawable.sound_effect,
        visible = visible,
        content = {
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.audio_effects.skip_silence"),
                title = stringResource(R.string.skip_silence),
                text = stringResource(R.string.skip_silent_parts_during_playback),
                isChecked = skipSilence,
                onCheckedChange = { skipSilence = it },
                icon = R.drawable.pause
            )

            AnimatedVisibility(visible = skipSilence) {
                val initialValue by remember { derivedStateOf { minimumSilenceDuration.toFloat() / 1000L } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }

                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SliderSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("general.audio_effects.minimum_silence_length"),
                        title = stringResource(R.string.minimum_silence_length),
                        text = stringResource(R.string.minimum_silence_length_description),
                        state = newValue,
                        onSlide = { newValue = it },
                        onSlideComplete = {
                            minimumSilenceDuration = newValue.toLong() * 1000L
                            onRestartServiceSet()
                        },
                        isIntegerOnly = true,
                        toDisplay = { stringResource(R.string.format_ms, it.toLong()) },
                        range = 1.00f..2000.000f,
                        stepSize = 0f,
                        defaultValue = 2000f,
                        drawValuePoints = true,
                        icon = R.drawable.time
                    )

                }
            }

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.audio_effects.loudness_normalization"),
                title = stringResource(R.string.loudness_normalization),
                text = stringResource(R.string.autoadjust_the_volume),
                isChecked = volumeNormalization,
                onCheckedChange = { volumeNormalization = it },
                icon = R.drawable.volume_up
            )
            AnimatedVisibility(visible = volumeNormalization) {
                val initialValue by remember { derivedStateOf { loudnessBaseGain } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }

                val initialValueVolume by remember { derivedStateOf { volumeBoostLevel } }
                var newValueVolume by remember(initialValueVolume) { mutableFloatStateOf(initialValueVolume) }

                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SliderSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("general.audio_effects.settings_loudness_base_gain"),
                        title = stringResource(R.string.settings_loudness_base_gain),
                        text = stringResource(R.string.settings_target_gain_loudness_info),
                        state = newValue,
                        onSlide = { newValue = kotlin.math.round(it * 100f) / 100f },
                        onSlideComplete = {
                            loudnessBaseGain = newValue
                        },
                        toDisplay = { "%.2f dB".format(it) },
                        range = -20f..20f,
                        stepSize = 0f,
                        defaultValue = 0f,
                        drawValuePoints = true,
                        icon = R.drawable.volume_up
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(-20f to "-20", -10f to "-10", 0f to "0", 10f to "10", 20f to "20").forEach { (v, label) ->
                            val isSelected = loudnessBaseGain == v
                            TextButton(
                                onClick = { loudnessBaseGain = v; newValue = v },
                                shape = uiRoundnessShape(),
                                colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else Color.Transparent)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (isSelected) colorPalette().accent else colorPalette().text,
                                    fontWeight = if (isSelected) FontWeight.Bold else null
                                )
                            }
                        }
                    }

                    SliderSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("general.audio_effects.loudness_boost_level"),
                        title = stringResource(R.string.loudness_boost_level),
                        text = stringResource(R.string.loudness_boost_level_info),
                        state = newValueVolume,
                        onSlide = { newValueVolume = kotlin.math.round(it * 100f) / 100f },
                        onSlideComplete = {
                            volumeBoostLevel = newValueVolume
                        },
                        toDisplay = { "%.2f dB".format(it).replace(",", ".") },
                        range = -30f..30f,
                        stepSize = 0f,
                        defaultValue = 0f,
                        drawValuePoints = true,
                        icon = R.drawable.volume_up
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(-30f to "-30", -15f to "-15", 0f to "0", 15f to "15", 30f to "30").forEach { (v, label) ->
                            val isSelected = volumeBoostLevel == v
                            TextButton(
                                onClick = { volumeBoostLevel = v; newValueVolume = v },
                                shape = uiRoundnessShape(),
                                colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else Color.Transparent)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (isSelected) colorPalette().accent else colorPalette().text,
                                    fontWeight = if (isSelected) FontWeight.Bold else null
                                )
                            }
                        }
                    }
                }
            }

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.audio_effects.settings_audio_bass_boost"),
                title = stringResource(R.string.settings_audio_bass_boost),
                text = stringResource(R.string.settings_desc_general_audio_effects_settings_audio_bass_boost),
                isChecked = bassboostEnabled,
                onCheckedChange = { bassboostEnabled = it },
                icon = R.drawable.equalizer
            )
            AnimatedVisibility(visible = bassboostEnabled) {
                val initialValue by remember { derivedStateOf { bassboostLevel } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }

                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SliderSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("general.audio_effects.settings_bass_boost_level"),
                        title = stringResource(R.string.settings_bass_boost_level),
                        text = stringResource(R.string.settings_desc_general_audio_effects_settings_bass_boost_level),
                        state = newValue,
                        onSlide = { newValue = kotlin.math.round(it * 10000f) / 10000f },
                        onSlideComplete = {
                            bassboostLevel = newValue
                        },
                        toDisplay = { "%.2f dB".format(it * 15f).replace(",", ".") },
                        range = 0f..1f,
                        stepSize = 0f,
                        defaultValue = 0f,
                        drawValuePoints = true,
                        icon = R.drawable.equalizer
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(0f to "0", (4f/15f) to "4", (8f/15f) to "8", (11f/15f) to "11", 1f to "15").forEach { (v, label) ->
                            val isSelected = bassboostLevel == v
                            TextButton(
                                onClick = { bassboostLevel = v; newValue = v },
                                shape = uiRoundnessShape(),
                                colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else Color.Transparent)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (isSelected) colorPalette().accent else colorPalette().text,
                                    fontWeight = if (isSelected) FontWeight.Bold else null
                                )
                            }
                        }
                    }
                }
            }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.audio_effects.settings_audio_reverb"),
                title = stringResource(R.string.settings_audio_reverb),
                text = stringResource(R.string.settings_desc_general_audio_effects_settings_audio_reverb),
                selectedValueText = stringResource(audioReverb.textRes),
                onClick = { showAudioReverbDialog = true },
                icon = R.drawable.reverb
            )

            DialogPresence(visible = showAudioReverbDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio),
                    selectedValue = audioReverb,
                    onValueSelected = {
                        audioReverb = it
                        onRestartServiceSet()
                    },
                    valueText = { stringResource(it.textRes) },
                    values = PresetsReverb.values().toList(),
                    onDismiss = { showAudioReverbDialog = false }
                )
            }

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("general.audio_effects.settings_audio_focus"),
                title = stringResource(R.string.settings_audio_focus),
                text = stringResource(R.string.settings_audio_focus_info),
                isChecked = audioFocusEnabled,
                onCheckedChange = { audioFocusEnabled = it },
                icon = R.drawable.focus_audio
            )

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.audio_effects.equalizer"),
                title = stringResource(R.string.equalizer),
                text = stringResource(R.string.interact_with_the_system_equalizer),
                onClick = launchEqualizer,
                icon = R.drawable.equalizer
            )
        }
    )
}
