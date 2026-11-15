package app.n_zik.android.components.settings.appearance.preview

import android.content.SharedPreferences
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.useSystemFontKey
import androidx.core.content.edit

internal enum class StoreGroup { Colors, PlayerAndQueue, Glass, Font, Navigation, PageLayout, Lyrics, Animations }

internal data class StoreAppearance(
    val document: String,
    val size: Int,
    val groups: Map<StoreGroup, ThemeSettings>,
)

internal enum class StoreSupport { Supported, Pending, RequiresUpdate }

internal fun StoreCompatibility.supportAt(versionCode: Int): StoreSupport = when {
    minVersionCode == null -> StoreSupport.Pending
    versionCode < minVersionCode -> StoreSupport.RequiresUpdate
    else -> StoreSupport.Supported
}

internal fun Map<ThemeCategory, ThemeSettings>.toStoreGroups(): Map<StoreGroup, ThemeSettings> {
    val groups = mutableMapOf<StoreGroup, ThemeSettings>()
    for ((category, settings) in this) {
        for ((group, fields) in settings.plain.entries.groupBy { storeGroup(category, it.key) }) {
            groups[group] = ThemeSettings(fields.associate { it.key to it.value }, settings.glass)
        }
    }
    return groups
}

private fun storeGroup(category: ThemeCategory, key: String): StoreGroup = when (category) {
    ThemeCategory.COLORS -> StoreGroup.Colors
    ThemeCategory.GLASS -> StoreGroup.Glass
    ThemeCategory.NAVIGATION -> when (key) {
        fontTypeKey, useSystemFontKey, applyFontPaddingKey -> StoreGroup.Font
        hideStatusBarKey, showFloatingIconKey -> StoreGroup.PageLayout
        else -> StoreGroup.Navigation
    }
    ThemeCategory.PLAYER_AND_QUEUE -> when (key) {
        showlyricsthumbnailKey, showButtonPlayerLyricsKey -> StoreGroup.Lyrics
        animatedGradientKey -> StoreGroup.Animations
        else -> StoreGroup.PlayerAndQueue
    }
}

internal fun StoreAppearance.selectedSettings(
    compatibility: StoreCompatibility,
    versionCode: Int,
    selection: Set<StoreGroup>,
): ThemeSettings? {
    if (compatibility.supportAt(versionCode) != StoreSupport.Supported) return null
    val selected = groups.filterKeys { it in selection }.values
    if (selected.isEmpty()) return null
    return ThemeSettings(
        plain = selected.flatMap { it.plain.entries }.associate { it.key to it.value },
        glass = selected.flatMap { it.glass.entries }.associate { it.key to it.value },
    )
}

internal fun ThemeSettings.applyTo(preferences: SharedPreferences, glassPreferences: SharedPreferences): () -> Unit {
    val previousPlain = preferences.all.filterKeys { it in plain }
    val previousGlass = glassPreferences.all.filterKeys { it in glass }
    preferences.writeThemeValues(plain, plain.keys)
    glassPreferences.writeThemeValues(glass, glass.keys)
    return {
        // Preserve any edits made after applying the theme.
        val currentPlain = preferences.all
        val currentGlass = glassPreferences.all
        preferences.writeThemeValues(previousPlain, plain.keys.filter { currentPlain[it] == plain[it] })
        glassPreferences.writeThemeValues(previousGlass, glass.keys.filter { currentGlass[it] == glass[it] })
    }
}

private fun SharedPreferences.writeThemeValues(values: Map<String, *>, keys: Collection<String>) {
    if (keys.isEmpty()) return
    edit {
        keys.forEach { key ->
            when (val value = values[key]) {
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                null -> remove(key)
                else -> error("Unsupported preference type")
            }
        }
    }
}
