package app.n_zik.android.components.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.glass.*
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.settingsSearchTarget

internal enum class GlassDestinationGroup(@StringRes val title: Int, val icon: Int, @StringRes val summary: Int) {
    Navigation(R.string.glass_destination_navigation, R.drawable.menu, R.string.glass_navigation_summary),
    Playback(R.string.glass_destination_playback, R.drawable.musical_notes, R.string.glass_playback_summary),
    Pages(R.string.glass_destination_pages, R.drawable.search, R.string.glass_pages_summary),
    Windows(R.string.glass_destination_windows, R.drawable.sort_grid, R.string.glass_windows_summary),
    Shared(R.string.glass_destination_shared, R.drawable.color_palette, R.string.glass_shared_summary),
}

internal data class GlassPolicyRow(
    @StringRes val title: Int,
    val area: GlassArea,
    val scope: GlassScope = GlassScope.Shared,
    @StringRes val description: Int,
)

internal fun glassDestinationRows(group: GlassDestinationGroup): List<GlassPolicyRow> = when (group) {
    GlassDestinationGroup.Navigation -> listOf(
        GlassPolicyRow(R.string.glass_navigation_bars, GlassArea.Nav, description = R.string.glass_navigation_bars_description),
        GlassPolicyRow(R.string.glass_navigation_header, GlassArea.Header, description = R.string.glass_navigation_header_description))
    GlassDestinationGroup.Playback -> listOf(
        GlassPolicyRow(R.string.glass_mini_player, GlassArea.MiniPlayer, description = R.string.glass_mini_player_description),
        GlassPolicyRow(R.string.glass_role_player_header, GlassArea.ActionButtons, GlassScope.PlayerHeader, description = R.string.glass_role_player_header_description),
        GlassPolicyRow(R.string.glass_role_player_information, GlassArea.ActionButtons, GlassScope.PlayerInformation, description = R.string.glass_role_player_information_description),
        GlassPolicyRow(R.string.glass_role_player_time, GlassArea.ActionButtons, GlassScope.PlayerTime, description = R.string.glass_role_player_time_description),
        GlassPolicyRow(R.string.glass_role_player_seek, GlassArea.ActionButtons, GlassScope.PlayerSeek, description = R.string.glass_role_player_seek_description),
        GlassPolicyRow(R.string.glass_role_player_transport, GlassArea.ActionButtons, GlassScope.PlayerTransport, description = R.string.glass_role_player_transport_description),
        GlassPolicyRow(R.string.glass_role_player_actions, GlassArea.ActionButtons, GlassScope.PlayerActions, description = R.string.glass_role_player_actions_description),
        GlassPolicyRow(R.string.glass_role_lyrics, GlassArea.ActionButtons, GlassScope.Lyrics, description = R.string.glass_role_lyrics_description),
        GlassPolicyRow(R.string.glass_role_visualizer, GlassArea.ActionButtons, GlassScope.Visualizer, description = R.string.glass_role_visualizer_description),
        GlassPolicyRow(R.string.glass_role_queue_surface, GlassArea.Menus, GlassScope.Queue, description = R.string.glass_role_queue_surface_description),
        GlassPolicyRow(R.string.glass_role_queue_actions, GlassArea.ActionButtons, GlassScope.Queue, description = R.string.glass_role_queue_actions_description))
    GlassDestinationGroup.Pages -> listOf(
        GlassPolicyRow(R.string.glass_role_home_actions, GlassArea.ActionButtons, GlassScope.Home, description = R.string.glass_role_home_actions_description),
        GlassPolicyRow(R.string.glass_role_home_cards, GlassArea.MoodCards, GlassScope.Home, description = R.string.glass_role_home_cards_description),
        GlassPolicyRow(R.string.glass_role_library_actions, GlassArea.ActionButtons, GlassScope.Library, description = R.string.glass_role_library_actions_description),
        GlassPolicyRow(R.string.glass_role_search_actions, GlassArea.ActionButtons, GlassScope.Search, description = R.string.glass_role_search_actions_description),
        GlassPolicyRow(R.string.glass_role_details_actions, GlassArea.ActionButtons, GlassScope.Details, description = R.string.glass_role_details_actions_description),
        GlassPolicyRow(R.string.glass_role_details_cards, GlassArea.SettingsCards, GlassScope.Details, description = R.string.glass_role_details_cards_description),
        GlassPolicyRow(R.string.glass_role_settings_actions, GlassArea.ActionButtons, GlassScope.SettingsProfiles, description = R.string.glass_role_settings_actions_description),
        GlassPolicyRow(R.string.glass_role_settings_cards, GlassArea.SettingsCards, GlassScope.SettingsProfiles, description = R.string.glass_role_settings_cards_description),
        GlassPolicyRow(R.string.glass_role_rewind_actions, GlassArea.ActionButtons, GlassScope.Rewind, description = R.string.glass_role_rewind_actions_description),
        GlassPolicyRow(R.string.glass_role_rewind_cards, GlassArea.MoodCards, GlassScope.Rewind, description = R.string.glass_role_rewind_cards_description),
        GlassPolicyRow(R.string.glass_role_onboarding_actions, GlassArea.ActionButtons, GlassScope.Onboarding, description = R.string.glass_role_onboarding_actions_description),
        GlassPolicyRow(R.string.glass_role_onboarding_cards, GlassArea.SettingsCards, GlassScope.Onboarding, description = R.string.glass_role_onboarding_cards_description))
    GlassDestinationGroup.Windows -> listOf(
        GlassPolicyRow(R.string.glass_where_menus, GlassArea.Menus, description = R.string.glass_where_menus_description),
        GlassPolicyRow(R.string.glass_where_dropdowns, GlassArea.Dropdowns, description = R.string.glass_where_dropdowns_description),
        GlassPolicyRow(R.string.glass_where_dialogs, GlassArea.Dialogs, description = R.string.glass_where_dialogs_description),
        GlassPolicyRow(R.string.glass_where_dialog_buttons, GlassArea.DialogButtons, description = R.string.glass_where_dialog_buttons_description),
        GlassPolicyRow(R.string.glass_role_messages, GlassArea.Menus, GlassScope.Toast, description = R.string.glass_role_messages_description))
    GlassDestinationGroup.Shared -> listOf(
        GlassPolicyRow(R.string.glass_where_action_buttons, GlassArea.ActionButtons, description = R.string.glass_where_action_buttons_description),
        GlassPolicyRow(R.string.glass_common_cards, GlassArea.SettingsCards, description = R.string.glass_common_cards_description),
        GlassPolicyRow(R.string.glass_common_discovery_cards, GlassArea.MoodCards, description = R.string.glass_common_discovery_cards_description),
        GlassPolicyRow(R.string.glass_where_search_bars, GlassArea.SearchBars, description = R.string.glass_where_search_bars_description),
        GlassPolicyRow(R.string.glass_common_current_track, GlassArea.NowPlaying, description = R.string.glass_common_current_track_description),
        GlassPolicyRow(R.string.glass_where_image_rims, GlassArea.Images, description = R.string.glass_where_image_rims_description))
}

