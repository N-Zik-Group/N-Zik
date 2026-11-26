package app.n_zik.android.components.settings.common

import android.content.Context
import app.n_zik.android.R
import app.n_zik.android.components.settings.accounts.discordSettingsCardTitle

const val SETTINGS_THEME_STORE_ROUTE = "settingsThemeStore"
const val SETTINGS_GLASS_ROUTE = "settingsGlass"

/** Legacy settings links still open the dedicated Glass destination. */
fun isGlassSettingsLink(tab: Int, focus: String): Boolean =
    tab == 1 && focus in setOf("glass", "liquid_glass", "card.1.liquid_glass")

/** Stable category routes; legacy tab numbers remain compatibility routes. */
data class SettingsCategory(val id: String, val labelRes: Int, val iconRes: Int, val group: Int, val descriptionRes: Int)

val OrganizedSettingsCategories = listOf(
    SettingsCategory("profiles", R.string.profile_title, R.drawable.person, SETTINGS_GROUP_ABOUT_UPDATE, R.string.settings_page_profiles_description),
    SettingsCategory("about", R.string.about_title, R.drawable.information, 1, R.string.settings_page_about_description),
    SettingsCategory("playback", R.string.common_playback_section_title, R.drawable.play_forward, 2, R.string.settings_page_playback_description),
    SettingsCategory("audio", R.string.settings_category_audio, R.drawable.sound_effect, 2, R.string.settings_page_audio_description),
    SettingsCategory("behavior", R.string.settings_category_behavior, R.drawable.gesture, 2, R.string.settings_page_behavior_description),
    SettingsCategory("interface", R.string.settings_category_interface, R.drawable.ui, 3, R.string.settings_page_interface_description),
    SettingsCategory("player", R.string.settings_category_player, R.drawable.player_control, 3, R.string.settings_page_player_description),
    SettingsCategory("privacy", R.string.settings_category_privacy, R.drawable.shield_checkmark, 4, R.string.settings_page_privacy_description),
    SettingsCategory("data", R.string.settings_data_title, R.drawable.server, 4, R.string.settings_page_data_description),
    SettingsCategory("downloads", R.string.downloads_title, R.drawable.download, 4, R.string.settings_page_downloads_description),
    SettingsCategory("network", R.string.settings_network_title, R.drawable.network, 4, R.string.settings_page_network_description),
    SettingsCategory("youtube", R.string.settings_youtube_music_title, R.drawable.ytmusic, 5, R.string.settings_page_youtube_description),
    SettingsCategory("services", R.string.settings_category_services, R.drawable.people, 5, R.string.settings_page_services_description),
    SettingsCategory("ai", R.string.settings_category_ai, R.drawable.sparkles, 6, R.string.settings_page_ai_description),
    SettingsCategory("rewind", R.string.rewind_settings_title, R.drawable.sparkles, 6, R.string.settings_page_rewind_description),
    SettingsCategory("maintenance", R.string.settings_category_maintenance, R.drawable.extension, 7, R.string.settings_page_maintenance_description),
    SettingsCategory("system", R.string.settings_category_system, R.drawable.settings, 7, R.string.settings_page_system_description),
)

internal fun searchSettingsCategories(context: Context, query: String): List<SettingsCategory> {
    val needle = normalizedSettingsText(query)
    if (needle.isEmpty()) return emptyList()
    val terms = needle.split(' ').sortedByDescending(String::length)
    return OrganizedSettingsCategories.filter { category ->
        val title = normalizedSettingsText(context.getString(category.labelRes))
        title.contains(needle) || matchesSettingsTerms(title.split(' ').toSet(), terms) ||
            normalizedSettingsText(context.getString(category.descriptionRes)).contains(needle)
    }
}

internal fun ambiguousSettingsCategoryIds(
    context: Context,
    categories: List<SettingsCategory> = OrganizedSettingsCategories,
): Set<String> = categories
    .groupBy { normalizedSettingsText(context.getString(it.labelRes)) }
    .values.filter { it.size > 1 }.flatten().map { it.id }.toSet()

fun SettingsCardRegistryEntry.categoryId(): String = when {
    titleRes == R.string.settings_reset -> "system"
    tab == 9 || (tab == 4 && titleRes == R.string.library_history_title) -> "privacy"
    tab == 0 -> when (titleRes) {
        R.string.settings_languages_title, R.string.settings_launcher_shortcuts_title -> "system"
        R.string.common_playback_section_title, R.string.settings_queue_management_title -> "playback"
        R.string.audio_effects -> "audio"
        R.string.player_controls, R.string.settings_picture_in_picture_title -> "player"
        else -> "behavior"
    }
    tab == 1 -> if (titleRes == R.string.settings_swipe_actions_title) "behavior" else "interface"
    tab == 2 -> "player"
    tab == 3 -> if (titleRes == R.string.rewind_settings_title) "rewind" else "ai"
    tab == 4 -> "data"
    tab == 5 -> when (titleRes) {
        R.string.profile_face -> "profiles"
        R.string.settings_discord_title, R.string.settings_lastfm_title, R.string.listen_together_title,
        R.string.common_brand_github -> "services"
        else -> "youtube"
    }
    tab == 6 -> if (titleRes == R.string.downloads_title || titleRes == R.string.settings_download_quality) "downloads" else "network"
    tab == 7 -> when (titleRes) {
        R.string.parental_control -> "playback"
        R.string.settings_section_proxy_title -> "network"
        R.string.maintenance_title, R.string.modules_title, R.string.recovery_center -> "maintenance"
        else -> "system"
    }
    else -> "about"
}

fun cardsForCategory(id: String): List<SettingsCardRegistryEntry> {
    val cards = SettingsCards.filter { it.categoryId() == id }
    val sourceTabs = when (id) {
        "player" -> listOf(2, 0)
        "privacy" -> listOf(9, 4, 3)
        "data" -> listOf(4, 7)
        else -> cards.map { it.tab }.distinct()
    }
    return sourceTabs.flatMap { tab -> cards.filter { it.tab == tab } }
        .sortedBy { it.titleRes == R.string.settings_reset }
}

internal val OrganizedSettingsCards by lazy {
    OrganizedSettingsCategories.flatMap { cardsForCategory(it.id) }
}

fun SettingsCardRegistryEntry.displayTitle(context: Context): String = when (titleRes) {
    R.string.settings_reset -> context.getString(
        R.string.settings_reset_scope, context.getString(SettingsCategories.first { it.tab == tab }.labelRes),
    )
    R.string.settings_discord_title -> discordSettingsCardTitle(context::getString)
    else -> context.getString(titleRes)
}
