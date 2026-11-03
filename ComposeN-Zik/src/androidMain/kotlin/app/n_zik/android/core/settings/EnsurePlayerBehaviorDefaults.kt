package app.n_zik.android.core.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.it.fast4x.rimusic.utils.closeWithBackButtonKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey

/** Shared by startup, Appearance/Interface resets and modern artwork readers. */
internal const val DEFAULT_THUMBNAIL_SIZE_DP = 90f

/** Seeds only missing keys before conflicting modern and legacy readers run. */
internal fun ensurePlayerBehaviorDefaults(preferences: SharedPreferences) {
    val missing = mapOf(closeWithBackButtonKey to true, showButtonPlayerVideoKey to true,
        effectRotationKey to false).filterKeys { !preferences.contains(it) }
    val missingThumbnailSize = !preferences.contains(thumbnailSizeDpKey)
    if (missing.isEmpty() && !missingThumbnailSize) return
    preferences.edit {
        missing.forEach { (key, value) -> putBoolean(key, value) }
        // The remaining legacy landscape reader uses 85f; seed the displayed default
        // before composition so a fresh profile and a reset profile have the same layout.
        if (missingThumbnailSize) putFloat(thumbnailSizeDpKey, DEFAULT_THUMBNAIL_SIZE_DP)
    }
}
