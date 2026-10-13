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
import app.n_zik.android.BuildConfig
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
import app.n_zik.android.updater.services.Updater
import app.n_zik.android.components.dialog.updater.NewUpdateAvailableDialog
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import java.text.SimpleDateFormat
import java.util.*
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.enums.NavRoutes
import app.kreate.android.me.knighthat.utils.Toaster
import androidx.compose.runtime.remember
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.it.fast4x.rimusic.utils.lastUpdateCheckKey
import androidx.navigation.NavController
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import java.util.Locale
import java.util.Date

@ExperimentalAnimationApi
@Composable
fun About(navController: NavController) {
    val uriHandler = LocalUriHandler.current
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    

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
 
         // Header Cards Row - App Info & Update Check
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
                    containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                        Color(0xFF1A1A1A) // Gray dark for pitch black themes
                    } else {
                        colorPalette().background1
                    }
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

                             Spacer(modifier = Modifier.height(6.dp))

                                                                                     // Version
                            // Version and Badge
                            Row(
                                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    text = "v${getVersionName()}",
                                    style = typography().xs.secondary.copy(
                                        textAlign = TextAlign.Center
                                    )
                                )
                                
                                val badge = Updater.flavorLabel(
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.BUILD_TYPE.equals("foss", ignoreCase = true)
                                )
                                if (badge != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = colorPalette().accent.copy(alpha = 0.2f),
                                                shape = uiRoundnessShape()
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        // Single source of truth with the header badge
                                        // (Updater.flavorLabel): FULL / FULL 32, MINIFIED /
                                        // MINIFIED 32, BETA / BETA 32, DEV / DEV 32, FOSS, DEBUG
                                        val badgeText = badge.literal
                                            ?: stringResource(badge.resId) + if (badge.append32) " 32" else ""
                                        BasicText(
                                            text = badgeText.uppercase(),
                                            style = typography().xxs.bold.copy(color = colorPalette().accent)
                                        )
                                    }
                                }
                            }
                              
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

                     // Update Check Card
                     Card(
                         modifier = Modifier
                             .weight(1f)
                             .fillMaxHeight()
                             .run {
                                 if (BuildConfig.IS_AUTOUPDATE) {
                                     clickable { navController.navigate(NavRoutes.updater.name) }
                                 } else this
                             }
                             .shadow(
                                 elevation = 8.dp,
                                 shape = uiRoundnessShape(),
                                 spotColor = colorPalette().accent.copy(alpha = 0.3f)
                             ),
                     shape = uiRoundnessShape(),
                     colors = CardDefaults.cardColors(
                         containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                             Color(0xFF1A1A1A) // Gray dark for pitch black themes
                         } else {
                             colorPalette().background1
                         }
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
                        if (BuildConfig.IS_AUTOUPDATE) {
                            // Top content
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Update Icon
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
                                    Icon(
                                        painter = painterResource(R.drawable.update),
                                        tint = colorPalette().accent,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
    
                                Spacer(modifier = Modifier.height(12.dp))
    
                                // Update Title
                                BasicText(
                                    text = stringResource(R.string.update),
                                    style = TextStyle(
                                        fontSize = typography().l.bold.fontSize,
                                        fontWeight = typography().l.bold.fontWeight,
                                        color = colorPalette().text,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
    
                                Spacer(modifier = Modifier.height(6.dp))
    
                                val newVersion = Updater.githubRelease?.tagName ?: ""
                                val hasUpdate = Updater.githubRelease != null && Updater.isVersionNewer(newVersion, BuildConfig.VERSION_NAME)
                                if (Updater.githubRelease != null) {
                                    BasicText(
                                        text = stringResource(if (hasUpdate) R.string.update_available else R.string.up_to_date),
                                        style = typography().xs.secondary.copy(
                                            textAlign = TextAlign.Center,
                                            color = if (hasUpdate) colorPalette().accent else colorPalette().textSecondary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    BasicText(
                                        text = stringResource(R.string.audio_quality_format_unknown),
                                        style = typography().xs.secondary.copy(
                                            textAlign = TextAlign.Center,
                                            color = colorPalette().textSecondary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                
                                val lastCheckTime by rememberPreference(lastUpdateCheckKey, 0L)
                                val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                                val lastCheckStr = if (lastCheckTime > 0) sdf.format(Date(lastCheckTime)) else stringResource(R.string.never_checked)
                                BasicText(
                                    text = if (lastCheckTime > 0) stringResource(R.string.last_check, lastCheckStr) else stringResource(R.string.never_checked),
                                    style = typography().xxs.secondary.copy(
                                        textAlign = TextAlign.Center,
                                        color = colorPalette().textSecondary.copy(alpha = 0.7f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                                
                                /* Removed Spacer */
                            }
    
                            Spacer(modifier = Modifier.weight(1f))
    
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(uiRoundnessShape()).clickable {
                                        // Same (profile-scoped) "preferences" file the beta toggle in
                                        // the updater settings writes to — the legacy "settings"
                                        // file never holds this key
                                        val checkBeta = appContext().preferences.getBoolean(checkBetaUpdatesKey, Updater.isBetaFamily(BuildConfig.VERSION_NAME))
                                        Toaster.i(R.string.checking_for_updates)
                                        Updater.checkForUpdate(isForced = true, checkBetaUpdates = checkBeta, showDialog = false)
                                        navController.navigate(NavRoutes.updater.name)
                                    },
                                colors = CardDefaults.cardColors(containerColor = colorPalette().accent),
                                shape = uiRoundnessShape()
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    BasicText(
                                        text = stringResource(R.string.check_update),
                                        style = typography().xs.semiBold.copy(color = Color.White)
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
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
                                    Icon(
                                        painter = painterResource(R.drawable.update),
                                        tint = colorPalette().textSecondary.copy(alpha = 0.5f),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                BasicText(
                                    text = stringResource(R.string.update),
                                    style = TextStyle(
                                        fontSize = typography().l.bold.fontSize,
                                        fontWeight = typography().l.bold.fontWeight,
                                        color = colorPalette().textSecondary.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = stringResource(R.string.description_app_not_installed_by_apk),
                                    style = typography().xxs.secondary.copy(
                                        textAlign = TextAlign.Center,
                                        color = colorPalette().textSecondary.copy(alpha = 0.7f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
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






