package app.n_zik.android.components.settings.appearance.preview

import app.n_zik.android.enums.FontType
import app.n_zik.android.utils.useSystemFontKey
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassEnabledDefault
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassTintSourceKey
import app.n_zik.android.core.settings.DEFAULT_THUMBNAIL_SIZE_DP
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.CarouselSize
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.NavigationBarType
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerControlsType
import app.n_zik.android.enums.PlayerInfoType
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.enums.PlayerTimelineSize
import app.n_zik.android.enums.PlayerTimelineType
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.enums.QueueType
import app.n_zik.android.enums.ThumbnailType
import app.n_zik.android.utils.actionspacedevenlyKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.blurStrengthKey
import app.n_zik.android.utils.bottomgradientKey
import app.n_zik.android.utils.carouselKey
import app.n_zik.android.utils.carouselSizeKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.expandedplayerKey
import app.n_zik.android.utils.expandedplayertoggleKey
import app.n_zik.android.utils.fadingedgeKey
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.navigationBarTypeKey
import app.n_zik.android.utils.noblurKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerControlsTypeKey
import app.n_zik.android.utils.playerInfoShowIconsKey
import app.n_zik.android.utils.playerInfoTypeKey
import app.n_zik.android.utils.playerPlayButtonTypeKey
import app.n_zik.android.utils.playerTimelineSizeKey
import app.n_zik.android.utils.playerTimelineTypeKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.queueTypeKey
import app.n_zik.android.utils.showButtonPlayerAddToPlaylistKey
import app.n_zik.android.utils.showButtonPlayerArrowKey
import app.n_zik.android.utils.showButtonPlayerDiscoverKey
import app.n_zik.android.utils.showButtonPlayerDownloadKey
import app.n_zik.android.utils.showButtonPlayerLoopKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showButtonPlayerMenuKey
import app.n_zik.android.utils.showButtonPlayerShuffleKey
import app.n_zik.android.utils.showButtonPlayerSleepTimerKey
import app.n_zik.android.utils.showButtonPlayerStartRadioKey
import app.n_zik.android.utils.showButtonPlayerVideoKey
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.showRemainingSongTimeKey
import app.n_zik.android.utils.showTopActionsBarKey
import app.n_zik.android.utils.showTotalTimeQueueKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.thumbnailFadeExKey
import app.n_zik.android.utils.thumbnailFadeKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.utils.thumbnailSpacingKey
import app.n_zik.android.utils.thumbnailTypeKey
import app.n_zik.android.utils.topPaddingKey
import app.n_zik.android.utils.transparentBackgroundPlayerActionBarKey
import app.n_zik.android.utils.transparentbarKey
import app.n_zik.android.utils.visualizerEnabledKey
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal enum class ThemeCategory { COLORS, PLAYER_AND_QUEUE, NAVIGATION, GLASS }
internal data class ThemeSettings(val plain: Map<String, Any>, val glass: Map<String, Any> = emptyMap())

internal fun parseThemeV1(source: String): Map<ThemeCategory, ThemeSettings> {
    val root = Json.parseToJsonElement(source) as? JsonObject ?: error("Invalid theme document")
    val version = root["schemaVersion"] as? JsonPrimitive
    require(root.keys == setOf("schemaVersion", "categories") &&
        version != null && !version.isString && jsonNumber.matches(version.content) &&
        version.content.toBigDecimalOrNull()?.compareTo(java.math.BigDecimal.ONE) == 0
    ) {
        "Unsupported theme schema"
    }
    val categories = root["categories"] as? JsonObject ?: error("Invalid theme categories")
    require(categories.isNotEmpty()) { "The theme contains no categories" }
    return categories.map { (name, content) ->
        val fields = ImportThemeFields(content)
        val category = ThemeCategory.entries.firstOrNull { it.wireKey == name }
            ?: error("Unknown theme category $name")
        category to fields.readCategory(category)
    }.toMap()
}

private val ThemeCategory.wireKey: String get() = when (this) {
    ThemeCategory.COLORS -> "colors"
    ThemeCategory.PLAYER_AND_QUEUE -> "playerAndQueue"
    ThemeCategory.NAVIGATION -> "navigation"
    ThemeCategory.GLASS -> "glass"
}

