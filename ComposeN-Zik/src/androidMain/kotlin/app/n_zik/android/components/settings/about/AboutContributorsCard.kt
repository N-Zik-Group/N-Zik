package app.n_zik.android.components.settings.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.extensions.contributors.ShowDevelopers
import app.n_zik.android.extensions.contributors.ShowTranslators
import app.n_zik.android.extensions.contributors.countDevelopers
import app.n_zik.android.extensions.contributors.countTranslators
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.ModernSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

/** Preserves the original About Contributors content. */
@Composable
internal fun AboutContributorsCard() {
    AnimatedVisibility(visible = true, enter = settingsCardEnter, exit = settingsCardExit) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_contributors_title),
                icon = R.drawable.people,
                content = {

                    var translatorsExpanded by remember { mutableStateOf(false) }

                    ModernSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("about.contributors.translators"),
                        title = pluralStringResource(R.plurals.about_translator_count, countTranslators(), countTranslators()),
                        text = stringResource(R.string.about_translators_description),
                        icon = R.drawable.translate,
                        expanded = translatorsExpanded,
                        onClick = { translatorsExpanded = !translatorsExpanded }
                    )

                    AnimatedVisibility(
                        visible = translatorsExpanded,
                        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                            animationSpec = tween(300),
                            initialScale = 0.95f
                        ),
                        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                            animationSpec = tween(200),
                            targetScale = 0.95f
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            SettingsDescription(text = stringResource(R.string.about_contributors_alphabetical_order_label))
                            ShowTranslators()
                        }
                    }



                    var developersExpanded by remember { mutableStateOf(false) }

                    ModernSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("about.contributors.about_developers_designers"),
                        title = pluralStringResource(R.plurals.about_developer_designer_count, countDevelopers(), countDevelopers()),
                        text = stringResource(R.string.about_developers_description),
                        icon = R.drawable.git_pull_request_outline,
                        expanded = developersExpanded,
                        onClick = { developersExpanded = !developersExpanded }
                    )

                    AnimatedVisibility(
                        visible = developersExpanded,
                        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                            animationSpec = tween(300),
                            initialScale = 0.95f
                        ),
                        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                            animationSpec = tween(200),
                            targetScale = 0.95f
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            SettingsDescription(text = stringResource(R.string.about_contributors_alphabetical_order_label))
                            ShowDevelopers()
                        }
                    }

                    AboutCreditsEntry()
                }
            )
        }
}
