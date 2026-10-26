package app.n_zik.android.components.settings.common

import app.n_zik.android.R

/** About actions map to visible rows; opening search never invokes their destinations. */
val AboutSettingsEntryTargets: List<SettingsEntryTarget> = listOf(
    SettingsEntryTarget("about.troubleshooting.view_the_source_code", 8, R.string.troubleshooting,
        R.string.view_the_source_code, listOf(R.string.you_will_be_redirected_to_github)),
    SettingsEntryTarget("about.troubleshooting.report_an_issue", 8, R.string.troubleshooting,
        R.string.report_an_issue, listOf(R.string.you_will_be_redirected_to_github)),
    SettingsEntryTarget("about.troubleshooting.request_a_feature_or_suggest_an_idea", 8, R.string.troubleshooting,
        R.string.request_a_feature_or_suggest_an_idea, listOf(R.string.you_will_be_redirected_to_github)),
    SettingsEntryTarget("about.contributors.translators", 8, R.string.contributors, R.string.translators),
    SettingsEntryTarget("about.contributors.about_developers_designers", 8, R.string.contributors, R.string.about_developers_designers),
)