/** Match the destination instead of repeating the palette for every selector. */
private val GlassPolicyRow.icon: Int
    get() = when {
        area == GlassArea.SettingsCards -> R.drawable.settings
        area == GlassArea.MoodCards -> R.drawable.image
        else -> when (scope) {
        GlassScope.PlayerTransport -> R.drawable.play
        GlassScope.PlayerActions -> R.drawable.ellipsis_horizontal
        GlassScope.Lyrics -> R.drawable.song_lyrics
        GlassScope.Visualizer -> R.drawable.equalizer
        GlassScope.Queue -> R.drawable.enqueue
        GlassScope.Home -> R.drawable.sparkles
        GlassScope.Library -> R.drawable.library
        GlassScope.Search -> R.drawable.search
        GlassScope.Details -> R.drawable.album
        GlassScope.SettingsProfiles -> R.drawable.settings
        GlassScope.Rewind -> R.drawable.history
        GlassScope.Onboarding -> R.drawable.checkmark
        else -> when (area) {
            GlassArea.Nav -> R.drawable.menu
            GlassArea.Header -> R.drawable.search
            GlassArea.MiniPlayer, GlassArea.NowPlaying -> R.drawable.musical_notes
            GlassArea.Menus, GlassArea.Dropdowns -> R.drawable.ellipsis_horizontal
            GlassArea.Dialogs, GlassArea.DialogButtons -> R.drawable.sort_grid
            GlassArea.Images -> R.drawable.image
            GlassArea.SearchBars -> R.drawable.search
            GlassArea.SettingsCards -> R.drawable.settings
            GlassArea.MoodCards -> R.drawable.album
            GlassArea.ActionButtons -> R.drawable.gesture
        }
    }

}

