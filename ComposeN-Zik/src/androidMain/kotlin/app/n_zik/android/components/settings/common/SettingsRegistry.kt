package app.n_zik.android.components.settings.common

import android.content.Context
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.R

/** Global row for a category, compatibility tab or dedicated route; descriptions feed search. */
data class SettingsPageItem(
    val labelRes: Int,
    val iconRes: Int,
    val descriptionRes: Int,
    val group: Int,
    val tab: Int? = null,
    val route: String? = null,
    val keywordRes: List<Int> = emptyList(),
    val categoryId: String? = null,
)

/** Group card holding About + Update (spec layout, card 1). */
const val SETTINGS_GROUP_ABOUT_UPDATE = 1

/** Group card holding the 8 remaining categories (spec layout, card 2). */
const val SETTINGS_GROUP_CATEGORIES = 2

/** Updater destination, kept next to About on Global. */
val SettingsUpdateItem = SettingsPageItem(
    labelRes = R.string.update,
    iconRes = R.drawable.update,
    descriptionRes = R.string.settings_global_update_description,
    group = SETTINGS_GROUP_ABOUT_UPDATE,
    route = NavRoutes.updater.name,
    keywordRes = listOf(R.string.update, R.string.check_update, R.string.update_available),
)

/** Dedicated Glass destination in the same Global group as Theme and Interface. */
val SettingsGlassItem = SettingsPageItem(
    labelRes = R.string.liquid_glass,
    iconRes = R.drawable.color_palette,
    descriptionRes = R.string.page_glass_summary,
    group = 3,
    route = SETTINGS_GLASS_ROUTE,
    keywordRes = listOf(
        R.string.liquid_glass, R.string.page_glass_summary, R.string.liquid_glass_enable,
        R.string.liquid_glass_tint_source, R.string.liquid_glass_material,
        R.string.liquid_glass_color, R.string.liquid_glass_frost,
        R.string.glass_group_material, R.string.glass_group_colour, R.string.glass_group_edges,
        R.string.glass_group_readability, R.string.glass_dialog_dimming, R.string.glass_menu_dimming,
        R.string.glass_group_where, R.string.glass_performance, R.string.glass_balanced,
        R.string.glass_quality, R.string.glass_vibrancy, R.string.glass_blur, R.string.glass_tint,
        R.string.glass_tint_strength, R.string.glass_film, R.string.glass_indicator,
        R.string.glass_rim, R.string.glass_refraction_depth, R.string.glass_refraction_amount,
        R.string.glass_dispersion, R.string.glass_depth, R.string.glass_where_bar,
        R.string.glass_where_mini_player, R.string.glass_where_header, R.string.glass_where_menus,
    ),
)

val SettingsCategories: List<SettingsPageItem> = listOf(
    SettingsPageItem(R.string.tab_general, R.drawable.ic_launcher_monochrome, R.string.general_settings_description, SETTINGS_GROUP_CATEGORIES, tab = 0),
    SettingsPageItem(R.string.ui_tab, R.drawable.ui, R.string.ui_settings_description, SETTINGS_GROUP_CATEGORIES, tab = 1),
    SettingsPageItem(R.string.player_appearance, R.drawable.color_palette, R.string.appearance_settings_description, SETTINGS_GROUP_CATEGORIES, tab = 2),
    SettingsPageItem(R.string.ai_recommendations, R.drawable.sparkles, R.string.quick_picks_description, SETTINGS_GROUP_CATEGORIES, tab = 3),
    SettingsPageItem(R.string.tab_data, R.drawable.server, R.string.data_settings_description, SETTINGS_GROUP_CATEGORIES, tab = 4),
    SettingsPageItem(R.string.tab_accounts, R.drawable.person, R.string.accounts_settings_description, SETTINGS_GROUP_CATEGORIES, tab = 5),
    SettingsPageItem(R.string.tab_network, R.drawable.network, R.string.network_settings_description, SETTINGS_GROUP_CATEGORIES, tab = 6),
    SettingsPageItem(R.string.tab_miscellaneous, R.drawable.equalizer, R.string.other_settings_description, SETTINGS_GROUP_CATEGORIES, tab = 7),
    SettingsPageItem(R.string.about, R.drawable.information, R.string.about_description, SETTINGS_GROUP_ABOUT_UPDATE, tab = 8),
)

