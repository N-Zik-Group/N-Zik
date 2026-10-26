package app.n_zik.android.components.settings.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.ModernSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.updater.models.UpdateEndpoints

/** Preserves the original About Support content. */
@Composable
internal fun AboutSupportCard() {
    val uriHandler = LocalUriHandler.current
    AnimatedVisibility(visible = true, enter = settingsCardEnter, exit = settingsCardExit) {
            SettingsSectionCard(
                title = stringResource(R.string.troubleshooting),
                icon = R.drawable.information,
                content = {

                    ModernSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("about.troubleshooting.view_the_source_code"),
                        title = stringResource(R.string.view_the_source_code),
                        text = stringResource(R.string.you_will_be_redirected_to_github),
                        icon = R.drawable.github_icon,
                        onClick = { uriHandler.openUri(UpdateEndpoints.REPO_URL) }
                    )

                    ModernSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("about.troubleshooting.report_an_issue"),
                        title = stringResource(R.string.report_an_issue),
                        text = stringResource(R.string.you_will_be_redirected_to_github),
                        icon = R.drawable.trending,
                        onClick = {
                            val issuePath = "/issues/new?assignees=&labels=bug&template=bug_report.yaml"
                            uriHandler.openUri(UpdateEndpoints.REPO_URL + issuePath)
                        }
                    )

                    ModernSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("about.troubleshooting.request_a_feature_or_suggest_an_idea"),
                        title = stringResource(R.string.request_a_feature_or_suggest_an_idea),
                        text = stringResource(R.string.you_will_be_redirected_to_github),
                        icon = R.drawable.star_brilliant,
                        onClick = {
                            val issuePath = "/issues/new?assignees=&labels=feature_request&template=feature_request.yaml"
                            uriHandler.openUri(UpdateEndpoints.REPO_URL + issuePath)
                        }
                    )
                }
            )
        }
}
