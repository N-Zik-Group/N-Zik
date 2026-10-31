package app.n_zik.android.components.settings.common

import java.net.Proxy
import java.util.Locale

import app.it.fast4x.rimusic.enums.AlbumSwipeAction
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.BackgroundProgress
import app.it.fast4x.rimusic.enums.CarouselSize
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.DurationInMinutes
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerMinTimeForEvent
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.HomeScreenTabs
import app.it.fast4x.rimusic.enums.IconLikeType
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.enums.LocalRecommandationsNumber
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.enums.MaxStatisticsItems
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.MusicAnimationType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.NotificationButtons
import app.it.fast4x.rimusic.enums.PauseBetweenSongs
import app.it.fast4x.rimusic.enums.PipModule
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.PlaylistSwipeAction
import app.it.fast4x.rimusic.enums.PresetsReverb
import app.it.fast4x.rimusic.enums.QueueSwipeAction
import app.it.fast4x.rimusic.enums.QueueType
import app.it.fast4x.rimusic.enums.RecommendationsNumber
import app.it.fast4x.rimusic.enums.SongsNumber
import app.it.fast4x.rimusic.enums.SyncDirection
import app.it.fast4x.rimusic.enums.ThumbnailCoverType
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.n_zik.android.enums.PlayerControlsColors
import app.n_zik.android.enums.PlayerSwipeSensitivity
import app.n_zik.android.enums.ShakeSensitivityTheme
import app.n_zik.android.R

