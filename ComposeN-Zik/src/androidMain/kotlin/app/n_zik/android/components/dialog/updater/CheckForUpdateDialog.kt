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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.CheckUpdateState
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.BuildConfig
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey

object CheckForUpdateDialog {

    private var isCanceled: Boolean by mutableStateOf(false)
    var isActive: Boolean by mutableStateOf(false)

    fun onDismiss() {
        isCanceled = true
        isActive = false
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun Render() {
        if (isCanceled || !isActive) return

        var checkUpdateState by rememberPreference(checkUpdateStateKey, CheckUpdateState.Enabled)
        var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.System)
        val checkBetaUpdates by rememberPreference(checkBetaUpdatesKey, Updater.extractVersionSuffix(BuildConfig.VERSION_NAME) == UpdaterConstants.SUFFIX_BETA.removePrefix("-"))

        Dialog(onDismissRequest = { onDismiss() }) {
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
                // Header with title
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
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
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
                                    painter = painterResource(R.drawable.update),
                                    contentDescription = null,
                                    tint = colorPalette().accent,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = stringResource(R.string.check_at_github_for_updates),
                                    style = typography().l.bold.copy(color = colorPalette().text)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicText(
                                    text = stringResource(R.string.when_an_update_is_available_you_will_be_asked_if_you_want_to_install_info),
                                    style = typography().xs.copy(color = colorPalette().textSecondary)
                                )
                                BasicText(
                                    text = stringResource(R.string.but_these_updates_would_not_go_through),
                                    style = typography().xs.copy(color = colorPalette().textSecondary)
                                )
                                BasicText(
                                    text = stringResource(R.string.you_can_still_turn_it_on_or_off_from_the_settings),
                                    style = typography().xs.copy(color = colorPalette().textSecondary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Check for updates
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                        animationSpec = tween(400),
                        initialScale = 0.9f
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape()).clickable {
                                onDismiss()
                                Updater.checkForUpdate(checkBetaUpdates = checkBetaUpdates)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = colorPalette().accent
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            BasicText(
                                text = stringResource(R.string.check_update),
                                style = typography().xs.semiBold.copy(color = Color.White),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.8f)
                            )
                            Icon(
                                painter = painterResource(R.drawable.update),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Option 2: Cancel (skip this time)
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
                            .clip(uiRoundnessShape()).clickable { onDismiss() },
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            }
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            BasicText(
                                text = stringResource(R.string.cancel),
                                style = typography().xs.semiBold.copy(color = colorPalette().text),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.8f)
                            )
                            Icon(
                                painter = painterResource(R.drawable.arrow_left),
                                contentDescription = null,
                                tint = colorPalette().accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Option 3: Turn off auto check
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                        animationSpec = tween(600),
                        initialScale = 0.9f
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape()).clickable {
                                checkUpdateState = CheckUpdateState.Disabled
                                onDismiss()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                                Color(0xFF1A1A1A) // Gray dark for pitch black themes
                            } else {
                                colorPalette().background1
                            }
                        ),
                        shape = uiRoundnessShape(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            BasicText(
                                text = stringResource(R.string.turn_off),
                                style = typography().xs.semiBold.copy(color = colorPalette().text),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.8f)
                            )
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                                tint = colorPalette().accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}





