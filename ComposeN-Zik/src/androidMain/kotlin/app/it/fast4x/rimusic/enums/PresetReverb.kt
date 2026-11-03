package app.it.fast4x.rimusic.enums

import android.media.audiofx.PresetReverb
import androidx.annotation.StringRes
import app.n_zik.android.R

enum class PresetsReverb(@StringRes val textRes: Int) {
    NONE(R.string.audio_reverb_none),
    SMALLROOM(R.string.audio_reverb_small_room),
    MEDIUMROOM(R.string.audio_reverb_medium_room),
    LARGEROOM(R.string.audio_reverb_large_room),
    MEDIUMHALL(R.string.audio_reverb_medium_hall),
    LARGEHALL(R.string.audio_reverb_large_hall),
    PLATE(R.string.audio_reverb_plate);

    val preset: Short
        get() = when (this) {
            NONE -> PresetReverb.PRESET_NONE
            SMALLROOM -> PresetReverb.PRESET_SMALLROOM
            MEDIUMROOM -> PresetReverb.PRESET_MEDIUMROOM
            LARGEROOM -> PresetReverb.PRESET_LARGEROOM
            MEDIUMHALL -> PresetReverb.PRESET_MEDIUMHALL
            LARGEHALL -> PresetReverb.PRESET_LARGEHALL
            PLATE -> PresetReverb.PRESET_PLATE
        }
}


