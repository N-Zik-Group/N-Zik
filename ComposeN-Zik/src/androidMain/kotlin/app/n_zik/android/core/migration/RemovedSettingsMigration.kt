package app.n_zik.android.core.migration

import android.content.SharedPreferences
import timber.log.Timber

/**
 * Drops values belonging to retired settings, without changing supported preferences.
 *
 * The keys are literals on purpose: their constants were deleted with the settings, and this
 * object is the only place in the code base allowed to still name them.
 *
 * Idempotent and cheap: it writes nothing once the keys are gone, and `apply()` never blocks.
 */
object RemovedSettingsMigration {
    private const val TAG = "RemovedSettingsMigration"

    internal val REMOVED_KEYS = listOf(
        "notificationType", "enableWallpaper", "wallpaperType", "UiType", "messageType",
    )

    fun isRemovedKey(key: String): Boolean = key in REMOVED_KEYS

    /** Stages cleanup with an import's existing transaction; never clears unrelated keys. */
    fun stageRemoval(editor: SharedPreferences.Editor) {
        REMOVED_KEYS.forEach(editor::remove)
    }

    /**
     * @return `true` if at least one obsolete key was present and has been removed.
     */
    fun run(preferences: SharedPreferences): Boolean {
        val present = REMOVED_KEYS.filter(preferences::contains)
        if (present.isEmpty()) return false

        preferences.edit().apply { present.forEach(::remove) }.apply()
        Timber.tag(TAG).i("Removed obsolete settings: %s", present.joinToString())
        return true
    }
}
