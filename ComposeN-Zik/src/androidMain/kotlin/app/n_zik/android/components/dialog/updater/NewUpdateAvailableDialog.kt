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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.updateCancelledKey
import app.n_zik.android.appContext
import app.kreate.android.me.knighthat.utils.Repository
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.uiRoundnessShape

@Composable
fun DialogText(
    text: String,
    style: TextStyle,
    spacerHeight: Dp = 10.dp
) {
    BasicText(
        text = text,
        style = style,
    )
    Spacer(Modifier.height(spacerHeight))
}

object NewUpdateAvailableDialog {

    /**
     * `false` by default.
     *
     * When this field is set to `true`, it'll
     * keep the dialog from showing up even when
     * [isActive] is `true`.
     *
     * This is used to prevent user from getting
     * annoyed by constant pop-up saying that
     * there's a new update available.
     *
     * This value will be reset once the app is
     * restart, either by user or by setting it
     * programmatically.
     */
    var isCancelled: Boolean by mutableStateOf( !BuildConfig.IS_AUTOUPDATE )

    var isActive: Boolean by mutableStateOf( false )

    fun onDismiss() {
        isCancelled = true
        isActive = false
        
        // Mark update as cancelled when user cancels (but don't update the check time)
        val sharedPrefs = appContext().getSharedPreferences("settings", 0)
        sharedPrefs.edit()
            .putBoolean(updateCancelledKey, true)
            .apply()
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun Render(onNavigateToUpdater: () -> Unit = {}) {
        if( isCancelled || !isActive ) return

        val uriHandler = LocalUriHandler.current
        var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.System)

        Dialog(onDismissRequest = { onDismiss() }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .testTag("updateDialogColumn")
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
                                    text = "${stringResource(Updater.getBuildTypeStringRes())} ${stringResource(R.string.update_available)}",
                                    style = typography().l.bold.copy(color = colorPalette().text)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                BasicText(
                                    text = stringResource(R.string.app_update_dialog_version, Updater.getDisplayVersion()),
                                    style = typography().xs.copy(color = colorPalette().textSecondary)
                                )
                                BasicText(
                                    text = stringResource(R.string.app_update_dialog_size, Updater.build.readableSize.ifEmpty { "?" }),
                                    style = typography().xs.copy(color = colorPalette().textSecondary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(700)) + scaleIn(
                        animationSpec = tween(700),
                        initialScale = 0.9f
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel button
                        Card(
                            modifier = Modifier
                                .weight(1f)
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null,
                                        tint = colorPalette().textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    BasicText(
                                        text = stringResource(R.string.cancel),
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                        style = typography().xs.semiBold.copy(color = colorPalette().textSecondary),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Go to download button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clip(uiRoundnessShape()).clickable {
                                    onDismiss()
                                    onNavigateToUpdater()
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(
                                        text = stringResource(R.string.go_to_download),
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                        style = typography().xs.semiBold.copy(color = Color.White),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        painter = painterResource(R.drawable.chevron_forward),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}




