package app.n_zik.android.components.settings.common

import app.n_zik.android.R

/** Stable category routes; legacy tab numbers remain compatibility routes. */
data class SettingsCategory(val id: String, val labelRes: Int, val iconRes: Int, val group: Int, val descriptionRes: Int)

val OrganizedSettingsCategories = listOf(
    SettingsCategory("about", R.string.about, R.drawable.information, 1, R.string.settings_page_about_description),
    SettingsCategory("playback", R.string.playback, R.drawable.play_forward, 2, R.string.settings_page_playback_description),
    SettingsCategory("audio", R.string.settings_category_audio, R.drawable.sound_effect, 2, R.string.settings_page_audio_description),
    SettingsCategory("behavior", R.string.settings_category_behavior, R.drawable.gesture, 2, R.string.settings_page_behavior_description),
    SettingsCategory("interface", R.string.settings_category_interface, R.drawable.ui, 3, R.string.settings_page_interface_description),
    SettingsCategory("player", R.string.settings_category_player, R.drawable.player_control, 3, R.string.settings_page_player_description),
    SettingsCategory("privacy", R.string.settings_category_privacy, R.drawable.shield_checkmark, 4, R.string.settings_page_privacy_description),
    SettingsCategory("data", R.string.tab_data, R.drawable.server, 4, R.string.settings_page_data_description),
    SettingsCategory("downloads", R.string.download, R.drawable.download, 4, R.string.settings_page_downloads_description),
    SettingsCategory("network", R.string.tab_network, R.drawable.network, 4, R.string.settings_page_network_description),
    SettingsCategory("youtube", R.string.youtube_music, R.drawable.ytmusic, 5, R.string.settings_page_youtube_description),
    SettingsCategory("profiles", R.string.profiles, R.drawable.person, 5, R.string.settings_page_profiles_description),
    SettingsCategory("services", R.string.settings_category_services, R.drawable.people, 5, R.string.settings_page_services_description),
    SettingsCategory("ai", R.string.settings_category_ai, R.drawable.sparkles, 6, R.string.settings_page_ai_description),
    SettingsCategory("rewind", R.string.rw_settings_title, R.drawable.sparkles, 6, R.string.settings_page_rewind_description),
    SettingsCategory("maintenance", R.string.settings_category_maintenance, R.drawable.extension, 7, R.string.settings_page_maintenance_description),
    SettingsCategory("system", R.string.settings_category_system, R.drawable.settings, 7, R.string.settings_page_system_description),
)

fun SettingsCardRegistryEntry.categoryId(): String = when {
    titleRes == R.string.settings_reset -> "system"
    tab == 9 || (tab == 4 && titleRes == R.string.history) || (tab == 3 && titleRes == R.string.tab_data) -> "privacy"
    tab == 0 -> when (titleRes) {
        R.string.languages, R.string.app_shortcuts_settings -> "system"
        R.string.playback, R.string.queue_management -> "playback"
        R.string.audio_effects -> "audio"
        R.string.player_controls, R.string.picture_in_picture -> "player"
        else -> "behavior"
    }
    tab == 1 -> if (titleRes == R.string.swipe_to_action) "behavior" else "interface"
    tab == 2 -> "player"
    tab == 3 -> if (titleRes == R.string.rw_settings_title) "rewind" else "ai"
    tab == 4 -> "data"
    tab == 5 -> when (titleRes) {
        R.string.profile_face -> "profiles"
        R.string.social_discord, R.string.social_lastfm, R.string.listen_together -> "services"
        else -> "youtube"
    }
    tab == 6 -> if (titleRes == R.string.download || titleRes == R.string.settings_download_quality) "downloads" else "network"
    tab == 7 -> when (titleRes) {
        R.string.androidheadunit -> "interface"
        R.string.parental_control -> "playback"
        R.string.on_device -> "data"
        R.string.proxy -> "network"
        R.string.maintenance, R.string.modules_title, R.string.rescue_center -> "maintenance"
        else -> "system"
    }
    else -> "about"
}

fun cardsForCategory(id: String): List<SettingsCardRegistryEntry> = SettingsCards.filter { it.categoryId() == id }

fun SettingsCardRegistryEntry.displayTitle(context: android.content.Context): String =
    if (titleRes == R.string.settings_reset)
        context.getString(R.string.settings_reset_scope, context.getString(SettingsCategories.first { it.tab == tab }.labelRes))
    else context.getString(titleRes)
