package app.n_zik.android.core.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.it.fast4x.rimusic.utils.closeWithBackButtonKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey

/**
 * Match the displayed/reset defaults before modern and legacy readers run.
 * Legacy video and loop-button rotation readers have conflicting fallbacks;
 * seeding only absent keys gives every reader one policy without overriding choices.
 */
internal fun ensurePlayerBehaviorDefaults(preferences: SharedPreferences) {
    val missing = mapOf(closeWithBackButtonKey to true, showButtonPlayerVideoKey to true,
        effectRotationKey to false).filterKeys { !preferences.contains(it) }
    if (missing.isEmpty()) return
    preferences.edit { missing.forEach { (key, value) -> putBoolean(key, value) } }
}