/** Finite selector labels keyed by exact navigation target; never reads saved preference values. */
internal fun settingsEntryChoiceLabels(
    resolveString: (Int) -> String,
    locale: Locale = Locale.getDefault(),
): Map<String, List<String>> {
    fun resources(vararg ids: Int) = ids.map(resolveString)
    fun cacheLabel(name: String): String = when (name) {
        "Custom" -> resolveString(R.string.custom)
        "Disabled" -> resolveString(R.string.turn_off)
        "Unlimited" -> resolveString(R.string.unlimited)
        else -> name
    }
    fun itemCountLabel(name: String): String = when (name) {
        "Custom" -> resolveString(R.string.max_items_custom)
        "Unlimited" -> resolveString(R.string.max_items_unlimited)
        else -> name
    }
    // Arbitrary text, custom numbers, credentials, folders and runtime-loaded lists are excluded.
    return mapOf(
        "general.languages.app_language" to Languages.entries.map { resolveString(it.textId) },
        "general.picture_in_picture.settings_pip_module" to PipModule.entries.map { resolveString(it.textId) },
        "general.queue_management.now_playing_indicator" to MusicAnimationType.entries.map { resolveString(it.textId) },
        "ui.user_interface.navigation_bar_position" to NavigationBarPosition.entries.map { resolveString(it.textId) },
        "ui.user_interface.navigation_bar_type" to NavigationBarType.entries.map { resolveString(it.textId) },
        "ui.user_interface.player_position" to PlayerPosition.entries.map { resolveString(it.textId) },
        "ui.user_interface.menu_style" to MenuStyle.entries.map { resolveString(it.textId) },
        "ui.user_interface.default_page" to HomeScreenTabs.entries.map { resolveString(it.textId) },
        "ui.user_interface.transition_effect" to TransitionEffect.entries.map { resolveString(it.textId) },
        "ui.swipe_to_action.queue_and_local_playlists_left_swipe" to QueueSwipeAction.entries.map { resolveString(it.textId) },
        "ui.swipe_to_action.queue_and_local_playlists_right_swipe" to QueueSwipeAction.entries.map { resolveString(it.textId) },
        "ui.swipe_to_action.playlist_left_swipe" to PlaylistSwipeAction.entries.map { resolveString(it.textId) },
        "ui.swipe_to_action.playlist_right_swipe" to PlaylistSwipeAction.entries.map { resolveString(it.textId) },
        "ui.swipe_to_action.album_left_swipe" to AlbumSwipeAction.entries.map { resolveString(it.textId) },
        "ui.swipe_to_action.album_right_swipe" to AlbumSwipeAction.entries.map { resolveString(it.textId) },
        "ui.theme.theme" to ColorPaletteName.entries.map { resolveString(it.textId) },
        "ui.theme.theme_mode" to ColorPaletteMode.entries.map { resolveString(it.textId) },
        "appearance.player_behavior_and_visuals.player_swipe_sensitivity" to
            PlayerSwipeSensitivity.entries.map { resolveString(it.textId) },
        "appearance.player_behavior_and_visuals.background_progress_bar" to BackgroundProgress.entries.map { resolveString(it.textId) },
        "appearance.colors_and_background.background_colors" to PlayerBackgroundColors.entries.map { resolveString(it.textId) },
        "appearance.colors_and_background.gradienttype" to AnimatedGradient.entries.map { resolveString(it.textId) },
        "appearance.colors_and_background.shake_sensitivity_theme" to ShakeSensitivityTheme.entries.map { resolveString(it.textId) },
        "appearance.cover_and_thumbnail.carouselsize" to CarouselSize.entries.map { resolveString(it.textId) },
        "appearance.cover_and_thumbnail.cover_thumbnail_animation_type" to ThumbnailCoverType.entries.map { resolveString(it.textId) },
        "appearance.notification_player.notificationPlayerFirstIcon" to NotificationButtons.entries.map { resolveString(it.textId) },
        "appearance.notification_player.notificationPlayerSecondIcon" to NotificationButtons.entries.map { resolveString(it.textId) },
        "appearance.player_presets_and_layout.playertype" to PlayerType.entries.map { resolveString(it.textId) },
        "appearance.player_presets_and_layout.queuetype" to QueueType.entries.map { resolveString(it.textId) },
        "appearance.player_presets_and_layout.thumbnailtype" to ThumbnailType.entries.map { resolveString(it.textId) },
        "appearance.player_presets_and_layout.pinfo_type" to PlayerInfoType.entries.map { resolveString(it.textId) },
        "appearance.player_presets_and_layout.pcontrols_type" to PlayerControlsType.entries.map { resolveString(it.textId) },
        "appearance.timeline_and_controls.timelinesize" to PlayerTimelineSize.entries.map { resolveString(it.textId) },
        "appearance.timeline_and_controls.timeline" to PlayerTimelineType.entries.map { resolveString(it.textId) },
        "appearance.timeline_and_controls.player_controls_colors" to PlayerControlsColors.entries.map { resolveString(it.textId) },
        "appearance.timeline_and_controls.play_button" to PlayerPlayButtonType.entries.map { resolveString(it.textId) },
        "appearance.timeline_and_controls.icon_like_button" to IconLikeType.entries.map { resolveString(it.textId) },
        "data.cache.set_cache_location" to ExoPlayerCacheLocation.entries.map { resolveString(it.textId) },
        "ai.quick_picks.tips" to PlayEventsType.entries.map { resolveString(it.textId) },
        "general.audio_effects.settings_audio_reverb" to PresetsReverb.entries.map { resolveString(it.textRes) },
        "ui.settings_use_font_type.settings_use_font_type" to FontType.entries.map { it.name },
        "appearance.player_behavior_and_visuals.songs_number_to_show" to SongsNumber.entries.map { it.name },
        "general.playback.min_listening_time" to ExoPlayerMinTimeForEvent.entries.map { it.name },
        "other.proxy.proxy_mode" to Proxy.Type.values().map { it.name },
        "accounts.sync_data_with_ytm_account.sync_direction" to SyncDirection.entries.map { resolveString(it.stringResource) },
        "network.quality.audio_quality_format" to
            resources(R.string.audio_quality_automatic, R.string.audio_quality_format_high, R.string.audio_quality_format_low),
        "embedded.network.download_quality_format" to
            resources(R.string.audio_quality_automatic, R.string.audio_quality_format_high, R.string.audio_quality_format_low),
        "network.quality.image_quality_format" to
            resources(R.string.audio_quality_automatic, R.string.audio_quality_format_high, R.string.audio_quality_format_medium, R.string.audio_quality_format_low),
        "ui.user_interface.search_display_order" to
            resources(R.string.search_display_order_suggestions_first, R.string.search_display_order_saved_searches_first),
        "appearance.player_behavior_and_visuals.visualizer_white_color_option" to
            resources(R.string.color_white, R.string.bg_colors_background_from_theme, R.string.bg_colors_background_from_cover, R.string.color_custom),
        "appearance.txt_lyrics.lyricscolor" to
            resources(R.string.color_white, R.string.bg_colors_background_from_theme, R.string.bg_colors_background_from_cover, R.string.color_custom),
        "appearance.cover_and_thumbnail.swipe_Animation_No_Thumbnail" to
            resources(R.string.te_slide_vertical, R.string.te_fade, R.string.te_scale, R.string.carousel, R.string.vt_circular),
        "general.playback.exclude_disliked_songs" to resources(R.string.on, R.string.off),
        "general.playback.exclude_disliked_artists" to resources(R.string.on, R.string.off),
        "general.playback.exclude_disliked_albums" to resources(R.string.on, R.string.off),
        "accounts.social_discord.discord_activity_type" to
            resources(R.string.discord_activity_listening, R.string.discord_activity_playing, R.string.discord_activity_watching, R.string.discord_activity_competing),
        "embedded.profile.face_name_source" to
            resources(R.string.face_source_profile, R.string.display_name_source_youtube, R.string.face_source_discord, R.string.face_source_lastfm),
        "embedded.profile.face_avatar_source" to
            resources(R.string.face_source_profile, R.string.display_name_source_youtube, R.string.face_source_discord, R.string.face_source_lastfm),
        "general.playback.pause_between_songs" to PauseBetweenSongs.entries.map { "${it.name}s" },
        "general.playback.exclude_songs_with_duration_limit" to
            DurationInMinutes.entries.map { if (it == DurationInMinutes.Disabled) resolveString(R.string.vt_disabled) else "${it.name}m" },
        "general.playback.crossfade_duration" to listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20).map { "${it}s" },
        "general.queue_management.max_songs_in_queue" to
            MaxSongs.entries.map { if (it == MaxSongs.Unlimited) resolveString(R.string.unlimited) else it.name },
        "general.queue_management.max_songs_in_queue_android_auto" to
            MaxSongs.entries.map { if (it == MaxSongs.Unlimited) resolveString(R.string.unlimited) else it.name },
        "data.cache.image_cache_max_size" to CoilDiskCacheMaxSize.entries.map { cacheLabel(it.name) },
        "data.cache.song_cache_max_size" to ExoPlayerDiskCacheMaxSize.entries.map { cacheLabel(it.name) },
        "data.cache.song_download_max_size" to ExoPlayerDiskDownloadCacheMaxSize.entries.map { cacheLabel(it.name) },
        "ai.statistics.statistics_max_number_of_items" to MaxStatisticsItems.entries.map { itemCountLabel(it.name) },
        "ai.playlist_top.statistics_max_number_of_items" to MaxTopPlaylistItems.entries.map { itemCountLabel(it.name) },
        "ai.smart_recommendations.smart_recommendations_number" to
            RecommendationsNumber.entries.map { if (it == RecommendationsNumber.Adaptive) resolveString(R.string.smart_recommendations_adaptive) else it.name },
        "ai.quick_picks.quick_selection_type" to
            LocalRecommandationsNumber.entries.map { String.format(locale, resolveString(R.string.quick_selection), it.value) },
    )
}
