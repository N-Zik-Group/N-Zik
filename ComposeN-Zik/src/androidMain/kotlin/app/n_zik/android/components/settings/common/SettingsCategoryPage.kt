package app.n_zik.android.components.settings.common

import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.components.pullrefresh.rememberPullRefreshOverlayInsetProvider
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.lastFmSettingsSearchFallback
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassContentCaptureEnabled
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.search.SearchHistorySection
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.rememberSearchHistory
import app.n_zik.android.components.settings.about.AboutSettingsContent
import app.n_zik.android.components.settings.accounts.AccountsSettingsContent
import app.n_zik.android.components.settings.accounts.discordSettingsSearchFallback
import app.n_zik.android.components.settings.accounts.githubSettingsSearchFallback
import app.n_zik.android.components.settings.ai.AIRecommendationSettingsContent
import app.n_zik.android.components.settings.appearance.AppearanceSettingsContent
import app.n_zik.android.components.settings.appearance.appearanceBehaviorSearchFallback
import app.n_zik.android.components.settings.data.DataSettingsContent
import app.n_zik.android.components.settings.data.isStorageCleanupSearchTarget
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.navigation.LocalPageNavigationEntry
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.PlayerInfoType
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import app.n_zik.android.components.settings.general.GeneralSettingsContent
import app.n_zik.android.components.settings.network.NetworkSettingsContent
import app.n_zik.android.components.settings.other.OtherSettingsContent
import app.n_zik.android.components.settings.privacy.PermissionUsageSettingsCard
import app.n_zik.android.components.settings.privacy.PrivacyScreenCaptureCard
import app.n_zik.android.components.settings.ui.UiSettingsContent
import app.n_zik.android.components.tab.Search
import app.n_zik.android.themed.n_zik.screen.player.readPlayerActionBarTopSlots
import app.n_zik.android.utils.StringResourceIdentity
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.expandedplayerKey
import app.n_zik.android.utils.expandedplayertoggleKey
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.showTipsKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.useSystemFontKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Proxy
import kotlin.math.roundToInt

private val rewindNotificationPrerequisites = mapOf(
    "embedded.rewind.rw_settings_monthly_notification" to "embedded.rewind.rw_settings_monthly",
    "embedded.rewind.rw_settings_yearly_notification" to "embedded.rewind.rw_settings_yearly",
    "embedded.rewind.rw_settings_playlists_monthly_notification" to "embedded.rewind.rw_settings_playlists_monthly",
    "embedded.rewind.rw_settings_playlists_yearly_notification" to "embedded.rewind.rw_settings_playlists_yearly",
)

private const val LyricsOnCoverSettingsTarget = "appearance.txt_lyrics.show_lyrics_thumbnail"
private const val LyricsBackgroundSettingsTarget = "appearance.txt_lyrics.show_background_in_lyrics"
private const val ShowCoverSettingsTarget = "appearance.cover_and_thumbnail.show_thumbnail"
private const val CoverAnimationEnabledTarget = "appearance.cover_and_thumbnail.show_cover_thumbnail_animation"
private const val CoverAnimationStyleTarget = "appearance.cover_and_thumbnail.cover_thumbnail_animation_type"
private const val BackgroundSettingsTarget = "appearance.colors_and_background.background_colors"
private const val GradientSettingsTarget = "appearance.colors_and_background.gradienttype"
private const val PlayerTypeSettingsTarget = "appearance.player_presets_and_layout.playertype"
private val expandedArtworkTargets = setOf(
    "appearance.cover_and_thumbnail.carousel",
    "appearance.cover_and_thumbnail.carouselsize",
)
private const val ThemeSettingsTarget = "ui.theme.theme"
private const val SwipeActionsSettingsTarget = "ui.swipe_to_action.swipe_to_action"
private const val VisualizerSettingsTarget = "appearance.player_behavior_and_visuals.visualizer"
private const val VisualizerColorSourceTarget = "appearance.player_behavior_and_visuals.visualizer_white_color_option"
private const val VisualizerArtworkTarget = "appearance.player_behavior_and_visuals.showvisthumbnail"
private const val ProxyModeSettingsTarget = "other.proxy.proxy_mode"
private const val AutoBackupEnabledTarget = "embedded.auto_backup.enabled"
private const val AutoBackupContentTarget = "embedded.auto_backup.auto_backup_target"
private const val AutoBackupIntervalTarget = "embedded.auto_backup.auto_backup_interval"
private const val LocalMusicEnabledTarget = "other.on_device.enabled"
private const val LocalMusicFolderModeTarget = "other.on_device.folders"
internal const val LocalMusicPermissionTarget = "other.on_device.permission"

private data class SearchPrerequisite(
    val target: String,
    val choices: List<Int> = emptyList(),
    val labelRes: Int? = null,
    val literalChoices: List<String> = emptyList(),
)

