package app.n_zik.android.components.dialog.updater

import androidx.compose.ui.draw.clip

import app.n_zik.android.updater.services.*
import app.n_zik.android.updater.models.*
import app.n_zik.android.updater.models.*
import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.updater.services.Updater
import app.n_zik.android.updater.services.UpdateDownloadManager
import app.n_zik.android.updater.models.UpdaterConstants
import app.n_zik.android.updater.models.GithubRelease
import app.n_zik.android.updater.models.MajorUpdateConfig
import app.n_zik.android.uiRoundnessShape

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.uiRoundnessShape

object BuildTransitionWarningDialog {
    var isActive: Boolean by mutableStateOf(false)
    var transitionType: String? by mutableStateOf(null)

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun Render(onConfirm: () -> Unit) {
        if (!isActive || transitionType == null) return

        var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.System)

        val titleRes = if (transitionType == "${UpdaterConstants.TYPE_STABLE}-to-${UpdaterConstants.TYPE_BETA}") R.string.stable_to_beta_warning_title else R.string.beta_to_stable_warning_title
        val messageRes = if (transitionType == "${UpdaterConstants.TYPE_STABLE}-to-${UpdaterConstants.TYPE_BETA}") R.string.stable_to_beta_warning_message else R.string.beta_to_stable_warning_message

        Dialog(onDismissRequest = { 
            isActive = false 
            onConfirm()
        }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    // Cap the column on wide screens (landscape phones, portrait tablets)
                    // so the dialog does not span the full window; narrow portrait phones
                    // are below the cap, so fillMaxWidth() still fills them
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Header
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                        animationSpec = tween(300),
                        initialScale = 0.9f
                    )
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                Color(0xFF1A1A1A)
                            } else {
                                colorPalette().background1
                            }
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.alert),
                                    contentDescription = null,
                                    tint = colorPalette().accent,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = stringResource(titleRes),
                                    style = typography().l.bold.copy(color = colorPalette().text)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                        animationSpec = tween(400),
                        initialScale = 0.9f
                    )
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                Color(0xFF1A1A1A)
                            } else {
                                colorPalette().background1
                            }
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            BasicText(
                                text = stringResource(messageRes),
                                style = typography().s.semiBold.copy(color = colorPalette().text)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm button
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(500)) + scaleIn(
                        animationSpec = tween(500),
                        initialScale = 0.9f
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape()).clickable { 
                                isActive = false
                                onConfirm()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = colorPalette().accent
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.checkmark),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicText(
                                    text = stringResource(R.string.major_update_warning_button),
                                    style = typography().s.semiBold.copy(color = Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}




