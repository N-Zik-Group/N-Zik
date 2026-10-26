package app.n_zik.android.components.settings.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.extensions.contributors.ShowDevelopers
import app.it.fast4x.rimusic.extensions.contributors.ShowTranslators
import app.it.fast4x.rimusic.extensions.contributors.countDevelopers
import app.it.fast4x.rimusic.extensions.contributors.countTranslators
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

/** Preserves the original About Contributors content. */
@Composable
internal fun AboutContributorsCard() {
    AnimatedVisibility(visible = true, enter = settingsCardEnter, exit = settingsCardExit) {
            SettingsSectionCard(
                title = stringResource(R.string.contributors),
                icon = R.drawable.people,
                content = {

                    var translatorsExpanded by remember { mutableStateOf(false) }
                    val translatorsRotation by animateFloatAsState(
                        targetValue = if (translatorsExpanded) 90f else 0f,
                        animationSpec = tween(300),
                        label = stringResource(R.string.txt_translators_rotation)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clip(uiRoundnessShape()).clickable { translatorsExpanded = !translatorsExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.chevron_forward),
                            tint = colorPalette().accent,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer(rotationZ = translatorsRotation)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = "${countTranslators()} " + stringResource(R.string.translators),
                            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary)
                        )
                    }

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
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp)
                        ) {
                            SettingsDescription(text = stringResource(R.string.in_alphabetical_order))
                            ShowTranslators()
                        }
                    }



                    var developersExpanded by remember { mutableStateOf(false) }
                    val developersRotation by animateFloatAsState(
                        targetValue = if (developersExpanded) 90f else 0f,
                        animationSpec = tween(300),
                        label = stringResource(R.string.txt_developers_rotation)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clip(uiRoundnessShape()).clickable { developersExpanded = !developersExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.chevron_forward),
                            tint = colorPalette().accent,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer(rotationZ = developersRotation)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = "${countDevelopers()} ${stringResource(R.string.about_developers_designers)}",
                            style = typography().xs.semiBold.copy(color = colorPalette().textSecondary)
                        )
                    }

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
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp)
                        ) {
                            SettingsDescription(text = stringResource(R.string.in_alphabetical_order))
                            ShowDevelopers()
                        }
                    }
                }
            )
        }
}