private fun settingsSearchPrerequisite(
    target: String,
    preferences: SharedPreferences,
    hasTarget: (String) -> Boolean,
): SearchPrerequisite? {
    if (hasTarget(ProxyModeSettingsTarget)) {
        val modes = when (target) {
            "other.proxy.proxy_host", "other.proxy.proxy_port" -> listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS)
            "other.proxy.settings_proxy_username", "other.proxy.settings_proxy_password" -> listOf(Proxy.Type.HTTP)
            else -> emptyList()
        }
        if (modes.isNotEmpty()) {
            return SearchPrerequisite(ProxyModeSettingsTarget, literalChoices = modes.map { it.name })
        }
    }
    requiredEnabledSettingTargets[target]?.let {
        return SearchPrerequisite(it, listOf(R.string.common_state_on))
    }
    rewindNotificationPrerequisites[target]?.let { prerequisite ->
        return SearchPrerequisite(
            prerequisite.takeIf(hasTarget) ?: RewindMasterSettingsTarget,
            listOf(R.string.common_state_on),
        )
    }

    when (target) {
        "appearance.player_presets_and_layout.pinfo_show_icons" -> return SearchPrerequisite(
            "appearance.player_presets_and_layout.pinfo_type", listOf(PlayerInfoType.Modern.textId),
        )
        "appearance.txt_lyrics.color_custom" -> return SearchPrerequisite(
            "appearance.txt_lyrics.lyricscolor", listOf(R.string.common_color_custom),
        )
        "appearance.player_behavior_and_visuals.color_custom" ->
            return if (hasTarget(VisualizerColorSourceTarget)) {
                SearchPrerequisite(VisualizerColorSourceTarget, listOf(R.string.common_color_custom))
            } else {
                SearchPrerequisite(VisualizerSettingsTarget, listOf(R.string.common_state_on))
            }
        "appearance.player_behavior_and_visuals.black_background_for_visualizer" ->
            return SearchPrerequisite(
                VisualizerArtworkTarget.takeIf(hasTarget) ?: VisualizerSettingsTarget,
                listOf(R.string.common_state_on),
            )
        GradientSettingsTarget -> return SearchPrerequisite(
            BackgroundSettingsTarget, listOf(R.string.player_background_animated_gradient_label),
        )
        "appearance.colors_and_background.m3e_always_show_shapes",
        "appearance.colors_and_background.shake_sensitivity_theme" -> return if (hasTarget(GradientSettingsTarget)) {
            SearchPrerequisite(
                GradientSettingsTarget,
                listOf(R.string.player_background_theme_m3e_morphing_label, R.string.player_background_artwork_m3e_morphing_label),
            )
        } else {
            SearchPrerequisite(BackgroundSettingsTarget, listOf(R.string.player_background_animated_gradient_label))
        }
        "appearance.player_behavior_and_visuals.player_swipe_sensitivity" ->
            return appearanceBehaviorSearchFallback(target, hasTarget)?.let {
                SearchPrerequisite(it, listOf(R.string.common_state_off))
            }
        CoverAnimationStyleTarget ->
            if (hasTarget(CoverAnimationEnabledTarget)) {
                return SearchPrerequisite(CoverAnimationEnabledTarget, listOf(R.string.common_state_on))
            }
    }

    if (target == "ui.theme.theme_mode" && preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic).isAlwaysDark) {
        return SearchPrerequisite(ThemeSettingsTarget, listOf(ColorPaletteName.Dynamic.textId, ColorPaletteName.Default.textId))
    }
    if (target == "ui.settings_use_font_type.settings_use_font_type" && preferences.getBoolean(useSystemFontKey, false)) {
        return SearchPrerequisite("ui.settings_use_font_type.use_system_font", listOf(R.string.common_state_off))
    }
    val requiredTheme = when (target) {
        "ui.theme.respect_brightness" -> ColorPaletteName.Dynamic
        "ui.theme.customcolor" -> ColorPaletteName.CustomColor
        else -> null
    }
    if (requiredTheme != null) return SearchPrerequisite(ThemeSettingsTarget, listOf(requiredTheme.textId))

    val requiredPlayerType = when {
        target == "appearance.cover_and_thumbnail.thumbnailpause" -> PlayerType.Essential
        target in expandedArtworkTargets ||
            target == "appearance.cover_and_thumbnail.fadingedge" ||
            target == "appearance.cover_and_thumbnail.swipe_Animation_No_Thumbnail" ||
            target == "appearance.colors_and_background.albumCoverRotation" -> PlayerType.Modern
        else -> null
    }
    if (requiredPlayerType != null && preferences.getEnum(playerTypeKey, PlayerType.Essential) != requiredPlayerType) {
        return SearchPrerequisite(PlayerTypeSettingsTarget, listOf(requiredPlayerType.textId))
    }
    val requiredCoverVisibility = when (target) {
        "appearance.cover_and_thumbnail.section.motion",
        CoverAnimationEnabledTarget,
        CoverAnimationStyleTarget,
        "appearance.cover_and_thumbnail.player_thumbnail_size" -> R.string.common_state_on
        "appearance.cover_and_thumbnail.carousel",
        "appearance.cover_and_thumbnail.carouselsize",
        "appearance.cover_and_thumbnail.fadingedge",
        "appearance.cover_and_thumbnail.thumbnailpause" ->
            R.string.common_state_on.takeUnless { preferences.getBoolean(showthumbnailKey, true) }
        "appearance.cover_and_thumbnail.noblur",
        "appearance.cover_and_thumbnail.swipe_Animation_No_Thumbnail" -> R.string.common_state_off
        else -> null
    }
    if (requiredCoverVisibility != null) {
        val showCoverAvailable = hasTarget(ShowCoverSettingsTarget) ||
            preferences.getEnum(playerBackgroundColorsKey, PlayerBackgroundColors.AnimatedGradient) == PlayerBackgroundColors.BlurredCoverColor
        return if (showCoverAvailable) {
            SearchPrerequisite(ShowCoverSettingsTarget, listOf(requiredCoverVisibility))
        } else {
            SearchPrerequisite(BackgroundSettingsTarget, listOf(R.string.player_background_artwork_blurred_label))
        }
    }

    if (target in setOf(
            ShowCoverSettingsTarget,
            "appearance.colors_and_background.rotating_cover_title",
            "appearance.colors_and_background.albumCoverRotation",
            "appearance.colors_and_background.bottomgradient",
        )
    ) {
        return SearchPrerequisite(BackgroundSettingsTarget, listOf(R.string.player_background_artwork_blurred_label))
    }
    if (target == "appearance.colors_and_background.blackgradient") {
        return SearchPrerequisite(
            BackgroundSettingsTarget,
            listOf(R.string.player_background_artwork_gradient_label, R.string.player_background_theme_gradient_label),
        )
    }
    if (target.startsWith("ui.swipe_to_action.") && target != SwipeActionsSettingsTarget) {
        return SearchPrerequisite(SwipeActionsSettingsTarget, listOf(R.string.common_state_on))
    }
    if (target.startsWith("other.on_device.") && hasTarget(LocalMusicPermissionTarget)) {
        return SearchPrerequisite(LocalMusicPermissionTarget, labelRes = R.string.permissions_label_media_audio)
    }
    if (target == "other.on_device.folder_that_will_show_when_you_open_on_device_page") {
        return SearchPrerequisite(
            LocalMusicFolderModeTarget.takeIf(hasTarget) ?: LocalMusicEnabledTarget,
            listOf(R.string.common_state_on),
        )
    }
    if (!target.startsWith("embedded.auto_backup.") || target == AutoBackupEnabledTarget) return null
    val prerequisite = when (target.substringAfterLast('.')) {
        "include_youtube_credentials", "include_discord_credentials", "include_lastfm_credentials",
        "include_github_credentials", "include_proxy_credentials" ->
            SearchPrerequisite(AutoBackupContentTarget, listOf(R.string.settings_title))
        "auto_backup_custom_interval_value", "auto_backup_custom_interval_unit" ->
            SearchPrerequisite(AutoBackupIntervalTarget, listOf(R.string.backup_auto_interval_custom))
        "auto_backup_schedule_time" -> SearchPrerequisite(
            AutoBackupIntervalTarget,
            listOf(R.string.backup_auto_interval_daily, R.string.backup_auto_interval_weekly, R.string.backup_auto_interval_monthly),
        )
        "auto_backup_schedule_day_of_week" ->
            SearchPrerequisite(AutoBackupIntervalTarget, listOf(R.string.backup_auto_interval_weekly))
        "auto_backup_schedule_day_of_month" ->
            SearchPrerequisite(AutoBackupIntervalTarget, listOf(R.string.backup_auto_interval_monthly))
        "auto_backup_on_change" -> SearchPrerequisite(
            AutoBackupIntervalTarget,
            listOf(
                R.string.backup_auto_interval_hourly,
                R.string.backup_auto_interval_daily,
                R.string.backup_auto_interval_weekly,
                R.string.backup_auto_interval_monthly,
                R.string.backup_auto_interval_custom,
            ),
        )
        else -> null
    }
    return prerequisite?.takeIf { hasTarget(it.target) }
        ?: SearchPrerequisite(AutoBackupEnabledTarget, listOf(R.string.common_state_on))
}

