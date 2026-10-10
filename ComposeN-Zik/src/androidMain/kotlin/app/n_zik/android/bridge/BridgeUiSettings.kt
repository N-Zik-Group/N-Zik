package app.n_zik.android.bridge

import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.blurDarkenFactorKey
import app.it.fast4x.rimusic.utils.blurStrengthKey
import app.it.fast4x.rimusic.utils.bottomgradientKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.iconLikeTypeKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.nowPlayingIndicatorKey
import app.it.fast4x.rimusic.utils.noblurKey
import app.it.fast4x.rimusic.utils.playerBackdropKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.rotatingAlbumCoverKey
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.textoutlineKey
import app.it.fast4x.rimusic.utils.transitionEffectKey
import kotlinx.serialization.Serializable

/**
 * `GET /api/v1/ui/settings` (contract §10.5, since 1.10.0, feature `ui.settings`): the phone's
 * UI settings the client mirrors. Enum values travel as the phone's enum names; every field
 * defaults to the phone's own default, and the client maps an unknown name to that default.
 */
@Serializable
internal data class UiSettingsDto(
    val colorPaletteName: String = "Dynamic",
    val colorPaletteMode: String = "Dark",
    val playerBackgroundColors: String = "AnimatedGradient",
    val blurStrength: Float = 25f,
    val blurDarkenFactor: Float = 0.2f,
    val playerBackdrop: Float = 0f,
    val rotatingAlbumCover: Boolean = false,
    val showThumbnail: Boolean = true,
    val noBlur: Boolean = true,
    val bottomGradient: Boolean = false,
    val iconLikeType: String = "Essential",
    val playerInfoShowIcons: Boolean = true,
    val showSkipTimeButtons: Boolean = true,
    val textOutline: Boolean = false,
    val transitionEffect: String = "Fade",
    val maxTopPlaylistItems: String = "10",
    /** The "Top N" the phone shows: its `MaxTopPlaylistItems.toInt(custom)`; `null` = unlimited. */
    val topN: Int? = 10,
    val menuStyle: String = "List",
    val disableScrollingText: Boolean = false,
    val navigationBarPosition: String = "BottomFloating",
    /** The phone's now-playing indicator (`MusicAnimationType` name: Disabled, Bars, CrazyBars, CrazyPoints, Bubbles). */
    val nowPlayingIndicator: String = "Bubbles",
)

/** Reads [UiSettingsDto] from the active profile's preferences, with the phone's defaults. */
internal object BridgeUiSettings {

    /** Minimal read view of the preferences (the test seam). */
    interface Source {
        fun string(key: String): String?
        fun boolean(key: String, default: Boolean): Boolean
        fun float(key: String, default: Float): Float
        fun int(key: String, default: Int): Int
    }

    fun of(preferences: SharedPreferences): Source = object : Source {
        override fun string(key: String): String? = runCatching { preferences.getString(key, null) }.getOrNull()
        override fun boolean(key: String, default: Boolean) = runCatching { preferences.getBoolean(key, default) }.getOrDefault(default)
        override fun float(key: String, default: Float) = runCatching { preferences.getFloat(key, default) }.getOrDefault(default)
        override fun int(key: String, default: Int) = runCatching { preferences.getInt(key, default) }.getOrDefault(default)
    }

    fun read(source: Source): UiSettingsDto {
        val defaults = UiSettingsDto()
        val maxItems = source.string(MaxTopPlaylistItemsKey) ?: defaults.maxTopPlaylistItems
        val custom = source.int(MaxTopPlaylistItemsCustomValueKey, 10)
        return UiSettingsDto(
            colorPaletteName = source.string(colorPaletteNameKey) ?: defaults.colorPaletteName,
            colorPaletteMode = source.string(colorPaletteModeKey) ?: defaults.colorPaletteMode,
            playerBackgroundColors = source.string(playerBackgroundColorsKey) ?: defaults.playerBackgroundColors,
            blurStrength = source.float(blurStrengthKey, defaults.blurStrength),
            blurDarkenFactor = source.float(blurDarkenFactorKey, defaults.blurDarkenFactor),
            playerBackdrop = source.float(playerBackdropKey, defaults.playerBackdrop),
            rotatingAlbumCover = source.boolean(rotatingAlbumCoverKey, defaults.rotatingAlbumCover),
            showThumbnail = source.boolean(showthumbnailKey, defaults.showThumbnail),
            noBlur = source.boolean(noblurKey, defaults.noBlur),
            bottomGradient = source.boolean(bottomgradientKey, defaults.bottomGradient),
            iconLikeType = source.string(iconLikeTypeKey) ?: defaults.iconLikeType,
            playerInfoShowIcons = source.boolean(playerInfoShowIconsKey, defaults.playerInfoShowIcons),
            showSkipTimeButtons = source.boolean(showSkipTimeButtonsKey, defaults.showSkipTimeButtons),
            textOutline = source.boolean(textoutlineKey, defaults.textOutline),
            transitionEffect = source.string(transitionEffectKey) ?: defaults.transitionEffect,
            maxTopPlaylistItems = maxItems,
            topN = topN(maxItems, custom),
            menuStyle = source.string(menuStyleKey) ?: defaults.menuStyle,
            disableScrollingText = source.boolean(disableScrollingTextKey, defaults.disableScrollingText),
            navigationBarPosition = source.string(navigationBarPositionKey) ?: defaults.navigationBarPosition,
            nowPlayingIndicator = source.string(nowPlayingIndicatorKey) ?: defaults.nowPlayingIndicator,
        )
    }

    /** The phone's `MaxTopPlaylistItems.toInt(customValue)`; `null` for `Unlimited`, `10` for an unknown name. */
    fun topN(name: String, custom: Int): Int? = when (name) {
        "Unlimited" -> null
        "Custom" -> custom.coerceAtLeast(1)
        else -> name.toIntOrNull() ?: 10
    }
}
