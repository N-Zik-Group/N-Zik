package app.it.fast4x.rimusic.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import app.kreate.android.drawable.APP_ICON_IMAGE_BITMAP
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.extensions.contributors.ShowDevelopers
import app.it.fast4x.rimusic.extensions.contributors.ShowTranslators
import app.it.fast4x.rimusic.extensions.contributors.countDevelopers
import app.it.fast4x.rimusic.extensions.contributors.countTranslators
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.*
import app.n_zik.android.updater.models.UpdateEndpoints
import java.util.*
import app.n_zik.android.uiRoundnessShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SettingsGroupSpacer
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.ModernSettingsEntry

@ExperimentalAnimationApi
@Composable
fun About(navController: NavController) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {


        if (UiType.ViMusic.isCurrent())
            if (NavigationBarPosition.Right.isCurrent() || NavigationBarPosition.Left.isCurrent())
                Spacer(Modifier.height(Dimensions.halfheaderHeight))

         // Header 
         HeaderWithIcon(
             title = stringResource(R.string.about),
             iconId = R.drawable.information,
             enabled = false,
             showIcon = true,
             modifier = Modifier,
             onClick = {}
         )
         SettingsDescription(
            text = stringResource(R.string.about_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        ) 
 
         // Header Card - App Info
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = tween(600),
                initialScale = 0.8f
            )
        ) {
                         Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
             ) {
                 Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .height(IntrinsicSize.Max),
                     horizontalArrangement = Arrangement.spacedBy(12.dp)
                 ) {
                     // App Info Card
                     Card(
                         modifier = Modifier
                             .weight(1f)
                             .fillMaxHeight()
                    .shadow(
                        elevation = 8.dp,
                        shape = uiRoundnessShape(),
                        spotColor = colorPalette().accent.copy(alpha = 0.3f)
                    ),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = surfaceColor(colorPalette().background1)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                         modifier = Modifier
                             .fillMaxHeight()
                             .padding(20.dp),
                         horizontalAlignment = Alignment.CenterHorizontally,
                         verticalArrangement = Arrangement.Top
                     ) {
                                                 // Top content
                         Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                                         // App Icon
                     Box(
                         modifier = Modifier
                                     .size(60.dp)
                             .background(
                                 brush = Brush.radialGradient(
                                     colors = listOf(
                                         colorPalette().accent.copy(alpha = 0.1f),
                                         colorPalette().accent.copy(alpha = 0.05f)
                                     )
                                 ),
                        shape = CircleShape
                             ),
                         contentAlignment = Alignment.Center
                     ) {
                         Image(
                             bitmap = APP_ICON_IMAGE_BITMAP,
                    contentDescription = null,
                                     modifier = Modifier.size(28.dp)
                )
            }

                             Spacer(modifier = Modifier.height(12.dp))

                                         // App Name
            val pkgManager = appContext().packageManager
                     val appInfo = pkgManager.getApplicationInfo(appContext().packageName, 0)
            BasicText(
                         text = pkgManager.getApplicationLabel(appInfo).toString(),
                style = TextStyle(
                                     fontSize = typography().l.bold.fontSize,
                                     fontWeight = typography().l.bold.fontWeight,
                    color = colorPalette().text,
                             textAlign = TextAlign.Center
                         ),
                         modifier = Modifier.fillMaxWidth()
                     )

                              
                            /* Removed Spacer */
                        }

                        Spacer(modifier = Modifier.weight(1f))                                                 // Bottom content - Author & Changelog
                         Column(
                             horizontalAlignment = Alignment.CenterHorizontally,
                             verticalArrangement = Arrangement.spacedBy(8.dp)
                         ) {
                             // Author
                         Row(
                             horizontalArrangement = Arrangement.Center,
                             verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .clip(uiRoundnessShape()).clickable {
                                     val url = "${UpdateEndpoints.GITHUB}/${UpdateEndpoints.REAL_OWNER}"
                                     uriHandler.openUri(url)
                                 }
                         ) {
                             BasicText(
                                 text = stringResource(R.string.by_string),
                                     style = typography().xs.secondary.copy(
                                     textAlign = TextAlign.Center
                                 ),
                             )
                             Spacer(modifier = Modifier.width(5.dp))
                             Icon(
                                 painter = painterResource(R.drawable.github_icon),
                                 tint = colorPalette().accent,
                                 contentDescription = null,
                                     modifier = Modifier.size(14.dp)
                             )
                                 Spacer(modifier = Modifier.width(3.dp))
                             BasicText(
                                 text = UpdateEndpoints.REAL_OWNER,
                                     style = typography().xs.secondary.copy(
                                     textDecoration = TextDecoration.Underline,
                                     color = colorPalette().accent,
                                     textAlign = TextAlign.Center
                                 )
                             )
                          }
                     }
                }
            }

             }
        }
    }

    /* Removed Spacer */

        // Support & Links Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                animationSpec = tween(800),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.troubleshooting),
                icon = R.drawable.information,
                content = {
                    // Support Items
                    ModernSettingsEntry(
                        title = stringResource(R.string.view_the_source_code),
                        text = stringResource(R.string.you_will_be_redirected_to_github),
                        icon = R.drawable.github_icon,
                        onClick = { uriHandler.openUri(UpdateEndpoints.REPO_URL) }
                    )

                    ModernSettingsEntry(
                        title = stringResource(R.string.report_an_issue),
                        text = stringResource(R.string.you_will_be_redirected_to_github),
                        icon = R.drawable.trending,
                        onClick = {
                            val issuePath = "/issues/new?assignees=&labels=bug&template=bug_report.yaml"
                            uriHandler.openUri(UpdateEndpoints.REPO_URL + issuePath)
                        }
                    )

                    ModernSettingsEntry(
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

        /* Removed Spacer */

        // Contributors Section
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                animationSpec = tween(1000),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.contributors),
                icon = R.drawable.people,
                content = {
                    // Translators Section
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

                    /* Removed Spacer */

                    // Developers Section
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


        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}