private fun ThemeFields.fields(category: ThemeCategory) {
    when (category) {
        ThemeCategory.COLORS -> {
            option<ColorPaletteName>("palette", colorPaletteNameKey, ColorPaletteName.Dynamic, setOf(ColorPaletteName.Customized, ColorPaletteName.CustomColor))
            option<ColorPaletteMode>("mode", colorPaletteModeKey, ColorPaletteMode.Dark)
        }
        ThemeCategory.PLAYER_AND_QUEUE -> player()
        ThemeCategory.NAVIGATION -> {
            option<NavigationBarPosition>("position", navigationBarPositionKey, NavigationBarPosition.BottomFloating)
            option<NavigationBarType>("labels", navigationBarTypeKey, NavigationBarType.IconOnly)
            if (this !is ImportThemeFields || has("font")) option<FontType>("font", fontTypeKey, FontType.Rubik)
            if (this !is ImportThemeFields || has("useSystemFont")) flag("useSystemFont", useSystemFontKey, false)
            if (this !is ImportThemeFields || has("applyFontPadding")) flag("applyFontPadding", applyFontPaddingKey, false)
            if (this !is ImportThemeFields || has("hideStatusBar")) flag("hideStatusBar", hideStatusBarKey, false)
            if (this !is ImportThemeFields || has("showFloatingSearch")) flag("showFloatingSearch", showFloatingIconKey, false)
        }
        ThemeCategory.GLASS -> glass()
    }
}

private fun ImportThemeFields.readCategory(category: ThemeCategory): ThemeSettings {
    fields(category)
    val settings = result()
    return if (category == ThemeCategory.GLASS) {
        ThemeSettings(plain = mapOf(glassEnabledKey to settings.getValue(glassEnabledKey)), glass = settings - glassEnabledKey)
    } else ThemeSettings(plain = settings)
}

private fun ThemeFields.player() {
    option<PlayerType>("playerStyle", playerTypeKey, PlayerType.Essential)
    option<QueueType>("queueStyle", queueTypeKey, QueueType.Modern)
    option<ThumbnailType>("coverStyle", thumbnailTypeKey, ThumbnailType.Modern)
    option<PlayerInfoType>("headerStyle", playerInfoTypeKey, PlayerInfoType.Modern)
    option<PlayerControlsType>("controlsStyle", playerControlsTypeKey, PlayerControlsType.Essential)
    option<PlayerBackgroundColors>("background", playerBackgroundColorsKey, PlayerBackgroundColors.AnimatedGradient)
    option<PlayerTimelineType>("timeline", playerTimelineTypeKey, PlayerTimelineType.Wavy)
    flag("showQueueDuration", showTotalTimeQueueKey, true)
    flag("showCover", showthumbnailKey, true)
    flag("transparentPlayerBar", transparentbarKey, true)
    flag("showLyricsOnCover", showlyricsthumbnailKey, true)
    flag("expandedPlayer", expandedplayerKey, false)
    option<PlayerPlayButtonType>("playButtonStyle", playerPlayButtonTypeKey, PlayerPlayButtonType.CircularRibbed)
    flag("bottomGradient", bottomgradientKey, false)
    flag("visualizerEnabled", visualizerEnabledKey, false)
    number("coverSize", thumbnailSizeDpKey, 0.0..100.0, DEFAULT_THUMBNAIL_SIZE_DP)
    option<PlayerTimelineSize>("timelineSize", playerTimelineSizeKey, PlayerTimelineSize.Biggest)
    flag("showExpandPlayerButton", expandedplayertoggleKey, false)
    flag("showNextSongs", showNextSongsInPlayerKey, false)
    flag("showRemainingSongTime", showRemainingSongTimeKey, true)
    number("coverRoundnessDp", thumbnailRoundnessDpKey, 0.0..48.0, 12f)
    flag("showTopActionsBar", showTopActionsBarKey, true)
    flag("transparentActionBar", transparentBackgroundPlayerActionBarKey, true)
    flag("actionButtonsSpacedEvenly", actionspacedevenlyKey, true)
    flag("noBlur", noblurKey, true)
    flag("coverFadingEdge", fadingedgeKey, false)
    flag("carousel", carouselKey, true)
    option<CarouselSize>("carouselSize", carouselSizeKey, CarouselSize.Biggest)
    flag("topPadding", topPaddingKey, true)
    option<AnimatedGradient>("animatedGradient", animatedGradientKey, AnimatedGradient.M3EMorphingCover)
    number("blurStrength", blurStrengthKey, 0.0..100.0, 25f)
    number("expandedCoverFade", thumbnailFadeExKey, 0.0..100.0, 5f)
    number("coverFade", thumbnailFadeKey, 0.0..100.0, 5f)
    number("coverSpacing", thumbnailSpacingKey, -100.0..100.0, 0f)
    flag("showHeaderIcons", playerInfoShowIconsKey, true)
    group("actionButtons") {
        flag("addToPlaylist", showButtonPlayerAddToPlaylistKey, true)
        flag("arrow", showButtonPlayerArrowKey, true)
        flag("download", showButtonPlayerDownloadKey, true)
        flag("loop", showButtonPlayerLoopKey, false)
        flag("lyrics", showButtonPlayerLyricsKey, true)
        flag("shuffle", showButtonPlayerShuffleKey, true)
        flag("sleepTimer", showButtonPlayerSleepTimerKey, false)
        flag("menu", showButtonPlayerMenuKey, false)
        flag("startRadio", showButtonPlayerStartRadioKey, false)
        flag("discover", showButtonPlayerDiscoverKey, false)
        flag("video", showButtonPlayerVideoKey, true)
    }
}

