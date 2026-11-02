package app.n_zik.android.core.coil

import android.content.SharedPreferences
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getEnum

/** Match the custom size shown in Data settings even when no custom size was saved. */
internal fun configuredImageCacheBytes(preferences: SharedPreferences): Long =
    when (val size = preferences.getEnum(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.`128MB`)) {
        CoilDiskCacheMaxSize.Custom -> preferences.getInt(coilCustomDiskCacheKey, 32) * 1_000_000L
        else -> size.bytes
    }
