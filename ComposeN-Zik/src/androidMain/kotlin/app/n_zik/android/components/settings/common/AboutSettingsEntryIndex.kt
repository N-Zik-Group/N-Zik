package app.n_zik.android.components.settings.common

import app.n_zik.android.R

/** About actions map to visible rows; opening search never invokes their destinations. */
val AboutSettingsEntryTargets: List<SettingsEntryTarget> = listOf(
    SettingsEntryTarget("about.troubleshooting.view_the_source_code", 8, R.string.settings_section_troubleshooting_title,
        R.string.about_action_view_source, listOf(R.string.about_github_link_description)),
    SettingsEntryTarget("about.troubleshooting.report_an_issue", 8, R.string.settings_section_troubleshooting_title,
        R.string.about_action_report_issue, listOf(R.string.about_github_link_description)),
    SettingsEntryTarget("about.troubleshooting.request_a_feature_or_suggest_an_idea", 8, R.string.settings_section_troubleshooting_title,
        R.string.about_action_request_feature, listOf(R.string.about_github_link_description)),
    SettingsEntryTarget("about.contributors.translators", 8, R.string.settings_section_contributors_title, R.string.about_translators_title),
    SettingsEntryTarget("about.contributors.about_developers_designers", 8, R.string.settings_section_contributors_title, R.string.about_developers_designers_title),
)
