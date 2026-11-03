package app.n_zik.android.components.settings.common

import android.content.Context
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.glassDestinationKeywordResources

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
    labelRes = R.string.common_action_update,
    iconRes = R.drawable.update,
    descriptionRes = R.string.settings_global_update_description,
    group = SETTINGS_GROUP_ABOUT_UPDATE,
    route = NavRoutes.updater.name,
    keywordRes = listOf(R.string.common_action_update, R.string.updates_action_check, R.string.updates_available),
)

/** Dedicated Glass destination in the same Global group as Theme and Interface. */
val SettingsGlassItem = SettingsPageItem(
    labelRes = R.string.glass_effect_title,
    iconRes = R.drawable.color_palette,
    descriptionRes = R.string.glass_effect_summary,
    group = 3,
    route = SETTINGS_GLASS_ROUTE,
    keywordRes = listOf(
        R.string.glass_effect_title, R.string.glass_effect_summary, R.string.glass_effect_enabled_title,
        R.string.glass_effect_tint_source, R.string.glass_effect_material,
        R.string.glass_effect_custom_color, R.string.glass_effect_frost_strength,
        R.string.glass_effect_group_material, R.string.glass_effect_group_color, R.string.glass_effect_group_edges,
        R.string.glass_effect_group_readability, R.string.glass_effect_dialog_dimming, R.string.glass_effect_menu_dimming,
        R.string.glass_effect_group_where, R.string.glass_effect_quality_fastest, R.string.glass_effect_quality_balanced,
        R.string.glass_effect_quality_high, R.string.glass_effect_color_vibrancy, R.string.glass_effect_frost, R.string.glass_effect_tint,
        R.string.glass_effect_tint_strength, R.string.glass_effect_film, R.string.glass_effect_active_tab_indicator,
        R.string.glass_effect_rim, R.string.glass_effect_refraction_depth, R.string.glass_effect_refraction_amount,
        R.string.glass_effect_dispersion, R.string.glass_effect_depth_enabled, R.string.glass_effect_where_bottom_bar,
        R.string.glass_effect_where_mini_player, R.string.glass_effect_where_header, R.string.glass_effect_where_menus,
    ) + glassDestinationKeywordResources(),
)