/** Global search follows the same localized destinations and roles displayed on this page. */
internal fun glassDestinationKeywordResources(): List<Int> = GlassDestinationGroup.entries.flatMap { group ->
    listOf(group.title, group.summary) + glassDestinationRows(group).flatMap { listOf(it.title, it.description) }
}.plus(glassInteractionKeywordResources()).distinct()

@Composable
internal fun GlassDestinationCard(group: GlassDestinationGroup, config: GlassSettings, store: GlassStore, target: String = "") {
    var expanded by rememberSaveable(group) { mutableStateOf(glassDestinationRows(group).any { it.searchId == target }) }
    LaunchedEffect(target) {
        if (glassDestinationRows(group).any { it.searchId == target }) expanded = true
    }
    SettingsSectionCard(
        title = stringResource(group.title),
        icon = group.icon,
        description = stringResource(group.summary),
        modifier = Modifier.testTag("settings.glass.group.${group.name}"),
        expanded = expanded,
        onHeaderClick = { expanded = !expanded },
        headerModifier = Modifier.testTag("settings.glass.group.${group.name}.toggle"),
        content = {
            glassDestinationRows(group).forEach { row -> GlassPolicySelector(row, config, store) }
        },
    )
}

@Composable
private fun GlassPolicySelector(row: GlassPolicyRow, config: GlassSettings, store: GlassStore) {
    val contextual = row.scope != GlassScope.Shared
    val selected = config.contextMode(row.scope, row.area)
    val sharedMode = config.contextMode(GlassScope.Shared, row.area)
    val tag = if (contextual) "${row.scope.name}.${row.area.name}" else row.area.name
    Column(Modifier.testTag("settings.glass.area.$tag").settingsSearchTarget(row.searchId)) {
        if (row.area == GlassArea.Images) {
            OtherEnumValueSelectorSettingsEntry(
                title = stringResource(row.title), text = stringResource(row.description),
                icon = row.icon,
                selectedValue = if (selected == GlassAreaMode.Disabled) GlassImageMode.Disabled else GlassImageMode.ContourOnly,
                onValueSelected = { store.setAreaMode(row.area, if (it == GlassImageMode.Disabled) GlassAreaMode.Disabled else GlassAreaMode.ContourOnly) },
                modifier = Modifier.testTag("settings.glass.mode.$tag"),
                valueText = { stringResource(if (it == GlassImageMode.Disabled) R.string.glass_mode_disabled else R.string.glass_mode_contour_only) },
            )
        } else {
            OtherEnumValueSelectorSettingsEntry(
                title = stringResource(row.title),
                text = stringResource(row.description),
                icon = row.icon, selectedValue = selected,
                onValueSelected = {
                    if (contextual) store.setContextMode(row.scope, row.area, it.takeUnless { mode -> mode == sharedMode })
                    else store.setAreaMode(row.area, it)
                },
                modifier = Modifier.testTag("settings.glass.mode.$tag"),
                valueText = { mode -> stringResource(when (mode) {
                    GlassAreaMode.Disabled -> R.string.glass_mode_disabled
                    GlassAreaMode.ContourOnly -> R.string.glass_mode_contour_only
                    GlassAreaMode.GlassOnly -> R.string.glass_mode_glass_only
                    GlassAreaMode.GlassAndContour -> R.string.glass_mode_glass_and_contour
                }) },
            )
        }
    }
}

/** Scope and area identify policy rows independently of translations and ordering. */
internal val GlassPolicyRow.searchId: String
    get() = "glass.destination.${scope.name}.${area.name}"
