package app.n_zik.android.components.settings.common

import app.n_zik.android.R
import app.n_zik.android.updater.models.UpdateEndpoints

internal const val AboutAuthorSettingsTarget = "about.app.author"

internal fun aboutAuthorSearchLabel(resolve: (Int) -> String): String =
    "${resolve(R.string.about_authorship_preposition)} ${UpdateEndpoints.REAL_OWNER}"

/** About actions map to visible rows; opening search never invokes their destinations. */
val AboutSettingsEntryTargets: List<SettingsEntryTarget> = listOf(
    SettingsEntryTarget(AboutAuthorSettingsTarget, 8, R.string.about_title, R.string.about_authorship_preposition),
    SettingsEntryTarget("about.troubleshooting.view_the_source_code", 8, R.string.settings_section_troubleshooting_title,
        R.string.about_action_view_source, listOf(R.string.about_github_link_description)),
    SettingsEntryTarget("about.troubleshooting.report_an_issue", 8, R.string.settings_section_troubleshooting_title,
        R.string.about_action_report_issue, listOf(R.string.about_github_link_description)),
    SettingsEntryTarget("about.troubleshooting.request_a_feature_or_suggest_an_idea", 8, R.string.settings_section_troubleshooting_title,
        R.string.about_action_request_feature, listOf(R.string.about_github_link_description)),
    SettingsEntryTarget("about.contributors.translators", 8, R.string.settings_section_contributors_title,
        R.string.about_translators_title, countedLabelRes = R.plurals.about_translator_count),
    SettingsEntryTarget("about.contributors.about_developers_designers", 8, R.string.settings_section_contributors_title,
        R.string.about_developers_designers_title, countedLabelRes = R.plurals.about_developer_designer_count),
    SettingsEntryTarget("about.contributors.credits", 8, R.string.settings_section_contributors_title,
        R.string.about_credits_title, listOf(R.string.about_credits_open)),
)
