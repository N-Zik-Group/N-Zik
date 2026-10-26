package app.n_zik.android.components.settings.common

import app.n_zik.android.R

/** Stable category routes; legacy tab numbers remain compatibility routes. */
data class SettingsCategory(val id: String, val labelRes: Int, val iconRes: Int, val group: Int)

val OrganizedSettingsCategories = listOf(
    SettingsCategory("about", R.string.about, R.drawable.information, 1),
    SettingsCategory("playback", R.string.playback, R.drawable.play_forward, 2),
    SettingsCategory("audio", R.string.settings_category_audio, R.drawable.sound_effect, 2),
    SettingsCategory("behavior", R.string.settings_category_behavior, R.drawable.gesture, 2),
    SettingsCategory("interface", R.string.settings_category_interface, R.drawable.ui, 3),
    SettingsCategory("player", R.string.settings_category_player, R.drawable.player_control, 3),
    SettingsCategory("data", R.string.tab_data, R.drawable.server, 4),
    SettingsCategory("downloads", R.string.download, R.drawable.arrow_down, 4),
    SettingsCategory("network", R.string.tab_network, R.drawable.network, 4),
    SettingsCategory("youtube", R.string.youtube_music, R.drawable.ytmusic, 5),
    SettingsCategory("profiles", R.string.profiles, R.drawable.person, 5),
    SettingsCategory("services", R.string.settings_category_services, R.drawable.people, 5),
    SettingsCategory("ai", R.string.settings_category_ai, R.drawable.sparkles, 6),
    SettingsCategory("rewind", R.string.rw_settings_title, R.drawable.sparkles, 6),
    SettingsCategory("maintenance", R.string.settings_category_maintenance, R.drawable.extension, 7),
    SettingsCategory("system", R.string.settings_category_system, R.drawable.settings, 7),
)

fun SettingsCardRegistryEntry.categoryId(): String = when {
    titleRes == R.string.settings_reset -> "system"
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
    tab == 6 -> if (titleRes == R.string.download) "downloads" else "network"
    tab == 7 -> when (titleRes) {
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