private fun ThemeFields.glass() {
    flag("enabled", glassEnabledKey, glassEnabledDefault)
    option<GlassTintSource>("tintSource", glassTintSourceKey, GlassTintSource.None, setOf(GlassTintSource.Custom))
    option<GlassAreaMode>("navigation", GlassArea.Nav.preferenceKey, GlassAreaMode.GlassAndContour)
    option<GlassAreaMode>("miniPlayer", GlassArea.MiniPlayer.preferenceKey, GlassAreaMode.GlassAndContour)
    option<GlassAreaMode>("nowPlaying", GlassArea.NowPlaying.preferenceKey, GlassAreaMode.GlassAndContour)
}

private interface ThemeFields {
    fun flag(field: String, key: String, default: Boolean)
    fun number(field: String, key: String, range: ClosedFloatingPointRange<Double>, default: Float)
    fun option(field: String, key: String, default: String, values: Set<String>)
    fun group(field: String, block: ThemeFields.() -> Unit)
}

private inline fun <reified T : Enum<T>> ThemeFields.option(
    field: String, key: String, default: T, excluded: Set<T> = emptySet(),
) = option(field, key, default.name, enumValues<T>().filterNot { it in excluded }.map { it.name }.toSet())

private class ImportThemeFields(content: JsonElement) : ThemeFields {
    private val remaining = (content as? JsonObject ?: error("Expected theme settings object")).toMutableMap()
    private val values = mutableMapOf<String, Any>()

    fun has(field: String): Boolean = field in remaining

    fun result(): Map<String, Any> {
        require(remaining.isEmpty()) { "Unrecognized theme fields" }
        return values
    }

    override fun flag(field: String, key: String, default: Boolean) {
        val value = primitive(field)
        require(!value.isString && value.content in setOf("true", "false")) { "Invalid boolean for $field" }
        values[key] = value.content == "true"
    }

    override fun number(field: String, key: String, range: ClosedFloatingPointRange<Double>, default: Float) {
        val primitive = primitive(field)
        require(!primitive.isString && jsonNumber.matches(primitive.content)) { "Invalid number for $field" }
        val value = primitive.content.toDoubleOrNull()
        require(value != null && value in range) { "Number outside range for $field" }
        values[key] = value.toFloat()
    }

    override fun option(field: String, key: String, default: String, values: Set<String>) {
        val value = primitive(field)
        require(value.isString) { "Invalid value for $field" }
        this.values[key] = requireNotNull(values.firstOrNull { wireName(it) == value.content }) { "Unknown value for $field" }
    }

    override fun group(field: String, block: ThemeFields.() -> Unit) {
        values.putAll(ImportThemeFields(requireNotNull(remaining.remove(field))).apply(block).result())
    }

    private fun primitive(field: String): JsonPrimitive =
        remaining.remove(field) as? JsonPrimitive ?: error("Missing or invalid field $field")
}

private val camelBoundary = Regex("([a-z0-9])([A-Z])|([A-Z])([A-Z][a-z])")
private val jsonNumber = Regex("-?(?:0|[1-9][0-9]*)(?:[.][0-9]+)?(?:[eE][+-]?[0-9]+)?")

private fun wireName(name: String): String =
    camelBoundary.replace(name) { match ->
        val (_, lower, upper, acronym, next) = match.groupValues
        if (lower.isNotEmpty()) "$lower-$upper" else "$acronym-$next"
    }.lowercase(Locale.ROOT)
