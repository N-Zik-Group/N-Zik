package app.n_zik.android.components.settings.appearance.preview

import android.content.SharedPreferences
import app.n_zik.android.utils.queueDurationExpandedKey
import app.n_zik.android.utils.titleExpandedKey
import app.n_zik.android.utils.timelineExpandedKey
import app.n_zik.android.utils.controlsExpandedKey
import app.n_zik.android.utils.miniQueueExpandedKey
import app.n_zik.android.utils.statsExpandedKey
import app.n_zik.android.utils.actionExpandedKey
import app.n_zik.android.utils.showCoverThumbnailAnimationKey
import app.n_zik.android.utils.coverThumbnailAnimationKey
import app.n_zik.android.utils.albumCoverRotationKey
import app.n_zik.android.utils.rotatingAlbumCoverKey
import app.n_zik.android.utils.swipeAnimationsNoThumbnailKey
import app.n_zik.android.utils.effectRotationKey
import app.n_zik.android.utils.nowPlayingIndicatorKey
import app.n_zik.android.utils.buttonzoomoutKey
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.showFoldersOnDeviceKey
import app.n_zik.android.utils.showStatsListeningTimeKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.useSystemFontKey
import app.n_zik.android.utils.floatActionIconOffsetXkey
import app.n_zik.android.utils.floatActionIconOffsetYkey
import app.n_zik.android.utils.multiFloatActionIconOffsetXkey
import app.n_zik.android.utils.multiFloatActionIconOffsetYkey
import androidx.core.content.edit

internal enum class StoreGroup { Colors, PlayerAndQueue, Glass, Font, Navigation, PageLayout, Lyrics, Animations }

internal data class StoreAppearance(
    val document: String,
    val size: Int,
    val groups: Map<StoreGroup, ThemeSettings>,
)

internal enum class StoreSupport { Supported, Pending, RequiresUpdate }

internal const val FirstThemeStoreVersionCode = 97

internal fun StoreTheme.withMinimumCompatibility(): StoreTheme = copy(
    compatibility = StoreCompatibility(compatibility.minVersionCode?.coerceAtLeast(FirstThemeStoreVersionCode)),
)

internal fun StoreCompatibility.supportAt(versionCode: Int): StoreSupport = when {
    minVersionCode == null -> StoreSupport.Pending
    versionCode < maxOf(FirstThemeStoreVersionCode, minVersionCode) -> StoreSupport.RequiresUpdate
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

private val fullscreenLyricsKeys = setOf(
    queueDurationExpandedKey, titleExpandedKey, timelineExpandedKey, controlsExpandedKey,
    miniQueueExpandedKey, statsExpandedKey, actionExpandedKey,
)

private val animationKeys = setOf(
    animatedGradientKey, showCoverThumbnailAnimationKey, coverThumbnailAnimationKey, albumCoverRotationKey,
    rotatingAlbumCoverKey, swipeAnimationsNoThumbnailKey, effectRotationKey, buttonzoomoutKey, nowPlayingIndicatorKey,
)

private fun storeGroup(category: ThemeCategory, key: String): StoreGroup = when (category) {
    ThemeCategory.COLORS -> StoreGroup.Colors
    ThemeCategory.GLASS -> StoreGroup.Glass
    ThemeCategory.NAVIGATION -> when (key) {
        fontTypeKey, useSystemFontKey, applyFontPaddingKey -> StoreGroup.Font
        hideStatusBarKey, showFloatingIconKey, uiRoundnessDpKey, artistThumbnailRoundnessDpKey,
        Preference.HOME_ARTIST_ITEM_SIZE.key, Preference.HOME_ALBUM_ITEM_SIZE.key, Preference.HOME_LIBRARY_ITEM_SIZE.key,
        Preference.SEARCH_RESULT_GRID_STATES.key, showFoldersOnDeviceKey, showStatsListeningTimeKey,
        floatActionIconOffsetXkey, floatActionIconOffsetYkey, multiFloatActionIconOffsetXkey,
        multiFloatActionIconOffsetYkey -> StoreGroup.PageLayout
        else -> StoreGroup.Navigation
    }
    ThemeCategory.PLAYER_AND_QUEUE -> when {
        key == showlyricsthumbnailKey || key == showButtonPlayerLyricsKey || key in portableLyricsKeys || key in fullscreenLyricsKeys -> StoreGroup.Lyrics
        key in animationKeys -> StoreGroup.Animations
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

internal fun ThemeSettings.applyTo(
    preferences: SharedPreferences,
    glassPreferences: SharedPreferences,
    appSettings: SharedPreferences = preferences,
): () -> Unit {
    val menu = plain.filterKeys { it in portableBurgerKeys }
    val profileAppearance = plain - menu.keys
    val previousMenu = appSettings.all.filterKeys { it in menu }
    val previousPlain = preferences.all.filterKeys { it in profileAppearance }
    val previousGlass = glassPreferences.all.filterKeys { it in glass }
    preferences.writeThemeValues(profileAppearance, profileAppearance.keys)
    appSettings.writeThemeValues(menu, menu.keys)
    glassPreferences.writeThemeValues(glass, glass.keys)
    return {
        // Preserve any edits made after applying the theme.
        val currentPlain = preferences.all
        val currentGlass = glassPreferences.all
        val currentMenu = appSettings.all
        preferences.writeThemeValues(previousPlain, profileAppearance.keys.filter { key ->
            currentPlain[key] == profileAppearance[key].takeUnless { it == UseThemeDefault }
        })
        appSettings.writeThemeValues(previousMenu, menu.keys.filter { currentMenu[it] == menu[it] })
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
                null, UseThemeDefault -> remove(key)
                else -> error("Unsupported preference type")
            }
        }
    }
}
