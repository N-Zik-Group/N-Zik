package app.n_zik.android.components.settings.common

import androidx.annotation.StringRes
import app.n_zik.android.R

/** Keeps existing card links valid when their display title resource is replaced. */
internal fun settingsSectionNavigationName(@StringRes titleRes: Int): String? = when (titleRes) {
    R.string.settings_section_troubleshooting_title -> "troubleshooting"
    R.string.settings_section_proxy_title -> "proxy"
    R.string.settings_section_contributors_title -> "contributors"
    R.string.settings_section_player_action_bar_title -> "player_action_bar"
    R.string.settings_section_smart_recommendations_title -> "smart_recommendations"
    R.string.settings_section_service_lifetime_title -> "service_lifetime"
    R.string.settings_section_notification_player_title -> "notification_player"
    R.string.settings_section_backup_and_restore_title -> "title_backup_and_restore"
    R.string.settings_section_fullscreen_lyrics_title -> "full_screen_lyrics_components"
    else -> null
}