private val requiredEnabledSettingTargets = mapOf(
    "general.app_behavior.resume_playback_on_start" to "general.app_behavior.persistent_queue",
    "general.playback.crossfade_duration" to "general.playback.crossfade",
    "general.playback.crossfade_gapless" to "general.playback.crossfade",
    "general.audio_effects.minimum_silence_length" to "general.audio_effects.skip_silence",
    "general.audio_effects.settings_loudness_base_gain" to "general.audio_effects.loudness_normalization",
    "general.audio_effects.loudness_boost_level" to "general.audio_effects.loudness_normalization",
    "general.audio_effects.settings_bass_boost_level" to "general.audio_effects.settings_audio_bass_boost",
    "general.picture_in_picture.settings_pip_module" to "general.picture_in_picture.settings_enable_pip",
    "general.picture_in_picture.settings_enable_pip_auto" to "general.picture_in_picture.settings_enable_pip",
    "general.picture_in_picture.open" to "general.picture_in_picture.settings_enable_pip",
    "appearance.player_behavior_and_visuals.songs_number_to_show" to "appearance.player_behavior_and_visuals.show_next_songs_in_player",
    "appearance.player_behavior_and_visuals.showalbumcover" to "appearance.player_behavior_and_visuals.show_next_songs_in_player",
    "appearance.player_behavior_and_visuals.save_visualizer_state" to VisualizerSettingsTarget,
    VisualizerColorSourceTarget to VisualizerSettingsTarget,
    "appearance.player_behavior_and_visuals.visualizer_line_thickness" to VisualizerSettingsTarget,
    VisualizerArtworkTarget to VisualizerSettingsTarget,
    "appearance.player_behavior_and_visuals.show_visualizer_buttons" to VisualizerSettingsTarget,
    "appearance.cover_and_thumbnail.hold_to_speed_up_rate" to "appearance.cover_and_thumbnail.hold_to_speed_up",
    "other.proxy.proxy_mode" to "other.proxy.enable_proxy",
    "other.proxy.proxy_host" to "other.proxy.enable_proxy",
    "other.proxy.proxy_port" to "other.proxy.enable_proxy",
    "other.proxy.settings_proxy_username" to "other.proxy.enable_proxy",
    "other.proxy.settings_proxy_password" to "other.proxy.enable_proxy",
    "other.proxy.settings_region_override" to "other.proxy.enable_proxy",
    "accounts.youtube_music.login_for_browse" to "accounts.youtube_music.enable_youtube_music_login",
    "embedded.youtube.use_account_for_searches" to "accounts.youtube_music.enable_youtube_music_login",
)

internal fun playerSettingsSearchFallback(
    target: String,
    prefs: SharedPreferences,
    isLandscape: Boolean,
    hasTarget: (String) -> Boolean,
): String? {
    val playerType = prefs.getEnum(playerTypeKey, PlayerType.Essential)
    val actionButtons = "appearance.player_action_bar.player_action_bar_buttons"
    val layout = PlayerTypeSettingsTarget
    return when (target) {
        "appearance.cover_and_thumbnail.carousel", "appearance.cover_and_thumbnail.carouselsize" ->
            if (isLandscape) null else if (playerType != PlayerType.Modern) layout else actionButtons
        "appearance.cover_and_thumbnail.fadingedge", "appearance.cover_and_thumbnail.thumbnailpause",
        "appearance.cover_and_thumbnail.statsfornerdsplayer" -> layout
        "appearance.full_screen_lyrics_components.show_total_time_of_queue" ->
            "appearance.colors_and_background.show_total_time_of_queue"
        "appearance.full_screen_lyrics_components.statsfornerds" ->
            if (prefs.getBoolean(showthumbnailKey, true) && playerType == PlayerType.Essential) layout
            else "appearance.cover_and_thumbnail.statsfornerdsplayer"
        "appearance.full_screen_lyrics_components.actionbar" -> actionButtons
        "appearance.full_screen_lyrics_components.miniqueue" ->
            if (!prefs.getBoolean(showNextSongsInPlayerKey, false)) {
                "appearance.player_behavior_and_visuals.show_next_songs_in_player"
            } else {
                "appearance.full_screen_lyrics_components.actionbar".takeIf(hasTarget) ?: actionButtons
            }
        else -> null
    }
}

private fun youtubeLoginSearchFallback(target: String, hasTarget: (String) -> Boolean): String? {
    val login = "accounts.youtube_music.account_login"
    val change = "accounts.youtube_music.change_account"
    val prerequisite = when (target) {
        login -> change
        change, "accounts.youtube_music.account_logoff" -> login
        else -> return null
    }
    return prerequisite.takeIf(hasTarget) ?: "accounts.youtube_music.enable_youtube_music_login"
}

fun settingsCardTargetId(context: android.content.Context, tab: Int, titleRes: Int): String =
    "card.$tab.${settingsSectionNavigationName(titleRes) ?: StringResourceIdentity.legacyName(context.resources.getResourceEntryName(titleRes))}"