val SettingsCategories: List<SettingsPageItem> = listOf(
    SettingsPageItem(R.string.settings_general_title, R.drawable.ic_launcher_monochrome, R.string.settings_general_page_description, SETTINGS_GROUP_CATEGORIES, tab = 0),
    SettingsPageItem(R.string.settings_interface_page_title, R.drawable.ui, R.string.settings_interface_page_description, SETTINGS_GROUP_CATEGORIES, tab = 1),
    SettingsPageItem(R.string.player_appearance, R.drawable.color_palette, R.string.settings_player_appearance_page_description, SETTINGS_GROUP_CATEGORIES, tab = 2),
    SettingsPageItem(R.string.settings_recommendations_title, R.drawable.sparkles, R.string.recommendations_description, SETTINGS_GROUP_CATEGORIES, tab = 3),
    SettingsPageItem(R.string.settings_data_title, R.drawable.server, R.string.settings_data_page_description, SETTINGS_GROUP_CATEGORIES, tab = 4),
    SettingsPageItem(R.string.settings_accounts_title, R.drawable.person, R.string.settings_accounts_page_description, SETTINGS_GROUP_CATEGORIES, tab = 5),
    SettingsPageItem(R.string.settings_network_title, R.drawable.network, R.string.network_settings_description, SETTINGS_GROUP_CATEGORIES, tab = 6),
    SettingsPageItem(R.string.settings_miscellaneous_title, R.drawable.equalizer, R.string.settings_miscellaneous_page_description, SETTINGS_GROUP_CATEGORIES, tab = 7),
    SettingsPageItem(R.string.about_title, R.drawable.information, R.string.settings_about_page_description, SETTINGS_GROUP_ABOUT_UPDATE, tab = 8),
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
    SettingsCardRegistryEntry(0, R.string.settings_languages_title, R.drawable.discover,
        keywordRes = listOf(R.string.settings_languages_title, R.string.settings_app_language_label)),
    SettingsCardRegistryEntry(0, R.string.common_playback_section_title, R.drawable.play_forward,
        keywordRes = listOf(
            R.string.settings_playback_resume_on_connection_label, R.string.settings_playback_resume_on_connection_description, R.string.settings_playback_skip_on_error_label, R.string.settings_playback_skip_on_error_description, R.string.player_pause_on_volume_zero, R.string.settings_playback_pause_at_zero_volume_description, R.string.settings_playback_pause_on_headphone_disconnect_label,
            R.string.common_playback_section_title, R.string.settings_playback_previous_restart_threshold_label, R.string.settings_playback_previous_restart_threshold_description,
            R.string.recommendations_minimum_playback_duration_label, R.string.recommendations_minimum_listening_time_description,
            R.string.settings_playback_duration_limit_label, R.string.common_option_disabled,
            R.string.settings_playback_duration_limit_description,
            R.string.settings_playback_pause_between_songs_label, R.string.settings_playback_pause_between_songs_description,
        )),
    SettingsCardRegistryEntry(0, R.string.player_controls, R.drawable.player_control,
        keywordRes = listOf(
            R.string.player_controls, R.string.player_keep_minimized,
            R.string.settings_player_start_minimized_description,
            R.string.player_collapsed_disable_swiping_down,
            R.string.settings_player_prevent_collapsed_dismissal_description,
        )),
    SettingsCardRegistryEntry(0, R.string.settings_queue_management_title, R.drawable.playlist,
        keywordRes = listOf(
            R.string.settings_player_persistent_queue_label, R.string.settings_player_persistent_queue_description, R.string.settings_playback_resume_on_start_label, R.string.settings_playback_resume_on_start_description, R.string.player_auto_load_songs_in_queue, R.string.player_auto_load_songs_in_queue_description,
            R.string.settings_queue_management_title, R.string.settings_player_queue_size_limit_label, R.string.common_limit_unlimited_label,
            R.string.queue_discover_title, R.string.queue_discover_description, R.string.settings_library_playlist_indicator_label,
            R.string.settings_library_playlist_indicator_description, R.string.settings_library_now_playing_indicator_label,
        )),
    SettingsCardRegistryEntry(0, R.string.settings_app_behavior_title, R.drawable.settings,
        keywordRes = listOf(
            R.string.settings_app_behavior_title, R.string.settings_behavior_back_button_exits_label,
            R.string.settings_behavior_back_button_exits_description,
            R.string.settings_player_stop_on_task_removal_label, R.string.settings_player_stop_on_task_removal_description,
        )),
    SettingsCardRegistryEntry(0, R.string.audio_effects, R.drawable.sound_effect,
        keywordRes = listOf(
            R.string.audio_effects, R.string.settings_audio_skip_silence_label, R.string.settings_audio_skip_silence_description,
            R.string.settings_audio_minimum_silence_duration_label, R.string.settings_audio_minimum_silence_duration_description,
            R.string.settings_audio_loudness_normalization_label, R.string.settings_audio_loudness_normalization_description,
            R.string.settings_loudness_base_gain, R.string.settings_target_gain_loudness_info,
            R.string.settings_audio_bass_boost, R.string.settings_bass_boost_level,
            R.string.settings_audio_reverb,
            R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio,
            R.string.settings_audio_focus, R.string.settings_audio_focus_info,
            R.string.settings_audio_equalizer_label, R.string.settings_audio_equalizer_description,
        )),
    SettingsCardRegistryEntry(0, R.string.settings_gestures_title, R.drawable.gesture,
        keywordRes = listOf(R.string.settings_gestures_title, R.string.settings_gestures_volume_keys_skip_label, R.string.settings_gestures_volume_keys_skip_description)),
    SettingsCardRegistryEntry(0, R.string.settings_picture_in_picture_title, R.drawable.video, focusId = "pip",
        keywordRes = listOf(
            R.string.settings_picture_in_picture_title, R.string.settings_enable_pip, R.string.settings_pip_module,
            R.string.settings_picture_in_picture_artwork_module_label, R.string.settings_enable_pip_auto,
            R.string.settings_picture_in_picture_android_12_auto_description,
        )),
    SettingsCardRegistryEntry(0, R.string.settings_launcher_shortcuts_title, R.drawable.shortcut_rescue,
        keywordRes = listOf(R.string.settings_launcher_shortcuts_title)),
    SettingsCardRegistryEntry(0, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 1 - UI
    SettingsCardRegistryEntry(1, R.string.common_color_source_theme, R.drawable.color_palette,
        keywordRes = listOf(R.string.common_color_source_theme, R.string.settings_theme_mode_label)),
    SettingsCardRegistryEntry(1, R.string.settings_interface_title, R.drawable.ui,
        keywordRes = listOf(
            R.string.settings_interface_hide_status_bar_label,
            R.string.search_display_order, R.string.search_history_pages, R.string.search_history_pages_description, R.string.settings_navigation_position_label,
            R.string.settings_navigation_style_label, R.string.player_position, R.string.settings_menu_style_label,
            R.string.home_tabs_settings, R.string.settings_home_default_page_label,
            R.string.settings_navigation_transition_effect_label,
        )),
    SettingsCardRegistryEntry(1, R.string.settings_use_font_type, R.drawable.text,
        keywordRes = listOf(R.string.settings_use_font_type, R.string.settings_font_use_system_label, R.string.settings_font_include_padding_label)),
    SettingsCardRegistryEntry(1, R.string.settings_swipe_actions_title, R.drawable.arrow_forward,
        keywordRes = listOf(R.string.settings_swipe_actions_title)),
    SettingsCardRegistryEntry(1, R.string.home_tabs_settings, R.drawable.library,
        keywordRes = listOf(
            R.string.home_tabs_settings, R.string.recommendations_title, R.string.library_songs_title, R.string.library_artists_title,
            R.string.library_albums_title, R.string.library_playlists_title,
        )),
    SettingsCardRegistryEntry(1, R.string.library_sort_menu, R.drawable.arrow_up,
        keywordRes = listOf(
            R.string.library_sort_menu, R.string.library_songs_title, R.string.library_artists_title, R.string.library_albums_title,
            R.string.library_playlists_title, R.string.library_history_title,
        )),
    SettingsCardRegistryEntry(1, R.string.settings_dock_title, R.drawable.ellipsis_horizontal,
        keywordRes = listOf(R.string.settings_dock_title, R.string.settings_dock_expanded_tab_order_label,
            R.string.settings_dock_compact_first_action_label, R.string.settings_dock_compact_second_action_label, R.string.settings_library_toolbar_title)),
    SettingsCardRegistryEntry(1, R.string.settings_library_toolbar_title, R.drawable.ellipsis_horizontal,
        keywordRes = listOf(R.string.settings_library_toolbar_title, R.string.library_songs_title, R.string.library_artists_title, R.string.library_albums_title, R.string.library_playlists_title)),
    SettingsCardRegistryEntry(1, R.string.settings_navigation_menu_title, R.drawable.burger, focusId = "burger",
        keywordRes = listOf(R.string.settings_navigation_menu_title, R.string.settings_navigation_menu_customization_description)),
    SettingsCardRegistryEntry(1, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 2 - Appearance
    SettingsCardRegistryEntry(2, R.string.player_presets_and_layout, R.drawable.ui,
        keywordRes = listOf(
            R.string.player_presets_and_layout, R.string.settings_player_appearance_presets_title, R.string.settings_player_appearance_presets_preview_description,
            R.string.player_type, R.string.settings_player_queue_style_label, R.string.settings_player_artwork_style_label, R.string.settings_player_header_style_label,
            R.string.settings_player_header_album_artist_description, R.string.settings_player_header_icons_visible_label, R.string.settings_player_controls_style_label,
            )),
    SettingsCardRegistryEntry(2, R.string.settings_player_artwork_section_title, R.drawable.album,
        keywordRes = listOf(
            R.string.settings_player_artwork_section_title, R.string.settings_player_artwork_visible_label,
            R.string.player_artwork_hidden_swipe_animation, R.string.settings_transition_slide_vertical, R.string.settings_transition_fade,
            R.string.settings_transition_scale, R.string.settings_player_artwork_carousel_label, R.string.player_artwork_transition_circular, R.string.settings_player_artwork_fading_edge_label,
            R.string.settings_player_expanded_artwork_size_label, R.string.settings_player_artwork_shrink_when_paused_label, R.string.settings_lyrics_artwork_visible_label,
            R.string.settings_visualizer_artwork_visible_label, R.string.settings_player_artwork_animation_enabled_label,
            R.string.settings_player_artwork_animation_style_label, R.string.player_thumbnail_size,
            R.string.settings_player_artwork_corner_radius_label, R.string.artist_roundness, R.string.settings_player_background_blur_disabled_without_lyrics_label,
            R.string.player_artwork_technical_info_title,
        )),
    SettingsCardRegistryEntry(2, R.string.settings_player_timeline_controls_section_title, R.drawable.horizontal_straight_line,
        keywordRes = listOf(
            R.string.settings_player_timeline_controls_section_title, R.string.settings_player_timeline_size_label, R.string.settings_player_mini_style_label,
            R.string.player_swap_controls_with_timeline, R.string.settings_player_timeline_label, R.string.settings_player_timeline_transparent_label,
            R.string.settings_player_play_button_label, R.string.settings_player_button_zoom_out_animation_label,
            R.string.settings_player_queue_duration_visible_label, R.string.settings_player_remaining_duration_visible_label, R.string.settings_player_seek_time_buttons_label,
        )),
    SettingsCardRegistryEntry(2, R.string.settings_player_background_section_title, R.drawable.color_palette,
        keywordRes = listOf(
            R.string.settings_player_background_section_title, R.string.settings_player_background_colors_label, R.string.settings_player_gradient_style_label,
            R.string.settings_player_bottom_black_gradient_label, R.string.player_artwork_rotation, R.string.settings_player_action_bar_gradient_label,
            R.string.settings_player_text_outline_label, R.string.settings_player_m3e_motion_sensitivity_label,
        )),
    SettingsCardRegistryEntry(2, R.string.player_behavior_and_visuals, R.drawable.equalizer,
        keywordRes = listOf(
            R.string.player_behavior_and_visuals, R.string.settings_player_top_action_bar_visible_label,
            R.string.settings_lyrics_top_spacing_label, R.string.settings_player_next_queue_visible_label, R.string.settings_player_next_two_songs_visible_label,
            R.string.settings_player_queue_visible_count_label, R.string.settings_player_album_cover_visible_label, R.string.settings_player_disable_text_marquee_label,
            R.string.settings_player_disable_text_marquee_description, R.string.player_rotating_buttons,
            R.string.player_enable_rotation_buttons,
            R.string.settings_player_background_progress_label,
            R.string.visualizer_title, R.string.settings_visualizer_remember_state_label, R.string.settings_visualizer_remember_state_description,
            R.string.visualizer_microphone_permission_message,
        )),
    SettingsCardRegistryEntry(2, R.string.lyrics_title, R.drawable.song_lyrics,
        keywordRes = listOf(
            R.string.settings_player_tap_artwork_toggle_lyrics_label, R.string.settings_lyrics_seek_on_line_press_label, R.string.lyrics_color,
            R.string.settings_lyrics_remember_state_label, R.string.settings_lyrics_match_player_background_label,
            R.string.player_enable_lyrics_popup_message,
        )),
    SettingsCardRegistryEntry(2, R.string.settings_section_player_action_bar_title, R.drawable.ellipsis_vertical,
        keywordRes = listOf(
            R.string.settings_section_player_action_bar_title, R.string.settings_player_action_bar_transparent_background_label,
            R.string.settings_player_actions_spaced_evenly_label, R.string.settings_player_queue_tap_to_open_label, R.string.settings_player_queue_swipe_up_label,
            R.string.settings_player_action_bar_video_visible_label, R.string.settings_player_action_bar_discover_visible_label,
            R.string.settings_player_action_bar_download_visible_label, R.string.settings_player_action_bar_playlist_add_visible_label,
            R.string.settings_player_action_bar_loop_visible_label, R.string.settings_player_action_bar_shuffle_visible_label,
            R.string.settings_action_bar_show_lyrics_button, R.string.settings_player_expanded_label,
            R.string.settings_player_action_bar_sleep_timer_visible_label, R.string.settings_player_action_bar_equalizer_visible_label,
            R.string.settings_player_action_bar_queue_arrow_visible_label,
            R.string.settings_player_action_bar_radio_visible_label, R.string.settings_player_action_bar_menu_visible_label,
        )),
    SettingsCardRegistryEntry(2, R.string.settings_section_fullscreen_lyrics_title, R.drawable.song_lyrics,
        keywordRes = listOf(
            R.string.settings_section_fullscreen_lyrics_title, R.string.settings_player_queue_duration_visible_label,
            R.string.settings_lyrics_title_artist_visible_label, R.string.settings_player_timeline_label, R.string.settings_lyrics_controls_visible_label, R.string.lyrics_fullscreen_technical_info_title,
            R.string.settings_lyrics_action_bar_visible_label, R.string.settings_lyrics_next_queue_visible_label,
        )),
    SettingsCardRegistryEntry(2, R.string.settings_section_notification_player_title, R.drawable.notification1,
        keywordRes = listOf(
            R.string.settings_section_notification_player_title, R.string.player_notification_first_action,
            R.string.player_notification_second_action,
        )),
    SettingsCardRegistryEntry(2, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 3 - AI Recommendations
    SettingsCardRegistryEntry(3, R.string.settings_general_title, R.drawable.settings,
        keywordRes = listOf(
            R.string.settings_general_title, R.string.settings_recommendations_quick_picks_enabled_label,
            R.string.recommendations_section_visibility_description,
        )),
    SettingsCardRegistryEntry(3, R.string.recommendations_title, R.drawable.star_brilliant,
        keywordRes = listOf(
            R.string.recommendations_title, R.string.recommendations_content, R.string.recommendations_show_section_label, R.string.recommendations_for_you_title,
            R.string.recommendations_charts_title, R.string.album_related_title, R.string.artist_similar_title,
            R.string.recommendations_followed_artists_new_albums_title, R.string.recommendations_new_albums_title, R.string.recommendations_playlists_title,
            R.string.recommendations_moods_and_genres_title, R.string.settings_recommendations_song_count_description,
            R.string.recommendations_section_visibility_description,
        )),
    SettingsCardRegistryEntry(3, R.string.rewind_settings_title, R.drawable.sparkles, focusId = "rewind",
        keywordRes = listOf(
            R.string.rewind_settings_title, R.string.modules_open, R.string.rewind_settings_yearly,
            R.string.rewind_settings_monthly, R.string.rewind_settings_global, R.string.rewind_background_music_title,
            R.string.rewind_settings_notifications, R.string.rewind_settings_yearly_notification,
            R.string.rewind_settings_monthly_notification, R.string.rewind_settings_playlists_monthly_notification,
            R.string.rewind_settings_playlists_yearly_notification, R.string.rewind_settings_playlists_title,
        )),
    SettingsCardRegistryEntry(3, R.string.settings_section_smart_recommendations_title, R.drawable.smart_shuffle,
        keywordRes = listOf(R.string.settings_section_smart_recommendations_title, R.string.recommendations_number)),
    SettingsCardRegistryEntry(3, R.string.statistics_title, R.drawable.trending, focusId = "statistics",
        keywordRes = listOf(R.string.statistics_title, R.string.statistics_max_number_of_items, R.string.statistics_playback_duration_label)),
    SettingsCardRegistryEntry(3, R.string.playlist_top, R.drawable.playlist,
        keywordRes = listOf(R.string.playlist_top, R.string.statistics_max_number_of_items, R.string.recommendations_personal_top_title)),
    SettingsCardRegistryEntry(3, R.string.settings_data_title, R.drawable.server,
        keywordRes = listOf(R.string.settings_data_title, R.string.recommendations_action_reset_quick_picks)),
    SettingsCardRegistryEntry(3, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 4 - Data
    SettingsCardRegistryEntry(4, R.string.settings_cache_title, R.drawable.server,
        keywordRes = listOf(
            R.string.settings_cache_title, R.string.settings_cache_eviction_description, R.string.settings_cache_image_size_limit_label,
            R.string.settings_cache_action_set_custom_size, R.string.settings_cache_song_size_limit_label, R.string.settings_downloads_storage_limit_label,
            R.string.settings_cache_location_label,
        )),
    SettingsCardRegistryEntry(4, R.string.settings_section_backup_and_restore_title, R.drawable.server,
        keywordRes = listOf(
            R.string.settings_section_backup_and_restore_title, R.string.backup_action_export, R.string.backup_action_import,
            R.string.backup_action_import_legacy_migration,
        )),
    SettingsCardRegistryEntry(4, R.string.backup_auto_title, R.drawable.history,
        keywordRes = listOf(
            R.string.backup_auto_title, R.string.backup_auto_location, R.string.backup_auto_target,
            R.string.backup_include_youtube_credentials, R.string.backup_include_discord_credentials,
            R.string.backup_include_lastfm_credentials, R.string.backup_include_proxy_password,
            R.string.backup_auto_max_backups, R.string.settings_backup_before_install_label, R.string.backup_auto_interval,
            R.string.backup_auto_schedule_time, R.string.backup_auto_schedule_day_of_week,
            R.string.backup_auto_schedule_day_of_month, R.string.backup_auto_on_change,
        )),
    SettingsCardRegistryEntry(4, R.string.library_history_title, R.drawable.history, focusId = "history",
        keywordRes = listOf(
            R.string.library_history_title, R.string.modules_open, R.string.search_history_pause_label,
            R.string.player_pause_listen_history, R.string.search_history_action_clear,
            R.string.settings_history_action_clear_listening,
        )),
    SettingsCardRegistryEntry(4, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 5 - Accounts
    SettingsCardRegistryEntry(5, R.string.profile_face, R.drawable.person, focusId = "profiles",
        keywordRes = listOf(
            R.string.profile_face, R.string.profile_face_description, R.string.profile_display_name_preview_label,
            R.string.modules_open, R.string.profile_open_desc, R.string.profile_name_source,
            R.string.profile_avatar_source, R.string.profile_local_name, R.string.profile_photo,
        )),
    SettingsCardRegistryEntry(5, R.string.settings_youtube_music_title, R.drawable.ytmusic,
        keywordRes = listOf(
            R.string.settings_youtube_music_title, R.string.settings_youtube_login_enabled_label, R.string.settings_youtube_authenticated_browsing_label,
            R.string.settings_youtube_authenticated_search_label,
        )),
    SettingsCardRegistryEntry(5, R.string.sync_data_with_ytm_account, R.drawable.sync,
        keywordRes = listOf(
            R.string.sync_data_with_ytm_account, R.string.sync_status_detail, R.string.sync_now,
            R.string.sync_direction, R.string.sync_cooldown, R.string.sync_show_details,
            R.string.sync_background_guard,
        )),
    SettingsCardRegistryEntry(5, R.string.sync_imported_playlists_automatic_title, R.drawable.sync,
        keywordRes = listOf(R.string.sync_imported_playlists_automatic_title, R.string.sync_channels, R.string.sync_albums, R.string.sync_likes)),
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
    SettingsCardRegistryEntry(5, R.string.settings_discord_title, R.drawable.logo_discord,
        keywordRes = listOf(
            R.string.settings_discord_title, R.string.updates_build_beta_label, R.string.discord_enable_rich_presence,
            R.string.discord_advanced_mode, R.string.discord_activity_type, R.string.discord_advanced_name,
            R.string.discord_advanced_details, R.string.discord_advanced_state, R.string.discord_advanced_pause,
            R.string.discord_advanced_image_text, R.string.discord_advanced_logo_text,
            R.string.discord_advanced_button1, R.string.discord_advanced_button2,
            R.string.discord_advanced_show_artwork, R.string.discord_advanced_show_timestamps,
            R.string.discord_advanced_idle_close,
        )),
    SettingsCardRegistryEntry(5, R.string.settings_lastfm_title, R.drawable.logo_lastfm,
        keywordRes = listOf(
            R.string.settings_lastfm_title, R.string.lastfm_enable_scrobbling, R.string.lastfm_now_playing,
            R.string.lastfm_scrobble, R.string.lastfm_min_track_duration,
            R.string.lastfm_scrobble_threshold, R.string.lastfm_max_scrobble_delay,
        )),
    SettingsCardRegistryEntry(5, R.string.listen_together_title, R.drawable.people, focusId = "listentogether",
        keywordRes = listOf(
            R.string.listen_together_title, R.string.listen_together_server_url, R.string.listen_together_username,
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
        keywordRes = listOf(R.string.downloads_quality_format, R.string.downloads_action_refresh_quality)),
    SettingsCardRegistryEntry(6, R.string.settings_network_quality_title, R.drawable.network,
        keywordRes = listOf(R.string.settings_network_quality_title, R.string.audio_quality_format, R.string.settings_network_image_quality_label)),
    SettingsCardRegistryEntry(6, R.string.network_settings, R.drawable.network,
        keywordRes = listOf(R.string.network_settings, R.string.settings_network_data_saver_label, R.string.settings_network_data_saver_description)),
    SettingsCardRegistryEntry(6, R.string.settings_network_offline_mode_label, R.drawable.airplane, focusId = "offline",
        keywordRes = listOf(R.string.settings_network_offline_mode_label, R.string.settings_network_offline_mode_description)),
    SettingsCardRegistryEntry(6, R.string.downloads_title, R.drawable.download,
        keywordRes = listOf(
            R.string.downloads_title, R.string.settings_enable_autodownload_song,
            R.string.settings_enable_autodownload_song_when_liked,
            R.string.settings_enable_autodownload_song_when_album_bookmarked,
        )),
    SettingsCardRegistryEntry(6, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Tab 7 - Other
    SettingsCardRegistryEntry(7, R.string.library_on_device_title, R.drawable.folder,
        keywordRes = listOf(
            R.string.library_on_device_title, R.string.settings_local_music_blocked_folders_title,
            R.string.settings_local_music_blocked_folders_description, R.string.settings_local_music_folders_label,
            R.string.settings_local_music_initial_folder_description,
        )),
    SettingsCardRegistryEntry(7, R.string.settings_section_service_lifetime_title, R.drawable.battery,
        keywordRes = listOf(R.string.settings_section_service_lifetime_title, R.string.settings_service_keep_screen_on_label, R.string.settings_service_keep_screen_on_description, R.string.settings_service_ignore_battery_optimizations_label)),
    SettingsCardRegistryEntry(7, R.string.settings_section_proxy_title, R.drawable.network,
        keywordRes = listOf(
            R.string.settings_section_proxy_title, R.string.settings_proxy_enabled_label, R.string.settings_proxy_mode_label, R.string.settings_proxy_host_label,
            R.string.settings_proxy_port_label, R.string.settings_restart_required_message,
        )),
    SettingsCardRegistryEntry(7, R.string.parental_control, R.drawable.shield_checkmark,
        keywordRes = listOf(R.string.parental_control, R.string.settings_parental_age_restriction_description)),
    SettingsCardRegistryEntry(7, R.string.maintenance_title, R.drawable.shield_checkmark, focusId = "maintenance",
        keywordRes = listOf(
            R.string.maintenance_title, R.string.maintenance_open, R.string.maintenance_debug_logging_title,
            R.string.settings_logging_error_file_description, R.string.logs_action_export,
            R.string.logs_export_debug_description,
        )),
    SettingsCardRegistryEntry(7, R.string.modules_title, R.drawable.extension, focusId = "extensions",
        keywordRes = listOf(R.string.modules_title, R.string.modules_ffmpeg, R.string.modules_ffmpeg_description)),
    SettingsCardRegistryEntry(7, R.string.recovery_center, R.drawable.shortcut_rescue, focusId = "rescue",
        keywordRes = listOf(R.string.recovery_center, R.string.recovery_center_description)),
    SettingsCardRegistryEntry(7, R.string.settings_reset, R.drawable.refresh,
        keywordRes = listOf(R.string.settings_reset, R.string.settings_restore_default_settings)),
    // Privacy has its own content source; legacy tabs remain unchanged.
    SettingsCardRegistryEntry(9, R.string.privacy_screen_protection, R.drawable.shield_checkmark),
    SettingsCardRegistryEntry(9, R.string.permissions_usage_journal, R.drawable.history),
    SettingsCardRegistryEntry(9, R.string.rewind_settings_enabled, R.drawable.sparkles),
    // Tab 8 - About
    SettingsCardRegistryEntry(8, R.string.about_title, R.drawable.information),
    SettingsCardRegistryEntry(8, R.string.settings_section_troubleshooting_title, R.drawable.information,
        keywordRes = listOf(
            R.string.settings_section_troubleshooting_title, R.string.about_action_view_source,
            R.string.about_github_link_description, R.string.about_action_report_issue,
            R.string.about_action_request_feature,
        )),
    SettingsCardRegistryEntry(8, R.string.settings_section_contributors_title, R.drawable.people,
        keywordRes = listOf(R.string.settings_section_contributors_title)),
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
        (card.tab to card.titleRes) in entryMatches || card.displayTitle(context).contains(queryLower, true) ||
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