/** Titleless Global card; grouping remains registry data. */
data class SettingsPageGroup(
    val group: Int,
)

val SettingsPageGroups: List<SettingsPageGroup> = (1..7).map(::SettingsPageGroup)

/** The About category, hoisted to the first position of the global page. */
val SettingsAboutItem = SettingsCategories.first { it.tab == 8 }

/** Global order includes Update after About and Glass after Theme and Interface. */
val SettingsPageItems: List<SettingsPageItem> = OrganizedSettingsCategories.map { category ->
    SettingsPageItem(category.labelRes, category.iconRes, category.descriptionRes, category.group, categoryId = category.id)
}.flatMap { item ->
    when (item.categoryId) {
        "about" -> listOf(item, SettingsUpdateItem)
        "interface" -> listOf(item, SettingsGlassItem)
        else -> listOf(item)
    }
}

/** Searchable card with its legacy tab, focus target and keyword resources. */
data class SettingsCardRegistryEntry(
    val tab: Int,
    val titleRes: Int,
    val iconRes: Int,
    val focusId: String = "",
    val keywordRes: List<Int> = emptyList(),
)

val SettingsCards: List<SettingsCardRegistryEntry> = listOf(
    // Tab 0 - General
    SettingsCardRegistryEntry(0, R.string.languages, R.drawable.discover,
        keywordRes = listOf(R.string.languages, R.string.app_language)),
    SettingsCardRegistryEntry(0, R.string.playback, R.drawable.play_forward,
        keywordRes = listOf(
            R.string.resume_playback, R.string.when_device_is_connected, R.string.skip_media_on_error, R.string.skip_media_on_error_description, R.string.player_pause_on_volume_zero, R.string.info_pauses_player_when_volume_zero, R.string.pause_on_headphone_disconnect,
            R.string.playback, R.string.jump_previous, R.string.jump_previous_blank,
            R.string.min_listening_time, R.string.is_min_list_time_for_tips_or_quick_pics,
            R.string.exclude_songs_with_duration_limit, R.string.vt_disabled,
            R.string.exclude_songs_with_duration_limit_description,
            R.string.pause_between_songs, R.string.pause_between_songs_description,
        )),
    SettingsCardRegistryEntry(0, R.string.player_controls, R.drawable.player_control,
        keywordRes = listOf(
            R.string.player_controls, R.string.player_keep_minimized,
            R.string.when_click_on_a_song_player_start_minimized,
            R.string.player_collapsed_disable_swiping_down,
            R.string.avoid_closing_the_player_cleaning_queue_by_swiping_down,
        )),
    SettingsCardRegistryEntry(0, R.string.queue_management, R.drawable.playlist,
        keywordRes = listOf(
            R.string.persistent_queue, R.string.save_and_restore_playing_songs, R.string.resume_playback_on_start, R.string.resume_automatically_when_app_opens, R.string.player_auto_load_songs_in_queue, R.string.player_auto_load_songs_in_queue_description,
            R.string.queue_management, R.string.max_songs_in_queue, R.string.unlimited,
            R.string.discover, R.string.discoverinfo, R.string.playlistindicator,
            R.string.playlistindicatorinfo, R.string.now_playing_indicator,
        )),
    SettingsCardRegistryEntry(0, R.string.app_behavior, R.drawable.settings,
        keywordRes = listOf(
            R.string.app_behavior, R.string.close_app_with_back_button,
            R.string.when_you_use_the_back_button_from_the_home_page,
            R.string.close_background_player, R.string.when_app_swipe_out_from_task_manager,
        )),
    SettingsCardRegistryEntry(0, R.string.audio_effects, R.drawable.sound_effect,
        keywordRes = listOf(
            R.string.audio_effects, R.string.skip_silence, R.string.skip_silent_parts_during_playback,
            R.string.minimum_silence_length, R.string.minimum_silence_length_description,
            R.string.loudness_normalization, R.string.autoadjust_the_volume,
            R.string.settings_loudness_base_gain, R.string.settings_target_gain_loudness_info,
            R.string.settings_audio_bass_boost, R.string.settings_bass_boost_level,
            R.string.settings_audio_reverb,
            R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio,
            R.string.settings_audio_focus, R.string.settings_audio_focus_info,
            R.string.equalizer, R.string.interact_with_the_system_equalizer,
        )),
    SettingsCardRegistryEntry(0, R.string.gestures_events, R.drawable.gesture,
        keywordRes = listOf(R.string.gestures_events, R.string.event_volumekeys, R.string.event_volumekeysinfo)),
    SettingsCardRegistryEntry(0, R.string.picture_in_picture, R.drawable.video, focusId = "pip",
        keywordRes = listOf(
            R.string.picture_in_picture, R.string.settings_enable_pip, R.string.settings_pip_module,
            R.string.pipmodule_cover, R.string.settings_enable_pip_auto,
            R.string.pip_info_from_android_12_pip_can_be_automatically_enabled,
        )),
    SettingsCardRegistryEntry(0, R.string.app_shortcuts_settings, R.drawable.shortcut_rescue,
        keywordRes = listOf(R.string.app_shortcuts_settings)),
    SettingsCardRegistryEntry(0, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 1 - UI
    SettingsCardRegistryEntry(1, R.string.theme, R.drawable.color_palette,
        keywordRes = listOf(R.string.theme, R.string.interface_in_use, R.string.theme_mode)),
    SettingsCardRegistryEntry(1, R.string.user_interface, R.drawable.ui,
        keywordRes = listOf(
            R.string.hide_status_bar,
            R.string.search_display_order, R.string.search_history_pages, R.string.search_history_pages_description, R.string.navigation_bar_position,
            R.string.navigation_bar_type, R.string.player_position, R.string.menu_style,
            R.string.message_type, R.string.home_tabs_settings, R.string.default_page,
            R.string.transition_effect,
        )),
    SettingsCardRegistryEntry(1, R.string.settings_use_font_type, R.drawable.text,
        keywordRes = listOf(R.string.settings_use_font_type, R.string.use_system_font, R.string.apply_font_padding)),
    SettingsCardRegistryEntry(1, R.string.swipe_to_action, R.drawable.arrow_forward,
        keywordRes = listOf(R.string.swipe_to_action)),
    SettingsCardRegistryEntry(1, R.string.home_tabs_settings, R.drawable.library,
        keywordRes = listOf(
            R.string.home_tabs_settings, R.string.quick_picks, R.string.songs, R.string.artists,
            R.string.albums, R.string.playlists,
        )),
    SettingsCardRegistryEntry(1, R.string.sort_menu, R.drawable.arrow_up,
        keywordRes = listOf(
            R.string.sort_menu, R.string.songs, R.string.artists, R.string.albums,
            R.string.playlists, R.string.history,
        )),
    SettingsCardRegistryEntry(1, R.string.floating_dock_settings, R.drawable.ellipsis_horizontal,
        keywordRes = listOf(R.string.floating_dock_settings, R.string.floating_dock_toolbar_order,
            R.string.compact_miniplayer_slot_1, R.string.compact_miniplayer_slot_2, R.string.toolbar)),
    SettingsCardRegistryEntry(1, R.string.toolbar, R.drawable.ellipsis_horizontal,
        keywordRes = listOf(R.string.toolbar, R.string.songs, R.string.artists, R.string.albums, R.string.playlists)),
    SettingsCardRegistryEntry(1, R.string.burger_menu, R.drawable.burger, focusId = "burger",
        keywordRes = listOf(R.string.burger_menu, R.string.burger_menu_customize)),
    SettingsCardRegistryEntry(1, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 2 - Appearance
    SettingsCardRegistryEntry(2, R.string.player_presets_and_layout, R.drawable.ui,
        keywordRes = listOf(
            R.string.player_presets_and_layout, R.string.appearancepresets, R.string.appearancepresetssecondary,
            R.string.playertype, R.string.queuetype, R.string.thumbnailtype, R.string.pinfo_type,
            R.string.pinfo_album_and_artist_name, R.string.pinfo_show_icons, R.string.pcontrols_type,
            )),
    SettingsCardRegistryEntry(2, R.string.cover_and_thumbnail, R.drawable.album,
        keywordRes = listOf(
            R.string.cover_and_thumbnail, R.string.show_thumbnail,
            R.string.swipe_Animation_No_Thumbnail, R.string.te_slide_vertical, R.string.te_fade,
            R.string.te_scale, R.string.carousel, R.string.vt_circular, R.string.fadingedge,
            R.string.carouselsize, R.string.thumbnailpause, R.string.show_lyrics_thumbnail,
            R.string.showvisthumbnail, R.string.show_cover_thumbnail_animation,
            R.string.cover_thumbnail_animation_type, R.string.player_thumbnail_size,
            R.string.thumbnail_roundness, R.string.artist_roundness, R.string.noblur,
            R.string.statsfornerdsplayer,
        )),
    SettingsCardRegistryEntry(2, R.string.timeline_and_controls, R.drawable.horizontal_straight_line,
        keywordRes = listOf(
            R.string.timeline_and_controls, R.string.timelinesize, R.string.miniplayertype,
            R.string.player_swap_controls_with_timeline, R.string.timeline, R.string.transparentbar,
            R.string.play_button, R.string.buttonzoomout,
            R.string.show_total_time_of_queue, R.string.show_remaining_song_time, R.string.skip_time_buttons,
        )),
    SettingsCardRegistryEntry(2, R.string.colors_and_background, R.drawable.color_palette,
        keywordRes = listOf(
            R.string.colors_and_background, R.string.background_colors, R.string.gradienttype,
            R.string.blackgradient, R.string.albumCoverRotation, R.string.bottomgradient,
            R.string.textoutline, R.string.shake_sensitivity_theme,
        )),
    SettingsCardRegistryEntry(2, R.string.player_behavior_and_visuals, R.drawable.equalizer,
        keywordRes = listOf(
            R.string.player_behavior_and_visuals, R.string.show_player_top_actions_bar,
            R.string.blankspace, R.string.show_next_songs_in_player, R.string.showtwosongs,
            R.string.songs_number_to_show, R.string.showalbumcover, R.string.disable_scrolling_text,
            R.string.scrolling_text_is_used_for_long_texts, R.string.player_rotating_buttons,
            R.string.player_enable_rotation_buttons,
            R.string.background_progress_bar,
            R.string.visualizer, R.string.save_visualizer_state, R.string.save_visualizer_state_description,
            R.string.require_mic_permission,
        )),
    SettingsCardRegistryEntry(2, R.string.txt_lyrics, R.drawable.song_lyrics,
        keywordRes = listOf(
            R.string.toggle_lyrics, R.string.click_lyrics_text, R.string.lyricscolor,
            R.string.save_lyrics_state, R.string.show_background_in_lyrics,
            R.string.player_enable_lyrics_popup_message,
        )),
    SettingsCardRegistryEntry(2, R.string.player_action_bar, R.drawable.ellipsis_vertical,
        keywordRes = listOf(
            R.string.player_action_bar, R.string.action_bar_transparent_background,
            R.string.actionspacedevenly, R.string.tapqueue, R.string.swipe_up_to_open_the_queue,
            R.string.action_bar_show_video_button, R.string.action_bar_show_discover_button,
            R.string.action_bar_show_download_button, R.string.action_bar_show_add_to_playlist_button,
            R.string.action_bar_show_loop_button, R.string.action_bar_show_shuffle_button,
            R.string.action_bar_show_lyrics_button, R.string.expandedplayer,
            R.string.action_bar_show_sleep_timer_button, R.string.show_equalizer,
            R.string.action_bar_show_arrow_button_to_open_queue,
            R.string.action_bar_show_start_radio_button, R.string.action_bar_show_menu_button,
        )),
    SettingsCardRegistryEntry(2, R.string.full_screen_lyrics_components, R.drawable.song_lyrics,
        keywordRes = listOf(
            R.string.full_screen_lyrics_components, R.string.show_total_time_of_queue,
            R.string.titleartist, R.string.timeline, R.string.controls, R.string.statsfornerds,
            R.string.actionbar, R.string.miniqueue,
        )),
    SettingsCardRegistryEntry(2, R.string.notification_player, R.drawable.notification1,
        keywordRes = listOf(
            R.string.notification_player, R.string.notificationPlayerFirstIcon,
            R.string.notificationPlayerSecondIcon,
        )),
    SettingsCardRegistryEntry(2, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 3 - AI Recommendations
    SettingsCardRegistryEntry(3, R.string.tab_general, R.drawable.settings,
        keywordRes = listOf(
            R.string.tab_general, R.string.enable_quick_picks_page,
            R.string.disable_if_you_do_not_want_to_see,
        )),
    SettingsCardRegistryEntry(3, R.string.quick_picks, R.drawable.star_brilliant,
        keywordRes = listOf(
            R.string.quick_picks, R.string.quick_picks_content, R.string.show, R.string.tips,
            R.string.charts, R.string.related_albums, R.string.similar_artists,
            R.string.new_albums_of_your_artists, R.string.new_albums, R.string.playlists_you_might_like,
            R.string.moods_and_genres, R.string.quick_selection_type,
            R.string.disable_if_you_do_not_want_to_see,
        )),
    SettingsCardRegistryEntry(3, R.string.rw_settings_title, R.drawable.sparkles, focusId = "rewind",
        keywordRes = listOf(
            R.string.rw_settings_title, R.string.module_open, R.string.rw_settings_yearly,
            R.string.rw_settings_monthly, R.string.rw_settings_global, R.string.rw_bgmusic_settings,
            R.string.rw_settings_notifications, R.string.rw_settings_yearly_notification,
            R.string.rw_settings_monthly_notification, R.string.rw_settings_playlists_monthly_notification,
            R.string.rw_settings_playlists_yearly_notification, R.string.rw_settings_playlists_title,
        )),
    SettingsCardRegistryEntry(3, R.string.smart_recommendations, R.drawable.smart_shuffle,
        keywordRes = listOf(R.string.smart_recommendations, R.string.smart_recommendations_number)),
    SettingsCardRegistryEntry(3, R.string.statistics, R.drawable.trending, focusId = "statistics",
        keywordRes = listOf(R.string.statistics, R.string.statistics_max_number_of_items, R.string.listening_time)),
    SettingsCardRegistryEntry(3, R.string.playlist_top, R.drawable.playlist,
        keywordRes = listOf(R.string.playlist_top, R.string.statistics_max_number_of_items, R.string.my_playlist_top1)),
    SettingsCardRegistryEntry(3, R.string.tab_data, R.drawable.server,
        keywordRes = listOf(R.string.tab_data, R.string.reset_quick_picks)),
    SettingsCardRegistryEntry(3, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 4 - Data
    SettingsCardRegistryEntry(4, R.string.cache, R.drawable.server,
        keywordRes = listOf(
            R.string.cache, R.string.cache_cleared, R.string.image_cache_max_size,
            R.string.set_custom_cache, R.string.song_cache_max_size, R.string.song_download_max_size,
            R.string.set_cache_location,
        )),
    SettingsCardRegistryEntry(4, R.string.title_backup_and_restore, R.drawable.server,
        keywordRes = listOf(
            R.string.title_backup_and_restore, R.string.export_backup, R.string.import_backup,
            R.string.title_import_settings_migration,
        )),
    SettingsCardRegistryEntry(4, R.string.auto_backup_title, R.drawable.history,
        keywordRes = listOf(
            R.string.auto_backup_title, R.string.auto_backup_location, R.string.auto_backup_target,
            R.string.include_youtube_credentials, R.string.include_discord_credentials,
            R.string.include_lastfm_credentials, R.string.include_proxy_credentials,
            R.string.auto_backup_max_backups, R.string.pre_install_backup, R.string.auto_backup_interval,
            R.string.auto_backup_schedule_time, R.string.auto_backup_schedule_day_of_week,
            R.string.auto_backup_schedule_day_of_month, R.string.auto_backup_on_change,
        )),
    SettingsCardRegistryEntry(4, R.string.history, R.drawable.history, focusId = "history",
        keywordRes = listOf(
            R.string.history, R.string.module_open, R.string.pause_search_history,
            R.string.player_pause_listen_history, R.string.clear_search_history,
            R.string.clear_listen_history,
        )),
    SettingsCardRegistryEntry(4, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 5 - Accounts
    SettingsCardRegistryEntry(5, R.string.profile_face, R.drawable.person, focusId = "profiles",
        keywordRes = listOf(
            R.string.profile_face, R.string.profile_face_description, R.string.display_name_shown_as,
            R.string.module_open, R.string.profiles_open_desc, R.string.face_name_source,
            R.string.face_avatar_source, R.string.face_profile_name, R.string.face_photo,
        )),
    SettingsCardRegistryEntry(5, R.string.youtube_music, R.drawable.ytmusic,
        keywordRes = listOf(
            R.string.youtube_music, R.string.enable_youtube_music_login, R.string.login_for_browse,
            R.string.use_account_for_searches,
        )),
    SettingsCardRegistryEntry(5, R.string.sync_data_with_ytm_account, R.drawable.sync,
        keywordRes = listOf(
            R.string.sync_data_with_ytm_account, R.string.sync_status_detail, R.string.sync_now,
            R.string.sync_direction, R.string.sync_cooldown, R.string.sync_show_details,
            R.string.sync_background_guard,
        )),
    SettingsCardRegistryEntry(5, R.string.autosync, R.drawable.sync,
        keywordRes = listOf(R.string.autosync, R.string.autosync_channels, R.string.autosync_albums, R.string.autosync_likes)),
    SettingsCardRegistryEntry(5, R.string.sync_import_from_youtube, R.drawable.download,
        keywordRes = listOf(
            R.string.sync_import_from_youtube, R.string.sync_import_history,
            R.string.sync_import_library_songs, R.string.sync_import_uploaded_songs,
            R.string.sync_import_uploaded_albums, R.string.sync_import_episodes,
        )),
    SettingsCardRegistryEntry(5, R.string.sync_push_to_youtube, R.drawable.arrow_up,
        keywordRes = listOf(
            R.string.sync_push_to_youtube, R.string.sync_push_history, R.string.sync_push_song_like,
            R.string.sync_push_album_bookmark, R.string.sync_push_artist_follow,
            R.string.sync_push_playlist, R.string.sync_push_episode,
        )),
    SettingsCardRegistryEntry(5, R.string.social_discord, R.drawable.logo_discord,
        keywordRes = listOf(
            R.string.social_discord, R.string.beta_title, R.string.discord_enable_rich_presence,
            R.string.discord_advanced_mode, R.string.discord_activity_type, R.string.discord_advanced_name,
            R.string.discord_advanced_details, R.string.discord_advanced_state, R.string.discord_advanced_pause,
            R.string.discord_advanced_image_text, R.string.discord_advanced_logo_text,
            R.string.discord_advanced_button1, R.string.discord_advanced_button2,
            R.string.discord_advanced_show_artwork, R.string.discord_advanced_show_timestamps,
            R.string.discord_advanced_idle_close,
        )),
    SettingsCardRegistryEntry(5, R.string.social_lastfm, R.drawable.logo_lastfm,
        keywordRes = listOf(
            R.string.social_lastfm, R.string.lastfm_enable_scrobbling, R.string.lastfm_now_playing,
            R.string.lastfm_scrobble, R.string.lastfm_min_track_duration,
            R.string.lastfm_scrobble_threshold, R.string.lastfm_max_scrobble_delay,
        )),
    SettingsCardRegistryEntry(5, R.string.listen_together, R.drawable.people, focusId = "listentogether",
        keywordRes = listOf(
            R.string.listen_together, R.string.listen_together_server_url, R.string.listen_together_username,
            R.string.listen_together_auto_approval_joins, R.string.listen_together_auto_approval_suggestions,
            R.string.listen_together_sync_volume, R.string.listen_together_history,
            R.string.listen_together_view_logs, R.string.listen_together_blocked_users,
        )),
    SettingsCardRegistryEntry(5, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 6 - Network
    SettingsCardRegistryEntry(6, R.string.bridge_server, R.drawable.server, focusId = "pcserver",
        keywordRes = listOf(R.string.bridge_server, R.string.bridge_server_description)),
    SettingsCardRegistryEntry(6, R.string.settings_download_quality, R.drawable.network,
        keywordRes = listOf(R.string.download_quality_format, R.string.update_downloads)),
    SettingsCardRegistryEntry(6, R.string.quality, R.drawable.network,
        keywordRes = listOf(R.string.quality, R.string.audio_quality_format, R.string.image_quality_format)),
    SettingsCardRegistryEntry(6, R.string.connection_settings, R.drawable.network,
        keywordRes = listOf(R.string.connection_settings, R.string.enable_connection_metered, R.string.info_enable_connection_metered)),
    SettingsCardRegistryEntry(6, R.string.offline_mode, R.drawable.airplane, focusId = "offline",
        keywordRes = listOf(R.string.offline_mode, R.string.offline_mode_description)),
    SettingsCardRegistryEntry(6, R.string.download, R.drawable.download,
        keywordRes = listOf(
            R.string.download, R.string.settings_enable_autodownload_song,
            R.string.settings_enable_autodownload_song_when_liked,
            R.string.settings_enable_autodownload_song_when_album_bookmarked,
        )),
    SettingsCardRegistryEntry(6, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 7 - Other
    SettingsCardRegistryEntry(7, R.string.on_device, R.drawable.folder,
        keywordRes = listOf(
            R.string.on_device, R.string.blacklisted_folders,
            R.string.edit_blacklist_for_on_device_songs, R.string.folders,
            R.string.folder_that_will_show_when_you_open_on_device_page,
        )),
    SettingsCardRegistryEntry(7, R.string.androidheadunit, R.drawable.car,
        keywordRes = listOf(R.string.androidheadunit, R.string.extra_space)),
    SettingsCardRegistryEntry(7, R.string.service_lifetime, R.drawable.battery,
        keywordRes = listOf(R.string.service_lifetime, R.string.keep_screen_on, R.string.prevents_screen_timeout, R.string.ignore_battery_optimizations)),
    SettingsCardRegistryEntry(7, R.string.proxy, R.drawable.network,
        keywordRes = listOf(
            R.string.proxy, R.string.enable_proxy, R.string.proxy_mode, R.string.proxy_host,
            R.string.proxy_port, R.string.restarting_rimusic_is_required,
        )),
    SettingsCardRegistryEntry(7, R.string.parental_control, R.drawable.shield_checkmark,
        keywordRes = listOf(R.string.parental_control, R.string.info_prevent_play_songs_with_age_limitation)),
    SettingsCardRegistryEntry(7, R.string.maintenance, R.drawable.shield_checkmark, focusId = "maintenance",
        keywordRes = listOf(
            R.string.maintenance, R.string.maintenance_open, R.string.enable_log_debug,
            R.string.if_enabled_create_a_log_file_to_highlight_errors, R.string.export_logs,
            R.string.export_debug_log_description,
        )),
    SettingsCardRegistryEntry(7, R.string.modules_title, R.drawable.extension, focusId = "extensions",
        keywordRes = listOf(R.string.modules_title, R.string.module_ffmpeg, R.string.module_ffmpeg_description)),
    SettingsCardRegistryEntry(7, R.string.rescue_center, R.drawable.shortcut_rescue, focusId = "rescue",
        keywordRes = listOf(R.string.rescue_center, R.string.rescue_center_description)),
    SettingsCardRegistryEntry(7, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Privacy has its own content source; legacy tabs remain unchanged.
    SettingsCardRegistryEntry(9, R.string.privacy_screen_protection, R.drawable.shield_checkmark),
    SettingsCardRegistryEntry(9, R.string.permission_usage_journal, R.drawable.history),
    SettingsCardRegistryEntry(9, R.string.rw_settings_master, R.drawable.sparkles),
    // Tab 8 - About
    SettingsCardRegistryEntry(8, R.string.about, R.drawable.information),
    SettingsCardRegistryEntry(8, R.string.troubleshooting, R.drawable.information,
        keywordRes = listOf(
            R.string.troubleshooting, R.string.view_the_source_code,
            R.string.you_will_be_redirected_to_github, R.string.report_an_issue,
            R.string.request_a_feature_or_suggest_an_idea,
        )),
    SettingsCardRegistryEntry(8, R.string.contributors, R.drawable.people,
        keywordRes = listOf(R.string.contributors)),
)

/**
 * Global settings search at the card level: case-insensitive `contains` on the
 * card title and its keywords. Blank query returns no card results (the global
 * page then shows the 9 categories instead).
 */
fun searchSettingsCards(context: Context, query: String): List<SettingsCardRegistryEntry> {
    if (query.isBlank()) return emptyList()
    val entryMatches = searchSettingsEntries(context, query).map { it.tab to it.cardTitleRes }.toSet()
    return SettingsCards.filter { card ->
        val queryLower = query.lowercase()
        (card.tab to card.titleRes) in entryMatches || context.getString(card.titleRes).contains(queryLower, true) ||
            card.keywordRes.any { context.getString(it).contains(queryLower, true) }
    }
}

/**
 * Per-tab in-page search at the card level: returns the `titleRes` of every card
 * in the tab. Blank query returns all of them; otherwise the same case-insensitive
 * `contains` match on title + keywords as [searchSettingsCards].
 */
fun visibleCardTitleRes(context: Context, tab: Int, query: String): Set<Int> {
    val cards = SettingsCards.filter { it.tab == tab }
    if (query.isBlank()) return cards.mapTo(HashSet(cards.size)) { it.titleRes }
    return searchSettingsCards(context, query).filter { it.tab == tab }.mapTo(HashSet()) { it.titleRes }
}

/** Searches dedicated Global destinations; categories have their own search results. */
fun searchSettingsPageItems(context: Context, query: String): List<SettingsPageItem> {
    if (query.isBlank()) return emptyList()
    val queryLower = query.lowercase()
    return SettingsPageItems.filter { item ->
        item.keywordRes.any { context.getString(it).contains(queryLower, true) }
    }
}
