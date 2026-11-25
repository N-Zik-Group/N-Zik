package app.n_zik.android.components.settings.appearance.preview

import android.content.SharedPreferences
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.n_zik.android.components.settings.ui.ToolbarSurface
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
import app.n_zik.android.utils.m3eAlwaysShowShapesKey
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarBottomAudioVideoKey
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarTopSlotsKey
import app.n_zik.android.utils.playerActionBarButtonOrderKey
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

internal enum class StoreSupport { Supported, Pending, RequiresUpdate, Legacy }

internal const val FirstThemeStoreVersionCode = 97

internal fun StoreTheme.withMinimumCompatibility(): StoreTheme = copy(
    compatibility = StoreCompatibility(compatibility.minVersionCode?.coerceAtLeast(FirstThemeStoreVersionCode)),
)

internal fun StoreCompatibility.supportAt(versionCode: Int): StoreSupport = when {
    minVersionCode == null -> StoreSupport.Pending
    versionCode < maxOf(FirstThemeStoreVersionCode, minVersionCode) -> StoreSupport.RequiresUpdate
    else -> StoreSupport.Supported
}

internal fun StoreTheme.supportAt(versionCode: Int): StoreSupport {
    if (themeId == BundledThemeId) return StoreSupport.Supported
    val support = compatibility.supportAt(versionCode)
    // Earlier V2 appearances used V1 manifest envelopes and already declared a minimum of 99.
    return if (support == StoreSupport.Supported && schemaVersion == 1 &&
        (compatibility.minVersionCode ?: 0) <= ThemeLegacyMaxVersionCode && versionCode > ThemeLegacyMaxVersionCode
    ) StoreSupport.Legacy else support
}

internal fun Map<ThemeCategory, ThemeSettings>.toStoreGroups(): Map<StoreGroup, ThemeSettings> {
    val groups = mutableMapOf<StoreGroup, ThemeSettings>()
    for ((category, settings) in this) {
        for ((group, fields) in settings.plain.entries.groupBy { storeGroup(category, it.key) }) {
            val previous = groups[group]
            groups[group] = ThemeSettings(
                previous?.plain.orEmpty() + fields.associate { it.key to it.value },
                previous?.glass.orEmpty() + settings.glass,
            )
        }
    }
    return groups
}

private val fullscreenLyricsKeys = setOf(
    queueDurationExpandedKey, titleExpandedKey, timelineExpandedKey, controlsExpandedKey,
    miniQueueExpandedKey, statsExpandedKey, actionExpandedKey,
)

private val animationKeys = setOf(
    animatedGradientKey, m3eAlwaysShowShapesKey, showCoverThumbnailAnimationKey, coverThumbnailAnimationKey, albumCoverRotationKey,
    rotatingAlbumCoverKey, swipeAnimationsNoThumbnailKey, effectRotationKey, buttonzoomoutKey, nowPlayingIndicatorKey,
)

private fun storeGroup(category: ThemeCategory, key: String): StoreGroup = when (category) {
    ThemeCategory.COLORS -> StoreGroup.Colors
    ThemeCategory.GLASS -> StoreGroup.Glass
    ThemeCategory.NAVIGATION -> when (key) {
        in ToolbarSurface.PLAYER_TOP.preferenceKeys, in ToolbarSurface.QUEUE.preferenceKeys -> StoreGroup.PlayerAndQueue
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
    return selectedGroups(selection)
}

internal fun StoreAppearance.selectedSettings(
    theme: StoreTheme,
    versionCode: Int,
    selection: Set<StoreGroup>,
): ThemeSettings? {
    if (theme.supportAt(versionCode) != StoreSupport.Supported) return null
    val selected = selectedGroups(selection) ?: return null
    if (theme.themeId != BundledThemeId) {
        if (StoreGroup.PlayerAndQueue !in selection) return selected
        val defaults = listOf(playerActionBarTopSlotsKey, playerActionBarBottomAudioVideoKey)
            .filterNot(selected.plain::containsKey)
        return selected.copy(plain = selected.plain + defaults.associateWith { UseThemeDefault })
    }

    // Stock resets local defaults without requiring optional fields in its portable document.
    val defaults = buildList {
        if (StoreGroup.Animations in selection) add(m3eAlwaysShowShapesKey)
        if (StoreGroup.Navigation in selection) addAll(stockLibraryToolbarKeys())
        if (StoreGroup.PlayerAndQueue in selection) {
            addAll(MiniPlayerButtonsSettingsDialog.preferenceKeys())
            add(playerActionBarButtonOrderKey)
            add(playerActionBarBottomAudioVideoKey)
        }
        ToolbarSurface.entries.forEach { surface ->
            if (storeGroup(ThemeCategory.NAVIGATION, surface.orderKey) in selection) {
                addAll(surface.preferenceKeys)
            }
        }
    }
    return selected.copy(plain = selected.plain + defaults.associateWith { UseThemeDefault })
}

private fun StoreAppearance.selectedGroups(selection: Set<StoreGroup>): ThemeSettings? {
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