/** Detailed search uses the settings host graph; source content retains app-level navigation. */
@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsCategoryPage(
    categoryId: String,
    navController: NavController,
    target: String = "",
    parentTarget: String = "",
    onSearchResultNavigate: (String) -> Unit = { navController.navigate(it) },
) {
    val category = OrganizedSettingsCategories.firstOrNull { it.id == categoryId } ?: return
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val searchInput = rememberSaveable(categoryId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var resetRestartRequired by rememberSaveable(categoryId) { mutableStateOf(false) }
    var searchTargetHandled by rememberSaveable(categoryId, target, parentTarget) { mutableStateOf(false) }
    val search = Search(initialVisible = true, inputState = searchInput)
    val contentSearch = Search(initialVisible = false)
    val searchFocus = rememberSettingsSearchFocus(search.isFocused, search.inputValue.isBlank())
    val registry = remember(categoryId) { SettingsTargetRegistry() }
    DisposableEffect(registry, target) {
        onDispose {
            registry.nestedTarget?.nestedDestination?.let { settingsNestedDialog(it.dialogId)?.hideDialog() }
            registry.nestedTarget = null
            registry.storageProfileTarget = null
            registry.storageEntryTarget = null
            registry.storageCleanupOpened = false
        }
    }
    val scroll = rememberLazyListState()
    val floatingBackdrop = rememberGlassFloatingActionBackdrop()
    var pinnedFooterHeightPx by remember(categoryId) { mutableIntStateOf(0) }
    var hasPinnedFooter by remember(categoryId) { mutableStateOf(false) }
    val pageEntryId = LocalPageNavigationEntry.current?.id
    val reportFooterHeight = LocalSettingsFooterHeight.current
    DisposableEffect(pageEntryId, reportFooterHeight) {
        onDispose { pageEntryId?.let { reportFooterHeight(it, 0) } }
    }
    val footerGlass = rememberGlassSettings()
    val footerBackdrop = rememberLayerBackdrop()
    val captureFooterBackdrop = hasPinnedFooter && footerGlass.usesGlassMaterial(GlassArea.SettingsCards) &&
        glassContentCaptureEnabled(footerGlass)
    val footerBottomInset = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Bottom).asPaddingValues()
    val footerSidePadding = rememberPageChromeContentPadding(topInsetProvider = { 0.dp })
    val footerPadding = remember(footerSidePadding, footerBottomInset) {
        object : PaddingValues by footerSidePadding {
            override fun calculateBottomPadding() = footerBottomInset.calculateBottomPadding()
        }
    }
    val listPadding = rememberSettingsContentPadding(
        base = PaddingValues(bottom = Dimensions.bottomSpacer),
        footerBottomInset = footerBottomInset.takeIf { hasPinnedFooter },
    )
    val density = LocalDensity.current
    var listTopPx by remember { mutableFloatStateOf(0f) }
    val visibleChromeInset = rememberPullRefreshOverlayInsetProvider()
    val statusTopPx = WindowInsets.safeDrawing.getTop(density)
    val searchClearancePx by rememberUpdatedState {
        (maxOf(statusTopPx.toFloat(), with(density) { visibleChromeInset().toPx() }) - listTopPx)
            .coerceAtLeast(0f)
    }
    var targetingListRow by remember { mutableStateOf(false) }
    val defaultScrollSpec = LocalBringIntoViewSpec.current
    val targetScrollSpec = remember(defaultScrollSpec) {
        object : BringIntoViewSpec by defaultScrollSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val clearance = if (targetingListRow) searchClearancePx().coerceAtMost(containerSize) else 0f
                if (clearance > 0f && size > containerSize - clearance) return offset - clearance
                return defaultScrollSpec.calculateScrollDistance(offset - clearance, size, containerSize - clearance)
            }
        }
    }
    val overscroll = rememberOverscrollEffect()
    val showMessage = LocalSettingsSearchMessage.current
    val unavailableMessage = stringResource(R.string.settings_target_unavailable)
    val historyPage = SearchHistoryStore.categoryPage(categoryId)
    val paused by rememberPreference(pauseSearchHistoryKey, false)
    val history = rememberSearchHistory(historyPage, paused)
    fun recordQuery(query: String = search.inputValue) {
        history.value = SearchHistoryStore.record(context.preferences, historyPage, query)
    }
    fun openInterfaceDestination(item: SettingsPageItem) {
        when (item) {
            SettingsGlassItem -> onSearchResultNavigate(SETTINGS_GLASS_ROUTE)
            SettingsThemeStoreItem -> navController.navigate(NavRoutes.themeStore.name)
            else -> item.route?.let { navController.navigate(it) }
        }
    }
    val cards = remember(categoryId) { cardsForCategory(categoryId) }
    val isSearching = search.inputValue.isNotBlank()
    val showInterfaceShortcuts = categoryId == "interface" && !isSearching
    val settingsHeaderItems = if (showInterfaceShortcuts) 2 else 1
    val profileShortcutIndex = if (categoryId == "system") rememberProfileShortcutSettingsIndex(context) else null
    val hasAudioPermission = if (categoryId == "data") rememberSettingsAudioPermission(context) else null
    val entryMatches = searchSettingsEntries(context, search.inputValue, profileShortcutIndex, hasAudioPermission)
        .filter { it.parentCard(categoryId).categoryId() == categoryId }
    val resetEntryScopes = entryMatches.filter { it.labelRes == R.string.settings_reset }.map { it.tab }.toSet()
    val matches = searchSettingsCardResults(context, search.inputValue).filter {
        it.categoryId() == categoryId && (it.titleRes != R.string.settings_reset || it.tab !in resetEntryScopes)
    }
    val interfacePageMatches = if (categoryId == "interface" && isSearching) {
        searchSettingsPageItems(context, search.inputValue).filter {
            it == SettingsGlassItem || it == SettingsThemeStoreItem || it.isThemeStoreCreatorPage()
        }
    } else emptyList()
    val exactInterfacePageMatches = interfacePageMatches.filter {
        normalizedSettingsText(context.getString(it.labelRes)) == normalizedSettingsText(search.inputValue)
    }
    val otherInterfacePageMatches = interfacePageMatches - exactInterfacePageMatches.toSet()
    val groupTitles = remember(cards) { cards.associate { it.titleRes to it.groupTitleRes } }
    val sourceTabs = remember(cards) { cards.map { it.tab }.distinct() }
    val slots = remember(categoryId) { sourceTabs.associateWith { SettingsCardSlots() } }
    val rows = if (isSearching) emptyList() else sourceTabs.flatMap { tab ->
        val selected = cards.filter { it.tab == tab && it.titleRes != R.string.settings_reset }.map { it.titleRes }.toSet()
        val titles = slots.getValue(tab).titles
        val ordered = if (titles.isEmpty()) {
            cards.filter { it.tab == tab && it.titleRes != R.string.settings_reset }.map { it.titleRes } +
                if (tab in listOf(0, 2, 6)) listOf(SettingsSourceFooter) else emptyList()
        } else titles.toList()
        ordered.filter { it in selected }.map { tab to it }
    }
    val latestRows by rememberUpdatedState(rows)
    val resetScopes = when {
        isSearching -> emptyList()
        categoryId == "downloads" -> listOf(DOWNLOAD_RESET_SCOPE)
        categoryId == "system" -> cards.filter { it.titleRes == R.string.settings_reset }.map { it.tab } +
            listOf(DOWNLOAD_RESET_SCOPE, LISTEN_TOGETHER_RESET_SCOPE, PLAYBACK_RESET_SCOPE, PRIVACY_RESET_SCOPE)
        else -> emptyList()
    }
    SettingsSearchEmptyFeedback(search.inputValue, matches.isEmpty() && entryMatches.isEmpty() && interfacePageMatches.isEmpty())
    LaunchedEffect(target, parentTarget) {
        if (target.isBlank() || searchTargetHandled || isSearching) return@LaunchedEffect
        if (categoryId == "privacy" && target in setOf(RewindMasterSettingsTarget, "card.9.rw_settings_master")) {
            val rewindMaster = AllSettingsEntryTargets.first { it.id == RewindMasterSettingsTarget }
            searchTargetHandled = true
            onSearchResultNavigate(rewindMaster.destinationRoute(context))
            return@LaunchedEffect
        }
        val storageRoute = target.split('|', limit = 3)
        val rawTarget = storageRoute.first()
        if (categoryId == "player" && rawTarget in setOf(
                InterfaceRoundnessSettingsTarget,
                ThumbnailRoundnessSettingsTarget,
                ArtistRoundnessSettingsTarget,
            )
        ) {
            val movedEntry = AllSettingsEntryTargets.first { it.id == rawTarget }
            searchTargetHandled = true
            onSearchResultNavigate(movedEntry.destinationRoute(context))
            return@LaunchedEffect
        }
        if (categoryId == "network") {
            val downloadTarget = when (rawTarget) {
                "network.network_status.download_quality_format",
                "embedded.network.download_quality_format" -> "embedded.network.download_quality_format"
                "card.6.settings_download_status",
                "card.6.settings_download_quality" -> "card.6.settings_download_quality"
                else -> null
            }
            if (downloadTarget != null) {
                searchTargetHandled = true
                onSearchResultNavigate("settingsCategory/downloads?target=${Uri.encode(downloadTarget)}")
                return@LaunchedEffect
            }
        }
        // Two layout frames include children and their initial conditional composition.
        withFrameNanos { }
        withFrameNanos { }
        val preferences = context.preferences
        val requiresExpandedPlayer = rawTarget in expandedArtworkTargets && !isLandscape &&
            preferences.getBoolean(showthumbnailKey, true) &&
            preferences.getEnum(playerTypeKey, PlayerType.Essential) == PlayerType.Modern &&
            !preferences.getBoolean(expandedplayerKey, false) &&
            !preferences.getBoolean(expandedplayertoggleKey, false) &&
            "expanded_player" !in readPlayerActionBarTopSlots(preferences)
        val requestedTarget = if (requiresExpandedPlayer) {
            "nested.appearance.player_action_bar.player_action_bar_buttons.1.expanded_player"
        } else when (rawTarget) {
            "appearance.player_presets_and_layout.icon_like_button" -> "appearance.timeline_and_controls.icon_like_button"
            "network.network_status.image_quality_format" -> "network.quality.image_quality_format"
            "network.network_status.audio_quality_format" -> "network.quality.audio_quality_format"
            "network.network_status.download_quality_format" -> "embedded.network.download_quality_format"
            "card.6.network_status" -> "card.6.quality"
            "card.6.settings_download_status" -> "card.6.settings_download_quality"
            "card.4.cache" -> settingsCardTargetId(context, 4, R.string.storage_breakdown_title)
            "card.4.auto_backup_title" -> "embedded.auto_backup.section"
            "card.7.on_device" -> "other.on_device.section"
            "ai.quick_picks.tips", "ai.quick_picks.quick_selection_type" -> {
                val prefs = context.preferences
                val showTips = prefs.getBoolean(showTipsKey, true)
                if (prefs.getBoolean(enableQuickPicksPageKey, true) && !showTips) {
                    SettingsNestedEntryTargets.firstOrNull {
                        it.nestedDestination?.openerId == "ai.quick_picks.quick_picks_content" &&
                            it.nestedDestination.itemId == "tips"
                    }?.id ?: rawTarget
                } else rawTarget
            }
            else -> rawTarget
        }
        val entry = findSettingsEntryTarget(context, requestedTarget)
        val storageEntry = entry?.cardTitleRes == R.string.storage_breakdown_title
        val profileStorageTarget = requestedTarget.startsWith("data.cache.") ||
            requestedTarget.startsWith("embedded.auto_backup.") || requestedTarget.startsWith("other.on_device.") ||
            storageRoute.size == 3
        if (storageEntry && profileStorageTarget) {
            val explicit = storageRoute.takeIf { it.size == 3 }?.let { ProfileStorageIdentity(it[2], it[1]) }
            registry.storageProfileTarget = withContext(NzikDispatchers.DATA) {
                if (explicit == null) profileStorageIdentity(context, getActiveProfile(context))
                else profileStorageIdentity(context, explicit.id)?.takeIf { it == explicit }
            }
            if (explicit != null && registry.storageProfileTarget == null) {
                searchTargetHandled = true
                showMessage(unavailableMessage)
                return@LaunchedEffect
            }
        }
        if (storageEntry && (profileStorageTarget || isStorageCleanupSearchTarget(requestedTarget))) {
            registry.storageEntryTarget = requestedTarget
        }
        val quickPicksCardId = settingsCardTargetId(context, 3, R.string.recommendations_title)
        val isQuickPicksContent = requestedTarget == quickPicksCardId ||
            (entry?.tab == 3 && entry.cardTitleRes == R.string.recommendations_title)
        val hiddenQuickPicksTarget = isQuickPicksContent && !context.preferences.getBoolean(enableQuickPicksPageKey, true)
        val originalCardId = entry?.let { settingsCardTargetId(context, it.tab, it.cardTitleRes) }
            ?: requestedTarget.takeIf { it.startsWith("card.") }
            ?: parentTarget
        val coverVisible = context.preferences.getBoolean(showthumbnailKey, true)
        val hiddenCoverLyricsTarget = categoryId == "player" && !coverVisible &&
            (requestedTarget == LyricsOnCoverSettingsTarget || requestedTarget == LyricsBackgroundSettingsTarget)
        val hiddenFullscreenLyricsTarget = categoryId == "player" && coverVisible &&
            originalCardId == settingsCardTargetId(context, 2, R.string.settings_section_fullscreen_lyrics_title) &&
            context.preferences.getBoolean(showlyricsthumbnailKey, true)
        val syncOverview = "accounts.sync_data_with_ytm_account.sync_data_with_ytm_account"
        val youtubeSyncTarget = categoryId == "youtube" && (requestedTarget.startsWith("accounts.autosync.") ||
            requestedTarget.startsWith("accounts.sync_import_from_youtube.") ||
            requestedTarget.startsWith("accounts.sync_push_to_youtube.") ||
            requestedTarget.startsWith("accounts.sync_data_with_ytm_account.") ||
            originalCardId in setOf(
                settingsCardTargetId(context, 5, R.string.sync_imported_playlists_automatic_title),
                settingsCardTargetId(context, 5, R.string.sync_import_from_youtube),
                settingsCardTargetId(context, 5, R.string.sync_push_to_youtube),
            ))
        val cardId = when {
            hiddenQuickPicksTarget -> settingsCardTargetId(context, 3, R.string.settings_section_quick_picks_visibility_title)
            hiddenCoverLyricsTarget -> settingsCardTargetId(context, 2, R.string.settings_player_artwork_section_title)
            hiddenFullscreenLyricsTarget -> settingsCardTargetId(context, 2, R.string.lyrics_title)
            youtubeSyncTarget && latestRows.none { (tab, title) -> settingsCardTargetId(context, tab, title) == originalCardId } ->
                settingsCardTargetId(context, 5, R.string.sync_data_with_ytm_account)
            else -> originalCardId
        }
        val row = latestRows.indexOfFirst { (tab, title) ->
            title != SettingsSourceFooter && settingsCardTargetId(context, tab, title) == cardId
        }
        val item = when {
            isSettingsResetDialogTarget(requestedTarget) || resetScopeForTarget(parentTarget) != null -> latestRows.size + settingsHeaderItems
            row >= 0 -> row + settingsHeaderItems
            else -> null
        }
        if (item != null) {
            // Lazy cards must be composed before exact-row availability can be evaluated.
            val paddingPx = with(density) { listPadding.calculateTopPadding().roundToPx() }
            scroll.scrollToItem(item, scrollOffset = paddingPx - searchClearancePx().roundToInt())
            withFrameNanos { }
            withFrameNanos { }
        }
        if (requestedTarget == SettingsResetChooserTarget || requestedTarget == SettingsResetAllTarget ||
            requestedTarget == SettingsResetGlassTarget || requestedTarget == SettingsResetDownloadsTarget ||
            requestedTarget == SettingsResetListenTogetherTarget || requestedTarget == SettingsResetPlaybackTarget ||
            requestedTarget == SettingsResetPrivacyTarget
        ) {
            searchTargetHandled = true
            return@LaunchedEffect
        }
        val nested = entry?.nestedDestination.takeUnless { hiddenQuickPicksTarget }
        if (requestedTarget == ListenTogetherCustomServerTarget &&
            registry.targets.containsKey("embedded.listen_together.listen_together_server_url")
        ) {
            registry.sheetTarget = requestedTarget
            withTimeoutOrNull(1500) {
                snapshotFlow { registry.targets[requestedTarget] }.first { it != null }
            }
        }
        if (storageEntry) {
            withTimeoutOrNull(1500) {
                snapshotFlow { registry.targets[requestedTarget] }.first { it != null }
            }
        }
        if (nested != null) {
            val opener = withTimeoutOrNull(1500) {
                snapshotFlow { registry.targets[nested.openerId] }.first { it != null }
            }
            if (opener != null) {
                registry.nestedTarget = entry
                settingsNestedDialog(nested.dialogId)?.showDialog()
                withTimeoutOrNull(1500) {
                    snapshotFlow { registry.targets[requestedTarget] }.first { it != null }
                }
            }
        }
        val disabledFontTarget = requestedTarget == "ui.settings_use_font_type.settings_use_font_type" &&
            preferences.getBoolean(useSystemFontKey, false)
        val unavailable = disabledFontTarget || hiddenFullscreenLyricsTarget || hiddenCoverLyricsTarget || !registry.targets.containsKey(requestedTarget)
        if (unavailable && registry.sheetTarget == requestedTarget) registry.sheetTarget = null
        val needsQuickPicksForYou = requestedTarget != rawTarget &&
            rawTarget in setOf("ai.quick_picks.tips", "ai.quick_picks.quick_selection_type")
        val searchPrerequisite = when {
            unavailable -> when {
                entry?.paletteTitleRes != null -> SearchPrerequisite(
                    ThemeSettingsTarget, listOf(ColorPaletteName.Customized.textId),
                )
                hiddenFullscreenLyricsTarget -> SearchPrerequisite(
                    LyricsOnCoverSettingsTarget, listOf(R.string.common_state_off),
                )
                else -> settingsSearchPrerequisite(requestedTarget, preferences, registry.targets::containsKey)
            }
            needsQuickPicksForYou || requiresExpandedPlayer ->
                SearchPrerequisite(requestedTarget, listOf(R.string.common_state_on))
            else -> null
        }
        if (unavailable || needsQuickPicksForYou || requiresExpandedPlayer) {
            if (unavailable && nested != null) {
                settingsNestedDialog(nested.dialogId)?.hideDialog()
                registry.nestedTarget = null
            }
            val requirement = searchPrerequisite?.let { prerequisite ->
                val labelRes = prerequisite.labelRes ?: AllSettingsEntryTargets.firstOrNull { it.id == prerequisite.target }?.labelRes
                labelRes?.let {
                    val label = context.getString(it)
                    val choices = (prerequisite.choices.map(context::getString) + prerequisite.literalChoices).joinToString(" / ")
                    if (choices.isEmpty()) label else "$label › $choices"
                }
            }
            showMessage(requirement?.let { context.getString(R.string.settings_target_prerequisite, it) } ?: unavailableMessage)
        }
        val resolved = if (unavailable) {
            val prerequisite = searchPrerequisite?.target ?: when {
                entry?.paletteTitleRes != null -> ThemeSettingsTarget
                requestedTarget.startsWith("embedded.rewind.") -> RewindMasterSettingsTarget
                hiddenQuickPicksTarget -> "ai.tab_general.enable_quick_picks_page"
                hiddenCoverLyricsTarget -> ShowCoverSettingsTarget
                hiddenFullscreenLyricsTarget || requestedTarget == LyricsBackgroundSettingsTarget -> LyricsOnCoverSettingsTarget
                requestedTarget.startsWith("other.on_device.") -> "other.on_device.enabled"
                youtubeSyncTarget -> syncOverview
                else -> playerSettingsSearchFallback(requestedTarget, context.preferences, isLandscape, registry.targets::containsKey)
                    ?: appearanceBehaviorSearchFallback(requestedTarget, registry.targets::containsKey)
                    ?: youtubeLoginSearchFallback(requestedTarget, registry.targets::containsKey)
                    ?: discordSettingsSearchFallback(requestedTarget, registry.targets::containsKey)
                    ?: lastFmSettingsSearchFallback(requestedTarget, registry.targets::containsKey)
                    ?: githubSettingsSearchFallback(requestedTarget, registry.targets::containsKey)
            }
            if (prerequisite != null && prerequisite !in registry.targets) {
                val prerequisiteEntry = AllSettingsEntryTargets.firstOrNull { it.id == prerequisite }
                val prerequisiteRow = latestRows.indexOfFirst { (tab, title) ->
                    prerequisiteEntry != null && prerequisiteEntry.tab == tab && prerequisiteEntry.cardTitleRes == title
                }
                if (prerequisiteRow >= 0) {
                    val paddingPx = with(density) { listPadding.calculateTopPadding().roundToPx() }
                    scroll.scrollToItem(prerequisiteRow + settingsHeaderItems, scrollOffset = paddingPx - searchClearancePx().roundToInt())
                    withFrameNanos { }
                }
            }
            prerequisite?.takeIf(registry.targets::containsKey) ?: cardId
        } else requestedTarget
        withFrameNanos { }
        val requester = registry.targets[resolved]
        if (requester != null) {
            // A tall card is already positioned by the list. Bringing the full card into view
            // again can move its header behind the overlay to reveal more of its body.
            if (item == null || resolved != cardId) {
                targetingListRow = unavailable || (nested == null && registry.sheetTarget != requestedTarget)
                try {
                    requester.bringIntoView()
                } finally {
                    targetingListRow = false
                }
            }
        }
        // The destination stays on the back stack after a search hit. Restore its scroll and dialogs as-is.
        searchTargetHandled = true
        if (requester != null) registry.blink(resolved)
    }
    CompositionLocalProvider(
        LocalSettingsTargets provides registry,
        LocalBringIntoViewSpec provides targetScrollSpec,
    ) {
        Box {
            Column(Modifier.glassFloatingActionContent(floatingBackdrop).testTag("settings.category.$categoryId").background(colorPalette().background0).fillMaxWidth().fillMaxHeight()) {
                // One source composition owns shared restart state and singleton dialogs.
                sourceTabs.forEach { tab ->
                    val selection = cards.filter { it.tab == tab }.map { it.titleRes }.toSet() - R.string.settings_reset
                    if (selection.isEmpty()) return@forEach
                    CompositionLocalProvider(
                        LocalSettingsCardSlots provides slots.getValue(tab),
                        LocalSettingsResetScope provides (SettingsCategories.firstOrNull { it.tab == tab }?.labelRes ?: R.string.settings_category_privacy),
                    ) {
                        SettingsSourceContent(
                            tab, contentSearch, navController, selection,
                            { title -> Modifier.settingsSearchTarget(settingsCardTargetId(context, tab, title)) },
                            searchTarget = target,
                        )
                    }
                }
                LazyColumn(
                    state = scroll,
                    contentPadding = listPadding,
                    overscrollEffect = if (scroll.canScrollForward || scroll.canScrollBackward) overscroll else null,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("settings.category.list.$categoryId")
                        .then(if (captureFooterBackdrop) Modifier.layerBackdrop(footerBackdrop) else Modifier)
                        .onGloballyPositioned { listTopPx = it.positionInWindow().y },
                ) {
                    item(key = "header", contentType = "header") {
                        Column {
                            settingsSearchTitle(searchFocus, category.labelRes, category.iconRes)
                            settingsSearchDescription(searchFocus) {
                                SettingsPageDescription(stringResource(category.descriptionRes))
                            }
                            if (categoryId != "about") {
                                search.SearchBar(
                                    this,
                                    barPadding = searchFocus.barPadding,
                                    placeholderRes = R.string.settings_global_search_hint,
                                    onSearch = { recordQuery() },
                                )
                                SearchHistorySection(
                                    visible = search.isFocused && filterSettingsSearchHistory(history.value, search.inputValue).isNotEmpty(),
                                    queries = filterSettingsSearchHistory(history.value, search.inputValue),
                                    allQueries = history.value,
                                    onQueryClick = { search.input = TextFieldValue(it); recordQuery(it); search.isFocused = false },
                                    onQueryDelete = { history.value = SearchHistoryStore.delete(context.preferences, historyPage, it) },
                                    onQueryDeleteAll = { history.value = SearchHistoryStore.deleteAll(context.preferences, historyPage) },
                                )
                            }
                        }
                    }
                    if (showInterfaceShortcuts) {
                        item(key = "interface-shortcuts", contentType = "card") {
                            Column(Modifier.animateItem().settingsSearchContentVisibility(searchFocus.contentAlpha)) {
                                InterfaceSettingsShortcuts(::openInterfaceDestination)
                            }
                        }
                    }
                    items(exactInterfacePageMatches, key = { "page:${it.labelRes}" }, contentType = { "page" }) { item ->
                        SettingsResultCard(testTag = "settings.category.result.page.${item.route}", modifier = Modifier.animateItem()) {
                            GlobalPageItemRow(item, showSearchLocation = true, onItemSelected = {
                                recordQuery()
                                openInterfaceDestination(item)
                            })
                        }
                    }
                    items(entryMatches, key = { "entry:${it.id}" }, contentType = { "entry" }) { entry ->
                        SettingsEntryResultRow(
                            result = entry,
                            surfaceTag = "settings.category.result.entry.${entry.id}",
                            rowTag = "settings.category.entry.${entry.id}",
                            modifier = Modifier.animateItem(),
                            searchCategoryId = categoryId,
                            onClick = {
                                recordQuery()
                                onSearchResultNavigate(entry.destinationRoute(context, categoryId))
                            },
                        )
                    }
                    items(otherInterfacePageMatches, key = { "page:${it.labelRes}" }, contentType = { "page" }) { item ->
                        SettingsResultCard(testTag = "settings.category.result.page.${item.route}", modifier = Modifier.animateItem()) {
                            GlobalPageItemRow(item, showSearchLocation = true, onItemSelected = {
                                recordQuery()
                                openInterfaceDestination(item)
                            })
                        }
                    }
                    items(matches, key = { "result:${it.tab}:${it.titleRes}" }, contentType = { "card_result" }) { card ->
                        SettingsCardResultRow(
                            card = card,
                            surfaceTag = "settings.category.result.card.${card.tab}.${card.titleRes}",
                            modifier = Modifier.animateItem(),
                            rowTag = "settings.category.card.${card.tab}.${card.titleRes}",
                            onClick = {
                                recordQuery()
                                val cardTarget = Uri.encode(settingsCardTargetId(context, card.tab, card.titleRes))
                                onSearchResultNavigate("settingsCategory/${card.categoryId()}?target=$cardTarget")
                            },
                        )
                    }
                    items(rows, key = { (tab, title) -> "$tab:$title" }, contentType = { "card" }) { (tab, title) ->
                        CompositionLocalProvider(
                            LocalSettingsCardGroupTitle provides groupTitles[title],
                            LocalSettingsResetScope provides (SettingsCategories.firstOrNull { it.tab == tab }?.labelRes ?: R.string.settings_category_privacy),
                        ) {
                            Column(Modifier.animateItem().settingsSearchContentVisibility(searchFocus.contentAlpha)) {
                                slots.getValue(tab).content[title]?.value?.invoke()
                            }
                        }
                    }
                    if (resetScopes.isNotEmpty()) {
                        item(key = "reset", contentType = "reset") {
                            Column(Modifier.animateItem().settingsSearchContentVisibility(searchFocus.contentAlpha)) {
                                SettingsResetCard(
                                    resetScopes,
                                    target,
                                    onRestartRequired = { resetRestartRequired = true },
                                    allowAllSettings = categoryId == "system",
                                ) { tab ->
                                    CompositionLocalProvider(
                                        LocalSettingsCardSlots provides null,
                                        LocalSettingsResetScope provides SettingsCategories.first { it.tab == tab }.labelRes,
                                    ) {
                                        SettingsSourceContent(
                                            tab, contentSearch, navController, setOf(R.string.settings_reset),
                                            { title -> Modifier.settingsSearchTarget(settingsCardTargetId(context, tab, title)) },
                                            searchTarget = "",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.testTag("settings.category.footer").onSizeChanged {
                    pinnedFooterHeightPx = it.height
                    pageEntryId?.let { id -> reportFooterHeight(id, it.height) }
                }) {
                    SettingsPinnedFooter(
                        bottomSpacer = 0.dp,
                        contentPadding = footerPadding,
                        backdrop = footerBackdrop.takeIf { captureFooterBackdrop },
                        onVisibilityChanged = { hasPinnedFooter = it },
                    ) {
                        RestartPlayerService(resetRestartRequired, onRestart = { resetRestartRequired = false })
                        sourceTabs.forEach { tab ->
                            CompositionLocalProvider(
                                LocalSettingsResetScope provides (SettingsCategories.firstOrNull { it.tab == tab }?.labelRes ?: R.string.settings_category_privacy),
                            ) {
                                slots.getValue(tab).content[SettingsSourceFooter]?.value?.invoke()
                            }
                        }
                    }
                }
            }
            GlassFloatingActions(floatingBackdrop) {
                SettingsScrollToTop(
                    lazyListState = scroll,
                    pinnedFooterHeightPx = pinnedFooterHeightPx.takeIf { hasPinnedFooter }?.plus(
                        with(density) { listPadding.calculateBottomPadding().roundToPx() },
                    ) ?: 0,
                    modifier = Modifier.testTag("settings.category.scrollToTop"),
                )
            }
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SettingsSourceContent(
    tab: Int,
    search: Search,
    navController: NavController,
    selection: Set<Int>,
    decorate: @Composable (Int) -> Modifier,
    searchTarget: String,
) {
    when (tab) {
        0 -> GeneralSettingsContent(search, selectedCards = selection, cardModifier = decorate)
        1 -> UiSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        2 -> AppearanceSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        3 -> AIRecommendationSettingsContent(
            search, navController, selectedCards = selection, cardModifier = decorate,
        )
        4 -> DataSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        5 -> AccountsSettingsContent(search, navController, selectedCards = selection, cardModifier = decorate)
        6 -> NetworkSettingsContent(
            search, navController, selectedCards = selection, cardModifier = decorate, searchTarget = searchTarget,
        )
        7 -> OtherSettingsContent(search, selectedCards = selection, cardModifier = decorate)
        8 -> AboutSettingsContent(selectedCards = selection, cardModifier = decorate)
        9 -> {
            if (R.string.privacy_screen_protection in selection) SettingsCardSlot(R.string.privacy_screen_protection, modifier = { decorate(R.string.privacy_screen_protection) }) { PrivacyScreenCaptureCard() }
            if (R.string.permissions_usage_journal in selection) SettingsCardSlot(R.string.permissions_usage_journal, modifier = { decorate(R.string.permissions_usage_journal) }) { PermissionUsageSettingsCard() }
        }
    }
}
